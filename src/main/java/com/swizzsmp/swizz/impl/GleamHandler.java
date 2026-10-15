package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.ComboTracker;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.config.Messages;
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
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    /** Upgraded low-health labels: viewer → (low-health player → label only the viewer sees). */
    private final Map<UUID, Map<UUID, TextDisplay>> labels = new HashMap<>();
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

    /**
     * Upgraded: untrusted players nearby who are under the health threshold get a red
     * "❤ 4.5" label riding on their head plus heart particles. Only the Gleam player sees
     * either (the label is a text display hidden from everyone else).
     */
    private void showLowHealth() {
        Map<UUID, Set<UUID>> wanted = new HashMap<>();
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
                wanted.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>()).add(other.getUniqueId());
                Location head = other.getLocation().add(0, other.getHeight() + 0.9, 0);
                viewer.spawnParticle(Particle.HEART, head, 1, 0.2, 0.05, 0.2, 0);
                viewer.spawnParticle(Particle.DUST, head, 4, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(LOW_HEALTH, 1.4f));
                label(viewer, other);
            }
        }
        // Labels for anyone no longer low (or out of range, or a viewer who lost the passive) go.
        for (Map.Entry<UUID, Map<UUID, TextDisplay>> entry : Map.copyOf(labels).entrySet()) {
            Set<UUID> keep = wanted.getOrDefault(entry.getKey(), Set.of());
            entry.getValue().entrySet().removeIf(e -> {
                if (keep.contains(e.getKey()) && e.getValue().isValid()) {
                    return false;
                }
                e.getValue().remove();
                return true;
            });
            if (entry.getValue().isEmpty()) {
                labels.remove(entry.getKey());
            }
        }
    }

    /** The red hearts label over {@code target}, shown to {@code viewer} only. */
    private void label(Player viewer, Player target) {
        Map<UUID, TextDisplay> mine = labels.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        TextDisplay label = mine.get(target.getUniqueId());
        Component text = plugin.messages().get("gleam.low-health-label",
                Messages.text("hearts", String.format(java.util.Locale.ROOT, "%.1f", target.getHealth() / 2.0)));
        if (label == null || !label.isValid()) {
            label = target.getWorld().spawn(target.getLocation().add(0, target.getHeight(), 0), TextDisplay.class, d -> {
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(90, 40, 0, 0));
                d.setTransformation(new Transformation(new Vector3f(0, 0.55f, 0), new AxisAngle4f(), new Vector3f(1.1f), new AxisAngle4f()));
            });
            target.addPassenger(label);
            viewer.showEntity(plugin, label);
            mine.put(target.getUniqueId(), label);
        } else {
            label.text(text);
        }
    }

    private void removeLabels(UUID viewer) {
        Map<UUID, TextDisplay> mine = labels.remove(viewer);
        if (mine != null) {
            mine.values().forEach(TextDisplay::remove);
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
    public void onQuit(Player player) {
        cleanup(player);
        removeLabels(player.getUniqueId());
        // Labels riding on the player who left go too.
        for (Map<UUID, TextDisplay> mine : labels.values()) {
            TextDisplay label = mine.remove(player.getUniqueId());
            if (label != null) {
                label.remove();
            }
        }
    }

    @Override
    public void shutdown() {
        lowHealthTask.cancel();
        charged.clear();
        for (UUID viewer : java.util.List.copyOf(labels.keySet())) {
            removeLabels(viewer);
        }
    }
}
