package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.items.RecipeService;
import com.swizzsmp.swizz.SwizzType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

/** Shows one custom item's crafting recipe, plus its guide book for swizz items. */
public final class RecipeMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW = 23;
    private static final int RESULT = 25;
    private static final int BACK = 36;
    private static final int READ = 40;

    private final SwizzPlugin plugin;
    private final SwizzType type;

    public RecipeMenu(SwizzPlugin plugin, String itemId, SwizzType type) {
        super(5, Component.text("Recipe", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));
        this.plugin = plugin;
        this.type = type;
        Recipe recipe = Bukkit.getRecipe(RecipeService.keyFor(itemId));
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    RecipeChoice choice = choices.get(shape[row].charAt(col));
                    if (choice instanceof RecipeChoice.MaterialChoice materials && !materials.getChoices().isEmpty()) {
                        getInventory().setItem(GRID[row * 3 + col], new ItemStack(materials.getChoices().getFirst()));
                    }
                }
            }
        } else {
            getInventory().setItem(GRID[4], icon(Material.BARRIER, Component.text("This item can't be crafted", NamedTextColor.RED)));
        }
        getInventory().setItem(ARROW, icon(Material.SPECTRAL_ARROW, Component.text("→", NamedTextColor.GRAY)));
        getInventory().setItem(RESULT, plugin.items().create(itemId, 1));
        getInventory().setItem(BACK, icon(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW)));
        if (type != null) {
            getInventory().setItem(READ, icon(Material.WRITTEN_BOOK, Component.text("Read about " + type.displayName(), type.textColor()),
                    Component.text("Passive, abilities and upgrades", NamedTextColor.GRAY), BookMenu.clickHint("read")));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
        // Leave the crafting grid visibly empty where the recipe has no item.
        ItemStack blank = icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.space());
        for (int slot : GRID) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                getInventory().setItem(slot, blank);
            }
        }
    }

    @Override
    protected void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == BACK) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
            new BookMenu(plugin).open(player);
        } else if (slot == READ && type != null) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
            player.closeInventory();
            player.openBook(plugin.guide().of(type));
        }
    }
}
