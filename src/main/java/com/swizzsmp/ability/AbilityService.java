package com.swizzsmp.ability;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzHandler;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Runs ability activations (DESIGN.md §6.1) and holds "armed" next-hit abilities. */
public final class AbilityService {

    private record ArmKey(UUID player, SwizzType type, Perk perk) {
    }

    private record Arm(AbilityContext context, BukkitTask expiry) {
    }

    private final SwizzPlugin plugin;
    private final Map<UUID, Long> lastActivation = new HashMap<>();
    private final Map<ArmKey, Arm> arms = new HashMap<>();

    public AbilityService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param index 1-4. 1 and 2 use the first element; 3 and 4 the Swizz Sword's second one.
     */
    public void activate(Player player, int index) {
        if (player.isDead()) {
            return;
        }
        long now = System.currentTimeMillis();
        Settings settings = plugin.settings();
        Long last = lastActivation.get(player.getUniqueId());
        if (last != null && now - last < settings.activationDebounceMs) {
            return;
        }
        lastActivation.put(player.getUniqueId(), now);

        int element = index <= 2 ? 0 : 1;
        Perk perk = (index % 2 == 1) ? Perk.ABILITY_1 : Perk.ABILITY_2;
        String key = (element == 0 ? "A" : "B") + (perk == Perk.ABILITY_1 ? "1" : "2");
        String abilityName = "Ability " + index;

        PlayerData data = plugin.store().get(player);
        List<SwizzType> elements = plugin.swizz().elements(player);
        if (elements.isEmpty()) {
            if (plugin.swizz().isDisabled(data)) {
                plugin.messages().send(player, "swizz.disabled",
                        Messages.text("time", Time.format(data.disabledUntil() - now)));
            } else {
                plugin.messages().send(player, "ability.no-swizz");
            }
            return;
        }
        if (element >= elements.size()) {
            plugin.messages().send(player, "ability.no-second-element");
            return;
        }
        SwizzType type = elements.get(element);
        SwizzHandler handler = plugin.swizz().handler(type);
        if (handler == null) {
            plugin.messages().send(player, "ability.not-implemented");
            return;
        }
        if (plugin.freeze().isFrozen(player)) {
            plugin.messages().send(player, "ability.frozen");
            return;
        }
        if (handler.intercept(player, perk)) {
            return;
        }
        if (settings.disabledWorlds.contains(player.getWorld().getName())) {
            plugin.messages().send(player, "ability.disabled-world");
            return;
        }
        if (!Loadout.isUnlocked(data.loadout(), data.energy(), perk)) {
            plugin.messages().send(player, "ability.locked",
                    Messages.text("perk", perk.displayName()),
                    Messages.text("needed", Loadout.requiredEnergy(data.loadout(), perk)));
            return;
        }
        long remaining = data.cooldown(key);
        if (remaining > 0) {
            plugin.messages().send(player, "ability.cooldown",
                    Messages.text("ability", abilityName), Messages.text("time", Time.format(remaining)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
            return;
        }
        ArmKey armKey = new ArmKey(player.getUniqueId(), type, perk);
        if (arms.containsKey(armKey)) {
            plugin.messages().send(player, "ability.already-armed", Messages.text("ability", abilityName));
            return;
        }

        AbilityValues values = plugin.abilityValues(type, perk == Perk.ABILITY_1 ? "ability1" : "ability2");
        AbilityContext ctx = new AbilityContext(this, player, type, perk, key, data.upgraded(), values);
        AbilityResult result;
        try {
            result = handler.activate(ctx);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error in " + type + " " + perk + " for " + player.getName(), e);
            ctx.commitCooldown(); // never allow a broken ability to be spammed
            return;
        }
        switch (result.kind()) {
            case SUCCESS -> ctx.commitCooldown();
            case ARMED -> arm(ctx, abilityName);
            case FAIL -> {
                if (result.messageKey() != null) {
                    plugin.messages().send(player, result.messageKey());
                }
            }
        }
    }

    void startCooldown(AbilityContext ctx) {
        PlayerData data = plugin.store().get(ctx.player());
        Settings settings = plugin.settings();
        double seconds = ctx.values().num("cooldown");
        if (Loadout.hasCooldownBonus(data.loadout(), data.energy(), ctx.perk())) {
            seconds *= settings.slotBonusMultiplier;
        }
        if (ctx.upgraded()) {
            seconds *= settings.upgradedMultiplier;
        }
        seconds = Math.max(settings.cooldownMinSeconds, seconds);
        data.setCooldown(ctx.cooldownKey(), Time.secondsToMillis(seconds));
    }

    // ── Loadout ───────────────────────────────────────────────────────────────

    /** Changes the perk order if the switch cooldown allows it (DESIGN.md §5.2). */
    public boolean changeLoadout(Player player, List<Perk> order) {
        PlayerData data = plugin.store().get(player);
        if (!Loadout.isValid(order)) {
            plugin.messages().send(player, "loadout.invalid");
            return false;
        }
        if (order.equals(data.loadout())) {
            return true;
        }
        long wait = data.loadoutChangedAt() + Time.secondsToMillis(plugin.settings().loadoutSwitchCooldownSeconds)
                - System.currentTimeMillis();
        if (wait > 0) {
            plugin.messages().send(player, "loadout.cooldown", Messages.text("time", Time.format(wait)));
            return false;
        }
        data.setLoadout(order);
        data.setLoadoutChangedAt(System.currentTimeMillis());
        plugin.onStateChanged(player);
        plugin.messages().send(player, "loadout.changed", Messages.text("value", Loadout.describe(order)));
        return true;
    }

    // ── Armed (next-hit) abilities ────────────────────────────────────────────

    private void arm(AbilityContext ctx, String abilityName) {
        ArmKey key = new ArmKey(ctx.player().getUniqueId(), ctx.type(), ctx.perk());
        double window = plugin.settings().armWindowSeconds;
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arms.remove(key) != null && ctx.player().isOnline()) {
                plugin.messages().send(ctx.player(), "ability.arm-expired", Messages.text("ability", abilityName));
            }
        }, Time.secondsToTicks(window));
        arms.put(key, new Arm(ctx, expiry));
        plugin.messages().send(ctx.player(), "ability.armed",
                Messages.text("ability", abilityName), Messages.text("time", Time.format(Time.secondsToMillis(window))));
    }

    /** If the player has this ability armed, disarms it and returns its context. */
    public Optional<AbilityContext> consumeArm(Player player, SwizzType type, Perk perk) {
        Arm arm = arms.remove(new ArmKey(player.getUniqueId(), type, perk));
        if (arm == null) {
            return Optional.empty();
        }
        arm.expiry().cancel();
        // Lost the swizz since arming (rerolled, mace, dragon egg): the arm fizzles.
        if (!plugin.swizz().has(player, type)) {
            return Optional.empty();
        }
        return Optional.of(arm.context());
    }

    public boolean isArmed(Player player, SwizzType type, Perk perk) {
        return arms.containsKey(new ArmKey(player.getUniqueId(), type, perk));
    }

    public void cleanup(Player player) {
        lastActivation.remove(player.getUniqueId());
        arms.entrySet().removeIf(entry -> {
            if (entry.getKey().player().equals(player.getUniqueId())) {
                entry.getValue().expiry().cancel();
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        arms.values().forEach(arm -> arm.expiry().cancel());
        arms.clear();
    }
}
