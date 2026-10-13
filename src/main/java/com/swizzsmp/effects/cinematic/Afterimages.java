package com.swizzsmp.effects.cinematic;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mannequin;

import java.util.ArrayList;
import java.util.List;

/** Statues of the player left along their path (mannequins with their skin), shattered later. */
final class Afterimages extends Layer {

    private static final Color ICE = Color.fromRGB(0x9FF3FF);

    private final List<Mannequin> echoes = new ArrayList<>();
    private final int max;
    private Location last;

    Afterimages(Cinematic host, int max) {
        super(host);
        this.max = max;
    }

    /** Starts measuring from where the player stands now. */
    void start() {
        last = host.player.getLocation();
    }

    /** Leaves a statue where the player was, once they have moved on; call every few ticks. */
    void echo() {
        Location at = host.player.getLocation();
        if (echoes.size() >= max || last == null || at.distanceSquared(last) < 0.8 * 0.8) {
            return;
        }
        Location from = last;
        last = at;
        ResolvableProfile profile = ResolvableProfile.resolvableProfile(host.player.getPlayerProfile());
        echoes.add(host.spawn(from, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setGravity(false);
            m.setDescription(Component.empty());
        }));
        host.world.spawnParticle(Particle.DUST, from.clone().add(0, 1, 0), 15, 0.3, 0.8, 0.3, 0, new Particle.DustOptions(ICE, 1f));
        host.world.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.6f);
    }

    /** The newest {@code count} statues burst like glass. */
    void shatter(int count) {
        for (int i = 0; i < count && !echoes.isEmpty(); i++) {
            Mannequin echo = echoes.remove(echoes.size() - 1);
            Location at = echo.getLocation().add(0, 1, 0);
            host.world.spawnParticle(Particle.DUST, at, 40, 0.3, 0.9, 0.3, 0, new Particle.DustOptions(ICE, 1.3f));
            host.world.spawnParticle(Particle.CRIT, at, 20, 0.3, 0.8, 0.3, 0.3);
            host.world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
            host.remove(echo);
        }
    }

    boolean any() {
        return !echoes.isEmpty();
    }
}
