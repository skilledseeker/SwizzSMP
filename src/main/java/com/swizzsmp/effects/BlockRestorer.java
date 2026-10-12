package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Temporary block changes that are always undone (DESIGN.md §3.2). Every change is
 * recorded with the original block, saved to restore.yml, and protected from breaking,
 * explosions, pistons, liquids and mobs until restored. Anything left over after a crash
 * is restored on the next startup.
 */
public final class BlockRestorer implements Listener {

    /** A block position. */
    public record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block block) {
            return new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block block() {
            World world = Bukkit.getWorld(this.world);
            return world == null ? null : world.getBlockAt(x, y, z);
        }
    }

    /** One group of changes that is restored together (e.g. one dome). */
    public final class Session {
        private final String id;
        private final Map<Pos, BlockData> originals = new LinkedHashMap<>();
        private boolean open = true;

        private Session(String id) {
            this.id = id;
        }

        /** Changes a block (without physics), remembering the original the first time. */
        public void set(Block block, BlockData data) {
            if (!open) {
                return;
            }
            Pos pos = Pos.of(block);
            if (!originals.containsKey(pos)) {
                if (owner.containsKey(pos)) {
                    return; // already changed by another active session
                }
                originals.put(pos, block.getBlockData());
                owner.put(pos, this);
            }
            block.setBlockData(data, false);
        }

        public boolean contains(Block block) {
            return originals.containsKey(Pos.of(block));
        }

        public int size() {
            return originals.size();
        }

        /** Writes the session to disk. Call after a batch of {@link #set} calls. */
        public void save() {
            persist();
        }

        /** Puts every block back. */
        public void restore() {
            if (!open) {
                return;
            }
            open = false;
            List<Map.Entry<Pos, BlockData>> entries = new ArrayList<>(originals.entrySet());
            // Restore top-down so attached blocks (plants, vines) get their support back first... in reverse.
            for (int i = entries.size() - 1; i >= 0; i--) {
                Map.Entry<Pos, BlockData> entry = entries.get(i);
                Block block = entry.getKey().block();
                if (block != null) {
                    block.setBlockData(entry.getValue(), false);
                }
                owner.remove(entry.getKey());
            }
            originals.clear();
            sessions.remove(id);
            persist();
        }
    }

    private final SwizzPlugin plugin;
    private final File file;
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<Pos, Session> owner = new HashMap<>();
    private int counter;

    public BlockRestorer(SwizzPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "restore.yml");
    }

    public Session open(String label) {
        String id = label + "-" + System.currentTimeMillis() + "-" + (counter++);
        Session session = new Session(id);
        sessions.put(id, session);
        return session;
    }

    public boolean isProtected(Block block) {
        return owner.containsKey(Pos.of(block));
    }

    public void restoreAll() {
        for (Session session : new ArrayList<>(sessions.values())) {
            session.restore();
        }
    }

    /** On startup: undo anything left from a crash. */
    public void recoverPending() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int restored = 0;
        for (String sessionId : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(sessionId);
            if (section == null) {
                continue;
            }
            for (String line : section.getStringList("blocks")) {
                try {
                    String[] parts = line.split(";", 5);
                    World world = Bukkit.getWorld(UUID.fromString(parts[0]));
                    if (world == null) {
                        continue;
                    }
                    Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    block.setBlockData(Bukkit.createBlockData(parts[4]), false);
                    restored++;
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("restore.yml: bad entry '" + line + "' skipped");
                }
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("Restored " + restored + " blocks left over from an unfinished ability.");
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete " + file);
        }
    }

    private void persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Session session : sessions.values()) {
            List<String> lines = new ArrayList<>(session.originals.size());
            for (Map.Entry<Pos, BlockData> entry : session.originals.entrySet()) {
                Pos p = entry.getKey();
                lines.add(p.world() + ";" + p.x() + ";" + p.y() + ";" + p.z() + ";" + entry.getValue().getAsString());
            }
            yaml.set(session.id + ".blocks", lines);
        }
        String text = yaml.saveToString();
        boolean empty = sessions.isEmpty();
        try {
            if (empty) {
                Files.deleteIfExists(file.toPath());
            } else {
                File temp = new File(file.getParentFile(), "restore.yml.tmp");
                Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not write restore.yml", e);
        }
    }

    // ── Protection ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Blocks placed into a recorded spot would be erased on restore, so placing is blocked. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isProtected(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
