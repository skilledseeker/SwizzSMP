package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /ability1 … /ability4. */
final class AbilityCommand extends CommandBase {

    private final int index;

    AbilityCommand(SwizzPlugin plugin, int index) {
        super(plugin, "swizz.use");
        this.index = index;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player != null) {
            plugin.abilities().activate(player, index);
        }
    }
}
