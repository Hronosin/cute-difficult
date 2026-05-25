package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Random;

/**
 * Flee from player — used only by 1-2 tail kitsune (young, fearful).
 *
 * <p>Activates when a player is within {@link #FLEE_TRIGGER_RADIUS}.
 * Picks a wander point AWAY from the player and runs there at {@link #FLEE_SPEED}.
 *
 * <p>Higher tail kitsune don't get this goal at all (they don't fear
 * mortals), so the behavior matrix is:
 * <ul>
 *   <li>1-2 tails: this goal active → flees</li>
 *   <li>3+ tails: this goal not added → stands, attacks</li>
 * </ul>
 */
public class KitsuneFleeGoal extends Goal {

    private static final int MAX_TAILS_THAT_FLEE = 2;
    private static final double FLEE_TRIGGER_RADIUS = 6.0;
    private static final double FLEE_SPEED = 1.2;
    private static final int FLEE_DISTANCE = 10;

    private final KitsuneEntity kitsune;
    private final Random random = new Random();

    private PlayerEntity threat;
    private double targetX, targetY, targetZ;

    public KitsuneFleeGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        KitsuneData data = FoxStorage.getOrCreate(this.kitsune, this.random);
        if (data.tails > MAX_TAILS_THAT_FLEE) return false;

        PlayerEntity nearestPlayer = this.kitsune.getWorld().getClosestPlayer(
            this.kitsune, FLEE_TRIGGER_RADIUS
        );
        if (nearestPlayer == null) return false;
        if (nearestPlayer.isCreative() || nearestPlayer.isSpectator()) return false;

        // Pick a fleeing target — a point in the opposite direction.
        Vec3d awayFromPlayer = this.kitsune.getPos().subtract(nearestPlayer.getPos()).normalize();
        Vec3d candidate = NoPenaltyTargeting.findFrom(
            this.kitsune, FLEE_DISTANCE, 7, awayFromPlayer
        );
        if (candidate == null) return false;

        this.threat = nearestPlayer;
        this.targetX = candidate.x;
        this.targetY = candidate.y;
        this.targetZ = candidate.z;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return !this.kitsune.getNavigation().isIdle();
    }

    @Override
    public void start() {
        this.kitsune.getNavigation().startMovingTo(
            this.targetX, this.targetY, this.targetZ, FLEE_SPEED
        );
    }

    @Override
    public void stop() {
        this.threat = null;
    }
}
