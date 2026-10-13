package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * /cinematic &lt;name&gt; [player] and /aura [player] (admin, DESIGN.md §13.7): plays a
 * showcase cinematic around a player, or around yourself without a name.
 */
final class CinematicCommand extends CommandBase {

    /** The cinematic this command always plays (/aura), or null to take it as the first argument. */
    private final String fixed;

    CinematicCommand(SwizzPlugin plugin, String fixed) {
        super(plugin, "swizz.admin");
        this.fixed = fixed;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String usage = fixed != null ? "/" + fixed + " [player]" : "/cinematic <" + String.join("|", plugin.cinematics().names()) + "> [player]";
        int nameArgs = fixed != null ? 0 : 1;
        if (args.length < nameArgs || args.length > nameArgs + 1) {
            usage(sender, usage);
            return;
        }
        String name = fixed != null ? fixed : args[0].toLowerCase();
        if (!plugin.cinematics().names().contains(name)) {
            msg().send(sender, "admin.cinematic-unknown", Messages.text("input", name),
                    Messages.text("options", String.join(", ", plugin.cinematics().names())));
            return;
        }
        Player target = args.length == nameArgs ? requirePlayer(sender) : findOnline(sender, args[nameArgs]).orElse(null);
        if (target == null) {
            return;
        }
        String key = switch (plugin.cinematics().play(name, target)) {
            case STARTED -> "admin.cinematic-started";
            case BUSY -> "admin.cinematic-busy";
            case UNKNOWN -> "admin.cinematic-unknown";
        };
        msg().send(sender, key, Messages.text("cinematic", name), Messages.text("name", target.getName()),
                Messages.text("input", name), Messages.text("options", String.join(", ", plugin.cinematics().names())));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (fixed == null && args.length <= 1) {
            return filter(plugin.cinematics().names(), last(args));
        }
        return args.length == (fixed == null ? 2 : 1) ? onlineNames(last(args)) : List.of();
    }
}
