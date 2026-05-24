package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;

/**
 * Post-mixin fox behavior tweaks.
 *
 * <p>The main flight suppression is now done by {@link com.cutedifficult.mixin.FoxEntityMixin}
 * which removes the avoid-goal from the fox's goal selector. This handler
 * is left with one job: putting the Kyuubi into a sitting pose when a
 * player is near, as a visual telegraph of "this is the one".
 *
 * <p>Tick rate: we check every tick but only act on Kyuubi (9-tail) foxes,
 * which are vanishingly rare. Cheap.
 */
public final class FoxBehaviorHandler {

    private static final double KYUUBI_SIT_RADIUS = 12.0;
    private static final Random RANDOM = new Random();

    private FoxBehaviorHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        maybeSit(world, fox);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxBehaviorHandler registered (Kyuubi pose only).");
    }

    private static void maybeSit(ServerWorld world, FoxEntity fox) {
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        if (data.tails() < FoxData.MAX_TAILS) {
            // Only Kyuubi sits. If a fox just dropped from 9 to <9 (shouldn't
            // happen but defensive), let vanilla handle posture again.
            return;
        }

        PlayerEntity nearestPlayer = world.getClosestPlayer(fox, KYUUBI_SIT_RADIUS);
        if (nearestPlayer != null) {
            fox.setSitting(true);
        }
    }
}
