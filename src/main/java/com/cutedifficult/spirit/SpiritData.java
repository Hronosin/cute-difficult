package com.cutedifficult.spirit;

import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;

/**
 * The Spirit system: nine element-specific values plus Karma, with derived
 * metrics (purity, resonance, dominant element).
 *
 * <p><b>Storage:</b> backed by {@link SpiritState} (a PersistentState saved with
 * the world) rather than scoreboards. Scoreboards were fragile — visible to
 * players, wiped by commands. Public method signatures are unchanged, so
 * callers don't know storage moved. Old scoreboard data isn't migrated (lost
 * across updates anyway — fresh start).
 */
public final class SpiritData {

    public static String objectiveFor(Element element) {
        return "cd_spirit_" + element.shortName();
    }

    public static final String KARMA_OBJECTIVE = "cd_karma";
    public static final String INIT_TAG = "cd_initialized_v3";
    public static final int DEFAULT_PER_ELEMENT = 1;
    public static final int RESONANCE_THRESHOLD = 10;
    /** Karma is clamped to this range. 0 = pure, 200 = utterly polluted. */
    public static final int MAX_KARMA = 200;

    private SpiritData() {}

    private static int indexOf(Element element) {
        Element[] all = Element.values();
        for (int i = 0; i < all.length; i++) if (all[i] == element) return i;
        return 0;
    }

    /** No-op now (kept for call-site compatibility — no scoreboards to set up). */
    public static void ensureObjectives(MinecraftServer server) {
        // Storage is PersistentState; nothing to register.
    }

    public static void initializePlayer(MinecraftServer server, ScoreHolder holder) {
        if (holder instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            if (player.getCommandTags().contains(INIT_TAG)) return;
            player.addCommandTag(INIT_TAG);
        }
        SpiritState state = SpiritState.get(server);
        SpiritState.Record r = state.getOrCreate(SpiritState.keyFor(holder));
        for (int i = 0; i < r.elements.length; i++) {
            r.elements[i] = DEFAULT_PER_ELEMENT;
        }
        r.karma = 0;
        state.markDirty();
    }

    // --- Per-element read/write ---

    public static int get(MinecraftServer server, ScoreHolder holder, Element element) {
        SpiritState.Record r = SpiritState.get(server).peek(SpiritState.keyFor(holder));
        if (r == null) return 0;
        return r.elements[indexOf(element)];
    }

    public static void set(MinecraftServer server, ScoreHolder holder, Element element, int value) {
        SpiritState state = SpiritState.get(server);
        SpiritState.Record r = state.getOrCreate(SpiritState.keyFor(holder));
        r.elements[indexOf(element)] = Math.max(-100, Math.min(100, value));
        state.markDirty();
    }

    public static void add(MinecraftServer server, ScoreHolder holder, Element element, int delta) {
        set(server, holder, element, get(server, holder, element) + delta);
    }

    // --- Derived metrics ---

    public static int totalSpirit(MinecraftServer server, ScoreHolder holder) {
        int total = 0;
        for (Element element : Element.values()) total += get(server, holder, element);
        return total;
    }

    public static int resonance(MinecraftServer server, ScoreHolder holder) {
        int count = 0;
        for (Element element : Element.values()) {
            if (get(server, holder, element) >= RESONANCE_THRESHOLD) count++;
        }
        return count;
    }

    public static double purity(MinecraftServer server, ScoreHolder holder) {
        int positive = 0, absolute = 0;
        for (Element element : Element.values()) {
            int v = get(server, holder, element);
            if (v > 0) positive += v;
            absolute += Math.abs(v);
        }
        if (absolute == 0) return 0.0;
        return (double) positive / absolute;
    }

    public static Element dominantElement(MinecraftServer server, ScoreHolder holder) {
        Element best = Element.KASAI;
        int bestValue = Integer.MIN_VALUE;
        for (Element element : Element.values()) {
            int v = get(server, holder, element);
            if (v > bestValue) { bestValue = v; best = element; }
        }
        return best;
    }

    // --- Karma ---

    public static int getKarma(MinecraftServer server, ScoreHolder holder) {
        SpiritState.Record r = SpiritState.get(server).peek(SpiritState.keyFor(holder));
        if (r == null) return 0;
        return r.karma;
    }

    public static void addKarma(MinecraftServer server, ScoreHolder holder, int delta) {
        SpiritState state = SpiritState.get(server);
        SpiritState.Record r = state.getOrCreate(SpiritState.keyFor(holder));
        r.karma = Math.max(0, Math.min(MAX_KARMA, r.karma + delta));
        state.markDirty();
    }

    // --- Karma tiers ---

    public enum KarmaTier { PURE, TAINTED, DEFILED, CURSED }

    public static KarmaTier karmaTier(int karma) {
        if (karma >= 150) return KarmaTier.CURSED;
        if (karma >= 100) return KarmaTier.DEFILED;
        if (karma >= 50) return KarmaTier.TAINTED;
        return KarmaTier.PURE;
    }

    public static KarmaTier karmaTier(MinecraftServer server, ScoreHolder holder) {
        return karmaTier(getKarma(server, holder));
    }

    /** Set karma directly (used by karma effects / cleansing). Floored at 0. */
    public static void setKarma(MinecraftServer server, ScoreHolder holder, int value) {
        SpiritState state = SpiritState.get(server);
        SpiritState.Record r = state.getOrCreate(SpiritState.keyFor(holder));
        r.karma = Math.max(0, Math.min(MAX_KARMA, value));
        state.markDirty();
    }
}
