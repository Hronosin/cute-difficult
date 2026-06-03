package com.cutedifficult.entity;

import com.cutedifficult.CuteDifficult;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The Hollow Lord — the mod's final boss, rebuilt from scratch on a flying-mob
 * base (instead of extending the Ender Dragon, which fought us on boss bars,
 * serialization, and phase control). This gives full control: our own AI,
 * our own boss bar, clean NBT, predictable behavior across reloads.
 *
 * <p>It renders with the vanilla dragon model (see HollowLordRenderer) at a
 * medium scale. Combat (attacks, phases) is layered in
 * {@code HollowLordCombatHandler}, which reads/feeds this entity.
 */
public class HollowLordEntity extends FlyingEntity {

    private int spawnInvuln = 0;
    private boolean dying = false;
    private int currentPhase = 1;
    private int phaseInvuln = 0; // brief invuln on phase transitions

    private final ServerBossBar bossBar = new ServerBossBar(
        Text.literal("The Hollow Lord").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
        BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

    public HollowLordEntity(EntityType<? extends FlyingEntity> entityType, World world) {
        super(entityType, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.bossBar.setDarkenSky(true);
        this.bossBar.setThickenFog(true);
        this.experiencePoints = 200;
    }

    public static DefaultAttributeContainer.Builder createHollowLordAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.6)
            .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.2)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
            .add(EntityAttributes.GENERIC_ARMOR, 8.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        // A simple circling/approach goal so the Lord orbits and closes on the
        // nearest player. Attacks themselves are driven by the combat handler.
        this.goalSelector.add(1, new HoverNearTargetGoal(this));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanEnterOpenDoors(false);
        nav.setCanSwim(false);
        return nav;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && spawnInvuln > 0) {
            spawnInvuln--;
        }
    }

    // No gravity while flying.
    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.getWorld() instanceof ServerWorld sw) {
            this.bossBar.setPercent(Math.max(0f, this.getHealth() / this.getMaxHealth()));

            if (phaseInvuln > 0) phaseInvuln--;

            // Phase detection by HP fraction: 1 (100-75%), 2 (75-50%), 3 (50-25%), 4 (<25%).
            float frac = this.getHealth() / this.getMaxHealth();
            int newPhase = frac > 0.75f ? 1 : frac > 0.50f ? 2 : frac > 0.25f ? 3 : 4;
            if (newPhase > currentPhase && !dying) {
                enterPhase(sw, newPhase);
            }

            if (this.age % 20 == 0) {
                java.util.Set<ServerPlayerEntity> nearby = new java.util.HashSet<>();
                for (ServerPlayerEntity p : sw.getPlayers()) {
                    if (p.squaredDistanceTo(this) < 128 * 128) nearby.add(p);
                }
                for (ServerPlayerEntity p : new java.util.ArrayList<>(bossBar.getPlayers())) {
                    if (!nearby.contains(p)) bossBar.removePlayer(p);
                }
                for (ServerPlayerEntity p : nearby) bossBar.addPlayer(p);
            }
        }
    }

    /** Transition into a new, harder phase: brief invuln, roar, flash, retitle. */
    private void enterPhase(ServerWorld sw, int phase) {
        currentPhase = phase;
        phaseInvuln = 40; // 2s of mercy so the transition reads clearly

        String name = switch (phase) {
            case 2 -> "The Hollow Lord — The Hollowing";
            case 3 -> "The Hollow Lord — The Devouring";
            case 4 -> "The Hollow Lord — The Last Light";
            default -> "The Hollow Lord";
        };
        this.bossBar.setName(net.minecraft.text.Text.literal(name)
            .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        this.bossBar.setColor(phase >= 4 ? BossBar.Color.RED
            : phase == 3 ? BossBar.Color.PURPLE : BossBar.Color.PURPLE);

        // Roar + shockwave flash.
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
            this.getX(), this.getY() + 2, this.getZ(), 120, 3, 3, 3, 0.4);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
            this.getX(), this.getY() + 2, this.getZ(), 5, 2, 2, 2, 0);
        sw.playSound(null, this.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
            net.minecraft.sound.SoundCategory.HOSTILE, 4.0f, 0.5f);

        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(this) < 128 * 128) {
                p.sendMessage(net.minecraft.text.Text.literal(switch (phase) {
                    case 2 -> "The Lord hollows. The air grows thin.";
                    case 3 -> "The Lord hungers. It begins to devour.";
                    case 4 -> "The last light gutters. Nothing is held back now.";
                    default -> "";
                }).formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
            }
        }
    }

    public int getPhase() { return currentPhase; }
    public boolean isPhaseInvuln() { return phaseInvuln > 0; }

    public void grantSpawnInvulnerability(int ticks) { this.spawnInvuln = ticks; }
    public int getSpawnInvuln() { return spawnInvuln; }

    public boolean isDying() { return dying; }
    public void setDying(boolean d) { this.dying = d; }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (spawnInvuln > 0) return false;
        if (phaseInvuln > 0) return false; // brief mercy on phase transitions
        if (dying) return false; // frozen during the death sequence
        // Immune to drowning/fall/void-less nonsense; takes normal combat damage.
        return super.damage(source, amount);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.bossBar.clearPlayers();
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CdSpawnInvuln", spawnInvuln);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.spawnInvuln = nbt.getInt("CdSpawnInvuln");
    }

    /**
     * Rich flight AI: the Lord cycles through maneuvers instead of just hovering.
     * It circles the target, makes diving dash attacks, retreats to reposition,
     * and hovers menacingly — picking a new maneuver every few seconds.
     */
    private static class HoverNearTargetGoal extends Goal {
        private final HollowLordEntity lord;

        private enum Move { CIRCLE, DASH, RETREAT, HOVER }
        private Move move = Move.HOVER;
        private int moveTicks = 0;
        private double circleAngle = 0;

        HoverNearTargetGoal(HollowLordEntity lord) {
            this.lord = lord;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return true; }
        @Override public boolean shouldContinue() { return true; }

        @Override
        public void tick() {
            LivingEntity target = lord.getWorld().getClosestPlayer(lord, 80);
            if (target == null) {
                // Idle drift upward a little so it doesn't sink.
                lord.getMoveControl().moveTo(lord.getX(), lord.getY() + 2, lord.getZ(), 0.3);
                return;
            }

            lord.getLookControl().lookAt(target, 30, 30);

            if (moveTicks > 0) moveTicks--;
            if (moveTicks <= 0) chooseNextMove(target);

            switch (move) {
                case CIRCLE -> doCircle(target);
                case DASH -> doDash(target);
                case RETREAT -> doRetreat(target);
                case HOVER -> doHover(target);
            }
        }

        private void chooseNextMove(LivingEntity target) {
            double dist = lord.distanceTo(target);
            int roll = lord.getRandom().nextInt(100);
            // Closer → more likely to dash; farther → more likely to circle/approach.
            if (dist > 25) {
                move = (roll < 60) ? Move.CIRCLE : Move.DASH;
                moveTicks = 60;
            } else if (dist < 8) {
                move = (roll < 50) ? Move.RETREAT : Move.HOVER;
                moveTicks = 30;
            } else {
                if (roll < 35) move = Move.DASH;
                else if (roll < 70) move = Move.CIRCLE;
                else if (roll < 85) move = Move.HOVER;
                else move = Move.RETREAT;
                moveTicks = 40 + lord.getRandom().nextInt(40);
            }
        }

        private void doCircle(LivingEntity target) {
            circleAngle += 0.08;
            double radius = 14;
            double tx = target.getX() + Math.cos(circleAngle) * radius;
            double tz = target.getZ() + Math.sin(circleAngle) * radius;
            double ty = target.getY() + 6 + Math.sin(circleAngle * 2) * 2;
            lord.getMoveControl().moveTo(tx, ty, tz, 1.0);
        }

        private void doDash(LivingEntity target) {
            // Charge straight at the target's position fast.
            double tx = target.getX();
            double ty = target.getY() + 1;
            double tz = target.getZ();
            lord.getMoveControl().moveTo(tx, ty, tz, 2.2);
            // Contact damage when very close.
            if (lord.distanceTo(target) < 3.5 && target instanceof net.minecraft.entity.player.PlayerEntity) {
                target.damage(lord.getWorld().getDamageSources().mobAttack(lord), 8.0f);
                Vec3d kb = target.getPos().subtract(lord.getPos()).normalize().multiply(1.2);
                target.addVelocity(kb.x, 0.4, kb.z);
                target.velocityModified = true;
            }
        }

        private void doRetreat(LivingEntity target) {
            // Back away and gain altitude to reposition.
            Vec3d away = lord.getPos().subtract(target.getPos()).normalize().multiply(16);
            lord.getMoveControl().moveTo(
                lord.getX() + away.x, target.getY() + 12, lord.getZ() + away.z, 1.4);
        }

        private void doHover(LivingEntity target) {
            // Hold position above and near the target, swaying slightly.
            double tx = target.getX();
            double ty = target.getY() + 9;
            double tz = target.getZ();
            lord.getMoveControl().moveTo(tx, ty, tz, 0.7);
        }
    }
}
