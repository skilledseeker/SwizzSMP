package com.swizzsmp.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Typed snapshot of config.yml. Rebuilt on every reload. */
public final class Settings {

    public final int energyStart;
    public final int energyMin;
    public final int energyMax;
    public final boolean killRequiresVictimLoss;

    public final double cooldownMinSeconds;
    public final double slotBonusMultiplier;
    public final double upgradedMultiplier;
    public final boolean readyNotification;

    public final double loadoutSwitchCooldownSeconds;

    public final double armWindowSeconds;
    public final Set<String> disabledWorlds;
    public final long activationDebounceMs;
    public final boolean trueDamage;

    public final boolean lowHealthEnabled;
    public final double lowHealthThreshold;
    public final int lowHealthSlowTicks;
    public final int lowHealthFastTicks;
    public final int lowHealthFlashTicks;
    public final double lowHealthVolume;

    public final boolean controlsDefault;
    public final boolean hudDefault;
    public final int hudUpdateTicks;

    public final double comboTimeoutSeconds;
    public final int maxTrusted;
    public final boolean swizzCraftRequiresNoSwizz;
    public final boolean assignRandomOnFirstJoin;
    public final boolean rerollKeepsUpgrade;

    public final double ritualDurationMinutes;
    public final double ritualLeakIntervalMinutes;
    public final List<String> ritualLeakOrder;
    public final double ritualRadius;
    public final int ritualMaxAbsCoordinate;
    public final double ritualConfirmSeconds;

    public final boolean dragonEggEnabled;
    public final double dragonEggDurationMinutes;
    public final double dragonEggCooldownMinutes;
    public final boolean dragonEggExcludeUser;

    public final double maceDisableSeconds;
    public final double maceCooldownSeconds;

    public final double autosaveMinutes;

    public Settings(YamlConfiguration c) {
        energyMin = Math.max(0, c.getInt("energy.min"));
        energyMax = Math.max(energyMin, c.getInt("energy.max"));
        energyStart = clamp(c.getInt("energy.start"), energyMin, energyMax);
        killRequiresVictimLoss = c.getBoolean("energy.kill-requires-victim-loss");

        cooldownMinSeconds = c.getDouble("cooldowns.min-seconds");
        slotBonusMultiplier = c.getDouble("cooldowns.slot-bonus-multiplier");
        upgradedMultiplier = c.getDouble("cooldowns.upgraded-multiplier");
        readyNotification = c.getBoolean("cooldowns.ready-notification");

        loadoutSwitchCooldownSeconds = c.getDouble("loadout.switch-cooldown-seconds");

        armWindowSeconds = c.getDouble("abilities.arm-window-seconds");
        disabledWorlds = new HashSet<>(c.getStringList("abilities.disabled-worlds"));
        activationDebounceMs = c.getLong("abilities.activation-debounce-ms");
        trueDamage = c.getBoolean("abilities.true-damage");

        lowHealthEnabled = c.getBoolean("low-health.enabled");
        lowHealthThreshold = Math.max(0.5, c.getDouble("low-health.threshold-hearts")) * 2.0;
        lowHealthSlowTicks = Math.max(2, c.getInt("low-health.slow-beat-ticks"));
        lowHealthFastTicks = Math.max(2, c.getInt("low-health.fast-beat-ticks"));
        lowHealthFlashTicks = Math.max(1, c.getInt("low-health.flash-ticks"));
        lowHealthVolume = c.getDouble("low-health.volume");

        controlsDefault = c.getBoolean("controls.default-enabled");
        hudDefault = c.getBoolean("hud.enabled-by-default");
        hudUpdateTicks = Math.max(1, c.getInt("hud.update-ticks"));

        comboTimeoutSeconds = c.getDouble("combo.timeout-seconds");
        maxTrusted = c.getInt("trust.max-trusted");
        swizzCraftRequiresNoSwizz = c.getBoolean("swizz-items.craft-requires-no-swizz");
        assignRandomOnFirstJoin = c.getBoolean("assign-random-on-first-join");
        rerollKeepsUpgrade = c.getBoolean("reroller.keeps-upgrade");

        ritualDurationMinutes = c.getDouble("ritual.duration-minutes");
        ritualLeakIntervalMinutes = c.getDouble("ritual.leak-interval-minutes");
        ritualLeakOrder = c.getStringList("ritual.leak-order");
        ritualRadius = c.getDouble("ritual.radius");
        ritualMaxAbsCoordinate = c.getInt("ritual.max-abs-coordinate");
        ritualConfirmSeconds = c.getDouble("ritual.confirm-seconds");

        dragonEggEnabled = c.getBoolean("dragon-egg.enabled");
        dragonEggDurationMinutes = c.getDouble("dragon-egg.duration-minutes");
        dragonEggCooldownMinutes = c.getDouble("dragon-egg.cooldown-minutes");
        dragonEggExcludeUser = c.getBoolean("dragon-egg.exclude-user");

        maceDisableSeconds = c.getDouble("swizz-mace.disable-seconds");
        maceCooldownSeconds = c.getDouble("swizz-mace.cooldown-seconds");

        autosaveMinutes = Math.max(1, c.getDouble("storage.autosave-minutes"));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
