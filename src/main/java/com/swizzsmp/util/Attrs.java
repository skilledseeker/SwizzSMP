package com.swizzsmp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

/**
 * Transient attribute modifiers. They are never saved to player data, so removing the
 * plugin leaves nothing behind. Applying is idempotent: an existing modifier with the
 * same key is replaced.
 */
public final class Attrs {

    private Attrs() {
    }

    public static void apply(LivingEntity entity, Attribute attribute, NamespacedKey key,
                             double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            if (existing.getAmount() == amount && existing.getOperation() == operation) {
                return;
            }
            instance.removeModifier(key);
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    public static void remove(LivingEntity entity, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(key) != null) {
            instance.removeModifier(key);
        }
    }

    public static boolean has(LivingEntity entity, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.getModifier(key) != null;
    }

    /** Clamps health to the current max after a max-health change. */
    public static void clampHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && entity.getHealth() > max.getValue()) {
            entity.setHealth(max.getValue());
        }
    }
}
