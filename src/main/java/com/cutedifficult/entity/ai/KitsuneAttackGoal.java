package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.event.FoxAbilityHandler;
import com.cutedifficult.spirit.FoxData;
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
        FoxData data = readData();
        if (data == null) return false;
        if (data.tails() < MIN_TAILS) return false;

        // Great Blessing of Inari makes ALL kitsune neutral, regardless
        // of trust/witness state. Check via nearest player; if any player
        // in range has it, none of them get attacked.
        ServerPlayerEntity candidateTarget = findTarget();
        if (candidateTarget != null
            && com.cutedifficult.event.ResonanceBlessingHandler.hasGreatBlessing(candidateTarget)) {
            return false;
        }

        // Trust gate: friendly foxes don't attack. BUT witnessed killings
        // override friendliness.
        if (data.witnessedKills() > 0) return true;
        if (data.trustLevel() >= FRIENDLY_TRUST_THRESHOLD) return false;
        return true;
    }

    @Override
    public boolean canStart() {
        if (!isEligible()) return false;
        ServerPlayerEntity nearest = findTarget();
        if (nearest == null) return false;
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
        FoxData data = readData();
        this.castCooldown = data == null ? 200 : FoxStats.abilityCooldownTicks(data.tails());
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

        FoxData data = readData();
        if (data == null) return;
        if (!(this.kitsune.getWorld() instanceof ServerWorld serverWorld)) return;

        FoxAbilityHandler.castElementalAbility(serverWorld, this.kitsune, data, this.target);

        this.ticksUntilCast = this.castCooldown + this.random.nextInt(20);
    }

    private FoxData readData() {
        return FoxData.getOrCreate(this.kitsune, this.random);
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
