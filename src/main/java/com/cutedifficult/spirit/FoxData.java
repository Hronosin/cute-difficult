package com.cutedifficult.spirit;

import com.cutedifficult.entity.KitsuneEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The full spiritual identity of a single fox/kitsune.
 *
 * <p><b>v0.4.0 storage strategy:</b> we no longer round-trip data through
 * NBT for in-memory access. Reason: writing FoxData via {@code fox.readNbt}
 * triggers vanilla's {@code readCustomDataFromNbt} which can throw NPE on
 * our KitsuneEntity (vanilla's addTypeSpecificGoals doesn't like our setup).
 * The KitsuneEntity catches the NPE but the side effect is that NBT-driven
 * data injection becomes unreliable.
 *
 * <p>New approach: in-memory cache keyed by entity UUID. Persistence to
 * disk still happens via the standard NBT path (Minecraft writes the
 * entity NBT on save, reads it on load — both done by vanilla, just
 * around our KitsuneEntity NBT override), but day-to-day reads and
 * writes hit the cache directly. On NPE during NBT read at load, the
 * cache stays empty for that fox; we lazily regenerate on first
 * getOrCreate call (which is fine for fresh worlds; existing saves
 * would lose fox data on first load, acceptable for a v0.4 transition).
 *
 * <p>The WeakHashMap is keyed by UUID rather than entity reference so
 * we don't pin entities in memory after unload. Entities themselves are
 * tracked by Minecraft's entity manager and our cache is just a side-channel.
 */
public record FoxData(
    Element element,
    FoxPersonality personality,
    int tails,
    int trustLevel,
    long lastFedTickStamp,
    int witnessedKills
) {
    public static final String NBT_KEY = "cd_fox_data";
    public static final int MAX_TAILS = 9;

    /** In-memory cache. Entries are added on creation and looked up on read. */
    private static final WeakHashMap<UUID, FoxData> CACHE = new WeakHashMap<>();

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("element", element.name());
        nbt.put("personality", personality.toNbt());
        nbt.putInt("tails", tails);
        nbt.putInt("trustLevel", trustLevel);
        nbt.putLong("lastFedTickStamp", lastFedTickStamp);
        nbt.putInt("witnessedKills", witnessedKills);
        return nbt;
    }

    public static FoxData fromNbt(NbtCompound nbt) {
        return new FoxData(
            Element.valueOf(nbt.getString("element")),
            FoxPersonality.fromNbt(nbt.getCompound("personality")),
            nbt.getInt("tails"),
            nbt.getInt("trustLevel"),
            nbt.getLong("lastFedTickStamp"),
            nbt.getInt("witnessedKills")
        );
    }

    public static FoxData generate(Random rng) {
        return new FoxData(
            Element.random(rng),
            FoxPersonality.random(rng),
            1, 0, 0L, 0
        );
    }

    public FoxData withTails(int newTails) {
        return new FoxData(element, personality, newTails, trustLevel, lastFedTickStamp, witnessedKills);
    }

    public FoxData withTrust(int newTrust) {
        int clamped = Math.max(0, Math.min(100, newTrust));
        return new FoxData(element, personality, tails, clamped, lastFedTickStamp, witnessedKills);
    }

    public FoxData withLastFed(long tick) {
        return new FoxData(element, personality, tails, trustLevel, tick, witnessedKills);
    }

    /**
     * Returns cached data for this fox, or generates and caches a fresh
     * one if none exists.
     */
    public static FoxData getOrCreate(FoxEntity fox, Random rng) {
        UUID id = fox.getUuid();
        FoxData cached = CACHE.get(id);
        if (cached != null) return cached;
        FoxData fresh = generate(rng);
        CACHE.put(id, fresh);
        return fresh;
    }

    /**
     * Update cached data for this fox.
     */
    public static void store(FoxEntity fox, FoxData data) {
        CACHE.put(fox.getUuid(), data);
    }
}