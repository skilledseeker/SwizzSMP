package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * "finale" (DESIGN.md §13.7): everything at once, in six acts over 75 seconds. Time stops
 * and the world freezes blue; the sigil blooms and the seven swizz orbit in a petal vortex;
 * reality fractures into the Matrix with agents and bullet time; a black hole devours the
 * code, the agents, the ground, the sigil and the petals; silence, black, and a then the detonation: a ring of lightning, a supernova, shards, the sunrise, the
 * rebuilt world, fireworks and a storm of petals as the player floats back down.
 */
final class FinaleCinematic extends Cinematic {

    // Act I · STILLNESS
    private static final int FREEZE = 20;
    private static final int FREEZE_TICKS = 15;
    // Act II · BLOOM
    private static final int ACT2 = 220;
    private static final int THAW_TICKS = 15;
    private static final int SIGIL = 230;
    private static final int GEMS = 270;
    private static final int GEM_GAP = 10;
    private static final int BEAMS = 350;
    // Act III · FRACTURE
    private static final int ACT3 = 560;
    private static final int[] TYPE_TICKS = {575, 607, 639};
    private static final String[] TYPE_KEYS = {"matrix.wake-up", "matrix.has-you", "matrix.knock"};
    private static final int KNOCK = 660;
    private static final int CONSTRUCT = 662;
    private static final int CONSTRUCT_TICKS = 34;
    private static final int RAIN = 664;
    private static final int[] AGENT_TICKS = {700, 706, 712, 718, 724};
    private static final int FIRE = 745;
    private static final int THE_ONE = 780;
    private static final int RECALL = 795;
    private static final int DISSOLVE = 809;
    // Act IV · DEVOUR
    private static final int ACT4 = 860;
    private static final int RAIN_RISE = 880;
    private static final int TEAR = 890;
    private static final int TEAR_END = 1120;
    private static final int SIGIL_PULL = 900;
    private static final int PETAL_STREAM = 950;
    private static final int GEM_PULL = 1000;
    private static final int COLLAPSE = 1130;
    // Act V · SILENCE
    private static final int ACT5 = 1140;
    // Act VI · ASCENSION
    private static final int ACT6 = 1200;
    private static final int REBLOOM = 1210;
    private static final int HALO = 1230;
    private static final int FIREWORKS = 1240;
    private static final int STORM = 1250;
    private static final int LAND = 1400;
    private static final int SIGIL_FADE = 1430;
    private static final int END = 1500;

    private static final double HOLE_HEIGHT = 7.5;
    private static final double FREEZE_RADIUS = 24;
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI"};
    /** The closing melody (semitones), one note every 6 ticks from {@link #MELODY_START}. */
    private static final int[] MELODY = {15, 19, 22, 24, 22, 19, 15, 22, 24, 27};
    private static final int MELODY_START = 1262;
    private static final Color PINK = Color.fromRGB(0xFF69B4);

    private final TimeFreeze freeze = new TimeFreeze(this);
    private final Afterimages echoes = new Afterimages(this, 8);
    private final SwizzOrbit gems = new SwizzOrbit(this);
    private final Petals petals = new Petals(this);
    private final FakeBlocks fakes = new FakeBlocks(this);
    private final Construct construct = new Construct(this, fakes);
    private final GroundTear tear = new GroundTear(this, fakes);
    private final CodeRain rain = new CodeRain(this);
    private final Agents agents = new Agents(this);
    private final Typewriter typewriter = new Typewriter(this);
    private final Shards shards = new Shards(this);
    private final Location holeAt;
    private Clock clock;
    private Sigil sigil;
    private BlackHole hole;
    private ItemDisplay halo;
    private long skyAt;

    FinaleCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
        this.holeAt = ground.clone().add(0, HOLE_HEIGHT, 0);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t < ACT2) {
            stillness();
        } else if (t < ACT3) {
            bloom();
        } else if (t < ACT4) {
            fracture();
        } else if (t < ACT5) {
            devour();
        } else if (t < ACT6) {
            silence();
        } else {
            ascension();
        }
        // Things that live across acts.
        if (clock != null) {
            clock.follow();
        }
        if (t >= GEMS && t < ACT6) {
            orbitGems();
        }
        if (t >= BEAMS && t < COLLAPSE && t % 2 == 0) {
            gems.beams(chest());
        }
        if (sigil != null && t % Sigil.SPIN_STEP == 0) {
            spinSigil();
        }
        if (halo != null) {
            halo.teleport(head());
        }
        tear.update();
    }

    // ── I · STILLNESS: time stops ──────────────────────────────────────────────

    private void stillness() {
        if (t == 0) {
            clock = new Clock(this);
            freeze.setTint(TimeStopCinematic.TINT, ACT2 - FREEZE);
            soundAll(Sound.BLOCK_BELL_USE, 1f, 0.5f);
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.DARKNESS, 50, 0);
            }
        }
        if (t == 5) {
            act(0, "finale.act-1", Sound.BLOCK_BELL_RESONATE, 0.6f);
        }
        if (t % 2 == 0) {
            clock.turn(t < FREEZE + FREEZE_TICKS ? 0.45 : 0, Clock.size(t + 2, 0, 12, ACT3, 1, t >= FREEZE));
        }
        if (t == FREEZE) {
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.7f);
                sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
                sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
                jolt(viewer, 0);
            }
            overlay(player, TimeStopCinematic.TINT, ACT2 - t);
            echoes.start();
        }
        if (t >= FREEZE && t < FREEZE + FREEZE_TICKS) {
            double radius = (t - FREEZE + 1) / (double) FREEZE_TICKS * FREEZE_RADIUS;
            TimeStopCinematic.shell(this, radius, TimeStopCinematic.ICE);
            freeze.sweep(radius);
        }
        frozenWorld();
        if (t >= 120 && t < 200 && t % 2 == 0) {
            petals.storm(8, 6, 3);
        }
        if (t == 180) {
            soundAll(Sound.BLOCK_BELL_USE, 1f, 0.5f);
        }
        heartbeat(40, 0.9f);
    }

    /** Clicks, frost and afterimages while time is stopped (acts I and II). */
    private void frozenWorld() {
        if (t < FREEZE + FREEZE_TICKS) {
            return;
        }
        freeze.sweep(FREEZE_RADIUS);
        if (t % 20 == 0) {
            world.playSound(head(), Sound.BLOCK_COMPARATOR_CLICK, 1.5f, (t / 20) % 2 == 0 ? 0.5f : 0.7f);
        }
        if (t % 6 == 0) {
            world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.5, 0), 25, 8, 4, 8, 0, new Particle.DustOptions(Color.fromRGB(0xDDF8FF), 0.8f));
            if (t < ACT2) {
                echoes.echo();
            }
        }
    }

    // ── II · BLOOM: the sigil and the seven ────────────────────────────────────

    private void bloom() {
        if (t == ACT2) {
            act(1, "finale.act-2", Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f);
            lift(30, 1);
            for (Player viewer : viewers()) {
                whiteFlash(viewer, 3);
                sound(viewer, Sound.BLOCK_BELL_RESONATE, 1f, 1f);
                sound(viewer, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
            }
        }
        // The sphere collapses and lets everyone go; the clock keeps floating, stopped.
        if (t > ACT2 && t <= ACT2 + THAW_TICKS) {
            double radius = FREEZE_RADIUS * (1 - (t - ACT2) / (double) THAW_TICKS);
            TimeStopCinematic.shell(this, radius, TimeStopCinematic.GOLD);
            freeze.thawOutside(radius);
            echoes.shatter(1);
        }
        if (t == ACT2 + THAW_TICKS) {
            freeze.thawAll();
        }
        if (t % 2 == 0) {
            clock.turn(0, Clock.size(t, 0, 12, ACT3, 1, true));
        }
        if (t == SIGIL) {
            sigil = new Sigil(this, ground);
            sound(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f);
        }
        if (t == ACT2 + 30) {
            hover();
        }
        if (t >= SIGIL + 10 && t % 4 == 0) {
            sigil.sparkle(ground);
        }
        if (t >= GEMS && t < GEMS + TYPES.length * GEM_GAP && (t - GEMS) % GEM_GAP == 0) {
            gems.raise((t - GEMS) / GEM_GAP, ground, 5.5);
        }
        if (t >= 300) {
            petals.vortex(chest(), lerp(2.8, 1.6, (t - 300) / 260.0), 3.2, 3, 4);
        }
        if (t == 360) {
            sound(Sound.BLOCK_PORTAL_TRIGGER, 1.2f);
        }
        if (t == 440) {
            sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f);
        }
        if (t == 520) {
            sound(Sound.ITEM_TRIDENT_RIPTIDE_3, 0.7f);
        }
        if (t >= 460 && t % 2 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, chest(), 4 + (t - 460) / 8, 0.5, 0.8, 0.5, 0.15);
        }
        if (t >= 540 && t % 2 == 0) {
            clock.turn(-0.9, Clock.SIZE);
            world.playSound(head(), Sound.BLOCK_COMPARATOR_CLICK, 1.5f, 1.8f);
        }
        heartbeat(t < 420 ? 30 : 20, 0.9f + (t - ACT2) / 800f);
    }

    // ── III · FRACTURE: reality cracks into the Matrix ─────────────────────────

    private void fracture() {
        if (t == ACT3) {
            clock.shatter();
            for (Player viewer : viewers()) {
                glitch(viewer, 3);
                jolt(viewer, 0, 2, 6);
                sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
            }
        }
        if (t > ACT3 && t < ACT3 + 6 && t % 2 == 0) {
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
            }
            echoes.shatter(3);
        }
        if (t == ACT3 + 8) {
            act(2, "finale.act-3", Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f);
            for (Player viewer : viewers()) {
                overlay(viewer, MatrixCinematic.CRT, KNOCK - t);
            }
        }
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            int end = i + 1 < TYPE_TICKS.length ? TYPE_TICKS[i + 1] : KNOCK;
            if (t >= TYPE_TICKS[i] && t < end) {
                typewriter.type(TYPE_KEYS[i], TYPE_TICKS[i], Typewriter.GREEN);
            }
        }
        if (t == KNOCK) {
            typewriter.clear();
            for (Player viewer : viewers()) {
                glitch(viewer, 3);
                jolt(viewer, 0, 2, 6, 10);
                sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
                sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
            }
            construct.plan(ground, 16);
        }
        if (t > KNOCK && t < KNOCK + 8 && t % 2 == 0) {
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
            }
        }
        if (t >= CONSTRUCT && t <= CONSTRUCT + CONSTRUCT_TICKS) {
            construct.spread((t - CONSTRUCT) / (double) CONSTRUCT_TICKS * 18);
        }
        if (t >= RAIN) {
            if (t >= KNOCK + 8) {
                rain.screens();
            }
            rain.fall(2, 5);
        }
        for (int i = 0; i < AGENT_TICKS.length; i++) {
            if (t == AGENT_TICKS[i]) {
                agents.multiply(1 << i);
            }
        }
        if (t > AGENT_TICKS[0] && t < DISSOLVE) {
            agents.jitter();
        }
        if (t == FIRE) {
            agents.fire(chest());
        }
        if (t > FIRE && t <= FIRE + Agents.FLIGHT && t % 3 == 0) {
            agents.ripples();
        }
        if (t == FIRE + Agents.FLIGHT) {
            agents.stopSound();
        }
        if (t == THE_ONE) {
            title("matrix.the-one", null, 200, 1400, 300);
            sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7f);
        }
        if (t == RECALL) {
            agents.recallBullets(DISSOLVE - RECALL);
        }
        if (t == DISSOLVE) {
            agents.removeBullets();
            agents.dissolve(16);
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
                sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.2f);
                jolt(viewer, 0);
            }
        }
        heartbeat(15, 1.1f);
    }

    // ── IV · DEVOUR: the black hole ────────────────────────────────────────────

    private void devour() {
        if (t == ACT4) {
            act(3, "finale.act-4", Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
            hole = new BlackHole(this, holeAt);
            hole.openingSounds();
            rain.setTarget(holeAt);
            tear.setLook(block -> construct.shownLook(block) != null ? construct.shownLook(block) : block.getBlockData());
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_WARDEN_EMERGE, 1f, 0.7f);
                effect(viewer, PotionEffectType.DARKNESS, 60, 0);
                overlay(viewer, SingularityCinematic.VOID, ACT5 - t);
                sky(viewer, 18000);
                weather(viewer, WeatherType.DOWNFALL);
            }
        }
        if (t % BlackHole.SPIN_STEP == 0) {
            int next = t + BlackHole.SPIN_STEP;
            hole.spin(smooth((next - ACT4) / 40.0), clamp((next - 940) / 190.0), next >= COLLAPSE ? 1 - smooth((next - COLLAPSE) / 12.0) : 1);
        }
        if (t >= ACT4 + 10 && t < COLLAPSE) {
            hole.streams(t < 950 ? 3 : 6);
        }
        if (t < RAIN_RISE) {
            rain.fall(2, 5);
        } else if (t == RAIN_RISE) {
            rain.liftAll(14);
        } else if (t < RAIN_RISE + 20) {
            rain.rise(3);
        } else if (t == RAIN_RISE + 20) {
            rain.removeAll();
        }
        if (t >= TEAR && t < TEAR_END) {
            tear.tear(t < 980 ? 2 : 4, 3.5, 14, holeAt);
            if (t % 10 == 0) {
                sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f);
            }
        }
        if (t >= SIGIL_PULL && t <= SIGIL_PULL + 100) {
            double f = smooth((t - SIGIL_PULL) / 100.0);
            sigil.moveTo(new Location(world, lerp(ground.getX(), holeAt.getX(), f), lerp(ground.getY(), holeAt.getY(), f), lerp(ground.getZ(), holeAt.getZ(), f)));
        }
        if (t >= PETAL_STREAM && t < COLLAPSE) {
            petals.stream(holeAt, 4);
        }
        if (t >= 1040 && t < COLLAPSE && t % 15 == 0) {
            world.strikeLightningEffect(around(ground, random().nextDouble(Math.PI * 2), random().nextDouble(18, 26), ground.getY()));
            soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f + random().nextFloat() * 0.6f);
        }
        if (t >= 1000 && t % 60 == 0) {
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
            }
        }
        if (t >= 1000 && t % 60 == 2) {
            for (Player viewer : viewers()) {
                overlay(viewer, SingularityCinematic.VOID, ACT5 - t);
            }
        }
        if (t == 1080) {
            for (Player viewer : viewers()) {
                effect(viewer, PotionEffectType.NAUSEA, 70, 0);
            }
        }
        if (t >= 1060 && t % 10 == 0) {
            for (Player viewer : viewers()) {
                jolt(viewer, 0);
            }
        }
        if (t == 1100) {
            sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
        }
        if (t == 1120) {
            sound(Sound.BLOCK_PORTAL_TRIGGER, 2f);
        }
        if (t == COLLAPSE) {
            hole.collapseSounds();
            gems.resize(0.3f, 10);
        }
        if (t == ACT5 - 2) {
            sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f);
        }
        if (t % 40 == 0) {
            sound(Sound.BLOCK_BEACON_AMBIENT, 0.5f);
        }
        heartbeat(t < 1060 ? 10 : 6, 1.2f);
    }

    // ── V · SILENCE ────────────────────────────────────────────────────────────

    private void silence() {
        if (t == ACT5) {
            for (Player viewer : viewers()) {
                viewer.stopAllSounds();
                blackout(viewer, ACT6 - ACT5);
            }
            construct.restore();
            tear.clearDebris();
            rain.removeAll();
            typewriter.clear();
        }
        if (t == ACT5 + 2) {
            hole.removeAll();
        }
        if (t == 1150 || t == 1165 || t == 1180 || t == 1192 || t == 1195) {
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1f);
        }
        if (t == 1196) {
            soundAll(Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f);
        }
    }

    // ── VI · ASCENSION ─────────────────────────────────────────────────────────

    private void ascension() {
        Location chest = chest();
        if (t == ACT6) {
            hole.detonate();
            gems.converge(chest);
            world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.END_ROD, chest, 150, 0.3, 0.3, 0.3, 0.6);
            world.spawnParticle(Particle.FIREWORK, chest, 100, 0.3, 0.3, 0.3, 0.45);
            shards.spawn(chest, Shards.ELEMENTS, TYPES.length * 6, 0.5f);
            shards.spawn(holeAt, Shards.COSMIC, 40, 0.6f);
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ITEM_TOTEM_USE, 1f, 1f);
                sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                for (int note : AuraCinematic.CHORD) {
                    sound(viewer, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch(note));
                }
                resetWeather(viewer);
            }
            sigil.moveTo(ground);
        }
        if (t >= ACT6 && t < ACT6 + 8) {
            world.strikeLightningEffect(around(ground, (t - ACT6) * Math.PI / 4, 7, ground.getY()));
        }
        if (t > ACT6 && t <= ACT6 + 22) {
            hole.shockwave(t - ACT6);
            gems.rainbowRing(t - ACT6);
        }
        if (t == ACT6 + 2) {
            shards.launch(9, 16, false, -0.5, 3.5);
        }
        if (t > ACT6 + 1 && !tear.fullyRebuilt()) {
            tear.rebuild((t - ACT6 - 1) * 0.9);
        }
        if (t == ACT6 + 8) {
            act(5, "finale.act-6", Sound.BLOCK_END_PORTAL_SPAWN, 1.4f);
            for (Player viewer : viewers()) {
                overlay(viewer, AuraCinematic.AURA_TEXTURE, 70);
            }
        }
        if (t == 1268) {
            title("finale.title", "finale.subtitle", 150, 3000, 900);
        }
        if (t == HALO) {
            halo = plane(head(), Sigil.INNER_MODEL, Display.Billboard.FIXED, flatTransform(0, 0.01f, 0f));
        }
        if (halo != null && t % Sigil.SPIN_STEP == 0) {
            animate(halo, flatTransform(-t * 0.08, t + Sigil.SPIN_STEP < HALO + 5 || t + Sigil.SPIN_STEP >= END - 20 ? 0.01f : 1.1f, 0f), Sigil.SPIN_STEP);
        }
        if (t >= FIREWORKS && t <= FIREWORKS + 100 && (t - FIREWORKS) % 8 == 0) {
            firework();
        }
        if (t >= STORM && t < STORM + 200) {
            petals.storm(14, 10, 10);
        }
        if (t == ACT6 + 44) {
            shards.removeAll();
        }
        if (t >= MELODY_START && t < MELODY_START + MELODY.length * 6 && (t - MELODY_START) % 6 == 0) {
            int note = MELODY[(t - MELODY_START) / 6];
            sound(Sound.BLOCK_NOTE_BLOCK_CHIME, pitch(note));
            sound(Sound.BLOCK_NOTE_BLOCK_BELL, pitch(note));
        }
        sunrise();
        if (t == LAND) {
            release();
        }
        if (t == 1440) {
            title(null, "finale.outro", 400, 2500, 800);
        }
        if (t == 1460) {
            soundAll(Sound.BLOCK_BELL_USE, 1f, 1f);
        }
        if (t > ACT6 + 30 && t < END - 20 && t % 3 == 0) {
            world.spawnParticle(Particle.END_ROD, chest, 2, 3, 2, 3, 0.01);
        }
    }

    /** From midnight to dawn over 11 seconds, then back to the real time of day. */
    private void sunrise() {
        if (t <= 1420) {
            skyAt = 18000 + (t - ACT6) * 27L;
            for (Player viewer : viewers()) {
                sky(viewer, skyAt);
            }
        } else if (t < 1490) {
            long real = world.getTime();
            long delta = Math.floorMod(real - skyAt, 24000L);
            if (delta > 12000) {
                delta -= 24000;
            }
            long time = skyAt + Math.round(delta * (t - 1420) / 70.0);
            for (Player viewer : viewers()) {
                sky(viewer, time);
            }
        } else if (t == 1490) {
            for (Player viewer : viewers()) {
                resetSky(viewer);
            }
        }
    }

    /** A real firework (no damage: the plugin cancels it) in the swizz colours. */
    private void firework() {
        Location at = around(ground, random().nextDouble(Math.PI * 2), random().nextDouble(5, 11), ground.getY() + random().nextDouble(5, 11));
        FireworkEffect.Type type = List.of(FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST).get(random().nextInt(3));
        Color a = TYPES[random().nextInt(TYPES.length)].primaryColor();
        Color b = random().nextInt(3) == 0 ? PINK : TYPES[random().nextInt(TYPES.length)].primaryColor();
        spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(type).withColor(a, b).withFade(Color.WHITE).trail(random().nextBoolean()).flicker(true).build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.setTicksToDetonate(1);
        });
    }

    // ── Across the acts ────────────────────────────────────────────────────────

    private void orbitGems() {
        double radius;
        double speed;
        Location center = chest();
        if (t < ACT3) {
            double charge = clamp((t - 420) / 140.0);
            radius = lerp(5.5, 4, charge);
            speed = 1.5 + 10 * charge * charge;
        } else if (t < GEM_PULL) {
            radius = 4;
            speed = 9;
        } else if (t < COLLAPSE) {
            double f = smooth((t - GEM_PULL) / 130.0);
            radius = lerp(4, 1.5, f);
            speed = 12 + 8 * f;
            center = new Location(world, lerp(center.getX(), holeAt.getX(), f * 0.4), lerp(center.getY(), holeAt.getY(), f * 0.4), lerp(center.getZ(), holeAt.getZ(), f * 0.4));
        } else {
            radius = lerp(1.5, 0.4, (t - COLLAPSE) / 10.0);
            speed = 24;
        }
        gems.orbit(center, radius, speed);
    }

    private void spinSigil() {
        int next = t + Sigil.SPIN_STEP;
        float outer;
        float inner;
        double charge;
        if (next < SIGIL_PULL) {
            outer = Sigil.unfold(next, SIGIL, 40, Sigil.OUTER_SIZE) + (next > SIGIL + 40 ? (float) Math.sin(next / 8.0) * 0.4f : 0);
            inner = Sigil.unfold(next, SIGIL + 10, 30, Sigil.INNER_SIZE);
            charge = clamp((next - 420) / 140.0);
        } else if (next < REBLOOM) {
            outer = Sigil.fold(next, SIGIL_PULL, 100, Sigil.OUTER_SIZE);
            inner = Sigil.fold(next, SIGIL_PULL, 90, Sigil.INNER_SIZE);
            charge = 1;
        } else if (next < SIGIL_FADE) {
            outer = Sigil.unfold(next, REBLOOM, 30, Sigil.OUTER_SIZE);
            inner = Sigil.unfold(next, REBLOOM + 5, 25, Sigil.INNER_SIZE);
            charge = 1 - smooth((next - REBLOOM) / 150.0);
        } else {
            outer = Sigil.fold(next, SIGIL_FADE, 50, Sigil.OUTER_SIZE);
            inner = Sigil.fold(next, SIGIL_FADE, 45, Sigil.INNER_SIZE);
            charge = 0;
        }
        sigil.spin(charge, outer, inner);
    }

    private void heartbeat(int every, float pitch) {
        if (t % every == 0) {
            sound(Sound.ENTITY_WARDEN_HEARTBEAT, pitch);
        }
    }

    /** The act card: the act's name big, its number small, and a sting. */
    private void act(int index, String nameKey, Sound sting, float pitch) {
        title(nameKey, "finale.act-number", 300, 1800, 600, Messages.text("numeral", NUMERALS[index]));
        soundAll(sting, 1f, pitch);
    }

    private void glitch(Player viewer, int ticks) {
        overlay(viewer, MatrixCinematic.GLITCH[random().nextInt(MatrixCinematic.GLITCH.length)], ticks);
        sound(viewer, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.5f + random().nextFloat() * 1.5f);
    }
}
