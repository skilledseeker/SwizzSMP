package com.swizzsmp.effects.cinematic;

import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stopped time: everything inside a sphere around the player holds still. Players (even
 * mid-jump) get transient ×0 speed, jump and gravity modifiers and can only look around, mobs
 * lose their AI, and items, arrows and other moving things keep their velocity and gravity on
 * hold. Thawing puts everything back exactly as it was going.
 */
final class TimeFreeze extends Layer {

    private record Held(Vector velocity, boolean gravity) {
    }

    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> pausedMobs = new HashSet<>();
    private final Map<UUID, Held> held = new HashMap<>();
    /** Overlay shown to frozen players (null for none) and how long it should last. */
    private String tint;
    private int tintTicks;

    TimeFreeze(Cinematic host) {
        super(host);
    }

    void setTint(String texture, int ticks) {
        this.tint = texture;
        this.tintTicks = ticks;
    }

    /** Freezes everything within {@code radius} of the player that isn't frozen yet. */
    void sweep(double radius) {
        Location center = host.player.getLocation();
        for (Entity entity : host.world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(center) <= radius * radius) {
                freeze(entity);
            }
        }
    }

    /** Holds one thing still. The caster's own fresh projectiles fly a couple of blocks first. */
    void freeze(Entity entity) {
        UUID id = entity.getUniqueId();
        Player player = host.player;
        if (entity == player || entity == player.getVehicle() || entity instanceof Display
                || entity.getPersistentDataContainer().has(Keys.SPAWNED_BY_SWIZZ, PersistentDataType.BYTE)
                || frozenPlayers.contains(id) || pausedMobs.contains(id) || held.containsKey(id)) {
            return;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() == player
                && projectile.getLocation().distanceSquared(player.getLocation()) < 6) {
            return;
        }
        if (entity instanceof Player other) {
            if (other.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            frozenPlayers.add(id);
            Attrs.apply(other, Attribute.MOVEMENT_SPEED, Keys.MOD_TIMESTOP_SPEED, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            Attrs.apply(other, Attribute.JUMP_STRENGTH, Keys.MOD_TIMESTOP_JUMP, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            Attrs.apply(other, Attribute.GRAVITY, Keys.MOD_TIMESTOP_GRAVITY, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            other.setVelocity(new Vector());
            other.stopAllSounds();
            if (tint != null) {
                host.overlay(other, tint, tintTicks);
            }
        } else if (entity instanceof LivingEntity living) {
            if (living.hasAI()) {
                living.setAI(false);
                pausedMobs.add(id);
            }
        } else {
            held.put(id, new Held(entity.getVelocity(), entity.hasGravity()));
            entity.setVelocity(new Vector());
            entity.setGravity(false);
        }
    }

    /** Lets go of everything further than {@code radius} from the player (a collapsing sphere). */
    void thawOutside(double radius) {
        Location center = host.player.getLocation();
        for (UUID id : List.copyOf(frozenPlayers)) {
            Player other = Bukkit.getPlayer(id);
            if (other == null || other.getWorld() != host.world || other.getLocation().distanceSquared(center) > radius * radius) {
                thawPlayer(id);
            }
        }
        for (UUID id : List.copyOf(pausedMobs)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || entity.getLocation().distanceSquared(center) > radius * radius) {
                thawMob(id);
            }
        }
        for (UUID id : List.copyOf(held.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || entity.getLocation().distanceSquared(center) > radius * radius) {
                letGo(id);
            }
        }
    }

    void thawAll() {
        List.copyOf(frozenPlayers).forEach(this::thawPlayer);
        List.copyOf(pausedMobs).forEach(this::thawMob);
        List.copyOf(held.keySet()).forEach(this::letGo);
    }

    boolean isFrozen(Player other) {
        return frozenPlayers.contains(other.getUniqueId());
    }

    private void thawPlayer(UUID id) {
        frozenPlayers.remove(id);
        Player other = Bukkit.getPlayer(id);
        if (other != null) {
            Attrs.remove(other, Attribute.MOVEMENT_SPEED, Keys.MOD_TIMESTOP_SPEED);
            Attrs.remove(other, Attribute.JUMP_STRENGTH, Keys.MOD_TIMESTOP_JUMP);
            Attrs.remove(other, Attribute.GRAVITY, Keys.MOD_TIMESTOP_GRAVITY);
        }
    }

    private void thawMob(UUID id) {
        pausedMobs.remove(id);
        if (Bukkit.getEntity(id) instanceof LivingEntity living && living.isValid()) {
            living.setAI(true);
        }
    }

    /** Things in flight carry on exactly as they were going. */
    private void letGo(UUID id) {
        Held state = held.remove(id);
        Entity entity = Bukkit.getEntity(id);
        if (state != null && entity != null && entity.isValid()) {
            entity.setGravity(state.gravity());
            entity.setVelocity(state.velocity());
        }
    }

    @Override
    void stop() {
        thawAll();
    }
}
