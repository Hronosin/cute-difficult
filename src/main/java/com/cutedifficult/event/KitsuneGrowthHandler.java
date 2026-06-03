package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kitsune grow tails over time. A kitsune accumulates "growth" each tick; when
 * it crosses a threshold it gains a tail (up to nine — Kyuubi). Growth is
 * passive by default but a recently-fed kitsune grows <b>50% faster</b>, so a
 * cared-for fox reaches Kyuubi in ~10 in-game days versus ~15 left alone.
 *
 * <p>Progress is tracked here (keyed by fox UUID) rather than in KitsuneData, to
 * avoid touching the persisted data schema. A fox's current tail count is the
 * source of truth on reload; in-memory progress simply resumes from the floor
 * of its current tier, so a restart costs at most a fraction of one tail.
 */
public final class KitsuneGrowthHandler {

    /** Effective ticks needed per tail (1.25 in-game days when fed). */
    private static final long TICKS_PER_TAIL = 30000;
    /** A fox counts as "recently fed" for this many ticks after eating. */
    private static final long FED_WINDOW = 6000; // 5 minutes
    /** Growth multiplier while recently fed. */
    private static final double FED_BONUS = 1.5;

    /** Accumulated growth progress per fox (effective ticks). */
    private static final Map<UUID, Double> PROGRESS = new ConcurrentHashMap<>();

    private static long tick = 0;

    private KitsuneGrowthHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            // Check growth a few times a second is plenty; accumulate per check.
            if (tick % 10 != 0) return;

            for (ServerWorld world : server.getWorlds()) {
                for (FoxEntity fox : world.getEntitiesByClass(FoxEntity.class,
                        new net.minecraft.util.math.Box(
                            -30000000, world.getBottomY(), -30000000,
                            30000000, world.getTopY(), 30000000),
                        f -> f instanceof com.cutedifficult.entity.KitsuneEntity && f.isAlive())) {
                    tickFox(world, fox);
                }
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] KitsuneGrowthHandler registered.");
    }

    private static void tickFox(ServerWorld world, FoxEntity fox) {
        KitsuneData data = FoxStorage.peekCache(fox);
        if (data == null) return;
        if (data.tails >= KitsuneData.MAX_TAILS) return; // already Kyuubi

        UUID id = fox.getUuid();
        // Seed progress at the floor of the current tier so a fresh load resumes
        // sensibly (a 3-tail fox starts this session's progress at the 3-tail mark).
        double progress = PROGRESS.computeIfAbsent(id,
            u -> (double) (data.tails - 1) * TICKS_PER_TAIL);

        // We tick every 10 ticks, so add 10 effective ticks (×bonus if fed).
        boolean recentlyFed = (world.getTime() - data.lastFedTickStamp) < FED_WINDOW
            && data.lastFedTickStamp > 0;
        double gain = 10.0 * (recentlyFed ? FED_BONUS : 1.0);
        progress += gain;
        PROGRESS.put(id, progress);

        // How many tails does this much progress justify?
        int earnedTails = Math.min(KitsuneData.MAX_TAILS, 1 + (int) (progress / TICKS_PER_TAIL));
        if (earnedTails > data.tails) {
            growTail(world, fox, data, earnedTails);
        }
    }

    private static void growTail(ServerWorld world, FoxEntity fox, KitsuneData data, int newTails) {
        KitsuneData updated = data.withTails(newTails);
        FoxStorage.store(fox, updated);
        com.cutedifficult.spirit.FoxStats.applyHpForTails(fox, newTails);

        // A quiet flourish each time a tail grows.
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
            fox.getX(), fox.getY() + 0.6, fox.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        if (newTails >= KitsuneData.MAX_TAILS) {
            // Becoming Kyuubi is a moment.
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                fox.getX(), fox.getY() + 0.8, fox.getZ(), 40, 0.5, 0.6, 0.5, 0.2);
            world.playSound(null, fox.getBlockPos(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        } else {
            world.playSound(null, fox.getBlockPos(),
                SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.3f);
        }
        CuteDifficult.LOGGER.info("[CuteDifficult] Kitsune {} grew to {} tails.",
            fox.getUuid().toString().substring(0, 8), newTails);
    }

    /** Forget progress for a removed fox (called opportunistically). */
    public static void forget(UUID id) {
        PROGRESS.remove(id);
    }
}
