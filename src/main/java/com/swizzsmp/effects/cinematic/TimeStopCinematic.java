package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "timestop" (DESIGN.md §13.7): a giant golden clock appears over the player and a sphere
 * of stopped time sweeps outward. Everything it passes freezes where it is: players (even
 * mid-jump), mobs, arrows and items hang in the air, the world goes silent and cold blue.
 * Only the caster moves, leaving afterimages behind. Then the hands spin back, the sphere
 * collapses and everything carries on exactly where it stopped.
 */
final class TimeStopCinematic extends Cinematic {

    private static final int SPHERE = 12;
    private static final int SPHERE_TICKS = 15;
    private static final int STOPPED = SPHERE + SPHERE_TICKS;
    private static final int WARN = 118;
    private static final int RESUME = 140;
    private static final int RESUME_TICKS = 15;
    private static final int CLOCK_OUT = 168;
    private static final int END = 230;
    private static final double RADIUS = 24;
    private static final float CLOCK_SIZE = 4.2f;
    private static final String FACE = "swizz:clock_face";
    private static final String HOUR = "swizz:clock_hour";
    private static final String MINUTE = "swizz:clock_minute";
    private static final String TINT = "swizz:misc/timestop";
    private static final int MAX_ECHOES = 12;

    /** A thing in flight, held in place: what it was doing before time stopped. */
    private record Held(Vector velocity, boolean gravity) {
    }

    private final Set<UUID> audience = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> pausedMobs = new HashSet<>();
    private final Map<UUID, Held> held = new HashMap<>();
    private final List<Mannequin> echoes = new ArrayList<>();
    private ItemDisplay face;
    private ItemDisplay hour;
    private ItemDisplay minute;
    private double hourAngle;
    private double minuteAngle;
    private Location lastEcho;
    private boolean stopped;

    TimeStopCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t == 0) {
            Location at = clockAt();
            face = plane(at, FACE, Display.Billboard.CENTER, facing(0, 0.01f, 0));
            hour = plane(at, HOUR, Display.Billboard.CENTER, facing(0, 0.01f, 0.04f));
            minute = plane(at, MINUTE, Display.Billboard.CENTER, facing(0, 0.01f, 0.08f));
            sound(Sound.BLOCK_BELL_USE, 0.5f);
            sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.6f);
        }
        if (face != null) {
            Location at = clockAt();
            face.teleport(at);
            hour.teleport(at);
            minute.teleport(at);
        }
        if (t % 2 == 0) {
            turnHands();
        }
        if (t == SPHERE) {
            zaWarudo();
        }
        if (t >= SPHERE && t < STOPPED) {
            double radius = (t - SPHERE + 1) / (double) SPHERE_TICKS * RADIUS;
            shell(radius, Color.fromRGB(0x9FF3FF));
            freezeWithin(radius);
        }
        if (stopped) {
            freezeWithin(RADIUS);
            if (t % 20 == 0 || (t >= WARN && t % (t < 130 ? 5 : 2) == 0)) {
                world.playSound(clockAt(), Sound.BLOCK_COMPARATOR_CLICK, 1.5f, (t / 2) % 2 == 0 ? 0.5f : 0.7f);
            }
            if (t % 6 == 0) {
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.5, 0), 25, 8, 4, 8, 0,
                        new Particle.DustOptions(Color.fromRGB(0xDDF8FF), 0.8f));
            }
            if (t % 6 == 0) {
                echo();
            }
            if (t % 10 == 0) {
                player.sendActionBar(plugin.messages().get("timestop.actionbar",
                        Messages.text("time", String.format("%.1fs", (RESUME - t) / 20.0))));
            }
        }
        if (t == WARN || t == WARN + 10) {
            sound(Sound.BLOCK_BELL_USE, 0.7f);
        }
        if (t == RESUME) {
            resume();
        }
        if (t > RESUME && t <= RESUME + RESUME_TICKS) {
            double radius = RADIUS * (1 - (t - RESUME) / (double) RESUME_TICKS);
            shell(radius, Color.fromRGB(0xFFE9A8));
            unfreezeOutside(radius);
            shatterEchoes(2);
        }
        if (t == RESUME + RESUME_TICKS) {
            unfreezeAll();
        }
        if (t == CLOCK_OUT) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.2f);
        }
    }

    // ── The clock ──────────────────────────────────────────────────────────────

    private Location clockAt() {
        return flat(player.getLocation().add(0, player.getHeight() + 2.8, 0));
    }

    /** Hands race while time winds up, stop dead while it is stopped, then spin back. */
    private void turnHands() {
        double speed;
        if (t < STOPPED) {
            speed = 0.45;
        } else if (t < WARN) {
            speed = 0;
        } else if (t < RESUME + RESUME_TICKS) {
            speed = -0.12 - 0.9 * clamp((t - WARN) / 30.0);
        } else {
            speed = 0.1;
        }
        minuteAngle -= speed;
        hourAngle -= speed / 12;
        float size = clockSize(t + 2);
        animate(face, facing(0, size, 0), 2);
        animate(hour, facing(hourAngle, size, 0.04f), 2);
        animate(minute, facing(minuteAngle, size, 0.08f), 2);
    }

    private float clockSize(int at) {
        if (at < 12) {
            return (float) Math.max(0.01, CLOCK_SIZE * smooth(at / 12.0));
        }
        if (at >= CLOCK_OUT) {
            return (float) Math.max(0.01, CLOCK_SIZE * (1 - smooth((at - CLOCK_OUT) / 15.0)));
        }
        if (stopped) {
            return CLOCK_SIZE + (float) Math.sin(at / 10.0) * 0.15f;
        }
        return CLOCK_SIZE;
    }

    // ── Stopping time ──────────────────────────────────────────────────────────

    private void zaWarudo() {
        stopped = true;
        Messages msg = plugin.messages();
        Title title = Title.title(msg.get("timestop.title", Messages.text("name", player.getName())),
                msg.get("timestop.subtitle", Messages.text("name", player.getName())),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(600)));
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            sound(viewer, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.7f);
            sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
            sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
            viewer.showTitle(title);
            jolt(viewer, 0);
        }
        overlay(player, TINT, RESUME - t);
        lastEcho = player.getLocation();
    }

    private void shell(double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        Location center = player.getLocation().add(0, 1, 0);
        Particles.sphere(center, radius, (int) Math.min(260, 30 + radius * radius * 0.6), false,
                p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust));
        world.spawnParticle(Particle.END_ROD, center, 6, radius * 0.6, radius * 0.3, radius * 0.6, 0);
    }

    private void freezeWithin(double radius) {
        Location center = player.getLocation();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(center) <= radius * radius) {
                freeze(entity);
            }
        }
    }

    /** Holds one thing still. The caster's own fresh projectiles fly a couple of blocks first. */
    private void freeze(Entity entity) {
        UUID id = entity.getUniqueId();
        if (entity == player || entity == player.getVehicle() || entity instanceof Display ||entity.getPersistentDataContainer().has(Keys.SPAWNED_BY_SWIZZ, PersistentDataType.BYTE)
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
            audience.add(id);
            Attrs.apply(other, Attribute.MOVEMENT_SPEED, Keys.MOD_TIMESTOP_SPEED, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            Attrs.apply(other, Attribute.JUMP_STRENGTH, Keys.MOD_TIMESTOP_JUMP, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            Attrs.apply(other, Attribute.GRAVITY, Keys.MOD_TIMESTOP_GRAVITY, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            other.setVelocity(new Vector());
            other.stopAllSounds();
            overlay(other, TINT, RESUME - t);
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

    /** Afterimages: statues of the caster left along their path while time is stopped. */
    private void echo() {
        Location at = player.getLocation();
        if (echoes.size() >= MAX_ECHOES || lastEcho == null || at.distanceSquared(lastEcho) < 0.8 * 0.8) {
            return;
        }
        Location from = lastEcho;
        lastEcho = at;
        ResolvableProfile profile = ResolvableProfile.resolvableProfile(player.getPlayerProfile());
        echoes.add(spawn(from, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setGravity(false);
            m.setDescription(Component.empty());
        }));
        world.spawnParticle(Particle.DUST, from.clone().add(0, 1, 0), 15, 0.3, 0.8, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x9FF3FF), 1f));
        world.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.6f);
    }

    // ── Time resumes ───────────────────────────────────────────────────────────

    private void resume() {
        stopped = false;
        Title title = Title.title(Component.empty(), plugin.messages().get("timestop.resumes"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(600)));
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            whiteFlash(viewer, 3);
            sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 1f);
            sound(viewer, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
            sound(viewer, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
            viewer.showTitle(title);
            jolt(viewer, 0, 3);
        }
    }

    private void shatterEchoes(int count) {
        for (int i = 0; i < count && !echoes.isEmpty(); i++) {
            Mannequin echo = echoes.remove(echoes.size() - 1);
            Location at = echo.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.DUST, at, 40, 0.3, 0.9, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x9FF3FF), 1.3f));
            world.spawnParticle(Particle.CRIT, at, 20, 0.3, 0.8, 0.3, 0.3);
            world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
            remove(echo);
        }
    }

    private void unfreezeOutside(double radius) {
        Location center = player.getLocation();
        for (UUID id : List.copyOf(frozenPlayers)) {
            Player other = Bukkit.getPlayer(id);
            if (other == null || other.getWorld() != world || other.getLocation().distanceSquared(center) > radius * radius) {
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

    private void unfreezeAll() {
        List.copyOf(frozenPlayers).forEach(this::thawPlayer);
        List.copyOf(pausedMobs).forEach(this::thawMob);
        List.copyOf(held.keySet()).forEach(this::letGo);
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
    protected void cleanup() {
        unfreezeAll();
        if (t < END) {
            for (UUID id : audience) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) {
                    clearOverlay(viewer);
                }
            }
        }
    }
}
