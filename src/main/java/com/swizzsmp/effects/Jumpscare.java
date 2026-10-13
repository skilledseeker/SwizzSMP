package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Settings;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin jumpscare (DESIGN.md §13.6): a horror picture fills the middle of the screen with a
 * huge scream and a camera jolt, then disappears. The picture is a camera overlay from the
 * texture pack, shown through {@link Flashbang}'s fake helmet, so it ends (and is cleaned up
 * on quit, death and disable) the same way. Players without the pack would see a missing
 * texture, so they get Minecraft's own elder guardian ghost instead.
 */
public final class Jumpscare {

    /** Ticks after the start when the camera jolts again. */
    private static final long[] JOLTS = {0, 3, 7};

    private final SwizzPlugin plugin;

    public Jumpscare(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void scare(Player player) {
        Settings s = plugin.settings();
        if (plugin.resourcePack().hasPack(player)) {
            plugin.flashbang().flash(player, s.jumpscareTicks, s.jumpscareTicks, 0, Flashbang.Mode.OVERLAY, s.jumpscareTexture);
        } else {
            player.showElderGuardian(true);
        }
        // The same sound stacked on itself plays louder than one copy at full volume.
        for (Sound sound : s.jumpscareSounds) {
            for (int i = 0; i < s.jumpscareRepeat; i++) {
                player.playSound(sound, Sound.Emitter.self());
            }
        }
        if (s.jumpscareShake) {
            for (long delay : JOLTS) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
                    }
                }, delay);
            }
        }
    }
}
