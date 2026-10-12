package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** /loadout opens the GUI; /loadout <p1> <p2> <p3> sets the perk order directly. */
final class LoadoutCommand extends CommandBase {

    LoadoutCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            plugin.openLoadout(player);
            return;
        }
        if (args.length != 3) {
            usage(sender, "/loadout [a1|a2|passive] [a1|a2|passive] [a1|a2|passive]");
            return;
        }
        List<Perk> order = new ArrayList<>();
        for (String arg : args) {
            Optional<Perk> perk = Perk.parse(arg);
            if (perk.isEmpty()) {
                msg().send(player, "loadout.invalid");
                return;
            }
            order.add(perk.get());
        }
        if (!Loadout.isValid(order)) {
            msg().send(player, "loadout.invalid");
            return;
        }
        plugin.abilities().changeLoadout(player, order);
    }

    static String describe(List<Perk> order) {
        return Loadout.describe(order);
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 3 ? filter(List.of("a1", "a2", "passive"), last(args)) : List.of();
    }
}
