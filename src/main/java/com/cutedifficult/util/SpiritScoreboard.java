package com.cutedifficult.util;

import com.cutedifficult.CuteDifficult;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Wraps the scoreboard objectives used to track Spirit and Karma.
 *
 * <p><b>Fixes applied in v0.1.1:</b>
 * <ul>
 *   <li>Spirit objective is now bound to the SIDEBAR display slot, so it
 *       actually shows up on the player's HUD on the right side of the
 *       screen. Previously it existed only as invisible data.</li>
 *   <li>Initialization to the default Spirit value of 5 was buggy — it used
 *       a non-existent {@code isLocked()} check and never reliably ran.
 *       Now we mark the player with a command tag {@code cd_initialized}
 *       on first init; tags persist across sessions, so each player is
 *       initialized exactly once.</li>
 *   <li>Removed the "auto-init on every getSpirit" behavior, which had a
 *       race with the scoreboard's default-0 state. Initialization is now
 *       explicit via {@link #initializePlayer}, called from the join handler.</li>
 * </ul>
 *
 * <p><b>Note on display:</b> only ONE objective can occupy SIDEBAR at a time.
 * We give that slot to Spirit because it's the headline progression stat;
 * Karma is queryable via {@code /cd karma} or {@code /scoreboard players list}
 * for now. Proper dual-stat HUD will come with the custom UI in v0.3.
 */
public final class SpiritScoreboard {
    public static final String SPIRIT_OBJECTIVE = "cd_spirit";
    public static final String KARMA_OBJECTIVE = "cd_karma";

    /** Marker tag added to players after their first Spirit initialization. */
    private static final String INIT_TAG = "cd_initialized";

    /** Default Spirit value for a freshly initialized "Mortal" tier player. */
    public static final int DEFAULT_SPIRIT = 5;

    private SpiritScoreboard() {}

    /**
     * Ensures both objectives exist, AND that Spirit is bound to the sidebar.
     * Safe to call repeatedly.
     */
    public static void ensureObjectives(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        ScoreboardObjective spirit = scoreboard.getNullableObjective(SPIRIT_OBJECTIVE);
        if (spirit == null) {
            spirit = scoreboard.addObjective(
                SPIRIT_OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Spirit").formatted(Formatting.AQUA),
                ScoreboardCriterion.RenderType.INTEGER,
                true,
                null
            );
            CuteDifficult.LOGGER.info("[CuteDifficult] Registered Spirit scoreboard objective.");
        }

        ScoreboardObjective karma = scoreboard.getNullableObjective(KARMA_OBJECTIVE);
        if (karma == null) {
            scoreboard.addObjective(
                KARMA_OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Karma").formatted(Formatting.RED),
                ScoreboardCriterion.RenderType.INTEGER,
                true,
                null
            );
            CuteDifficult.LOGGER.info("[CuteDifficult] Registered Karma scoreboard objective.");
        }

        // Bind Spirit to the sidebar so players can see it on the right of the screen.
        // Idempotent: setting the same objective again is a no-op.
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, spirit);
    }

    /**
     * Initializes a player's Spirit to the default value if they haven't been
     * initialized before. Uses a command tag as a persistent flag.
     *
     * <p>Call this once on player join.
     */
    public static void initializePlayer(MinecraftServer server, ServerPlayerEntity player) {
        if (player.getCommandTags().contains(INIT_TAG)) {
            return; // Already initialized in a previous session.
        }
        player.addCommandTag(INIT_TAG);

        ScoreboardObjective spirit = server.getScoreboard().getNullableObjective(SPIRIT_OBJECTIVE);
        if (spirit != null) {
            server.getScoreboard()
                .getOrCreateScore(player, spirit)
                .setScore(DEFAULT_SPIRIT);
        }

        ScoreboardObjective karma = server.getScoreboard().getNullableObjective(KARMA_OBJECTIVE);
        if (karma != null) {
            server.getScoreboard()
                .getOrCreateScore(player, karma)
                .setScore(0);
        }

        CuteDifficult.LOGGER.info(
            "[CuteDifficult] Initialized Spirit={} Karma=0 for {}",
            DEFAULT_SPIRIT, player.getName().getString()
        );
    }

    // === Read/write helpers ===

    public static int getSpirit(MinecraftServer server, ScoreHolder holder) {
        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(SPIRIT_OBJECTIVE);
        if (objective == null) return DEFAULT_SPIRIT;
        return server.getScoreboard().getOrCreateScore(holder, objective).getScore();
    }

    public static void setSpirit(MinecraftServer server, ScoreHolder holder, int value) {
        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(SPIRIT_OBJECTIVE);
        if (objective == null) return;
        int clamped = Math.max(-100, Math.min(100, value));
        server.getScoreboard().getOrCreateScore(holder, objective).setScore(clamped);
    }

    public static void addSpirit(MinecraftServer server, ScoreHolder holder, int delta) {
        setSpirit(server, holder, getSpirit(server, holder) + delta);
    }

    public static int getKarma(MinecraftServer server, ScoreHolder holder) {
        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(KARMA_OBJECTIVE);
        if (objective == null) return 0;
        return server.getScoreboard().getOrCreateScore(holder, objective).getScore();
    }

    public static void setKarma(MinecraftServer server, ScoreHolder holder, int value) {
        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(KARMA_OBJECTIVE);
        if (objective == null) return;
        server.getScoreboard().getOrCreateScore(holder, objective).setScore(value);
    }

    public static void addKarma(MinecraftServer server, ScoreHolder holder, int delta) {
        setKarma(server, holder, getKarma(server, holder) + delta);
    }
}
