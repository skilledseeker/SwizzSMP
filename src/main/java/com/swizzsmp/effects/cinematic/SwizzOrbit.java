package com.swizzsmp.effects.cinematic;

import com.swizzsmp.effects.Particles;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

/**
 * The seven swizz items as glowing gems: they rise one by one with a chime, orbit a point, and
 * stream their colours into a target.
 */
final class SwizzOrbit extends Layer {

    static final SwizzType[] TYPES = SwizzType.values();
    /** A rising pentatonic run, one note per swizz. */
    static final int[] NOTES = {5, 7, 10, 12, 15, 17, 19};

    private final ItemDisplay[] gems = new ItemDisplay[TYPES.length];
    private final int[] born = new int[TYPES.length];
    private double angle;

    SwizzOrbit(Cinematic host) {
        super(host);
    }

    int count() {
        int n = 0;
        for (ItemDisplay gem : gems) {
            if (gem != null) {
                n++;
            }
        }
        return n;
    }

    /** Gem {@code i} appears on the floor at its orbit slot and swells up, with its chime. */
    void raise(int i, Location center, double radius) {
        SwizzType type = TYPES[i];
        Location at = Cinematic.around(center, angle + i * 2 * Math.PI / TYPES.length, radius, host.ground.getY() + 0.3);
        ItemDisplay gem = host.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(host.plugin.items().swizzItem(type));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(Cinematic.FULL_BRIGHT);
            d.setGlowing(true);
            d.setGlowColorOverride(type.primaryColor());
            d.setTeleportDuration(2);
            d.setTransformation(Cinematic.scaled(0.01f));
        });
        gems[i] = gem;
        born[i] = host.t;
        Bukkit.getScheduler().runTaskLater(host.plugin, () -> Cinematic.animate(gem, Cinematic.scaled(1.4f), 8), 2L);
        host.world.spawnParticle(Particle.DUST, at, 30, 0.3, 0.6, 0.3, 0, new Particle.DustOptions(type.primaryColor(), 1.6f));
        host.sound(Sound.BLOCK_NOTE_BLOCK_BELL, Cinematic.pitch(NOTES[i]));
        host.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, Cinematic.pitch(NOTES[i]));
    }

    /**
     * Moves every gem one step around {@code center} at {@code radius}, turning {@code degrees}
     * per tick. Fresh gems glide up from the floor over their first second.
     */
    void orbit(Location center, double radius, double degrees) {
        angle += Math.toRadians(degrees);
        for (int i = 0; i < gems.length; i++) {
            if (gems[i] == null) {
                continue;
            }
            double rise = Cinematic.smooth((host.t - born[i]) / 20.0);
            double y = Cinematic.lerp(host.ground.getY() + 0.3, center.getY() + Math.sin(host.t / 6.0 + i) * 0.25, rise);
            gems[i].teleport(Cinematic.around(center, angle + i * 2 * Math.PI / gems.length, radius, y));
        }
    }

    /** Each gem streams its colour into the target (only once it has been up for a second). */
    void beams(Location target) {
        for (int i = 0; i < gems.length; i++) {
            if (gems[i] == null || host.t < born[i] + 20) {
                continue;
            }
            Location from = gems[i].getLocation();
            Color color = TYPES[i].primaryColor();
            host.world.spawnParticle(Particle.TRAIL, from, 2, 0.08, 0.08, 0.08, 0, new Particle.Trail(target, color, 12));
            host.world.spawnParticle(Particle.DUST, from, 2, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(color, 1.4f));
        }
    }

    /** A ring of every swizz colour racing out along the floor; call with k = 1, 2, 3… */
    void rainbowRing(int k) {
        double radius = k * 0.85;
        int[] next = {k};
        Particles.circle(host.ground.clone().add(0, 0.3, 0), radius, 10 + (int) (radius * 7), 0, p -> host.world.spawnParticle(Particle.DUST, p, 1,
                0.05, 0.05, 0.05, 0, new Particle.DustOptions(TYPES[next[0]++ % TYPES.length].primaryColor(), 1.8f)));
        if (k % 2 == 0) {
            Particles.circle(host.ground.clone().add(0, 0.5, 0), radius, 6 + (int) (radius * 2), 0.3,
                    p -> host.world.spawnParticle(Particle.FIREWORK, p, 1, 0, 0.05, 0, 0.02));
        }
    }

    void resize(float scale, int ticks) {
        for (ItemDisplay gem : gems) {
            Cinematic.animate(gem, Cinematic.scaled(scale), ticks);
        }
    }

    /** The gems vanish into {@code at} in a burst of every swizz colour. */
    void converge(Location at) {
        for (int i = 0; i < gems.length; i++) {
            host.remove(gems[i]);
            gems[i] = null;
        }
        for (SwizzType type : TYPES) {
            host.world.spawnParticle(Particle.DUST, at, 25, 1.2, 1.2, 1.2, 0, new Particle.DustOptions(type.primaryColor(), 2.2f));
        }
    }
}
