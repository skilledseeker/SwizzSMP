package com.swizzsmp.swizz;

import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.passive.BuffSet;
import org.bukkit.entity.Player;

/**
 * One swizz's behaviour. Handlers that also implement {@link org.bukkit.event.Listener}
 * are registered as listeners automatically. Event-based passives must check
 * {@code SwizzService#hasPassive} themselves.
 */
public interface SwizzHandler {

    SwizzType type();

    /** Runs ability 1 or 2 ({@code ctx.perk()}). Cooldown and unlock checks already passed. */
    AbilityResult activate(AbilityContext ctx);

    /**
     * Called before any checks when an ability command is used. Return true to consume
     * the activation, e.g. Frost throwing its next dagger while a session is active.
     */
    default boolean intercept(Player player, Perk perk) {
        return false;
    }

    /** Buffs to keep on the player while this passive is active (refreshed every second). */
    default void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
    }

    /** Cooldown tick-rate multiplier while this passive is active (1.0 = normal). */
    default double cooldownRate(Player player, boolean upgraded) {
        return 1.0;
    }

    /** Ends temporary effects this handler holds for the player (death and quit). */
    default void cleanup(Player player) {
    }

    /** The player is leaving the server. Defaults to {@link #cleanup}. */
    default void onQuit(Player player) {
        cleanup(player);
    }

    /** Ends everything (plugin disable or reload). */
    default void shutdown() {
    }
}
