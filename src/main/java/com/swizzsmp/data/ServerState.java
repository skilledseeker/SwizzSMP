package com.swizzsmp.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Small server-wide values (DESIGN.md §3.2), e.g. the dragon egg cooldown. */
public final class ServerState {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public ServerState(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "server-state.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public long getLong(String key) {
        return yaml.getLong(key);
    }

    public void setLong(String key, long value) {
        yaml.set(key, value);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save server-state.yml", e);
        }
    }
}
