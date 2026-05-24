package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Applies half-HP and initializes Spirit on player join + respawn.
 *
 * <p>v0.3.0: switched from single-Spirit {@code SpiritScoreboard} to the
 * 9-element {@code SpiritData}. New players get 1 Spirit per element.
 */
public final class PlayerJoinHandler {
    public static final Identifier CRUEL_HP_MODIFIER_ID =
        Identifier.of(CuteDifficult.MOD_ID, "cruel_half_hp");

    private PlayerJoinHandler() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SpiritData.ensureObjectives(server);
            CuteDifficult.LOGGER.info("[CuteDifficult] Server world initialized.");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            SpiritData.ensureObjectives(server);
            SpiritData.initializePlayer(server, player);

            if (CuteDifficult.currentMode == DifficultyMode.CRUEL) {
                applyHalfHp(player);
                sendWelcomeMessage(player);
            } else {
                removeHalfHp(player);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (CuteDifficult.currentMode == DifficultyMode.CRUEL) {
                applyHalfHp(newPlayer);
            } else {
                removeHalfHp(newPlayer);
            }
        });
    }

    public static void applyHalfHp(ServerPlayerEntity player) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;
        if (maxHealth.getModifier(CRUEL_HP_MODIFIER_ID) != null) return;

        EntityAttributeModifier modifier = new EntityAttributeModifier(
            CRUEL_HP_MODIFIER_ID,
            -10.0,
            EntityAttributeModifier.Operation.ADD_VALUE
        );
        maxHealth.addPersistentModifier(modifier);
        player.setHealth(player.getMaxHealth());
    }

    public static void removeHalfHp(ServerPlayerEntity player) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.removeModifier(CRUEL_HP_MODIFIER_ID);
    }

    private static void sendWelcomeMessage(ServerPlayerEntity player) {
        player.sendMessage(
            Text.literal("The world watches you. ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("The kitsune watch closer.").formatted(Formatting.GRAY, Formatting.ITALIC)),
            false
        );
    }
}
