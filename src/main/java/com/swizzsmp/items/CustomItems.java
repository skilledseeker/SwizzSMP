package com.swizzsmp.items;

import com.swizzsmp.config.ConfigFile;
import com.swizzsmp.config.Messages;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Builds and identifies custom items from items.yml (DESIGN.md §9). */
public final class CustomItems {

    private final ConfigFile file;
    private final Logger logger;

    public CustomItems(ConfigFile file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public ItemStack create(String id, int amount) {
        ConfigurationSection def = file.yaml().getConfigurationSection("items." + id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown custom item " + id);
        }
        Material material = Material.matchMaterial(def.getString("material", "ECHO_SHARD"));
        if (material == null || !material.isItem()) {
            logger.warning("items.yml: bad material for " + id + ", using ECHO_SHARD");
            material = Material.ECHO_SHARD;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, id);
        meta.itemName(Messages.MM.deserialize(def.getString("name", id)));
        List<Component> lore = new ArrayList<>();
        for (String line : def.getStringList("lore")) {
            lore.add(Messages.MM.deserialize(line));
        }
        meta.lore(lore);
        String model = def.getString("model", "");
        if (!model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) {
                meta.setItemModel(key);
            } else {
                logger.warning("items.yml: bad model '" + model + "' for " + id);
            }
        }
        if (def.contains("max-stack")) {
            meta.setMaxStackSize(Math.max(1, Math.min(99, def.getInt("max-stack"))));
        }
        meta.setEnchantmentGlintOverride(true);
        if (ItemIds.SWORD.equals(id) || ItemIds.MACE.equals(id) || ItemIds.BREACH_MACE.equals(id)) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        item.setItemMeta(meta);
        // The resource pack maps this string to the item's own texture; without the pack the
        // vanilla "model" above is what players see.
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(id).build());
        return item;
    }

    public ItemStack swizzItem(SwizzType type) {
        return create(type.itemId(), 1);
    }

    /** The custom item id of a stack, or null for vanilla items. */
    public static String idOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
    }

    public static boolean isCustom(ItemStack item) {
        return idOf(item) != null;
    }

    public static boolean is(ItemStack item, String id) {
        return id.equals(idOf(item));
    }

    public boolean exists(String id) {
        return file.yaml().isConfigurationSection("items." + id);
    }
}
