package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /trail [on|off]: turns your cherry blossom trail off and on (DESIGN.md §13.5). */
final class TrailCommand extends CommandBase {

    TrailCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.petalTrail().hasAccess(player)) {
            msg().send(player, "trail.no-access");
            return;
        }
        PlayerData data = plugin.store().get(player);
        boolean on;
        if (args.length == 0) {
            on = !data.trailEnabled();
        } else if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) {
            on = args[0].equalsIgnoreCase("on");
        } else {
            usage(sender, "/trail [on|off]");
            return;
        }
        data.setTrailEnabled(on);
        msg().send(player, "trail.toggled", Messages.comp("state", msg().get(on ? "swizz.state-on" : "swizz.state-off")));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? filter(List.of("on", "off"), last(args)) : List.of();
    }
}
