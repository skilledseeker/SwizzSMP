package com.swizzsmp.effects.cinematic;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Blocks that only the watchers see: changes are sent to their clients and never touch the
 * world. Every faked block is re-sent from the real world when restored, or when the show ends.
 */
final class FakeBlocks extends Layer {

    private final Set<Block> faked = new HashSet<>();
    private final Set<UUID> sentTo = new HashSet<>();

    FakeBlocks(Cinematic host) {
        super(host);
    }

    /** Shows these looks to everyone watching. */
    void send(List<BlockState> states) {
        if (states.isEmpty()) {
            return;
        }
        for (Player viewer : host.viewers()) {
            sentTo.add(viewer.getUniqueId());
            viewer.sendBlockChanges(states);
        }
        for (BlockState state : states) {
            faked.add(state.getBlock());
        }
    }

    boolean isFaked(Block block) {
        return faked.contains(block);
    }

    /** The real blocks again, for these blocks only. */
    void restore(Collection<Block> blocks) {
        List<BlockState> real = new ArrayList<>();
        for (Block block : blocks) {
            if (faked.remove(block)) {
                real.add(block.getState());
            }
        }
        if (real.isEmpty()) {
            return;
        }
        for (UUID id : sentTo) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.getWorld() == host.world) {
                viewer.sendBlockChanges(real);
            }
        }
    }

    void restoreAll() {
        restore(new ArrayList<>(faked));
    }

    @Override
    void stop() {
        restoreAll();
    }
}
