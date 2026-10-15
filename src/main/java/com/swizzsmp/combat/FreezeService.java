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
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freeze (DESIGN.md §6.5): no walking, no jumping, no turning, no melee, no escape items.
 * Eating, potions and shields still work. Falling still works.
 */
public final class FreezeService implements Listener {

    private static final BlockData ICE = Material.ICE.createBlockData();
    private static final Set<Material> BLOCKED_WHILE_FROZEN = EnumSet.of(Material.ENDER_PEARL, Material.CHORUS_FRUIT,
            Material.WIND_CHARGE, Material.FIREWORK_ROCKET, Material.TRIDENT, Material.BOW, Material.CROSSBOW);

    private final SwizzPlugin plugin;
    /** Entity uuid -> freeze end, in server ticks. */
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    /** Where a frozen player was looking when the freeze started: {yaw, pitch}. */
    private final Map<UUID, float[]> lockedView = new HashMap<>();
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
        lockedView.putIfAbsent(entity.getUniqueId(), new float[]{entity.getLocation().getYaw(), entity.getLocation().getPitch()});
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
        lockedView.remove(entity.getUniqueId());
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
        float[] view = lockedView.get(event.getPlayer().getUniqueId());
        boolean turned = view != null && (Math.abs(to.getYaw() - view[0]) > 0.5f || Math.abs(to.getPitch() - view[1]) > 0.5f);
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY() || turned) {
            // Moving or turning snaps the player back: the camera stays where it froze.
            Location fixed = from.clone();
            fixed.setY(Math.min(from.getY(), to.getY()));
            fixed.setYaw(view != null ? view[0] : to.getYaw());
            fixed.setPitch(view != null ? view[1] : to.getPitch());
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

    /**
     * Frozen players can still eat (golden apples), drink or throw potions and block with a
     * shield. Only items that would let them escape or fight back at range are stopped.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !isFrozen(event.getPlayer())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && BLOCKED_WHILE_FROZEN.contains(item.getType())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        // Splash and lingering potions are allowed; arrows, pearls, tridents etc. are not.
        if (event.getEntity().getShooter() instanceof Entity shooter && isFrozen(shooter)
                && !(event.getEntity() instanceof ThrownPotion)) {
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
