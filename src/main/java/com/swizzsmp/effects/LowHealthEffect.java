package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Low-health warning: red screen edges that pulse with a heartbeat sound. The red comes
 * from the client's world-border warning vignette: the player gets a private copy of the
 * real border with a huge warning distance, so only their screen tints and the real
 * border still works. The beat speeds up as health drops.
 */
public final class LowHealthEffect implements Listener {

    private static final class Beat {
        int ticksToNextBeat;
        int redTicksLeft;
    }

    private final SwizzPlugin plugin;
    private final Map<UUID, Beat> beating = new HashMap<>();
    private BukkitTask task;

    public LowHealthEffect(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (UUID id : Map.copyOf(beating).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clearRed(player);
            }
        }
        beating.clear();
    }

    private void tick() {
        var s = plugin.settings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Beat beat = beating.get(player.getUniqueId());
            boolean low = s.lowHealthEnabled && player.getHealth() <= s.lowHealthThreshold
                    && !player.isDead() && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR
                    && plugin.store().get(player).heartbeatEnabled();
            if (!low) {
                if (beat != null) {
                    beating.remove(player.getUniqueId());
                    clearRed(player);
                }
                continue;
            }
            if (beat == null) {
                beat = new Beat();
                beating.put(player.getUniqueId(), beat);
            }
            if (beat.redTicksLeft > 0 && --beat.redTicksLeft == 0) {
                clearRed(player);
            }
            if (--beat.ticksToNextBeat > 0) {
                continue;
            }
            // Lower health -> faster heartbeat (slow at the threshold, fast near death).
            double t = Math.max(0, Math.min(1, player.getHealth() / s.lowHealthThreshold));
            beat.ticksToNextBeat = (int) Math.round(s.lowHealthFastTicks + (s.lowHealthSlowTicks - s.lowHealthFastTicks) * t);
            beat.redTicksLeft = s.lowHealthFlashTicks;
            showRed(player);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, (float) s.lowHealthVolume, 1.0f);
        }
    }

    private static void showRed(Player player) {
        WorldBorder real = player.getWorld().getWorldBorder();
        WorldBorder red = Bukkit.createWorldBorder();
        red.setCenter(real.getCenter());
        // Some worlds report a border bigger than a player border may be; clamp it.
        red.setSize(Math.max(1.0, Math.min(real.getSize(), 5.9999968E7)));
        red.setWarningDistance(Integer.MAX_VALUE);
        player.setWorldBorder(red);
    }

    private static void clearRed(Player player) {
        player.setWorldBorder(null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        beating.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (beating.remove(event.getPlayer().getUniqueId()) != null) {
            clearRed(event.getPlayer());
        }
    }
}
