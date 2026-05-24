package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stub mixin into {@link PlayerEntity}. Logs once at construction so we can
 * verify the mixin pipeline is wired up correctly on first launch. Real
 * mechanics will graduate to dedicated mixins as we add them — for example:
 *
 * <ul>
 *   <li>Totem usage: mixin {@code LivingEntity#tryUseTotem} to apply a
 *       random spirit-flavored buff in place of saving from death.</li>
 *   <li>Crafting table interaction: mixin {@code CraftingScreenHandler} to
 *       roll the sabotage chance on close.</li>
 *   <li>Furnace overheat: mixin {@code AbstractFurnaceBlockEntity#tick}
 *       to accumulate heat and explode at threshold.</li>
 * </ul>
 *
 * <p>Until those are implemented, this file exists primarily to keep the
 * mixin config non-empty (so Loom doesn't warn during build) and to confirm
 * the class transformer is loading.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cuteDifficult$onConstruct(CallbackInfo ci) {
        // One-shot log per JVM. Cheap, helps debug "did the mod actually load".
        if (!loggedOnce) {
            CuteDifficult.LOGGER.debug("[CuteDifficult] PlayerEntityMixin applied.");
            loggedOnce = true;
        }
    }

    private static boolean loggedOnce = false;
}
