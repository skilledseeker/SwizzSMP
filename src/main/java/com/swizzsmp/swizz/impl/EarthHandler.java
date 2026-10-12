package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import com.swizzsmp.util.Locs;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** EARTH (DESIGN.md §7.6). */
public final class EarthHandler extends BaseHandler {

    private static final NamespacedKey HEALTH_KEY = Keys.key("passive_health_earth");
    private static final NamespacedKey KNOCKBACK_KEY = Keys.key("passive_knockback_earth");
    private static final Color BROWN = Color.fromRGB(0x7B4A2A);
    private static final BlockData ROOTS = Material.MANGROVE_ROOTS.createBlockData();
    private static final BlockData ROOTED_DIRT = Material.ROOTED_DIRT.createBlockData();

    /** Players currently held or dragged by roots, so cleanup can free them. */
    private final Set<UUID> rooted = new HashSet<>();
    private final List<RootField> fields = new ArrayList<>();
    private record Grab(BukkitTask task, List<Player> held) {
    }

    private final Map<UUID, Grab> grabs = new HashMap<>();

    public EarthHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.EARTH);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        AbilityValues v = passiveValues();
        if (upgraded) {
            out.attribute(HEALTH_KEY, Attribute.MAX_HEALTH, v.upgradedNum("bonus-health-everywhere"));
            out.attribute(KNOCKBACK_KEY, Attribute.KNOCKBACK_RESISTANCE, v.upgradedNum("knockback-resistance"));
            if (v.upgradedBool("weaving")) {
                out.effect(PotionEffectType.WEAVING, 1);
            }
        } else if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            out.attribute(HEALTH_KEY, Attribute.MAX_HEALTH, v.num("overworld-bonus-health"));
            out.attribute(KNOCKBACK_KEY, Attribute.KNOCKBACK_RESISTANCE, v.num("knockback-resistance"));
        }
    }

    @Override
    public double cooldownRate(Player player, boolean upgraded) {
        return player.getWorld().getEnvironment() == World.Environment.NORMAL
                ? passiveValues().num("overworld-cooldown-rate") : 1.0;
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        if (ctx.perk() == Perk.ABILITY_1) {
            return faultLine(ctx);
        }
        return ctx.upgraded() ? rootField(ctx) : rootGrab(ctx);
    }

    // ── Ability 1: Fault Line ─────────────────────────────────────────────────

    private AbilityResult faultLine(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        boolean up = ctx.upgraded();
        int length = Math.max(1, (int) Math.round(v.num("length")));
        double halfWidth = v.num("width") / 2.0;
        double damage = v.up("damage", up);
        double kbH = v.num("knockback-horizontal");
        double kbV = v.num("knockback-vertical");
        int slowTicks = Time.secondsToTicks(v.num("slowness-seconds"));
        int slowAmp = Math.max(0, v.integer("slowness-level") - 1);
        boolean breakWebs = up && v.upgradedBool("break-webs");

        Vector dir = Locs.horizontalDirection(caster.getLocation());
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        Location cursor = caster.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        Set<UUID> hit = new HashSet<>();
        World world = caster.getWorld();
        world.playSound(caster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
        world.playSound(caster.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);

        new org.bukkit.scheduler.BukkitRunnable() {
            int step;
            final Location pos = cursor.clone();

            @Override
            public void run() {
                if (step >= length || !caster.isOnline()) {
                    cancel();
                    return;
                }
                if (!advance(pos, dir)) {
                    cancel();
                    return;
                }
                step++;
                quakeVisuals(pos, perp, halfWidth, step);
                if (breakWebs) {
                    breakWebs(pos, perp, halfWidth);
                }
                for (Entity entity : world.getNearbyEntities(pos.clone().add(0, 1, 0), halfWidth + 0.4, 1.6, halfWidth + 0.4)) {
                    if (!(entity instanceof LivingEntity living) || !hit.add(entity.getUniqueId())
                            || !canHarm(caster, entity, TargetFilter.Mode.ENEMIES)) {
                        continue;
                    }
                    damage(living, damage, caster, DamageType.MAGIC);
                    living.setVelocity(dir.clone().multiply(kbH).setY(kbV));
                    if (slowTicks > 0) {
                        living.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmp));
                    }
                    world.spawnParticle(Particle.EXPLOSION, living.getLocation(), 1);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return AbilityResult.success();
    }

    /** Moves the quake one block forward, stepping up or down over terrain. False = blocked. */
    private boolean advance(Location pos, Vector dir) {
        Location next = pos.clone().add(dir);
        Block feet = next.getBlock();
        for (int up = 0; up <= 1; up++) {
            Block candidate = feet.getRelative(0, up, 0);
            if (candidate.isPassable()) {
                // Drop down to the ground (max 3 blocks).
                Block ground = candidate;
                int fall = 0;
                while (fall < 3 && ground.getRelative(0, -1, 0).isPassable()) {
                    ground = ground.getRelative(0, -1, 0);
                    fall++;
                }
                pos.setX(next.getX());
                pos.setZ(next.getZ());
                pos.setY(ground.getY());
                return true;
            }
        }
        return false;
    }

    private void quakeVisuals(Location pos, Vector perp, double halfWidth, int step) {
        World world = pos.getWorld();
        Block groundBlock = pos.getBlock().getRelative(0, -1, 0);
        BlockData ground = groundBlock.getType().isSolid() ? groundBlock.getBlockData() : ROOTED_DIRT;
        for (double side = -halfWidth; side <= halfWidth + 0.01; side += 0.75) {
            Location at = pos.clone().add(perp.clone().multiply(side)).add(0, 0.2, 0);
            world.spawnParticle(Particle.BLOCK, at, 8, 0.2, 0.1, 0.2, 0.15, ground);
            world.spawnParticle(Particle.DUST, at.add(0, 0.3, 0), 2, 0.2, 0.3, 0.2, 0, new Particle.DustOptions(BROWN, 1.6f));
        }
        world.spawnParticle(Particle.BLOCK_CRUMBLE, pos.clone().add(0, 0.5, 0), 10, 0.5, 0.4, 0.5, 0.1, ground);
        if (step % 3 == 0) {
            world.spawnParticle(Particle.EXPLOSION, pos.clone().add(0, 0.4, 0), 1);
            world.playSound(pos, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.2f, 0.6f);
            world.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.6f);
        }
    }

    private void breakWebs(Location pos, Vector perp, double halfWidth) {
        for (double side = -halfWidth; side <= halfWidth + 0.01; side += 1.0) {
            Location column = pos.clone().add(perp.clone().multiply(side));
            for (int h = 0; h < 3; h++) {
                Block block = column.getBlock().getRelative(0, h, 0);
                if (block.getType() == Material.COBWEB && !plugin.blocks().isProtected(block)) {
                    block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0, block.getBlockData());
                    block.setType(Material.AIR);
                }
            }
        }
    }

    // ── Ability 2: Root Grab ──────────────────────────────────────────────────

    private AbilityResult rootGrab(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        // Roots can't reach through walls: only players the caster can see are grabbed.
        List<LivingEntity> targets = targets(caster, caster.getLocation(), v.num("radius"), TargetFilter.Mode.PLAYERS);
        targets.removeIf(target -> !caster.hasLineOfSight(target));
        if (targets.isEmpty()) {
            return AbilityResult.fail("ability.no-targets");
        }
        int hold = Math.max(1, v.integer("hold-ticks"));
        double grabDamage = v.num("grab-damage");
        int rootAfter = Math.max(0, v.integer("root-after-pull-ticks"));
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 1.5f, 0.5f);
        dust(caster.getLocation().add(0, 0.2, 0), 30, 1.0, 1.4f);
        List<Player> held = new ArrayList<>();
        for (LivingEntity target : targets) {
            Player victim = (Player) target;
            held.add(victim);
            root(victim);
            damage(victim, grabDamage, caster, DamageType.MAGIC);
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 0.5, 0), 40, 0.3, 0.6, 0.3, 0, ROOTS);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_HANGING_ROOTS_PLACE, 1.5f, 0.6f);
        }
        UUID casterId = caster.getUniqueId();
        BukkitTask task = later(hold, () -> {
            grabs.remove(casterId);
            int i = 0;
            List<Player> pulled = new ArrayList<>();
            for (Player victim : held) {
                unroot(victim);
                if (!caster.isOnline() || caster.isDead() || !victim.isOnline() || victim.isDead()) {
                    continue;
                }
                pullTo(victim, caster, i++, held.size());
                if (rootAfter > 0) {
                    root(victim); // held in place next to the caster for a moment
                    pulled.add(victim);
                }
            }
            if (!pulled.isEmpty()) {
                BukkitTask release = later(rootAfter, () -> {
                    grabs.remove(casterId);
                    pulled.forEach(this::unroot);
                });
                grabs.put(casterId, new Grab(release, pulled));
            }
        });
        Grab previous = grabs.put(casterId, new Grab(task, held));
        if (previous != null) {
            previous.task().cancel();
            previous.held().forEach(this::unroot);
        }
        return AbilityResult.success();
    }

    private void pullTo(Player victim, Player caster, int index, int total) {
        Location from = victim.getLocation();
        Location center = caster.getLocation();
        double angle = 2 * Math.PI * index / Math.max(1, total);
        Location candidate = center.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
        Location spot = Locs.findSafe(candidate, 3);
        if (spot == null) {
            spot = center.clone();
        }
        spot.setYaw(Locs.yawTowards(spot, center));
        spot.setPitch(0);
        if (from.getWorld().equals(center.getWorld())) {
            Particles.line(from.clone().add(0, 1, 0), spot.clone().add(0, 1, 0), 0.5,
                    at -> at.getWorld().spawnParticle(Particle.BLOCK, at, 2, 0.1, 0.1, 0.1, 0, ROOTS));
        }
        victim.teleport(spot);
        victim.getWorld().playSound(spot, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 0.7f);
        victim.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.5, 0), 30, 0.3, 0.5, 0.3, 0, ROOTS);
    }

    // ── Upgraded ability 2: Root Field ────────────────────────────────────────

    private final class RootField {
        final UUID caster;
        final Location center;
        final double radius;
        final double speed;
        final double thorns;
        final double release;
        final int maxTicks;
        final Set<UUID> victims = new HashSet<>();
        BukkitTask task;
        int age;

        RootField(Player caster, Location center, AbilityValues v) {
            this.caster = caster.getUniqueId();
            this.center = center;
            this.radius = v.upgradedNum("radius");
            this.speed = radius / Math.max(1, v.upgradedNum("drag-seconds") * 20);
            this.thorns = v.upgradedNum("thorns-damage");
            this.release = v.upgradedNum("release-distance");
            this.maxTicks = Time.secondsToTicks(v.upgradedNum("max-seconds"));
        }
    }

    private AbilityResult rootField(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        RootField field = new RootField(caster, caster.getLocation().clone(), v);
        for (LivingEntity target : targets(caster, field.center, field.radius, TargetFilter.Mode.PLAYERS)) {
            if (!caster.hasLineOfSight(target)) {
                continue; // roots can't reach through walls
            }
            field.victims.add(target.getUniqueId());
            root((Player) target);
        }
        if (field.victims.isEmpty()) {
            return AbilityResult.fail("ability.no-targets");
        }
        World world = caster.getWorld();
        world.playSound(field.center, Sound.ENTITY_WARDEN_DIG, 2f, 0.6f);
        world.playSound(field.center, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.4f);
        fields.add(field);
        field.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickField(field), 0L, 1L);
        return AbilityResult.success();
    }

    private void tickField(RootField field) {
        field.age++;
        Player caster = Bukkit.getPlayer(field.caster);
        if (caster == null || field.age > field.maxTicks + 20) {
            endField(field);
            return;
        }
        World world = field.center.getWorld();
        if (field.age % 10 == 1) {
            Particles.circle(field.center.clone().add(0, 0.3, 0), field.radius, 140, field.age * 0.02,
                    at -> world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(BROWN, 2.0f)));
        }
        // Iterate a copy: thorns damage can kill a victim, and death cleanup edits the set.
        for (UUID id : new ArrayList<>(field.victims)) {
            if (!field.victims.contains(id)) {
                continue;
            }
            Player victim = Bukkit.getPlayer(id);
            if (victim == null || victim.isDead() || !victim.getWorld().equals(world) || field.age > field.maxTicks) {
                field.victims.remove(id);
                if (victim != null) {
                    unroot(victim);
                }
                continue;
            }
            Location pos = victim.getLocation();
            Vector toCenter = field.center.toVector().subtract(pos.toVector()).setY(0);
            double dist = toCenter.length();
            if (dist <= field.release) {
                field.victims.remove(id);
                unroot(victim);
                victim.setVelocity(new Vector());
                world.spawnParticle(Particle.BLOCK, pos.clone().add(0, 0.5, 0), 30, 0.4, 0.5, 0.4, 0, ROOTS);
                continue;
            }
            Vector dir = toCenter.multiply(1.0 / dist);
            double y = victim.getVelocity().getY();
            Block ahead = pos.clone().add(dir.clone().multiply(0.8)).getBlock();
            if (ahead.getType().isSolid() && ahead.getRelative(0, 1, 0).isPassable()) {
                y = 0.42;
            }
            // Velocity only: a wall in the way stops the drag (no pulling through blocks).
            victim.setVelocity(dir.clone().multiply(Math.min(field.speed, dist)).setY(Math.max(-0.6, y)));
            if (field.age % 20 == 0) {
                damage(victim, field.thorns, caster, DamageType.THORNS);
            }
            if (field.age % 4 == 0) {
                world.spawnParticle(Particle.BLOCK, pos.clone().add(0, 0.3, 0), 6, 0.3, 0.2, 0.3, 0, ROOTS);
                world.spawnParticle(Particle.DUST, pos.clone().add(0, 0.6, 0), 3, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(BROWN, 1.2f));
            }
        }
        if (field.victims.isEmpty()) {
            endField(field);
        }
    }

    private void endField(RootField field) {
        if (field.task != null) {
            field.task.cancel();
        }
        for (UUID id : field.victims) {
            Player victim = Bukkit.getPlayer(id);
            if (victim != null) {
                unroot(victim);
            }
        }
        field.victims.clear();
        fields.remove(field);
    }

    // ── Shared root state ─────────────────────────────────────────────────────

    private void root(Player victim) {
        rooted.add(victim.getUniqueId());
        Attrs.apply(victim, Attribute.MOVEMENT_SPEED, Keys.MOD_DRAG_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        Attrs.apply(victim, Attribute.JUMP_STRENGTH, Keys.MOD_DRAG_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private void unroot(Player victim) {
        if (rooted.remove(victim.getUniqueId())) {
            Attrs.remove(victim, Attribute.MOVEMENT_SPEED, Keys.MOD_DRAG_SPEED);
            Attrs.remove(victim, Attribute.JUMP_STRENGTH, Keys.MOD_DRAG_SPEED);
        }
    }

    @Override
    public void cleanup(Player player) {
        UUID id = player.getUniqueId();
        unroot(player);
        for (RootField field : new ArrayList<>(fields)) {
            if (field.caster.equals(id)) {
                endField(field);
            } else {
                field.victims.remove(id);
            }
        }
        Grab grab = grabs.remove(id);
        if (grab != null) {
            grab.task().cancel();
            grab.held().forEach(this::unroot);
        }
    }

    @Override
    public void shutdown() {
        for (RootField field : new ArrayList<>(fields)) {
            endField(field);
        }
        grabs.values().forEach(grab -> grab.task().cancel());
        grabs.clear();
        for (UUID id : new ArrayList<>(rooted)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unroot(player);
            }
        }
        rooted.clear();
    }
}
