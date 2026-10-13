package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.swizz.SwizzType;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * "aura" (DESIGN.md §13.7): a glowing sigil unfolds under the player as they rise, the seven
 * swizz orbit faster and faster while streaming their colours into them, then collapse in a
 * white flash, lightning and a shockwave of element blocks, with a rainbow energy border.
 */
final class AuraCinematic extends Cinematic {

    private static final int SIGIL_START = 10;
    private static final int GEMS_START = 50;
    private static final int GEM_GAP = 8;
    private static final int BEAMS_START = 95;
    private static final int CHARGE_START = 100;
    private static final int CONVERGE = 180;
    private static final int HALO = 200;
    private static final int FADE = 225;
    private static final int LAND = 232;
    private static final int END = 280;
    private static final int SPIN_STEP = 5;
    private static final double ORBIT_RADIUS = 5.5;
    private static final float OUTER_SIZE = 16f;
    private static final float INNER_SIZE = 8.5f;
    private static final String OUTER_MODEL = "swizz:sigil_outer";
    private static final String INNER_MODEL = "swizz:sigil_inner";
    private static final String AURA_TEXTURE = "swizz:misc/aura";
    /** Shockwave block for each swizz, in {@link SwizzType} order. */
    private static final Material[] SHARDS = {Material.LIGHT_BLUE_STAINED_GLASS, Material.AMETHYST_BLOCK,
            Material.PACKED_MUD, Material.EMERALD_BLOCK, Material.BLUE_ICE, Material.GOLD_BLOCK, Material.SCULK};
    /** A rising pentatonic run, one note per swizz. */
    private static final int[] NOTES = {5, 7, 10, 12, 15, 17, 19};
    /** A major, played when the seven become one. */
    private static final int[] CHORD = {15, 19, 22};

    private final ItemDisplay[] gems = new ItemDisplay[TYPES.length];
    private final List<BlockDisplay> shards = new ArrayList<>();
    private ItemDisplay outer;
    private ItemDisplay inner;
    private ItemDisplay halo;
    private double orbitAngle;
    private double outerAngle;
    private double innerAngle;

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
            outer = plane(ground, OUTER_MODEL, Display.Billboard.FIXED, flatTransform(0, 0.01f, 0.03f));
            inner = plane(ground, INNER_MODEL, Display.Billboard.FIXED, flatTransform(0, 0.01f, 0.06f));
        }
        if (t == 30) {
            hover();
        }
        if (t % SPIN_STEP == 0 && t > 0) {
            spin();
        }
        if (t == SIGIL_START) {
            sound(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f);
        }
        if (t >= 40 && t < CONVERGE && t % 4 == 0) {
            Particles.circle(ground.clone().add(0, 0.2, 0), 7.4, 28, t * 0.05,
                    p -> world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0.1, 0, 0.02));
            world.spawnParticle(Particle.ENCHANT, ground.clone().add(0, 1.5, 0), 20, 3, 0.5, 3, 1.0);
        }
        if (t < CONVERGE && t % (t < 140 ? 20 : 10) == 0) {
            sound(Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f + t / 400f);
        }
        if (t >= GEMS_START && t < GEMS_START + TYPES.length * GEM_GAP && (t - GEMS_START) % GEM_GAP == 0) {
            raiseGem((t - GEMS_START) / GEM_GAP);
        }
        if (t >= GEMS_START && t < CONVERGE) {
            moveGems(chest);
        }
        if (t >= BEAMS_START && t < CONVERGE && t % 2 == 0) {
            beams(chest);
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
        if (t == CONVERGE + 2) {
            launchShards();
        }
        if (t > CONVERGE && t <= CONVERGE + 20) {
            shockwave(t - CONVERGE);
        }
        if (t == CONVERGE + 7) {
            for (Player viewer : viewers()) {
                overlay(viewer, AURA_TEXTURE, 70);
                effect(viewer, PotionEffectType.NAUSEA, 90, 0);
            }
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f);
        }
        if (t == CONVERGE + 9) {
            titles();
        }
        if (t == HALO) {
            halo = plane(head(), INNER_MODEL, Display.Billboard.FIXED, flatTransform(0, 0.01f, 0f));
        }
        if (halo != null) {
            halo.teleport(head());
        }
        if (t == CONVERGE + 40) {
            shards.forEach(this::remove);
            shards.clear();
        }
        if (t == FADE) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
        }
        if (t == LAND) {
            release();
        }
        if (t == 245) {
            for (int note : CHORD) {
                sound(Sound.BLOCK_NOTE_BLOCK_CHIME, pitch(note));
            }
        }
    }

    private void spin() {
        double charge = charge();
        outerAngle += Math.toRadians(SPIN_STEP * (1.2 + 4 * charge));
        innerAngle -= Math.toRadians(SPIN_STEP * (2.4 + 7 * charge));
        int next = t + SPIN_STEP;
        animate(outer, flatTransform(outerAngle, outerScale(next), 0.03f), SPIN_STEP);
        animate(inner, flatTransform(innerAngle, innerScale(next), 0.06f), SPIN_STEP);
        animate(halo, flatTransform(innerAngle * 1.5, next < HALO + 5 || next >= END - 8 ? 0.01f : 1.1f, 0f), SPIN_STEP);
    }

    private void raiseGem(int i) {
        SwizzType type = TYPES[i];
        double angle = orbitAngle + i * 2 * Math.PI / TYPES.length;
        Location at = new Location(world, ground.getX() + Math.cos(angle) * ORBIT_RADIUS, ground.getY() + 0.3,
                ground.getZ() + Math.sin(angle) * ORBIT_RADIUS);
        ItemDisplay gem = spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(plugin.items().swizzItem(type));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(FULL_BRIGHT);
            d.setGlowing(true);
            d.setGlowColorOverride(type.primaryColor());
            d.setTeleportDuration(2);
            d.setTransformation(scaled(0.01f));
        });
        gems[i] = gem;
        Bukkit.getScheduler().runTaskLater(plugin, () -> animate(gem, scaled(1.4f), 8), 2L);
        world.spawnParticle(Particle.DUST, at, 30, 0.3, 0.6, 0.3, 0, new Particle.DustOptions(type.primaryColor(), 1.6f));
        sound(Sound.BLOCK_NOTE_BLOCK_BELL, pitch(NOTES[i]));
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, pitch(NOTES[i]));
    }

    /** Orbit the player, faster and tighter as the charge builds, then collapse into them. */
    private void moveGems(Location chest) {
        double charge = charge();
        orbitAngle += Math.toRadians(1.5 + 16 * charge * charge);
        double radius = t < 130 ? ORBIT_RADIUS
                : t < 170 ? lerp(ORBIT_RADIUS, 3, (t - 130) / 40.0)
                : lerp(3, 0, (t - 170) / 10.0);
        if (t == 172) {
            for (ItemDisplay gem : gems) {
                animate(gem, scaled(0.3f), 8);
            }
        }
        for (int i = 0; i < gems.length; i++) {
            if (gems[i] == null) {
                continue;
            }
            int born = GEMS_START + i * GEM_GAP;
            double rise = smooth((t - born) / 20.0);
            double angle = orbitAngle + i * 2 * Math.PI / gems.length;
            double y = lerp(ground.getY() + 0.3, chest.getY() + Math.sin(t / 6.0 + i) * 0.25, rise);
            gems[i].teleport(new Location(world, chest.getX() + Math.cos(angle) * radius, y, chest.getZ() + Math.sin(angle) * radius));
        }
    }

    private void beams(Location chest) {
        for (int i = 0; i < gems.length; i++) {
            if (gems[i] == null || t < GEMS_START + i * GEM_GAP + 20) {
                continue;
            }
            Location from = gems[i].getLocation();
            Color color = TYPES[i].primaryColor();
            world.spawnParticle(Particle.TRAIL, from, 2, 0.08, 0.08, 0.08, 0, new Particle.Trail(chest, color, 12));
            world.spawnParticle(Particle.DUST, from, 2, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(color, 1.4f));
        }
    }

    private void converge(Location chest) {
        for (int i = 0; i < gems.length; i++) {
            remove(gems[i]);
            gems[i] = null;
        }
        world.strikeLightningEffect(player.getLocation());
        world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, chest, 1);
        world.spawnParticle(Particle.END_ROD, chest, 150, 0.3, 0.3, 0.3, 0.6);
        world.spawnParticle(Particle.FIREWORK, chest, 100, 0.3, 0.3, 0.3, 0.45);
        for (SwizzType type : TYPES) {
            world.spawnParticle(Particle.DUST, chest, 25, 1.2, 1.2, 1.2, 0, new Particle.DustOptions(type.primaryColor(), 2.2f));
        }
        for (Player viewer : viewers()) {
            whiteFlash(viewer, 7);
            sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
            sound(viewer, Sound.ITEM_TOTEM_USE, 1f, 1f);
            sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            for (int note : CHORD) {
                sound(viewer, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch(note));
            }
            jolt(viewer, 0, 3, 7);
        }
        for (int i = 0; i < TYPES.length * 6; i++) {
            Material block = SHARDS[i % TYPES.length];
            shards.add(spawn(chest, BlockDisplay.class, d -> {
                d.setBlock(block.createBlockData());
                d.setBrightness(FULL_BRIGHT);
                d.setTransformation(cube(new Vector3f(), new Quaternionf(), 0.5f));
            }));
        }
    }

    /** Each shard flies outward, spinning and shrinking away. */
    private void launchShards() {
        for (int i = 0; i < shards.size(); i++) {
            double angle = 2 * Math.PI * i / shards.size() + random().nextDouble(-0.1, 0.1);
            double distance = random().nextDouble(9, 15);
            Vector3f to = new Vector3f((float) (Math.cos(angle) * distance), (float) random().nextDouble(-0.5, 3.5),
                    (float) (Math.sin(angle) * distance));
            animate(shards.get(i), cube(to, randomRotation(3), 0.06f), 26 + random().nextInt(10));
        }
    }

    /** A ring of every swizz colour racing out along the floor. */
    private void shockwave(int k) {
        double radius = k * 0.85;
        int[] next = {k};
        Particles.circle(ground.clone().add(0, 0.3, 0), radius, 10 + (int) (radius * 7), 0, p -> world.spawnParticle(Particle.DUST, p, 1,
                0.05, 0.05, 0.05, 0, new Particle.DustOptions(TYPES[next[0]++ % TYPES.length].primaryColor(), 1.8f)));
        if (k % 2 == 0) {
            Particles.circle(ground.clone().add(0, 0.5, 0), radius, 6 + (int) (radius * 2), 0.3,
                    p -> world.spawnParticle(Particle.FIREWORK, p, 1, 0, 0.05, 0, 0.02));
        }
    }

    private void titles() {
        Messages msg = plugin.messages();
        Title title = Title.title(msg.get("aura.title", Messages.text("name", player.getName())),
                msg.get("aura.subtitle", Messages.text("name", player.getName())),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2600), Duration.ofMillis(900)));
        for (Player viewer : viewers()) {
            viewer.showTitle(title);
        }
    }

    /** 0 → 1 while the seven build up, falling back to 0 after the convergence. */
    private double charge() {
        if (t <= CONVERGE) {
            return clamp((t - CHARGE_START) / (double) (CONVERGE - CHARGE_START));
        }
        return clamp(1 - (t - CONVERGE) / 40.0);
    }

    private float outerScale(int at) {
        if (at < SIGIL_START) {
            return 0.01f;
        }
        if (at < 50) {
            return (float) Math.max(0.01, OUTER_SIZE * smooth((at - SIGIL_START) / 40.0));
        }
        if (at >= CONVERGE && at < CONVERGE + 8) {
            return OUTER_SIZE * 1.2f;
        }
        if (at >= FADE) {
            return (float) Math.max(0.01, OUTER_SIZE * (1 - smooth((at - FADE) / 35.0)));
        }
        return OUTER_SIZE + (float) Math.sin(at / 8.0) * 0.4f;
    }

    private float innerScale(int at) {
        if (at < SIGIL_START + 10) {
            return 0.01f;
        }
        if (at < 50) {
            return (float) Math.max(0.01, INNER_SIZE * smooth((at - SIGIL_START - 10) / 30.0));
        }
        if (at >= FADE - 5) {
            return (float) Math.max(0.01, INNER_SIZE * (1 - smooth((at - FADE + 5) / 35.0)));
        }
        return INNER_SIZE;
    }
}
