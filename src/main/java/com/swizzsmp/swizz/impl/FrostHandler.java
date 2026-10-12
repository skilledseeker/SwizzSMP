package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Keys;
import com.swizzsmp.util.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** FROST (DESIGN.md §7.5). */
public final class FrostHandler extends BaseHandler implements Listener {

    private static final NamespacedKey HEALTH_KEY = Keys.key("passive_health_frost");
    private static final BlockData ICE = Material.ICE.createBlockData();
    private static final ItemStack DAGGER_ITEM = new ItemStack(Material.ICE);

    private static final class Session {
        final AbilityContext ctx;
        int remaining;
        long nextThrowAt;
        BukkitTask timeout;

        Session(AbilityContext ctx, int daggers) {
            this.ctx = ctx;
            this.remaining = daggers;
        }
    }

    private record Dagger(UUID owner, double damage, boolean upgraded, double freezeSeconds, double shieldSeconds,
                          BukkitTask expiry) {
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Dagger> daggers = new HashMap<>();

    public FrostHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.FROST);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    private boolean isCold(Player player) {
        return player.getLocation().getBlock().getTemperature() < passiveValues().num("cold-temperature");
    }

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        if (!isCold(player)) {
            return;
        }
        AbilityValues v = passiveValues();
        out.effect(PotionEffectType.SPEED, v.integer("cold-speed-level"));
        if (upgraded) {
            out.attribute(HEALTH_KEY, Attribute.MAX_HEALTH, v.upgradedNum("cold-bonus-health"));
        }
    }

    @Override
    public double cooldownRate(Player player, boolean upgraded) {
        return isCold(player) ? passiveValues().num("cold-cooldown-rate") : 1.0;
    }

    /** Can't sink into powder snow unless sneaking. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getBlock().getType() != Material.POWDER_SNOW) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() || !hasPassive(player) || !passiveValues().bool("powder-snow-walk")) {
            return;
        }
        Block top = to.getBlock();
        while (top.getRelative(0, 1, 0).getType() == Material.POWDER_SNOW) {
            top = top.getRelative(0, 1, 0);
        }
        if (!top.getRelative(0, 1, 0).isPassable()) {
            return;
        }
        Location lifted = to.clone();
        lifted.setY(top.getY() + 1.0);
        event.setTo(lifted);
        player.setFallDistance(0);
        player.setFreezeTicks(0);
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        if (ctx.perk() == Perk.ABILITY_1) {
            return startIcicles(ctx);
        }
        return ctx.upgraded() ? freezeAround(ctx) : AbilityResult.armed();
    }

    // ── Ability 1: Icicles ────────────────────────────────────────────────────

    private AbilityResult startIcicles(AbilityContext ctx) {
        Player player = ctx.player();
        AbilityValues v = ctx.values();
        endSession(player.getUniqueId(), false);
        Session session = new Session(ctx, Math.max(1, v.integer("daggers")));
        session.timeout = later(Time.secondsToTicks(v.num("session-timeout")), () -> endSession(player.getUniqueId(), true));
        sessions.put(player.getUniqueId(), session);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_PLACE, 1f, 1.6f);
        throwDagger(player, session);
        return AbilityResult.success();
    }

    @Override
    public boolean intercept(Player player, Perk perk) {
        if (perk != Perk.ABILITY_1) {
            return false;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        throwDagger(player, session);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && !plugin.freeze().isFrozen(event.getPlayer()) && throwDagger(event.getPlayer(), session)) {
            event.setCancelled(true);
        }
    }

    private boolean throwDagger(Player player, Session session) {
        long now = System.currentTimeMillis();
        if (now < session.nextThrowAt) {
            plugin.hud().suppress(player, 20);
            player.sendActionBar(Component.text("❄ Next icicle in " + Time.format(session.nextThrowAt - now), NamedTextColor.AQUA));
            return false;
        }
        AbilityValues v = session.ctx.values();
        boolean up = session.ctx.upgraded();
        double speed = v.num("speed");
        Vector velocity = player.getEyeLocation().getDirection().multiply(speed);
        Snowball snowball = player.launchProjectile(Snowball.class, velocity);
        snowball.setItem(DAGGER_ITEM);
        snowball.setGravity(false);
        snowball.setPersistent(false);
        int lifeTicks = (int) Math.ceil(v.num("max-range") / Math.max(0.1, speed));
        UUID id = snowball.getUniqueId();
        BukkitTask expiry = later(lifeTicks, () -> {
            daggers.remove(id);
            if (snowball.isValid()) {
                shatter(snowball.getLocation());
                snowball.remove();
            }
        });
        daggers.put(id, new Dagger(player.getUniqueId(), v.num("damage"), up,
                v.upgradedNum("freeze-seconds"), v.upgradedNum("shield-disable-seconds"), expiry));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 1.8f);

        session.remaining--;
        session.nextThrowAt = now + Time.secondsToMillis(v.num("delay-between"));
        if (session.remaining <= 0) {
            endSession(player.getUniqueId(), false);
        } else {
            plugin.hud().suppress(player, 30);
            player.sendActionBar(Component.text("❄ Icicles left: " + session.remaining, NamedTextColor.AQUA));
        }
        return true;
    }

    @EventHandler
    public void onDaggerHit(ProjectileHitEvent event) {
        Dagger dagger = daggers.remove(event.getEntity().getUniqueId());
        if (dagger == null) {
            return;
        }
        event.setCancelled(true);
        dagger.expiry().cancel();
        Entity projectile = event.getEntity();
        Location at = projectile.getLocation();
        projectile.remove();
        shatter(at);
        Player owner = org.bukkit.Bukkit.getPlayer(dagger.owner());
        Entity hit = event.getHitEntity();
        if (owner == null || !(hit instanceof LivingEntity target) || !canHarm(owner, hit, TargetFilter.Mode.ENEMIES)) {
            return;
        }
        if (target instanceof Player victim && victim.isBlocking() && isFacing(victim, projectile.getVelocity())) {
            if (!dagger.upgraded()) {
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                return;
            }
            victim.setCooldown(Material.SHIELD, Time.secondsToTicks(dagger.shieldSeconds()));
            victim.clearActiveItem();
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
        }
        damage(target, dagger.damage(), owner, DamageType.THROWN);
        if (dagger.upgraded() && target instanceof Player victim) {
            plugin.freeze().freeze(victim, Time.secondsToTicks(dagger.freezeSeconds()));
        }
    }

    private static boolean isFacing(Player victim, Vector incoming) {
        Vector look = victim.getLocation().getDirection().setY(0);
        Vector from = incoming.clone().setY(0);
        return look.lengthSquared() > 0 && from.lengthSquared() > 0 && look.normalize().dot(from.normalize()) < -0.2;
    }

    private void shatter(Location at) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, 15, 0.15, 0.15, 0.15, 0, ICE);
        at.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 8, 0.2, 0.2, 0.2, 0.02);
        at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        endSession(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        endSession(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        endSession(event.getPlayer().getUniqueId(), true);
    }

    private void endSession(UUID player, boolean announce) {
        Session session = sessions.remove(player);
        if (session == null) {
            return;
        }
        if (session.timeout != null) {
            session.timeout.cancel();
        }
        Player online = org.bukkit.Bukkit.getPlayer(player);
        if (announce && online != null && session.remaining > 0) {
            plugin.hud().suppress(online, 30);
            online.sendActionBar(Component.text("❄ Your icicles shattered", NamedTextColor.GRAY));
        }
    }

    // ── Ability 2: Deep Freeze ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !plugin.abilities().isArmed(attacker, SwizzType.FROST, Perk.ABILITY_2)
                || !canHarm(attacker, victim, TargetFilter.Mode.PLAYERS)) {
            return;
        }
        plugin.abilities().consumeArm(attacker, SwizzType.FROST, Perk.ABILITY_2).ifPresent(ctx -> {
            plugin.freeze().freeze(victim, Time.secondsToTicks(ctx.values().num("freeze-seconds")));
            ctx.commitCooldown();
        });
    }

    private AbilityResult freezeAround(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        var targets = targets(caster, caster.getLocation(), v.upgradedNum("radius"), TargetFilter.Mode.PLAYERS);
        if (targets.isEmpty()) {
            return AbilityResult.fail("ability.no-targets");
        }
        int ticks = Time.secondsToTicks(v.num("freeze-seconds"));
        for (LivingEntity target : targets) {
            plugin.freeze().freeze(target, ticks);
        }
        Location center = caster.getLocation().add(0, 0.2, 0);
        com.swizzsmp.effects.Particles.circle(center, v.upgradedNum("radius"), 60, 0,
                at -> at.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 2, 0.1, 0.3, 0.1, 0.01));
        caster.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
        return AbilityResult.success();
    }

    @Override
    public void cleanup(Player player) {
        endSession(player.getUniqueId(), false);
    }

    @Override
    public void shutdown() {
        for (UUID id : Map.copyOf(sessions).keySet()) {
            endSession(id, false);
        }
        for (Map.Entry<UUID, Dagger> entry : Map.copyOf(daggers).entrySet()) {
            entry.getValue().expiry().cancel();
            Entity entity = org.bukkit.Bukkit.getEntity(entry.getKey());
            if (entity != null) {
                entity.remove();
            }
        }
        daggers.clear();
    }
}
