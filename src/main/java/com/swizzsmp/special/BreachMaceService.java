package com.swizzsmp.special;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemIds;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Breach Swizz Mace (DESIGN.md §13.5): press F while holding it to make your own and your
 * trusted teammates' (within range) ability cooldowns run twice as fast for 5 minutes, i.e.
 * half as long. 10 minute cooldown. Sneak + F stays the ability-1 key.
 */
public final class BreachMaceService implements Listener {

    /** "X:" keys are not sped up by cooldown passives (see CooldownService). */
    public static final String COOLDOWN_KEY = "X:BREACH";
    private static final Color BLUE = Color.fromRGB(0x3C78FF);
    private static final Color PALE = Color.fromRGB(0xA8D0FF);

    private final SwizzPlugin plugin;
    /** Player → when their breach boost ends (epoch millis). */
    private final Map<UUID, Long> boosted = new HashMap<>();
    private BukkitTask task;

    public BreachMaceService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        boosted.clear();
    }

    /** How much faster this player's ability cooldowns run right now (1 = normal). */
    public double cooldownRate(Player player) {
        Long until = boosted.get(player.getUniqueId());
        if (until == null || until <= System.currentTimeMillis()) {
            return 1.0;
        }
        return 1.0 / Math.max(0.05, plugin.settings().breachCooldownMultiplier);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!CustomItems.is(player.getInventory().getItemInMainHand(), ItemIds.BREACH_MACE)) {
            return;
        }
        event.setCancelled(true);
        PlayerData data = plugin.store().get(player);
        long left = data.cooldown(COOLDOWN_KEY);
        if (left > 0) {
            plugin.hud().suppress(player, 30);
            player.sendActionBar(plugin.messages().get("breach.cooldown", Messages.text("time", Time.format(left))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        activate(player, data);
    }

    private void activate(Player player, PlayerData data) {
        Settings s = plugin.settings();
        long duration = Math.round(s.breachDurationMinutes * 60_000);
        long until = System.currentTimeMillis() + duration;
        double radius = s.breachRadius;
        List<Player> team = new ArrayList<>();
        team.add(player);
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && data.trusts(other.getUniqueId())
                    && other.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                team.add(other);
            }
        }
        data.setCooldown(COOLDOWN_KEY, Math.round(s.breachCooldownMinutes * 60_000));
        String time = Time.format(duration);
        for (Player member : team) {
            boosted.put(member.getUniqueId(), until);
            Location at = member.getLocation().add(0, 1, 0);
            member.getWorld().spawnParticle(Particle.DUST, at, 40, 0.4, 0.8, 0.4, 0, new Particle.DustOptions(BLUE, 1.6f));
            member.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.4, 0.8, 0.4, 0.05);
            member.playSound(member.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
            if (!member.equals(player)) {
                plugin.messages().send(member, "breach.buffed", Messages.text("name", player.getName()), Messages.text("time", time));
            }
        }
        plugin.messages().send(player, "breach.activated", Messages.text("count", team.size() - 1), Messages.text("time", time));
        Location center = player.getLocation();
        player.getWorld().playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.8f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
        for (double r = 1; r <= radius; r += 1.5) {
            double ring = r;
            Bukkit.getScheduler().runTaskLater(plugin, () -> Particles.circle(center.clone().add(0, 0.2, 0), ring, (int) (ring * 6), 0,
                    p -> p.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(PALE, 1.4f))), (long) (r / 1.5));
        }
    }

    /** Blue sparks on boosted players; a message when the boost wears off. */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : Map.copyOf(boosted).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (entry.getValue() <= now) {
                boosted.remove(entry.getKey());
                plugin.messages().send(player, "breach.ended");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
                continue;
            }
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.1, 0), 4, 0.35, 0.05, 0.35, 0,
                    new Particle.DustOptions(BLUE, 1.1f));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // The boost only lasts while you're online; logging out ends it.
        boosted.remove(event.getPlayer().getUniqueId());
    }
}
