package com.swizzsmp.effects.cinematic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Digital rain: falling columns of glowing katakana (text displays) and green streaks in the
 * world, plus the looping rain overlay on the watchers' screens.
 */
final class CodeRain extends Layer {

    static final String[] FRAMES = frames("swizz:misc/rain_", 8);
    private static final TextColor GREEN = TextColor.color(0x00FF41);
    private static final TextColor DARK_GREEN = TextColor.color(0x0A5A1E);
    private static final TextColor PALE = TextColor.color(0xD4FFE0);
    private static final String GLYPHS = "ｦｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ0123456789Z:=*+<>¦";
    private static final int[] MINOR = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22};

    private record Column(TextDisplay display, int dies) {
    }

    private final List<Column> columns = new ArrayList<>();
    private Location target;

    CodeRain(Cinematic host) {
        super(host);
    }

    /** Where rising columns fly to (the black hole); null = straight up. */
    void setTarget(Location target) {
        this.target = target;
    }

    /** The overlay frame for this tick, for everyone watching. */
    void screens() {
        if (host.t % 2 == 0) {
            for (Player viewer : host.viewers()) {
                host.overlay(viewer, FRAMES[(host.t / 2) % FRAMES.length], 4);
            }
        }
    }

    /** New falling columns and streaks, with the ambient bit-notes. */
    void fall(int columnsPerTick, int streaksPerTick) {
        for (int i = 0; i < columnsPerTick; i++) {
            spawnColumn(false);
        }
        for (int i = 0; i < streaksPerTick; i++) {
            double angle = Cinematic.random().nextDouble(Math.PI * 2);
            double distance = Cinematic.random().nextDouble(1.5, 14);
            Location bottom = host.ground.clone().add(Math.cos(angle) * distance, 0.2, Math.sin(angle) * distance);
            Location top = bottom.clone().add(0, Cinematic.random().nextDouble(9, 15), 0);
            Color color = Cinematic.random().nextInt(4) == 0 ? Color.fromRGB(0xD4FFE0) : Color.fromRGB(0x00FF41);
            host.world.spawnParticle(Particle.TRAIL, top, 1, 0, 0, 0, 0, new Particle.Trail(bottom, color, Cinematic.random().nextInt(16, 28)));
        }
        if (host.t % 40 == 0) {
            host.sound(Sound.BLOCK_BEACON_AMBIENT, 0.5f);
        }
        if (host.t % 5 == 0) {
            host.sound(Sound.BLOCK_NOTE_BLOCK_BIT, Cinematic.pitch(MINOR[Cinematic.random().nextInt(MINOR.length)]));
        }
        update();
    }

    /** Short-lived columns that shoot up (or into the target) instead of falling. */
    void rise(int count) {
        for (int i = 0; i < count; i++) {
            spawnColumn(true);
        }
        update();
    }

    /** Every column still alive flies up (or into the target) over {@code ticks}. */
    void liftAll(int ticks) {
        for (Column column : columns) {
            Cinematic.animate(column.display(), new Transformation(rise(column.display().getLocation()), new Quaternionf(),
                    column.display().getTransformation().getScale(), new Quaternionf()), ticks);
        }
    }

    /** Glyphs keep changing; expired columns go. */
    void update() {
        columns.removeIf(c -> {
            if (host.t >= c.dies()) {
                host.remove(c.display());
                return true;
            }
            if ((host.t + c.dies()) % 4 == 0) {
                c.display().text(code(Cinematic.random().nextInt(8, 15)));
            }
            return false;
        });
    }

    void removeAll() {
        columns.forEach(c -> host.remove(c.display()));
        columns.clear();
    }

    private void spawnColumn(boolean rising) {
        double angle = Cinematic.random().nextDouble(Math.PI * 2);
        double distance = Cinematic.random().nextDouble(2.5, 14);
        double height = rising ? Cinematic.random().nextDouble(-1, 2) : Cinematic.random().nextDouble(3, 9);
        Location at = host.ground.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
        float scale = (float) Cinematic.random().nextDouble(0.9, 1.7);
        int life = rising ? 14 : Cinematic.random().nextInt(26, 38);
        TextDisplay column = host.spawn(at, TextDisplay.class, d -> {
            d.text(code(Cinematic.random().nextInt(8, 15)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setBrightness(Cinematic.FULL_BRIGHT);
            d.setLineWidth(40);
            d.setTransformation(Cinematic.scaled(scale));
        });
        Vector3f to = rising ? rise(at) : new Vector3f(0, -Cinematic.random().nextFloat(5, 10), 0);
        new BukkitRunnable() {
            @Override
            public void run() {
                Cinematic.animate(column, new Transformation(to, new Quaternionf(), new Vector3f(scale), new Quaternionf()), life - 2);
            }
        }.runTaskLater(host.plugin, 2L);
        columns.add(new Column(column, host.t + life));
    }

    /** Where a rising column ends up, relative to where it is. */
    private Vector3f rise(Location from) {
        if (target == null) {
            return new Vector3f(0, Cinematic.random().nextFloat(14, 22), 0);
        }
        return new Vector3f((float) (target.getX() - from.getX()), (float) (target.getY() - from.getY()), (float) (target.getZ() - from.getZ()));
    }

    /** A vertical string of katakana, dim at the top and white-hot at the falling head. */
    static Component code(int length) {
        Component text = Component.empty();
        for (int i = 0; i < length; i++) {
            String glyph = String.valueOf(GLYPHS.charAt(Cinematic.random().nextInt(GLYPHS.length())));
            TextColor color = i == length - 1 ? PALE : TextColor.lerp((float) i / length, DARK_GREEN, GREEN);
            text = text.append(Component.text(glyph, color));
            if (i < length - 1) {
                text = text.append(Component.newline());
            }
        }
        return text;
    }

    static String[] frames(String prefix, int count) {
        String[] frames = new String[count];
        for (int i = 0; i < count; i++) {
            frames[i] = prefix + i;
        }
        return frames;
    }
}
