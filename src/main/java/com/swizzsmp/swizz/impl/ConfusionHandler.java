package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.ComboTracker;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.ControlInverter;
import com.swizzsmp.effects.Disguise;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** CONFUSION (DESIGN.md §7.1). */
public final class ConfusionHandler extends BaseHandler implements Listener, ComboTracker.ComboListener {

    private static final Color PURPLE = Color.fromRGB(0xB05CE0);
    private static final Color DARK_PURPLE = Color.fromRGB(0x6C2D91);

    /** A dazed player's hits on {@code target} miss. */
    private static final class Miss {
        final UUID target;
        int left;
        final long until;

        Miss(UUID target, int left, long until) {
            this.target = target;
            this.left = left;
            this.until = until;
        }
    }

    private final class Dome {
        final UUID caster;
        final Location center;
        final double radius;
        final int duration;
        final boolean blockEntry;
        final Set<UUID> sealed = new HashSet<>();
        final Disguise disguise;
        BukkitTask task;
        int age;

        Dome(Player caster, AbilityValues v, Set<UUID> participants) {
            this.caster = caster.getUniqueId();
            this.center = caster.getLocation().clone();
            this.radius = v.num("radius");
            this.duration = Time.secondsToTicks(v.num("duration"));
            this.blockEntry = v.bool("block-entry");
            this.sealed.addAll(participants);
            this.disguise = new Disguise(plugin, caster, new HashSet<>(participants), v.bool("hide-nametags"));
        }

        boolean inside(Location loc, double margin) {
            return loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= (radius - margin) * (radius - margin);
        }
    }

    private final ControlInverter inverter;
    private final Map<UUID, Miss> misses = new HashMap<>();
    /** Attackers whose current combo already triggered the miss (for the upgraded 5th hit). */
    private final Set<UUID> triggered = new HashSet<>();
    private final List<Dome> domes = new ArrayList<>();
    private final List<BukkitTask> rescrambles = new ArrayList<>();

    public ConfusionHandler(SwizzPlugin plugin, ControlInverter inverter) {
        super(plugin, SwizzType.CONFUSION);
        this.inverter = inverter;
        Disguise.cleanupLeftovers();
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void onComboHit(Player attacker, Player victim, int count, EntityDamageByEntityEvent event) {
        if (!hasPassive(attacker) || !canHarm(attacker, victim, TargetFilter.Mode.PLAYERS)) {
            return;
        }
        AbilityValues v = passiveValues();
        if (count == v.integer("combo-hits") && passiveReady(attacker)) {
            misses.put(victim.getUniqueId(), new Miss(attacker.getUniqueId(), Math.max(1, v.integer("miss-count")),
                    System.currentTimeMillis() + Time.secondsToMillis(v.num("miss-window"))));
            triggered.add(attacker.getUniqueId());
            startReload(attacker, v.num("reload"));
            plugin.messages().send(attacker, "confusion.miss-armed", Messages.text("name", victim.getName()));
            victim.getWorld().spawnParticle(Particle.WITCH, victim.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.05);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.4f);
            return;
        }
        if (upgraded(attacker) && triggered.contains(attacker.getUniqueId()) && count == v.upgradedInt("levitation-hit")) {
            triggered.remove(attacker.getUniqueId());
            victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                    Time.secondsToTicks(v.upgradedNum("levitation-seconds")), Math.max(0, v.upgradedInt("levitation-level") - 1)));
            victim.getWorld().spawnParticle(Particle.REVERSE_PORTAL, victim.getLocation(), 40, 0.4, 0.2, 0.4, 0.05);
        }
    }

    @Override
    public void onComboBreak(Player attacker, UUID victim, int count) {
        triggered.remove(attacker.getUniqueId());
    }

    /** A dazed player's melee hits on the confusion user miss. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDazedHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Miss miss = misses.get(attacker.getUniqueId());
        if (miss == null || !miss.target.equals(event.getEntity().getUniqueId())) {
            return;
        }
        if (miss.until < System.currentTimeMillis()) {
            misses.remove(attacker.getUniqueId());
            return;
        }
        event.setCancelled(true);
        if (--miss.left <= 0) {
            misses.remove(attacker.getUniqueId());
        }
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.8f);
        attacker.getWorld().spawnParticle(Particle.WITCH, event.getEntity().getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.02);
        plugin.hud().suppress(attacker, 30);
        attacker.sendActionBar(plugin.messages().get("confusion.missed"));
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        return ctx.perk() == Perk.ABILITY_1 ? AbilityResult.armed() : mirageDome(ctx);
    }

    // ── Ability 1: Scramble ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onScrambleHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !plugin.abilities().isArmed(attacker, SwizzType.CONFUSION, Perk.ABILITY_1)
                || !canHarm(attacker, victim, TargetFilter.Mode.PLAYERS)) {
            return;
        }
        plugin.abilities().consumeArm(attacker, SwizzType.CONFUSION, Perk.ABILITY_1).ifPresent(ctx -> {
            scramble(victim);
            plugin.messages().send(attacker, "confusion.scrambled-target", Messages.text("name", victim.getName()));
            ctx.commitCooldown();
            if (ctx.upgraded()) {
                UUID victimId = victim.getUniqueId();
                BukkitTask[] holder = new BukkitTask[1];
                holder[0] = later(Time.secondsToTicks(ctx.values().upgradedNum("second-shuffle-delay")), () -> {
                    rescrambles.remove(holder[0]);
                    Player again = Bukkit.getPlayer(victimId);
                    if (again != null && !again.isDead()) {
                        scramble(again);
                    }
                });
                rescrambles.add(holder[0]);
            }
        });
    }

    private void scramble(Player victim) {
        PlayerInventory inv = victim.getInventory();
        List<ItemStack> hotbar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            hotbar.add(inv.getItem(i));
        }
        List<ItemStack> shuffled = new ArrayList<>(hotbar);
        for (int attempt = 0; attempt < 10; attempt++) {
            Collections.shuffle(shuffled);
            if (!sameOrder(hotbar, shuffled)) {
                break;
            }
        }
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, shuffled.get(i));
        }
        plugin.messages().send(victim, "confusion.scrambled");
        victim.playSound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        victim.getWorld().spawnParticle(Particle.WITCH, victim.getEyeLocation(), 25, 0.4, 0.4, 0.4, 0.05);
        dust(victim.getEyeLocation(), 15, 0.4, 1.2f);
    }

    private static boolean sameOrder(List<ItemStack> a, List<ItemStack> b) {
        for (int i = 0; i < a.size(); i++) {
            ItemStack x = a.get(i);
            ItemStack y = b.get(i);
            if (x == null ? y != null : !x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    // ── Ability 2: Mirage Dome ────────────────────────────────────────────────

    private AbilityResult mirageDome(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        Set<UUID> participants = new HashSet<>();
        double radius = v.num("radius");
        for (Player player : caster.getWorld().getPlayers()) {
            GameMode gm = player.getGameMode();
            if (!player.isDead() && gm != GameMode.SPECTATOR && gm != GameMode.CREATIVE
                    && player.getLocation().distanceSquared(caster.getLocation()) <= radius * radius) {
                participants.add(player.getUniqueId());
            }
        }
        participants.add(caster.getUniqueId());
        Dome dome = new Dome(caster, v, participants);
        domes.add(dome);
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isInsideVehicle()) {
                player.leaveVehicle(); // vehicles would carry them through the seal
            }
        }
        dome.disguise.start();

        if (ctx.upgraded() && v.upgradedBool("invert-controls")) {
            for (UUID id : participants) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && canHarm(caster, player, TargetFilter.Mode.PLAYERS)) {
                    inverter.invert(player, dome.duration, v.upgradedNum("invert-strength"), v.upgradedNum("invert-sprint-multiplier"));
                }
            }
        }
        World world = caster.getWorld();
        world.playSound(dome.center, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 2f, 0.8f);
        world.playSound(dome.center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
        dome.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickDome(dome), 0L, 1L);
        return AbilityResult.success();
    }

    private void tickDome(Dome dome) {
        if (dome.age++ >= dome.duration || Bukkit.getPlayer(dome.caster) == null) {
            endDome(dome);
            return;
        }
        if (dome.age % 2 == 0) {
            dome.disguise.refreshEquipment();
        }
        if (dome.age % 5 == 0) {
            World world = dome.center.getWorld();
            Particles.sphere(dome.center, dome.radius, 260, false, at -> {
                Color color = ((int) (at.getY() * 2) & 1) == 0 ? PURPLE : DARK_PURPLE;
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.6f));
            });
        }
    }

    private void endDome(Dome dome) {
        if (dome.task != null) {
            dome.task.cancel();
        }
        dome.disguise.end();
        for (UUID id : dome.sealed) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                inverter.release(player);
            }
        }
        dome.sealed.clear();
        dome.center.getWorld().playSound(dome.center, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2f, 0.6f);
        domes.remove(dome);
    }

    /** Sealed: participants can't leave, outsiders can't enter. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (domes.isEmpty() || !event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        for (Dome dome : domes) {
            boolean participant = dome.sealed.contains(player.getUniqueId());
            boolean wasInside = dome.inside(event.getFrom(), 0.4);
            boolean nowInside = dome.inside(event.getTo(), 0.4);
            if (participant && wasInside && !nowInside) {
                bounce(event, dome, true);
                return;
            }
            if (!participant && dome.blockEntry && !wasInside && dome.inside(event.getTo(), -0.4)) {
                bounce(event, dome, false);
                return;
            }
        }
    }

    private void bounce(PlayerMoveEvent event, Dome dome, boolean inward) {
        Location from = event.getFrom().clone();
        from.setYaw(event.getTo().getYaw());
        from.setPitch(event.getTo().getPitch());
        event.setTo(from);
        Vector push = dome.center.toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() > 0) {
            push.normalize().multiply(inward ? 0.35 : -0.35).setY(0.1);
            event.getPlayer().setVelocity(push);
        }
        event.getPlayer().getWorld().spawnParticle(Particle.DUST, event.getPlayer().getLocation().add(0, 1, 0), 8,
                0.3, 0.5, 0.3, 0, new Particle.DustOptions(PURPLE, 1.4f));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (domes.isEmpty()) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (!SculkHandler.isEscapeCause(cause) && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return; // admin /tp (COMMAND) is always allowed
        }
        Player player = event.getPlayer();
        for (Dome dome : domes) {
            boolean participant = dome.sealed.contains(player.getUniqueId());
            if ((participant && !dome.inside(event.getTo(), 0.4))
                    || (!participant && dome.blockEntry && dome.inside(event.getTo(), -0.4))) {
                event.setCancelled(true);
                if (participant) {
                    plugin.messages().send(player, "dome.no-escape");
                }
                return;
            }
        }
    }

    /** No chorus fruit for anyone sealed in a Mirage Dome. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorus(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == org.bukkit.Material.CHORUS_FRUIT
                && domes.stream().anyMatch(d -> d.sealed.contains(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "dome.no-escape");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && domes.stream().anyMatch(d -> d.sealed.contains(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** True while the player wears a Mirage dome look-alike disguise (or is its caster). */
    public boolean isDisguised(UUID id) {
        for (Dome dome : domes) {
            if (dome.disguise.includes(id)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onParticipantDeath(PlayerDeathEvent event) {
        for (Dome dome : domes) {
            dome.sealed.remove(event.getPlayer().getUniqueId());
        }
    }

    @Override
    public void cleanup(Player player) {
        misses.remove(player.getUniqueId());
        triggered.remove(player.getUniqueId());
    }

    @Override
    public void onQuit(Player player) {
        cleanup(player);
        UUID id = player.getUniqueId();
        for (Dome dome : new ArrayList<>(domes)) {
            if (dome.caster.equals(id)) {
                endDome(dome);
            } else {
                dome.sealed.remove(id);
                dome.disguise.removeParticipant(player);
            }
        }
    }

    @Override
    public void shutdown() {
        for (Dome dome : new ArrayList<>(domes)) {
            endDome(dome);
        }
        rescrambles.forEach(BukkitTask::cancel);
        rescrambles.clear();
        misses.clear();
        triggered.clear();
    }
}
