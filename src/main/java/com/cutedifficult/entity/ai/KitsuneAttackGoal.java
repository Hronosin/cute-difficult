package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.event.FoxAbilityHandler;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.FoxStats;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;
import java.util.Random;

/**
 * Proactive ranged ability casting.
 *
 * <p><b>v0.4.2 fix:</b> trust gate was only in {@code canStart()}.
 * When a player feeds a fox WHILE the goal is running, {@code shouldContinue()}
 * had no trust check — the goal kept casting through the friendship
 * threshold being crossed. Now {@code shouldContinue()} also gates on
 * trust, so a running attack goal is immediately terminated by a
 * successful offering.
 */
public class KitsuneAttackGoal extends Goal {

    private static final int MIN_TAILS = 3;
    private static final int FRIENDLY_TRUST_THRESHOLD = 30;
    private static final double ATTACK_RADIUS = 12.0;
    private static final int INITIAL_DELAY = 40;

    private final KitsuneEntity kitsune;
    private final Random random = new Random();

    private ServerPlayerEntity target;
    private int ticksUntilCast;
    private int castCooldown;

    public KitsuneAttackGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    /** Combined eligibility check used by both canStart and shouldContinue. */
    private boolean isEligible() {
        KitsuneData data = readData();
        if (data == null || data.tails < MIN_TAILS) return false;
        if (this.target == null) return false;
        return com.cutedifficult.spirit.FoxHostility.canAttackWithLineOfSight(this.kitsune, this.target);
    }

    @Override
    public boolean canStart() {
        KitsuneData data = readData();
        if (data == null || data.tails < MIN_TAILS) return false;
        ServerPlayerEntity nearest = findTarget();
        if (nearest == null) return false;
        // Apply hostility check using found target.
        if (!com.cutedifficult.spirit.FoxHostility.canAttackWithLineOfSight(this.kitsune, nearest)) return false;
        this.target = nearest;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (!isEligible()) return false; // <-- KEY: trust check on every tick too
        if (this.target == null) return false;
        if (!this.target.isAlive() || this.target.isCreative() || this.target.isSpectator()) return false;
        return this.kitsune.squaredDistanceTo(this.target) <= ATTACK_RADIUS * ATTACK_RADIUS;
    }

    @Override
    public void start() {
        this.ticksUntilCast = INITIAL_DELAY;
        KitsuneData data = readData();
        this.castCooldown = data == null ? 200 : FoxStats.abilityCooldownTicks(data.tails);
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        if (--this.ticksUntilCast > 0) return;

        this.kitsune.getLookControl().lookAt(this.target, 30f, 30f);

        KitsuneData data = readData();
        if (data == null) return;
        if (!(this.kitsune.getWorld() instanceof ServerWorld serverWorld)) return;

        FoxAbilityHandler.castElementalAbility(serverWorld, this.kitsune, data, this.target);

        this.ticksUntilCast = this.castCooldown + this.random.nextInt(20);
    }

    private KitsuneData readData() {
        return FoxStorage.getOrCreate(this.kitsune, this.random);
    }

    private ServerPlayerEntity findTarget() {
        var nearest = this.kitsune.getWorld().getClosestPlayer(
            this.kitsune.getX(), this.kitsune.getY(), this.kitsune.getZ(),
            ATTACK_RADIUS,
            p -> p instanceof ServerPlayerEntity sp
                && !sp.isCreative() && !sp.isSpectator() && sp.isAlive()
        );
        return nearest instanceof ServerPlayerEntity sp ? sp : null;
    }
}
