package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.items.TokenListener;
import com.swizzsmp.swizz.SwizzType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Reroller GUI (DESIGN.md §4.2): pick a new swizz; energy is kept. */
public final class RerollMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final SwizzPlugin plugin;
    private final EquipmentSlot hand;

    public RerollMenu(SwizzPlugin plugin, Player player, EquipmentSlot hand) {
        super(3, plugin.messages().get("reroll.title"));
        this.plugin = plugin;
        this.hand = hand;
        SwizzType current = plugin.store().get(player).swizz();
        SwizzType[] types = SwizzType.values();
        for (int i = 0; i < types.length; i++) {
            ItemStack icon = plugin.items().swizzItem(types[i]);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.messages().get(types[i] == current ? "reroll.current" : "reroll.click"));
            meta.lore(lore);
            if (types[i] == current) {
                meta.setEnchantmentGlintOverride(false);
            }
            icon.setItemMeta(meta);
            getInventory().setItem(SLOTS[i], icon);
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void onClick(Player player, int slot, InventoryClickEvent event) {
        int index = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        SwizzType chosen = SwizzType.values()[index];
        PlayerData data = plugin.store().get(player);
        if (!data.hasSwizz()) {
            plugin.messages().send(player, "reroll.need-swizz");
            player.closeInventory();
            return;
        }
        if (chosen == data.swizz()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.6f);
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (!CustomItems.is(held, ItemIds.REROLLER)) {
            plugin.messages().send(player, "reroll.lost-reroller");
            player.closeInventory();
            return;
        }
        TokenListener.consumeOne(player, hand, held);
        data.setSwizz(chosen);
        if (!plugin.settings().rerollKeepsUpgrade) {
            data.setUpgraded(false);
        }
        player.closeInventory();
        plugin.onStateChanged(player);
        plugin.saveNow(player);
        plugin.messages().send(player, "reroll.success", Messages.comp("swizz", chosen.displayComponent()));
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5,
                new Particle.DustOptions(chosen.primaryColor(), 1.6f));
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
    }
}
