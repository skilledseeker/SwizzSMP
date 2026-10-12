package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /swizzbook opens the recipe and guide menu. */
final class SwizzBookCommand extends CommandBase {

    SwizzBookCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player != null) {
            plugin.openBook(player);
        }
    }
}
