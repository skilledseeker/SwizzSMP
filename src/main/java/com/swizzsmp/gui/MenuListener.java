package com.swizzsmp.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes clicks to {@link Menu}s and stops items from being taken out of them. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        boolean top = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        // Some menus let players pick items up in their own inventory (e.g. the recipe editor).
        // Double-click collecting would also pull items out of the menu, so it's never allowed.
        if (!top && menu.allowsOwnInventory() && !event.isShiftClick()
                && event.getAction() != org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null) {
            return;
        }
        if (top) {
            menu.onClick(player, event.getSlot(), event);
        } else {
            menu.onOwnInventoryClick(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu menu && event.getPlayer() instanceof Player player) {
            menu.onClose(player);
        }
    }
}
