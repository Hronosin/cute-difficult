package com.cutedifficult.mixin;

import com.cutedifficult.event.CraftingSabotageHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Triggers the crafting-table sabotage on screen close.
 *
 * <p>Injected at TAIL of {@code onClosed} — vanilla cleanup (returning the
 * input items to player inventory) has already run, so when our sabotage
 * logic scans the inventory, it sees the post-close state which is what
 * the player will actually have.
 *
 * <p>Only fires for {@link CraftingScreenHandler} specifically (not other
 * screens that might inherit the same {@code onClosed}). This keeps the
 * sabotage scoped to actual crafting tables, not enchanting tables,
 * anvils, etc.
 *
 * <p>Server-side only — we gate on {@code ServerPlayerEntity} to ignore
 * client-side calls.
 */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {

    @Inject(method = "onClosed", at = @At("TAIL"))
    private void cuteDifficult$maybeSabotage(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        CraftingSabotageHandler.trySabotage(serverPlayer);
    }
}
