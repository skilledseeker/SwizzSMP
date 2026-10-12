package com.swizzsmp.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** A chest GUI. All clicks are cancelled; subclasses react in {@link #onClick}. */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Called for clicks in the top (menu) inventory. The event is already cancelled. */
    protected abstract void onClick(Player player, int slot, InventoryClickEvent event);

    protected void onClose(Player player) {
    }

    /** True if players may move items around in their own inventory while this is open. */
    protected boolean allowsOwnInventory() {
        return false;
    }

    /** Shift-clicks in the player's own inventory (already cancelled). */
    protected void onOwnInventoryClick(Player player, InventoryClickEvent event) {
    }

    // ── Item helpers ──────────────────────────────────────────────────────────

    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack icon(Material material, Component name, Component... lore) {
        return icon(material, name, new ArrayList<>(List.of(lore)));
    }

    protected void fill(Material material) {
        ItemStack pane = icon(material, Component.space());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }
}
