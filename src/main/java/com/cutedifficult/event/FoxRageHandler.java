package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recent player attacks on individual foxes.
 *
 * <p>When a player has the Great Blessing of Inari, kitsune normally
 * stand at peace with that player regardless of trust or witness
 * history. But this created an exploit: a blessed player could kill
 * any kitsune they wanted without consequence. That's narratively
 * wrong — even a kami-blessed mortal who raises a hand against a
 * kitsune should provoke immediate retaliation.
 *
 * <p>This handler keeps a per-fox map of "the last player who hit me,
 * and when." {@link com.cutedifficult.spirit.FoxHostility#canAttack}
 * queries this to override the Great Blessing peace when the target
 * matches the recent attacker. Entries time out after {@link #RAGE_TICKS}.
 *
 * <p>Cleanup runs each server tick — entries older than the rage
 * window are dropped. Negligible CPU cost since the map is keyed on
 * fox UUID and is typically tiny.
 */
public final class FoxRageHandler {

    /** How long a fox stays angry at a specific player after being hit. */
    public static final int RAGE_TICKS = 200; // 10 seconds

    /**
     * fox UUID → (attacker UUID, server tick when hit happened).
     */
    private static final Map<UUID, AttackRecord> RECENT_ATTACKS = new ConcurrentHashMap<>();

    private FoxRageHandler() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            // Don't override the actual attack — just record it.
            RECENT_ATTACKS.put(fox.getUuid(),
                new AttackRecord(sp.getUuid(), world.getTime()));
            return ActionResult.PASS;
        });

        // Cleanup expired entries.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (RECENT_ATTACKS.isEmpty()) return;
            long now = server.getOverworld().getTime();
            RECENT_ATTACKS.entrySet().removeIf(e -> now - e.getValue().tick > RAGE_TICKS);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxRageHandler registered.");
    }

    /**
     * Does this fox have a grudge against this specific player right now?
     * Returns true if the player hit this fox within {@link #RAGE_TICKS}.
     */
    public static boolean isEnragedAt(FoxEntity fox, ServerPlayerEntity player) {
        AttackRecord record = RECENT_ATTACKS.get(fox.getUuid());
        if (record == null) return false;
        if (!record.attackerId.equals(player.getUuid())) return false;
        long now = fox.getWorld().getTime();
        return now - record.tick <= RAGE_TICKS;
    }

    private record AttackRecord(UUID attackerId, long tick) {}
}
