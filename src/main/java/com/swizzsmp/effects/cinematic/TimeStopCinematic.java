package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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
    static final double RADIUS = 24;
    static final String TINT = "swizz:misc/timestop";
    static final Color ICE = Color.fromRGB(0x9FF3FF);
    static final Color GOLD = Color.fromRGB(0xFFE9A8);

    private final TimeFreeze freeze = new TimeFreeze(this);
    private final Afterimages echoes = new Afterimages(this, 12);
    private Clock clock;
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
            clock = new Clock(this);
            freeze.setTint(TINT, RESUME - SPHERE);
            sound(Sound.BLOCK_BELL_USE, 0.5f);
            sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.6f);
        }
        clock.follow();
        if (t % 2 == 0) {
            clock.turn(handSpeed(), Clock.size(t + 2, 0, 12, CLOCK_OUT, 15, stopped));
        }
        if (t == SPHERE) {
            stopped = true;
            title("timestop.title", "timestop.subtitle", 100, 2000, 600);
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.7f);
                sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
                sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
                jolt(viewer, 0);
            }
            overlay(player, TINT, RESUME - t);
            echoes.start();
        }
        if (t >= SPHERE && t < STOPPED) {
            double radius = (t - SPHERE + 1) / (double) SPHERE_TICKS * RADIUS;
            shell(this, radius, ICE);
            freeze.sweep(radius);
        }
        if (stopped) {
            freeze.sweep(RADIUS);
            if (t % 20 == 0 || (t >= WARN && t % (t < 130 ? 5 : 2) == 0)) {
                world.playSound(head(), Sound.BLOCK_COMPARATOR_CLICK, 1.5f, (t / 2) % 2 == 0 ? 0.5f : 0.7f);
            }
            if (t % 6 == 0) {
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.5, 0), 25, 8, 4, 8, 0, new Particle.DustOptions(Color.fromRGB(0xDDF8FF), 0.8f));
                echoes.echo();
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
            stopped = false;
            title(null, "timestop.resumes", 100, 1500, 600);
            for (Player viewer : viewers()) {
                whiteFlash(viewer, 3);
                sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 1f);
                sound(viewer, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
                sound(viewer, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
                jolt(viewer, 0, 3);
            }
        }
        if (t > RESUME && t <= RESUME + RESUME_TICKS) {
            double radius = RADIUS * (1 - (t - RESUME) / (double) RESUME_TICKS);
            shell(this, radius, GOLD);
            freeze.thawOutside(radius);
            echoes.shatter(2);
        }
        if (t == RESUME + RESUME_TICKS) {
            freeze.thawAll();
        }
        if (t == CLOCK_OUT) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.2f);
        }
    }

    /** Hands race while time winds up, stop dead while it is stopped, then spin back. */
    private double handSpeed() {
        if (t < STOPPED) {
            return 0.45;
        }
        if (t < WARN) {
            return 0;
        }
        if (t < RESUME + RESUME_TICKS) {
            return -0.12 - 0.9 * clamp((t - WARN) / 30.0);
        }
        return 0.1;
    }

    /** The edge of the time sphere: a shell of dust around the player. */
    static void shell(Cinematic show, double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        Location center = show.player.getLocation().add(0, 1, 0);
        Particles.sphere(center, radius, (int) Math.min(260, 30 + radius * radius * 0.6), false,
                p -> show.world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust));
        show.world.spawnParticle(Particle.END_ROD, center, 6, radius * 0.6, radius * 0.3, radius * 0.6, 0);
    }
}
