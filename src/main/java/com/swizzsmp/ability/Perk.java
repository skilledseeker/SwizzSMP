package com.swizzsmp.ability;

import java.util.Locale;
import java.util.Optional;

public enum Perk {

    ABILITY_1("Ability 1", "A1"),
    ABILITY_2("Ability 2", "A2"),
    PASSIVE("Passive", "P");

    private final String displayName;
    private final String shortName;

    Perk(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    public boolean isAbility() {
        return this != PASSIVE;
    }

    public static Optional<Perk> parse(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (s) {
            case "a1", "ability1", "1", "first" -> Optional.of(ABILITY_1);
            case "a2", "ability2", "2", "second" -> Optional.of(ABILITY_2);
            case "p", "passive" -> Optional.of(PASSIVE);
            default -> Optional.empty();
        };
    }
}
