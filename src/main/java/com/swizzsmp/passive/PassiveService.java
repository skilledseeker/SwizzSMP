package com.swizzsmp.passive;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.swizz.SwizzHandler;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Attrs;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps each player's passive buffs in sync (DESIGN.md §6.6). Every second (and on any
 * state change) it builds the wanted {@link BuffSet} and applies the difference.
 * Effects are infinite, ambient and particle-free. We only remove an effect if it is
 * still infinite and at our amplifier, so potions the player drank are never touched.
 */
public final class PassiveService {

    /** Every effect type a passive may grant. Used to clean up after crashes. */
    private static final Set<PotionEffectType> MANAGED_EFFECTS = Set.of(
            PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.HASTE, PotionEffectType.WATER_BREATHING,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.SPEED, PotionEffectType.WEAVING);

    /** Every attribute key a passive may set. */
    private final Map<NamespacedKey, Attribute> managedAttributes = new HashMap<>();

    private final SwizzPlugin plugin;
    private final Map<UUID, BuffSet> applied = new HashMap<>();
    private BukkitTask task;

    public PassiveService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refresh(player);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAll(player);
        }
    }

    public void refresh(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        BuffSet wanted = new BuffSet();
        if (plugin.swizz().isUnlocked(player, Perk.PASSIVE)) {
            boolean upgraded = plugin.swizz().isUpgraded(player);
            for (SwizzType type : plugin.swizz().elements(player)) {
                SwizzHandler handler = plugin.swizz().handler(type);
                if (handler != null) {
                    handler.passiveBuffs(player, upgraded, wanted);
                }
            }
        }
        BuffSet previous = applied.getOrDefault(player.getUniqueId(), new BuffSet());

        // Effects
        for (Map.Entry<PotionEffectType, Integer> old : previous.effects().entrySet()) {
            Integer now = wanted.effects().get(old.getKey());
            if (now == null || !now.equals(old.getValue())) {
                removeOurEffect(player, old.getKey(), old.getValue());
            }
        }
        for (Map.Entry<PotionEffectType, Integer> want : wanted.effects().entrySet()) {
            PotionEffect current = player.getPotionEffect(want.getKey());
            boolean ours = current != null && current.isInfinite() && current.getAmplifier() == want.getValue();
            boolean stronger = current != null && current.getAmplifier() > want.getValue();
            if (!ours && !stronger) {
                player.addPotionEffect(new PotionEffect(want.getKey(), PotionEffect.INFINITE_DURATION,
                        want.getValue(), true, false, true));
            }
        }

        // Attributes
        boolean healthChanged = false;
        for (Map.Entry<NamespacedKey, BuffSet.AttributeBuff> old : previous.attributes().entrySet()) {
            if (!wanted.attributes().containsKey(old.getKey())) {
                Attrs.remove(player, old.getValue().attribute(), old.getKey());
                healthChanged |= old.getValue().attribute() == Attribute.MAX_HEALTH;
            }
        }
        for (Map.Entry<NamespacedKey, BuffSet.AttributeBuff> want : wanted.attributes().entrySet()) {
            BuffSet.AttributeBuff buff = want.getValue();
            managedAttributes.put(want.getKey(), buff.attribute());
            Attrs.apply(player, buff.attribute(), want.getKey(), buff.amount(), buff.operation());
        }
        if (healthChanged) {
            Attrs.clampHealth(player);
        }
        applied.put(player.getUniqueId(), wanted);
    }

    /** On join: clear leftover infinite passive effects (e.g. after a crash), then apply. */
    public void onJoin(Player player) {
        for (PotionEffectType type : MANAGED_EFFECTS) {
            PotionEffect effect = player.getPotionEffect(type);
            if (effect != null && effect.isInfinite() && effect.isAmbient() && !effect.hasParticles()) {
                player.removePotionEffect(type);
            }
        }
        applied.remove(player.getUniqueId());
        refresh(player);
    }

    /** On quit: remove everything so no infinite effect is saved into player data. */
    public void onQuit(Player player) {
        removeAll(player);
    }

    private void removeAll(Player player) {
        BuffSet previous = applied.remove(player.getUniqueId());
        if (previous == null) {
            return;
        }
        previous.effects().forEach((type, amp) -> removeOurEffect(player, type, amp));
        previous.attributes().forEach((key, buff) -> Attrs.remove(player, buff.attribute(), key));
    }

    private void removeOurEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.isInfinite() && current.getAmplifier() == amplifier) {
            player.removePotionEffect(type);
        }
    }
}
