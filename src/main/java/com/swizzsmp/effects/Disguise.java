package com.swizzsmp.effects;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.swizzsmp.SwizzPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Mirage Dome disguise (DESIGN.md §7.1). Every participant takes the caster's skin.
 * Everyone except the caster sees all participants wearing the caster's armor.
 * Nametags are hidden, and only the caster sees floating real-name labels.
 */
public final class Disguise {

    private static final String TEAM_NAME = "swizz_mirage";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final SwizzPlugin plugin;
    private final UUID caster;
    private final Set<UUID> participants;
    private final boolean hideNametags;
    private final Map<UUID, PlayerProfile> originalProfiles = new HashMap<>();
    private final Map<UUID, Team> previousTeams = new HashMap<>();
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private boolean active;

    public Disguise(SwizzPlugin plugin, Player caster, Set<UUID> participants, boolean hideNametags) {
        this.plugin = plugin;
        this.caster = caster.getUniqueId();
        this.participants = participants;
        this.hideNametags = hideNametags;
    }

    public void start() {
        Player casterPlayer = Bukkit.getPlayer(caster);
        if (casterPlayer == null) {
            return;
        }
        active = true;
        List<ProfileProperty> skin = new ArrayList<>();
        for (ProfileProperty property : casterPlayer.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                skin.add(property);
            }
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = null;
        if (hideNametags) {
            team = main.getTeam(TEAM_NAME);
            if (team == null) {
                team = main.registerNewTeam(TEAM_NAME);
            }
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!id.equals(caster) && !skin.isEmpty()) {
                PlayerProfile original = player.getPlayerProfile();
                originalProfiles.put(id, original.clone());
                PlayerProfile changed = original.clone();
                List<ProfileProperty> kept = new ArrayList<>();
                for (ProfileProperty property : changed.getProperties()) {
                    if (!property.getName().equals("textures")) {
                        kept.add(property);
                    }
                }
                kept.addAll(skin);
                changed.clearProperties();
                changed.setProperties(kept);
                player.setPlayerProfile(changed);
            }
            if (team != null) {
                Team previous = main.getEntryTeam(player.getName());
                if (previous != null && !previous.equals(team)) {
                    previousTeams.put(id, previous);
                }
                team.addEntry(player.getName());
            }
            if (!id.equals(caster)) {
                spawnLabel(casterPlayer, player);
            }
        }
        refreshEquipment();
    }

    private void spawnLabel(Player viewer, Player target) {
        TextDisplay label = target.getWorld().spawn(target.getLocation().add(0, target.getHeight() + 0.4, 0), TextDisplay.class, d -> {
            d.text(Component.text(target.getName(), NamedTextColor.LIGHT_PURPLE));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setSeeThrough(true);
            d.setShadowed(true);
            d.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
        });
        target.addPassenger(label);
        viewer.showEntity(plugin, label);
        labels.put(target.getUniqueId(), label);
    }

    /** Re-sends the fake armor. Call every couple of ticks: real updates would undo it. */
    public void refreshEquipment() {
        if (!active) {
            return;
        }
        Player casterPlayer = Bukkit.getPlayer(caster);
        if (casterPlayer == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> fake = new EnumMap<>(EquipmentSlot.class);
        EntityEquipment casterGear = casterPlayer.getEquipment();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = casterGear.getItem(slot);
            fake.put(slot, item == null ? ItemStack.empty() : item.clone());
        }
        List<Player> targets = onlineParticipants();
        for (Player viewer : casterPlayer.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(caster)) {
                continue;
            }
            for (Player target : targets) {
                if (!target.equals(viewer) && !target.equals(casterPlayer)) {
                    viewer.sendEquipmentChange(target, fake);
                }
            }
        }
    }

    /** One participant leaves early (quit). */
    public void removeParticipant(Player player) {
        if (!participants.remove(player.getUniqueId())) {
            return;
        }
        restorePlayer(player);
    }

    public void end() {
        if (!active) {
            return;
        }
        active = false;
        for (UUID id : List.copyOf(participants)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restorePlayer(player);
            }
        }
        participants.clear();
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM_NAME);
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    private void restorePlayer(Player player) {
        UUID id = player.getUniqueId();
        TextDisplay label = labels.remove(id);
        if (label != null) {
            label.remove();
        }
        PlayerProfile original = originalProfiles.remove(id);
        if (original != null) {
            player.setPlayerProfile(original);
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM_NAME);
        if (team != null) {
            team.removeEntry(player.getName());
        }
        Team previous = previousTeams.remove(id);
        if (previous != null) {
            try {
                previous.addEntry(player.getName());
            } catch (IllegalStateException ignored) {
                // the old team was unregistered meanwhile
            }
        }
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = player.getEquipment().getItem(slot);
            real.put(slot, item == null ? ItemStack.empty() : item);
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, real);
            }
        }
    }

    private List<Player> onlineParticipants() {
        List<Player> list = new ArrayList<>();
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /** Removes leftover state from a crash (team entries are saved with the world). */
    public static void cleanupLeftovers() {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM_NAME);
        if (team != null) {
            team.unregister();
        }
    }
}
