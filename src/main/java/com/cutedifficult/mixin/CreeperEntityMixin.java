package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import com.cutedifficult.util.SmartMobsTuning;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes creeper fuses commit — once a creeper starts hissing, it WILL
 * explode, even if the player backs away.
 *
 * <p><b>v0.2.1 fix:</b> the original mixin targeted {@code tickMovement()},
 * which exists on {@code LivingEntity} but isn't overridden in
 * {@code CreeperEntity} — Mixin couldn't find it. Switched to {@code tick()},
 * which IS explicitly overridden in {@code CreeperEntity} (vanilla uses it
 * to manage the fuse). Same hook point, just one level up the call chain.
 *
 * <p>Vanilla creepers reset their fuse if the target moves too far away
 * mid-ignition. We snapshot the fuse value before {@code tick}, let
 * vanilla run, then check: if the fuse WAS advancing but vanilla rolled
 * it back, we force-restart the ignition. The creeper commits to its
 * decision.
 *
 * <p>The {@code @At("HEAD")} on {@code tick} snapshots before any vanilla
 * logic, {@code @At("TAIL")} reads after — clean before/after diff.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {

    private int cuteDifficult$preTickFuse = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cuteDifficult$snapshotFuse(CallbackInfo ci) {
        if (!SmartMobsTuning.CREEPERS_COMMIT_TO_EXPLOSION) return;
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        CreeperEntity self = (CreeperEntity)(Object)this;
        this.cuteDifficult$preTickFuse = self.getFuseSpeed();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cuteDifficult$commitFuse(CallbackInfo ci) {
        if (!SmartMobsTuning.CREEPERS_COMMIT_TO_EXPLOSION) return;
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

        CreeperEntity self = (CreeperEntity)(Object)this;
        // If the fuse was actively advancing before tick (>0) — keep it on.
        if (this.cuteDifficult$preTickFuse > 0) {
            self.setFuseSpeed(1);
        }
    }
}