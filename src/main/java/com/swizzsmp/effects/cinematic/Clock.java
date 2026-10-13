package com.swizzsmp.effects.cinematic;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

/** A golden clock that floats over the player: a camera-facing face with two turning hands. */
final class Clock extends Layer {

    static final float SIZE = 4.2f;
    private static final String FACE = "swizz:clock_face";
    private static final String HOUR = "swizz:clock_hour";
    private static final String MINUTE = "swizz:clock_minute";

    private final ItemDisplay face;
    private final ItemDisplay hour;
    private final ItemDisplay minute;
    private double hourAngle;
    private double minuteAngle;
    private boolean gone;

    Clock(Cinematic host) {
        super(host);
        Location at = at();
        face = host.plane(at, FACE, Display.Billboard.CENTER, Cinematic.facing(0, 0.01f, 0));
        hour = host.plane(at, HOUR, Display.Billboard.CENTER, Cinematic.facing(0, 0.01f, 0.04f));
        minute = host.plane(at, MINUTE, Display.Billboard.CENTER, Cinematic.facing(0, 0.01f, 0.08f));
    }

    private Location at() {
        return Cinematic.flat(host.player.getLocation().add(0, host.player.getHeight() + 2.8, 0));
    }

    /** Follows the player; call every tick. */
    void follow() {
        if (gone) {
            return;
        }
        Location at = at();
        face.teleport(at);
        hour.teleport(at);
        minute.teleport(at);
    }

    /**
     * Turns the hands by {@code speed} radians (negative winds back) and sets the size for the
     * next two ticks; call every other tick.
     */
    void turn(double speed, float size) {
        if (gone) {
            return;
        }
        minuteAngle -= speed;
        hourAngle -= speed / 12;
        Cinematic.animate(face, Cinematic.facing(0, size, 0), 2);
        Cinematic.animate(hour, Cinematic.facing(hourAngle, size, 0.04f), 2);
        Cinematic.animate(minute, Cinematic.facing(minuteAngle, size, 0.08f), 2);
    }

    /** The clock bursts like glass and is gone. */
    void shatter() {
        if (gone) {
            return;
        }
        gone = true;
        Location at = face.getLocation();
        host.world.spawnParticle(Particle.DUST, at, 120, 1.6, 1.6, 0.4, 0, new Particle.DustOptions(Color.fromRGB(0xFFD27F), 1.4f));
        host.world.spawnParticle(Particle.CRIT, at, 80, 1.4, 1.4, 0.4, 0.4);
        host.world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.fromRGB(0xFFE9A8));
        host.world.playSound(at, Sound.BLOCK_GLASS_BREAK, 2f, 0.6f);
        host.world.playSound(at, Sound.BLOCK_BELL_RESONATE, 2f, 0.5f);
        host.world.playSound(at, Sound.ENTITY_ITEM_BREAK, 2f, 0.5f);
        host.remove(face);
        host.remove(hour);
        host.remove(minute);
    }

    /** Scale for a clock that grows in over {@code ticks} from {@code from} and shrinks from {@code until}. */
    static float size(int t, int from, int ticks, int until, int outTicks, boolean pulse) {
        if (t < from + ticks) {
            return (float) Math.max(0.01, SIZE * Cinematic.smooth((t - from) / (double) ticks));
        }
        if (t >= until) {
            return (float) Math.max(0.01, SIZE * (1 - Cinematic.smooth((t - until) / (double) outTicks)));
        }
        return pulse ? SIZE + (float) Math.sin(t / 10.0) * 0.15f : SIZE;
    }
}
