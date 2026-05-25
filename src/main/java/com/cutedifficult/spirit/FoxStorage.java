package com.cutedifficult.spirit;

import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Cache + NBT serialization for {@link KitsuneData}.
 *
 * <p>The data class itself ({@link KitsuneData}) is dumb — just public final
 * fields. All logic lives here:
 * <ul>
 *   <li>{@link #getOrCreate} — primary read path, used by every handler</li>
 *   <li>{@link #store} — write to cache</li>
 *   <li>{@link #peekCache} — peek without creating</li>
 *   <li>{@link #injectIntoCache} — set from NBT load</li>
 *   <li>{@link #toNbt} / {@link #fromNbt} — persistence</li>
 *   <li>{@link #generate} — randomized initialization</li>
 * </ul>
 *
 * <p>This separation makes the system robust: the data class can never
 * "be broken" by record-related compilation quirks because it has no
 * record-magic at all. All quirks are isolated to FoxStorage where they
 * can be debugged in one place.
 */
public final class FoxStorage {

    public static final String NBT_KEY = "cd_fox_data";

    private static final WeakHashMap<UUID, KitsuneData> CACHE = new WeakHashMap<>();

    private FoxStorage() {}

    public static NbtCompound toNbt(KitsuneData data) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("element", data.element.name());
        nbt.put("personality", data.personality.toNbt());
        nbt.putInt("tails", data.tails);
        nbt.putInt("trustLevel", data.trustLevel);
        nbt.putLong("lastFedTickStamp", data.lastFedTickStamp);
        nbt.putInt("witnessedKills", data.witnessedKills);
        nbt.putString("customName", data.customName);
        nbt.putLong("lastPettedTickStamp", data.lastPettedTickStamp);
        return nbt;
    }

    public static KitsuneData fromNbt(NbtCompound nbt) {
        Element element;
        try {
            element = Element.valueOf(nbt.getString("element"));
        } catch (IllegalArgumentException e) {
            element = Element.KASAI;
        }
        FoxPersonality personality = FoxPersonality.fromNbt(nbt.getCompound("personality"));
        return new KitsuneData(
            element,
            personality,
            nbt.getInt("tails"),
            nbt.getInt("trustLevel"),
            nbt.getLong("lastFedTickStamp"),
            nbt.getInt("witnessedKills"),
            nbt.contains("customName") ? nbt.getString("customName") : "",
            nbt.contains("lastPettedTickStamp") ? nbt.getLong("lastPettedTickStamp") : 0L
        );
    }

    public static KitsuneData generate(Random rng) {
        return new KitsuneData(
            Element.random(rng),
            FoxPersonality.random(rng),
            1, 0, 0L, 0, "", 0L
        );
    }

    public static KitsuneData getOrCreate(FoxEntity fox, Random rng) {
        UUID id = fox.getUuid();
        KitsuneData cached = CACHE.get(id);
        if (cached != null) return cached;
        KitsuneData fresh = generate(rng);
        CACHE.put(id, fresh);
        return fresh;
    }

    public static void store(FoxEntity fox, KitsuneData data) {
        CACHE.put(fox.getUuid(), data);
    }

    public static KitsuneData peekCache(FoxEntity fox) {
        return CACHE.get(fox.getUuid());
    }

    public static void injectIntoCache(FoxEntity fox, KitsuneData data) {
        CACHE.put(fox.getUuid(), data);
    }
}
