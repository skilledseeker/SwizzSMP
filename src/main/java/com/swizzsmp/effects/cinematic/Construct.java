package com.swizzsmp.effects.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Matrix construct: every visible block around a point turns black with a lime grid on
 * its visible faces and walk-through blocks vanish, spreading out as a ring. Fake blocks only.
 */
final class Construct extends Layer {

    private record Fake(Block block, BlockData data, double distance) {
    }

    private final FakeBlocks fakes;
    private final List<Fake> planned = new ArrayList<>();
    private final Map<Block, BlockData> shown = new HashMap<>();
    private int sent;

    Construct(Cinematic host, FakeBlocks fakes) {
        super(host);
        this.fakes = fakes;
    }

    /** Works out the look of every block within {@code radius} of {@code center}, nearest first. */
    void plan(Location center, int radius) {
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -6; dy <= 12; dy++) {
                    Block block = host.world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    BlockData data = look(block);
                    if (data != null) {
                        planned.add(new Fake(block, data, Math.sqrt(dx * dx + dz * dz + dy * dy * 0.5)));
                    }
                }
            }
        }
        planned.sort(Comparator.comparingDouble(Fake::distance));
    }

    /**
     * Full blocks become black with a lime grid on their visible faces; plants, torches and
     * other walk-through blocks vanish. Anything else (slabs, glass, leaves, block entities)
     * is left alone, so the client's idea of where it can stand never changes.
     */
    private static BlockData look(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || block.getState(false) instanceof TileState) {
            return null;
        }
        if (block.isPassable()) {
            return Material.AIR.createBlockData();
        }
        if (!type.isOccluding()) {
            return null;
        }
        boolean up = !block.getRelative(BlockFace.UP).getType().isOccluding();
        boolean northSouth = !block.getRelative(BlockFace.NORTH).getType().isOccluding() || !block.getRelative(BlockFace.SOUTH).getType().isOccluding();
        boolean eastWest = !block.getRelative(BlockFace.EAST).getType().isOccluding() || !block.getRelative(BlockFace.WEST).getType().isOccluding();
        if (!up && !northSouth && !eastWest) {
            return null;
        }
        boolean gx = Math.floorMod(block.getX(), 4) == 0;
        boolean gy = Math.floorMod(block.getY(), 4) == 0;
        boolean gz = Math.floorMod(block.getZ(), 4) == 0;
        boolean line = (up && (gx || gz)) || (northSouth && (gx || gy)) || (eastWest && (gz || gy));
        return (line ? Material.LIME_CONCRETE : Material.BLACK_CONCRETE).createBlockData();
    }

    /** Sends everything planned within {@code radius}, with spreading sounds. */
    void spread(double radius) {
        List<BlockState> wave = new ArrayList<>();
        while (sent < planned.size() && planned.get(sent).distance() <= radius) {
            Fake fake = planned.get(sent++);
            BlockState state = fake.block().getState();
            state.setBlockData(fake.data());
            wave.add(state);
            shown.put(fake.block(), fake.data());
        }
        if (wave.isEmpty()) {
            return;
        }
        fakes.send(wave);
        if (host.t % 6 == 0) {
            host.sound(Sound.BLOCK_SCULK_SPREAD, 0.6f);
            host.sound(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f + Cinematic.random().nextFloat());
        }
    }

    /** What a block looks like right now in the construct (null if untouched). */
    BlockData shownLook(Block block) {
        return shown.get(block);
    }

    void restore() {
        fakes.restore(new ArrayList<>(shown.keySet()));
        shown.clear();
    }

    @Override
    void stop() {
        restore();
    }
}
