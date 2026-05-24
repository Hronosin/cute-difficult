package com.cutedifficult.spirit;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.FoxEntity;

/**
 * Tail-count-driven stat scaling for foxes.
 *
 * <p>One source of truth so that {@link com.cutedifficult.event.FoxSpawnHandler},
 * the {@code /cd fox tails} command, and any future tail-modifying code
 * all apply consistent stats.
 */
public final class FoxStats {

    /**
     * HP scaling per tail count. Vanilla fox = 10 HP (5 hearts).
     * Tail growth is exponential-ish to make Kyuubi feel mythic.
     * <ul>
     *   <li>1 tail → 10 HP</li>
     *   <li>2 → 14</li>
     *   <li>3 → 20</li>
     *   <li>4 → 28</li>
     *   <li>5 → 40</li>
     *   <li>6 → 55</li>
     *   <li>7 → 72</li>
     *   <li>8 → 88</li>
     *   <li>9 (Kyuubi) → 100</li>
     * </ul>
     */
    public static double hpForTails(int tails) {
        return switch (tails) {
            case 1 -> 10.0;
            case 2 -> 14.0;
            case 3 -> 20.0;
            case 4 -> 28.0;
            case 5 -> 40.0;
            case 6 -> 55.0;
            case 7 -> 72.0;
            case 8 -> 88.0;
            case 9 -> 100.0;
            default -> Math.max(1.0, tails * 10.0);
        };
    }

    /**
     * Apply HP scaling for the given fox's current tail count. Updates
     * both the base attribute (so it persists) and current health (so
     * the fox is full HP after the change).
     */
    public static void applyHpForTails(FoxEntity fox, int tails) {
        EntityAttributeInstance maxHealth = fox.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;
        double newHp = hpForTails(tails);
        maxHealth.setBaseValue(newHp);
        fox.setHealth((float) newHp);
    }

    /**
     * Whether a fox of this element + tails should be able to fly.
     * Yurei and Tengoku foxes gain flight at 5+ tails — they're spirit /
     * sky elementals.
     */
    public static boolean canFly(Element element, int tails) {
        if (tails < 5) return false;
        return element == Element.YUREI || element == Element.TENGOKU;
    }

    /**
     * Cooldown between ability casts in ticks, scaling with tails (more
     * tails → faster cooldowns).
     */
    public static int abilityCooldownTicks(int tails) {
        // 1-tail: 200 ticks (10s). 9-tail: 40 ticks (2s).
        return Math.max(40, 220 - tails * 20);
    }

    private FoxStats() {}
}
