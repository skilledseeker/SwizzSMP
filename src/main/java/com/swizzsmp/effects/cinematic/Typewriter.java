package com.swizzsmp.effects.cinematic;

import com.swizzsmp.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Lines from messages.yml typed out one letter per tick in the subtitle, with a blinking cursor and key clicks. */
final class Typewriter extends Layer {

    static final TextColor GREEN = TextColor.color(0x00FF41);
    static final TextColor PALE = TextColor.color(0xD4FFE0);

    Typewriter(Cinematic host) {
        super(host);
    }

    /** Shows the line as far as it has been typed since {@code start}; call every tick while it should be on screen. */
    void type(String key, int start, TextColor color) {
        String text = line(key);
        int shown = Math.min(text.length(), host.t - start + 1);
        if (shown <= 0) {
            return;
        }
        boolean typing = shown < text.length() || host.t == start + text.length() - 1;
        boolean cursor = shown < text.length() || (host.t / 6) % 2 == 0;
        Component sub = Component.text(text.substring(0, shown), color).append(Component.text(cursor ? "█" : " ", color));
        Title title = Title.title(Component.empty(), sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
        boolean click = typing && text.charAt(shown - 1) != ' ';
        for (Player viewer : host.viewers()) {
            viewer.showTitle(title);
            if (click) {
                Cinematic.sound(viewer, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.35f, 1.6f + Cinematic.random().nextFloat() * 0.4f);
            }
        }
    }

    /** How many ticks a line takes to type, plus a pause to read it. */
    int duration(String key, int readTicks) {
        return line(key).length() + readTicks;
    }

    String line(String key) {
        return PlainTextComponentSerializer.plainText().serialize(host.plugin.messages().get(key, Messages.text("name", host.player.getName())));
    }

    void clear() {
        for (Player viewer : host.viewers()) {
            viewer.clearTitle();
        }
    }
}
