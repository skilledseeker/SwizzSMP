package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** /giveswizz <swizz> <player>: sets the player's swizz directly (admin, D-27). */
final class GiveSwizzCommand extends CommandBase {

    GiveSwizzCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length != 2) {
            usage(sender, "/giveswizz <swizz> <player>");
            return;
        }
        Optional<SwizzType> type = SwizzType.parse(args[0]);
        if (type.isEmpty()) {
            msg().send(sender, "general.invalid-swizz", Messages.text("input", args[0]), Messages.text("options", SwizzType.optionsList()));
            return;
        }
        findTarget(sender, args[1]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            data.setSwizz(type.get());
            if (target.online() != null) {
                plugin.onStateChanged(target.online());
                msg().send(target.online(), "admin.your-swizz-set", Messages.comp("swizz", type.get().displayComponent()));
            } else {
                plugin.store().saveAsync(data);
            }
            msg().send(sender, "admin.swizz-set", Messages.text("name", target.name()), Messages.comp("swizz", type.get().displayComponent()));
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 0, 1 -> filter(Arrays.stream(SwizzType.values()).map(SwizzType::id).toList(), last(args));
            case 2 -> onlineNames(last(args));
            default -> List.of();
        };
    }
}
