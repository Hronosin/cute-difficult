package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

/**
 * Turns the kitsune's head toward the nearest player when one is within
 * {@link #LOOK_RADIUS}.
 *
 * <p>Doesn't move the body — only head/eyes. Compatible with WanderGoal
 * (look-at uses LOOK control; wander uses MOVE).
 */
public class KitsuneLookAtPlayerGoal extends Goal {

    private static final double LOOK_RADIUS = 16.0;
    private static final int DURATION = 40; // 2s of staring

    private final KitsuneEntity kitsune;
    private PlayerEntity target;
    private int ticksLeft;

    public KitsuneLookAtPlayerGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (this.kitsune.getRandom().nextInt(8) != 0) return false;
        this.target = this.kitsune.getWorld().getClosestPlayer(this.kitsune, LOOK_RADIUS);
        return this.target != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.target != null && this.target.isAlive()
            && this.kitsune.squaredDistanceTo(this.target) < LOOK_RADIUS * LOOK_RADIUS
            && this.ticksLeft > 0;
    }

    @Override
    public void start() {
        this.ticksLeft = DURATION;
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        this.ticksLeft--;
        this.kitsune.getLookControl().lookAt(
            this.target.getX(),
            this.target.getEyeY(),
            this.target.getZ()
        );
    }
}
