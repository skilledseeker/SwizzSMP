package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Shared helpers for all commands. */
abstract class CommandBase implements BasicCommand {

    protected final SwizzPlugin plugin;
    private final String permission;

    protected CommandBase(SwizzPlugin plugin, String permission) {
        this.plugin = plugin;
        this.permission = permission;
    }

    @Override
    public final void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        run(sender, args);
    }

    protected abstract void run(CommandSender sender, String[] args);

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return complete(source.getSender(), args);
    }

    protected Collection<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

    @Override
    public String permission() {
        return permission;
    }

    protected Messages msg() {
        return plugin.messages();
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        msg().send(sender, "general.players-only");
        return null;
    }

    protected void usage(CommandSender sender, String usage) {
        msg().send(sender, "general.usage", Messages.text("usage", usage));
    }

    /** A player known to the server: online, or offline with saved Swizz data. */
    protected record Target(UUID uuid, String name, Player online) {
    }

    protected Optional<Target> findTarget(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName(), online));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && plugin.store().exists(cached.getUniqueId())) {
            return Optional.of(new Target(cached.getUniqueId(), cached.getName() == null ? name : cached.getName(), null));
        }
        msg().send(sender, "general.player-not-found", Messages.text("name", name));
        return Optional.empty();
    }

    protected Optional<Player> findOnline(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            msg().send(sender, "general.player-not-found", Messages.text("name", name));
        }
        return Optional.ofNullable(online);
    }

    protected Optional<Integer> parsePositive(CommandSender sender, String input) {
        try {
            int value = Integer.parseInt(input);
            if (value > 0) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        msg().send(sender, "general.invalid-number", Messages.text("input", input));
        return Optional.empty();
    }

    protected static List<String> onlineNames(String prefix) {
        List<String> names = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    protected static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    /** The argument being typed (Paper passes an empty last arg after a trailing space). */
    protected static String last(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }
}
