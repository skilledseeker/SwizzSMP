package com.swizzsmp.gui;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.ConfigFile;
import com.swizzsmp.config.Messages;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Time;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Builds the /swizzbook written books from guide.yml with live numbers (DESIGN.md §12). */
public final class GuideBook {

    private final SwizzPlugin plugin;
    private final ConfigFile guide;

    public GuideBook(SwizzPlugin plugin, ConfigFile guide) {
        this.plugin = plugin;
        this.guide = guide;
    }

    public Book general() {
        return build(Component.text("SwizzSMP"), guide.yaml().getStringList("general"), null);
    }

    public Book of(SwizzType type) {
        ConfigurationSection values = plugin.abilitiesSection(type);
        return build(type.displayComponent(), guide.yaml().getStringList("swizz." + type.id()), values);
    }

    private Book build(Component title, List<String> rawPages, ConfigurationSection values) {
        TagResolver resolver = values == null ? TagResolver.empty() : TagResolver.resolver(
                valueTag("v", values, GuideBook::trim),
                valueTag("h", values, hp -> trim(hp / 2.0)),
                valueTag("t", values, s -> Time.format(Time.secondsToMillis(s))),
                valueTag("ut", values, s -> Time.format(Time.secondsToMillis(
                        Math.max(plugin.settings().cooldownMinSeconds, s * plugin.settings().upgradedMultiplier)))));
        List<Component> pages = new ArrayList<>();
        for (String raw : rawPages) {
            pages.add(Messages.MM.deserialize(raw, resolver));
        }
        if (pages.isEmpty()) {
            pages.add(Component.text("No guide yet."));
        }
        return Book.book(title, Component.text("SwizzSMP"), pages);
    }

    private static TagResolver valueTag(String name, ConfigurationSection values, DoubleFunction<String> format) {
        return TagResolver.resolver(name, (args, ctx) -> {
            String path = args.popOr("<" + name + "> needs a path").value();
            String text = values.contains(path) ? format.apply(values.getDouble(path)) : "?";
            return Tag.selfClosingInserting(Component.text(text));
        });
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 100) / 100.0);
    }
}
