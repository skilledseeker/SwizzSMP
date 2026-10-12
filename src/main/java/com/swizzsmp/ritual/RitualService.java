package com.swizzsmp.ritual;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.items.TokenListener;
import com.swizzsmp.util.Time;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Upgrader rituals (DESIGN.md §8). A ritual lasts 20 minutes, and its coordinates leak
 * one by one. If the owner is close when it ends, their swizz is upgraded. Otherwise
 * the Upgrader drops at the ritual site. Saved to rituals.yml; downtime pauses it.
 */
public final class RitualService {

    private record PendingConfirm(Location block, long until) {
    }

    private final SwizzPlugin plugin;
    private final File file;
    private final Map<UUID, Ritual> rituals = new HashMap<>();
    private final Map<UUID, PendingConfirm> confirms = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public RitualService(SwizzPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rituals.yml");
    }

    public void start() {
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        save();
    }

    // ── Starting ──────────────────────────────────────────────────────────────

    public void onUpgraderUse(Player player, EquipmentSlot hand, Block clicked, BlockFace face) {
        if (clicked == null) {
            plugin.messages().send(player, "ritual.click-block");
            return;
        }
        PlayerData data = plugin.store().get(player);
        Settings s = plugin.settings();
        if (!data.hasSwizz()) {
            plugin.messages().send(player, "ritual.need-swizz");
            return;
        }
        if (data.upgraded()) {
            plugin.messages().send(player, "ritual.already-upgraded");
            return;
        }
        if (rituals.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "ritual.already-running");
            return;
        }
        Block anchor = clicked.getRelative(BlockFace.UP);
        org.bukkit.Location spawn = anchor.getWorld().getSpawnLocation();
        int max = s.ritualMaxDistanceFromSpawn;
        if (Math.abs(anchor.getX() - spawn.getBlockX()) > max || Math.abs(anchor.getZ() - spawn.getBlockZ()) > max) {
            plugin.messages().send(player, "ritual.out-of-bounds", Messages.text("max", max));
            return;
        }
        long now = System.currentTimeMillis();
        PendingConfirm pending = confirms.get(player.getUniqueId());
        if (pending == null || pending.until() < now || !pending.block().equals(anchor.getLocation())) {
            confirms.put(player.getUniqueId(), new PendingConfirm(anchor.getLocation(), now + Time.secondsToMillis(s.ritualConfirmSeconds)));
            plugin.messages().send(player, "ritual.confirm",
                    Messages.text("time", Time.format(Time.secondsToMillis(s.ritualConfirmSeconds))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        confirms.remove(player.getUniqueId());
        ItemStack item = player.getInventory().getItem(hand);
        if (!CustomItems.is(item, ItemIds.UPGRADER)) {
            return;
        }
        TokenListener.consumeOne(player, hand, item);
        begin(player, anchor);
        player.saveData(); // the upgrader is gone from the saved inventory too (no dupe on crash)
    }

    /** Starts a ritual for {@code player} with its anchor at {@code anchor}. */
    private void begin(Player player, Block anchor) {
        Settings s = plugin.settings();
        Ritual ritual = new Ritual(player.getUniqueId(), player.getName(), anchor.getWorld().getUID(),
                anchor.getX(), anchor.getY(), anchor.getZ());
        ritual.leaked.add("Y");
        rituals.put(player.getUniqueId(), ritual);
        save();
        broadcast("ritual.started", ritual);
        plugin.messages().send(player, "ritual.stay-close", Messages.text("radius", trim(s.ritualRadius)));
        World world = anchor.getWorld();
        Location center = ritual.center();
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        world.playSound(center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 2f, 0.6f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 60, 0.5, 1, 0.5, 0.4);
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    private void tick() {
        tick++;
        if (rituals.isEmpty()) {
            return;
        }
        boolean second = tick % 20 == 0;
        for (Ritual ritual : new ArrayList<>(rituals.values())) {
            Location center = ritual.center();
            if (center == null) {
                continue; // world not loaded: paused
            }
            if (tick % 5 == 0) {
                visuals(ritual, center);
            }
            if (!second) {
                continue;
            }
            ritual.elapsedSeconds++;
            Settings s = plugin.settings();
            int duration = (int) Math.round(s.ritualDurationMinutes * 60);
            int interval = Math.max(1, (int) Math.round(s.ritualLeakIntervalMinutes * 60));
            if (ritual.elapsedSeconds >= duration) {
                finish(ritual, center);
                continue;
            }
            int step = ritual.elapsedSeconds / interval;
            if (step > ritual.lastAnnouncedStep) {
                ritual.lastAnnouncedStep = step;
                announce(ritual, step);
            }
            if (ritual.elapsedSeconds % 100 == 0) {
                center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.6f);
            }
        }
    }

    private void announce(Ritual ritual, int step) {
        List<String> order = plugin.settings().ritualLeakOrder;
        String next = null;
        for (String axis : order) {
            String upper = axis.toUpperCase(Locale.ROOT);
            if (!ritual.leaked.contains(upper)) {
                next = upper;
                break;
            }
        }
        if (next != null) {
            ritual.leaked.add(next);
            broadcast("ritual.leak", ritual);
        } else {
            broadcast("ritual.reminder", ritual);
        }
        save();
    }

    private void visuals(Ritual ritual, Location center) {
        World world = center.getWorld();
        double radius = plugin.settings().ritualRadius;
        double spin = tick * 0.03;
        Location ground = center.clone().add(0, -0.4, 0);
        Particles.circle(ground, radius, 64, spin, at -> {
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xFF3B3B), 1.5f));
        });
        Particles.circle(ground, radius, 16, -spin * 2, at -> world.spawnParticle(Particle.END_ROD, at.add(0, 0.2, 0), 1, 0, 0.1, 0, 0.01));
        world.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1, 0), 12, 0.6, 0.8, 0.6, 1.0);
        world.spawnParticle(Particle.DUST, center, 3, 0.15, 0.6, 0.15, 0, new Particle.DustOptions(Color.fromRGB(0xB31217), 1.2f));
    }

    private void finish(Ritual ritual, Location center) {
        rituals.remove(ritual.owner);
        save();
        Player owner = Bukkit.getPlayer(ritual.owner);
        double radius = plugin.settings().ritualRadius;
        boolean close = owner != null && !owner.isDead() && owner.getWorld().equals(center.getWorld())
                && owner.getLocation().distanceSquared(center) <= radius * radius;
        if (close) {
            PlayerData data = plugin.store().get(owner);
            data.setUpgraded(true);
            plugin.onStateChanged(owner);
            broadcast("ritual.success", ritual);
            celebrate(center);
            owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            dropUpgrader(center);
            broadcast("ritual.failed", ritual);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.6f);
        }
    }

    private void dropUpgrader(Location center) {
        center.getWorld().dropItem(center, plugin.items().create(ItemIds.UPGRADER, 1));
    }

    private void celebrate(Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 150, 0.6, 1.5, 0.6, 0.6);
        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 2f, 0.8f);
        Firework firework = world.spawn(center, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(0xFF5E5E), Color.fromRGB(0xFFD36E)).withFade(Color.WHITE).trail(true).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
        firework.detonate();
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    private void broadcast(String key, Ritual ritual) {
        Bukkit.getServer().sendMessage(plugin.messages().get(key, placeholders(ritual)));
    }

    private TagResolver[] placeholders(Ritual ritual) {
        Settings s = plugin.settings();
        int duration = (int) Math.round(s.ritualDurationMinutes * 60);
        long left = Math.max(0, duration - ritual.elapsedSeconds) * 1000L;
        return new TagResolver[]{
                Messages.text("name", ritual.ownerName),
                Messages.parsed("x", coordinate(ritual, "X", ritual.x)),
                Messages.parsed("y", coordinate(ritual, "Y", ritual.y)),
                Messages.parsed("z", coordinate(ritual, "Z", ritual.z)),
                Messages.text("dimension", dimensionName(ritual.world())),
                Messages.text("time", Time.format(left))
        };
    }

    private static String coordinate(Ritual ritual, String axis, int value) {
        return ritual.leaked.contains(axis) ? String.valueOf(value) : "<obfuscated>" + "0".repeat(String.valueOf(value).length()) + "</obfuscated>";
    }

    private static String dimensionName(World world) {
        if (world == null) {
            return "an unknown world";
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> "the Overworld";
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> world.getName();
        };
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public void adminCommand(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            if (rituals.isEmpty()) {
                plugin.messages().send(sender, "ritual.admin-list-empty");
                return;
            }
            for (Ritual ritual : rituals.values()) {
                TagResolver[] base = placeholders(ritual);
                // Admins always see real coordinates.
                plugin.messages().send(sender, "ritual.admin-list-entry", base[0],
                        Messages.text("x", ritual.x), Messages.text("y", ritual.y), Messages.text("z", ritual.z), base[4], base[5]);
            }
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("start")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messages().send(sender, "general.player-not-found", Messages.text("name", args[2]));
            } else if (rituals.containsKey(target.getUniqueId())) {
                plugin.messages().send(sender, "ritual.already-running");
            } else {
                begin(target, target.getLocation().getBlock());
                plugin.messages().send(sender, "ritual.admin-started", Messages.text("name", target.getName()));
            }
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("cancel")) {
            Ritual found = null;
            for (Ritual ritual : rituals.values()) {
                if (ritual.ownerName.equalsIgnoreCase(args[2])) {
                    found = ritual;
                }
            }
            if (found == null) {
                plugin.messages().send(sender, "ritual.admin-none", Messages.text("name", args[2]));
                return;
            }
            rituals.remove(found.owner);
            save();
            Player owner = Bukkit.getPlayer(found.owner);
            ItemStack upgrader = plugin.items().create(ItemIds.UPGRADER, 1);
            if (owner != null) {
                owner.getInventory().addItem(upgrader).values()
                        .forEach(rest -> owner.getWorld().dropItemNaturally(owner.getLocation(), rest));
            } else if (found.center() != null) {
                dropUpgrader(found.center());
            }
            plugin.messages().send(sender, "ritual.admin-cancelled", Messages.text("name", found.ownerName));
            return;
        }
        plugin.messages().send(sender, "general.usage", Messages.text("usage", "/swizzadmin ritual <list | start <player> | cancel <player>>"));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Ritual ritual : rituals.values()) {
            String path = ritual.owner.toString();
            yaml.set(path + ".name", ritual.ownerName);
            yaml.set(path + ".world", ritual.world.toString());
            yaml.set(path + ".x", ritual.x);
            yaml.set(path + ".y", ritual.y);
            yaml.set(path + ".z", ritual.z);
            yaml.set(path + ".elapsed", ritual.elapsedSeconds);
            yaml.set(path + ".leaked", new ArrayList<>(ritual.leaked));
            yaml.set(path + ".announced-step", ritual.lastAnnouncedStep);
        }
        try {
            if (rituals.isEmpty()) {
                java.nio.file.Files.deleteIfExists(file.toPath());
            } else {
                yaml.save(file);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save rituals.yml", e);
        }
    }

    private void load() {
        rituals.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Ritual ritual = new Ritual(UUID.fromString(key), section.getString("name", "?"),
                        UUID.fromString(section.getString("world", "")), section.getInt("x"), section.getInt("y"), section.getInt("z"));
                ritual.elapsedSeconds = section.getInt("elapsed");
                ritual.leaked.addAll(section.getStringList("leaked"));
                ritual.lastAnnouncedStep = section.getInt("announced-step");
                rituals.put(ritual.owner, ritual);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("rituals.yml: bad entry " + key + " skipped");
            }
        }
        if (!rituals.isEmpty()) {
            plugin.getLogger().info("Resumed " + rituals.size() + " upgrade ritual(s).");
        }
    }
}
