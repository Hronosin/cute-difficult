package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Per-tick maintenance.
 *
 * <p>Three things happen here:
 * <ol>
 *   <li><b>Hunger refresh</b> — re-apply {@link StatusEffects#HUNGER} every
 *       5 seconds so it never drops off.</li>
 *   <li><b>Spirit decay</b> — once per in-game day, every element's Spirit
 *       decays by 1. This makes maintenance an active choice.</li>
 *   <li><b>Sidebar binding</b> — every 5 seconds, find the player's dominant
 *       element and bind ITS objective to the sidebar slot. Sidebar can only
 *       hold one objective; we rotate to the most-relevant.</li>
 * </ol>
 *
 * <p>The sidebar binding is server-global (the scoreboard is shared) which
 * means in multiplayer, all players see the dominant element of whoever
 * was last "ticked into" the sidebar. Single-player and small servers
 * with similar progression: fine. Big multiplayer servers: we'll need
 * per-player sidebars via a custom HUD (out of scope for v0.3.0).
 */
public final class PlayerTickHandler {
    private static final int HUNGER_REFRESH_INTERVAL = 100; // 5s
    private static final int HUNGER_DURATION_TICKS = 1200;
    private static final int HUNGER_AMPLIFIER = 0;

    private static final int SPIRIT_DECAY_INTERVAL = 24000; // 1 in-game day

    /** How often to re-evaluate dominant element. Every 5 seconds. */
    private static final int SIDEBAR_REFRESH_INTERVAL = 100;

    private static long tickCounter = 0;

    private PlayerTickHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            boolean refreshHunger = (tickCounter % HUNGER_REFRESH_INTERVAL == 0);
            boolean decaySpirit = (tickCounter % SPIRIT_DECAY_INTERVAL == 0);
            boolean refreshSidebar = (tickCounter % SIDEBAR_REFRESH_INTERVAL == 0);

            if (!refreshHunger && !decaySpirit && !refreshSidebar) return;
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            var playerList = server.getPlayerManager().getPlayerList();
            if (playerList.isEmpty()) return;

            // Sidebar update — pick whichever player's dominant element to show.
            // In single-player this is trivially correct; in multiplayer we
            // pick the first connected player. Custom per-player HUD is future work.
            if (refreshSidebar) {
                updateSidebar(server, playerList.get(0));
            }

            for (ServerPlayerEntity player : playerList) {
                if (player.isCreative() || player.isSpectator()) continue;

                if (refreshHunger) {
                    applyOrRefreshHunger(player);
                }
                if (decaySpirit) {
                    decayAllElements(server, player);
                }
            }
        });
    }

    private static void applyOrRefreshHunger(ServerPlayerEntity player) {
        StatusEffectInstance existing = player.getStatusEffect(StatusEffects.HUNGER);
        if (existing != null && existing.getDuration() > 40) return;

        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.HUNGER,
            HUNGER_DURATION_TICKS,
            HUNGER_AMPLIFIER,
            true,
            false,
            true
        ));
    }

    /**
     * Pick this player's dominant Spirit element and bind its objective to
     * the sidebar slot. The sidebar will then update live as the score
     * changes for that element.
     */
    private static void updateSidebar(MinecraftServer server, ServerPlayerEntity referencePlayer) {
        Element dominant = SpiritData.dominantElement(server, referencePlayer);
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective obj = scoreboard.getNullableObjective(SpiritData.objectiveFor(dominant));
        if (obj == null) return;
        // Only re-bind if the currently bound objective is different — avoids
        // unnecessary scoreboard packet spam.
        ScoreboardObjective currentlyBound = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (currentlyBound != obj) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
        }
    }

    /**
     * Once per in-game day: every element loses 1 Spirit, but never goes
     * below 0 from decay alone (negative values come only from active
     * offenses).
     */
    private static void decayAllElements(MinecraftServer server, ServerPlayerEntity player) {
        for (Element element : Element.values()) {
            int current = SpiritData.get(server, player, element);
            if (current > 0) {
                SpiritData.set(server, player, element, current - 1);
            }
        }
    }
}
