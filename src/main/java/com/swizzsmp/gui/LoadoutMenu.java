package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /loadout GUI (DESIGN.md §5.2): click two perks to swap their slots. */
public final class LoadoutMenu extends Menu {

    private static final int[] PERK_SLOTS = {11, 13, 15};
    private static final int[] UNLOCK_ENERGY = {1, 3, 5};
    private static final int[] BONUS_ENERGY = {2, 4, -1};

    private final SwizzPlugin plugin;
    private final Player player;
    private final List<Perk> order;
    private int selected = -1;

    public LoadoutMenu(SwizzPlugin plugin, Player player) {
        super(3, plugin.messages().get("loadout-menu.title"));
        this.plugin = plugin;
        this.player = player;
        this.order = new ArrayList<>(plugin.store().get(player).loadout());
        render();
    }

    private void render() {
        getInventory().clear();
        PlayerData data = plugin.store().get(player);
        for (int i = 0; i < 3; i++) {
            Perk perk = order.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.messages().get("loadout-menu.slot", Messages.text("slot", i + 1), Messages.text("energy", UNLOCK_ENERGY[i])));
            if (BONUS_ENERGY[i] > 0) {
                lore.add(plugin.messages().get(perk.isAbility() ? "loadout-menu.bonus" : "loadout-menu.bonus-wasted",
                        Messages.text("energy", BONUS_ENERGY[i])));
            }
            lore.add(plugin.messages().get(Loadout.isUnlocked(order, data.energy(), perk) ? "loadout-menu.unlocked" : "loadout-menu.locked"));
            lore.add(Component.empty());
            lore.add(plugin.messages().get(i == selected ? "loadout-menu.selected" : "loadout-menu.hint"));
            Material material = switch (perk) {
                case ABILITY_1 -> Material.BLAZE_POWDER;
                case ABILITY_2 -> Material.FIRE_CHARGE;
                case PASSIVE -> Material.TOTEM_OF_UNDYING;
            };
            ItemStack icon = icon(material, Component.text(perk.displayName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true), lore);
            if (i == selected) {
                ItemMeta meta = icon.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                icon.setItemMeta(meta);
            }
            getInventory().setItem(PERK_SLOTS[i], icon);
        }
        // Energy tier strip on the bottom row.
        for (int tier = 1; tier <= 5; tier++) {
            boolean on = data.energy() >= tier;
            getInventory().setItem(19 + tier, icon(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    Component.text("Energy " + tier, on ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void onClick(Player clicker, int slot, InventoryClickEvent event) {
        int index = -1;
        for (int i = 0; i < PERK_SLOTS.length; i++) {
            if (PERK_SLOTS[i] == slot) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        if (selected < 0) {
            selected = index;
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            render();
            return;
        }
        if (selected != index) {
            List<Perk> next = new ArrayList<>(order);
            Perk a = next.get(selected);
            next.set(selected, next.get(index));
            next.set(index, a);
            if (plugin.abilities().changeLoadout(clicker, next)) {
                order.clear();
                order.addAll(next);
                clicker.playSound(clicker.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.6f);
            }
        }
        selected = -1;
        render();
    }
}
