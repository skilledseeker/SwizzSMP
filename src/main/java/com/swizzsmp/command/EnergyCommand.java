package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /energy set|add|remove|view <player> [amount] (admin). */
final class EnergyCommand extends CommandBase {

    EnergyCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/energy <set|add|remove|view> <player> [amount]");
            return;
        }
        String action = args[0].toLowerCase();
        boolean needsAmount = !action.equals("view");
        if (!List.of("set", "add", "remove", "view").contains(action) || (needsAmount && args.length < 3)) {
            usage(sender, "/energy <set|add|remove|view> <player> [amount]");
            return;
        }
        findTarget(sender, args[1]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            if (action.equals("view")) {
                msg().send(sender, "energy.admin-view", Messages.text("name", target.name()), Messages.text("value", data.energy()));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                msg().send(sender, "general.invalid-number", Messages.text("input", args[2]));
                return;
            }
            switch (action) {
                case "set" -> plugin.energy().set(data, amount);
                case "add" -> plugin.energy().add(data, amount);
                default -> plugin.energy().add(data, -amount);
            }
            if (target.online() == null) {
                plugin.store().saveAsync(data);
            }
            msg().send(sender, "energy.admin-set", Messages.text("name", target.name()), Messages.text("value", data.energy()));
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 0, 1 -> filter(List.of("set", "add", "remove", "view"), last(args));
            case 2 -> onlineNames(last(args));
            default -> List.of();
        };
    }
}
