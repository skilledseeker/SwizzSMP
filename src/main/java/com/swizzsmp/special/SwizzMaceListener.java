package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.util.Time;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Swizz Mace (DESIGN.md §13.3): a hit disables the victim's swizz for 60 s. */
public final class SwizzMaceListener implements Listener {

    /** "X:" keys are not sped up by cooldown passives (see CooldownService). */
    public static final String COOLDOWN_KEY = "X:MACE";

    private final SwizzPlugin plugin;

    public SwizzMaceListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !CustomItems.is(attacker.getInventory().getItemInMainHand(), ItemIds.MACE)
                || !plugin.targets().canHarm(attacker, victim, TargetFilter.Mode.PLAYERS)) {
            return;
        }
        PlayerData attackerData = plugin.store().get(attacker);
        long left = attackerData.cooldown(COOLDOWN_KEY);
        if (left > 0) {
            plugin.hud().suppress(attacker, 30);
            attacker.sendActionBar(plugin.messages().get("mace.cooldown", Messages.text("time", Time.format(left))));
            return;
        }
        Settings s = plugin.settings();
        long disable = Time.secondsToMillis(s.maceDisableSeconds);
        PlayerData victimData = plugin.store().get(victim);
        victimData.setDisabledUntil(System.currentTimeMillis() + disable);
        attackerData.setCooldown(COOLDOWN_KEY, Time.secondsToMillis(s.maceCooldownSeconds));
        plugin.onStateChanged(victim);
        String time = Time.format(disable);
        plugin.messages().send(attacker, "mace.hit", Messages.text("name", victim.getName()), Messages.text("time", time));
        plugin.messages().send(victim, "mace.victim", Messages.text("time", time));
        victim.getWorld().spawnParticle(Particle.SMOKE, victim.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.6f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
    }
}
