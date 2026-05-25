package com.cutedifficult.client;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.client.gui.BestiaryScreen;
import com.cutedifficult.client.hud.SpiritOverlayHud;
import com.cutedifficult.entity.ModEntities;
import com.cutedifficult.network.BestiaryOpenPayload;
import com.cutedifficult.network.SpiritOverlayPayload;
import com.cutedifficult.spirit.BestiaryData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FoxEntityRenderer;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initializer.
 *
 * <p>v0.9.1: added the Spirit HUD toggle keybind (default: H) and the
 * background-fade behind the HUD content. Keybind is registered through
 * vanilla's KeyBinding system, so it appears in the Controls menu under
 * a "Cute Difficult" category and respects player rebinds.
 */
public class CuteDifficultClient implements ClientModInitializer {

    /** Keybind for toggling the Spirit overlay. Default: H. */
    private static KeyBinding toggleHudKey;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.KITSUNE, FoxEntityRenderer::new);

        // Bestiary GUI receiver.
        ClientPlayNetworking.registerGlobalReceiver(BestiaryOpenPayload.PAYLOAD_ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> BestiaryScreen.open(payload.entries(), BestiaryData.MAX_ENTRIES));
        });

        // Spirit overlay receiver.
        ClientPlayNetworking.registerGlobalReceiver(SpiritOverlayPayload.PAYLOAD_ID, (payload, context) -> {
            SpiritOverlayHud.updateFromPayload(payload);
            // Debug: log first received packet to confirm path works.
            if (!SpiritOverlayHud.hasReceivedData()) {
                CuteDifficult.LOGGER.info(
                    "[CuteDifficult] Client received first spirit overlay packet.");
            }
        });

        // HUD render hook.
        HudRenderCallback.EVENT.register(SpiritOverlayHud::render);

        // Toggle keybind. Default: H. Translation key is mapped in lang/en_us.json
        // (or left as the raw key string if no lang entry — fine for testing).
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cutedifficult.toggle_spirit_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.cutedifficult.main"
        ));

        // Tick handler to detect press events. We use wasPressed() which
        // consumes one press per call — that's exactly the toggle pattern
        // we want.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.wasPressed()) {
                SpiritOverlayHud.toggleVisibility();
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] Client init complete (renderer + bestiary + spirit HUD + keybinds).");
    }
}
