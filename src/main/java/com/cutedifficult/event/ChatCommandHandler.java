package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

/**
 * Watches chat for two specific phrases that flip the world between modes.
 *
 * <p>The surrender phrase ("я казуал" / "i am casual" / "im casual") activates
 * Path of Peace. The redemption phrase ("я готов страдать" / "i am ready to
 * suffer") restores Cruel mode but applies the permanent Shame penalty.
 *
 * <p>Currently mode is server-global. Per-player tracking with persistent
 * state will come in v0.2 (data attachments on the player) so each member of
 * a multiplayer server walks their own path.
 *
 * <p>Note for the Iron Will achievement: we also passively log whether each
 * player has ever sent a message containing the word "casual" / "казуал" —
 * this lets us award the secret achievement at end-game without needing
 * the player to actively opt in to anything.
 */
public final class ChatCommandHandler {
    /** Phrases that activate Path of Peace. Match is case-insensitive, trimmed. */
    private static final Set<String> SURRENDER_PHRASES = Set.of(
        "я казуал",
        "i am casual",
        "im casual",
        "i'm casual"
    );

    /** Phrases that bring the player back to Cruel mode. */
    private static final Set<String> REDEMPTION_PHRASES = Set.of(
        "я готов страдать",
        "i am ready to suffer",
        "im ready to suffer"
    );

    private ChatCommandHandler() {}

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String content = message.getContent().getString().trim().toLowerCase();

            if (SURRENDER_PHRASES.contains(content)) {
                activatePathOfPeace(sender);
            } else if (REDEMPTION_PHRASES.contains(content)) {
                activateRedemption(sender);
            }
            // TODO: detect partial mentions ("это слишком сложно") to trigger
            // gaslighting reminders in player chat.
        });
    }

    private static void activatePathOfPeace(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CuteDifficult.currentMode = DifficultyMode.PATH_OF_PEACE;

        // Restore HP to vanilla.
        player.sendMessage(
                net.minecraft.text.Text.literal("You have chosen the Path of Peace.")
                        .formatted(net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.ITALIC),
                false
        );

        // Public announcement — the shame is part of the mechanic.
        Text announcement = Text.literal("[LOSS OF SPIRIT] ")
            .formatted(Formatting.DARK_GRAY)
            .append(Text.literal(player.getName().getString())
                .formatted(Formatting.GRAY))
            .append(Text.literal(" has chosen the Path of Peace. The kitsune grieve.")
                .formatted(Formatting.GRAY, Formatting.ITALIC));

        server.getPlayerManager().broadcast(announcement, false);

        CuteDifficult.LOGGER.info(
            "[CuteDifficult] Player {} surrendered to Path of Peace.",
            player.getName().getString()
        );
    }

    private static void activateRedemption(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (CuteDifficult.currentMode != DifficultyMode.PATH_OF_PEACE) {
            player.sendMessage(
                Text.literal("You have not yet fallen. There is nothing to return from.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                false
            );
            return;
        }

        CuteDifficult.currentMode = DifficultyMode.CRUEL;
        player.sendMessage(
                net.minecraft.text.Text.literal("You return to suffering. The cruel world watches you again.")
                        .formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.ITALIC),
                false
        );

        Text announcement = Text.literal("[RETURNED] ")
            .formatted(Formatting.GOLD)
            .append(Text.literal(player.getName().getString())
                .formatted(Formatting.YELLOW))
            .append(Text.literal(" walks the cruel road once more. A scar remains.")
                .formatted(Formatting.YELLOW, Formatting.ITALIC));

        server.getPlayerManager().broadcast(announcement, false);
    }
}
