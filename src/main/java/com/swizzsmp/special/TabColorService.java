package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Colours each player's name in the tab list by the special item they carry:
 * dragon egg, Swizz Sword, Swizz Mace, upgrader, or nothing (in that priority order).
 * Colours come from config.yml → tab-colors.
 */
public final class TabColorService {

    private enum Carried { DRAGON_EGG, SWORD, MACE, UPGRADER, NONE }

    private final SwizzPlugin plugin;
    private final Map<UUID, Carried> shown = new HashMap<>();
    private BukkitTask task;

    public TabColorService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
        }
        shown.clear();
    }

    public void forget(Player player) {
        shown.remove(player.getUniqueId());
    }

    private void tick() {
        // Root-level lookups so the bundled defaults apply when the server file lacks a key.
        var root = plugin.settingsFile();
        boolean enabled = root.getBoolean("tab-colors.enabled");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled) {
                if (shown.remove(player.getUniqueId()) != null) {
                    player.playerListName(null);
                }
                continue;
            }
            Carried carried = carried(player);
            if (carried == shown.get(player.getUniqueId())) {
                continue;
            }
            shown.put(player.getUniqueId(), carried);
            player.playerListName(Component.text(player.getName(), color(root, carried)));
        }
    }

    private static Carried carried(Player player) {
        boolean egg = false, sword = false, mace = false, upgrader = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (item.getType() == Material.DRAGON_EGG) {
                egg = true;
                continue;
            }
            String id = CustomItems.idOf(item);
            if (ItemIds.SWORD.equals(id)) {
                sword = true;
            } else if (ItemIds.MACE.equals(id)) {
                mace = true;
            } else if (ItemIds.UPGRADER.equals(id)) {
                upgrader = true;
            }
        }
        if (egg) {
            return Carried.DRAGON_EGG;
        }
        if (sword) {
            return Carried.SWORD;
        }
        if (mace) {
            return Carried.MACE;
        }
        return upgrader ? Carried.UPGRADER : Carried.NONE;
    }

    private static TextColor color(ConfigurationSection root, Carried carried) {
        String key = switch (carried) {
            case DRAGON_EGG -> "dragon-egg";
            case SWORD -> "swizz-sword";
            case MACE -> "swizz-mace";
            case UPGRADER -> "upgrader";
            case NONE -> "default";
        };
        String raw = root.getString("tab-colors." + key);
        if (raw == null) {
            return TextColor.color(0xAAAAAA);
        }
        TextColor color = TextColor.fromCSSHexString(raw.startsWith("#") ? raw : "#" + raw);
        return color != null ? color : TextColor.color(0xAAAAAA);
    }
}
