package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.item.ModItems;
import com.cutedifficult.network.BestiaryOpenPayload;
import com.cutedifficult.spirit.BestiaryData;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypedActionResult;

import java.util.HashSet;

/**
 * Bestiary right-click handler. Sends a network payload to the client
 * containing the player's discovered entries, then the client opens
 * the custom {@code BestiaryScreen} GUI.
 *
 * <p>v0.6: rewritten to use a custom GUI instead of attempting to open
 * a written book via packet manipulation. The previous offhand-swap
 * approach was fragile across client/server states; a proper packet +
 * client screen is the canonical Fabric way to do this.
 *
 * <p><b>Registration order matters:</b> we must register the payload
 * type BEFORE first use. {@link #registerPayloadType()} is called from
 * the main mod initializer for the server side; the client init handles
 * the receiver side.
 */
public final class BestiaryHandler {

    private BestiaryHandler() {}

    public static void registerPayloadType() {
        PayloadTypeRegistry.playS2C().register(BestiaryOpenPayload.PAYLOAD_ID, BestiaryOpenPayload.CODEC);
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(ModItems.BESTIARY_OF_INARI)) {
                return TypedActionResult.pass(stack);
            }
            if (world.isClient) {
                return TypedActionResult.success(stack);
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(stack);
            }

            // Send the entries to the client.
            BestiaryOpenPayload payload = new BestiaryOpenPayload(
                new HashSet<>(BestiaryData.getEntries(serverPlayer))
            );
            ServerPlayNetworking.send(serverPlayer, payload);

            return TypedActionResult.success(stack);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] BestiaryHandler registered (custom GUI mode).");
    }
}
