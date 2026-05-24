package com.cutedifficult.entity.ai;

import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxStats;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Random;

/**
 * Close-range dash attack for kitsune. When a player is within
 * {@link #DASH_TRIGGER_RADIUS} (and outside the very-close
 * {@link #DASH_MIN_RADIUS} so we don't dash into them when stuck),
 * the kitsune launches itself in a small ballistic arc at the player
 * and deals impact damage on landing.
 *
 * <p><b>State machine:</b>
 * <ol>
 *   <li>Idle — waiting for player to be in dash range.</li>
 *   <li>Wind-up — looking at target, small particle telegraph for
 *       {@link #WINDUP_TICKS} ticks. Gives the player a chance to react.</li>
 *   <li>Airborne — velocity assigned, kitsune flies at target. We track
 *       this state for {@link #AIRBORNE_TIMEOUT} ticks max.</li>
 *   <li>Impact — when close enough during airborne, deal damage and apply
 *       a brief slowness to the target. Reset.</li>
 *   <li>Cooldown — block dashes for {@link #DASH_COOLDOWN_TICKS}.</li>
 * </ol>
 *
 * <p><b>Damage scaling:</b> base 4 damage × (tails / 9). 9-tail dashes
 * for 4 damage; 3-tail (the minimum for melee) for ~1.3.
 *
 * <p>Higher goal priority than {@link KitsuneAttackGoal} so close-range
 * is preferred over ranged. Takes MOVE + LOOK controls during wind-up
 * and dash to ensure other goals don't interrupt mid-pounce.
 */
public class KitsuneMeleeGoal extends Goal {

    private static final int MIN_TAILS = 3;
    private static final int FRIENDLY_TRUST_THRESHOLD = 30;
    /** Outer radius — player must be within this for dash to consider. */
    private static final double DASH_TRIGGER_RADIUS = 4.0;
    /** Inner buffer — don't dash if player is literally inside the kitsune. */
    private static final double DASH_MIN_RADIUS = 0.6;
    /** Wind-up duration before launching. Telegraphs the attack. */
    private static final int WINDUP_TICKS = 10; // 0.5s
    /** Max airborne duration before resetting if no impact (safety net). */
    private static final int AIRBORNE_TIMEOUT = 30; // 1.5s
    /** Distance at which airborne kitsune deals impact damage. */
    private static final double IMPACT_DISTANCE = 1.5;
    /** Cooldown between dashes. */
    private static final int DASH_COOLDOWN_TICKS = 100; // 5s

    /** Horizontal speed of the dash. */
    private static final double DASH_HORIZONTAL = 1.2;
    /** Upward velocity component — small arc, not big leap. */
    private static final double DASH_UPWARD = 0.4;

    private final KitsuneEntity kitsune;
    private final Random random = new Random();

    private ServerPlayerEntity target;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private long lastDashEndedAt;

    private enum Phase { IDLE, WINDUP, AIRBORNE }

    public KitsuneMeleeGoal(KitsuneEntity kitsune) {
        this.kitsune = kitsune;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        FoxData data = FoxData.getOrCreate(this.kitsune, this.random);
        if (data.tails() < MIN_TAILS) return false;

        long now = this.kitsune.getWorld().getTime();
        if (now - this.lastDashEndedAt < DASH_COOLDOWN_TICKS) return false;

        var nearest = this.kitsune.getWorld().getClosestPlayer(
            this.kitsune.getX(), this.kitsune.getY(), this.kitsune.getZ(),
            DASH_TRIGGER_RADIUS,
            p -> p instanceof ServerPlayerEntity sp
                && !sp.isCreative() && !sp.isSpectator() && sp.isAlive()
        );
        if (!(nearest instanceof ServerPlayerEntity sp)) return false;

        // Great Blessing peace — never dash a blessed player.
        if (com.cutedifficult.event.ResonanceBlessingHandler.hasGreatBlessing(sp)) return false;

        // Witnessed killings override trust. Otherwise trust gates attack.
        if (data.witnessedKills() == 0 && data.trustLevel() >= FRIENDLY_TRUST_THRESHOLD) return false;

        double distance = this.kitsune.distanceTo(sp);
        if (distance < DASH_MIN_RADIUS) return false;

        this.target = sp;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (this.target == null) return false;
        if (!this.target.isAlive()) return false;
        // Great Blessing peace.
        if (com.cutedifficult.event.ResonanceBlessingHandler.hasGreatBlessing(this.target)) return false;
        // Trust gate with witness override.
        FoxData data = FoxData.getOrCreate(this.kitsune, this.random);
        if (data.witnessedKills() == 0 && data.trustLevel() >= FRIENDLY_TRUST_THRESHOLD) return false;
        return this.phase != Phase.IDLE;
    }

    @Override
    public void start() {
        this.phase = Phase.WINDUP;
        this.phaseTicks = 0;
        // Stop any current navigation — we'll take over.
        this.kitsune.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.target = null;
        this.phase = Phase.IDLE;
        this.lastDashEndedAt = this.kitsune.getWorld().getTime();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            this.phase = Phase.IDLE;
            return;
        }
        this.phaseTicks++;

        switch (this.phase) {
            case WINDUP -> tickWindup();
            case AIRBORNE -> tickAirborne();
            default -> {}
        }
    }

    private void tickWindup() {
        // Lock look on target.
        this.kitsune.getLookControl().lookAt(this.target, 60f, 60f);

        // Telegraph particles at the kitsune's feet — preparing to pounce.
        if (this.kitsune.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.CRIT,
                this.kitsune.getX(), this.kitsune.getY() + 0.2, this.kitsune.getZ(),
                3, 0.2, 0.1, 0.2, 0.05
            );
        }

        if (this.phaseTicks >= WINDUP_TICKS) {
            launchDash();
            this.phase = Phase.AIRBORNE;
            this.phaseTicks = 0;
        }
    }

    private void launchDash() {
        Vec3d toTarget = this.target.getPos().subtract(this.kitsune.getPos());
        Vec3d horizontal = new Vec3d(toTarget.x, 0, toTarget.z);
        double horizontalLen = horizontal.length();
        if (horizontalLen < 0.01) {
            // Player directly above/below — small upward push.
            horizontal = new Vec3d(0.1, 0, 0);
            horizontalLen = horizontal.length();
        }
        Vec3d direction = horizontal.multiply(1.0 / horizontalLen);

        Vec3d velocity = new Vec3d(
            direction.x * DASH_HORIZONTAL,
            DASH_UPWARD,
            direction.z * DASH_HORIZONTAL
        );
        this.kitsune.setVelocity(velocity);
        this.kitsune.velocityModified = true;

        // Audio — pounce sound.
        this.kitsune.getWorld().playSound(
            null,
            this.kitsune.getX(), this.kitsune.getY(), this.kitsune.getZ(),
            SoundEvents.ENTITY_FOX_BITE,
            SoundCategory.HOSTILE,
            1.3f, 1.1f
        );
    }

    private void tickAirborne() {
        // Check for impact every airborne tick.
        double distance = this.kitsune.distanceTo(this.target);
        if (distance < IMPACT_DISTANCE) {
            doImpact();
            this.phase = Phase.IDLE; // canStart will block via cooldown after stop()
            return;
        }
        if (this.phaseTicks >= AIRBORNE_TIMEOUT) {
            // Missed — just end the dash.
            this.phase = Phase.IDLE;
        }
    }

    private void doImpact() {
        FoxData data = FoxData.getOrCreate(this.kitsune, this.random);
        double power = data.tails() / 9.0;

        // Damage — generic, scaled by tails.
        this.target.damage(
            this.kitsune.getWorld().getDamageSources().mobAttack(this.kitsune),
            (float) (4.0 * power)
        );

        // Brief slowness on impact — represents the player being knocked off-balance.
        this.target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1));

        // Impact particles + sound.
        if (this.kitsune.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.SWEEP_ATTACK,
                this.target.getX(), this.target.getY() + 1.0, this.target.getZ(),
                1, 0.1, 0.1, 0.1, 0.0
            );
        }
        this.kitsune.getWorld().playSound(
            null,
            this.target.getX(), this.target.getY(), this.target.getZ(),
            SoundEvents.ENTITY_FOX_BITE,
            SoundCategory.HOSTILE,
            1.5f, 0.8f
        );
    }
}
