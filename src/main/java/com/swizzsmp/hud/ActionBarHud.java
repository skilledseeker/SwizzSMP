package com.swizzsmp.hud;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.Loadout;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The action bar status line (DESIGN.md §11). */
public final class ActionBarHud {

    private static final TextColor ENERGY_ON = TextColor.color(0xD58CFF);

    private final SwizzPlugin plugin;
    private final Map<UUID, Long> suppressedUntil = new HashMap<>();
    private BukkitTask task;

    public ActionBarHud(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int period = plugin.settings().hudUpdateTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Pauses the HUD for a player so another action bar message stays visible. */
    public void suppress(Player player, int ticks) {
        suppressedUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
    }

    public void forget(Player player) {
        suppressedUntil.remove(player.getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player);
            if (!data.hudEnabled()) {
                continue;
            }
            Long until = suppressedUntil.get(player.getUniqueId());
            if (until != null && until > now) {
                continue;
            }
            Component line = render(player, data);
            if (line != null) {
                player.sendActionBar(line);
            }
        }
    }

    private Component render(Player player, PlayerData data) {
        List<SwizzType> elements = plugin.swizz().elements(player);
        if (elements.isEmpty()) {
            if (!data.hasSwizz()) {
                return null;
            }
            long left = data.disabledUntil() - System.currentTimeMillis();
            return Component.text("✖ Swizz disabled " + Time.format(left), NamedTextColor.RED);
        }
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < elements.size(); i++) {
            SwizzType type = elements.get(i);
            if (i > 0) {
                b.append(Component.text("  ┃  ", NamedTextColor.DARK_GRAY));
            }
            b.append(Component.text("◆ ", type.textColor())).append(type.displayComponent());
            if (data.upgraded()) {
                b.append(plugin.messages().get("hud.upgraded"));
            }
            if (i == 0) {
                b.append(Component.space()).append(energyDots(data.energy()));
            }
            String prefix = i == 0 ? "A" : "B";
            b.append(perk(data, "  " + (i == 0 ? "1" : "3") + " ", Perk.ABILITY_1, prefix + "1"));
            b.append(perk(data, "  " + (i == 0 ? "2" : "4") + " ", Perk.ABILITY_2, prefix + "2"));
            b.append(passive(data, type));
        }
        return b.build();
    }

    private Component energyDots(int energy) {
        int max = plugin.settings().energyMax;
        TextComponent.Builder b = Component.text();
        for (int i = 1; i <= max; i++) {
            b.append(Component.text("●", i <= energy ? ENERGY_ON : NamedTextColor.DARK_GRAY));
        }
        return b.build();
    }

    private Component perk(PlayerData data, String label, Perk perk, String key) {
        Component head = Component.text(label, NamedTextColor.GRAY);
        if (!Loadout.isUnlocked(data.loadout(), data.energy(), perk)) {
            return head.append(plugin.messages().get("hud.locked"));
        }
        long left = data.cooldown(key);
        if (left <= 0) {
            return head.append(plugin.messages().get("hud.ready"));
        }
        return head.append(Component.text(Time.format(left), NamedTextColor.RED));
    }

    private Component passive(PlayerData data, SwizzType type) {
        Component head = Component.text("  P ", NamedTextColor.GRAY);
        if (!Loadout.isUnlocked(data.loadout(), data.energy(), Perk.PASSIVE)) {
            return head.append(plugin.messages().get("hud.locked"));
        }
        long reload = data.cooldown("P:" + type.name());
        if (reload > 0) {
            return head.append(Component.text("⟳" + Time.format(reload), NamedTextColor.YELLOW));
        }
        return head.append(plugin.messages().get("hud.ready"));
    }
}
