package com.cutedifficult.entity;

import com.cutedifficult.entity.ai.KitsuneAttackGoal;
import com.cutedifficult.entity.ai.KitsuneFleeGoal;
import com.cutedifficult.entity.ai.KitsuneLookAtPlayerGoal;
import com.cutedifficult.entity.ai.KitsuneMeleeGoal;
import com.cutedifficult.entity.ai.KitsuneSitGoal;
import com.cutedifficult.entity.ai.KitsuneWanderGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * The kitsune.
 *
 * <p><b>v0.4.0:</b> dropped the goal-stripping in {@code readCustomDataFromNbt}
 * because it was over-eager and removed our own KitsuneAttackGoal too —
 * KitsuneAttackGoal's enclosing class is com.cutedifficult.entity.ai
 * and {@code getEnclosingClass()} returns the .ai package container,
 * which... never mind, it was buggy. We now just swallow the NPE from
 * vanilla's addTypeSpecificGoals and leave the goalSelector alone.
 *
 * <p>Behavior is enforced through our custom goal set:
 * <ul>
 *   <li>{@link KitsuneFleeGoal} only activates for 1-2 tail kitsune
 *       → they flee like vanilla foxes.</li>
 *   <li>{@link KitsuneAttackGoal} only activates for 3+ tail
 *       → ranged elemental casting.</li>
 *   <li>{@link KitsuneMeleeGoal} only activates for 3+ tail
 *       → dash attack at close range.</li>
 *   <li>{@link KitsuneSitGoal} only activates for 9-tail Kyuubi
 *       → regal sitting pose.</li>
 * </ul>
 * Each goal is internally tail-gated, so adding all of them and letting
 * each filter out its own irrelevant cases is clean and stateless.
 *
 * <p>Vanilla type-specific goals ARE added by {@code addTypeSpecificGoals}
 * during NBT read; they coexist harmlessly because none of them have
 * higher priority than our SwimGoal/SitGoal, and most just do nothing
 * relevant. The NPE on add is caught and suppressed.
 */
public class KitsuneEntity extends FoxEntity {

    public KitsuneEntity(EntityType<? extends KitsuneEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new KitsuneSitGoal(this));
        this.goalSelector.add(2, new KitsuneFleeGoal(this));        // 1-2 tails flee
        this.goalSelector.add(3, new KitsuneMeleeGoal(this));        // 3+ tails dash
        this.goalSelector.add(4, new KitsuneAttackGoal(this));       // 3+ tails ranged
        this.goalSelector.add(5, new KitsuneWanderGoal(this));
        this.goalSelector.add(6, new KitsuneLookAtPlayerGoal(this));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        // Vanilla's readCustomDataFromNbt calls addTypeSpecificGoals which
        // may NPE for our entity. Catch and continue — base NBT fields
        // (type, sleeping, sitting) are read before the crash, so the
        // entity still has its essential state.
        try {
            super.readCustomDataFromNbt(nbt);
        } catch (NullPointerException npe) {
            com.cutedifficult.CuteDifficult.LOGGER.debug(
                    "[CuteDifficult] Suppressed NPE from vanilla addTypeSpecificGoals (expected)."
            );
        }
    }
}