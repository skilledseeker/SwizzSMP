package com.swizzsmp.effects.cinematic;

import com.swizzsmp.effects.Particles;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

/**
 * The glowing floor sigil: a big outer ring (7-point star, one node per swizz) and a cyan inner
 * ring spinning the other way. Pack planes, so it needs the texture pack.
 */
final class Sigil extends Layer {

    static final int SPIN_STEP = 5;
    static final float OUTER_SIZE = 16f;
    static final float INNER_SIZE = 8.5f;
    static final String OUTER_MODEL = "swizz:sigil_outer";
    static final String INNER_MODEL = "swizz:sigil_inner";

    private final ItemDisplay outer;
    private final ItemDisplay inner;
    private double outerAngle;
    private double innerAngle;

    Sigil(Cinematic host, Location at) {
        super(host);
        outer = host.plane(at, OUTER_MODEL, Display.Billboard.FIXED, Cinematic.flatTransform(0, 0.01f, 0.03f));
        inner = host.plane(at, INNER_MODEL, Display.Billboard.FIXED, Cinematic.flatTransform(0, 0.01f, 0.06f));
    }

    /**
     * Turns both rings and sets their sizes for the next {@link #SPIN_STEP} ticks; call every
     * SPIN_STEP ticks. {@code charge} 0→1 spins them faster.
     */
    void spin(double charge, float outerScale, float innerScale) {
        outerAngle += Math.toRadians(SPIN_STEP * (1.2 + 4 * charge));
        innerAngle -= Math.toRadians(SPIN_STEP * (2.4 + 7 * charge));
        Cinematic.animate(outer, Cinematic.flatTransform(outerAngle, Math.max(0.01f, outerScale), 0.03f), SPIN_STEP);
        Cinematic.animate(inner, Cinematic.flatTransform(innerAngle, Math.max(0.01f, innerScale), 0.06f), SPIN_STEP);
    }

    /** Moves the whole sigil (the client glides it over 2 ticks). */
    void moveTo(Location at) {
        outer.teleport(Cinematic.flat(at.clone()));
        inner.teleport(Cinematic.flat(at.clone()));
    }

    /** The ring of reverse-portal sparks and enchant glyphs that drifts around the sigil. */
    void sparkle(Location at) {
        Particles.circle(at.clone().add(0, 0.2, 0), 7.4, 28, host.t * 0.05,
                p -> host.world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0.1, 0, 0.02));
        host.world.spawnParticle(Particle.ENCHANT, at.clone().add(0, 1.5, 0), 20, 3, 0.5, 3, 1.0);
    }

    /** Scale for a sigil that unfolds from {@code from} over {@code ticks}. */
    static float unfold(int t, int from, int ticks, float size) {
        return t < from ? 0.01f : (float) Math.max(0.01, size * Cinematic.smooth((t - from) / (double) ticks));
    }

    /** Scale for a sigil that shrinks away from {@code from} over {@code ticks}. */
    static float fold(int t, int from, int ticks, float size) {
        return t < from ? size : (float) Math.max(0.01, size * (1 - Cinematic.smooth((t - from) / (double) ticks)));
    }
}
