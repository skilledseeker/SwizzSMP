package com.swizzsmp.energy;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Energy on death and kill (DESIGN.md §5.1). */
public final class EnergyListener implements Listener {

    private final SwizzPlugin plugin;

    public EnergyListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Settings settings = plugin.settings();
        PlayerData victimData = plugin.store().get(victim);
        Location deathLoc = victim.getLocation();

        int lost = -plugin.energy().add(victimData, -1);
        if (lost > 0) {
            plugin.messages().send(victim, "energy.lost", Messages.text("amount", lost),
                    Messages.text("value", victimData.energy()), Messages.text("max", settings.energyMax));
        }

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            // Natural death: the lost energy drops on the ground.
            plugin.energy().drop(deathLoc, lost);
            return;
        }
        if (lost == 0 && settings.killRequiresVictimLoss) {
            return;
        }
        PlayerData killerData = plugin.store().get(killer);
        if (plugin.energy().isAtMax(killerData)) {
            plugin.energy().drop(deathLoc, Math.max(1, lost));
            plugin.messages().send(killer, "energy.at-max");
            return;
        }
        int gained = plugin.energy().add(killerData, 1);
        if (gained > 0) {
            plugin.messages().send(killer, "energy.gained", Messages.text("amount", gained),
                    Messages.text("value", killerData.energy()), Messages.text("max", settings.energyMax));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
        }
    }
}
