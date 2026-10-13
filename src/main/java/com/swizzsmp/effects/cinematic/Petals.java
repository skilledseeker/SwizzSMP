package com.swizzsmp.effects.cinematic;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.List;

/** Cherry blossom petals (the same pinks as the petal trail): a vortex around a point, a stream into one, or a storm from the sky. */
final class Petals extends Layer {

    Petals(Cinematic host) {
        super(host);
    }

    private Color pink() {
        List<Color> colors = host.plugin.settings().trailColors;
        return colors.get(Cinematic.random().nextInt(colors.size()));
    }

    private void petal(Location at, double spread) {
        if (Cinematic.random().nextBoolean()) {
            host.world.spawnParticle(Particle.CHERRY_LEAVES, at, 1, spread, spread / 2, spread, 0);
        } else {
            host.world.spawnParticle(Particle.TINTED_LEAVES, at, 1, spread, spread / 2, spread, 0, pink());
        }
    }

    /** {@code strands} spirals of petals climbing around {@code center}, turning with time. */
    void vortex(Location center, double radius, double height, int strands, int perStrand) {
        for (int s = 0; s < strands; s++) {
            for (int i = 0; i < perStrand; i++) {
                double f = (i + Cinematic.random().nextDouble()) / perStrand;
                double angle = host.t * 0.12 + s * 2 * Math.PI / strands + f * Math.PI * 2;
                petal(Cinematic.around(center, angle, radius * (0.6 + 0.4 * f), center.getY() - height / 2 + f * height), 0.1);
            }
        }
    }

    /** Petals from around the floor streaming into {@code target}. */
    void stream(Location target, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Cinematic.random().nextDouble(Math.PI * 2);
            Location from = Cinematic.around(host.ground, angle, Cinematic.random().nextDouble(3, 12), host.ground.getY() + Cinematic.random().nextDouble(0.3, 4));
            host.world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0, new Particle.Trail(target, pink(), Cinematic.random().nextInt(16, 30)));
            if (i % 3 == 0) {
                petal(from, 0.2);
            }
        }
    }

    /** A storm of petals drifting down from {@code height} above the floor within {@code radius}. */
    void storm(double radius, double height, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Cinematic.random().nextDouble(Math.PI * 2);
            double distance = Math.sqrt(Cinematic.random().nextDouble()) * radius;
            petal(Cinematic.around(host.ground, angle, distance, host.ground.getY() + height + Cinematic.random().nextDouble(-2, 2)), 0.3);
        }
    }
}
