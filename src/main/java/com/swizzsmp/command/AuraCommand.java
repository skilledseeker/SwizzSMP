package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /aura [player] (admin, DESIGN.md §13.7): plays the showcase cinematic around a player. */
final class AuraCommand extends CommandBase {

    AuraCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length > 1) {
            usage(sender, "/aura [player]");
            return;
        }
        Player target = args.length == 0 ? requirePlayer(sender) : findOnline(sender, args[0]).orElse(null);
        if (target == null) {
            return;
        }
        boolean started = plugin.auraShowcase().play(target);
        msg().send(sender, started ? "admin.aura-started" : "admin.aura-busy", Messages.text("name", target.getName()));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? onlineNames(last(args)) : List.of();
    }
}
