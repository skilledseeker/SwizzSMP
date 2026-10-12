package com.swizzsmp.ritual;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One running upgrade ritual (DESIGN.md §8). */
final class Ritual {

    final UUID owner;
    final String ownerName;
    final UUID world;
    final int x;
    final int y;
    final int z;
    /** Seconds the ritual has run. Server downtime does not count. */
    int elapsedSeconds;
    /** Coordinates already revealed: "X", "Y", "Z". */
    final Set<String> leaked = new LinkedHashSet<>();
    /** Interval boundaries already announced. */
    int lastAnnouncedStep;

    Ritual(UUID owner, String ownerName, UUID world, int x, int y, int z) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    World world() {
        return Bukkit.getWorld(world);
    }

    /** Centre of the anchor block, or null if the world is not loaded. */
    Location center() {
        World w = world();
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }
}
