package com.swizzsmp.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The energy tier model (DESIGN.md §5.2). The loadout is an ordering of the three perks:
 * <pre>
 * energy >= 1  slot 1        unlocks order[0]
 * energy >= 2  slot 1 bonus  order[0] gets the cooldown bonus (wasted if it is the passive)
 * energy >= 3  slot 2        unlocks order[1]
 * energy >= 4  slot 2 bonus  order[1] gets the cooldown bonus (wasted if it is the passive)
 * energy >= 5  slot 3        unlocks order[2]
 * </pre>
 */
public final class Loadout {

    public static final List<Perk> DEFAULT = List.of(Perk.ABILITY_1, Perk.ABILITY_2, Perk.PASSIVE);

    private static final int[] UNLOCK_ENERGY = {1, 3, 5};
    private static final int[] BONUS_ENERGY = {2, 4, Integer.MAX_VALUE};

    private Loadout() {
    }

    public static boolean isUnlocked(List<Perk> order, int energy, Perk perk) {
        return energy >= requiredEnergy(order, perk);
    }

    /** Energy needed to unlock {@code perk} with this loadout. */
    public static int requiredEnergy(List<Perk> order, Perk perk) {
        int slot = order.indexOf(perk);
        return slot < 0 ? Integer.MAX_VALUE : UNLOCK_ENERGY[slot];
    }

    /** Whether {@code perk} gets the tier cooldown bonus. Never true for the passive. */
    public static boolean hasCooldownBonus(List<Perk> order, int energy, Perk perk) {
        if (!perk.isAbility()) {
            return false;
        }
        int slot = order.indexOf(perk);
        return slot >= 0 && energy >= BONUS_ENERGY[slot];
    }

    /** True if the list holds each perk exactly once. */
    public static boolean isValid(List<Perk> order) {
        return order != null && order.size() == 3 && EnumSet.copyOf(order).size() == 3;
    }

    public static List<Perk> sanitize(List<Perk> order) {
        return isValid(order) ? List.copyOf(order) : DEFAULT;
    }

    public static Set<Perk> unlocked(List<Perk> order, int energy) {
        Set<Perk> set = EnumSet.noneOf(Perk.class);
        for (Perk perk : Perk.values()) {
            if (isUnlocked(order, energy, perk)) {
                set.add(perk);
            }
        }
        return set;
    }

    /** "A1 → A2 → P". */
    public static String describe(List<Perk> order) {
        return String.join(" → ", shortNames(order));
    }

    public static List<String> shortNames(List<Perk> order) {
        List<String> names = new ArrayList<>(order.size());
        for (Perk perk : order) {
            names.add(perk.shortName());
        }
        return names;
    }
}
