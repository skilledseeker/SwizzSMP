package com.swizzsmp.data;

import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.swizz.SwizzType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Saved per-player state (DESIGN.md §3.1). Mutate only on the main thread. */
public final class PlayerData {

    private final UUID uuid;
    private String lastKnownName;
    private SwizzType swizz;
    private int energy;
    private boolean upgraded;
    private List<Perk> loadout = Loadout.DEFAULT;
    private long loadoutChangedAt;
    private final Set<UUID> trusted = new LinkedHashSet<>();
    /** Remaining cooldown milliseconds by key ("A1", "A2", "B1", "B2", "P:FROST", ...). */
    private final Map<String, Long> cooldowns = new HashMap<>();
    private SwizzType overrideSwizz;
    private long overrideUntil;
    private long disabledUntil;
    private boolean hudEnabled;
    private boolean controlsEnabled;
    private boolean heartbeatEnabled = true;
    /** Cherry blossom trail on/off (/trail). Only PetalTrail.OWNER has the trail. */
    private boolean trailEnabled = true;
    /** Health at logout, so passive max-health bonuses don't cost hearts on relog. */
    private double lastHealth;
    private boolean dirty;

    public PlayerData(UUID uuid, int startEnergy, boolean hudEnabled, boolean controlsEnabled) {
        this.uuid = uuid;
        this.energy = startEnergy;
        this.hudEnabled = hudEnabled;
        this.controlsEnabled = controlsEnabled;
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String name) {
        if (name != null && !name.equals(lastKnownName)) {
            lastKnownName = name;
            dirty = true;
        }
    }

    public SwizzType swizz() {
        return swizz;
    }

    public boolean hasSwizz() {
        return swizz != null;
    }

    public void setSwizz(SwizzType swizz) {
        this.swizz = swizz;
        dirty = true;
    }

    public int energy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
        dirty = true;
    }

    public boolean upgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
        dirty = true;
    }

    public List<Perk> loadout() {
        return loadout;
    }

    public void setLoadout(List<Perk> loadout) {
        this.loadout = Loadout.sanitize(loadout);
        dirty = true;
    }

    public long loadoutChangedAt() {
        return loadoutChangedAt;
    }

    public void setLoadoutChangedAt(long at) {
        this.loadoutChangedAt = at;
        dirty = true;
    }

    public Set<UUID> trusted() {
        return trusted;
    }

    public boolean trusts(UUID other) {
        return trusted.contains(other);
    }

    /** @return true if now trusted, false if removed */
    public boolean toggleTrust(UUID other) {
        dirty = true;
        if (trusted.remove(other)) {
            return false;
        }
        trusted.add(other);
        return true;
    }

    public Map<String, Long> cooldowns() {
        return cooldowns;
    }

    public long cooldown(String key) {
        return cooldowns.getOrDefault(key, 0L);
    }

    public void setCooldown(String key, long remainingMillis) {
        if (remainingMillis <= 0) {
            cooldowns.remove(key);
        } else {
            cooldowns.put(key, remainingMillis);
        }
        dirty = true;
    }

    public SwizzType overrideSwizz() {
        return overrideSwizz;
    }

    public long overrideUntil() {
        return overrideUntil;
    }

    public void setOverride(SwizzType swizz, long until) {
        this.overrideSwizz = swizz;
        this.overrideUntil = until;
        dirty = true;
    }

    public long disabledUntil() {
        return disabledUntil;
    }

    public void setDisabledUntil(long until) {
        this.disabledUntil = until;
        dirty = true;
    }

    public boolean hudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
        dirty = true;
    }

    public boolean controlsEnabled() {
        return controlsEnabled;
    }

    public void setControlsEnabled(boolean controlsEnabled) {
        this.controlsEnabled = controlsEnabled;
        dirty = true;
    }

    public boolean heartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
        dirty = true;
    }

    public boolean trailEnabled() {
        return trailEnabled;
    }

    public void setTrailEnabled(boolean trailEnabled) {
        this.trailEnabled = trailEnabled;
        dirty = true;
    }

    public double lastHealth() {
        return lastHealth;
    }

    public void setLastHealth(double lastHealth) {
        this.lastHealth = lastHealth;
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
