package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.data.ServerState;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dragon Egg (DESIGN.md §13.2): right-click air with the vanilla dragon egg to give
 * every other online player with a swizz a random one for 5 minutes. 30 minute cooldown
 * for the whole server.
 */
public final class DragonEggService implements Listener {

    private static final String COOLDOWN_KEY = "dragon-egg-ready-at";

    private final SwizzPlugin plugin;
    private final ServerState state;

    public DragonEggService(SwizzPlugin plugin, ServerState state) {
        this.plugin = plugin;
        this.state = state;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND
                || event.getItem() == null || event.getItem().getType() != Material.DRAGON_EGG) {
            return;
        }
        Settings s = plugin.settings();
        if (!s.dragonEggEnabled) {
            return;
        }
        Player user = event.getPlayer();
        if (plugin.freeze().isFrozen(user)) {
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = state.getLong(COOLDOWN_KEY);
        if (readyAt > now) {
            plugin.messages().send(user, "dragon-egg.cooldown", Messages.text("time", Time.format(readyAt - now)));
            return;
        }
        long duration = Time.secondsToMillis(s.dragonEggDurationMinutes * 60);
        state.setLong(COOLDOWN_KEY, now + Time.secondsToMillis(s.dragonEggCooldownMinutes * 60));
        String time = Time.format(duration);

        Set<UUID> affected = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (s.dragonEggExcludeUser && player.equals(user)) {
                continue;
            }
            PlayerData data = plugin.store().get(player);
            if (!data.hasSwizz()) {
                continue;
            }
            SwizzType random = randomOtherThan(data.swizz());
            data.setOverride(random, now + duration);
            affected.add(player.getUniqueId());
            plugin.onStateChanged(player);
            plugin.messages().send(player, "dragon-egg.yours", Messages.comp("swizz", random.displayComponent()),
                    Messages.text("time", time));
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }
        Bukkit.getServer().sendMessage(plugin.messages().get("dragon-egg.used",
                Messages.text("name", user.getName()), Messages.text("time", time)));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
        user.getWorld().spawnParticle(Particle.PORTAL, user.getLocation().add(0, 1, 0), 150, 1, 1, 1, 0.5);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : affected) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                PlayerData data = plugin.store().get(player);
                if (data.overrideSwizz() != null && data.overrideUntil() <= System.currentTimeMillis()) {
                    data.setOverride(null, 0);
                    plugin.onStateChanged(player);
                    plugin.messages().send(player, "dragon-egg.ended");
                }
            }
        }, Time.secondsToTicks(s.dragonEggDurationMinutes * 60) + 1);
    }

    public void resetCooldown() {
        state.setLong(COOLDOWN_KEY, 0);
    }

    private static SwizzType randomOtherThan(SwizzType current) {
        List<SwizzType> options = new ArrayList<>(List.of(SwizzType.values()));
        options.remove(current);
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
