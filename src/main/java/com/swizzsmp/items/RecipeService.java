package com.swizzsmp.items;

import com.swizzsmp.config.ConfigFile;
import com.swizzsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registers the shaped recipes from items.yml → recipes.
 *
 * Ingredient syntax (the value of each letter):
 * <ul>
 *   <li>{@code DIAMOND} — any item of that material</li>
 *   <li>{@code GOLDEN_APPLE,ENCHANTED_GOLDEN_APPLE} — any of several materials</li>
 *   <li>{@code POTION:LONG_WATER_BREATHING} — a potion (or SPLASH_POTION, LINGERING_POTION,
 *       TIPPED_ARROW) of exactly that potion type</li>
 * </ul>
 */
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
                    if (entry.getKey().length() != 1) {
                        throw new IllegalArgumentException("ingredient keys must be one letter: " + entry.getKey());
                    }
                    recipe.setIngredient(entry.getKey().charAt(0), parseIngredient(String.valueOf(entry.getValue())));
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

    // ── Ingredient syntax ─────────────────────────────────────────────────────

    /** Parses one ingredient value (see the class comment). */
    public static RecipeChoice parseIngredient(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.contains(",")) {
            List<Material> materials = new ArrayList<>();
            for (String part : value.split(",")) {
                materials.add(material(part.trim()));
            }
            return new RecipeChoice.MaterialChoice(materials);
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return new RecipeChoice.MaterialChoice(material(value));
        }
        Material material = material(value.substring(0, colon));
        String extra = value.substring(colon + 1);
        if (isPotionLike(material)) {
            PotionType type;
            try {
                type = PotionType.valueOf(extra);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown potion type " + extra);
            }
            // Matches any potion of that type, even if renamed or otherwise modified.
            return RecipeChoice.predicateChoice(stack -> stack != null && stack.getType() == material
                    && stack.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType()
                    && meta.getBasePotionType() == type, potion(material, type));
        }
        throw new IllegalArgumentException("'" + material + ":" + extra + "' is not supported (only potions take a type)");
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("unknown item " + name);
        }
        return material;
    }

    public static boolean isPotionLike(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW;
    }

    public static ItemStack potion(Material material, PotionType type) {
        ItemStack stack = new ItemStack(material);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(type);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The items.yml value that describes this stack as an ingredient. */
    public static String ingredientKey(ItemStack stack) {
        if (isPotionLike(stack.getType()) && stack.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType()) {
            return stack.getType().name() + ":" + meta.getBasePotionType().name();
        }
        return stack.getType().name();
    }

    /** A stack to show for a recipe choice in menus. */
    @SuppressWarnings("deprecation")
    public static ItemStack displayStack(RecipeChoice choice) {
        if (choice == null) {
            return null;
        }
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            return materials.getChoices().isEmpty() ? null : new ItemStack(materials.getChoices().getFirst());
        }
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            return exact.getChoices().isEmpty() ? null : exact.getChoices().getFirst().clone();
        }
        try {
            ItemStack shown = choice.getItemStack(); // PredicateChoice: its display stack
            return shown == null || shown.isEmpty() ? null : shown.clone();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
