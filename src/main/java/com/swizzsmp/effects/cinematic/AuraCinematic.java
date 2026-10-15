package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * "aura" (DESIGN.md §13.7): a glowing sigil unfolds under the player as they rise, three rings
 * tumble around them like a gyroscope while the seven swizz orbit faster and faster, streaming
 * their colours in; then everything collapses into a white flash, a ring of lightning, a
 * pillar of light into the sky and a shockwave of element blocks, and the player hangs there
 * with wings of light and a halo before floating down.
 */
final class AuraCinematic extends Cinematic {

    private static final int SIGIL_START = 10;
    private static final int GEMS_START = 50;
    private static final int GEM_GAP = 8;
    private static final int RINGS = 70;
    private static final int BEAMS_START = 95;
    private static final int CHARGE_START = 100;
    private static final int CONVERGE = 180;
    private static final int WINGS = 190;
    private static final int HALO = 200;
    private static final int PILLAR_OUT = 250;
    private static final int FADE = 275;
    private static final int LAND = 300;
    private static final int END = 340;
    private static final double ORBIT_RADIUS = 5.5;
    static final String AURA_TEXTURE = "swizz:misc/aura";
    /** A major, played when the seven become one. */
    static final int[] CHORD = {15, 19, 22};
    private static final Color GOLD = Color.fromRGB(0xFFE36E);
    private static final Color WHITE = Color.fromRGB(0xFFFFFF);

    private final SwizzOrbit gems = new SwizzOrbit(this);
    private final Shards shards = new Shards(this);
    private Sigil sigil;
    private Gyroscope rings;
    private LightPillar pillar;
    private Wings wings;
    private ItemDisplay halo;

    AuraCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        Location chest = chest();
        if (t == 0) {
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.DARKNESS, 90, 0);
            }
            sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.5f);
            lift(30, 1);
            sigil = new Sigil(this, ground);
        }
        if (t == 30) {
            hover();
        }
        if (t % Sigil.SPIN_STEP == 0 && t > 0) {
            int next = t + Sigil.SPIN_STEP;
            sigil.spin(charge(), outerScale(next), innerScale(next));
            animate(halo, flatTransform(-t * 0.08, next < HALO + 5 || next >= END - 12 ? 0.01f : 1.1f, 0f), Sigil.SPIN_STEP);
            if (rings != null) {
                rings.spin(1 + 3 * charge(), ringScale(next));
            }
        }
        if (t == SIGIL_START) {
            sound(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f);
        }
        if (t >= 40 && t < CONVERGE && t % 4 == 0) {
            sigil.sparkle(ground);
        }
        if (t < CONVERGE && t % (t < 140 ? 20 : 10) == 0) {
            sound(Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f + t / 400f);
        }
        if (t >= GEMS_START && t < GEMS_START + TYPES.length * GEM_GAP && (t - GEMS_START) % GEM_GAP == 0) {
            gems.raise((t - GEMS_START) / GEM_GAP, ground, ORBIT_RADIUS);
        }
        if (t == RINGS) {
            rings = new Gyroscope(this);
            sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f);
            sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f);
        }
        if (rings != null) {
            rings.follow();
        }
        if (t >= GEMS_START && t < CONVERGE) {
            double charge = charge();
            double radius = t < 130 ? ORBIT_RADIUS : t < 170 ? lerp(ORBIT_RADIUS, 3, (t - 130) / 40.0) : lerp(3, 0, (t - 170) / 10.0);
            if (t == 172) {
                gems.resize(0.3f, 8);
            }
            gems.orbit(chest, radius, 1.5 + 16 * charge * charge);
        }
        if (t >= BEAMS_START && t < CONVERGE && t % 2 == 0) {
            gems.beams(chest);
        }
        if (t == CHARGE_START) {
            sound(Sound.BLOCK_PORTAL_TRIGGER, 1.2f);
        }
        if (t == 140) {
            sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f);
        }
        if (t == 172) {
            sound(Sound.ITEM_TRIDENT_RIPTIDE_3, 0.7f);
        }
        if (t >= 120 && t < CONVERGE && t % 2 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, chest, 4 + (t - 120) / 4, 0.5, 0.8, 0.5, 0.15);
        }
        if (t == CONVERGE) {
            converge(chest);
        }
        if (t >= CONVERGE && t < CONVERGE + 8) {
            world.strikeLightningEffect(around(ground, (t - CONVERGE) * Math.PI / 4, 7, ground.getY()));
        }
        if (t == CONVERGE + 2) {
            shards.launch(9, 15, false, -0.5, 3.5);
        }
        if (t > CONVERGE && t <= CONVERGE + 20) {
            gems.rainbowRing(t - CONVERGE);
        }
        if (pillar != null) {
            pillar.shine();
            if (t == CONVERGE + 1) {
                pillar.resize(1.1f, 6);
            } else if (t == PILLAR_OUT) {
                pillar.resize(0.01f, 20);
                sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.5f);
            } else if (t > CONVERGE + 6 && t < PILLAR_OUT && t % 10 == 0) {
                pillar.resize(1.1f + (float) Math.sin(t / 7.0) * 0.15f, 10);
            }
        }
        if (t == CONVERGE + 7) {
            for (Player viewer : viewers()) {
                overlay(viewer, AURA_TEXTURE, 70);
                effect(viewer, PotionEffectType.NAUSEA, 90, 0);
            }
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f);
        }
        if (t == CONVERGE + 9) {
            title("aura.title", "aura.subtitle", 150, 2600, 900);
        }
        if (t == WINGS) {
            wings = new Wings(this, GOLD, WHITE);
            sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.3f);
            sound(Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.8f);
        }
        if (wings != null && t < END - 10) {
            double unfold = t < WINGS + 20 ? (t - WINGS) / 20.0 : t > END - 40 ? 1 - (t - (END - 40)) / 30.0 : 1;
            wings.draw(2.4, unfold);
            if (t % 25 == 0) {
                sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f + random().nextFloat() * 0.3f);
            }
        }
        if (t == HALO) {
            halo = plane(head(), Sigil.INNER_MODEL, Display.Billboard.FIXED, flatTransform(0, 0.01f, 0f));
        }
        if (halo != null) {
            halo.teleport(head());
        }
        if (t == CONVERGE + 40) {
            shards.removeAll();
        }
        if (t == FADE) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
        }
        if (t == LAND) {
            release();
        }
        if (t == 262) {
            for (int note : CHORD) {
                sound(Sound.BLOCK_NOTE_BLOCK_CHIME, pitch(note));
            }
        }
        if (t > CONVERGE + 20 && t < END - 20 && t % 3 == 0) {
            world.spawnParticle(Particle.END_ROD, chest, 2, 2.5, 1.5, 2.5, 0.01);
        }
    }

    private void converge(Location chest) {
        gems.converge(chest);
        world.strikeLightningEffect(player.getLocation());
        world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, chest, 1);
        world.spawnParticle(Particle.END_ROD, chest, 150, 0.3, 0.3, 0.3, 0.6);
        world.spawnParticle(Particle.FIREWORK, chest, 100, 0.3, 0.3, 0.3, 0.45);
        for (Player viewer : viewers()) {
            whiteFlash(viewer, 7);
            sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
            sound(viewer, Sound.ITEM_TOTEM_USE, 1f, 1f);
            sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            sound(viewer, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.8f);
            for (int note : CHORD) {
                sound(viewer, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch(note));
            }
            jolt(viewer, 0, 3, 7);
        }
        shards.spawn(chest, Shards.ELEMENTS, TYPES.length * 6, 0.5f);
        pillar = new LightPillar(this, ground, Material.WHITE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS);
    }

    /** 0 → 1 while the seven build up, falling back to 0 after the convergence. */
    private double charge() {
        if (t <= CONVERGE) {
            return clamp((t - CHARGE_START) / (double) (CONVERGE - CHARGE_START));
        }
        return clamp(1 - (t - CONVERGE) / 40.0);
    }

    private float ringScale(int at) {
        if (at < CONVERGE) {
            return (float) (2.6 * smooth((at - RINGS) / 20.0));
        }
        if (at < CONVERGE + 6) {
            return 3.6f;
        }
        return (float) Math.max(0.01, 3.6 * (1 - smooth((at - CONVERGE - 6) / 14.0)));
    }

    private float outerScale(int at) {
        if (at < 50) {
            return Sigil.unfold(at, SIGIL_START, 40, Sigil.OUTER_SIZE);
        }
        if (at >= CONVERGE && at < CONVERGE + 8) {
            return Sigil.OUTER_SIZE * 1.2f;
        }
        if (at >= FADE) {
            return Sigil.fold(at, FADE, 45, Sigil.OUTER_SIZE);
        }
        return Sigil.OUTER_SIZE + (float) Math.sin(at / 8.0) * 0.4f;
    }

    private float innerScale(int at) {
        if (at < 50) {
            return Sigil.unfold(at, SIGIL_START + 10, 30, Sigil.INNER_SIZE);
        }
        if (at >= FADE - 5) {
            return Sigil.fold(at, FADE - 5, 45, Sigil.INNER_SIZE);
        }
        return Sigil.INNER_SIZE;
    }
}
