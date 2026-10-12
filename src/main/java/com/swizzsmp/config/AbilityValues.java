package com.swizzsmp.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Read-only view of one part of abilities.yml, e.g. "frost.ability1".
 * Upgraded values live in an "upgraded" subsection. {@link #up} returns the upgraded
 * value when the player is upgraded and one is set, and the base value otherwise.
 */
public final class AbilityValues {

    private final ConfigurationSection section;

    public AbilityValues(ConfigurationSection section) {
        this.section = section;
    }

    public ConfigurationSection raw() {
        return section;
    }

    public double num(String key) {
        return section.getDouble(key);
    }

    public int integer(String key) {
        return section.getInt(key);
    }

    public boolean bool(String key) {
        return section.getBoolean(key);
    }

    public String str(String key) {
        return section.getString(key, "");
    }

    public List<String> list(String key) {
        return section.getStringList(key);
    }

    /** The upgraded value of {@code key} if upgraded and present, else the base value. */
    public double up(String key, boolean upgraded) {
        String upgradedPath = "upgraded." + key;
        if (upgraded && section.contains(upgradedPath)) {
            return section.getDouble(upgradedPath);
        }
        return section.getDouble(key);
    }

    public double upgradedNum(String key) {
        return section.getDouble("upgraded." + key);
    }

    public int upgradedInt(String key) {
        return section.getInt("upgraded." + key);
    }

    public boolean upgradedBool(String key) {
        return section.getBoolean("upgraded." + key);
    }

    public List<String> upgradedList(String key) {
        return section.getStringList("upgraded." + key);
    }
}
