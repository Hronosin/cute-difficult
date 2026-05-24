package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import com.cutedifficult.util.FurnaceOverheatTracker;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the static {@code tick} method of {@link AbstractFurnaceBlockEntity}
 * to drive the overheat mechanic. Catches all three variants — regular
 * furnace, blast furnace, smoker — because they all inherit this tick.
 *
 * <p>The vanilla tick fires once per game tick per loaded furnace. We
 * inject at TAIL (after vanilla burn/cook logic has fully run for this
 * tick), so we see the resulting BlockState (LIT updated, cookTime
 * advanced) when we read it. This is the most predictable injection
 * point.
 *
 * <p>We're a no-op on the client side and in Path of Peace mode — the
 * tracker call is cheap but checks reduce noise.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceOverheatMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private static void cuteDifficult$onFurnaceTick(
        World world,
        BlockPos pos,
        BlockState state,
        AbstractFurnaceBlockEntity blockEntity,
        CallbackInfo ci
    ) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

        FurnaceOverheatTracker.tickFurnace(serverWorld, pos, state);
    }
}
