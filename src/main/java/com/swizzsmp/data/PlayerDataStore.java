package com.swizzsmp.data;

import com.swizzsmp.ability.Perk;
import com.swizzsmp.config.Settings;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Loads and saves {@link PlayerData} as one YAML file per player.
 * The YAML text is built on the main thread and written on a single background thread,
 * so writes for one player always land in order.
 */
public final class PlayerDataStore {

    private final JavaPlugin plugin;
    private final Supplier<Settings> settings;
    private final File folder;
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SwizzSMP-DataWriter");
        thread.setDaemon(true);
        return thread;
    });

    public PlayerDataStore(JavaPlugin plugin, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
    }

    /** Data of an online player. Always present between join and quit. */
    public PlayerData get(Player player) {
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            // Should not happen; load lazily so callers never see null.
            data = loadOnline(player);
        }
        return data;
    }

    public Optional<PlayerData> getIfOnline(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    public Collection<PlayerData> allOnline() {
        return online.values();
    }

    public boolean exists(UUID uuid) {
        return online.containsKey(uuid) || file(uuid).exists();
    }

    public PlayerData loadOnline(Player player) {
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            data = read(player.getUniqueId());
            online.put(player.getUniqueId(), data);
        }
        data.setLastKnownName(player.getName());
        return data;
    }

    /** Saves and forgets an online player's data. */
    public void unload(UUID uuid) {
        PlayerData data = online.remove(uuid);
        if (data != null) {
            saveAsync(data);
        }
    }

    /**
     * Data for any player: the live copy if online, otherwise read from disk.
     * Call {@link #saveAsync} after changing an offline copy.
     */
    public PlayerData loadAny(UUID uuid) {
        PlayerData data = online.get(uuid);
        return data != null ? data : read(uuid);
    }

    public void saveAsync(PlayerData data) {
        String text = serialize(data);
        UUID uuid = data.uuid();
        data.clearDirty();
        writer.execute(() -> write(uuid, text));
    }

    public void saveDirtyAsync() {
        for (PlayerData data : online.values()) {
            if (data.isDirty()) {
                saveAsync(data);
            }
        }
    }

    /** Saves everyone and waits for all writes to finish (plugin disable). */
    public void shutdown() {
        for (PlayerData data : online.values()) {
            saveAsync(data);
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for player data to save.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private PlayerData read(UUID uuid) {
        Settings s = settings.get();
        PlayerData data = new PlayerData(uuid, s.energyStart, s.hudDefault, s.controlsDefault);
        File file = file(uuid);
        if (!file.exists()) {
            data.markDirty();
            return data;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Corrupt player file " + file.getName() + " — starting fresh (a backup copy was kept).", e);
            backupCorrupt(file);
            data.markDirty();
            return data;
        }
        data.setLastKnownName(yaml.getString("name"));
        data.setSwizz(SwizzType.parse(yaml.getString("swizz")).orElse(null));
        data.setEnergy(Math.max(s.energyMin, Math.min(s.energyMax, yaml.getInt("energy", s.energyStart))));
        data.setUpgraded(yaml.getBoolean("upgraded"));
        List<Perk> loadout = new ArrayList<>();
        for (String raw : yaml.getStringList("loadout")) {
            Perk.parse(raw).ifPresent(loadout::add);
        }
        data.setLoadout(loadout);
        data.setLoadoutChangedAt(yaml.getLong("loadout-changed-at"));
        for (String raw : yaml.getStringList("trusted")) {
            try {
                data.trusted().add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // skip bad entries
            }
        }
        long offlineMillis = Math.max(0, System.currentTimeMillis() - yaml.getLong("cooldowns-saved-at", System.currentTimeMillis()));
        ConfigurationSection cds = yaml.getConfigurationSection("cooldowns");
        if (cds != null) {
            for (String key : cds.getKeys(false)) {
                long remaining = cds.getLong(key) - offlineMillis;
                if (remaining > 0) {
                    data.cooldowns().put(key, remaining);
                }
            }
        }
        SwizzType override = SwizzType.parse(yaml.getString("override.swizz")).orElse(null);
        if (override != null) {
            data.setOverride(override, yaml.getLong("override.until"));
        }
        data.setDisabledUntil(yaml.getLong("disabled-until"));
        data.setHudEnabled(yaml.getBoolean("hud", s.hudDefault));
        data.setControlsEnabled(yaml.getBoolean("controls", s.controlsDefault));
        data.setLastHealth(yaml.getDouble("last-health"));
        data.clearDirty();
        return data;
    }

    private String serialize(PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", data.lastKnownName());
        yaml.set("swizz", data.swizz() == null ? null : data.swizz().id());
        yaml.set("energy", data.energy());
        yaml.set("upgraded", data.upgraded());
        yaml.set("loadout", data.loadout().stream().map(Perk::name).toList());
        yaml.set("loadout-changed-at", data.loadoutChangedAt());
        yaml.set("trusted", data.trusted().stream().map(UUID::toString).toList());
        yaml.set("cooldowns-saved-at", System.currentTimeMillis());
        for (Map.Entry<String, Long> entry : data.cooldowns().entrySet()) {
            yaml.set("cooldowns." + entry.getKey(), entry.getValue());
        }
        if (data.overrideSwizz() != null) {
            yaml.set("override.swizz", data.overrideSwizz().id());
            yaml.set("override.until", data.overrideUntil());
        }
        yaml.set("disabled-until", data.disabledUntil());
        yaml.set("hud", data.hudEnabled());
        yaml.set("controls", data.controlsEnabled());
        yaml.set("last-health", data.lastHealth());
        return yaml.saveToString();
    }

    private void write(UUID uuid, String text) {
        Path target = file(uuid).toPath();
        Path temp = target.resolveSibling(uuid + ".yml.tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + uuid, e);
        }
    }

    private void backupCorrupt(File file) {
        try {
            Files.copy(file.toPath(), file.toPath().resolveSibling(file.getName() + ".corrupt-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not back up " + file, e);
        }
    }
}
