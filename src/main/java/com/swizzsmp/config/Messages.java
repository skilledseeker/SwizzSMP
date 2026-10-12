package com.swizzsmp.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml access. Every message may use the {@code <prefix>} tag. */
public final class Messages {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private final ConfigFile file;
    private Component prefix = Component.empty();

    public Messages(ConfigFile file) {
        this.file = file;
    }

    public void reload() {
        prefix = MM.deserialize(file.yaml().getString("prefix", ""));
    }

    public String raw(String key) {
        YamlConfiguration yaml = file.yaml();
        String value = yaml.getString(key);
        return value != null ? value : "<red>[missing message: " + key + "]";
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), TagResolver.resolver(TagResolver.resolver(resolvers), Placeholder.component("prefix", prefix)));
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(get(key, resolvers));
    }

    // Small helpers for placeholders.

    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public static TagResolver parsed(String name, String miniMessage) {
        return Placeholder.parsed(name, miniMessage);
    }
}
