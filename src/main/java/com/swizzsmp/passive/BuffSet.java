package com.swizzsmp.passive;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/** The permanent buffs a player should have right now from their passives. */
public final class BuffSet {

    public record AttributeBuff(Attribute attribute, double amount, AttributeModifier.Operation operation) {
    }

    private final Map<PotionEffectType, Integer> effects = new HashMap<>();
    private final Map<NamespacedKey, AttributeBuff> attributes = new HashMap<>();

    /** Adds an infinite effect. {@code level} is 1-based (Haste I = 1). Keeps the highest level. */
    public void effect(PotionEffectType type, int level) {
        if (level <= 0) {
            return;
        }
        effects.merge(type, level - 1, Math::max);
    }

    /** Adds a flat attribute bonus. Two passives with the same key keep the larger amount. */
    public void attribute(NamespacedKey key, Attribute attribute, double amount) {
        AttributeBuff existing = attributes.get(key);
        if (existing == null || existing.amount() < amount) {
            attributes.put(key, new AttributeBuff(attribute, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** Effect type to amplifier (0-based). */
    public Map<PotionEffectType, Integer> effects() {
        return effects;
    }

    public Map<NamespacedKey, AttributeBuff> attributes() {
        return attributes;
    }
}
