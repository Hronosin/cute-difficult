package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the Totem of Undying's "save from death" behavior.
 *
 * <p>{@code LivingEntity.tryUseTotem(DamageSource)} is vanilla's code that
 * runs the moment an entity would die. It checks both hands for a totem,
 * and if found: consumes it, sets HP to 1, applies Regeneration II /
 * Absorption II / Fire Resistance I, and returns true. The caller then
 * cancels the death.
 *
 * <p>By injecting at HEAD and force-returning {@code false}, we tell every
 * caller "no totem was used, proceed with death". The totem in the hand
 * stays untouched (so the player can still consume it via
 * {@link com.cutedifficult.event.TotemEffectHandler}).
 *
 * <p>This affects ALL living entities, not just players. Mobs that
 * spawn with totems (vindicators in raids, etc.) also lose the save.
 * Considered acceptable — the lore "totems no longer save" applies to
 * the world, not just the player.
 *
 * <p>Active only in CRUEL mode; in Path of Peace, vanilla save behavior
 * is restored.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "tryUseTotem", at = @At("HEAD"), cancellable = true)
    private void cuteDifficult$disableTotemSave(
        DamageSource source,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        cir.setReturnValue(false);
    }
}
