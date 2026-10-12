package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.gui.Menu;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.swizz.SwizzType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Pick the two elements of an unbound Swizz Sword. */
final class SwordBindMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final SwizzPlugin plugin;
    private final SwizzSwordService swords;
    private final int heldSlot;
    /** Hand holding the Reroller that pays for a re-bind, or null for the first binding. */
    private final EquipmentSlot rerollerHand;
    private SwizzType first;

    SwordBindMenu(SwizzPlugin plugin, SwizzSwordService swords, int heldSlot, EquipmentSlot rerollerHand) {
        super(3, plugin.messages().get("sword.bind-title"));
        this.plugin = plugin;
        this.swords = swords;
        this.heldSlot = heldSlot;
        this.rerollerHand = rerollerHand;
        render();
    }

    private void render() {
        SwizzType[] types = SwizzType.values();
        for (int i = 0; i < types.length; i++) {
            ItemStack icon = plugin.items().swizzItem(types[i]);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (types[i] == first) {
                lore.add(plugin.messages().get("sword.picked"));
            } else {
                lore.add(plugin.messages().get(first == null ? "sword.pick-first" : "sword.pick-second"));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(types[i] == first);
            icon.setItemMeta(meta);
            getInventory().setItem(SLOTS[i], icon);
        }
        fill(Material.PURPLE_STAINED_GLASS_PANE);
    }

    @Override
    protected void onClick(Player player, int slot, InventoryClickEvent event) {
        SwizzType clicked = null;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                clicked = SwizzType.values()[i];
            }
        }
        if (clicked == null) {
            return;
        }
        if (first == null || first == clicked) {
            first = first == clicked ? null : clicked;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            render();
            return;
        }
        ItemStack sword = player.getInventory().getItem(heldSlot);
        boolean rebind = rerollerHand != null;
        if (!CustomItems.is(sword, ItemIds.SWORD) || (SwizzSwordService.elementsOf(sword) != null) != rebind) {
            player.closeInventory();
            return;
        }
        if (rebind) {
            ItemStack reroller = player.getInventory().getItem(rerollerHand);
            if (!CustomItems.is(reroller, ItemIds.REROLLER)) {
                plugin.messages().send(player, "reroll.lost-reroller");
                player.closeInventory();
                return;
            }
            com.swizzsmp.items.TokenListener.consumeOne(player, rerollerHand, reroller);
        }
        swords.bind(sword, first, clicked);
        player.getInventory().setItem(heldSlot, sword);
        player.closeInventory();
        plugin.messages().send(player, "sword.bound",
                Messages.comp("first", first.displayComponent()), Messages.comp("second", clicked.displayComponent()));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
    }
}
