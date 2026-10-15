package com.swizzsmp.effects.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A pillar of light shooting from the floor into the sky: two nested glass columns, slowly turning, with light rising inside. */
final class LightPillar extends Layer {

    private static final float HEIGHT = 48f;

    private final BlockDisplay core;
    private final BlockDisplay glow;
    private final Location base;
    private float width = 0.01f;

    LightPillar(Cinematic host, Location base, Material coreBlock, Material glowBlock) {
        super(host);
        this.base = base.clone();
        core = host.spawn(Cinematic.flat(base.clone()), BlockDisplay.class, d -> {
            d.setBlock(coreBlock.createBlockData());
            d.setBrightness(Cinematic.FULL_BRIGHT);
            d.setTransformation(column(0.01f, 0));
        });
        glow = host.spawn(Cinematic.flat(base.clone()), BlockDisplay.class, d -> {
            d.setBlock(glowBlock.createBlockData());
            d.setBrightness(Cinematic.FULL_BRIGHT);
            d.setTransformation(column(0.01f, 0.4));
        });
    }

    /** Sets the width for the next {@code ticks}; the columns keep turning. */
    void resize(float newWidth, int ticks) {
        width = Math.max(0.01f, newWidth);
        double turn = host.t * 0.02;
        Cinematic.animate(core, column(width, turn), ticks);
        Cinematic.animate(glow, column(width * 1.8f, -turn * 0.6), ticks);
    }

    /** Light drifting up inside the pillar; call every tick while it shows. */
    void shine() {
        if (width < 0.1f) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double angle = Cinematic.random().nextDouble(Math.PI * 2);
            double r = Cinematic.random().nextDouble(width * 0.6);
            host.world.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(angle) * r, Cinematic.random().nextDouble(HEIGHT * 0.6), Math.sin(angle) * r), 1, 0, 0.3, 0, 0.02);
        }
    }

    private static Transformation column(float width, double turn) {
        return new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf().rotateY((float) turn), new Vector3f(width, HEIGHT, width), new Quaternionf());
    }
}
