package com.cutedifficult.spirit;

/**
 * Plain data-holder for a fox's spiritual identity.
 *
 * <p><b>v0.9.4 refactor:</b> previously a Java record, but the record
 * approach caused compile-time symbol resolution issues that we never
 * fully diagnosed. Now a regular class with public final fields. Slightly
 * more verbose but rock-solid: no auto-generated accessors that the
 * compiler can fail to see.
 *
 * <p>All read access is via field name directly (e.g. {@code data.element}
 * not {@code data.element()}). Write access goes through {@code with*}
 * helper methods that return new instances — preserves the immutability
 * pattern we wanted from records without the records themselves.
 *
 * <p>NBT serialization, generation, and the in-memory cache all live in
 * {@link FoxStorage}.
 */
public final class KitsuneData {
    public static final int MAX_TAILS = 9;

    public final Element element;
    public final FoxPersonality personality;
    public final int tails;
    public final int trustLevel;
    public final long lastFedTickStamp;
    public final int witnessedKills;
    public final String customName;
    public final long lastPettedTickStamp;

    public KitsuneData(Element element, FoxPersonality personality, int tails,
                   int trustLevel, long lastFedTickStamp, int witnessedKills,
                   String customName, long lastPettedTickStamp) {
        this.element = element;
        this.personality = personality;
        this.tails = tails;
        this.trustLevel = trustLevel;
        this.lastFedTickStamp = lastFedTickStamp;
        this.witnessedKills = witnessedKills;
        this.customName = customName == null ? "" : customName;
        this.lastPettedTickStamp = lastPettedTickStamp;
    }

    // Back-compat 6-arg helper.
    public static KitsuneData of6(Element element, FoxPersonality personality, int tails,
                              int trustLevel, long lastFedTickStamp, int witnessedKills) {
        return new KitsuneData(element, personality, tails, trustLevel,
            lastFedTickStamp, witnessedKills, "", 0L);
    }

    public KitsuneData withTails(int newTails) {
        return new KitsuneData(element, personality, newTails, trustLevel,
            lastFedTickStamp, witnessedKills, customName, lastPettedTickStamp);
    }

    public KitsuneData withTrust(int newTrust) {
        int clamped = Math.max(0, Math.min(100, newTrust));
        return new KitsuneData(element, personality, tails, clamped,
            lastFedTickStamp, witnessedKills, customName, lastPettedTickStamp);
    }

    public KitsuneData withLastFed(long tick) {
        return new KitsuneData(element, personality, tails, trustLevel,
            tick, witnessedKills, customName, lastPettedTickStamp);
    }

    public KitsuneData withCustomName(String name) {
        return new KitsuneData(element, personality, tails, trustLevel,
            lastFedTickStamp, witnessedKills, name, lastPettedTickStamp);
    }

    public KitsuneData withPersonality(FoxPersonality newPersonality) {
        return new KitsuneData(element, newPersonality, tails, trustLevel,
            lastFedTickStamp, witnessedKills, customName, lastPettedTickStamp);
    }

    public KitsuneData withLastPetted(long tick) {
        return new KitsuneData(element, personality, tails, trustLevel,
            lastFedTickStamp, witnessedKills, customName, tick);
    }

    public KitsuneData withWitnessUpdate(int newTrust, int newWitnessCount) {
        int clampedTrust = Math.max(0, Math.min(100, newTrust));
        return new KitsuneData(element, personality, tails, clampedTrust,
            lastFedTickStamp, newWitnessCount, customName, lastPettedTickStamp);
    }
}
