package com.swizzsmp.effects.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The ground tearing apart: real surface blocks vanish for the watchers (fake air) while
 * copies of them lift off and spiral into a target. {@link #rebuild} brings the ground back
 * ring by ring from the centre.
 */
final class GroundTear extends Layer {

    private record Debris(BlockDisplay display, int born, Vector3f toTarget) {
    }

    private final FakeBlocks fakes;
    private final List<Block> ripped = new ArrayList<>();
    private final Set<Block> rippedSet = new HashSet<>();
    private final Set<Block> rebuilt = new HashSet<>();
    private final List<Debris> debris = new ArrayList<>();
    private Function<Block, BlockData> look = Block::getBlockData;

    GroundTear(Cinematic host, FakeBlocks fakes) {
        super(host);
        this.fakes = fakes;
    }

    /** How a flying copy looks (default: like the real block). */
    void setLook(Function<Block, BlockData> look) {
        this.look = look;
    }

    /** Rips up {@code count} random surface blocks between the radii and sends them flying to {@code target}. */
    void tear(int count, double minRadius, double maxRadius, Location target) {
        List<BlockState> hidden = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = Cinematic.random().nextDouble(Math.PI * 2);
            double distance = Cinematic.random().nextDouble(minRadius, maxRadius);
            Block block = host.surface((int) Math.floor(host.ground.getX() + Math.cos(angle) * distance),
                    (int) Math.floor(host.ground.getZ() + Math.sin(angle) * distance), host.ground.getBlockY() - 1);
            if (block == null || rippedSet.contains(block) || standingOn(block) || block.getState(false) instanceof TileState) {
                continue;
            }
            rippedSet.add(block);
            ripped.add(block);
            BlockState air = block.getState();
            air.setType(Material.AIR);
            hidden.add(air);
            Location corner = block.getLocation();
            BlockData data = look.apply(block);
            BlockDisplay copy = host.spawn(corner, BlockDisplay.class, d -> {
                d.setBlock(data);
                d.setTransformation(Cinematic.cube(new Vector3f(0.5f), new Quaternionf(), 1f));
            });
            Vector3f toTarget = new Vector3f((float) (target.getX() - corner.getX()), (float) (target.getY() - corner.getY()),
                    (float) (target.getZ() - corner.getZ()));
            debris.add(new Debris(copy, host.t, toTarget));
            if (i == 0) {
                host.world.playSound(corner, block.getBlockData().getSoundGroup().getBreakSound(), SoundCategory.MASTER, 1f, 0.6f);
            }
        }
        fakes.send(hidden);
    }

    /** Fake air under someone's feet would make their game think they are falling. */
    private boolean standingOn(Block block) {
        for (Player other : host.world.getPlayers()) {
            Location at = other.getLocation();
            double dy = at.getY() - (block.getY() + 1);
            if (Math.abs(at.getX() - block.getX() - 0.5) < 1.8 && Math.abs(at.getZ() - block.getZ() - 0.5) < 1.8 && dy > -0.5 && dy < 3) {
                return true;
            }
        }
        return false;
    }

    /** Copies lift, tumble, then fly into the target and vanish. */
    void update() {
        debris.removeIf(d -> {
            int age = host.t - d.born();
            if (age == 2) {
                Cinematic.animate(d.display(), Cinematic.cube(new Vector3f(0.5f, 0.5f + (float) Cinematic.random().nextDouble(1.2, 2.6), 0.5f),
                        Cinematic.randomRotation(0.6), 0.9f), 16);
            } else if (age == 18) {
                Cinematic.animate(d.display(), Cinematic.cube(d.toTarget(), Cinematic.randomRotation(3), 0.05f), 22 + Cinematic.random().nextInt(8));
            } else if (age >= 50) {
                host.remove(d.display());
                return true;
            }
            return false;
        });
    }

    /** Removes every flying copy at once (the moment everything is swallowed). */
    void clearDebris() {
        debris.forEach(d -> host.remove(d.display()));
        debris.clear();
    }

    /** Brings back every ripped block within {@code radius} of the centre. */
    void rebuild(double radius) {
        List<Block> back = new ArrayList<>();
        for (Block block : ripped) {
            if (rebuilt.contains(block)) {
                continue;
            }
            double dx = block.getX() + 0.5 - host.ground.getX();
            double dz = block.getZ() + 0.5 - host.ground.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                rebuilt.add(block);
                back.add(block);
                host.world.spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.1, 0.5), 2, 0.2, 0.1, 0.2, 0.02);
            }
        }
        if (back.isEmpty()) {
            return;
        }
        fakes.restore(back);
        if (host.t % 3 == 0) {
            host.sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6f + Cinematic.random().nextFloat() * 0.6f);
        }
    }

    boolean fullyRebuilt() {
        return rebuilt.size() == ripped.size();
    }

    @Override
    void stop() {
        clearDebris();
        fakes.restore(ripped);
    }
}
