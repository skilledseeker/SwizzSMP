package com.swizzsmp.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

import java.util.ArrayList;
import java.util.List;

/** Geometry and block rules for block domes (Sculk dome). */
public final class Domes {

    private Domes() {
    }

    /** Blocks whose centre is within half a block of a sphere of {@code radius}. */
    public static List<Block> sphereShell(Location center, double radius) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int r = (int) Math.ceil(radius) + 1;
        double inner = (radius - 0.5) * (radius - 0.5);
        double outer = (radius + 0.5) * (radius + 0.5);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        List<Block> blocks = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    int y = cy + dy;
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= inner && d2 < outer) {
                        blocks.add(world.getBlockAt(cx + dx, y, cz + dz));
                    }
                }
            }
        }
        return blocks;
    }

    /** True if a dome block may replace this block (air, plants, snow layers, liquids). */
    public static boolean canReplace(Block block) {
        if (block.isEmpty() || block.isLiquid()) {
            return true;
        }
        if (!block.isReplaceable()) {
            return false;
        }
        return !(block.getState(false) instanceof TileState);
    }

    /**
     * True if a ground block may be turned into another full block and back:
     * a full solid cube, no tile entity, not unbreakable, not part of a multi-block.
     */
    public static boolean canConvertGround(Block block) {
        Material type = block.getType();
        if (!type.isSolid() || !type.isOccluding() || type.getHardness() < 0) {
            return false;
        }
        if (Tag.BEDS.isTagged(type) || Tag.DOORS.isTagged(type) || type == Material.SCULK) {
            return false;
        }
        return !(block.getState(false) instanceof TileState);
    }

    /** True if the location's distance from the centre is at least {@code minDistance}. */
    public static boolean inShellZone(Location center, Block block, double minDistance, double maxDistance) {
        if (!block.getWorld().equals(center.getWorld())) {
            return false;
        }
        double dx = block.getX() + 0.5 - (center.getBlockX() + 0.5);
        double dy = block.getY() + 0.5 - (center.getBlockY() + 0.5);
        double dz = block.getZ() + 0.5 - (center.getBlockZ() + 0.5);
        double d2 = dx * dx + dy * dy + dz * dz;
        return d2 >= minDistance * minDistance && d2 <= maxDistance * maxDistance;
    }
}
