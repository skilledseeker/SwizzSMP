package com.swizzsmp.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** Location helpers. */
public final class Locs {

    private Locs() {
    }

    /** Unit vector of the horizontal direction an entity faces. */
    public static Vector horizontalDirection(Location loc) {
        double yaw = Math.toRadians(loc.getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** True if a player-sized entity can stand with feet in this block. */
    public static boolean isStandable(Block feet) {
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);
        return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                && below.getType().isSolid();
    }

    /**
     * A safe standing spot at the same x/z as {@code around}, searching up and down by up
     * to {@code range} blocks. Returns null if none is found.
     */
    public static Location findSafe(Location around, int range) {
        Block base = around.getBlock();
        for (int i = 0; i <= range; i++) {
            for (int sign : new int[]{1, -1}) {
                if (i == 0 && sign == -1) {
                    continue;
                }
                Block feet = base.getRelative(0, i * sign, 0);
                if (feet.getY() <= feet.getWorld().getMinHeight() || feet.getY() >= feet.getWorld().getMaxHeight() - 2) {
                    continue;
                }
                if (isStandable(feet)) {
                    Location loc = feet.getLocation().add(0.5, 0, 0.5);
                    loc.setYaw(around.getYaw());
                    loc.setPitch(around.getPitch());
                    return loc;
                }
            }
        }
        return null;
    }

    /** Yaw that makes something at {@code from} look at {@code to}. */
    public static float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The highest solid block at or below {@code start}, searching down {@code depth} blocks. */
    public static Block groundBelow(Location start, int depth) {
        Block block = start.getBlock();
        for (int i = 0; i <= depth; i++) {
            Block candidate = block.getRelative(0, -i, 0);
            if (candidate.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    public static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
