package com.swizzsmp.items;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Allay;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Stops custom items from being used as their vanilla material (DESIGN.md §9.3):
 * no crafting with them, smelting, brewing, smithing, trading, placing or eating.
 * Also enforces "you need a swizz to craft a Reroller".
 */
public final class ItemGuardListener implements Listener {

    private final SwizzPlugin plugin;

    public ItemGuardListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean anyCustom(ItemStack[] items) {
        for (ItemStack item : items) {
            if (CustomItems.isCustom(item)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        if (anyCustom(inv.getMatrix())) {
            inv.setResult(null);
            return;
        }
        if (isRerollerRecipe(event.getRecipe()) && !viewerHasSwizz(event.getView().getPlayer())) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (anyCustom(event.getInventory().getMatrix())) {
            event.setCancelled(true);
            return;
        }
        if (isRerollerRecipe(event.getRecipe()) && !viewerHasSwizz(event.getWhoClicked())) {
            event.setCancelled(true);
            plugin.messages().send(event.getWhoClicked(), "items.craft-reroller-needs-swizz");
            return;
        }
        if (plugin.settings().swizzCraftRequiresNoSwizz
                && ItemIds.swizzOf(CustomItems.idOf(event.getRecipe().getResult())).isPresent()
                && viewerHasSwizz(event.getWhoClicked())) {
            event.setCancelled(true);
            plugin.messages().send(event.getWhoClicked(), "swizz.already-have");
        }
    }

    private boolean isRerollerRecipe(Recipe recipe) {
        return recipe instanceof ShapedRecipe shaped
                && shaped.getKey().equals(RecipeService.keyFor(ItemIds.REROLLER));
    }

    private boolean viewerHasSwizz(HumanEntity human) {
        return human instanceof Player player && plugin.store().get(player).hasSwizz();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter && anyCustom(crafter.getInventory().getContents())) {
            event.setCancelled(true);
            return;
        }
        // A crafter has no player, so rules that depend on the crafter's swizz can't be checked.
        String result = CustomItems.idOf(event.getResult());
        if (ItemIds.REROLLER.equals(result)
                || (plugin.settings().swizzCraftRequiresNoSwizz && ItemIds.swizzOf(result).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        if (anyCustom(event.getInventory().getContents())) {
            ItemStack base = event.getInventory().getInputEquipment();
            // Allow upgrading/trimming the Swizz Sword and Mace like normal gear.
            String id = CustomItems.idOf(base);
            if (id == null || ItemIds.isToken(id) || anyToken(event.getInventory().getContents())) {
                event.setResult(null);
            }
        }
    }

    private static boolean anyToken(ItemStack[] items) {
        for (ItemStack item : items) {
            if (ItemIds.isToken(CustomItems.idOf(item))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (CustomItems.isCustom(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (CustomItems.isCustom(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (CustomItems.isCustom(event.getContents().getIngredient()) || CustomItems.isCustom(event.getContents().getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT || event.getRawSlot() != 2) {
            return;
        }
        MerchantInventory merchant = (MerchantInventory) event.getInventory();
        if (CustomItems.isCustom(merchant.getItem(0)) || CustomItems.isCustom(merchant.getItem(1))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (ItemIds.isToken(CustomItems.idOf(event.getItemInHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (ItemIds.isToken(CustomItems.idOf(event.getItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGiveAllay(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (event.getRightClicked() instanceof Allay && ItemIds.isToken(CustomItems.idOf(hand))) {
            event.setCancelled(true);
        }
    }
}
