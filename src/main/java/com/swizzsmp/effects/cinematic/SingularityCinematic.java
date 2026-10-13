package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.Particles;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private static final int SPIN_STEP = 5;
    private static final double HEIGHT = 7.5;
    private static final int TEAR_RADIUS = 14;
    private static final String CORE = "swizz:bh_core";
    private static final String RING = "swizz:bh_ring";
    private static final String DISK = "swizz:bh_disk";
    private static final String VOID = "swizz:misc/void";
    private static final Color[] MATTER = {Color.fromRGB(0xFF8A3D), Color.fromRGB(0xB06BFF), Color.fromRGB(0xFFD27F), Color.fromRGB(0xFF4FA3)};
    private static final Material[] BLAST = {Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_STONE, Material.AMETHYST_BLOCK, Material.MAGMA_BLOCK};

    private record Debris(BlockDisplay display, Block from, int born, Vector3f toCore) {
    }

    private final Location core;
    private final Set<UUID> audience = new HashSet<>();
    private final Set<UUID> darkSky = new HashSet<>();
    private final List<Block> ripped = new ArrayList<>();
    private final Set<Block> rippedSet = new HashSet<>();
    private final Set<UUID> rippedSentTo = new HashSet<>();
    private final Set<Block> rebuilt = new HashSet<>();
    private final List<Debris> debris = new ArrayList<>();
    private final List<BlockDisplay> blast = new ArrayList<>();
    private ItemDisplay coreDisplay;
    private ItemDisplay ringDisplay;
    private ItemDisplay disk;
    private ItemDisplay innerDisk;
    private double spin;

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
                audience.add(viewer.getUniqueId());
                effect(viewer, PotionEffectType.DARKNESS, 80, 0);
                sound(viewer, Sound.ENTITY_WARDEN_EMERGE, 1f, 0.7f);
            }
            lift(20, 0);
            coreDisplay = plane(core, CORE, Display.Billboard.CENTER, facing(0, 0.01f, 0));
            ringDisplay = plane(core, RING, Display.Billboard.CENTER, facing(0, 0.01f, 0.02f));
            disk = plane(core, DISK, Display.Billboard.FIXED, diskTransform(0, 0.01f, 0.30));
            innerDisk = plane(core, DISK, Display.Billboard.FIXED, diskTransform(0, 0.01f, -0.18));
        }
        if (t == 20) {
            hover();
        }
        if (t % SPIN_STEP == 0 && t > 0 && t <= DETONATE) {
            spinUp();
        }
        if (t == FORM) {
            sound(Sound.BLOCK_END_GATEWAY_SPAWN, 0.5f);
        }
        if (t == 30) {
            sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
        }
        if (t == DARK) {
            for (Player viewer : viewers()) {
                audience.add(viewer.getUniqueId());
                overlay(viewer, VOID, DETONATE - DARK);
                viewer.setPlayerTime(18000, false);
                darkSky.add(viewer.getUniqueId());
            }
        }
        if (t >= FORM && t < COLLAPSE) {
            matterStreams(t < 60 ? 3 : 6);
        }
        if (t >= TEAR_START && t < TEAR_END) {
            tear();
        }
        updateDebris();
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
            collapse();
        }
        if (t == SILENCE) {
            for (Player viewer : viewers()) {
                viewer.stopAllSounds();
            }
        }
        if (t == DETONATE) {
            detonate();
        }
        if (t > DETONATE && t <= DETONATE + 16) {
            shockwave(t - DETONATE);
        }
        if (t == DETONATE + 2) {
            release();
            launchBlast();
        }
        if (t == DETONATE + 6) {
            titles();
            resetSky();
        }
        if (t > DETONATE + 1) {
            rebuild((t - DETONATE - 1) * 0.9);
        }
        if (t == DETONATE + 40) {
            blast.forEach(this::remove);
            blast.clear();
        }
        if (t > DETONATE + 10 && t < END - 10 && t % 3 == 0) {
            world.spawnParticle(Particle.REVERSE_PORTAL, core, 20, 4, 3, 4, 0.01);
            world.spawnParticle(Particle.WHITE_ASH, core, 30, 6, 4, 6, 0);
        }
        if (t == DETONATE + 30) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f);
        }
    }

    // ── The black hole ─────────────────────────────────────────────────────────

    private void spinUp() {
        int next = t + SPIN_STEP;
        double grow = smooth((next - FORM) / 40.0);
        double fury = clamp((next - 100) / 65.0);
        double shrink = next >= COLLAPSE ? 1 - smooth((next - COLLAPSE) / 12.0) : 1;
        spin += Math.toRadians(SPIN_STEP * (3 + 9 * fury));
        float coreSize = (float) Math.max(0.01, (3.0 + 1.5 * fury) * grow * shrink);
        animate(coreDisplay, facing(0, coreSize, 0), SPIN_STEP);
        animate(ringDisplay, facing(-spin * 0.7, coreSize * 1.45f, 0.02f), SPIN_STEP);
        animate(disk, diskTransform(spin, (float) Math.max(0.01, 14 * grow * shrink), 0.30), SPIN_STEP);
        animate(innerDisk, diskTransform(-spin * 1.6, (float) Math.max(0.01, 8.5 * grow * shrink), -0.18), SPIN_STEP);
    }

    /** A flat disk tilted by {@code tilt} radians and spinning about its own axis. */
    private static Transformation diskTransform(double angle, float scale, double tilt) {
        return new Transformation(new Vector3f(),
                new Quaternionf().rotateX((float) tilt).rotateY((float) angle).rotateX((float) (-Math.PI / 2)),
                new Vector3f(scale), new Quaternionf());
    }

    /** Streaks of glowing matter falling in from every direction. */
    private void matterStreams(int count) {
        for (int i = 0; i < count; i++) {
            double theta = random().nextDouble(Math.PI * 2);
            double phi = Math.acos(random().nextDouble(-1, 1));
            double r = random().nextDouble(9, 16);
            Location from = core.clone().add(r * Math.sin(phi) * Math.cos(theta), r * Math.cos(phi), r * Math.sin(phi) * Math.sin(theta));
            Color color = MATTER[random().nextInt(MATTER.length)];
            world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0, new Particle.Trail(core, color, random().nextInt(14, 24)));
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, core, 6, 1.2, 1.2, 1.2, 0.05);
    }

    // ── Tearing the ground apart ───────────────────────────────────────────────

    /** Real surface blocks vanish (fake air) and copies of them lift off and spiral in. */
    private void tear() {
        int perTick = t < 80 ? 2 : 4;
        List<BlockState> hidden = new ArrayList<>();
        for (int i = 0; i < perTick; i++) {
            double angle = random().nextDouble(Math.PI * 2);
            double distance = random().nextDouble(3.5, TEAR_RADIUS);
            Block block = surface((int) Math.floor(ground.getX() + Math.cos(angle) * distance),
                    (int) Math.floor(ground.getZ() + Math.sin(angle) * distance), ground.getBlockY() - 1);
            if (block == null || rippedSet.contains(block) || standingOn(block) || block.getState(false) instanceof TileState) {
                continue;
            }
            rippedSet.add(block);
            ripped.add(block);
            BlockState air = block.getState();
            air.setType(Material.AIR);
            hidden.add(air);
            Location corner = block.getLocation();
            BlockDisplay copy = spawn(corner, BlockDisplay.class, d -> {
                d.setBlock(block.getBlockData());
                d.setTransformation(cube(new Vector3f(0.5f), new Quaternionf(), 1f));
            });
            Vector3f toCore = new Vector3f((float) (core.getX() - corner.getX()), (float) (core.getY() - corner.getY()),
                    (float) (core.getZ() - corner.getZ()));
            debris.add(new Debris(copy, block, t, toCore));
            if (i == 0) {
                world.playSound(corner, block.getBlockData().getSoundGroup().getBreakSound(), SoundCategory.MASTER, 1f, 0.6f);
            }
        }
        if (!hidden.isEmpty()) {
            for (Player viewer : viewers()) {
                rippedSentTo.add(viewer.getUniqueId());
                viewer.sendBlockChanges(hidden);
            }
        }
        if (t % 10 == 0) {
            sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f);
        }
    }

    /** Fake air under someone's feet would make their game think they are falling. */
    private boolean standingOn(Block block) {
        for (Player other : world.getPlayers()) {
            Location at = other.getLocation();
            double dy = at.getY() - (block.getY() + 1);
            if (Math.abs(at.getX() - block.getX() - 0.5) < 1.8 && Math.abs(at.getZ() - block.getZ() - 0.5) < 1.8 && dy > -0.5 && dy < 3) {
                return true;
            }
        }
        return false;
    }

    private void updateDebris() {
        debris.removeIf(d -> {
            int age = t - d.born();
            if (age == 2) {
                animate(d.display(), cube(new Vector3f(0.5f, 0.5f + (float) random().nextDouble(1.2, 2.6), 0.5f), randomRotation(0.6), 0.9f), 16);
            } else if (age == 18) {
                animate(d.display(), cube(d.toCore(), randomRotation(3), 0.05f), 22 + random().nextInt(8));
            } else if (age >= 50 || t >= DETONATE) {
                remove(d.display());
                return true;
            }
            return false;
        });
    }

    /** After the blast the ground comes back, ring by ring from the centre. */
    private void rebuild(double radius) {
        List<BlockState> back = new ArrayList<>();
        for (Block block : ripped) {
            if (rebuilt.contains(block)) {
                continue;
            }
            double dx = block.getX() + 0.5 - ground.getX();
            double dz = block.getZ() + 0.5 - ground.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                rebuilt.add(block);
                back.add(block.getState());
                world.spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.1, 0.5), 2, 0.2, 0.1, 0.2, 0.02);
            }
        }
        if (back.isEmpty()) {
            return;
        }
        sendTo(rippedSentTo, back);
        if (t % 3 == 0) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6f + random().nextFloat() * 0.6f);
        }
    }

    // ── Collapse and detonation ────────────────────────────────────────────────

    private void collapse() {
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f);
        sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f);
        sound(Sound.BLOCK_PORTAL_TRIGGER, 2f);
    }

    private void detonate() {
        world.strikeLightningEffect(core);
        world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, core, 1);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 3, 1.5, 1.5, 1.5, 0);
        world.spawnParticle(Particle.END_ROD, core, 250, 0.5, 0.5, 0.5, 0.8);
        for (Color color : MATTER) {
            world.spawnParticle(Particle.DUST, core, 40, 2, 2, 2, 0, new Particle.DustOptions(color, 2.4f));
        }
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            whiteFlash(viewer, 8);
            sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
            sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
            sound(viewer, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.7f);
            sound(viewer, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.4f);
            sound(viewer, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.5f);
            jolt(viewer, 0, 2, 5, 9);
        }
        for (int i = 0; i < 40; i++) {
            Material block = BLAST[i % BLAST.length];
            blast.add(spawn(flat(core.clone()), BlockDisplay.class, d -> {
                d.setBlock(block.createBlockData());
                d.setBrightness(FULL_BRIGHT);
                d.setTransformation(cube(new Vector3f(), new Quaternionf(), 0.6f));
            }));
        }
    }

    /** The swallowed matter is thrown back out in every direction. */
    private void launchBlast() {
        for (BlockDisplay shard : blast) {
            double theta = random().nextDouble(Math.PI * 2);
            double phi = Math.acos(random().nextDouble(-0.6, 1));
            double r = random().nextDouble(10, 18);
            Vector3f to = new Vector3f((float) (r * Math.sin(phi) * Math.cos(theta)), (float) (r * Math.cos(phi)),
                    (float) (r * Math.sin(phi) * Math.sin(theta)));
            animate(shard, cube(to, randomRotation(3), 0.05f), 24 + random().nextInt(10));
        }
    }

    /** An expanding shell of light, and a ring racing along the floor. */
    private void shockwave(int k) {
        double radius = k * 1.4;
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(0xF2E6FF), 1.6f);
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(0xB06BFF), 1.6f);
        int[] i = {0};
        Particles.sphere(core, radius, 40 + k * 8, false, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, i[0]++ % 2 == 0 ? white : purple));
        Particles.circle(ground.clone().add(0, 0.3, 0), radius, 12 + (int) (radius * 6), 0,
                p -> world.spawnParticle(Particle.CLOUD, p, 1, 0, 0.05, 0, 0.02));
    }

    private void titles() {
        Messages msg = plugin.messages();
        Title title = Title.title(msg.get("singularity.title", Messages.text("name", player.getName())),
                msg.get("singularity.subtitle", Messages.text("name", player.getName())),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(900)));
        for (Player viewer : viewers()) {
            viewer.showTitle(title);
        }
    }

    private void resetSky() {
        for (UUID id : darkSky) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                viewer.resetPlayerTime();
            }
        }
        darkSky.clear();
    }

    private static void sendTo(Set<UUID> players, List<BlockState> states) {
        for (UUID id : players) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                viewer.sendBlockChanges(states);
            }
        }
    }

    @Override
    protected void cleanup() {
        resetSky();
        List<BlockState> back = new ArrayList<>();
        for (Block block : ripped) {
            if (!rebuilt.contains(block)) {
                back.add(block.getState());
            }
        }
        if (!back.isEmpty()) {
            sendTo(rippedSentTo, back);
        }
        if (t < END) {
            for (UUID id : audience) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) {
                    clearOverlay(viewer);
                    clearEffect(viewer, PotionEffectType.DARKNESS);
                    clearEffect(viewer, PotionEffectType.NAUSEA);
                }
            }
        }
    }
}
