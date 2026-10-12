package com.swizzsmp.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * A YAML file in the plugin folder. The copy bundled in the jar is used as defaults,
 * so a key missing from the server's copy falls back to the bundled value.
 */
public final class ConfigFile {

    private final JavaPlugin plugin;
    private final String name;
    private YamlConfiguration yaml = new YamlConfiguration();

    public ConfigFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read " + name + " — using bundled defaults. Fix the file and run /swizzadmin reload.", e);
        }
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled defaults for " + name, e);
        }
        this.yaml = loaded;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            section = yaml.createSection(path);
        }
        return section;
    }
}
