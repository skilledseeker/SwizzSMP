package com.swizzsmp.swizz;

import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.data.PlayerDataStore;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves which swizz a player effectively has right now (DESIGN.md §4.3) and answers
 * the "does this player have X" questions every feature asks.
 */
public final class SwizzService {

    private final PlayerDataStore store;
    private final Map<SwizzType, SwizzHandler> handlers = new EnumMap<>(SwizzType.class);
    /** Set by the Swizz Sword feature: the 2 bound elements, or null. */
    private Function<Player, List<SwizzType>> swordElements = p -> null;

    public SwizzService(PlayerDataStore store) {
        this.store = store;
    }

    public void register(SwizzHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public SwizzHandler handler(SwizzType type) {
        return handlers.get(type);
    }

    public Iterable<SwizzHandler> handlers() {
        return handlers.values();
    }

    public void setSwordElements(Function<Player, List<SwizzType>> provider) {
        this.swordElements = provider;
    }

    public boolean isDisabled(PlayerData data) {
        return data.disabledUntil() > System.currentTimeMillis();
    }

    /** The active elements: empty, one, or two (Swizz Sword). */
    public List<SwizzType> elements(Player player) {
        PlayerData data = store.get(player);
        if (isDisabled(data)) {
            return List.of();
        }
        List<SwizzType> sword = swordElements.apply(player);
        if (sword != null && !sword.isEmpty()) {
            return sword;
        }
        if (data.overrideSwizz() != null && data.overrideUntil() > System.currentTimeMillis()) {
            return List.of(data.overrideSwizz());
        }
        return data.swizz() == null ? List.of() : List.of(data.swizz());
    }

    /** The first active element, or null. */
    public SwizzType primary(Player player) {
        List<SwizzType> elements = elements(player);
        return elements.isEmpty() ? null : elements.getFirst();
    }

    public boolean has(Player player, SwizzType type) {
        return elements(player).contains(type);
    }

    public boolean isUnlocked(Player player, Perk perk) {
        PlayerData data = store.get(player);
        return Loadout.isUnlocked(data.loadout(), data.energy(), perk);
    }

    /** True if the player has {@code type} active and their passive is unlocked. */
    public boolean hasPassive(Player player, SwizzType type) {
        return has(player, type) && isUnlocked(player, Perk.PASSIVE);
    }

    public boolean isUpgraded(Player player) {
        return store.get(player).upgraded();
    }

    /** The highest cooldown rate from any active passive (DESIGN.md §6.3). */
    public double cooldownRate(Player player) {
        if (!isUnlocked(player, Perk.PASSIVE)) {
            return 1.0;
        }
        boolean upgraded = isUpgraded(player);
        double rate = 1.0;
        for (SwizzType type : elements(player)) {
            SwizzHandler handler = handlers.get(type);
            if (handler != null) {
                rate = Math.max(rate, handler.cooldownRate(player, upgraded));
            }
        }
        return rate;
    }

    public void cleanup(Player player, boolean quitting) {
        for (SwizzHandler handler : handlers.values()) {
            if (quitting) {
                handler.onQuit(player);
            } else {
                handler.cleanup(player);
            }
        }
    }

    public void shutdown() {
        for (SwizzHandler handler : handlers.values()) {
            handler.shutdown();
        }
    }
}
