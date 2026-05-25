package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;
import java.util.Random;

/**
 * For 9-tail Kyuubi: when a player approaches, sit in regal pose.
 *
 * <p>Implementation: the goal takes MOVE control to prevent wander/attack
 * goals from making the kitsune walk while sitting. The sit flag is set
 * each tick the player is nearby; cleared when they leave.
 */
public class KitsuneSitGoal extends Goal {

    private static final double SIT_RADIUS = 8.0;

    private final KitsuneEntity kitsune;
    private final Random random = new Random();

    public KitsuneSitGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        KitsuneData data = FoxStorage.getOrCreate(this.kitsune, this.random);
        if (data.tails < KitsuneData.MAX_TAILS) return false;
        PlayerEntity player = this.kitsune.getWorld().getClosestPlayer(this.kitsune, SIT_RADIUS);
        return player != null && !player.isSpectator();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        this.kitsune.setSitting(true);
        this.kitsune.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.kitsune.setSitting(false);
    }
}
