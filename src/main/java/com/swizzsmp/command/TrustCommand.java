package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** /trust <player> toggles trust, /trust list shows it. */
final class TrustCommand extends CommandBase {

    TrustCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 1) {
            usage(sender, "/trust <player> | /trust list");
            return;
        }
        PlayerData data = plugin.store().get(player);
        if (args[0].equalsIgnoreCase("list")) {
            list(player, data);
            return;
        }
        findTarget(sender, args[0]).ifPresent(target -> {
            if (target.uuid().equals(player.getUniqueId())) {
                msg().send(player, "trust.self");
                return;
            }
            if (!data.trusts(target.uuid()) && data.trusted().size() >= plugin.settings().maxTrusted) {
                msg().send(player, "trust.full", Messages.text("max", plugin.settings().maxTrusted));
                return;
            }
            boolean nowTrusted = data.toggleTrust(target.uuid());
            msg().send(player, nowTrusted ? "trust.added" : "trust.removed", Messages.text("name", target.name()));
        });
    }

    private void list(Player player, PlayerData data) {
        if (data.trusted().isEmpty()) {
            msg().send(player, "trust.list-empty");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : data.trusted()) {
            OfflinePlayer other = Bukkit.getOfflinePlayer(uuid);
            names.add(other.getName() != null ? other.getName() : uuid.toString().substring(0, 8));
        }
        msg().send(player, "trust.list", Messages.text("count", names.size()), Messages.text("names", String.join(", ", names)));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(onlineNames(last(args)));
            if ("list".startsWith(last(args).toLowerCase())) {
                options.add("list");
            }
            return options;
        }
        return List.of();
    }
}
