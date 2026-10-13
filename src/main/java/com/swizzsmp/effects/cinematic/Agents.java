package com.swizzsmp.effects.cinematic;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Agents: black-suited copies of the player (mannequins with their skin) that multiply, stare,
 * twitch, and fire bullets that fly in slow motion and stop in mid-air.
 */
final class Agents extends Layer {

    static final int FLIGHT = 30;
    private static final Vector3f BULLET = new Vector3f(0.07f, 0.07f, 0.24f);
    private static final Color GREEN = Color.fromRGB(0x00FF41);

    private record Bullet(BlockDisplay display, Location from, Vector3f stop, Vector3f direction, Quaternionf rotation) {
    }

    private final List<Mannequin> agents = new ArrayList<>();
    private final List<Location> homes = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private int firedAt = -1;

    Agents(Cinematic host) {
        super(host);
    }

    int count() {
        return agents.size();
    }

    /** Adds agents until there are {@code total}, on a sunflower spiral around the player. */
    void multiply(int total) {
        ResolvableProfile profile = ResolvableProfile.resolvableProfile(host.player.getPlayerProfile());
        while (agents.size() < total) {
            int k = agents.size();
            double angle = k * Math.PI * (3 - Math.sqrt(5));
            double radius = 6 + (k % 3) * 1.6;
            Location home = Cinematic.floor(host.ground.clone().add(Math.cos(angle) * radius, 2, Math.sin(angle) * radius));
            face(home);
            Mannequin agent = host.spawn(home, Mannequin.class, m -> {
                m.setProfile(profile);
                m.setImmovable(true);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setDescription(Component.empty());
                EntityEquipment gear = m.getEquipment();
                gear.setChestplate(suit(Material.LEATHER_CHESTPLATE));
                gear.setLeggings(suit(Material.LEATHER_LEGGINGS));
                gear.setBoots(suit(Material.LEATHER_BOOTS));
            });
            agents.add(agent);
            homes.add(home);
            host.world.spawnParticle(Particle.DUST, home.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0, new Particle.DustOptions(GREEN, 1.3f));
        }
        host.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f);
        host.sound(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f);
    }

    private static ItemStack suit(Material piece) {
        ItemStack item = new ItemStack(piece);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(0x141414));
        item.setItemMeta(meta);
        return item;
    }

    /** Agents stare at the player and glitch in place. */
    void jitter() {
        for (int i = 0; i < agents.size(); i++) {
            Location home = homes.get(i);
            face(home);
            Location at = home.clone();
            if (Cinematic.random().nextInt(14) == 0) {
                at.add(Cinematic.random().nextDouble(-0.35, 0.35), 0, Cinematic.random().nextDouble(-0.35, 0.35));
            }
            agents.get(i).teleport(at);
        }
    }

    private void face(Location at) {
        Vector look = host.player.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (look.lengthSquared() > 1e-4) {
            at.setDirection(look);
        }
        at.setPitch(0);
    }

    /** Up to 12 agents fire a bullet at the target; each flies {@link #FLIGHT} ticks and stops 1.5 blocks short. */
    void fire(Location target) {
        firedAt = host.t;
        for (int i = 0; i < Math.min(12, agents.size()); i++) {
            Location from = Cinematic.flat(agents.get(i).getLocation().add(0, 1.4, 0));
            Vector direction = target.toVector().subtract(from.toVector()).normalize();
            Vector stopAt = target.toVector().subtract(direction.clone().multiply(1.5))
                    .add(new Vector(Cinematic.random().nextDouble(-0.4, 0.4), Cinematic.random().nextDouble(-0.4, 0.4), Cinematic.random().nextDouble(-0.4, 0.4)));
            Vector3f stop = stopAt.subtract(from.toVector()).toVector3f();
            Vector3f dir = direction.toVector3f();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
            BlockDisplay bullet = host.spawn(from, BlockDisplay.class, d -> {
                d.setBlock(Material.GOLD_BLOCK.createBlockData());
                d.setBrightness(Cinematic.FULL_BRIGHT);
                d.setTransformation(Cinematic.box(new Vector3f(), rotation, BULLET));
            });
            Bukkit.getScheduler().runTaskLater(host.plugin, () -> Cinematic.animate(bullet, Cinematic.box(stop, rotation, BULLET), FLIGHT - 2), 2L);
            bullets.add(new Bullet(bullet, from, stop, dir, rotation));
            host.world.spawnParticle(Particle.FLASH, from, 1, 0, 0, 0, 0, Color.fromRGB(0xFFE0A0));
        }
        for (Player viewer : host.viewers()) {
            for (int i = 0; i < 3; i++) {
                Cinematic.sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
            }
            Cinematic.sound(viewer, Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
            Cinematic.sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
            Cinematic.effect(viewer, PotionEffectType.SLOWNESS, FLIGHT + 10, 1);
        }
    }

    /** Slow-motion air ripples trailing each bullet; call every few ticks while they fly. */
    void ripples() {
        if (firedAt < 0) {
            return;
        }
        double f = Cinematic.clamp((host.t - firedAt - 2) / (double) (FLIGHT - 2));
        for (Bullet bullet : bullets) {
            Vector3f d = bullet.direction();
            Vector3f u = new Vector3f(d).cross(0, 1, 0);
            if (u.lengthSquared() < 1e-4) {
                u.set(1, 0, 0);
            }
            u.normalize();
            Vector3f v = new Vector3f(d).cross(u).normalize();
            Location at = bullet.from().clone().add(bullet.stop().x * f, bullet.stop().y * f, bullet.stop().z * f);
            for (int i = 0; i < 12; i++) {
                double a = 2 * Math.PI * i / 12;
                double r = 0.32;
                host.world.spawnParticle(Particle.DUST, at.clone().add(
                        (u.x * Math.cos(a) + v.x * Math.sin(a)) * r,
                        (u.y * Math.cos(a) + v.y * Math.sin(a)) * r,
                        (u.z * Math.cos(a) + v.z * Math.sin(a)) * r), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xE8FFF0), 0.5f));
            }
        }
    }

    void stopSound() {
        host.sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 2f);
        host.sound(Sound.BLOCK_BELL_RESONATE, 1.6f);
    }

    /** The stopped bullets fall and clink on the floor. */
    void dropBullets() {
        for (Bullet bullet : bullets) {
            Vector3f landed = new Vector3f(bullet.stop().x, (float) (host.ground.getY() + 0.04 - bullet.from().getY()), bullet.stop().z);
            Quaternionf lying = new Quaternionf().rotateY((float) Cinematic.random().nextDouble(Math.PI * 2)).rotateX((float) (Math.PI / 2));
            Cinematic.animate(bullet.display(), Cinematic.box(landed, lying, BULLET), 10 + Cinematic.random().nextInt(5));
        }
        for (int i = 0; i < 4; i++) {
            int delay = 10 + i * 2;
            Bukkit.getScheduler().runTaskLater(host.plugin, () -> host.sound(Sound.BLOCK_CHAIN_FALL, 1.8f + Cinematic.random().nextFloat() * 0.2f), delay);
        }
    }

    /** The stopped bullets fly straight back to whoever fired them, fast. */
    void recallBullets(int ticks) {
        for (Bullet bullet : bullets) {
            Cinematic.animate(bullet.display(), Cinematic.box(new Vector3f(), bullet.rotation(), BULLET), ticks);
        }
        host.sound(Sound.ENTITY_ARROW_SHOOT, 0.6f);
        host.sound(Sound.BLOCK_BEACON_POWER_SELECT, 1.8f);
    }

    void removeBullets() {
        bullets.forEach(b -> host.remove(b.display()));
        bullets.clear();
    }

    /** The last {@code count} agents dissolve into rising green code. */
    void dissolve(int count) {
        for (int i = 0; i < count && !agents.isEmpty(); i++) {
            Mannequin agent = agents.remove(agents.size() - 1);
            homes.remove(homes.size() - 1);
            Location at = agent.getLocation().add(0, 1, 0);
            host.world.spawnParticle(Particle.DUST, at, 60, 0.3, 0.9, 0.3, 0, new Particle.DustOptions(GREEN, 1.4f));
            host.world.spawnParticle(Particle.TRAIL, at, 8, 0.3, 0.6, 0.3, 0, new Particle.Trail(at.clone().add(0, 8, 0), GREEN, 20));
            host.remove(agent);
        }
    }
}
