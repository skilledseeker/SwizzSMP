package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * "singularity" (DESIGN.md §13.7): a black hole opens above the player. The sky goes dark,
 * an accretion disk spins up and the ground tears apart, blocks peeling off and spiralling
 * in (copies, while the real blocks are hidden from viewers with fake air). It collapses to
 * a point, there is a moment of silence, then it detonates and the ground rebuilds itself
 * ring by ring.
 */
final class SingularityCinematic extends Cinematic {

    private static final int FORM = 10;
    private static final int DARK = 40;
    private static final int TEAR_START = 45;
    private static final int TEAR_END = 165;
    private static final int COLLAPSE = 165;
    private static final int SILENCE = 174;
    private static final int DETONATE = 180;
    private static final int END = 250;
    private static final double HEIGHT = 7.5;
    static final String VOID = "swizz:misc/void";

    private final FakeBlocks fakes = new FakeBlocks(this);
    private final GroundTear tear = new GroundTear(this, fakes);
    private final Shards shards = new Shards(this);
    private final Location core;
    private BlackHole hole;

    SingularityCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
        this.core = ground.clone().add(0, HEIGHT, 0);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t == 0) {
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.DARKNESS, 80, 0);
                sound(viewer, Sound.ENTITY_WARDEN_EMERGE, 1f, 0.7f);
            }
            lift(20, 0);
            hole = new BlackHole(this, core);
        }
        if (t == 20) {
            hover();
        }
        if (t % BlackHole.SPIN_STEP == 0 && t > 0 && t <= DETONATE) {
            int next = t + BlackHole.SPIN_STEP;
            hole.spin(smooth((next - FORM) / 40.0), clamp((next - 100) / 65.0), next >= COLLAPSE ? 1 - smooth((next - COLLAPSE) / 12.0) : 1);
        }
        if (t == FORM) {
            hole.openingSounds();
        }
        if (t == 30) {
            sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
        }
        if (t == DARK) {
            for (Player viewer : viewers()) {
                overlay(viewer, VOID, DETONATE - DARK);
                sky(viewer, 18000);
            }
        }
        if (t >= FORM && t < COLLAPSE) {
            hole.streams(t < 60 ? 3 : 6);
        }
        if (t >= TEAR_START && t < TEAR_END) {
            tear.tear(t < 80 ? 2 : 4, 3.5, 14, core);
            if (t % 10 == 0) {
                sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f);
            }
        }
        tear.update();
        if (t < COLLAPSE && t % 40 == 0) {
            sound(Sound.BLOCK_BEACON_AMBIENT, 0.5f);
        }
        if (t >= 60 && t < COLLAPSE && t % (t < 120 ? 20 : 10) == 0) {
            sound(Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f);
        }
        if (t == 120) {
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.NAUSEA, 70, 0);
            }
        }
        if (t >= 120 && t < COLLAPSE && t % 12 == 0) {
            for (Player viewer : viewers()) {
                jolt(viewer, 0);
            }
        }
        if (t == COLLAPSE) {
            hole.collapseSounds();
        }
        if (t == SILENCE) {
            for (Player viewer : viewers()) {
                viewer.stopAllSounds();
            }
        }
        if (t == DETONATE) {
            tear.clearDebris();
            hole.detonate();
            shards.spawn(core, Shards.COSMIC, 40, 0.6f);
        }
        if (t > DETONATE && t <= DETONATE + 16) {
            hole.shockwave(t - DETONATE);
        }
        if (t == DETONATE + 2) {
            release();
            shards.launch(10, 18, true, 0, 0);
        }
        if (t == DETONATE + 6) {
            title("singularity.title", "singularity.subtitle", 150, 2500, 900);
            for (Player viewer : viewers()) {
                resetSky(viewer);
            }
        }
        if (t > DETONATE + 1) {
            tear.rebuild((t - DETONATE - 1) * 0.9);
        }
        if (t == DETONATE + 40) {
            shards.removeAll();
            hole.removeAll();
        }
        if (t > DETONATE + 10 && t < END - 10 && t % 3 == 0) {
            world.spawnParticle(Particle.REVERSE_PORTAL, core, 20, 4, 3, 4, 0.01);
            world.spawnParticle(Particle.WHITE_ASH, core, 30, 6, 4, 6, 0);
        }
        if (t == DETONATE + 30) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f);
        }
    }
}
