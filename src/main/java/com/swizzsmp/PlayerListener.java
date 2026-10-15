package com.swizzsmp;

import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.concurrent.ThreadLocalRandom;

/** Player lifecycle (load, save, cleanup) and the Sneak+F / Sneak+Q ability keys. */
public final class PlayerListener implements Listener {

    private final SwizzPlugin plugin;

    public PlayerListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.store().loadOnline(player);
        if (!data.hasSwizz() && plugin.settings().assignRandomOnFirstJoin) {
            SwizzType[] types = SwizzType.values();
            data.setSwizz(types[ThreadLocalRandom.current().nextInt(types.length)]);
        }
        plugin.recipes().discoverAll(player);
        double maxBeforeBonus = maxHealth(player);
        plugin.passives().onJoin(player);
        restoreHealth(player, data, maxBeforeBonus);
    }

    private static double maxHealth(Player player) {
        return player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
    }

    /**
     * Passive max-health bonuses are transient, so a player who logged out above 20 HP
     * was clamped down on join. Give that health back, but only when the clamp actually
     * happened (health sits at the pre-bonus max). The saved value is used once.
     */
    private void restoreHealth(Player player, PlayerData data, double maxBeforeBonus) {
        double saved = data.lastHealth();
        data.setLastHealth(0);
        if (saved > player.getHealth() && player.getHealth() >= maxBeforeBonus - 0.01) {
            player.setHealth(Math.min(saved, maxHealth(player)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.store().get(player);
        data.setLastHealth(player.isDead() ? 0 : player.getHealth());
        plugin.cleanupPlayer(player, true);
        plugin.passives().onQuit(player);
        plugin.hud().forget(player);
        plugin.tabColors().forget(player);
        plugin.store().unload(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        plugin.cleanupPlayer(player, false);
        // Dying costs the upgrade (owner rule; DESIGN.md §8). Passives update on respawn.
        PlayerData data = plugin.store().get(player);
        if (data.upgraded() && plugin.settings().upgradeLostOnDeath) {
            data.setUpgraded(false);
            plugin.messages().send(player, "swizz.upgrade-lost");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.passives().refresh(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.passives().refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && plugin.store().get(player).controlsEnabled()
                && plugin.swizz().primary(player) != null) {
            event.setCancelled(true);
            plugin.abilities().activate(player, 1);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && plugin.store().get(player).controlsEnabled()
                && plugin.swizz().primary(player) != null) {
            event.setCancelled(true);
            plugin.abilities().activate(player, 2);
        }
    }
}
