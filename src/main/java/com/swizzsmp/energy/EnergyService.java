package com.swizzsmp.energy;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.items.ItemIds;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Energy changes (DESIGN.md §5.1). Everything that changes energy goes through here. */
public final class EnergyService {

    private final SwizzPlugin plugin;

    public EnergyService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public int clamp(int value) {
        Settings s = plugin.settings();
        return Math.max(s.energyMin, Math.min(s.energyMax, value));
    }

    /**
     * Sets energy (clamped) and refreshes everything that depends on it.
     * @return the energy actually set
     */
    public int set(PlayerData data, int value) {
        int clamped = clamp(value);
        data.setEnergy(clamped);
        plugin.store().getIfOnline(data.uuid())
                .map(d -> plugin.getServer().getPlayer(d.uuid()))
                .ifPresent(plugin::onStateChanged);
        return clamped;
    }

    /** Adds (or removes, if negative) energy. @return the change actually applied */
    public int add(PlayerData data, int delta) {
        int before = data.energy();
        int after = set(data, before + delta);
        return after - before;
    }

    public boolean isAtMax(PlayerData data) {
        return data.energy() >= plugin.settings().energyMax;
    }

    public ItemStack item(int amount) {
        return plugin.items().create(ItemIds.ENERGY, amount);
    }

    public void drop(Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        Item dropped = location.getWorld().dropItemNaturally(location, item(amount));
        dropped.setUnlimitedLifetime(true);
    }

    /** Gives energy items; anything that doesn't fit drops at the player's feet. */
    public void give(Player player, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(64, left);
            left -= stack;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item(stack));
            overflow.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
