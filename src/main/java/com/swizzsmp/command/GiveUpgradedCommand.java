package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /giveupgraded <player>: upgrades a player's current swizz without a ritual (admin). */
final class GiveUpgradedCommand extends CommandBase {

    GiveUpgradedCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length != 1) {
            usage(sender, "/giveupgraded <player>");
            return;
        }
        findTarget(sender, args[0]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            if (!data.hasSwizz()) {
                msg().send(sender, "admin.no-swizz", Messages.text("name", target.name()));
                return;
            }
            data.setUpgraded(true);
            if (target.online() != null) {
                plugin.onStateChanged(target.online());
                msg().send(target.online(), "admin.you-were-upgraded", Messages.comp("swizz", data.swizz().displayComponent()));
            } else {
                plugin.store().saveAsync(data);
            }
            msg().send(sender, "admin.upgrade-set", Messages.text("name", target.name()), Messages.text("value", true));
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? onlineNames(last(args)) : List.of();
    }
}
