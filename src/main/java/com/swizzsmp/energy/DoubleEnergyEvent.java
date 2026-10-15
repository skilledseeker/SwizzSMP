package com.swizzsmp.energy;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.ServerState;
import com.swizzsmp.util.Time;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Double energy event (DESIGN.md §5.3): for 2 hours every kill that earns energy earns extra.
 * The end time is kept in server-state.yml, so a restart doesn't end it. Everyone sees a
 * boss bar counting down while it runs.
 */
public final class DoubleEnergyEvent implements Listener {

    private static final String KEY = "double-energy.until";
    private static final String TOTAL = "double-energy.total";

    private final SwizzPlugin plugin;
    private final ServerState state;
    private final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
    private BukkitTask task;

    public DoubleEnergyEvent(SwizzPlugin plugin, ServerState state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
    }

    public boolean isActive() {
        return state.getLong(KEY) > System.currentTimeMillis();
    }

    /** Extra energy per kill while the event runs (0 when it doesn't). */
    public int extraPerKill() {
        return isActive() ? Math.max(0, plugin.settings().doubleEnergyExtra) : 0;
    }

    /** Starts (or restarts) the event for {@code minutes}. */
    public void begin(double minutes) {
        long length = Math.round(minutes * 60_000);
        state.setLong(KEY, System.currentTimeMillis() + length);
        state.setLong(TOTAL, length);
        String time = Time.format(length);
        Bukkit.getServer().sendMessage(plugin.messages().get("event.started", Messages.text("time", time)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        tick();
    }

    public void end() {
        boolean wasActive = isActive();
        state.setLong(KEY, 0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
        if (wasActive) {
            Bukkit.getServer().sendMessage(plugin.messages().get("event.ended"));
        }
    }

    private void tick() {
        long until = state.getLong(KEY);
        if (until <= 0) {
            return;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            end();
            return;
        }
        long total = Math.max(1, state.getLong(TOTAL));
        bar.name(plugin.messages().get("event.bossbar", Messages.text("time", Time.format(left))));
        bar.progress((float) Math.max(0, Math.min(1, left / (double) total)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bar);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isActive()) {
            event.getPlayer().showBossBar(bar);
        }
    }
}
