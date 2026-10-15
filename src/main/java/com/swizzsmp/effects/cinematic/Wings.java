package com.swizzsmp.effects.cinematic;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

/** A pair of glowing particle wings on the player's back that follow where they look and slowly beat. */
final class Wings extends Layer {

    private final Color root;
    private final Color tip;

    Wings(Cinematic host, Color root, Color tip) {
        super(host);
        this.root = root;
        this.tip = tip;
    }

    /** Draws both wings for this tick at {@code span} blocks (0 → 1 unfolds them). */
    void draw(double span, double unfold) {
        Location at = host.player.getLocation();
        Vector forward = at.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location back = at.clone().add(forward.clone().multiply(-0.35)).add(0, 1.25, 0);
        double beat = 1 + 0.12 * Math.sin(host.t / 5.0);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 16; i++) {
                double u = i / 16.0 * Cinematic.clamp(unfold);
                // The upper edge sweeps up and out; the lower edge trails down into feathers.
                double out = (0.25 + u * span) * beat;
                double upper = 0.35 + 1.1 * Math.sin(u * Math.PI * 0.85) - 0.55 * u * u;
                double lower = upper - 0.35 - 0.75 * Math.sin(u * Math.PI) * (1 - u * 0.4);
                Color color = blend(u);
                Particle.DustOptions dust = new Particle.DustOptions(color, (float) (1.4 - u * 0.5));
                Location top = back.clone().add(right.clone().multiply(side * out)).add(forward.clone().multiply(-u * 0.5)).add(0, upper, 0);
                Location bottom = back.clone().add(right.clone().multiply(side * out * 0.95)).add(forward.clone().multiply(-u * 0.5)).add(0, lower, 0);
                host.world.spawnParticle(Particle.DUST, top, 1, 0.02, 0.02, 0.02, 0, dust);
                if (i % 2 == 0) {
                    host.world.spawnParticle(Particle.DUST, bottom, 1, 0.02, 0.02, 0.02, 0, dust);
                }
                if (i % 4 == 0) {
                    host.world.spawnParticle(Particle.END_ROD, top, 1, 0.05, 0.05, 0.05, 0.005);
                }
            }
        }
    }

    private Color blend(double u) {
        return Color.fromRGB(
                (int) Cinematic.lerp(root.getRed(), tip.getRed(), u),
                (int) Cinematic.lerp(root.getGreen(), tip.getGreen(), u),
                (int) Cinematic.lerp(root.getBlue(), tip.getBlue(), u));
    }
}
