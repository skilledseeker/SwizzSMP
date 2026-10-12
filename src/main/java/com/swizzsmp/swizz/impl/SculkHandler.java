package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.effects.BlockRestorer;
import com.swizzsmp.effects.Domes;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Keys;
import com.swizzsmp.util.Locs;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** SCULK (DESIGN.md §7.2). */
public final class SculkHandler extends BaseHandler implements Listener {

    private static final BlockData SCULK = Material.SCULK.createBlockData();
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final NamespacedKey WARDEN_OWNER = Keys.key("warden_owner");

    private final class Dome {
        final UUID caster;
        final Location center;
        final double radius;
        final int duration;
        final boolean upgraded;
        final AbilityValues values;
        final BlockRestorer.Session session;
        final List<Block> pending = new ArrayList<>();
        final List<UUID> wardens = new ArrayList<>();
        BukkitTask task;
        int age;

        Dome(Player caster, AbilityValues values, boolean upgraded) {
            this.caster = caster.getUniqueId();
            this.center = caster.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            this.values = values;
            this.upgraded = upgraded;
            this.radius = values.up("radius", upgraded);
            this.duration = Time.secondsToTicks(values.num("duration"));
            this.session = plugin.blocks().open("sculk-dome");
        }

        boolean inShellZone(Block block) {
            return Domes.inShellZone(center, block, radius - 1.5, radius + 1.5);
        }

        boolean inside(Location loc) {
            return loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= radius * radius;
        }
    }

    private final List<Dome> domes = new ArrayList<>();
    private final Map<UUID, Long> lastSculkEat = new HashMap<>();
    private final Map<UUID, BukkitTask> charging = new HashMap<>();

    public SculkHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.SCULK);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        out.attribute(Keys.MOD_PASSIVE_SNEAK, Attribute.SNEAKING_SPEED, passiveValues().num("sneak-speed-bonus"));
    }

    /** Right-click a sculk block to eat it and repair armor. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEatSculk(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getItem() == null || event.getItem().getType() != Material.SCULK || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.freeze().isFrozen(player) || !hasPassive(player) || !anyArmorDamaged(player)) {
            return;
        }
        event.setCancelled(true);
        AbilityValues v = passiveValues();
        long now = System.currentTimeMillis();
        if (now - lastSculkEat.getOrDefault(player.getUniqueId(), 0L) < v.integer("sculk-eat-delay-ticks") * 50L) {
            return;
        }
        lastSculkEat.put(player.getUniqueId(), now);
        ItemStack hand = event.getItem();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItem(event.getHand(), hand.getAmount() <= 0 ? null : hand);
        int repair = v.integer("sculk-repair");
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = player.getInventory().getItem(slot);
            if (piece != null && piece.getItemMeta() instanceof Damageable meta && meta.getDamage() > 0) {
                meta.setDamage(Math.max(0, meta.getDamage() - repair));
                piece.setItemMeta(meta);
                player.getInventory().setItem(slot, piece);
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_CHARGE, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 5, 0.3, 0.4, 0.3, 0.02);
    }

    private static boolean anyArmorDamaged(Player player) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = player.getInventory().getItem(slot);
            if (piece != null && piece.getItemMeta() instanceof Damageable meta && meta.getDamage() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean upgradedPassive(Player player) {
        return hasPassive(player) && upgraded(player);
    }

    /** Upgraded: wardens ignore you and can't hurt you. Our own dome wardens follow their own rules. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Warden warden) || !(event.getTarget() instanceof Player target)) {
            return;
        }
        UUID owner = wardenOwner(warden);
        if (owner != null) {
            Player caster = Bukkit.getPlayer(owner);
            if (caster == null || !canHarm(caster, target, TargetFilter.Mode.PLAYERS)) {
                event.setCancelled(true);
            }
            return;
        }
        if (upgradedPassive(target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Warden warden)) {
            return;
        }
        UUID owner = wardenOwner(warden);
        if (owner != null) {
            Player caster = Bukkit.getPlayer(owner);
            if (caster == null || !canHarm(caster, event.getEntity(), TargetFilter.Mode.PLAYERS)) {
                event.setCancelled(true);
                return;
            }
            double cap = domeFor(owner).map(d -> d.values.upgradedNum("warden-max-damage")).orElse(6.0);
            event.setDamage(Math.min(event.getDamage(), cap));
            return;
        }
        if (event.getEntity() instanceof Player player && upgradedPassive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDarkness(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && event.getNewEffect() != null
                && event.getNewEffect().getType().equals(PotionEffectType.DARKNESS) && upgradedPassive(player)) {
            event.setCancelled(true);
        }
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        return ctx.perk() == Perk.ABILITY_1 ? sonicBeam(ctx) : sculkDome(ctx);
    }

    // ── Ability 1: Sonic Beam ─────────────────────────────────────────────────

    private AbilityResult sonicBeam(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        boolean up = ctx.upgraded();
        int charge = Math.max(0, v.integer("charge-ticks"));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 1f);
        BukkitTask task = new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                if (!caster.isOnline() || caster.isDead()) {
                    cancel();
                    charging.remove(caster.getUniqueId());
                    return;
                }
                if (age++ < charge) {
                    Location chest = caster.getLocation().add(0, 1.2, 0);
                    double angle = age * 0.6;
                    chest.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7);
                    caster.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, chest, 2, 0.05, 0.05, 0.05, 0.01);
                    caster.getWorld().spawnParticle(Particle.SCULK_SOUL, caster.getLocation().add(0, 1.2, 0), 1, 0.3, 0.3, 0.3, 0.01);
                    return;
                }
                cancel();
                charging.remove(caster.getUniqueId());
                fireBeam(caster, v.num("range"), v.up("hit-radius", up), v.up("damage", up), v.bool("pass-through-blocks"));
            }
        }.runTaskTimer(plugin, 0L, 1L);
        BukkitTask previous = charging.put(caster.getUniqueId(), task);
        if (previous != null) {
            previous.cancel();
        }
        return AbilityResult.success();
    }

    private void fireBeam(Player caster, double range, double hitRadius, double damage, boolean throughBlocks) {
        World world = caster.getWorld();
        Location origin = caster.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
        double length = range;
        if (!throughBlocks) {
            // Stop at the first block with a collision shape (glass, slabs, fences, leaves...).
            var blockHit = world.rayTraceBlocks(origin, dir, range, org.bukkit.FluidCollisionMode.NEVER, true);
            if (blockHit != null) {
                length = blockHit.getHitPosition().distance(origin.toVector());
                world.spawnParticle(Particle.SCULK_CHARGE_POP, blockHit.getHitPosition().toLocation(world), 10, 0.2, 0.2, 0.2, 0.05);
            }
        }
        Set<UUID> hit = new HashSet<>();
        for (double d = 0.5; d <= length; d += 0.5) {
            Location point = origin.clone().add(dir.clone().multiply(d));
            if ((int) (d * 2) % 2 == 0) {
                world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            }
            if (hitRadius > 0.6) {
                world.spawnParticle(Particle.SCULK_SOUL, point, 2, hitRadius * 0.4, hitRadius * 0.4, hitRadius * 0.4, 0.01);
            }
            for (Entity entity : world.getNearbyEntities(point, hitRadius + 1, hitRadius + 1.5, hitRadius + 1)) {
                if (!(entity instanceof LivingEntity living) || hit.contains(entity.getUniqueId())
                        || !entity.getBoundingBox().expand(hitRadius).contains(point.toVector())
                        || !canHarm(caster, entity, TargetFilter.Mode.ENEMIES)) {
                    continue;
                }
                hit.add(entity.getUniqueId());
                damage(living, damage, caster, DamageType.SONIC_BOOM);
                living.setVelocity(living.getVelocity().add(dir.clone().setY(0).multiply(0.6).setY(0.2)));
            }
        }
    }

    // ── Ability 2: Sculk Dome ─────────────────────────────────────────────────

    private AbilityResult sculkDome(AbilityContext ctx) {
        Player caster = ctx.player();
        Dome dome = new Dome(caster, ctx.values(), ctx.upgraded());
        World world = caster.getWorld();

        List<BoundingBox> occupied = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(dome.center) <= (dome.radius + 3) * (dome.radius + 3)) {
                occupied.add(player.getBoundingBox());
            }
        }
        for (Block block : Domes.sphereShell(dome.center, dome.radius)) {
            if (!Domes.canShellReplace(block) || plugin.blocks().isProtected(block)) {
                continue;
            }
            if (isOccupied(block, occupied)) {
                dome.pending.add(block);
            } else {
                dome.session.set(block, SCULK);
            }
        }
        if (dome.upgraded && dome.values.upgradedBool("convert-ground")) {
            convertGround(dome);
        }
        dome.session.save();
        domes.add(dome);

        world.playSound(dome.center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 3f, 0.8f);
        world.playSound(dome.center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 3f, 0.6f);
        Particles.sphere(dome.center, Math.min(dome.radius, 6), 120, false,
                at -> world.spawnParticle(Particle.SCULK_SOUL, at, 1, 0, 0, 0, 0.02));

        if (dome.upgraded) {
            spawnWardens(caster, dome);
        }
        dome.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickDome(dome), 1L, 1L);
        return AbilityResult.success();
    }

    private static boolean isOccupied(Block block, List<BoundingBox> occupied) {
        BoundingBox box = BoundingBox.of(block);
        for (BoundingBox player : occupied) {
            if (box.overlaps(player)) {
                return true;
            }
        }
        return false;
    }

    /** Upgraded: turn the ground inside the dome into sculk and vines into sculk veins. */
    private void convertGround(Dome dome) {
        World world = dome.center.getWorld();
        int cx = dome.center.getBlockX();
        int cy = dome.center.getBlockY();
        int cz = dome.center.getBlockZ();
        int r = (int) Math.floor(dome.radius - 1);
        int minY = Math.max(world.getMinHeight() + 1, cy - 8);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double inside = (dome.radius - 1) * (dome.radius - 1) - dx * dx - dz * dz;
                if (inside < 0) {
                    continue;
                }
                int top = Math.min(world.getMaxHeight() - 2, cy + (int) Math.floor(Math.sqrt(inside)));
                for (int y = top; y >= minY; y--) {
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (plugin.blocks().isProtected(block)) {
                        continue;
                    }
                    if (block.getType() == Material.VINE) {
                        dome.session.set(block, toSculkVein(block.getBlockData()));
                        continue;
                    }
                    if (block.isPassable() || org.bukkit.Tag.LEAVES.isTagged(block.getType())) {
                        continue;
                    }
                    Block above = block.getRelative(0, 1, 0);
                    if (Domes.canConvertGround(block) && (above.isPassable() || above.isEmpty())) {
                        dome.session.set(block, SCULK);
                        if (!above.isEmpty() && above.isReplaceable() && !plugin.blocks().isProtected(above)) {
                            Block twoUp = above.getRelative(0, 1, 0);
                            if (twoUp.getType() == above.getType()) {
                                dome.session.set(twoUp, AIR); // top half of tall plants
                            }
                            dome.session.set(above, AIR);
                        }
                    }
                    break; // only the top ground block of each column
                }
            }
        }
    }

    private static BlockData toSculkVein(BlockData vine) {
        MultipleFacing vein = (MultipleFacing) Material.SCULK_VEIN.createBlockData();
        if (vine instanceof MultipleFacing faces) {
            for (BlockFace face : faces.getFaces()) {
                if (vein.getAllowedFaces().contains(face)) {
                    vein.setFace(face, true);
                }
            }
        }
        if (vein.getFaces().isEmpty()) {
            vein.setFace(BlockFace.DOWN, true);
        }
        return vein;
    }

    private void spawnWardens(Player caster, Dome dome) {
        World world = dome.center.getWorld();
        int count = dome.values.upgradedInt("wardens");
        double health = dome.values.upgradedNum("warden-health");
        for (int i = 0; i < count; i++) {
            Location spot = null;
            for (int attempt = 0; attempt < 12 && spot == null; attempt++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double dist = 3 + ThreadLocalRandom.current().nextDouble(Math.max(1, dome.radius / 3));
                spot = Locs.findSafe(dome.center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist), 5);
            }
            if (spot == null) {
                spot = dome.center.clone();
            }
            Warden warden = world.spawn(spot, Warden.class, w -> {
                w.setPersistent(false);
                w.getPersistentDataContainer().set(WARDEN_OWNER, PersistentDataType.STRING, caster.getUniqueId().toString());
                w.getPersistentDataContainer().set(Keys.SPAWNED_BY_SWIZZ, PersistentDataType.BYTE, (byte) 1);
                var max = w.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(health);
                }
                w.setHealth(health);
            });
            dome.wardens.add(warden.getUniqueId());
            world.spawnParticle(Particle.SCULK_SOUL, spot.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
        }
    }

    private static UUID wardenOwner(Warden warden) {
        String raw = warden.getPersistentDataContainer().get(WARDEN_OWNER, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private java.util.Optional<Dome> domeFor(UUID caster) {
        return domes.stream().filter(d -> d.caster.equals(caster)).findFirst();
    }

    private void tickDome(Dome dome) {
        dome.age++;
        Player caster = Bukkit.getPlayer(dome.caster);
        if (dome.age >= dome.duration || caster == null) {
            endDome(dome);
            return;
        }
        World world = dome.center.getWorld();
        if (dome.age % 20 == 0) {
            fillPending(dome, world);
            if (dome.upgraded) {
                upgradedPulse(caster, dome);
            }
        }
        if (dome.age % 10 == 0) {
            world.spawnParticle(Particle.SCULK_SOUL, dome.center.clone().add(0, 1, 0),
                    (int) Math.min(40, dome.radius * 2), dome.radius * 0.5, 2, dome.radius * 0.5, 0.01);
        }
    }

    private void fillPending(Dome dome, World world) {
        if (dome.pending.isEmpty()) {
            return;
        }
        List<BoundingBox> occupied = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            occupied.add(player.getBoundingBox());
        }
        boolean changed = false;
        Iterator<Block> it = dome.pending.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (!Domes.canShellReplace(block)) {
                it.remove();
            } else if (!isOccupied(block, occupied)) {
                dome.session.set(block, SCULK);
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            dome.session.save();
        }
    }

    private void upgradedPulse(Player caster, Dome dome) {
        if (dome.values.upgradedBool("darkness")) {
            for (LivingEntity target : targets(caster, dome.center, dome.radius, TargetFilter.Mode.PLAYERS)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
            }
        }
        List<LivingEntity> prey = targets(caster, dome.center, dome.radius, TargetFilter.Mode.PLAYERS);
        for (UUID id : dome.wardens) {
            if (Bukkit.getEntity(id) instanceof Warden warden && !warden.isDead()) {
                if (!prey.isEmpty()) {
                    LivingEntity nearest = prey.stream()
                            .min((a, b) -> Double.compare(a.getLocation().distanceSquared(warden.getLocation()),
                                    b.getLocation().distanceSquared(warden.getLocation())))
                            .orElse(prey.getFirst());
                    warden.setAnger(nearest, 150);
                    warden.setTarget(nearest);
                }
                if (!dome.inside(warden.getLocation())) {
                    warden.teleport(dome.center);
                }
            }
        }
    }

    private void endDome(Dome dome) {
        if (dome.task != null) {
            dome.task.cancel();
        }
        for (UUID id : dome.wardens) {
            Entity warden = Bukkit.getEntity(id);
            if (warden != null) {
                warden.getWorld().spawnParticle(Particle.SCULK_SOUL, warden.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
                warden.remove();
            }
        }
        dome.wardens.clear();
        dome.session.restore();
        dome.center.getWorld().playSound(dome.center, Sound.BLOCK_SCULK_BREAK, 3f, 0.5f);
        domes.remove(dome);
    }

    private Dome domeContaining(Location loc) {
        for (Dome dome : domes) {
            if (dome.inside(loc)) {
                return dome;
            }
        }
        return null;
    }

    /** No chorus fruit inside a sculk dome. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorus(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.CHORUS_FRUIT && !domes.isEmpty()
                && domeContaining(event.getPlayer().getLocation()) != null) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "dome.no-escape");
        }
    }

    /** No pearling or chorusing out of a sculk dome. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEscapeTeleport(PlayerTeleportEvent event) {
        if (domes.isEmpty() || !isEscapeCause(event.getCause())) {
            return;
        }
        Dome dome = domeContaining(event.getFrom());
        if (dome != null && !dome.inside(event.getTo())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "dome.no-escape");
        }
    }

    static boolean isEscapeCause(PlayerTeleportEvent.TeleportCause cause) {
        String name = cause.name();
        return name.equals("ENDER_PEARL") || name.equals("CONSUMABLE_EFFECT") || name.equals("CHORUS_FRUIT");
    }

    /** The dome shell zone can't be broken, so nobody tunnels out. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakInDome(BlockBreakEvent event) {
        for (Dome dome : domes) {
            if (dome.inShellZone(event.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!domes.isEmpty()) {
            event.blockList().removeIf(b -> domes.stream().anyMatch(d -> d.inShellZone(b)));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!domes.isEmpty()) {
            event.blockList().removeIf(b -> domes.stream().anyMatch(d -> d.inShellZone(b)));
        }
    }

    @EventHandler
    public void onWardenDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Warden warden && wardenOwner(warden) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @Override
    public void cleanup(Player player) {
        BukkitTask charge = charging.remove(player.getUniqueId());
        if (charge != null) {
            charge.cancel();
        }
        lastSculkEat.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        charging.values().forEach(BukkitTask::cancel);
        charging.clear();
        for (Dome dome : new ArrayList<>(domes)) {
            endDome(dome);
        }
    }
}
