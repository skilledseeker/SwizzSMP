package com.swizzsmp.combat;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Melee combos (DESIGN.md §6.4). A combo is consecutive melee hits by one player on one
 * player. It breaks when the victim hits back, the attacker hits someone else, or the
 * timeout passes. Listeners get each counted hit (at MONITOR) and each break.
 */
public final class ComboTracker implements Listener {

    public interface ComboListener {
        /** A melee hit landed and was counted. {@code count} includes this hit. */
        void onComboHit(Player attacker, Player victim, int count, EntityDamageByEntityEvent event);

        /** A combo of {@code count} hits ended. */
        default void onComboBreak(Player attacker, UUID victim, int count) {
        }
    }

    private static final class Combo {
        final UUID victim;
        int count;
        long lastHit;

        Combo(UUID victim) {
            this.victim = victim;
        }
    }

    private final SwizzPlugin plugin;
    private final Map<UUID, Combo> combos = new HashMap<>();
    private final List<ComboListener> listeners = new ArrayList<>();
    private BukkitTask task;

    public ComboTracker(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void addListener(ComboListener listener) {
        listeners.add(listener);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        combos.clear();
    }

    private long timeoutMillis() {
        return Math.round(plugin.settings().comboTimeoutSeconds * 1000);
    }

    /** The count this hit would reach if it lands (used before MONITOR to change damage). */
    public int peekNext(Player attacker, Player victim) {
        Combo combo = combos.get(attacker.getUniqueId());
        if (combo == null || !combo.victim.equals(victim.getUniqueId())
                || System.currentTimeMillis() - combo.lastHit > timeoutMillis()) {
            return 1;
        }
        return combo.count + 1;
    }

    /** The current count against {@code victim}, or 0. */
    public int current(Player attacker, UUID victim) {
        Combo combo = combos.get(attacker.getUniqueId());
        return combo != null && combo.victim.equals(victim) ? combo.count : 0;
    }

    public void reset(Player attacker) {
        Combo combo = combos.remove(attacker.getUniqueId());
        if (combo != null) {
            fireBreak(attacker, combo);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player source = damager instanceof Player p ? p
                : damager instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;

        // The victim hitting back breaks the attacker's combo on them.
        if (event.getEntity() instanceof Player hurt && source != null) {
            Combo theirs = combos.get(hurt.getUniqueId());
            if (theirs != null && theirs.victim.equals(source.getUniqueId())) {
                combos.remove(hurt.getUniqueId());
                fireBreak(hurt, theirs);
            }
        }

        if (!(damager instanceof Player attacker) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            reset(attacker);
            return;
        }
        long now = System.currentTimeMillis();
        Combo combo = combos.get(attacker.getUniqueId());
        if (combo != null && (!combo.victim.equals(victim.getUniqueId()) || now - combo.lastHit > timeoutMillis())) {
            combos.remove(attacker.getUniqueId());
            fireBreak(attacker, combo);
            combo = null;
        }
        if (combo == null) {
            combo = new Combo(victim.getUniqueId());
            combos.put(attacker.getUniqueId(), combo);
        }
        combo.count++;
        combo.lastHit = now;
        for (ComboListener listener : listeners) {
            listener.onComboHit(attacker, victim, combo.count, event);
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        long timeout = timeoutMillis();
        Iterator<Map.Entry<UUID, Combo>> it = combos.entrySet().iterator();
        List<Runnable> breaks = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Combo> entry = it.next();
            if (now - entry.getValue().lastHit > timeout) {
                it.remove();
                Player attacker = Bukkit.getPlayer(entry.getKey());
                Combo combo = entry.getValue();
                if (attacker != null) {
                    breaks.add(() -> fireBreak(attacker, combo));
                }
            }
        }
        breaks.forEach(Runnable::run);
    }

    private void fireBreak(Player attacker, Combo combo) {
        for (ComboListener listener : listeners) {
            listener.onComboBreak(attacker, combo.victim, combo.count);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID dead = event.getPlayer().getUniqueId();
        reset(event.getPlayer());
        List<UUID> attackers = new ArrayList<>();
        combos.forEach((attacker, combo) -> {
            if (combo.victim.equals(dead)) {
                attackers.add(attacker);
            }
        });
        for (UUID attacker : attackers) {
            Player player = Bukkit.getPlayer(attacker);
            Combo combo = combos.remove(attacker);
            if (player != null && combo != null) {
                fireBreak(player, combo);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combos.remove(event.getPlayer().getUniqueId());
    }
}
