package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxStats;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Proactive ability casting for high-tail kitsune. The previous design
 * tied ability casts to "took damage this tick", which meant kitsune
 * never used their powers unless the player attacked first. For mature
 * kitsune, this didn't match the lore — they should be assertive,
 * occasionally threatening even peaceful players.
 *
 * <p><b>Rule:</b> if a kitsune has {@link #AGGRESSION_MIN_TAILS} or more
 * tails, AND a player is within {@link #AGGRESSION_RADIUS} blocks, AND
 * the kitsune's own cooldown is up, AND the random roll lands, then the
 * kitsune casts an ability at the player.
 *
 * <p><b>Roll probabilities</b> are very small per tick so that even with
 * a kitsune sitting next to the player, an aggression cast happens
 * maybe once every 5-15 seconds, not every tick. Cooldown then enforces
 * the lower bound.
 *
 * <p>The actual ability code lives in {@link FoxAbilityHandler} —
 * we call into its dispatch by simulating a tiny HP drop (this is gross
 * but avoids duplicating the elemental dispatch). A cleaner refactor
 * would expose a public {@code FoxAbilityHandler.castNow(fox, player)}
 * method; will do that if we add more triggers.
 *
 * <p><b>Tied to FoxAbilityHandler's cooldown</b>: both handlers respect
 * the same {@code FoxStats.abilityCooldownTicks} so we don't double-cast.
 * Different per-fox WeakHashMaps though — we keep our own to avoid
 * stepping on the other handler's state.
 */
public final class FoxAggressionHandler {

    /** Minimum tails to be proactively aggressive. */
    private static final int AGGRESSION_MIN_TAILS = 3;

    /** Radius around fox to look for a player target. */
    private static final double AGGRESSION_RADIUS = 12.0;

    /** Per-tick probability of attempting a cast (gated by cooldown). */
    private static final double CAST_CHANCE_PER_TICK = 0.01;

    private static final WeakHashMap<UUID, Long> LAST_CAST = new WeakHashMap<>();
    private static final Random RANDOM = new Random();

    private FoxAggressionHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        tickFox(world, fox);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxAggressionHandler registered.");
    }

    private static void tickFox(ServerWorld world, FoxEntity fox) {
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        if (data.tails() < AGGRESSION_MIN_TAILS) return;

        // Random roll first — most of the time this is the only check.
        if (RANDOM.nextDouble() >= CAST_CHANCE_PER_TICK) return;

        // Find a valid target.
        ServerPlayerEntity target = (ServerPlayerEntity) world.getClosestPlayer(
            fox.getX(), fox.getY(), fox.getZ(),
            AGGRESSION_RADIUS,
            p -> p instanceof ServerPlayerEntity sp
                && !sp.isCreative() && !sp.isSpectator() && sp.isAlive()
        );
        if (target == null) return;

        // Cooldown check.
        UUID id = fox.getUuid();
        long now = world.getTime();
        Long lastCast = LAST_CAST.get(id);
        int cooldown = FoxStats.abilityCooldownTicks(data.tails());
        if (lastCast != null && now - lastCast < cooldown) return;

        // Cast! We invoke the ability through the public helper.
        FoxAbilityHandler.castElementalAbility(world, fox, data, target);
        LAST_CAST.put(id, now);
    }
}
