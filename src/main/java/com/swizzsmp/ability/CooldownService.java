package com.swizzsmp.ability;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Counts down every cooldown. Remaining time goes down by {@code elapsed × rate}, where
 * the rate comes from environment passives (DESIGN.md §6.3). Offline time is subtracted
 * on load at rate 1 (see PlayerDataStore).
 */
public final class CooldownService {

    private static final int PERIOD_TICKS = 5;
    private static final long PERIOD_MILLIS = PERIOD_TICKS * 50L;

    private final SwizzPlugin plugin;
    private BukkitTask task;

    public CooldownService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public long remaining(Player player, String key) {
        return plugin.store().get(player).cooldown(key);
    }

    public boolean isReady(Player player, String key) {
        return remaining(player, key) <= 0;
    }

    public void set(Player player, String key, long millis) {
        plugin.store().get(player).setCooldown(key, millis);
    }

    public void resetAll(PlayerData data) {
        data.cooldowns().clear();
        data.setLoadoutChangedAt(0);
        data.markDirty();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player);
            Map<String, Long> cooldowns = data.cooldowns();
            if (cooldowns.isEmpty()) {
                continue;
            }
            long step = Math.round(PERIOD_MILLIS * plugin.swizz().cooldownRate(player) * plugin.breachMace().cooldownRate(player));
            // "X:" keys (item cooldowns such as the Swizz Mace) are never sped up by passives.
            List<String> finished = new ArrayList<>(2);
            Iterator<Map.Entry<String, Long>> it = cooldowns.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                long left = entry.getValue() - (entry.getKey().startsWith("X:") ? PERIOD_MILLIS : step);
                if (left <= 0) {
                    it.remove();
                    finished.add(entry.getKey());
                } else {
                    entry.setValue(left);
                }
            }
            data.markDirty();
            for (String key : finished) {
                notifyReady(player, key);
            }
        }
    }

    private void notifyReady(Player player, String key) {
        if (!plugin.settings().readyNotification) {
            return;
        }
        String name = switch (key) {
            case "A1" -> plugin.abilityName(player, 1);
            case "A2" -> plugin.abilityName(player, 2);
            case "B1" -> plugin.abilityName(player, 3);
            case "B2" -> plugin.abilityName(player, 4);
            default -> null;
        };
        if (name == null) {
            return;
        }
        plugin.messages().send(player, "ability.ready", Messages.text("ability", name));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
    }
}
