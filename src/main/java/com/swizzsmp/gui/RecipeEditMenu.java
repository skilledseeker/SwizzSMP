package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.items.RecipeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /swizzadmin recipe <item>: edit a custom item's crafting recipe in game.
 * The grid holds templates only: click a slot while holding any item to set that
 * ingredient, click with an empty hand to clear it. Nothing is taken from or given to
 * the admin. Save writes items.yml and reloads the recipes.
 */
public final class RecipeEditMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 25;
    private static final int HELP = 4;
    private static final int DISABLE = 38;
    private static final int SAVE = 42;

    private final SwizzPlugin plugin;
    private final String itemId;
    private final Material[] grid = new Material[9];

    public RecipeEditMenu(SwizzPlugin plugin, String itemId) {
        super(5, Component.text("Edit recipe: " + itemId, NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true));
        this.plugin = plugin;
        this.itemId = itemId;
        Recipe recipe = Bukkit.getRecipe(RecipeService.keyFor(itemId));
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    if (choices.get(shape[row].charAt(col)) instanceof RecipeChoice.MaterialChoice m && !m.getChoices().isEmpty()) {
                        grid[row * 3 + col] = m.getChoices().getFirst();
                    }
                }
            }
        }
        render();
    }

    private void render() {
        ItemStack empty = icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.text("Empty", NamedTextColor.GRAY),
                Component.text("Click while holding an item to set it", NamedTextColor.YELLOW));
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null) {
                getInventory().setItem(GRID[i], empty);
            } else {
                getInventory().setItem(GRID[i], icon(grid[i], Component.text(grid[i].name(), NamedTextColor.WHITE),
                        Component.text("Click with an item to replace", NamedTextColor.YELLOW),
                        Component.text("Click with an empty hand to clear", NamedTextColor.YELLOW)));
            }
        }
        getInventory().setItem(23, icon(Material.SPECTRAL_ARROW, Component.text("→", NamedTextColor.GRAY)));
        getInventory().setItem(RESULT, plugin.items().create(itemId, 1));
        getInventory().setItem(HELP, icon(Material.BOOK, Component.text("How to edit", NamedTextColor.GOLD),
                Component.text("Pick up an item in your inventory,", NamedTextColor.GRAY),
                Component.text("then click a grid slot to set it.", NamedTextColor.GRAY),
                Component.text("Shift-click an item to add it.", NamedTextColor.GRAY),
                Component.text("Your items are never used up.", NamedTextColor.GRAY)));
        getInventory().setItem(DISABLE, icon(Material.BARRIER, Component.text("Disable crafting", NamedTextColor.RED),
                Component.text("Removes this recipe", NamedTextColor.GRAY)));
        getInventory().setItem(SAVE, icon(Material.LIME_CONCRETE, Component.text("Save recipe", NamedTextColor.GREEN),
                Component.text("Writes items.yml and reloads recipes", NamedTextColor.GRAY)));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    protected boolean allowsOwnInventory() {
        return true;
    }

    @Override
    protected void onOwnInventoryClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (!event.isShiftClick() || clicked == null || clicked.isEmpty()) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null) {
                grid[i] = clicked.getType();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                render();
                return;
            }
        }
    }

    @Override
    protected void onClick(Player player, int slot, InventoryClickEvent event) {
        for (int i = 0; i < 9; i++) {
            if (GRID[i] == slot) {
                ItemStack cursor = event.getCursor();
                grid[i] = cursor == null || cursor.isEmpty() ? null : cursor.getType();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                render();
                return;
            }
        }
        if (slot == SAVE) {
            save(player);
        } else if (slot == DISABLE) {
            write(null);
            plugin.messages().send(player, "admin.recipe-disabled", Messages.text("item", itemId));
            player.closeInventory();
        }
    }

    private void save(Player player) {
        Map<Material, Character> letters = new LinkedHashMap<>();
        List<String> shape = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                Material m = grid[row * 3 + col];
                line.append(m == null ? ' ' : letters.computeIfAbsent(m, k -> (char) ('A' + letters.size())));
            }
            shape.add(line.toString());
        }
        if (letters.isEmpty()) {
            plugin.messages().send(player, "admin.recipe-empty");
            return;
        }
        Map<String, Object> ingredients = new LinkedHashMap<>();
        letters.forEach((material, letter) -> ingredients.put(String.valueOf(letter), material.name()));
        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("shape", shape);
        recipe.put("ingredients", ingredients);
        write(recipe);
        plugin.messages().send(player, "admin.recipe-saved", Messages.text("item", itemId));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
        player.closeInventory();
    }

    private void write(Map<String, Object> recipe) {
        YamlConfiguration items = plugin.itemsConfig().yaml();
        items.set("recipes." + itemId, null);
        if (recipe != null) {
            items.createSection("recipes." + itemId, recipe);
        }
        plugin.itemsConfig().save();
        plugin.recipes().registerAll();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.recipes().discoverAll(online);
        }
    }
}
