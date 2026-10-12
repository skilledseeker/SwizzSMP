package com.swizzsmp.ability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.swizzsmp.ability.Perk.ABILITY_1;
import static com.swizzsmp.ability.Perk.ABILITY_2;
import static com.swizzsmp.ability.Perk.PASSIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutTest {

    @Test
    void defaultLoadoutMatchesSpecTiers() {
        List<Perk> order = Loadout.DEFAULT;
        // 1 energy: first ability only, no bonus
        assertTrue(Loadout.isUnlocked(order, 1, ABILITY_1));
        assertFalse(Loadout.hasCooldownBonus(order, 1, ABILITY_1));
        assertFalse(Loadout.isUnlocked(order, 1, ABILITY_2));
        // 2 energy: first ability -25%
        assertTrue(Loadout.hasCooldownBonus(order, 2, ABILITY_1));
        assertFalse(Loadout.isUnlocked(order, 2, ABILITY_2));
        // 3 energy: second ability
        assertTrue(Loadout.isUnlocked(order, 3, ABILITY_2));
        assertFalse(Loadout.hasCooldownBonus(order, 3, ABILITY_2));
        // 4 energy: second ability -25%
        assertTrue(Loadout.hasCooldownBonus(order, 4, ABILITY_2));
        assertFalse(Loadout.isUnlocked(order, 4, PASSIVE));
        // 5 energy: passive
        assertTrue(Loadout.isUnlocked(order, 5, PASSIVE));
    }

    @Test
    void swappingMovesTheCooldownBonus() {
        List<Perk> order = List.of(ABILITY_2, ABILITY_1, PASSIVE);
        assertTrue(Loadout.isUnlocked(order, 2, ABILITY_2));
        assertTrue(Loadout.hasCooldownBonus(order, 2, ABILITY_2));
        assertFalse(Loadout.isUnlocked(order, 2, ABILITY_1));
    }

    @Test
    void passiveFirstWastesTheBonusPoint() {
        List<Perk> order = List.of(PASSIVE, ABILITY_1, ABILITY_2);
        assertTrue(Loadout.isUnlocked(order, 2, PASSIVE));
        assertFalse(Loadout.hasCooldownBonus(order, 2, PASSIVE));
        assertFalse(Loadout.isUnlocked(order, 2, ABILITY_1));
        assertEquals(3, Loadout.requiredEnergy(order, ABILITY_1));
        assertEquals(5, Loadout.requiredEnergy(order, ABILITY_2));
    }

    @Test
    void thirdSlotNeverGetsBonus() {
        List<Perk> order = List.of(PASSIVE, ABILITY_2, ABILITY_1);
        assertTrue(Loadout.isUnlocked(order, 5, ABILITY_1));
        assertFalse(Loadout.hasCooldownBonus(order, 5, ABILITY_1));
        assertTrue(Loadout.hasCooldownBonus(order, 5, ABILITY_2));
    }

    @Test
    void validation() {
        assertTrue(Loadout.isValid(List.of(PASSIVE, ABILITY_2, ABILITY_1)));
        assertFalse(Loadout.isValid(List.of(PASSIVE, PASSIVE, ABILITY_1)));
        assertFalse(Loadout.isValid(List.of(PASSIVE, ABILITY_1)));
        assertEquals(Loadout.DEFAULT, Loadout.sanitize(List.of(ABILITY_1, ABILITY_1, ABILITY_1)));
    }
}
