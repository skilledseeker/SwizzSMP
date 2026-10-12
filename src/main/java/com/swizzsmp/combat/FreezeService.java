package com.swizzsmp.combat;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Freeze (DESIGN.md §6.5): no walking, no jumping, no attacking, no item use.
 * Looking around and falling still work. Frost overlay through freeze ticks.
 */
public final class FreezeService implements Listener {

    private static final BlockData ICE = Material.ICE.createBlockData();

    private final SwizzPlugin plugin;
    /** Entity uuid -> freeze end, in server ticks. */
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private long tick;
    private BukkitTask task;

    public FreezeService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (UUID uuid : Map.copyOf(frozenUntil).keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                unfreeze(living);
            }
        }
        frozenUntil.clear();
    }

    /** Freezes (or extends the freeze of) an entity. */
    public void freeze(LivingEntity entity, int ticks) {
        long end = tick + ticks;
        frozenUntil.merge(entity.getUniqueId(), end, Math::max);
        Attrs.apply(entity, Attribute.MOVEMENT_SPEED, Keys.MOD_FREEZE_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        Attrs.apply(entity, Attribute.JUMP_STRENGTH, Keys.MOD_FREEZE_JUMP, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        entity.setFreezeTicks(entity.getMaxFreezeTicks());
        Location loc = entity.getLocation().add(0, 1, 0);
        entity.getWorld().spawnParticle(Particle.BLOCK, loc, 40, 0.4, 0.8, 0.4, 0.1, ICE);
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 30, 0.5, 0.8, 0.5, 0.02);
        entity.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        entity.getWorld().playSound(loc, Sound.BLOCK_POWDER_SNOW_PLACE, 1f, 0.5f);
    }

    public boolean isFrozen(Entity entity) {
        Long end = frozenUntil.get(entity.getUniqueId());
        return end != null && end > tick;
    }

    public void unfreeze(LivingEntity entity) {
        frozenUntil.remove(entity.getUniqueId());
        Attrs.remove(entity, Attribute.MOVEMENT_SPEED, Keys.MOD_FREEZE_SPEED);
        Attrs.remove(entity, Attribute.JUMP_STRENGTH, Keys.MOD_FREEZE_JUMP);
        entity.setFreezeTicks(0);
    }

    private void tick() {
        tick++;
        if (frozenUntil.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Long> entry : Map.copyOf(frozenUntil).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                frozenUntil.remove(entry.getKey());
                continue;
            }
            if (entry.getValue() <= tick) {
                unfreeze(living);
                living.getWorld().playSound(living.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
                continue;
            }
            living.setFreezeTicks(living.getMaxFreezeTicks());
            if (tick % 5 == 0) {
                living.getWorld().spawnParticle(Particle.SNOWFLAKE, living.getLocation().add(0, 1, 0), 4, 0.3, 0.6, 0.3, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location fixed = from.clone();
            fixed.setY(Math.min(from.getY(), to.getY()));
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            event.setTo(fixed);
        }
    }

    /** Frozen players can't melee. Their ongoing abilities (damage over time) still work. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if ((cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && isFrozen(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE && isFrozen(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL && isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter && isFrozen(shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (frozenUntil.containsKey(event.getPlayer().getUniqueId())) {
            unfreeze(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (frozenUntil.containsKey(player.getUniqueId())) {
            unfreeze(player);
        }
    }
}
