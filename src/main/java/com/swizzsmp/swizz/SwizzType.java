package com.swizzsmp.swizz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum SwizzType {

    AQUA("Aqua", 0x3498DB, 0x1A3C8C, "water"),
    CONFUSION("Confusion", 0xB05CE0, 0x6C2D91),
    EARTH("Earth", 0xA0703C, 0x5C3A1E),
    FORTUNE("Fortune", 0x2ECC71, 0xF1C40F),
    FROST("Frost", 0xAEE6FF, 0xFFFFFF),
    GLEAM("Gleam", 0xFFE36E, 0xFFFFFF),
    SCULK("Sculk", 0x1E9FB0, 0x0B2A4A, "skulk");

    private final String displayName;
    private final int primary;
    private final int secondary;
    private final List<String> aliases;

    SwizzType(String displayName, int primary, int secondary, String... aliases) {
        this.displayName = displayName;
        this.primary = primary;
        this.secondary = secondary;
        this.aliases = List.of(aliases);
    }

    public String displayName() {
        return displayName;
    }

    /** Lower-case id used in config files and commands, e.g. "aqua". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Custom item id of this swizz's crafted item, e.g. "swizz_aqua". */
    public String itemId() {
        return "swizz_" + id();
    }

    public TextColor textColor() {
        return TextColor.color(primary);
    }

    public Color primaryColor() {
        return Color.fromRGB(primary);
    }

    public Color secondaryColor() {
        return Color.fromRGB(secondary);
    }

    /** The coloured, bold display name. */
    public Component displayComponent() {
        return Component.text(displayName, textColor()).decorate(TextDecoration.BOLD);
    }

    /** MiniMessage hex tag for the primary colour, e.g. "#3498DB". */
    public String hex() {
        return String.format("#%06X", primary);
    }

    public static Optional<SwizzType> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String needle = input.trim().toLowerCase(Locale.ROOT);
        for (SwizzType type : values()) {
            if (type.id().equals(needle) || type.aliases.contains(needle)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String optionsList() {
        return Arrays.stream(values()).map(SwizzType::id).collect(Collectors.joining(", "));
    }
}
