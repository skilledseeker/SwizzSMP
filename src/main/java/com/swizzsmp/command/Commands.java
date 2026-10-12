package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.items.ItemIds;

/** Registers every command (DESIGN.md §10). */
public final class Commands {

    private Commands() {
    }

    public static void registerAll(SwizzPlugin plugin) {
        for (int i = 1; i <= 4; i++) {
            plugin.registerCommand("ability" + i, "Use swizz ability " + i, new AbilityCommand(plugin, i));
        }
        plugin.registerCommand("trust", "Trust a player so your abilities don't hit them", new TrustCommand(plugin));
        plugin.registerCommand("withdraw", "Withdraw energy as items", new WithdrawCommand(plugin));
        plugin.registerCommand("loadout", "Choose which perks your energy unlocks", new LoadoutCommand(plugin));
        plugin.registerCommand("swizz", "Your swizz status and settings", new SwizzCommand(plugin));
        plugin.registerCommand("swizzbook", "Recipes and guides for every swizz", new SwizzBookCommand(plugin));

        plugin.registerCommand("energy", "Manage a player's energy", new EnergyCommand(plugin));
        plugin.registerCommand("giveenergy", "Give energy items", new GiveItemCommand(plugin, ItemIds.ENERGY, "giveenergy"));
        plugin.registerCommand("givereroller", "Give rerollers", new GiveItemCommand(plugin, ItemIds.REROLLER, "givereroller"));
        plugin.registerCommand("giveupgrader", "Give upgraders", new GiveItemCommand(plugin, ItemIds.UPGRADER, "giveupgrader"));
        plugin.registerCommand("giveswizz", "Set a player's swizz", new GiveSwizzCommand(plugin));
        plugin.registerCommand("resetcooldown", "Reset all of a player's cooldowns", new ResetCooldownCommand(plugin));
        plugin.registerCommand("swizzadmin", "SwizzSMP admin tools", new SwizzAdminCommand(plugin));
    }
}
