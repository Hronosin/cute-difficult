package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import com.cutedifficult.util.SmartMobsTuning;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes endermen ignore water and rain.
 *
 * <p>Vanilla {@code hurtByWater()} returns true for endermen, which makes
 * vanilla apply continuous damage while they're wet (water contact or
 * rain). By overriding to false, endermen walk through puddles and
 * stand in rain without harm.
 *
 * <p>This is cleaner than intercepting individual damage instances —
 * one method returning false short-circuits the entire "should I damage
 * this entity for being wet" check.
 *
 * <p>Side effect: endermen no longer flee water or teleport away when it
 * starts raining. They commit to attacking through any weather.
 */
@Mixin(EndermanEntity.class)
public abstract class EndermanEntityMixin {

    @Inject(method = "hurtByWater", at = @At("HEAD"), cancellable = true)
    private void cuteDifficult$dryEnderman(CallbackInfoReturnable<Boolean> cir) {
        if (!SmartMobsTuning.ENDERMEN_IGNORE_WATER) return;
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        cir.setReturnValue(false);
    }
}
