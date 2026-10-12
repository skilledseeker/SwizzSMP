package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Swizz Sword (DESIGN.md §13.1). While a bound sword is anywhere in a player's inventory,
 * their effective swizz is its two elements. Losing it restores their own swizz at once.
 */
public final class SwizzSwordService implements Listener {

    private final SwizzPlugin plugin;
    private final Map<UUID, List<SwizzType>> cache = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private BukkitTask task;
    private int tick;

    public SwizzSwordService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.swizz().setSwordElements(player -> cache.get(player.getUniqueId()));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        cache.clear();
    }

    /** The two elements bound to a sword item, or null if it is unbound or not a sword. */
    public static List<SwizzType> elementsOf(ItemStack item) {
        if (!CustomItems.is(item, ItemIds.SWORD)) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.SWORD_ELEMENTS, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        List<SwizzType> types = new ArrayList<>(2);
        for (String part : raw.split(",")) {
            SwizzType.parse(part).ifPresent(types::add);
        }
        return types.size() == 2 && types.get(0) != types.get(1) ? List.copyOf(types) : null;
    }

    public void bind(ItemStack sword, SwizzType first, SwizzType second) {
        ItemMeta meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.SWORD_ELEMENTS, PersistentDataType.STRING, first.id() + "," + second.id());
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(plugin.messages().get("sword.elements-lore",
                Messages.comp("first", first.displayComponent()), Messages.comp("second", second.displayComponent())));
        meta.lore(lore);
        sword.setItemMeta(meta);
    }

    private void tick() {
        tick++;
        boolean full = tick % 20 == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (full || dirty.contains(player.getUniqueId())) {
                recompute(player);
            }
        }
        dirty.clear();
    }

    private void recompute(Player player) {
        List<SwizzType> found = null;
        for (ItemStack item : player.getInventory().getContents()) {
            List<SwizzType> elements = elementsOf(item);
            if (elements != null) {
                found = elements;
                break;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (found == null && player.getOpenInventory().getCursor() != null) {
            found = elementsOf(cursor);
        }
        List<SwizzType> previous = found == null ? cache.remove(player.getUniqueId()) : cache.put(player.getUniqueId(), found);
        if (java.util.Objects.equals(previous, found)) {
            return;
        }
        plugin.onStateChanged(player);
        if (found != null) {
            plugin.messages().send(player, "sword.gained",
                    Messages.comp("first", found.get(0).displayComponent()), Messages.comp("second", found.get(1).displayComponent()));
        } else {
            plugin.messages().send(player, "sword.lost");
        }
    }

    private void markDirty(Player player) {
        dirty.add(player.getUniqueId());
    }

    // Inventory changes that could add or remove a sword.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            markDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDropItem(PlayerDropItemEvent event) {
        markDirty(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        markDirty(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markDirty(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    /** Right-click an unbound sword to choose its two elements. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!CustomItems.is(item, ItemIds.SWORD) || elementsOf(item) != null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        new SwordBindMenu(plugin, this, event.getPlayer().getInventory().getHeldItemSlot()).open(event.getPlayer());
    }
}
