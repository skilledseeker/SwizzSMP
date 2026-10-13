package com.swizzsmp.effects;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Settings;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.swizz.impl.ConfusionHandler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cosmetic cherry blossom trail (DESIGN.md §13.5). Pink petals drift down behind the
 * player's feet while they move, and puff out when they jump or land from a fall. Three
 * layers: Minecraft's cherry petals, leaf petals tinted a random pink, and pink glitter
 * that fades to white. The trail is hidden whenever it could give the player away, and
 * it is particles only, so there is nothing to clean up.
 */
public final class PetalTrail implements Listener {

    public static final String PERMISSION = "swizz.trail";
    /** Clients don't draw particles further away than this, so nobody beyond it is sent any. */
    private static final double VIEW_DISTANCE = 32;
    private static final double PETAL_LIFT = 0.35;
    private static final double PETAL_SPREAD = 0.2;
    private static final double SPARKLE_LIFT = 0.15;
    private static final double SPARKLE_SPREAD = 0.15;

    private static final class Walker {
        Location last;
        boolean wasOnGround = true;
        float peakFall;
    }

    private record Puff(Particle type, Location at, double spread, Object data) {
    }

    private final SwizzPlugin plugin;
    private final Map<UUID, Walker> walkers = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public PetalTrail(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        walkers.clear();
    }

    /** Given by an admin ({@code /swizzadmin trail}) or through the swizz.trail permission. */
    public boolean hasAccess(Player player) {
        return plugin.store().get(player).trailGranted() || player.hasPermission(PERMISSION);
    }

    private boolean wears(Player player) {
        return plugin.settings().trailEnabled && plugin.store().get(player).trailEnabled() && hasAccess(player);
    }

    private void tick() {
        Settings s = plugin.settings();
        boolean draw = ++ticks % s.trailIntervalTicks == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!wears(player)) {
                walkers.remove(player.getUniqueId());
                continue;
            }
            Walker walker = walkers.computeIfAbsent(player.getUniqueId(), id -> new Walker());
            boolean shown = shown(player);
            land(player, walker, shown, s);
            if (draw) {
                trail(player, walker, shown, s);
            }
        }
    }

    /** Petals scattered along the path walked since the last drop. */
    private void trail(Player player, Walker walker, boolean shown, Settings s) {
        Location now = player.getLocation();
        Location last = walker.last;
        if (last == null || last.getWorld() != now.getWorld()) {
            walker.last = now;
            return;
        }
        double moved = last.distance(now);
        if (moved < s.trailMinMove) {
            return; // keep the old point, so slow creeping still adds up
        }
        walker.last = now;
        if (!shown) {
            return;
        }
        double factor = player.isSprinting() || player.isGliding() ? s.trailFastMultiplier : 1;
        List<Puff> puffs = new ArrayList<>();
        for (int i = 0; i < scaled(s.trailCherryPetals, factor); i++) {
            puffs.add(petal(along(last, now, PETAL_LIFT), true, s));
        }
        for (int i = 0; i < scaled(s.trailPinkPetals, factor); i++) {
            puffs.add(petal(along(last, now, PETAL_LIFT), false, s));
        }
        for (int i = 0; i < scaled(s.trailSparkles, factor); i++) {
            puffs.add(sparkle(along(last, now, SPARKLE_LIFT), s));
        }
        send(player, puffs);
    }

    /**
     * A puff on landing from a real fall (the highest fall distance seen while airborne).
     * The on-ground flag comes from the client, which is fine for a cosmetic.
     */
    @SuppressWarnings("deprecation")
    private void land(Player player, Walker walker, boolean shown, Settings s) {
        boolean onGround = player.isOnGround();
        if (!onGround) {
            walker.peakFall = Math.max(walker.peakFall, player.getFallDistance());
        } else if (!walker.wasOnGround) {
            if (shown && walker.peakFall >= s.trailLandMinFall) {
                burst(player, s.trailLandBurst, s);
            }
            walker.peakFall = 0;
        }
        walker.wasOnGround = onGround;
    }

    /** A ring of petals and glitter around the feet. */
    private void burst(Player player, int count, Settings s) {
        Location feet = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Puff> puffs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double radius = 0.3 + random.nextDouble(0.5);
            Location at = feet.clone().add(Math.cos(angle) * radius, 0.2 + random.nextDouble(0.4), Math.sin(angle) * radius);
            puffs.add(i % 3 == 2 ? sparkle(at, s) : petal(at, i % 3 == 0, s));
        }
        send(player, puffs);
    }

    private static Puff petal(Location at, boolean cherry, Settings s) {
        return cherry ? new Puff(Particle.CHERRY_LEAVES, at, PETAL_SPREAD, null)
                : new Puff(Particle.TINTED_LEAVES, at, PETAL_SPREAD, pink(s));
    }

    private static Puff sparkle(Location at, Settings s) {
        return new Puff(Particle.DUST_COLOR_TRANSITION, at, SPARKLE_SPREAD,
                new Particle.DustTransition(pink(s), s.trailSparkleFade, s.trailSparkleSize));
    }

    private static Color pink(Settings s) {
        return s.trailColors.get(ThreadLocalRandom.current().nextInt(s.trailColors.size()));
    }

    private static Location along(Location from, Location to, double lift) {
        double t = ThreadLocalRandom.current().nextDouble();
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(t)).add(0, lift, 0);
    }

    private static int scaled(int count, double factor) {
        return (int) Math.round(count * factor);
    }

    /** Sent per viewer, so players hidden from someone (vanish plugins) stay hidden. */
    private static void send(Player source, List<Puff> puffs) {
        Location at = source.getLocation();
        for (Player viewer : source.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > VIEW_DISTANCE * VIEW_DISTANCE || !viewer.canSee(source)) {
                continue;
            }
            for (Puff puff : puffs) {
                viewer.spawnParticle(puff.type(), puff.at(), 1, puff.spread(), puff.spread() / 2, puff.spread(), 0, puff.data());
            }
        }
    }

    /** False whenever the trail could give the player away ("vanished" is set by vanish plugins). */
    @SuppressWarnings("deprecation")
    private boolean shown(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || (plugin.settings().trailHideWhileSneaking && player.isSneaking())) {
            return false;
        }
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return false;
            }
        }
        // Inside a Mirage dome everyone looks alike; petals would point out the real player.
        return !(plugin.swizz().handler(SwizzType.CONFUSION) instanceof ConfusionHandler confusion
                && confusion.isDisguised(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (wears(player) && shown(player)) {
            burst(player, plugin.settings().trailJumpBurst, plugin.settings());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        forgetPosition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forgetPosition(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        walkers.remove(event.getPlayer().getUniqueId());
    }

    /** No petal line from where the player was to where they were sent. */
    private void forgetPosition(Player player) {
        Walker walker = walkers.get(player.getUniqueId());
        if (walker != null) {
            walker.last = null;
        }
    }
}
