package com.swizzsmp.combat;

import com.swizzsmp.SwizzPlugin;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Deals ability damage, credited to the caster (kills count). Shields never block it:
 * abilities that respect shields (Frost icicles) check blocking themselves first. With
 * {@code abilities.true-damage}, armor and Protection don't reduce it either, so
 * "3 hearts" means 3 hearts. Resistance and absorption still apply.
 */
public final class AbilityDamage implements Listener {

    private final SwizzPlugin plugin;
    private LivingEntity pending;
    private double pendingAmount;

    public AbilityDamage(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void deal(LivingEntity target, double amount, Player caster, DamageType type) {
        if (amount <= 0 || target.isDead()) {
            return;
        }
        target.setNoDamageTicks(0);
        DamageSource source = DamageSource.builder(type).withCausingEntity(caster).withDirectEntity(caster).build();
        LivingEntity previous = pending;
        double previousAmount = pendingAmount;
        pending = target;
        pendingAmount = amount;
        try {
            target.damage(amount, source);
        } finally {
            pending = previous;
            pendingAmount = previousAmount;
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (pending == null || event.getEntity() != pending) {
            return;
        }
        // Some damage types (e.g. sonic boom) are scaled by difficulty even when a player
        // causes them. Ability damage is always exactly the configured amount.
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, pendingAmount);
        if (event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        }
        if (!plugin.settings().trueDamage) {
            return;
        }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
        }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
        }
    }
}
