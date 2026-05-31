package com.cutedifficult.spirit;

import net.minecraft.util.Formatting;

/**
 * Six special weather events, mirroring the {@link SpecialMoon} system but
 * keyed to daytime/rain rather than night. Each pairs a downside with an
 * upside so the weather is something to read and plan around.
 *
 * <p>Weights are relative spawn chances when a weather roll happens.
 */
public enum WeatherEvent {
    ACID_RAIN("Acid Rain",
        "The rain turns acrid. It eats at the unsheltered — but the waters remember the deep.",
        Formatting.GREEN, 18),
    THUNDERSTORM("Thunderstorm of Power",
        "The storm crackles with raw power. Lightning hunts the open — and Kaminari wakes.",
        Formatting.YELLOW, 16),
    BLIZZARD("Blizzard",
        "A blizzard howls. The cold cuts deep and the world goes white.",
        Formatting.AQUA, 14),
    HEATWAVE("Heatwave",
        "A merciless heat settles in. Drink, or wither — but the forge burns bright.",
        Formatting.GOLD, 14),
    FOG("Creeping Fog",
        "A thick fog rolls in. You cannot see them coming — but they cannot see you either.",
        Formatting.GRAY, 16),
    METEOR_SHOWER("Meteor Shower",
        "The sky falls in streaks of fire. Treasure rains down — if it doesn't land on you.",
        Formatting.LIGHT_PURPLE, 6);

    public final String displayName;
    public final String announcement;
    public final Formatting color;
    public final int weight;

    WeatherEvent(String displayName, String announcement, Formatting color, int weight) {
        this.displayName = displayName;
        this.announcement = announcement;
        this.color = color;
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase();
    }

    public static WeatherEvent byId(String id) {
        for (WeatherEvent w : values()) {
            if (w.id().equalsIgnoreCase(id)) return w;
        }
        return null;
    }

    public static int totalWeight() {
        int sum = 0;
        for (WeatherEvent w : values()) sum += w.weight;
        return sum;
    }
}
