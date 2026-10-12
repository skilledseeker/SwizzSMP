package com.swizzsmp.command;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /swizz [info|hud|controls|heartbeat]: your status and personal toggles. */
final class SwizzCommand extends CommandBase {

    SwizzCommand(SwizzPlugin plugin) {
        super(plugin, "swizz.use");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerData data = plugin.store().get(player);
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "hud" -> {
                data.setHudEnabled(!data.hudEnabled());
                msg().send(player, "swizz.hud-toggled", Messages.comp("state", state(data.hudEnabled())));
            }
            case "controls" -> {
                data.setControlsEnabled(!data.controlsEnabled());
                msg().send(player, "swizz.controls-toggled", Messages.comp("state", state(data.controlsEnabled())));
            }
            case "heartbeat" -> {
                data.setHeartbeatEnabled(!data.heartbeatEnabled());
                msg().send(player, "swizz.heartbeat-toggled", Messages.comp("state", state(data.heartbeatEnabled())));
            }
            case "info" -> info(player, data);
            default -> usage(sender, "/swizz [info|hud|controls|heartbeat]");
        }
    }

    private Component state(boolean on) {
        return msg().get(on ? "swizz.state-on" : "swizz.state-off");
    }

    private void info(Player player, PlayerData data) {
        if (!data.hasSwizz()) {
            msg().send(player, "swizz.none");
            return;
        }
        SwizzType type = data.swizz();
        msg().send(player, "swizz.info-header", Messages.comp("swizz", type.displayComponent()));
        msg().send(player, "swizz.info-energy",
                Messages.text("energy", "●".repeat(data.energy()) + "○".repeat(Math.max(0, plugin.settings().energyMax - data.energy()))),
                Messages.text("value", data.energy()), Messages.text("max", plugin.settings().energyMax));
        msg().send(player, "swizz.info-upgraded", Messages.comp("value", state(data.upgraded())));
        msg().send(player, "swizz.info-loadout", Messages.text("value", LoadoutCommand.describe(data.loadout())));
        for (Perk perk : Perk.values()) {
            Component perkState;
            if (!Loadout.isUnlocked(data.loadout(), data.energy(), perk)) {
                perkState = Component.text("locked (needs " + Loadout.requiredEnergy(data.loadout(), perk) + " energy)", NamedTextColor.DARK_GRAY);
            } else {
                String key = perk == Perk.ABILITY_1 ? "A1" : perk == Perk.ABILITY_2 ? "A2" : "P:" + type.name();
                long left = data.cooldown(key);
                Component bonus = Loadout.hasCooldownBonus(data.loadout(), data.energy(), perk)
                        ? Component.text(" (-25% cooldown)", NamedTextColor.AQUA) : Component.empty();
                perkState = (left > 0 ? Component.text(Time.format(left), NamedTextColor.RED)
                        : Component.text("ready", NamedTextColor.GREEN)).append(bonus);
            }
            msg().send(player, "swizz.info-perk", Messages.text("perk", perk.displayName()), Messages.comp("state", perkState));
        }
        msg().send(player, "swizz.info-trusted", Messages.text("count", data.trusted().size()));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? filter(List.of("info", "hud", "controls", "heartbeat"), last(args)) : List.of();
    }
}
