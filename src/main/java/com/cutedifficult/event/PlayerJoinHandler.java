package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Player initialization.
 *
 * <p><b>v0.7:</b> the half-HP modifier has been REMOVED. Players now
 * have full vanilla HP (20). Difficulty pressure has shifted to the
 * Quality system — weapons, tools, and armor have a random quality
 * tier on creation, and crude-tier gear is significantly weaker, while
 * masterwork is significantly stronger. This creates a fairer pacing:
 * you can survive but you're constantly hunting for better tier gear.
 *
 * <p>The hunger debuff and other ambient pressures remain. Path of
 * Peace still works via chat phrases (handled elsewhere).
 *
 * <p>This handler still exists because we may want to add other join-time
 * setup later (e.g. spirit init, intro message), but its current role
 * is minimal — just logging that the player joined a cruel world.
 */
public final class PlayerJoinHandler {

    /** Kept as a legacy identifier so we can REMOVE the modifier from any
     *  player who joined a save under the old half-HP version. */
    private static final Identifier LEGACY_HALF_HP_MODIFIER =
            Identifier.of(CuteDifficult.MOD_ID, "cruel_half_hp");

    private PlayerJoinHandler() {}

    public static void register() {
        // v0.9.5 fix: create all 9 element Spirit objectives + Karma at server
        // start. Without this, SpiritData.set silently does nothing and every
        // spirit-related command/handler in the mod fails. Sat broken since
        // v0.3 because nobody actually called this method.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SpiritData.ensureObjectives(server);
            CuteDifficult.LOGGER.info("[CuteDifficult] Spirit objectives initialized on server start.");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            removeLegacyHalfHp(player);
            // Set the player's Spirit to the default starting value if this is
            // their first time in this world. Idempotent — marker tag prevents
            // re-initialization on subsequent joins.
            SpiritData.initializePlayer(server, player);
            if (CuteDifficult.currentMode == DifficultyMode.CRUEL) {
                player.sendMessage(
                        net.minecraft.text.Text.literal("[CuteDifficult] The world is cruel. Forge better gear to survive.")
                                .formatted(net.minecraft.util.Formatting.DARK_RED),
                        false
                );
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            removeLegacyHalfHp(newPlayer);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] PlayerJoinHandler registered (HP restored, quality-driven balance, spirit init).");
    }

    /**
     * Strip the legacy half-HP modifier if it's present from a previous
     * version of the mod. Without this, players saved under v0.6 would
     * keep their reduced max HP forever even after upgrading.
     */
    private static void removeLegacyHalfHp(ServerPlayerEntity player) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;
        if (maxHealth.getModifier(LEGACY_HALF_HP_MODIFIER) != null) {
            maxHealth.removeModifier(LEGACY_HALF_HP_MODIFIER);
            // Heal to new max so the player isn't stuck at half the displayed bar.
            player.setHealth(player.getMaxHealth());
        }
    }
}