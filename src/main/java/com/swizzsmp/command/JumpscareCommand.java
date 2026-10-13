package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /jumpscare [player|all] (admin, DESIGN.md §13.6). No argument scares everyone except
 * you, {@code all} includes you, and a name scares just that player (use your own to preview).
 */
final class JumpscareCommand extends CommandBase {

    JumpscareCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length > 1) {
            usage(sender, "/jumpscare [player|all]");
            return;
        }
        List<Player> targets = new ArrayList<>();
        if (args.length == 1 && !args[0].equalsIgnoreCase("all")) {
            findOnline(sender, args[0]).ifPresent(targets::add);
            if (targets.isEmpty()) {
                return;
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (args.length == 1 || player != sender) {
                    targets.add(player);
                }
            }
        }
        if (targets.isEmpty()) {
            msg().send(sender, "admin.jumpscare-nobody");
            return;
        }
        targets.forEach(plugin.jumpscare()::scare);
        msg().send(sender, "admin.jumpscared", Messages.text("count", targets.size()));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(onlineNames(last(args)));
        options.addAll(filter(List.of("all"), last(args)));
        return options;
    }
}
