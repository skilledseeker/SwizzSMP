package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.swizz.SwizzType;
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

/** /swizzbook main page: every swizz, the reroller, the upgrader and the rules. */
public final class BookMenu extends Menu {

    private static final int RULES = 4;
    private static final int[] SWIZZ_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int REROLLER = 38;
    private static final int ENERGY = 40;
    private static final int UPGRADER = 42;

    private final SwizzPlugin plugin;

    public BookMenu(SwizzPlugin plugin) {
        super(6, Component.text("Swizz Book", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));
        this.plugin = plugin;
        getInventory().setItem(RULES, icon(Material.WRITTEN_BOOK, Component.text("How Swizz works", NamedTextColor.GOLD),
                Component.text("Energy, loadouts, upgrades", NamedTextColor.GRAY), clickHint("read")));
        SwizzType[] types = SwizzType.values();
        for (int i = 0; i < types.length; i++) {
            getInventory().setItem(SWIZZ_SLOTS[i], withHint(plugin.items().swizzItem(types[i]), "see the recipe and guide"));
        }
        getInventory().setItem(REROLLER, withHint(plugin.items().create(ItemIds.REROLLER, 1), "see the recipe"));
        getInventory().setItem(ENERGY, withHint(plugin.items().create(ItemIds.ENERGY, 1), "read about energy"));
        getInventory().setItem(UPGRADER, withHint(plugin.items().create(ItemIds.UPGRADER, 1), "see the recipe"));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    static Component clickHint(String what) {
        return Component.text("Click to " + what, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack withHint(ItemStack item, String what) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(clickHint(what));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void onClick(Player player, int slot, InventoryClickEvent event) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
        if (slot == RULES || slot == ENERGY) {
            player.closeInventory();
            player.openBook(plugin.guide().general());
            return;
        }
        if (slot == REROLLER) {
            new RecipeMenu(plugin, ItemIds.REROLLER, null).open(player);
            return;
        }
        if (slot == UPGRADER) {
            new RecipeMenu(plugin, ItemIds.UPGRADER, null).open(player);
            return;
        }
        for (int i = 0; i < SWIZZ_SLOTS.length; i++) {
            if (SWIZZ_SLOTS[i] == slot) {
                SwizzType type = SwizzType.values()[i];
                new RecipeMenu(plugin, type.itemId(), type).open(player);
                return;
            }
        }
    }
}
