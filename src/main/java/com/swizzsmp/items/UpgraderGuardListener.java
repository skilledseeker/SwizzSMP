package com.swizzsmp.items;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Upgraders can't be stashed (DESIGN.md §8): they may only be in a player inventory,
 * on the ground or inside a ritual. Also makes dropped custom items immune to despawning
 * and damage, so energy and upgraders are never lost to lava or cactus.
 */
public final class UpgraderGuardListener implements Listener {

    private final SwizzPlugin plugin;

    public UpgraderGuardListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isUpgrader(ItemStack item) {
        return CustomItems.is(item, ItemIds.UPGRADER);
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().endsWith("BUNDLE");
    }

    /** True while a non-player inventory (chest, ender chest, shulker, hopper, ...) is open on top. */
    private static boolean containerOpen(InventoryClickEvent event) {
        InventoryType type = event.getView().getTopInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE && type != InventoryType.PLAYER;
    }

    private static boolean exempt(HumanEntity who) {
        return who instanceof Player player && player.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (exempt(event.getWhoClicked())) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Bundles, in either direction.
        if ((isUpgrader(cursor) && isBundle(current)) || (isBundle(cursor) && isUpgrader(current))) {
            deny(event);
            return;
        }
        if (!containerOpen(event)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(top);

        // Placing from the cursor into the container.
        if (clickedTop && isUpgrader(cursor)) {
            deny(event);
            return;
        }
        // Shift-click from the player inventory into the container.
        if (!clickedTop && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && isUpgrader(current)) {
            deny(event);
            return;
        }
        // Number keys / offhand key swapping an upgrader into a container slot.
        if (clickedTop && event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (isUpgrader(hotbar)) {
                deny(event);
                return;
            }
        }
        if (clickedTop && event.getClick() == ClickType.SWAP_OFFHAND
                && isUpgrader(event.getWhoClicked().getInventory().getItemInOffHand())) {
            deny(event);
        }
    }

    private void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        plugin.messages().send(event.getWhoClicked(), "ritual.no-stash");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (exempt(event.getWhoClicked()) || !isUpgrader(event.getOldCursor())) {
            return;
        }
        InventoryType type = event.getView().getTopInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE || type == InventoryType.PLAYER) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                plugin.messages().send(event.getWhoClicked(), "ritual.no-stash");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isUpgrader(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isUpgrader(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player) && isUpgrader(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (isUpgrader(hand) && !exempt(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isUpgrader(event.getPlayerItem()) && !exempt(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Dropped custom items never despawn and can't be destroyed (except by the void). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (ItemIds.isToken(CustomItems.idOf(item.getItemStack()))) {
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
            if (isUpgrader(item.getItemStack())) {
                item.setCanMobPickup(false);
            }
        }
    }
}
