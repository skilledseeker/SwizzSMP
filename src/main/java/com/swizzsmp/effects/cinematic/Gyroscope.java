package com.swizzsmp.effects.cinematic;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Three glowing rings tumbling around the player like a gyroscope: one flat around the waist,
 * one upright sweeping around them, one tilted and rolling the other way. Pack planes.
 */
final class Gyroscope extends Layer {

    static final int STEP = 5;

    private final ItemDisplay[] rings = new ItemDisplay[3];
    private double spin;

    Gyroscope(Cinematic host) {
        super(host);
        Location at = host.chest();
        rings[0] = host.plane(at, Sigil.OUTER_MODEL, Display.Billboard.FIXED, new Transformation(new Vector3f(), rotation(0, 0), new Vector3f(0.01f), new Quaternionf()));
        rings[1] = host.plane(at, Sigil.INNER_MODEL, Display.Billboard.FIXED, new Transformation(new Vector3f(), rotation(1, 0), new Vector3f(0.01f), new Quaternionf()));
        rings[2] = host.plane(at, Sigil.INNER_MODEL, Display.Billboard.FIXED, new Transformation(new Vector3f(), rotation(2, 0), new Vector3f(0.01f), new Quaternionf()));
    }

    /** Keeps the rings on the player; call every tick. */
    void follow() {
        Location at = host.chest();
        for (ItemDisplay ring : rings) {
            ring.teleport(at);
        }
    }

    /** Turns the rings for the next {@link #STEP} ticks at {@code speed} (1 = calm, 4 = frantic); call every STEP ticks. */
    void spin(double speed, float scale) {
        spin += Math.toRadians(STEP * 3 * speed);
        for (int i = 0; i < rings.length; i++) {
            float size = Math.max(0.01f, scale * (i == 0 ? 1f : 0.82f));
            Cinematic.animate(rings[i], new Transformation(new Vector3f(), rotation(i, spin), new Vector3f(size), new Quaternionf()), STEP);
        }
    }

    private static Quaternionf rotation(int ring, double spin) {
        return switch (ring) {
            case 0 -> new Quaternionf().rotateY((float) spin).rotateX((float) (-Math.PI / 2));
            case 1 -> new Quaternionf().rotateY((float) (spin * 0.7));
            default -> new Quaternionf().rotateY((float) (-spin * 0.5)).rotateX((float) (Math.PI / 3));
        };
    }

    void removeAll() {
        for (ItemDisplay ring : rings) {
            host.remove(ring);
        }
    }
}
