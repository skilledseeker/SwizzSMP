package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /withdraw <amount> turns energy into energy items. You always keep the minimum. */
final class WithdrawCommand extends CommandBase {

    WithdrawCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 1) {
            usage(sender, "/withdraw <amount>");
            return;
        }
        parsePositive(sender, args[0]).ifPresent(amount -> {
            PlayerData data = plugin.store().get(player);
            int max = data.energy() - plugin.settings().energyMin;
            if (amount > max) {
                msg().send(player, "energy.withdraw-too-much", Messages.text("max", Math.max(0, max)));
                return;
            }
            plugin.energy().add(data, -amount);
            plugin.energy().give(player, amount);
            plugin.saveNow(player);
            msg().send(player, "energy.withdraw-success", Messages.text("amount", amount),
                    Messages.text("value", data.energy()), Messages.text("max", plugin.settings().energyMax));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? List.of("1", "2", "3") : List.of();
    }
}
