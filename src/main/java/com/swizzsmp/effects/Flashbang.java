package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Gleam flashbang (DESIGN.md §7.4). The victim's screen goes white without any
 * potion effect. In OVERLAY mode only the victim is sent a fake helmet whose
 * {@code equippable} component has a full-screen camera overlay. TITLE mode is a
 * fallback that fills the screen with white block characters.
 */
public final class Flashbang implements Listener {

    public enum Mode { OVERLAY, TITLE }

    private static final class State {
        final int whiteTicks;
        final int cycleTicks;
        final int endTick;
        final Mode mode;
        final ItemStack overlayItem;
        int age;
        boolean white;

        State(int totalTicks, int whiteTicks, int clearTicks, Mode mode, ItemStack overlayItem) {
            this.whiteTicks = whiteTicks;
            this.cycleTicks = Math.max(1, whiteTicks + clearTicks);
            this.endTick = totalTicks;
            this.mode = mode;
            this.overlayItem = overlayItem;
        }
    }

    private static final Component WHITE_LINE = Component.text("█".repeat(40), NamedTextColor.WHITE);

    private final SwizzPlugin plugin;
    private final Map<UUID, State> active = new HashMap<>();
    private BukkitTask task;

    public Flashbang(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (UUID uuid : Map.copyOf(active).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                end(player);
            }
        }
        active.clear();
    }

    /** Flashes a player: white for {@code whiteTicks}, clear for {@code clearTicks}, repeating. */
    public void flash(Player target, int totalTicks, int whiteTicks, int clearTicks, Mode mode, String overlayTexture) {
        ItemStack overlay = null;
        if (mode == Mode.OVERLAY) {
            overlay = new ItemStack(Material.PAPER);
            Key texture = Key.key(overlayTexture);
            overlay.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).cameraOverlay(texture).build());
        }
        State previous = active.put(target.getUniqueId(), new State(totalTicks, whiteTicks, clearTicks, mode, overlay));
        if (previous != null && previous.white) {
            clearScreen(target, previous);
        }
    }

    public boolean isFlashed(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, State> entry : Map.copyOf(active).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null) {
                active.remove(entry.getKey());
                continue;
            }
            if (state.age >= state.endTick) {
                end(player);
                continue;
            }
            boolean shouldBeWhite = (state.age % state.cycleTicks) < state.whiteTicks;
            if (shouldBeWhite) {
                // Re-send often: any real helmet update from the server would undo the fake.
                if (!state.white || state.age % 5 == 0) {
                    whiteScreen(player, state);
                }
            } else if (state.white) {
                clearScreen(player, state);
            }
            state.white = shouldBeWhite;
            state.age++;
        }
    }

    private void whiteScreen(Player player, State state) {
        if (state.mode == Mode.OVERLAY) {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, state.overlayItem);
        } else {
            player.showTitle(Title.title(WHITE_LINE, WHITE_LINE,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ZERO)));
        }
    }

    private void clearScreen(Player player, State state) {
        if (state.mode == Mode.OVERLAY) {
            ItemStack real = player.getInventory().getHelmet();
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, real == null ? ItemStack.empty() : real);
            player.updateInventory();
        } else {
            player.clearTitle();
        }
    }

    private void end(Player player) {
        State state = active.remove(player.getUniqueId());
        if (state != null) {
            clearScreen(player, state);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getPlayer());
    }
}
