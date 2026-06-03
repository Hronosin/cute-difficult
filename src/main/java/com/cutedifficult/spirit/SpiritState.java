package com.cutedifficult.spirit;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Durable storage for player Spirit (9 elements) and Karma, backed by the
 * world's {@link PersistentState} (saved in the overworld's data folder) rather
 * than scoreboards. Scoreboards were fragile — visible to players, wiped by
 * commands, and easy to clobber. This keeps the same data invisibly and safely.
 *
 * <p>Keyed by holder name (player UUID string for players). Each record holds
 * the nine element values and a karma value.
 */
public class SpiritState extends PersistentState {

    private static final String STATE_ID = "cutedifficult_spirit";

    /** name -> record */
    private final Map<String, Record> records = new HashMap<>();

    public static final class Record {
        public final int[] elements = new int[Element.values().length];
        public int karma = 0;
    }

    public SpiritState() {}

    // ===== Access =====

    public Record getOrCreate(String key) {
        return records.computeIfAbsent(key, k -> new Record());
    }

    public Record peek(String key) {
        return records.get(key);
    }

    // ===== Persistence =====

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        NbtCompound all = new NbtCompound();
        for (Map.Entry<String, Record> e : records.entrySet()) {
            NbtCompound rec = new NbtCompound();
            Record r = e.getValue();
            for (int i = 0; i < r.elements.length; i++) {
                rec.putInt("e" + i, r.elements[i]);
            }
            rec.putInt("karma", r.karma);
            all.put(e.getKey(), rec);
        }
        nbt.put("records", all);
        return nbt;
    }

    public static SpiritState createFromNbt(NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        SpiritState state = new SpiritState();
        NbtCompound all = nbt.getCompound("records");
        for (String key : all.getKeys()) {
            NbtCompound rec = all.getCompound(key);
            Record r = new Record();
            for (int i = 0; i < r.elements.length; i++) {
                r.elements[i] = rec.getInt("e" + i);
            }
            r.karma = rec.getInt("karma");
            state.records.put(key, r);
        }
        return state;
    }

    private static final PersistentState.Type<SpiritState> TYPE =
        new PersistentState.Type<>(SpiritState::new, SpiritState::createFromNbt, null);

    /** Fetch the singleton state from the server (stored on the overworld). */
    public static SpiritState get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        PersistentStateManager mgr = overworld.getPersistentStateManager();
        SpiritState state = mgr.getOrCreate(TYPE, STATE_ID);
        state.markDirty();
        return state;
    }

    /** Stable storage key for a score holder (UUID string for players). */
    public static String keyFor(ScoreHolder holder) {
        return holder.getNameForScoreboard();
    }
}
