package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * "matrix" (DESIGN.md §13.7): the player escapes the Matrix. Screens become a green CRT
 * that types "Wake up…", a black cat walks past twice (déjà vu), the world derezzes into a
 * black-and-green construct (fake blocks only the viewers see) under falling green code,
 * black-suited copies of the player multiply and fire bullets that stop in mid-air, then
 * liquid chrome swallows the screen and the player wakes up in "the real world".
 */
final class MatrixCinematic extends Cinematic {

    private static final int CAT_1 = 80;
    private static final int CAT_2 = 104;
    private static final int CAT_WALK = 20;
    private static final int KNOCK = 135;
    private static final int CONSTRUCT_START = 138;
    private static final int CONSTRUCT_TICKS = 34;
    private static final int RAIN_START = 140;
    private static final int[] AGENT_TICKS = {175, 181, 187, 193, 199};
    private static final int FIRE = 210;
    private static final int THE_ONE = 244;
    private static final int DROP = 252;
    private static final int ESCAPE = 262;
    private static final int WHITE = 279;
    private static final int BLACKOUT = 282;
    private static final int BLACKOUT_TICKS = 60;
    private static final int ESCAPED_LINE = 292;
    private static final int REAL_WORLD_LINE = 326;
    private static final int END = 370;
    /** Typed lines on the CRT: message key and the tick typing starts. */
    private static final String[] INTRO_KEYS = {"matrix.wake-up", "matrix.has-you", "matrix.rabbit", "matrix.knock"};
    private static final int[] INTRO_TICKS = {10, 42, 74, 106};
    private static final int CONSTRUCT_RADIUS = 16;
    static final String CRT = "swizz:misc/crt";
    static final String[] GLITCH = CodeRain.frames("swizz:misc/glitch_", 3);
    static final String[] CHROME = CodeRain.frames("swizz:misc/chrome_", 3);

    private final FakeBlocks fakes = new FakeBlocks(this);
    private final Construct construct = new Construct(this, fakes);
    private final CodeRain rain = new CodeRain(this);
    private final Agents agents = new Agents(this);
    private final Typewriter typewriter = new Typewriter(this);
    private Cat cat;
    private Location catFrom;
    private Vector catPath;

    MatrixCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t == 0) {
            begin();
        }
        for (int i = 0; i < INTRO_KEYS.length; i++) {
            int end = i + 1 < INTRO_TICKS.length ? INTRO_TICKS[i + 1] : KNOCK;
            if (t >= INTRO_TICKS[i] && t < end) {
                typewriter.type(INTRO_KEYS[i], INTRO_TICKS[i], Typewriter.GREEN);
            }
        }
        if (t >= CAT_1 && t <= CAT_1 + CAT_WALK) {
            walkCat(CAT_1);
        }
        if (t >= CAT_2 && t <= CAT_2 + CAT_WALK) {
            walkCat(CAT_2);
        }
        if (t == CAT_2) {
            for (Player viewer : viewers()) {
                glitch(viewer, 3);
                jolt(viewer, 0);
            }
        }
        if (t == CAT_2 + 3) {
            for (Player viewer : viewers()) {
                overlay(viewer, CRT, KNOCK - t);
            }
        }
        if (t == KNOCK) {
            knock();
        }
        if (t > KNOCK && t < KNOCK + 12 && t % 2 == 0) {
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
            }
        }
        if (t >= CONSTRUCT_START && t <= CONSTRUCT_START + CONSTRUCT_TICKS) {
            construct.spread((t - CONSTRUCT_START) / (double) CONSTRUCT_TICKS * (CONSTRUCT_RADIUS + 2));
        }
        if (t >= RAIN_START && t < ESCAPE) {
            rain.screens();
            rain.fall(2, 5);
        }
        for (int i = 0; i < AGENT_TICKS.length; i++) {
            if (t == AGENT_TICKS[i]) {
                agents.multiply(1 << i);
            }
        }
        if (t > AGENT_TICKS[0] && t < ESCAPE) {
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
            title("matrix.the-one", null, 200, 1800, 400);
            sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7f);
        }
        if (t == DROP) {
            agents.dropBullets();
        }
        if (t >= ESCAPE && t < BLACKOUT) {
            escape();
        }
        if (t == BLACKOUT) {
            for (Player viewer : viewers()) {
                viewer.stopAllSounds();
                blackout(viewer, BLACKOUT_TICKS);
            }
        }
        if (t == BLACKOUT + 1) {
            construct.restore();
            rain.removeAll();
            agents.removeBullets();
        }
        if (t >= ESCAPED_LINE && t < REAL_WORLD_LINE) {
            typewriter.type("matrix.escaped", ESCAPED_LINE, Typewriter.GREEN);
        }
        if (t >= REAL_WORLD_LINE && t < BLACKOUT + BLACKOUT_TICKS + 14) {
            typewriter.type("matrix.real-world", REAL_WORLD_LINE, Typewriter.PALE);
        }
        if (t > BLACKOUT && t < BLACKOUT + BLACKOUT_TICKS && t % 20 == 8) {
            soundAll(Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1f);
        }
        if (t == BLACKOUT + 18) {
            soundAll(Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f);
        }
        if (t == BLACKOUT + BLACKOUT_TICKS) {
            for (Player viewer : viewers()) {
                whiteFlash(viewer, 4);
                sound(viewer, Sound.ENTITY_PLAYER_BREATH, 1f, 1f);
                sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
                sound(viewer, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
            }
        }
        if (t == BLACKOUT + BLACKOUT_TICKS + 14) {
            typewriter.clear();
        }
    }

    private void begin() {
        for (Player viewer : viewers()) {
            overlay(viewer, CRT, KNOCK);
            sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 0.5f);
        }
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        catFrom = player.getLocation().add(forward.clone().multiply(4.5)).subtract(right.clone().multiply(4));
        catPath = right.multiply(8);
    }

    /** Déjà vu: the same black cat walks the same path twice. */
    private void walkCat(int start) {
        double f = (t - start) / (double) CAT_WALK;
        Location at = floor(catFrom.clone().add(catPath.clone().multiply(f)).add(0, 1.5, 0));
        at.setDirection(catPath);
        at.setPitch(0);
        if (t == start) {
            cat = spawn(at, Cat.class, c -> {
                c.setCatType(Cat.Type.ALL_BLACK);
                c.setAI(false);
                c.setInvulnerable(true);
                c.setSilent(true);
                c.setCollidable(false);
            });
        } else if (cat != null) {
            cat.teleport(at);
        }
        if (t == start + CAT_WALK / 2) {
            world.playSound(at, Sound.ENTITY_CAT_AMBIENT, 1f, 1f);
        }
        if (t == start + CAT_WALK) {
            remove(cat);
            cat = null;
        }
    }

    private void knock() {
        for (Player viewer : viewers()) {
            viewer.clearTitle();
            glitch(viewer, 3);
            jolt(viewer, 0, 2, 6, 10);
            sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
            sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        }
        construct.plan(ground, CONSTRUCT_RADIUS);
    }

    private void escape() {
        int k = t - ESCAPE;
        if (k == 0) {
            for (Player viewer : viewers()) {
                sound(viewer, Sound.BLOCK_PORTAL_TRAVEL, 0.9f, 1f);
                sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                effect(viewer, PotionEffectType.NAUSEA, 60, 0);
            }
            rain.liftAll(10);
        }
        if (k == 2) {
            soundAll(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
            soundAll(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        }
        agents.dissolve(2);
        rain.rise(2);
        // Liquid chrome creeps in from the edges and swallows the screen.
        int chrome = k < 2 ? -1 : Math.min(CHROME.length - 1, (k - 2) / 5);
        for (Player viewer : viewers()) {
            if (chrome < 0) {
                glitch(viewer, 2);
            } else if (t < WHITE && (k - 2) % 5 == 0) {
                overlay(viewer, CHROME[chrome], 8);
            }
            if (k % 2 == 0) {
                jolt(viewer, 0);
                sound(viewer, Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f + random().nextFloat() * 1.5f);
            }
            if (t == WHITE) {
                whiteFlash(viewer, 3);
            }
        }
    }

    private void glitch(Player viewer, int ticks) {
        overlay(viewer, GLITCH[random().nextInt(GLITCH.length)], ticks);
        sound(viewer, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.5f + random().nextFloat() * 1.5f);
    }
}
