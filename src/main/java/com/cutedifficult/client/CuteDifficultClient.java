package com.cutedifficult.client;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FoxEntityRenderer;

/**
 * Client-side initializer. Registers the renderer for our custom
 * {@link com.cutedifficult.entity.KitsuneEntity} so the game knows how
 * to draw it.
 *
 * <p>We reuse vanilla {@link FoxEntityRenderer} — KitsuneEntity extends
 * FoxEntity, so the same renderer handles it. Visually identical to a
 * regular fox; the only "kitsune-ness" is the particle aura that our
 * {@code FoxAuraHandler} spawns around it.
 *
 * <p>Registered as the {@code "client"} entrypoint in
 * {@code fabric.mod.json}. Server-side initialization is not affected.
 */
public class CuteDifficultClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.KITSUNE, FoxEntityRenderer::new);
        CuteDifficult.LOGGER.info("[CuteDifficult] Client renderer registered for Kitsune.");
    }
}
