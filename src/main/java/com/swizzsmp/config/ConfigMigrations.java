package com.swizzsmp.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.logging.Logger;

/**
 * Updates config files written by older versions. A server keeps its own copies of the
 * config files, so changed defaults would otherwise never reach it. A value is only
 * changed if it still has the old default, so admins' own edits are never overwritten.
 */
public final class ConfigMigrations {

    public static final int CURRENT = 3;

    private ConfigMigrations() {
    }

    public static void run(ConfigFile config, ConfigFile abilities, ConfigFile messages, Logger logger) {
        migrate(config, abilities, logger);
        // New options appear in the server's files so admins can see and edit them.
        // (Not items.yml: a recipe an admin removed must stay removed.)
        addMissing(config, logger);
        addMissing(abilities, logger);
        addMissing(messages, logger);
    }

    /** Copies every option the server's file doesn't have yet from the bundled defaults. */
    private static void addMissing(ConfigFile file, Logger logger) {
        YamlConfiguration yaml = file.yaml();
        org.bukkit.configuration.Configuration defaults = yaml.getDefaults();
        if (defaults == null) {
            return;
        }
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !yaml.isSet(key)) {
                yaml.set(key, defaults.get(key));
                added++;
            }
        }
        if (added > 0) {
            file.save();
            logger.info("Config update: added " + added + " new option(s) to " + file.name() + ".");
        }
    }

    private static void migrate(ConfigFile config, ConfigFile abilities, Logger logger) {
        YamlConfiguration c = config.yaml();
        int version = c.isSet("config-version") ? c.getInt("config-version") : 1;
        if (version >= CURRENT) {
            return;
        }
        if (version < 2) {
            YamlConfiguration a = abilities.yaml();
            replaceIfDefault(a, "sculk.ability2.radius", 15, 10, logger);
            replaceIfDefault(a, "sculk.ability2.upgraded.radius", 30, 25, logger);
            replaceIfDefault(a, "fortune.ability1.hunger-level", 1, 2, logger);
            if (c.isSet("ritual.max-abs-coordinate")) {
                c.set("ritual.max-abs-coordinate", null);
                c.set("ritual.max-distance-from-spawn", 1500);
                logger.info("Config update: rituals now work within 1500 blocks of spawn.");
            }
            abilities.save();
        }
        if (version < 3) {
            YamlConfiguration a = abilities.yaml();
            replaceIfDefault(a, "earth.ability1.cooldown", 150, 120, logger);
            replaceIfDefault(a, "earth.ability1.length", 15, 18, logger);
            replaceIfDefault(a, "earth.ability1.damage", 4, 6, logger);
            replaceIfDefault(a, "earth.ability1.upgraded.damage", 6, 8, logger);
            replaceIfDefault(a, "earth.ability2.cooldown", 120, 100, logger);
            replaceIfDefault(a, "earth.ability2.upgraded.thorns-damage", 1, 2, logger);
            abilities.save();
        }
        c.set("config-version", CURRENT);
        config.save();
    }

    private static void replaceIfDefault(YamlConfiguration yaml, String path, double oldDefault, double newValue, Logger logger) {
        if (yaml.isSet(path) && yaml.getDouble(path) == oldDefault) {
            yaml.set(path, newValue == Math.floor(newValue) ? (Object) (int) newValue : newValue);
            logger.info("Config update: " + path + " " + fmt(oldDefault) + " -> " + fmt(newValue));
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
