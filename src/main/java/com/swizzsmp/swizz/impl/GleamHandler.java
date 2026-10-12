package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.ComboTracker;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.effects.Flashbang;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** GLEAM (DESIGN.md §7.4). */
public final class GleamHandler extends BaseHandler implements Listener, ComboTracker.ComboListener {

    private static final Color YELLOW = Color.fromRGB(0xFFF59D);
    private static final Color LOW_HEALTH = Color.fromRGB(0xFF2A2A);

    /** Attackers whose current combo has reached the lightning stage. */
    private final Set<UUID> charged = new HashSet<>();
    private final BukkitTask lowHealthTask;

    public GleamHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.GLEAM);
        lowHealthTask = every(10L, this::showLowHealth);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        out.effect(PotionEffectType.HASTE, passiveValues().integer("haste-level"));
    }

    private boolean comboActive(Player attacker, Player victim, int count) {
        return count > passiveValues().integer("combo-hits") && hasPassive(attacker) && passiveReady(attacker)
                && canHarm(attacker, victim, TargetFilter.Mode.PLAYERS);
    }

    /** Extra damage on hits after the combo threshold. Runs before MONITOR so damage can change. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onComboDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (comboActive(attacker, victim, plugin.combos().peekNext(attacker, victim))) {
            event.setDamage(event.getDamage() * passiveValues().num("bonus-damage-multiplier"));
        }
    }

    @Override
    public void onComboHit(Player attacker, Player victim, int count, EntityDamageByEntityEvent event) {
        if (!comboActive(attacker, victim, count)) {
            return;
        }
        if (charged.add(attacker.getUniqueId())) {
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 2f);
        }
        double lightning = passiveValues().num("lightning-damage");
        later(1L, () -> {
            if (victim.isOnline() && !victim.isDead() && attacker.isOnline()) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                damage(victim, lightning, attacker, DamageType.LIGHTNING_BOLT);
            }
        });
    }

    @Override
    public void onComboBreak(Player attacker, UUID victim, int count) {
        if (charged.remove(attacker.getUniqueId())) {
            startReload(attacker, passiveValues().num("reload"));
        }
    }

    /** Upgraded: see untrusted players nearby who are under the health threshold. */
    private void showLowHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!hasPassive(viewer) || !upgraded(viewer)) {
                continue;
            }
            AbilityValues v = passiveValues();
            double threshold = v.upgradedNum("low-health-threshold");
            double radius = v.upgradedNum("low-health-radius");
            for (Player other : viewer.getWorld().getPlayers()) {
                if (other.equals(viewer) || other.isDead() || other.getHealth() >= threshold
                        || other.getLocation().distanceSquared(viewer.getLocation()) > radius * radius
                        || !canHarm(viewer, other, TargetFilter.Mode.PLAYERS)) {
                    continue;
                }
                Location head = other.getLocation().add(0, other.getHeight() + 0.5, 0);
                viewer.spawnParticle(Particle.DUST, head, 6, 0.25, 0.1, 0.25, 0, new Particle.DustOptions(LOW_HEALTH, 1.6f));
                viewer.spawnParticle(Particle.DAMAGE_INDICATOR, head, 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        return ctx.perk() == Perk.ABILITY_1 ? radiance(ctx) : flashbang(ctx);
    }

    // ── Ability 1: Radiance ───────────────────────────────────────────────────

    private AbilityResult radiance(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        boolean up = ctx.upgraded();
        double radius = v.up("radius", up);
        int ticks = Time.secondsToTicks(v.up("duration", up));
        for (LivingEntity target : targets(caster, caster.getLocation(), radius, TargetFilter.Mode.PLAYERS)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false, true));
        }
        caster.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, Math.max(0, v.integer("haste-level") - 1), false, true, true));
        Location center = caster.getLocation().add(0, 1, 0);
        for (int ring = 1; ring <= 3; ring++) {
            double r = ring * 1.5;
            int delay = ring * 2;
            later(delay, () -> Particles.circle(center, r, 40, 0, at -> {
                at.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0.02);
                at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(YELLOW, 1.5f));
            }));
        }
        caster.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.8f);
        caster.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.4f);
        return AbilityResult.success();
    }

    // ── Ability 2: Flashbang ──────────────────────────────────────────────────

    private AbilityResult flashbang(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        List<LivingEntity> targets = targets(caster, caster.getLocation(), v.num("radius"), TargetFilter.Mode.PLAYERS);
        if (targets.isEmpty()) {
            return AbilityResult.fail("ability.no-targets");
        }
        Flashbang.Mode mode;
        try {
            mode = Flashbang.Mode.valueOf(v.str("mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Flashbang.Mode.OVERLAY;
        }
        int total = Time.secondsToTicks(v.num("duration"));
        int white = Time.secondsToTicks(v.num("white-seconds"));
        int clear = Time.secondsToTicks(v.num("clear-seconds"));
        for (LivingEntity target : targets) {
            Player victim = (Player) target;
            plugin.flashbang().flash(victim, total, white, clear, mode, v.str("overlay-texture"));
            victim.playSound(victim.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 2f);
            victim.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 2f);
        }
        Location center = caster.getEyeLocation();
        caster.getWorld().spawnParticle(Particle.END_ROD, center, 80, 0.5, 0.5, 0.5, 0.35);
        caster.getWorld().spawnParticle(Particle.FIREWORK, center, 60, 0.3, 0.3, 0.3, 0.4);
        caster.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 2f);
        if (ctx.upgraded()) {
            List<LivingEntity> enemies = targets(caster, caster.getLocation(), v.num("radius"), TargetFilter.Mode.ENEMIES);
            if (!enemies.isEmpty()) {
                LivingEntity nearest = enemies.getFirst();
                nearest.getWorld().strikeLightningEffect(nearest.getLocation());
                damage(nearest, v.upgradedNum("lightning-damage"), caster, DamageType.LIGHTNING_BOLT);
            }
        }
        return AbilityResult.success();
    }

    @Override
    public void cleanup(Player player) {
        charged.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        lowHealthTask.cancel();
        charged.clear();
    }
}
