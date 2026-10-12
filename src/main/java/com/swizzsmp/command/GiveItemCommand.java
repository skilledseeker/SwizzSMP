package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** /giveenergy, /givereroller, /giveupgrader: <player> [amount] (admin). */
final class GiveItemCommand extends CommandBase {

    private final String itemId;
    private final String label;

    GiveItemCommand(SwizzPlugin plugin, String itemId, String label) {
        super(plugin, "swizz.admin");
        this.itemId = itemId;
        this.label = label;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            usage(sender, "/" + label + " <player> [amount]");
            return;
        }
        int amount = 1;
        if (args.length == 2) {
            var parsed = parsePositive(sender, args[1]);
            if (parsed.isEmpty()) {
                return;
            }
            amount = parsed.get();
        }
        int finalAmount = amount;
        findOnline(sender, args[0]).ifPresent(target -> give(plugin, sender, target, itemId, finalAmount));
    }

    static void give(SwizzPlugin plugin, CommandSender sender, Player target, String itemId, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = plugin.items().create(itemId, 1);
            int size = Math.min(left, stack.getMaxStackSize());
            stack.setAmount(size);
            left -= size;
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            overflow.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        plugin.messages().send(sender, "admin.given", Messages.text("amount", amount),
                Messages.text("item", itemId), Messages.text("name", target.getName()));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? onlineNames(last(args)) : List.of();
    }
}
