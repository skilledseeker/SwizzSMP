package com.swizzsmp.util;

import org.bukkit.NamespacedKey;

/** All namespaced keys used by the plugin (PDC tags, attribute modifiers, recipes). */
public final class Keys {

    public static final String NAMESPACE = "swizz";

    // Item PDC
    public static final NamespacedKey ITEM_ID = key("item");
    public static final NamespacedKey SWORD_ELEMENTS = key("sword_elements");

    // Entity PDC
    public static final NamespacedKey SPAWNED_BY_SWIZZ = key("spawned");

    // Attribute modifiers
    public static final NamespacedKey MOD_PASSIVE_HEALTH = key("passive_health");
    public static final NamespacedKey MOD_PASSIVE_SNEAK = key("passive_sneak");
    public static final NamespacedKey MOD_FREEZE_SPEED = key("freeze_speed");
    public static final NamespacedKey MOD_FREEZE_JUMP = key("freeze_jump");
    public static final NamespacedKey MOD_ROLL_HEALTH = key("roll_health");
    public static final NamespacedKey MOD_ROLL_SCALE = key("roll_scale");
    public static final NamespacedKey MOD_DRAG_SPEED = key("drag_speed");
    public static final NamespacedKey MOD_INVERT_SPEED = key("invert_speed");
    public static final NamespacedKey MOD_CINEMATIC_HOVER = key("cinematic_hover");
    public static final NamespacedKey MOD_TIMESTOP_SPEED = key("timestop_speed");
    public static final NamespacedKey MOD_TIMESTOP_JUMP = key("timestop_jump");
    public static final NamespacedKey MOD_TIMESTOP_GRAVITY = key("timestop_gravity");

    private Keys() {
    }

    public static NamespacedKey key(String value) {
        return new NamespacedKey(NAMESPACE, value);
    }
}
