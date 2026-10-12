package com.swizzsmp.items;

import com.swizzsmp.swizz.SwizzType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Custom item ids (the value of the {@code swizz:item} PDC tag). */
public final class ItemIds {

    public static final String ENERGY = "energy";
    public static final String REROLLER = "reroller";
    public static final String UPGRADER = "upgrader";
    public static final String SWORD = "swizz_sword";
    public static final String MACE = "swizz_mace";

    private ItemIds() {
    }

    public static List<String> all() {
        List<String> ids = new ArrayList<>();
        for (SwizzType type : SwizzType.values()) {
            ids.add(type.itemId());
        }
        ids.add(ENERGY);
        ids.add(REROLLER);
        ids.add(UPGRADER);
        ids.add(SWORD);
        ids.add(MACE);
        return ids;
    }

    /** The swizz a swizz item id belongs to, e.g. "swizz_frost" → FROST. */
    public static Optional<SwizzType> swizzOf(String id) {
        if (id == null || !id.startsWith("swizz_")) {
            return Optional.empty();
        }
        for (SwizzType type : SwizzType.values()) {
            if (type.itemId().equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** True for items with no vanilla use (everything except the sword and mace). */
    public static boolean isToken(String id) {
        return id != null && !SWORD.equals(id) && !MACE.equals(id);
    }
}
