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
        // During the double energy event every kill earns extra (DESIGN.md §5.3), but only
        // when the victim actually lost energy: a victim at the minimum never feeds the event.
        int extra = lost > 0 ? plugin.doubleEnergy().extraPerKill() : 0;
        if (plugin.energy().isAtMax(killerData)) {
            // Only energy the victim really lost (plus the event bonus) ever hits the ground,
            // so killing someone at the minimum never creates energy.
            if (lost > 0) {
                plugin.energy().drop(deathLoc, lost + extra);
            }
            plugin.messages().send(killer, "energy.at-max");
            return;
        }
        int wanted = 1 + extra;
        int gained = plugin.energy().add(killerData, wanted);
        if (gained < wanted) {
            plugin.energy().drop(deathLoc, wanted - gained); // the rest would go over the max
        }
        if (gained > 0) {
            plugin.messages().send(killer, extra > 0 ? "energy.gained-event" : "energy.gained", Messages.text("amount", gained),
                    Messages.text("value", killerData.energy()), Messages.text("max", settings.energyMax));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
        }
    }
}
