package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.util.Time;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** /swizzadmin reload | give | upgrade | info | ritual (admin). */
final class SwizzAdminCommand extends CommandBase {

    private static final String USAGE = "/swizzadmin <reload | give <item> <player> [amount] | upgrade <player> <true|false> | info <player> | ritual <list | start <player> | cancel <player>> | dragonegg reset>";

    SwizzAdminCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            usage(sender, USAGE);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadEverything();
                msg().send(sender, "general.reloaded");
            }
            case "give" -> give(sender, args);
            case "upgrade" -> upgrade(sender, args);
            case "info" -> info(sender, args);
            case "ritual" -> plugin.rituals().adminCommand(sender, args);
            case "dragonegg" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                    plugin.dragonEgg().resetCooldown();
                    msg().send(sender, "dragon-egg.admin-reset");
                } else {
                    usage(sender, "/swizzadmin dragonegg reset");
                }
            }
            default -> usage(sender, USAGE);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/swizzadmin give <item> <player> [amount]");
            return;
        }
        String id = args[1].toLowerCase();
        if (!ItemIds.all().contains(id)) {
            msg().send(sender, "admin.unknown-item", Messages.text("input", id), Messages.text("options", String.join(", ", ItemIds.all())));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            var parsed = parsePositive(sender, args[3]);
            if (parsed.isEmpty()) {
                return;
            }
            amount = parsed.get();
        }
        int finalAmount = amount;
        findOnline(sender, args[2]).ifPresent(target -> GiveItemCommand.give(plugin, sender, target, id, finalAmount));
    }

    private void upgrade(CommandSender sender, String[] args) {
        if (args.length != 3) {
            usage(sender, "/swizzadmin upgrade <player> <true|false>");
            return;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        findTarget(sender, args[1]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            data.setUpgraded(value);
            if (target.online() != null) {
                plugin.onStateChanged(target.online());
            } else {
                plugin.store().saveAsync(data);
            }
            msg().send(sender, "admin.upgrade-set", Messages.text("name", target.name()), Messages.text("value", value));
        });
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            usage(sender, "/swizzadmin info <player>");
            return;
        }
        findTarget(sender, args[1]).ifPresent(target -> {
            PlayerData data = plugin.store().loadAny(target.uuid());
            String cooldowns = data.cooldowns().entrySet().stream()
                    .map(e -> e.getKey() + "=" + Time.format(e.getValue()))
                    .collect(Collectors.joining(", "));
            msg().send(sender, "admin.info",
                    Messages.text("name", target.name()),
                    Messages.text("swizz", data.swizz() == null ? "none" : data.swizz().id()),
                    Messages.text("energy", data.energy()),
                    Messages.text("upgraded", data.upgraded()),
                    Messages.text("loadout", LoadoutCommand.describe(data.loadout())),
                    Messages.text("trusted", data.trusted().size()),
                    Messages.text("cooldowns", cooldowns.isEmpty() ? "none" : cooldowns));
        });
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filter(List.of("reload", "give", "upgrade", "info", "ritual", "dragonegg"), last(args));
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "give" -> switch (args.length) {
                case 2 -> filter(ItemIds.all(), last(args));
                case 3 -> onlineNames(last(args));
                default -> List.of();
            };
            case "upgrade" -> args.length == 2 ? onlineNames(last(args)) : args.length == 3 ? filter(List.of("true", "false"), last(args)) : List.of();
            case "info" -> args.length == 2 ? onlineNames(last(args)) : List.of();
            case "ritual" -> args.length == 2 ? filter(List.of("list", "start", "cancel"), last(args)) : args.length == 3 ? onlineNames(last(args)) : List.of();
            case "dragonegg" -> args.length == 2 ? filter(List.of("reset"), last(args)) : List.of();
            default -> List.of();
        };
    }
}
