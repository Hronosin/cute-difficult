package com.cutedifficult.spirit;

import com.cutedifficult.item.ScrollOfInquiryItem;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player Bestiary state. Tracks which (element, tail-tier) entries
 * the player has discovered via Scroll of Inquiry.
 *
 * <p>Entries are keyed by {@code element.name() + ":" + tailTier} so
 * "young Kasai" and "matured Kasai" are separate entries — different
 * recordings yield different lore reveals.
 *
 * <p>Like {@link FoxData}, this is in-memory cached. Persistence
 * across sessions is deferred — for v0.5 the bestiary resets on world
 * reload. Acceptable for early-game testing; will be fixed with a proper
 * persistent state attached to the player entity later.
 */
public final class BestiaryData {

    /** UUID → set of discovered keys. */
    private static final Map<UUID, Set<String>> ENTRIES = new HashMap<>();

    private BestiaryData() {}

    /** The full set of possible bestiary keys, in canonical order for display. */
    public static String entryKey(Element element, int tails) {
        return element.name() + ":" + ScrollOfInquiryItem.tailTier(tails);
    }

    /**
     * Adds an entry for this player. Returns true if newly added,
     * false if it was already present.
     */
    public static boolean recordEntry(ServerPlayerEntity player, FoxData data) {
        Set<String> playerEntries = ENTRIES.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
        return playerEntries.add(entryKey(data.element(), data.tails()));
    }

    /** Returns the set of entries this player has discovered. */
    public static Set<String> getEntries(ServerPlayerEntity player) {
        return ENTRIES.getOrDefault(player.getUuid(), Set.of());
    }

    public static int getEntryCount(ServerPlayerEntity player) {
        return getEntries(player).size();
    }

    /** Total possible entries: 9 elements × 5 tiers = 45. */
    public static final int MAX_ENTRIES = 45;
}
