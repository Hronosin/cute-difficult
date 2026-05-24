package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Calm wandering — the kitsune picks a random spot within {@link #RANGE}
 * blocks and walks there at {@link #SPEED}. Repeats with {@link #CHANCE_DENOMINATOR}-th
 * probability per tick when idle.
 *
 * <p>This replaces vanilla's {@code WanderAroundFarGoal} which had
 * fox-specific quirks (avoiding the player's bed area, escaping when
 * "lifted by jumping into water") that we don't want for kitsune.
 *
 * <p>The goal yields when the navigation reports idle so other goals
 * (look-at-player, attack, sit) can take over without contest.
 */
public class KitsuneWanderGoal extends Goal {

    private static final double RANGE = 10.0;
    private static final double SPEED = 0.6;
    private static final int CHANCE_DENOMINATOR = 120; // ~1 attempt per 6s

    private final KitsuneEntity kitsune;
    private double targetX, targetY, targetZ;

    public KitsuneWanderGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (this.kitsune.isSitting()) return false;
        if (this.kitsune.hasNoGravity()) return false; // flying handled elsewhere
        if (this.kitsune.getRandom().nextInt(CHANCE_DENOMINATOR) != 0) return false;

        Vec3d target = NoPenaltyTargeting.find(this.kitsune, (int) RANGE, 4);
        if (target == null) return false;
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return !this.kitsune.getNavigation().isIdle();
    }

    @Override
    public void start() {
        this.kitsune.getNavigation().startMovingTo(this.targetX, this.targetY, this.targetZ, SPEED);
    }
}
