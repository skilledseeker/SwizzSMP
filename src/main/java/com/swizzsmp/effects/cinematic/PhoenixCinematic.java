package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.effects.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * "phoenix" (DESIGN.md §13.7): the player catches fire. Flames spiral up around them and
 * screens glow with heat until they burn away to ash in the dark; then a flash, a cry, and
 * they are reborn in a pillar of fire with burning wings and a ring of flame racing out.
 */
final class PhoenixCinematic extends Cinematic {

    private static final int IGNITE = 40;
    private static final int ASH = 130;
    private static final int REBIRTH = 185;
    private static final int WINGS_OUT = 300;
    private static final int LAND = 310;
    private static final int END = 350;
    private static final Color EMBER = Color.fromRGB(0xFF4F2F);
    private static final Color GOLD = Color.fromRGB(0xFFD27F);

    private final VisualFire fire = new VisualFire(this);
    private Wings wings;
    private LightPillar pillar;

    PhoenixCinematic(SwizzPlugin plugin, Player player) {
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
                overlay(viewer, MeteorCinematic.EMBER, ASH - t);
                sound(viewer, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
                sound(viewer, Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.5f);
            }
            lift(20, 0);
        }
        if (t == 20) {
            hover();
        }
        if (t < ASH) {
            double heat = clamp(t / (double) ASH);
            spiral(chest, 1.2 + heat, 2 + (int) (6 * heat), heat > 0.5);
            if (t % 10 == 0) {
                sound(Sound.BLOCK_FIRE_AMBIENT, 0.8f + (float) heat * 0.6f);
            }
            if (t % 30 == 0) {
                sound(Sound.ENTITY_BLAZE_AMBIENT, 0.6f + (float) heat);
            }
        }
        if (t == IGNITE) {
            fire.set(true);
            sound(Sound.ENTITY_BLAZE_SHOOT, 0.7f);
            sound(Sound.ENTITY_GENERIC_BURN, 0.8f);
            for (Player viewer : viewers()) {
                jolt(viewer, 0);
            }
        }
        if (t >= IGNITE && t < ASH && t % 4 == 0) {
            world.spawnParticle(Particle.LAVA, chest, 2, 0.4, 0.6, 0.4, 0);
            world.spawnParticle(Particle.SMOKE, chest.clone().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.02);
        }
        if (t >= 100 && t < ASH && t % (t < 120 ? 10 : 5) == 0) {
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.1f);
        }
        if (t == ASH) {
            fire.set(false);
            world.spawnParticle(Particle.ASH, chest, 300, 0.6, 1.2, 0.6, 0.4);
            world.spawnParticle(Particle.WHITE_ASH, chest, 200, 0.8, 1.2, 0.8, 0.3);
            world.spawnParticle(Particle.LARGE_SMOKE, chest, 60, 0.4, 0.8, 0.4, 0.05);
            for (Player viewer : viewers()) {
                viewer.stopAllSounds();
                sound(viewer, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
                sound(viewer, Sound.ENTITY_GENERIC_BURN, 1f, 0.5f);
                blackout(viewer, REBIRTH - t);
            }
        }
        if (t == ASH + 25 || t == ASH + 42) {
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.8f);
        }
        if (t == REBIRTH - 12) {
            soundAll(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
        }
        if (t == REBIRTH) {
            rebirth(chest);
        }
        if (t > REBIRTH && t <= REBIRTH + 18) {
            double radius = (t - REBIRTH) * 1.2;
            Particles.circle(ground.clone().add(0, 0.4, 0), radius, 12 + (int) (radius * 6), 0, p -> world.spawnParticle(Particle.FLAME, p, 1, 0.05, 0.1, 0.05, 0.02));
            Particles.circle(ground.clone().add(0, 0.6, 0), radius, 8 + (int) (radius * 3), 0.2, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.8f)));
        }
        if (pillar != null) {
            pillar.shine();
            if (t == REBIRTH + 1) {
                pillar.resize(1.3f, 5);
            } else if (t == REBIRTH + 50) {
                pillar.resize(0.01f, 25);
            }
        }
        if (t == REBIRTH + 8) {
            title("phoenix.title", "phoenix.subtitle", 150, 2600, 900);
            for (Player viewer : viewers()) {
                overlay(viewer, MeteorCinematic.EMBER, 50);
            }
        }
        if (wings != null && t < END - 10) {
            double unfold = t < REBIRTH + 15 ? (t - REBIRTH) / 15.0 : t > WINGS_OUT ? 1 - (t - WINGS_OUT) / 30.0 : 1;
            wings.draw(2.6, unfold);
            if (t % 22 == 0) {
                sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f + random().nextFloat() * 0.3f);
                world.spawnParticle(Particle.FLAME, chest, 30, 1.5, 0.8, 1.5, 0.04);
            }
        }
        if (t > REBIRTH + 5 && t < END - 20) {
            spiral(chest, 1.4, 2, true);
            if (t % 3 == 0) {
                world.spawnParticle(Particle.LAVA, chest, 1, 0.8, 0.8, 0.8, 0);
            }
        }
        if (t == LAND) {
            release();
        }
        if (t == END - 30) {
            sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.8f);
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.2f);
        }
    }

    /** Flames climbing around the player in two spirals. */
    private void spiral(Location center, double radius, int perStrand, boolean soul) {
        for (int s = 0; s < 2; s++) {
            for (int i = 0; i < perStrand; i++) {
                double f = (i + random().nextDouble()) / perStrand;
                double angle = t * 0.25 + s * Math.PI + f * Math.PI * 2;
                Location at = around(center, angle, radius * (0.5 + 0.5 * f), center.getY() - 1 + f * 2.6);
                world.spawnParticle(soul && random().nextInt(3) == 0 ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, at, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    private void rebirth(Location chest) {
        fire.set(true);
        lift(12, 1);
        world.strikeLightningEffect(player.getLocation());
        world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.fromRGB(0xFFB070));
        world.spawnParticle(Particle.FLAME, chest, 300, 0.4, 0.6, 0.4, 0.6);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, chest, 120, 0.4, 0.6, 0.4, 0.4);
        world.spawnParticle(Particle.LAVA, chest, 60, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.END_ROD, chest, 100, 0.3, 0.3, 0.3, 0.5);
        for (Player viewer : viewers()) {
            whiteFlash(viewer, 6);
            sound(viewer, Sound.ENTITY_GHAST_SCREAM, 1f, 1.8f);
            sound(viewer, Sound.ENTITY_PHANTOM_DEATH, 1f, 0.6f);
            sound(viewer, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            sound(viewer, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
            sound(viewer, Sound.ITEM_TOTEM_USE, 1f, 0.8f);
            sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            jolt(viewer, 0, 3, 7);
        }
        wings = new Wings(this, EMBER, GOLD);
        pillar = new LightPillar(this, ground, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::hover, 13L);
    }
}
