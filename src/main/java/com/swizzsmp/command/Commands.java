package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.items.ItemIds;
import org.bukkit.command.PluginCommand;

/** Binds every command declared in plugin.yml (DESIGN.md §10). */
public final class Commands {

    private Commands() {
    }

    public static void registerAll(SwizzPlugin plugin) {
        for (int i = 1; i <= 4; i++) {
            bind(plugin, "ability" + i, new AbilityCommand(plugin, i));
        }
        bind(plugin, "trust", new TrustCommand(plugin));
        bind(plugin, "withdraw", new WithdrawCommand(plugin));
        bind(plugin, "loadout", new LoadoutCommand(plugin));
        bind(plugin, "swizz", new SwizzCommand(plugin));
        bind(plugin, "swizzbook", new SwizzBookCommand(plugin));
        bind(plugin, "trail", new TrailCommand(plugin));

        bind(plugin, "energy", new EnergyCommand(plugin));
        bind(plugin, "giveenergy", new GiveItemCommand(plugin, ItemIds.ENERGY, "giveenergy"));
        bind(plugin, "givereroller", new GiveItemCommand(plugin, ItemIds.REROLLER, "givereroller"));
        bind(plugin, "giveupgrader", new GiveItemCommand(plugin, ItemIds.UPGRADER, "giveupgrader"));
        bind(plugin, "giveswizz", new GiveSwizzCommand(plugin));
        bind(plugin, "giveupgraded", new GiveUpgradedCommand(plugin));
        bind(plugin, "resetcooldown", new ResetCooldownCommand(plugin));
        bind(plugin, "swizzadmin", new SwizzAdminCommand(plugin));
    }

    private static void bind(SwizzPlugin plugin, String name, CommandBase command) {
        PluginCommand declared = plugin.getCommand(name);
        if (declared == null) {
            throw new IllegalStateException("Command '" + name + "' is missing from plugin.yml");
        }
        declared.setExecutor(command);
        declared.setTabCompleter(command);
    }
}
