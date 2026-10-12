package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.swizz.SwizzHandler;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Shared helpers for the seven swizz handlers. */
abstract class BaseHandler implements SwizzHandler {

    protected final SwizzPlugin plugin;
    private final SwizzType type;

    protected BaseHandler(SwizzPlugin plugin, SwizzType type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public final SwizzType type() {
        return type;
    }

    protected AbilityValues passiveValues() {
        return plugin.abilityValues(type, "passive");
    }

    protected AbilityValues abilityValues(int number) {
        return plugin.abilityValues(type, "ability" + number);
    }

    protected boolean hasPassive(Player player) {
        return plugin.swizz().hasPassive(player, type);
    }

    protected boolean upgraded(Player player) {
        return plugin.swizz().isUpgraded(player);
    }

    // ── Passive reload (combo passives) ───────────────────────────────────────

    protected String reloadKey() {
        return "P:" + type.name();
    }

    protected boolean passiveReady(Player player) {
        return plugin.store().get(player).cooldown(reloadKey()) <= 0;
    }

    protected void startReload(Player player, double seconds) {
        plugin.store().get(player).setCooldown(reloadKey(), Time.secondsToMillis(seconds));
    }

    // ── Targets and damage ────────────────────────────────────────────────────

    protected List<LivingEntity> targets(Player caster, Location center, double radius, TargetFilter.Mode mode) {
        return plugin.targets().nearby(caster, center, radius, mode);
    }

    protected boolean canHarm(Player caster, org.bukkit.entity.Entity entity, TargetFilter.Mode mode) {
        return plugin.targets().canHarm(caster, entity, mode);
    }

    protected void damage(LivingEntity target, double amount, Player caster, DamageType damageType) {
        plugin.damage().deal(target, amount, caster, damageType);
    }

    // ── Visuals ───────────────────────────────────────────────────────────────

    protected void dust(Location at, int count, double spread, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0,
                new Particle.DustOptions(type.primaryColor(), size));
    }

    protected void dust2(Location at, int count, double spread, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0,
                new Particle.DustOptions(type.secondaryColor(), size));
    }

    protected static void dust(Location at, Color color, int count, double spread, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    protected BukkitTask later(long ticks, Runnable action) {
        return Bukkit.getScheduler().runTaskLater(plugin, action, ticks);
    }

    protected BukkitTask every(long period, Runnable action) {
        return Bukkit.getScheduler().runTaskTimer(plugin, action, 0L, period);
    }
}
