package com.swizzsmp.combat;

import com.swizzsmp.data.PlayerDataStore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Who a harmful ability may hit (DESIGN.md §6.2). */
public final class TargetFilter {

    public enum Mode {
        /** Untrusted players only. */
        PLAYERS,
        /** Untrusted players and hostile mobs. */
        ENEMIES
    }

    private final PlayerDataStore store;

    public TargetFilter(PlayerDataStore store) {
        this.store = store;
    }

    /** True if {@code caster} trusts {@code other}. */
    public boolean trusts(Player caster, Entity other) {
        return other instanceof Player p && store.get(caster).trusts(p.getUniqueId());
    }

    public boolean canHarm(Player caster, Entity entity, Mode mode) {
        if (!(entity instanceof LivingEntity living) || living.isDead() || entity.equals(caster)) {
            return false;
        }
        if (entity instanceof Player player) {
            GameMode gm = player.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                return false;
            }
            return !store.get(caster).trusts(player.getUniqueId());
        }
        if (mode == Mode.PLAYERS || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            UUID owner = tameable.getOwnerUniqueId();
            if (owner != null && (owner.equals(caster.getUniqueId()) || store.get(caster).trusts(owner))) {
                return false;
            }
        }
        return entity instanceof Enemy;
    }

    /** Valid targets within a sphere, nearest first. */
    public List<LivingEntity> nearby(Player caster, Location center, double radius, Mode mode) {
        List<LivingEntity> result = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(center) <= r2 && canHarm(caster, entity, mode)) {
                result.add((LivingEntity) entity);
            }
        }
        result.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
        return result;
    }

    /** Players the caster trusts within a sphere (not including the caster), nearest first. */
    public List<Player> trustedNearby(Player caster, double radius) {
        List<Player> result = new ArrayList<>();
        Location center = caster.getLocation();
        double r2 = radius * radius;
        for (Player other : caster.getWorld().getPlayers()) {
            if (!other.equals(caster) && !other.isDead() && other.getLocation().distanceSquared(center) <= r2
                    && store.get(caster).trusts(other.getUniqueId())) {
                result.add(other);
            }
        }
        result.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(center)));
        return result;
    }
}
