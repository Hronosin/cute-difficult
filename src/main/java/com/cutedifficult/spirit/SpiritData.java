package com.cutedifficult.spirit;

import com.cutedifficult.CuteDifficult;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The fully suffocating Spirit system. Replaces the single Spirit scoreboard
 * value with NINE element-specific values, plus purity and resonance metrics.
 *
 * <p>The player's spiritual standing is no longer "I have 47 Spirit". It is:
 *
 * <pre>
 *   Kasai Spirit: 12 / 100
 *   Mizu Spirit: 5 / 100
 *   Daichi Spirit: 23 / 100
 *   Kaze Spirit: 0 / 100
 *   Kaminari Spirit: 0 / 100
 *   Mori Spirit: 41 / 100
 *   Kori Spirit: 8 / 100
 *   Yurei Spirit: 0 / 100
 *   Tengoku Spirit: 0 / 100
 *
 *   Total Purity: 67%
 *   Resonance: 4 (of 9)
 *   Hollow risk: low
 * </pre>
 *
 * <p>To approach a Kyuubi of Kasai, you need {@code Kasai Spirit} above 60.
 * Total Spirit doesn't help — only the matching element. To perform certain
 * cross-elemental rituals, you need Resonance (number of elements above
 * threshold) at a minimum count.
 *
 * <p><b>Hollow check</b>: the {@code totalSpirit()} of all nine is permitted
 * to go negative. Below 0 total, the player enters Hollow state.
 *
 * <p><b>Stored in scoreboard objectives:</b> one objective per element
 * ({@code cd_spirit_kasai}, {@code cd_spirit_mizu}, etc.) so they persist
 * with the world. The legacy {@code cd_spirit} from v0.1 is kept for
 * backward compatibility but is now COMPUTED as the sum.
 *
 * <p>Players who started a world in v0.1/0.2 will see their accumulated
 * {@code cd_spirit} ignored — fresh start on the 9-axis system. This is
 * deliberate: the new system isn't backward-mappable, and committing to
 * a fresh accounting matches the "ascetic restart" the late-game requires
 * anyway.
 *
 * <p><b>Display:</b> sidebar can only show ONE objective. We rotate through
 * the elements based on which the player has the highest value in, so the
 * sidebar shows what's most relevant. {@code /cd spirit} reveals the full
 * matrix.
 */
public final class SpiritData {

    /** Per-element scoreboard objective IDs. */
    public static String objectiveFor(Element element) {
        return "cd_spirit_" + element.shortName();
    }

    /** Karma is unchanged from v0.1. */
    public static final String KARMA_OBJECTIVE = "cd_karma";

    /** Marker tag — set when a player has been initialized in v0.3+. */
    public static final String INIT_TAG = "cd_initialized_v3";

    /** Starting value for each element when player joins fresh. */
    public static final int DEFAULT_PER_ELEMENT = 1;

    /** Threshold above which an element "counts" for resonance. */
    public static final int RESONANCE_THRESHOLD = 10;

    private SpiritData() {}

    public static void ensureObjectives(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        for (Element element : Element.values()) {
            String objId = objectiveFor(element);
            if (scoreboard.getNullableObjective(objId) == null) {
                scoreboard.addObjective(
                    objId,
                    ScoreboardCriterion.DUMMY,
                    Text.literal(element.kamiName() + " Spirit").formatted(element.color()),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    null
                );
                CuteDifficult.LOGGER.info("[CuteDifficult] Registered Spirit objective for {}", element);
            }
        }

        if (scoreboard.getNullableObjective(KARMA_OBJECTIVE) == null) {
            scoreboard.addObjective(
                KARMA_OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Karma").formatted(Formatting.RED),
                ScoreboardCriterion.RenderType.INTEGER,
                true,
                null
            );
        }
    }

    public static void initializePlayer(MinecraftServer server, ScoreHolder holder) {
        if (holder instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            if (player.getCommandTags().contains(INIT_TAG)) return;
            player.addCommandTag(INIT_TAG);
        }

        Scoreboard scoreboard = server.getScoreboard();
        for (Element element : Element.values()) {
            ScoreboardObjective obj = scoreboard.getNullableObjective(objectiveFor(element));
            if (obj != null) {
                scoreboard.getOrCreateScore(holder, obj).setScore(DEFAULT_PER_ELEMENT);
            }
        }

        ScoreboardObjective karma = scoreboard.getNullableObjective(KARMA_OBJECTIVE);
        if (karma != null) {
            scoreboard.getOrCreateScore(holder, karma).setScore(0);
        }
    }

    // --- Per-element read/write ---

    public static int get(MinecraftServer server, ScoreHolder holder, Element element) {
        ScoreboardObjective obj = server.getScoreboard().getNullableObjective(objectiveFor(element));
        if (obj == null) return 0;
        return server.getScoreboard().getOrCreateScore(holder, obj).getScore();
    }

    public static void set(MinecraftServer server, ScoreHolder holder, Element element, int value) {
        ScoreboardObjective obj = server.getScoreboard().getNullableObjective(objectiveFor(element));
        if (obj == null) return;
        int clamped = Math.max(-100, Math.min(100, value));
        server.getScoreboard().getOrCreateScore(holder, obj).setScore(clamped);
    }

    public static void add(MinecraftServer server, ScoreHolder holder, Element element, int delta) {
        set(server, holder, element, get(server, holder, element) + delta);
    }

    // --- Derived metrics ---

    /**
     * Sum of Spirit across all nine elements. Below 0 = Hollow.
     */
    public static int totalSpirit(MinecraftServer server, ScoreHolder holder) {
        int total = 0;
        for (Element element : Element.values()) {
            total += get(server, holder, element);
        }
        return total;
    }

    /**
     * "Resonance" — number of elements where Spirit >= {@link #RESONANCE_THRESHOLD}.
     * Range 0..9. Needed for certain cross-element rituals.
     */
    public static int resonance(MinecraftServer server, ScoreHolder holder) {
        int count = 0;
        for (Element element : Element.values()) {
            if (get(server, holder, element) >= RESONANCE_THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    /**
     * Purity (0..1) — sum of POSITIVE elements / sum of |all elements|.
     * Players close to Hollow have low purity even if they have one
     * strongly-developed element, because negatives drag it down.
     */
    public static double purity(MinecraftServer server, ScoreHolder holder) {
        int positive = 0;
        int absolute = 0;
        for (Element element : Element.values()) {
            int v = get(server, holder, element);
            if (v > 0) positive += v;
            absolute += Math.abs(v);
        }
        if (absolute == 0) return 0.0;
        return (double) positive / absolute;
    }

    /**
     * Which element does the player have the most Spirit in? Used for
     * sidebar display rotation.
     */
    public static Element dominantElement(MinecraftServer server, ScoreHolder holder) {
        Element best = Element.KASAI;
        int bestValue = Integer.MIN_VALUE;
        for (Element element : Element.values()) {
            int v = get(server, holder, element);
            if (v > bestValue) {
                bestValue = v;
                best = element;
            }
        }
        return best;
    }

    // --- Karma (unchanged) ---

    public static int getKarma(MinecraftServer server, ScoreHolder holder) {
        ScoreboardObjective obj = server.getScoreboard().getNullableObjective(KARMA_OBJECTIVE);
        if (obj == null) return 0;
        return server.getScoreboard().getOrCreateScore(holder, obj).getScore();
    }

    public static void addKarma(MinecraftServer server, ScoreHolder holder, int delta) {
        ScoreboardObjective obj = server.getScoreboard().getNullableObjective(KARMA_OBJECTIVE);
        if (obj == null) return;
        var score = server.getScoreboard().getOrCreateScore(holder, obj);
        score.setScore(score.getScore() + delta);
    }
}
