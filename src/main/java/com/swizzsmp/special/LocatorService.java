package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.ServerState;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Dragon Egg locator bar (DESIGN.md §13.2). The locator bar (other players shown on the
 * XP bar) is hidden for everyone, except the player who activated the Dragon Egg tracker,
 * for 30 minutes. 12 hour cooldown for the whole server. State is kept in
 * server-state.yml so it survives restarts.
 */
public final class LocatorService {

    private static final NamespacedKey HIDE_KEY = Keys.key("locator_hidden");
    private static final String USER = "locator-user";
    private static final String UNTIL = "locator-until";
    private static final String READY_AT = "locator-ready-at";

    private final SwizzPlugin plugin;
    private final ServerState state;
    private BukkitTask task;

    public LocatorService(SwizzPlugin plugin, ServerState state) {
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
            Attrs.remove(player, Attribute.WAYPOINT_RECEIVE_RANGE, HIDE_KEY);
        }
    }

    private boolean enabled() {
        return plugin.settingsFile().getBoolean("dragon-egg.locator.enabled");
    }

    private UUID activeUser() {
        String raw = state.getString(USER);
        if (raw == null || state.getLong(UNTIL) <= System.currentTimeMillis()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Sneak + right-click with the dragon egg. */
    public void activate(Player player) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = state.getLong(READY_AT);
        if (readyAt > now) {
            plugin.messages().send(player, "dragon-egg.locator-cooldown", Messages.text("time", Time.format(readyAt - now)));
            return;
        }
        var cfg = plugin.settingsFile();
        long duration = Time.secondsToMillis(cfg.getDouble("dragon-egg.locator.duration-minutes") * 60);
        long cooldown = Time.secondsToMillis(cfg.getDouble("dragon-egg.locator.cooldown-hours") * 3600);
        state.setString(USER, player.getUniqueId().toString());
        state.setLong(UNTIL, now + duration);
        state.setLong(READY_AT, now + cooldown);
        plugin.messages().send(player, "dragon-egg.locator-on", Messages.text("time", Time.format(duration)));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.8f);
        tick();
    }

    public void resetCooldown() {
        state.setLong(READY_AT, 0);
    }

    private void tick() {
        boolean enabled = enabled();
        boolean hideOthers = plugin.settingsFile().getBoolean("dragon-egg.locator.hide-for-everyone-else");
        UUID user = activeUser();
        // The tracker only works while the user still carries the dragon egg.
        if (user != null) {
            Player holder = Bukkit.getPlayer(user);
            if (holder == null || !holder.getInventory().contains(org.bukkit.Material.DRAGON_EGG)) {
                state.setLong(UNTIL, 0);
                user = null;
            }
        }
        // Tell the user once when their tracker runs out.
        String raw = state.getString(USER);
        if (raw != null && user == null) {
            state.setString(USER, null);
            Player previous = Bukkit.getPlayer(UUID.fromString(raw));
            if (previous != null) {
                plugin.messages().send(previous, "dragon-egg.locator-ended");
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hidden = enabled && hideOthers && !player.getUniqueId().equals(user);
            if (hidden) {
                // Receive range 0 = no other players on this player's locator bar.
                Attrs.apply(player, Attribute.WAYPOINT_RECEIVE_RANGE, HIDE_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            } else {
                Attrs.remove(player, Attribute.WAYPOINT_RECEIVE_RANGE, HIDE_KEY);
            }
        }
    }
}
