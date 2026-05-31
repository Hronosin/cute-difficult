package com.cutedifficult.spirit;

import net.minecraft.util.Formatting;

/**
 * The nine special moons. Each night (rolled at dusk) has a chance to be one
 * of these instead of an ordinary night. Each moon is balanced: a downside
 * paired with an upside, so players learn to read the sky and plan around it.
 *
 * <p>Weights are relative spawn chances. Common moons (Pumpkin, Harvest) have
 * higher weight; rare ones (Blue, Hollow) much lower. {@code NONE} is the
 * implicit "ordinary night" and is not in this enum — the handler rolls a
 * total-weight lottery and may decide no special moon occurs.
 */
public enum SpecialMoon {
    BLOOD("Blood Moon",
        "A Blood Moon rises. The hungry things grow strong — but they bleed more freely too.",
        Formatting.DARK_RED, 20),
    PUMPKIN("Pumpkin Moon",
        "A Pumpkin Moon glows orange. Masked horrors swarm, but they carry sweet treasures.",
        Formatting.GOLD, 18),
    HARVEST("Harvest Moon",
        "A Harvest Moon hangs heavy and gold. The forest is at peace, and growth comes swiftly.",
        Formatting.YELLOW, 18),
    FROST("Frost Moon",
        "A Frost Moon chills the air. The cold bites the unsheltered, and the slow grow tough.",
        Formatting.AQUA, 14),
    WOLF("Wolf Moon",
        "A Wolf Moon howls. The packs are hunting tonight — and so can you.",
        Formatting.GRAY, 14),
    MIRROR("Mirror Moon",
        "A Mirror Moon shines cold and still. Wounds reflect both ways tonight.",
        Formatting.WHITE, 10),
    CURSED("Cursed Moon",
        "A Cursed Moon bleeds shadow. The kitsune turn away from you — kegare spreads.",
        Formatting.DARK_PURPLE, 8),
    BLUE("Blue Moon",
        "Once in a blue moon... the spirits are generous beyond reason tonight.",
        Formatting.BLUE, 3),
    HOLLOW("Hollow Moon",
        "A Hollow Moon. The void watches through it. Something ancient stirs, and the dead walk near.",
        Formatting.LIGHT_PURPLE, 2);

    public final String displayName;
    public final String announcement;
    public final Formatting color;
    public final int weight;

    SpecialMoon(String displayName, String announcement, Formatting color, int weight) {
        this.displayName = displayName;
        this.announcement = announcement;
        this.color = color;
        this.weight = weight;
    }

    /** Lowercase id used in commands (e.g. "blood", "pumpkin"). */
    public String id() {
        return name().toLowerCase();
    }

    public static SpecialMoon byId(String id) {
        for (SpecialMoon m : values()) {
            if (m.id().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    /** Total weight of all moons, used for the spawn lottery. */
    public static int totalWeight() {
        int sum = 0;
        for (SpecialMoon m : values()) sum += m.weight;
        return sum;
    }
}
