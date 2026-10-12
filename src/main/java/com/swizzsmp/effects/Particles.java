package com.swizzsmp.effects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/** Small geometry helpers for particle shapes. */
public final class Particles {

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private Particles() {
    }

    public static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    public static void dust(Location at, Color color, float size, int count, double spread) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, dust(color, size));
    }

    /** Calls {@code action} at evenly spaced points from {@code from} to {@code to}. */
    public static void line(Location from, Location to, double step, Consumer<Location> action) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1e-6) {
            action.accept(from.clone());
            return;
        }
        Vector dir = delta.multiply(1.0 / length);
        for (double d = 0; d <= length; d += step) {
            action.accept(from.clone().add(dir.clone().multiply(d)));
        }
    }

    /** Horizontal circle around {@code center}. {@code offset} rotates the start (radians). */
    public static void circle(Location center, double radius, int points, double offset, Consumer<Location> action) {
        for (int i = 0; i < points; i++) {
            double angle = offset + 2 * Math.PI * i / points;
            action.accept(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
    }

    /** Roughly even points on a sphere (Fibonacci lattice). {@code upperOnly} keeps y >= center. */
    public static void sphere(Location center, double radius, int points, boolean upperOnly, Consumer<Location> action) {
        for (int i = 0; i < points; i++) {
            double y = 1 - (i + 0.5) * 2.0 / points;
            if (upperOnly && y < 0) {
                continue;
            }
            double r = Math.sqrt(1 - y * y);
            double theta = GOLDEN_ANGLE * i;
            action.accept(center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius));
        }
    }

    /** Spawns a particle at a location for everyone nearby. */
    public static void at(Location loc, Particle particle, int count, double spread, double speed) {
        World world = loc.getWorld();
        world.spawnParticle(particle, loc, count, spread, spread, spread, speed);
    }
}
