package com.swizzsmp.items;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.data.PlayerData;
import com.swizzsmp.swizz.SwizzType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Right-click behaviour of token items (swizz items, energy, reroller, upgrader).
 * Vanilla use is always denied for tokens.
 */
public final class TokenListener implements Listener {

    private final SwizzPlugin plugin;

    public TokenListener(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String id = CustomItems.idOf(item);
        if (!ItemIds.isToken(id)) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null || player.isDead() || plugin.freeze().isFrozen(player)) {
            return;
        }

        Optional<SwizzType> swizz = ItemIds.swizzOf(id);
        if (swizz.isPresent()) {
            absorbSwizz(player, hand, item, swizz.get());
            return;
        }
        switch (id) {
            case ItemIds.ENERGY -> useEnergy(player, hand, item);
            case ItemIds.REROLLER -> plugin.openReroller(player, hand);
            case ItemIds.UPGRADER -> plugin.rituals().onUpgraderUse(player, hand, event.getClickedBlock(), event.getBlockFace());
            default -> {
            }
        }
    }

    private void absorbSwizz(Player player, EquipmentSlot hand, ItemStack item, SwizzType type) {
        PlayerData data = plugin.store().get(player);
        if (data.hasSwizz()) {
            plugin.messages().send(player, "swizz.already-have");
            return;
        }
        consumeOne(player, hand, item);
        data.setSwizz(type);
        plugin.onStateChanged(player);
        plugin.saveNow(player);
        plugin.messages().send(player, "swizz.obtained", Messages.comp("swizz", type.displayComponent()));
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5,
                new Particle.DustOptions(type.primaryColor(), 1.6f));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.6f);
    }

    private void useEnergy(Player player, EquipmentSlot hand, ItemStack item) {
        PlayerData data = plugin.store().get(player);
        if (plugin.energy().isAtMax(data)) {
            plugin.messages().send(player, "energy.item-at-max");
            return;
        }
        consumeOne(player, hand, item);
        int gained = plugin.energy().add(data, 1);
        plugin.saveNow(player);
        plugin.messages().send(player, "energy.gained", Messages.text("amount", gained),
                Messages.text("value", data.energy()), Messages.text("max", plugin.settings().energyMax));
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
    }

    public static void consumeOne(Player player, EquipmentSlot hand, ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(hand, item);
        }
    }
}
