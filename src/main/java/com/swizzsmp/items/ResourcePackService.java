package com.swizzsmp.items;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Sends players the SwizzSMP resource pack (custom item textures). The pack's SHA-1 is
 * computed by downloading it once at startup (and on reload), so updating the pack only
 * means replacing the zip at the configured URL and running /swizzadmin reload.
 * Config: config.yml → resource-pack.
 */
public final class ResourcePackService implements Listener {

    private final SwizzPlugin plugin;
    private volatile ResourcePackInfo info;
    private String loadedUrl;

    public ResourcePackService(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.settingsFile().getConfigurationSection("resource-pack");
    }

    public boolean enabled() {
        ConfigurationSection c = cfg();
        return c != null && c.getBoolean("enabled") && !c.getString("url", "").isBlank();
    }

    /** Loads (or reloads) the pack info. Downloads the zip once to compute its hash. */
    public void load() {
        info = null;
        if (!enabled()) {
            loadedUrl = null;
            return;
        }
        ConfigurationSection c = cfg();
        String url = c.getString("url", "").trim();
        loadedUrl = url;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("resource-pack.url is not a valid URL: " + url);
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("swizz-pack:" + url).getBytes());
        String sha1 = c.getString("sha1", "").trim();
        if (sha1.length() == 40) {
            info = ResourcePackInfo.resourcePackInfo().id(id).uri(uri).hash(sha1).build();
            sendToAll();
            return;
        }
        CompletableFuture<ResourcePackInfo> future = ResourcePackInfo.resourcePackInfo().id(id).uri(uri).computeHashAndBuild();
        future.whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!url.equals(loadedUrl)) {
                return; // config changed meanwhile
            }
            if (error != null || result == null) {
                plugin.getLogger().log(Level.WARNING, "Could not download the resource pack from " + url
                        + " — players won't get it. (" + (error == null ? "no result" : error.getMessage()) + ")");
                return;
            }
            info = result;
            plugin.getLogger().info("Resource pack ready: " + url + " (sha1 " + result.hash() + ")");
            sendToAll();
        }));
    }

    private void sendToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player);
        }
    }

    public void send(Player player) {
        ResourcePackInfo current = info;
        if (current == null || !enabled()) {
            return;
        }
        ConfigurationSection c = cfg();
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(current)
                .required(c.getBoolean("required"))
                .prompt(Messages.MM.deserialize(c.getString("prompt", "")))
                // false = keep other plugins' packs (e.g. kill effects) loaded next to ours
                .replace(c.getBoolean("replace-other-packs"))
                .build();
        player.sendResourcePacks(request);
    }

    /** Logs what each client did with the pack, so problems can be seen in the console. */
    @EventHandler
    public void onStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent event) {
        ResourcePackInfo current = info;
        if (current == null || !current.id().equals(event.getID())) {
            return;
        }
        plugin.getLogger().info("Resource pack " + event.getStatus() + " for " + event.getPlayer().getName());
    }

    /** Sent a moment after join so a pack another plugin sends on join can't wipe ours. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ConfigurationSection c = cfg();
        long delay = c == null ? 100 : Math.max(0, c.getLong("send-delay-ticks", 100));
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player);
            }
        }, delay);
    }
}
