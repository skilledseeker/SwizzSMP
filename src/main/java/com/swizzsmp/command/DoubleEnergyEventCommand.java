package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /doubleenergyevent [start [minutes] | stop] (admin, DESIGN.md §5.3). No argument starts it. */
final class DoubleEnergyEventCommand extends CommandBase {

    DoubleEnergyEventCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.admin");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "start" : args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                double minutes = plugin.settings().doubleEnergyMinutes;
                if (args.length >= 2) {
                    var parsed = parsePositive(sender, args[1]);
                    if (parsed.isEmpty()) {
                        return;
                    }
                    minutes = parsed.get();
                }
                plugin.doubleEnergy().begin(minutes);
            }
            case "stop" -> {
                if (!plugin.doubleEnergy().isActive()) {
                    msg().send(sender, "event.not-running");
                    return;
                }
                plugin.doubleEnergy().end();
            }
            default -> usage(sender, "/doubleenergyevent [start [minutes] | stop]");
        }
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? filter(List.of("start", "stop"), last(args)) : List.of();
    }
}
