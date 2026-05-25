package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.network.SpiritOverlayPayload;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Periodically sends each player a snapshot of their spirit state so the
 * client HUD overlay can render an up-to-date display.
 *
 * <p>Refresh interval is {@link #REFRESH_INTERVAL_TICKS} ticks (1 second
 * at 20 TPS). The HUD doesn't need higher resolution than that — spirit
 * changes happen at human-noticeable intervals (offerings, kills,
 * blessings activating). 1 Hz is comfortable and trivial bandwidth.
 *
 * <p>The replacement for the old vanilla sidebar scoreboard, which had
 * fundamental problems: (1) it's global, all players saw whoever the
 * server last refreshed, (2) it could only show ONE element at a time
 * via the rotating display, (3) it conflicted with vanilla and other
 * mod sidebars.
 */
public final class SpiritOverlayHandler {

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static long tickCounter = 0;

    private SpiritOverlayHandler() {}

    public static void registerPayloadType() {
        PayloadTypeRegistry.playS2C().register(SpiritOverlayPayload.PAYLOAD_ID, SpiritOverlayPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % REFRESH_INTERVAL_TICKS != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                sendSnapshot(server, player);
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] SpiritOverlayHandler registered.");
    }

    private static void sendSnapshot(MinecraftServer server, ServerPlayerEntity player) {
        Map<Element, Integer> spirits = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            spirits.put(element, SpiritData.get(server, player, element));
        }
        int karma = SpiritData.getKarma(server, player);
        boolean great = (CuteDifficult.currentMode == DifficultyMode.CRUEL)
            && ResonanceBlessingHandler.hasGreatBlessing(player);

        SpiritOverlayPayload payload = new SpiritOverlayPayload(spirits, karma, great);
        ServerPlayNetworking.send(player, payload);

        // Debug: log once every ~10 seconds so we can confirm the packet is being sent.
        if (tickCounter % 200 == 0) {
            CuteDifficult.LOGGER.info(
                "[CuteDifficult] Sent spirit overlay snapshot to {}: spirits={}, karma={}, great={}",
                player.getName().getString(), spirits, karma, great);
        }
    }
}
