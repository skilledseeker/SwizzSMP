package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/** Starts showcase cinematics by name, one at a time per player (DESIGN.md §13.7). */
public final class Cinematics implements Listener {

    public enum Result { STARTED, BUSY, UNKNOWN }

    private final SwizzPlugin plugin;
    private final Map<String, BiFunction<SwizzPlugin, Player, Cinematic>> types = new LinkedHashMap<>();
    private final Map<UUID, Cinematic> running = new HashMap<>();

    public Cinematics(SwizzPlugin plugin) {
        this.plugin = plugin;
        types.put("aura", AuraCinematic::new);
        types.put("matrix", MatrixCinematic::new);
        types.put("singularity", SingularityCinematic::new);
        types.put("timestop", TimeStopCinematic::new);
    }

    public Set<String> names() {
        return types.keySet();
    }

    public Result play(String name, Player player) {
        BiFunction<SwizzPlugin, Player, Cinematic> factory = types.get(name.toLowerCase());
        if (factory == null) {
            return Result.UNKNOWN;
        }
        if (running.containsKey(player.getUniqueId())) {
            return Result.BUSY;
        }
        Cinematic cinematic = factory.apply(plugin, player);
        running.put(player.getUniqueId(), cinematic);
        cinematic.start(() -> running.remove(player.getUniqueId(), cinematic));
        return Result.STARTED;
    }

    public void stop() {
        List.copyOf(running.values()).forEach(Cinematic::end);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getPlayer());
    }

    private void end(Player player) {
        Cinematic cinematic = running.get(player.getUniqueId());
        if (cinematic != null) {
            cinematic.end();
        }
    }
}
