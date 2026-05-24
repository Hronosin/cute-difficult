package com.cutedifficult.util;

/**
 * Central configuration for the "smarter mobs" mechanic. Keep numbers here
 * so you can tune without going through five files.
 *
 * <p>Tuning philosophy: changes should be felt, not catastrophic. A 2x
 * sight radius forces map-awareness without making the player unable to
 * exist; door-breaking forces real walls instead of cardboard houses
 * without making any shelter pointless.
 */
public final class SmartMobsTuning {
    private SmartMobsTuning() {}

    /** Follow range for all hostile mobs (vanilla default is 16 or 35 depending on type). */
    public static final double EXTENDED_FOLLOW_RANGE = 48.0;

    /** Whether zombies break wooden doors regardless of difficulty. */
    public static final boolean ZOMBIES_ALWAYS_BREAK_DOORS = true;

    /** Multiplier applied to door-break speed (1.0 = vanilla Hard-difficulty rate). */
    public static final double DOOR_BREAK_SPEED_MULT = 1.5;

    /** Whether spiders can see and target players through solid blocks. */
    public static final boolean SPIDERS_X_RAY = true;

    /** Whether endermen ignore water/rain damage. */
    public static final boolean ENDERMEN_IGNORE_WATER = true;

    /** Whether creepers maintain their fuse even when target moves away. */
    public static final boolean CREEPERS_COMMIT_TO_EXPLOSION = true;

    /** Skeleton strafing intensity (0..1; vanilla doesn't strafe at all by default). */
    public static final float SKELETON_STRAFE_INTENSITY = 0.5f;
}
