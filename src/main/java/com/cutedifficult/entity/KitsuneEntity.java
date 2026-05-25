package com.cutedifficult.entity;

import com.cutedifficult.entity.ai.KitsuneAttackGoal;
import com.cutedifficult.entity.ai.KitsuneFleeGoal;
import com.cutedifficult.entity.ai.KitsuneLookAtPlayerGoal;
import com.cutedifficult.entity.ai.KitsuneMeleeGoal;
import com.cutedifficult.entity.ai.KitsuneSitGoal;
import com.cutedifficult.entity.ai.KitsuneWanderGoal;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.Random;

/**
 * KitsuneEntity. v0.9.4: KitsuneData is now a plain class, NBT and cache
 * logic lives in {@link FoxStorage}. Persistence pattern is unchanged
 * — cache holds the live working copy, NBT is written on save by this
 * class via {@link FoxStorage#toNbt}, read on load via
 * {@link FoxStorage#fromNbt} and pushed back into cache.
 */
public class KitsuneEntity extends FoxEntity {

    private static final Random RANDOM = new Random();

    public KitsuneEntity(EntityType<? extends KitsuneEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new KitsuneSitGoal(this));
        this.goalSelector.add(2, new KitsuneFleeGoal(this));
        this.goalSelector.add(3, new KitsuneMeleeGoal(this));
        this.goalSelector.add(4, new KitsuneAttackGoal(this));
        this.goalSelector.add(5, new KitsuneWanderGoal(this));
        this.goalSelector.add(6, new KitsuneLookAtPlayerGoal(this));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        KitsuneData data = FoxStorage.peekCache(this);
        if (data == null) data = FoxStorage.getOrCreate(this, RANDOM);
        nbt.put(FoxStorage.NBT_KEY, FoxStorage.toNbt(data));
        com.cutedifficult.CuteDifficult.LOGGER.debug(
            "[CuteDifficult] Wrote KitsuneData for {} (element={}, tails={}, trust={})",
            this.getUuid(), data.element, data.tails, data.trustLevel);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        try {
            super.readCustomDataFromNbt(nbt);
        } catch (NullPointerException npe) {
            com.cutedifficult.CuteDifficult.LOGGER.debug(
                "[CuteDifficult] Suppressed NPE from vanilla addTypeSpecificGoals (expected)."
            );
        }
        if (nbt.contains(FoxStorage.NBT_KEY)) {
            KitsuneData data = FoxStorage.fromNbt(nbt.getCompound(FoxStorage.NBT_KEY));
            FoxStorage.injectIntoCache(this, data);
            com.cutedifficult.CuteDifficult.LOGGER.debug(
                "[CuteDifficult] Loaded KitsuneData for {} (element={}, tails={}, trust={})",
                this.getUuid(), data.element, data.tails, data.trustLevel);
        } else {
            com.cutedifficult.CuteDifficult.LOGGER.debug(
                "[CuteDifficult] readCustomDataFromNbt: no spirit data found for {} — will regenerate.",
                this.getUuid());
        }
    }
}
