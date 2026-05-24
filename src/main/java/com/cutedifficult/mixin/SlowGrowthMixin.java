package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slows crop and stem growth to 40% of vanilla rate (i.e., 2.5x slower).
 *
 * <p><b>How vanilla growth works:</b> the world ticks a fixed number of
 * random blocks per chunk per game tick (gamerule {@code randomTickSpeed},
 * default 3). When a crop block receives a random tick, its
 * {@code randomTick()} runs vanilla logic — check light, hydration, roll
 * a growth chance, and possibly advance the growth stage.
 *
 * <p><b>Our approach:</b> inject at HEAD of {@code randomTick} with
 * {@code cancellable=true}. Roll once; if our roll says "skip", we cancel
 * the callback before vanilla logic runs. With a 60% skip rate, only 40%
 * of random ticks reach vanilla's growth check — net effect is a 2.5x
 * slowdown in growth speed.
 *
 * <p><b>Why this is better than tweaking {@code randomTickSpeed}:</b> the
 * gamerule affects EVERYTHING — fire spread, leaf decay, ice melting,
 * grass spreading, mob spawning checks. Touching it would have a dozen
 * unintended side effects. Per-block injection only slows the things we
 * actually want slowed.
 *
 * <p><b>Targets:</b>
 * <ul>
 *   <li>{@link CropBlock} — wheat, carrot, potato, beetroot.</li>
 *   <li>{@link StemBlock} — melon and pumpkin stems (before fruit attaches).</li>
 * </ul>
 * Both classes have an identical {@code randomTick} signature, so one
 * mixin file targets both via the array form of {@code @Mixin}.
 *
 * <p><b>Not covered (intentional, for now):</b> sugar cane, cocoa, nether
 * wart, sweet berries, kelp, bamboo. These have different block classes
 * and somewhat different growth mechanics. If the player tries to dodge
 * the slowdown by switching to industrial sugarcane farms — that's their
 * problem; we can extend coverage in a v0.2 patch.
 *
 * <p><b>Active only in CRUEL mode.</b>
 */
@Mixin({CropBlock.class, StemBlock.class})
public abstract class SlowGrowthMixin {

    /** Probability that a random tick is dropped before vanilla logic. */
    private static final float SKIP_CHANCE = 0.6f;

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void cuteDifficult$slowGrowth(
        BlockState state,
        ServerWorld world,
        BlockPos pos,
        Random random,
        CallbackInfo ci
    ) {
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        if (random.nextFloat() < SKIP_CHANCE) {
            ci.cancel();
        }
    }
}
