package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AQUA (DESIGN.md §7.7). */
public final class AquaHandler extends BaseHandler implements Listener {

    private static final Color BLUE = Color.fromRGB(0x3498DB);
    private static final Color DARK_BLUE = Color.fromRGB(0x1A3C8C);

    private static final class Drowning {
        final UUID caster;
        final double damage;
        final int interval;
        int ticksLeft;
        int age;

        Drowning(UUID caster, double damage, int interval, int ticks) {
            this.caster = caster;
            this.damage = damage;
            this.interval = Math.max(1, interval);
            this.ticksLeft = ticks;
        }
    }

    private static final class Whirlpool {
        final UUID caster;
        final Location center;
        final double radius;
        final double pull;
        final int duration;
        BukkitTask task;
        int age;

        Whirlpool(UUID caster, Location center, double radius, double pull, int duration) {
            this.caster = caster;
            this.center = center;
            this.radius = radius;
            this.pull = pull;
            this.duration = duration;
        }
    }

    private final Map<UUID, Drowning> drowning = new HashMap<>();
    private final List<Whirlpool> whirlpools = new ArrayList<>();
    private final Map<UUID, Long> bottleReadyAt = new HashMap<>();
    private BukkitTask drownTask;

    public AquaHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.AQUA);
        drownTask = every(1L, this::tickDrowning);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    /** Vanilla trident melee values (player base + trident modifier). */
    private static final double TRIDENT_SPEED = 1.1;
    private static final double TRIDENT_DAMAGE = 9.0;
    private static final org.bukkit.NamespacedKey TRIDENT_SPEED_KEY = com.swizzsmp.util.Keys.key("aqua_trident_speed");
    private static final org.bukkit.NamespacedKey TRIDENT_DAMAGE_KEY = com.swizzsmp.util.Keys.key("aqua_trident_damage");

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        out.effect(PotionEffectType.WATER_BREATHING, 1);
        out.effect(PotionEffectType.DOLPHINS_GRACE, 1);
        if (player.getInventory().getItemInMainHand().getType() == Material.TRIDENT) {
            AbilityValues v = passiveValues();
            out.attribute(TRIDENT_SPEED_KEY, org.bukkit.attribute.Attribute.ATTACK_SPEED, v.num("trident-attack-speed") - TRIDENT_SPEED);
            out.attribute(TRIDENT_DAMAGE_KEY, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, v.num("trident-attack-damage") - TRIDENT_DAMAGE);
        }
    }

    /** Re-check the trident bonus as soon as the held item changes. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldChange(org.bukkit.event.player.PlayerItemHeldEvent event) {
        refreshSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        refreshSoon(event.getPlayer());
    }

    private void refreshSoon(Player player) {
        if (plugin.swizz().has(player, SwizzType.AQUA)) {
            later(1L, () -> plugin.passives().refresh(player));
        }
    }

    @Override
    public double cooldownRate(Player player, boolean upgraded) {
        return player.getWorld().hasStorm() ? passiveValues().num("rain-cooldown-rate") : 1.0;
    }

    /** Tridents break shields like axes. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTridentHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || attacker.getInventory().getItemInMainHand().getType() != Material.TRIDENT
                || !victim.isBlocking() || !hasPassive(attacker)) {
            return;
        }
        victim.setCooldown(Material.SHIELD, Time.secondsToTicks(passiveValues().num("trident-shield-disable-seconds")));
        victim.clearActiveItem();
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.9f);
    }

    private boolean upgradedPassive(Player player) {
        return hasPassive(player) && upgraded(player);
    }

    private boolean isWaterMob(Entity entity) {
        if (entity == null) {
            return false;
        }
        String name = entity.getType().name();
        for (String raw : passiveValues().upgradedList("water-mobs")) {
            if (raw.toUpperCase(Locale.ROOT).equals(name)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaterMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isWaterMob(event.getEntity()) && upgradedPassive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaterMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (isWaterMob(source) && upgradedPassive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaterMobEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) {
            return;
        }
        PotionEffectType type = event.getNewEffect().getType();
        boolean fromMob = event.getCause() == EntityPotionEffectEvent.Cause.ATTACK || event.getCause() == EntityPotionEffectEvent.Cause.UNKNOWN;
        if (fromMob && (type.equals(PotionEffectType.MINING_FATIGUE) || type.equals(PotionEffectType.POISON))
                && upgradedPassive(player) && nearWaterMob(player)) {
            event.setCancelled(true);
        }
    }

    private boolean nearWaterMob(Player player) {
        for (Entity entity : player.getNearbyEntities(50, 50, 50)) {
            if (isWaterMob(entity)) {
                return true;
            }
        }
        return false;
    }

    /** Upgraded: a water bottle is an enhanced golden apple and refills itself. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)
                || meta.getBasePotionType() != PotionType.WATER) {
            return;
        }
        Player player = event.getPlayer();
        if (!upgradedPassive(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (bottleReadyAt.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        AbilityValues v = passiveValues();
        double refillSeconds = v.upgradedNum("bottle-refill-seconds");
        bottleReadyAt.put(player.getUniqueId(), now + Time.secondsToMillis(refillSeconds));
        int slot = event.getHand() == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot();
        later(1L, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400,
                    Math.max(0, v.upgradedInt("bottle-absorption-level") - 1)));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    Time.secondsToTicks(v.upgradedNum("bottle-regeneration-seconds")),
                    Math.max(0, v.upgradedInt("bottle-regeneration-level") - 1)));
            player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
        });
        later(Time.secondsToTicks(refillSeconds), () -> refill(player, slot));
    }

    private void refill(Player player, int preferredSlot) {
        if (!player.isOnline()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        int slot = -1;
        ItemStack preferred = inv.getItem(preferredSlot);
        if (preferred != null && preferred.getType() == Material.GLASS_BOTTLE) {
            slot = preferredSlot;
        } else {
            slot = inv.first(Material.GLASS_BOTTLE);
        }
        if (slot < 0) {
            return;
        }
        ItemStack bottles = inv.getItem(slot);
        ItemStack water = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) water.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        water.setItemMeta(meta);
        if (bottles.getAmount() <= 1) {
            inv.setItem(slot, water);
        } else {
            bottles.setAmount(bottles.getAmount() - 1);
            inv.setItem(slot, bottles);
            inv.addItem(water).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8f, 1.2f);
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        return ctx.perk() == Perk.ABILITY_1 ? drown(ctx) : whirlpool(ctx);
    }

    // ── Ability 1: Drown ──────────────────────────────────────────────────────

    private AbilityResult drown(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        boolean up = ctx.upgraded();
        List<LivingEntity> targets = targets(caster, caster.getLocation(), v.up("radius", up), TargetFilter.Mode.PLAYERS);
        if (targets.isEmpty()) {
            return AbilityResult.fail("ability.no-targets");
        }
        int ticks = Time.secondsToTicks(v.num("duration"));
        int interval = (int) v.up("interval-ticks", up);
        for (LivingEntity target : targets) {
            drowning.put(target.getUniqueId(), new Drowning(caster.getUniqueId(), v.num("damage"), interval, ticks));
            target.getWorld().playSound(target.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1.2f, 0.8f);
        }
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.7f);
        dust(caster.getLocation().add(0, 1, 0), 40, 1.5, 1.5f);
        return AbilityResult.success();
    }

    private void tickDrowning() {
        if (drowning.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Drowning> entry : Map.copyOf(drowning).entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            Drowning state = entry.getValue();
            if (target == null || target.isDead() || state.ticksLeft <= 0) {
                drowning.remove(entry.getKey());
                continue;
            }
            state.ticksLeft--;
            state.age++;
            target.setRemainingAir(0);
            Location head = target.getEyeLocation();
            if (state.age % 3 == 0) {
                target.getWorld().spawnParticle(Particle.BUBBLE_POP, head, 4, 0.3, 0.3, 0.3, 0.02);
                target.getWorld().spawnParticle(Particle.DRIPPING_WATER, head, 2, 0.3, 0.2, 0.3, 0);
                target.getWorld().spawnParticle(Particle.DUST, head, 2, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(BLUE, 1.2f));
            }
            if (state.age % state.interval == 0) {
                Player caster = Bukkit.getPlayer(state.caster);
                if (caster != null) {
                    damage(target, state.damage, caster, DamageType.DROWN);
                } else {
                    target.damage(state.damage, org.bukkit.damage.DamageSource.builder(DamageType.DROWN).build());
                }
            }
        }
    }

    // ── Ability 2: Whirlpool ──────────────────────────────────────────────────

    private AbilityResult whirlpool(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        boolean up = ctx.upgraded();
        RayTraceResult hit = caster.getWorld().rayTraceBlocks(caster.getEyeLocation(), caster.getEyeLocation().getDirection(),
                v.num("aim-range"), FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitBlock() == null) {
            return AbilityResult.fail("ability.no-block-in-range");
        }
        Location center = hit.getHitPosition().toLocation(caster.getWorld()).add(0, 1.0, 0);
        Whirlpool pool = new Whirlpool(caster.getUniqueId(), center, v.up("radius", up), v.num("pull-speed"),
                Time.secondsToTicks(v.up("duration", up)));
        whirlpools.add(pool);
        caster.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.5f);
        caster.getWorld().playSound(center, Sound.ITEM_BUCKET_EMPTY, 2f, 0.6f);
        pool.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickWhirlpool(pool), 0L, 1L);
        return AbilityResult.success();
    }

    private void tickWhirlpool(Whirlpool pool) {
        Player caster = Bukkit.getPlayer(pool.caster);
        if (caster == null || pool.age++ >= pool.duration) {
            endWhirlpool(pool);
            return;
        }
        World world = pool.center.getWorld();
        whirlpoolVisuals(pool, world);
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : world.getNearbyEntities(pool.center, pool.radius, pool.radius, pool.radius)) {
            if (!(entity instanceof LivingEntity living) || !seen.add(entity.getUniqueId())
                    || entity.getLocation().distanceSquared(pool.center) > pool.radius * pool.radius
                    || !canHarm(caster, entity, TargetFilter.Mode.ENEMIES)) {
                continue;
            }
            pull(living, pool);
        }
        if (pool.age % 20 == 0) {
            world.playSound(pool.center, Sound.BLOCK_WATER_AMBIENT, 1.5f, 0.6f);
        }
    }

    private void pull(LivingEntity entity, Whirlpool pool) {
        Location pos = entity.getLocation();
        Vector toCenter = pool.center.toVector().subtract(pos.toVector());
        double dist = toCenter.length();
        if (dist < 0.6) {
            entity.setVelocity(new Vector(0, Math.min(0, entity.getVelocity().getY()), 0));
            return;
        }
        Vector dir = toCenter.multiply(1.0 / dist);
        Vector swirl = new Vector(-dir.getZ(), 0, dir.getX()).multiply(0.12);
        Vector move = dir.clone().setY(dir.getY() * 0.5).multiply(Math.min(pool.pull, dist)).add(swirl);
        boolean inWeb = pos.getBlock().getType() == Material.COBWEB
                || entity.getEyeLocation().getBlock().getType() == Material.COBWEB;
        if (inWeb) {
            // Velocity is crushed by cobwebs, so move by small teleports instead.
            Location next = pos.clone().add(move);
            entity.teleport(next, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y,
                    TeleportFlag.Relative.VELOCITY_Z);
        } else {
            entity.setVelocity(move);
        }
    }

    private void whirlpoolVisuals(Whirlpool pool, World world) {
        double spin = pool.age * 0.35;
        for (int arm = 0; arm < 4; arm++) {
            for (double r = 0.4; r <= pool.radius; r += 0.6) {
                double angle = spin + arm * Math.PI / 2 + r * 0.9;
                Location at = pool.center.clone().add(Math.cos(angle) * r, 0.1 + (pool.radius - r) * 0.08, Math.sin(angle) * r);
                Color color = (arm % 2 == 0) ? BLUE : DARK_BLUE;
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
            }
        }
        if (pool.age % 2 == 0) {
            world.spawnParticle(Particle.SPLASH, pool.center, 12, pool.radius * 0.4, 0.2, pool.radius * 0.4, 0.1);
            world.spawnParticle(Particle.BUBBLE_POP, pool.center, 6, pool.radius * 0.5, 0.3, pool.radius * 0.5, 0.05);
        }
    }

    private void endWhirlpool(Whirlpool pool) {
        if (pool.task != null) {
            pool.task.cancel();
        }
        whirlpools.remove(pool);
    }

    @Override
    public void cleanup(Player player) {
        drowning.remove(player.getUniqueId());
        for (Whirlpool pool : new ArrayList<>(whirlpools)) {
            if (pool.caster.equals(player.getUniqueId())) {
                endWhirlpool(pool);
            }
        }
    }

    @Override
    public void shutdown() {
        if (drownTask != null) {
            drownTask.cancel();
        }
        drowning.clear();
        for (Whirlpool pool : new ArrayList<>(whirlpools)) {
            endWhirlpool(pool);
        }
        bottleReadyAt.clear();
    }
}
