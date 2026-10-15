package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.effects.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * "meteor" (DESIGN.md §13.7): the sky falls. The dusk turns red, meteors streak down around
 * the player and blast glowing craters into the ground (fake blocks), then one enormous rock
 * comes straight down on them for a white-out impact. Ash falls, and the craters cool and heal.
 */
final class MeteorCinematic extends Cinematic {

    private static final int FIRST = 25;
    private static final int LAST = 190;
    private static final int GAP = 12;
    private static final int FLIGHT = 24;
    private static final int BIG = 205;
    private static final int BIG_FLIGHT = 45;
    private static final int IMPACT = BIG + BIG_FLIGHT;
    private static final int HEAL = IMPACT + 50;
    private static final int END = 340;
    static final String EMBER = "swizz:misc/ember";
    private static final Material[] ROCKS = {Material.MAGMA_BLOCK, Material.BLACKSTONE, Material.BASALT, Material.NETHERRACK};
    private static final Material[] CRATER = {Material.MAGMA_BLOCK, Material.BLACKSTONE, Material.BASALT, Material.BLACKSTONE};

    private record Meteor(BlockDisplay rock, Location from, Location to, int lands, float size) {
    }

    private final FakeBlocks fakes = new FakeBlocks(this);
    private final List<Meteor> meteors = new ArrayList<>();
    private final List<Block> craters = new ArrayList<>();
    private int healed;

    MeteorCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t == 0) {
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.DARKNESS, 40, 0);
                overlay(viewer, EMBER, IMPACT - t);
                sky(viewer, 13000);
                sound(viewer, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
                sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
            }
            lift(20, 0);
        }
        if (t == 20) {
            hover();
        }
        if (t >= FIRST && t <= LAST && (t - FIRST) % GAP == 0) {
            launch(around(ground, random().nextDouble(Math.PI * 2), random().nextDouble(4, 13), ground.getY()),
                    (float) random().nextDouble(1.0, 1.9), FLIGHT, false);
        }
        if (t == BIG) {
            title("meteor.brace", null, 100, 1600, 300);
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
                sound(viewer, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
                jolt(viewer, 0, 10, 20, 30, 40);
            }
            launch(ground.clone(), 4.5f, BIG_FLIGHT, true);
        }
        if (t > BIG && t < IMPACT && t % 3 == 0) {
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f + (t - BIG) / 60f);
        }
        fly();
        if (t > IMPACT && t <= IMPACT + 18) {
            shockwave(t - IMPACT);
        }
        if (t == IMPACT + 6) {
            title("meteor.title", "meteor.subtitle", 150, 2600, 900);
        }
        if (t > IMPACT && t < END - 20 && t % 2 == 0) {
            world.spawnParticle(Particle.ASH, chest(), 40, 10, 5, 10, 0);
            world.spawnParticle(Particle.LAVA, ground.clone().add(0, 0.5, 0), 3, 5, 0.5, 5, 0);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ground.clone().add(0, 0.5, 0), 4, 5, 0.3, 5, 0.01);
        }
        if (t >= HEAL) {
            heal(6);
        }
        if (t == HEAL) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.6f);
            for (Player viewer : viewers()) {
                resetSky(viewer);
            }
        }
        if (t == IMPACT + 30) {
            release();
        }
    }

    /** A rock appears high up and off to one side, and starts falling toward {@code to}. */
    private void launch(Location to, float size, int flight, boolean straightDown) {
        Location from = straightDown ? to.clone().add(0, 40, 0)
                : to.clone().add(random().nextDouble(-14, 14), random().nextDouble(26, 34), random().nextDouble(-14, 14));
        Material block = ROCKS[random().nextInt(ROCKS.length)];
        Quaternionf tumble = randomRotation(3);
        BlockDisplay rock = spawn(flat(from.clone()), BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setBrightness(FULL_BRIGHT);
            d.setTransformation(cube(new Vector3f(), new Quaternionf(), size));
        });
        Vector3f delta = new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()), (float) (to.getZ() - from.getZ()));
        int lands = t + flight;
        meteors.add(new Meteor(rock, from, to, lands, size));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animate(rock, cube(delta, tumble, size), flight - 2), 2L);
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 3f, 0.5f);
        world.playSound(from, Sound.ENTITY_ENDER_DRAGON_FLAP, 3f, 0.4f);
    }

    /** Fire trails behind every rock in the air; impacts when they land. */
    private void fly() {
        List<Meteor> landed = new ArrayList<>();
        for (Meteor m : meteors) {
            double f = clamp(1 - (m.lands() - t) / (double) (m.size() > 3 ? BIG_FLIGHT : FLIGHT));
            Location at = new Location(world, lerp(m.from().getX(), m.to().getX(), f), lerp(m.from().getY(), m.to().getY(), f), lerp(m.from().getZ(), m.to().getZ(), f));
            world.spawnParticle(Particle.FLAME, at, (int) (6 * m.size()), m.size() * 0.3, m.size() * 0.3, m.size() * 0.3, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, at, 2, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.LAVA, at, 1, 0.2, 0.2, 0.2, 0);
            if (t >= m.lands()) {
                landed.add(m);
            }
        }
        for (Meteor m : landed) {
            meteors.remove(m);
            impact(m);
        }
    }

    private void impact(Meteor m) {
        remove(m.rock());
        Location at = m.to().clone().add(0, 0.5, 0);
        boolean big = m.size() > 3;
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, big ? 6 : 1, big ? 2 : 0, 0.5, big ? 2 : 0, 0);
        world.spawnParticle(Particle.LAVA, at, big ? 80 : 20, 1, 0.5, 1, 0);
        world.spawnParticle(Particle.FLAME, at, big ? 200 : 50, 0.5, 0.3, 0.5, big ? 0.5 : 0.25);
        world.spawnParticle(Particle.LARGE_SMOKE, at, big ? 60 : 15, 1, 0.5, 1, 0.05);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3f, big ? 0.4f : 0.7f);
        world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 3f, big ? 0.5f : 0.9f);
        crater(m.to(), big ? 5 : 2);
        for (Player viewer : viewers()) {
            if (big) {
                whiteFlash(viewer, 6);
                sound(viewer, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.5f);
                sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                jolt(viewer, 0, 2, 5, 9, 14);
                effect(viewer, PotionEffectType.NAUSEA, 60, 0);
            } else if (viewer.getLocation().distanceSquared(at) < 20 * 20) {
                jolt(viewer, 0);
            }
        }
        if (big) {
            world.strikeLightningEffect(at);
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.fromRGB(0xFFB070));
        }
    }

    /** The ground around an impact turns to glowing rock and fire (fake blocks). */
    private void crater(Location center, int radius) {
        List<BlockState> states = new ArrayList<>();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.5) {
                    continue;
                }
                Block block = surface(cx + dx, cz + dz, center.getBlockY() - 1);
                if (block == null || block.getState(false) instanceof TileState || craters.contains(block)) {
                    continue;
                }
                BlockState state = block.getState();
                state.setType(d < radius * 0.45 ? Material.MAGMA_BLOCK : CRATER[random().nextInt(CRATER.length)]);
                states.add(state);
                craters.add(block);
                Block above = block.getRelative(0, 1, 0);
                if (above.getType().isAir() && d < radius * 0.7 && random().nextInt(3) == 0) {
                    BlockState fire = above.getState();
                    fire.setType(Material.FIRE);
                    states.add(fire);
                    craters.add(above);
                }
            }
        }
        fakes.send(states);
    }

    /** The craters cool from the oldest impact on, a few blocks per tick. */
    private void heal(int perTick) {
        if (healed >= craters.size()) {
            return;
        }
        int to = Math.min(craters.size(), healed + perTick);
        List<Block> blocks = craters.subList(healed, to);
        for (Block block : blocks) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, block.getLocation().add(0.5, 1.2, 0.5), 1, 0.1, 0.1, 0.1, 0.01);
        }
        fakes.restore(new ArrayList<>(blocks));
        healed = to;
        if (t % 4 == 0) {
            sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.6f + random().nextFloat() * 0.5f);
        }
    }

    private void shockwave(int k) {
        double radius = k * 1.3;
        Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(0xFF8A3D), 1.8f);
        Particles.circle(ground.clone().add(0, 0.4, 0), radius, 12 + (int) (radius * 6), 0,
                p -> world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, ember));
        if (k % 2 == 0) {
            Particles.circle(ground.clone().add(0, 0.6, 0), radius, 6 + (int) (radius * 2), 0.3,
                    p -> world.spawnParticle(Particle.FLAME, p, 1, 0, 0.1, 0, 0.03));
        }
    }
}
