package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Makes skeletons (and strays/wither skeletons) switch to melee combat when
 * the player gets close enough that bow shots become impractical.
 *
 * <p><b>State machine:</b>
 * <ul>
 *   <li>BOW mode (default): vanilla behavior, ranged attacks with bow.</li>
 *   <li>If a player stays within {@link #MELEE_TRIGGER_DIST} for
 *       {@link #COMMIT_TICKS} consecutive ticks, the skeleton swaps to
 *       a sword and enters MELEE mode.</li>
 *   <li>If in MELEE mode and target is beyond {@link #BOW_RESUME_DIST},
 *       the skeleton swaps back to its bow.</li>
 * </ul>
 *
 * <p><b>Hysteresis</b> (different thresholds for switching to vs from
 * melee) prevents oscillation when the player hovers near the threshold.
 * The "commit ticks" delay prevents instant flip-flop when a player
 * sprints past.
 *
 * <p>If the skeleton doesn't have a bow stored (we cache it on first
 * swap), we keep its current bow stack and remember it to restore later.
 * The sword we give it is just an iron sword — vanilla skeletons don't
 * normally hold any, so we generate one.
 *
 * <p>Wither skeletons in vanilla already hold stone swords and do melee;
 * this handler still applies to them (the dist tracking is harmless),
 * but the swap is a no-op since they already have a sword.
 *
 * <p>Active only in CRUEL mode.
 */
public final class SkeletonMeleeHandler {

    /** Inside this distance, skeleton wants to melee. */
    private static final double MELEE_TRIGGER_DIST = 4.0;

    /** Outside this distance, skeleton wants to bow. (Hysteresis gap.) */
    private static final double BOW_RESUME_DIST = 6.0;

    /** Ticks the player must stay close before melee swap fires. */
    private static final int COMMIT_TICKS = 30; // 1.5s

    /** Per-skeleton state: how many ticks player has been in melee range. */
    private static final WeakHashMap<UUID, Integer> PROXIMITY_TICKS = new WeakHashMap<>();

    /** Per-skeleton state: stashed bow, restored when switching back to ranged. */
    private static final WeakHashMap<UUID, ItemStack> STASHED_BOW = new WeakHashMap<>();

    /** Per-skeleton state: are we currently in melee mode? */
    private static final WeakHashMap<UUID, Boolean> IN_MELEE = new WeakHashMap<>();

    private SkeletonMeleeHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof AbstractSkeletonEntity skeleton && skeleton.isAlive()) {
                        tickSkeleton(skeleton);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] SkeletonMeleeHandler registered.");
    }

    private static void tickSkeleton(AbstractSkeletonEntity skeleton) {
        LivingEntity target = skeleton.getTarget();
        if (!(target instanceof PlayerEntity player)) {
            // No target — reset proximity tracking, keep mode as-is.
            PROXIMITY_TICKS.remove(skeleton.getUuid());
            return;
        }

        double distance = skeleton.distanceTo(player);
        UUID id = skeleton.getUuid();
        boolean inMelee = IN_MELEE.getOrDefault(id, false);

        if (!inMelee) {
            // Currently ranged. Track how long player has been close.
            if (distance <= MELEE_TRIGGER_DIST) {
                int ticks = PROXIMITY_TICKS.getOrDefault(id, 0) + 1;
                PROXIMITY_TICKS.put(id, ticks);
                if (ticks >= COMMIT_TICKS) {
                    swapToMelee(skeleton);
                    IN_MELEE.put(id, true);
                    PROXIMITY_TICKS.remove(id);
                }
            } else {
                PROXIMITY_TICKS.remove(id);
            }
        } else {
            // Currently melee. Switch back to bow only if player gets far.
            if (distance >= BOW_RESUME_DIST) {
                swapToBow(skeleton);
                IN_MELEE.put(id, false);
            }
        }
    }

    private static void swapToMelee(AbstractSkeletonEntity skeleton) {
        ItemStack mainHand = skeleton.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isOf(Items.BOW)) {
            STASHED_BOW.put(skeleton.getUuid(), mainHand.copy());
        }
        skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void swapToBow(AbstractSkeletonEntity skeleton) {
        ItemStack stashed = STASHED_BOW.remove(skeleton.getUuid());
        if (stashed != null) {
            skeleton.equipStack(EquipmentSlot.MAINHAND, stashed);
        } else {
            // Skeleton didn't originally have a bow (maybe wither skeleton).
            // Restore a default bow.
            skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
    }
}
