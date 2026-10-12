package com.swizzsmp.items;

import com.swizzsmp.config.ConfigFile;
import com.swizzsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Registers the shaped recipes from items.yml → recipes. */
public final class RecipeService {

    private final ConfigFile file;
    private final CustomItems items;
    private final Logger logger;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeService(ConfigFile file, CustomItems items, Logger logger) {
        this.file = file;
        this.items = items;
        this.logger = logger;
    }

    public static NamespacedKey keyFor(String itemId) {
        return Keys.key("recipe_" + itemId);
    }

    public void registerAll() {
        unregisterAll();
        ConfigurationSection recipes = file.yaml().getConfigurationSection("recipes");
        if (recipes == null) {
            return;
        }
        List<Recipe> built = new ArrayList<>();
        for (String id : recipes.getKeys(false)) {
            ConfigurationSection def = recipes.getConfigurationSection(id);
            if (def == null || !items.exists(id)) {
                logger.warning("items.yml: recipe for unknown item '" + id + "' skipped");
                continue;
            }
            try {
                NamespacedKey key = keyFor(id);
                ShapedRecipe recipe = new ShapedRecipe(key, items.create(id, 1));
                List<String> shape = def.getStringList("shape");
                recipe.shape(shape.toArray(String[]::new));
                ConfigurationSection ingredients = def.getConfigurationSection("ingredients");
                if (ingredients == null) {
                    throw new IllegalArgumentException("no ingredients");
                }
                for (Map.Entry<String, Object> entry : ingredients.getValues(false).entrySet()) {
                    Material material = Material.matchMaterial(String.valueOf(entry.getValue()));
                    if (material == null || entry.getKey().length() != 1) {
                        throw new IllegalArgumentException("bad ingredient " + entry.getKey() + ": " + entry.getValue());
                    }
                    recipe.setIngredient(entry.getKey().charAt(0), material);
                }
                built.add(recipe);
                registered.add(key);
            } catch (RuntimeException e) {
                logger.warning("items.yml: recipe '" + id + "' is invalid (" + e.getMessage() + ") and was skipped");
            }
        }
        Bukkit.addRecipes(built);
    }

    public void unregisterAll() {
        if (!registered.isEmpty()) {
            Bukkit.removeRecipes(List.copyOf(registered));
            registered.clear();
        }
    }

    public List<NamespacedKey> registered() {
        return registered;
    }

    public void discoverAll(Player player) {
        player.discoverRecipes(registered);
    }
}
