package com.cutedifficult.quality;

import net.minecraft.util.Formatting;

import java.util.Random;

/**
 * Quality tiers for tools, weapons, and armor.
 *
 * <p>Each tier has a multiplier applied to damage, durability, and
 * armor value (where applicable). Crude items are notably weak; masterwork
 * items are noticeably better than vanilla.
 *
 * <p><b>Distribution on craft:</b> weighted toward Common — masterwork
 * is rare. Players who want consistent gear should expect to re-craft
 * a few times. Found-loot items roll under the same distribution.
 *
 * <table>
 *   <tr><th>Tier</th><th>Multiplier</th><th>Weight</th><th>Color</th></tr>
 *   <tr><td>Crude</td><td>0.6×</td><td>15%</td><td>Dark Gray</td></tr>
 *   <tr><td>Common</td><td>0.85×</td><td>40%</td><td>White</td></tr>
 *   <tr><td>Fine</td><td>1.0×</td><td>30%</td><td>Green</td></tr>
 *   <tr><td>Superior</td><td>1.2×</td><td>12%</td><td>Aqua</td></tr>
 *   <tr><td>Masterwork</td><td>1.5×</td><td>3%</td><td>Gold</td></tr>
 * </table>
 *
 * <p>Sample math: an iron sword (vanilla 6 damage) at Crude deals
 * 3.6 damage; at Masterwork deals 9.0 damage. That's a meaningful
 * spread without trivializing combat at the top end.
 */
public enum QualityTier {
    CRUDE("Crude", 0.6f, 15, Formatting.DARK_GRAY),
    COMMON("Common", 0.85f, 40, Formatting.WHITE),
    FINE("Fine", 1.0f, 30, Formatting.GREEN),
    SUPERIOR("Superior", 1.2f, 12, Formatting.AQUA),
    MASTERWORK("Masterwork", 1.5f, 3, Formatting.GOLD);

    private final String displayName;
    private final float multiplier;
    private final int weight;
    private final Formatting color;

    QualityTier(String displayName, float multiplier, int weight, Formatting color) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.weight = weight;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public float multiplier() { return multiplier; }
    public Formatting color() { return color; }

    /**
     * Random-roll a tier using the weighted distribution above. Total
     * weights sum to 100; we roll a value in [0, 100) and walk the
     * list cumulatively.
     */
    public static QualityTier roll(Random rng) {
        int roll = rng.nextInt(100);
        int cumulative = 0;
        for (QualityTier tier : values()) {
            cumulative += tier.weight;
            if (roll < cumulative) return tier;
        }
        return COMMON; // fallback shouldn't be needed but safe
    }
}
