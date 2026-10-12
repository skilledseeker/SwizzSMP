package com.swizzsmp.ability;

import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.entity.Player;

/**
 * Everything a handler needs for one activation. Handlers that return ARMED keep the
 * context and call {@link #commitCooldown()} when the ability actually triggers.
 */
public final class AbilityContext {

    private final AbilityService service;
    private final Player player;
    private final SwizzType type;
    private final Perk perk;
    private final String cooldownKey;
    private final boolean upgraded;
    private final AbilityValues values;
    private boolean committed;

    AbilityContext(AbilityService service, Player player, SwizzType type, Perk perk, String cooldownKey,
                   boolean upgraded, AbilityValues values) {
        this.service = service;
        this.player = player;
        this.type = type;
        this.perk = perk;
        this.cooldownKey = cooldownKey;
        this.upgraded = upgraded;
        this.values = values;
    }

    public Player player() {
        return player;
    }

    public SwizzType type() {
        return type;
    }

    public Perk perk() {
        return perk;
    }

    /** "A1", "A2", or "B1"/"B2" for the Swizz Sword's second element. */
    public String cooldownKey() {
        return cooldownKey;
    }

    public boolean upgraded() {
        return upgraded;
    }

    /** This ability's section of abilities.yml, e.g. "frost.ability1". */
    public AbilityValues values() {
        return values;
    }

    /** Starts the cooldown (only the first call does anything). */
    public void commitCooldown() {
        if (!committed) {
            committed = true;
            service.startCooldown(this);
        }
    }
}
