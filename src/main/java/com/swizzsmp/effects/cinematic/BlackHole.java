package com.swizzsmp.effects.cinematic;

import com.swizzsmp.effects.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A black hole: a camera-facing black disc with a photon ring, and two tilted accretion disks
 * spinning against each other. Everything is pack planes, so it needs the texture pack.
 */
final class BlackHole extends Layer {

    static final int SPIN_STEP = 5;
    static final Color[] MATTER = {Color.fromRGB(0xFF8A3D), Color.fromRGB(0xB06BFF), Color.fromRGB(0xFFD27F), Color.fromRGB(0xFF4FA3)};
    private static final String CORE = "swizz:bh_core";
    private static final String RING = "swizz:bh_ring";
    private static final String DISK = "swizz:bh_disk";

    final Location center;
    private final ItemDisplay core;
    private final ItemDisplay ring;
    private final ItemDisplay disk;
    private final ItemDisplay innerDisk;
    private double spin;

    BlackHole(Cinematic host, Location center) {
        super(host);
        this.center = center.clone();
        core = host.plane(center, CORE, Display.Billboard.CENTER, Cinematic.facing(0, 0.01f, 0));
        ring = host.plane(center, RING, Display.Billboard.CENTER, Cinematic.facing(0, 0.01f, 0.02f));
        disk = host.plane(center, DISK, Display.Billboard.FIXED, diskTransform(0, 0.01f, 0.30));
        innerDisk = host.plane(center, DISK, Display.Billboard.FIXED, diskTransform(0, 0.01f, -0.18));
    }

    /**
     * Sets the look for the next {@link #SPIN_STEP} ticks; call every SPIN_STEP ticks.
     * {@code grow} 0→1 opens it, {@code fury} 0→1 spins it up and swells the core,
     * {@code shrink} 1→0 collapses it.
     */
    void spin(double grow, double fury, double shrink) {
        spin += Math.toRadians(SPIN_STEP * (3 + 9 * fury));
        float coreSize = (float) Math.max(0.01, (3.0 + 1.5 * fury) * grow * shrink);
        Cinematic.animate(core, Cinematic.facing(0, coreSize, 0), SPIN_STEP);
        Cinematic.animate(ring, Cinematic.facing(-spin * 0.7, coreSize * 1.45f, 0.02f), SPIN_STEP);
        Cinematic.animate(disk, diskTransform(spin, (float) Math.max(0.01, 14 * grow * shrink), 0.30), SPIN_STEP);
        Cinematic.animate(innerDisk, diskTransform(-spin * 1.6, (float) Math.max(0.01, 8.5 * grow * shrink), -0.18), SPIN_STEP);
    }

    /** A flat disk tilted by {@code tilt} radians and spinning about its own axis. */
    private static Transformation diskTransform(double angle, float scale, double tilt) {
        return new Transformation(new Vector3f(),
                new Quaternionf().rotateX((float) tilt).rotateY((float) angle).rotateX((float) (-Math.PI / 2)),
                new Vector3f(scale), new Quaternionf());
    }

    /** Streaks of glowing matter falling in from every direction. */
    void streams(int count) {
        for (int i = 0; i < count; i++) {
            double theta = Cinematic.random().nextDouble(Math.PI * 2);
            double phi = Math.acos(Cinematic.random().nextDouble(-1, 1));
            double r = Cinematic.random().nextDouble(9, 16);
            Location from = center.clone().add(r * Math.sin(phi) * Math.cos(theta), r * Math.cos(phi), r * Math.sin(phi) * Math.sin(theta));
            Color color = MATTER[Cinematic.random().nextInt(MATTER.length)];
            host.world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0, new Particle.Trail(center, color, Cinematic.random().nextInt(14, 24)));
        }
        host.world.spawnParticle(Particle.REVERSE_PORTAL, center, 6, 1.2, 1.2, 1.2, 0.05);
    }

    void openingSounds() {
        host.sound(Sound.BLOCK_END_GATEWAY_SPAWN, 0.5f);
    }

    void collapseSounds() {
        host.sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f);
        host.sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f);
        host.sound(Sound.BLOCK_PORTAL_TRIGGER, 2f);
    }

    /** The big bang at the centre: flash, lightning, explosions, light and every matter colour. */
    void detonate() {
        host.world.strikeLightningEffect(center);
        host.world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
        host.world.spawnParticle(Particle.SONIC_BOOM, center, 1);
        host.world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, 1.5, 1.5, 1.5, 0);
        host.world.spawnParticle(Particle.END_ROD, center, 250, 0.5, 0.5, 0.5, 0.8);
        for (Color color : MATTER) {
            host.world.spawnParticle(Particle.DUST, center, 40, 2, 2, 2, 0, new Particle.DustOptions(color, 2.4f));
        }
        for (Player viewer : host.viewers()) {
            host.whiteFlash(viewer, 8);
            Cinematic.sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
            Cinematic.sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
            Cinematic.sound(viewer, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.7f);
            Cinematic.sound(viewer, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.4f);
            Cinematic.sound(viewer, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.5f);
            host.jolt(viewer, 0, 2, 5, 9);
        }
    }

    /** An expanding shell of light around the centre, and a ring racing along the floor. */
    void shockwave(int k) {
        double radius = k * 1.4;
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(0xF2E6FF), 1.6f);
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(0xB06BFF), 1.6f);
        int[] i = {0};
        Particles.sphere(center, radius, 40 + k * 8, false, p -> host.world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, i[0]++ % 2 == 0 ? white : purple));
        Particles.circle(host.ground.clone().add(0, 0.3, 0), radius, 12 + (int) (radius * 6), 0,
                p -> host.world.spawnParticle(Particle.CLOUD, p, 1, 0, 0.05, 0, 0.02));
    }

    void removeAll() {
        host.remove(core);
        host.remove(ring);
        host.remove(disk);
        host.remove(innerDisk);
    }
}
