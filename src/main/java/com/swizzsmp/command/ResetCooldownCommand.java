package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /resetcooldown <player>: clears ability cooldowns, passive reloads and the loadout timer. */
final class ResetCooldownCommand extends CommandBase {

    ResetCooldownCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length != 1) {
            usage(sender, "/resetcooldown <player>");
            return;
        }
        findTarget(sender, args[0]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            plugin.cooldowns().resetAll(data);
            if (target.online() == null) {
                plugin.store().saveAsync(data);
            }
            msg().send(sender, "admin.cooldowns-reset", Messages.text("name", target.name()));
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? onlineNames(last(args)) : List.of();
    }
}
