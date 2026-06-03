package com.cutedifficult.mixin;

import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the "Worlds using Experimental Settings are not supported / Here
 * be dragons!" warning that vanilla shows every time a world with custom
 * dimensions or world settings is loaded. Our Horizon dimension (added via a
 * datapack) trips this flag, so without this the player gets nagged on every
 * single world load.
 *
 * <p>Mirrors the open-source HereBeNoDragons / Disable Custom Worlds Advice
 * approach: intercept the loader's experimental-warning prompt and skip
 * straight to loading. Purely client-side QoL; nothing about world behavior
 * changes.
 *
 * <p>Target signature (confirmed from the 1.21.1 mapping at runtime):
 * {@code showBackupPromptScreen(LevelStorage.Session, boolean, Runnable callback, Runnable onCancel)}.
 * We run the load callback directly and cancel the original (which would show
 * the warning screen).
 */
@Mixin(IntegratedServerLoader.class)
public class ExperimentalWarningMixin {

    @Inject(
            method = "showBackupPromptScreen",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cutedifficult$skipExperimentalWarning(LevelStorage.Session session,
                                                       boolean customised,
                                                       Runnable callback,
                                                       Runnable onCancel,
                                                       CallbackInfo ci) {
        // Skip the warning screen and proceed straight to loading.
        callback.run();
        ci.cancel();
    }
}