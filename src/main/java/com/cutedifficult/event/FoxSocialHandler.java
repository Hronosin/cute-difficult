package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Random;

/**
 * Social mechanics for kitsune.
 *
 * <p>Currently exposes one static helper used by other handlers
 * ({@code FoxOfferingHandler} can be extended to use it for spirit
 * multipliers when same-element kitsune are nearby).
 *
 * <p>This isn't a tick handler — it's a query interface. We don't run
 * any per-tick logic for social mechanics; instead, when an event
 * happens (offering, death), the relevant handler queries this class
 * to see which other foxes are nearby and modulate behavior.
 *
 * <p>Future expansions can hook here for fox-fox interactions like
 * pack-bonding, mating display particles, or cross-element resonance
 * cascades.
 */
public final class FoxSocialHandler {

    /** Radius for "nearby same-element" social queries. */
    public static final double SOCIAL_RADIUS = 12.0;

    private FoxSocialHandler() {}

    /**
     * Count of other kitsune of the same element within social range of
     * the given fox. Used to scale spirit gain on offerings (multiple
     * same-element witnesses → bigger reward).
     */
    public static int sameElementNearby(ServerWorld world, FoxEntity fox) {
        KitsuneData data = FoxStorage.getOrCreate(fox, new Random());
        Box box = new Box(
            fox.getX() - SOCIAL_RADIUS, fox.getY() - SOCIAL_RADIUS, fox.getZ() - SOCIAL_RADIUS,
            fox.getX() + SOCIAL_RADIUS, fox.getY() + SOCIAL_RADIUS, fox.getZ() + SOCIAL_RADIUS
        );
        List<FoxEntity> nearby = world.getEntitiesByClass(FoxEntity.class, box, f -> f != fox && f.isAlive());
        int count = 0;
        for (FoxEntity other : nearby) {
            KitsuneData od = FoxStorage.getOrCreate(other, new Random());
            if (od.element == data.element) count++;
        }
        return count;
    }
}
