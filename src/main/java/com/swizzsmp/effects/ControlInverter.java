package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reversed WASD (Confusion upgraded dome, DESIGN.md §7.1). The player's own movement
 * speed is set to 0, and every tick we read their keys and push them the opposite way.
 * Holding sprint pushes harder, so sprinting still works. Jumping is unchanged.
 */
public final class ControlInverter implements Listener {

    private record State(long untilTick, double strength, double sprintMultiplier) {
    }

    private final SwizzPlugin plugin;
    private final Map<UUID, State> inverted = new HashMap<>();
    private long tick;
    private BukkitTask task;

    public ControlInverter(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (UUID id : Map.copyOf(inverted).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                release(player);
            }
        }
        inverted.clear();
    }

    /** Reverses the player's keys for {@code ticks}; holding sprint pushes {@code sprintMultiplier} times harder. */
    public void invert(Player player, int ticks, double strength, double sprintMultiplier) {
        inverted.put(player.getUniqueId(), new State(tick + ticks, strength, Math.max(1, sprintMultiplier)));
        Attrs.apply(player, Attribute.MOVEMENT_SPEED, Keys.MOD_INVERT_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    public void release(Player player) {
        inverted.remove(player.getUniqueId());
        Attrs.remove(player, Attribute.MOVEMENT_SPEED, Keys.MOD_INVERT_SPEED);
    }

    private void tick() {
        tick++;
        if (inverted.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, State> entry : Map.copyOf(inverted).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                inverted.remove(entry.getKey());
                continue;
            }
            if (entry.getValue().untilTick() <= tick || player.isDead()) {
                release(player);
                continue;
            }
            Input input = player.getCurrentInput();
            int forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
            int strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
            if (forward == 0 && strafe == 0) {
                continue;
            }
            double yaw = Math.toRadians(player.getLocation().getYaw());
            Vector facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Vector left = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
            // Reversed: forward pushes back, left pushes right.
            Vector move = facing.multiply(-forward).add(left.multiply(-strafe));
            State state = entry.getValue();
            move.normalize().multiply(state.strength() * (input.isSprint() ? state.sprintMultiplier() : 1));
            Vector velocity = player.getVelocity();
            player.setVelocity(new Vector(move.getX(), velocity.getY(), move.getZ()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        release(event.getPlayer());
    }
}
