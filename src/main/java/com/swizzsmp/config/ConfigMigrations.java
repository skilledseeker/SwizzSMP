package com.swizzsmp.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.logging.Logger;

/**
 * Updates config files written by older versions. A server keeps its own copies of the
 * config files, so changed defaults would otherwise never reach it. A value is only
 * changed if it still has the old default, so admins' own edits are never overwritten.
 */
public final class ConfigMigrations {

    public static final int CURRENT = 6;

    private ConfigMigrations() {
    }

    public static void run(ConfigFile config, ConfigFile abilities, ConfigFile messages, ConfigFile items, Logger logger) {
        migrate(config, abilities, items, logger);
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

    private static void migrate(ConfigFile config, ConfigFile abilities, ConfigFile items, Logger logger) {
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
        if (version < 4) {
            // The placeholder recipes from 1.0 are replaced by the owner's recipes, but only
            // where the server still has the placeholder (edited recipes are kept).
            YamlConfiguration it = items.yaml();
            org.bukkit.configuration.Configuration defaults = it.getDefaults();
            String[][] old = {
                    {"swizz_aqua", "DIAMOND", "NAUTILUS_SHELL", "HEART_OF_THE_SEA"},
                    {"swizz_confusion", "DIAMOND", "FERMENTED_SPIDER_EYE", "ENDER_EYE"},
                    {"swizz_fortune", "DIAMOND", "GOLD_INGOT", "EMERALD_BLOCK"},
                    {"swizz_frost", "DIAMOND", "POWDER_SNOW_BUCKET", "BLUE_ICE"},
                    {"swizz_gleam", "DIAMOND", "GLOW_INK_SAC", "GOLD_BLOCK"},
                    {"swizz_sculk", "DIAMOND", "ECHO_SHARD", "SCULK_CATALYST"},
            };
            int replaced = 0;
            for (String[] o : old) {
                String path = "recipes." + o[0];
                boolean placeholder = it.isConfigurationSection(path)
                        && java.util.List.of("DED", "ECE", "DED").equals(it.getStringList(path + ".shape"))
                        && o[1].equals(it.getString(path + ".ingredients.D"))
                        && o[2].equals(it.getString(path + ".ingredients.E"))
                        && o[3].equals(it.getString(path + ".ingredients.C"));
                if (placeholder && defaults != null && defaults.isConfigurationSection(path)) {
                    it.set(path, null);
                    it.set(path + ".shape", defaults.getStringList(path + ".shape"));
                    for (String key : defaults.getConfigurationSection(path + ".ingredients").getKeys(false)) {
                        it.set(path + ".ingredients." + key, defaults.get(path + ".ingredients." + key));
                    }
                    replaced++;
                }
            }
            if (replaced > 0) {
                items.save();
                logger.info("Config update: " + replaced + " recipe(s) updated to the Season 5 recipes.");
            }
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
        if (version < 6) {
            replaceIfDefault(abilities.yaml(), "confusion.ability2.upgraded.invert-sprint-multiplier", 1.35, 1.5, logger);
            abilities.save();
        }
        if (version < 5 && c.isConfigurationSection("aura")) {
            // /aura became one of several cinematics, which share these options.
            c.set("cinematics.screen-radius", c.getDouble("aura.screen-radius", 30));
            c.set("cinematics.levitate", c.getBoolean("aura.levitate", true));
            c.set("aura", null);
            logger.info("Config update: the aura options moved to 'cinematics'.");
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
