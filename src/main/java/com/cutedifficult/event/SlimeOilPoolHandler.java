package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Slimes and magma cubes leave a slowing pool on death. The pool persists
 * for {@link #POOL_DURATION_TICKS} (8 seconds) and slows any LivingEntity
 * that walks into its radius.
 *
 * <p>Each pool is a transient entry in a list, ticked down each server
 * tick. No entity is actually placed in the world — pools are just
 * data + visual particles.
 *
 * <p>For magma cubes, the pool also briefly sets enemies on fire.
 */
public final class SlimeOilPoolHandler {

    private static final int POOL_DURATION_TICKS = 160; // 8s
    private static final double POOL_RADIUS = 1.8;

    private record Pool(ServerWorld world, Vec3d center, long expireTick, boolean fiery) {}

    private static final List<Pool> POOLS = new ArrayList<>();

    private SlimeOilPoolHandler() {}

    public static void register() {
        // On entity death, register a pool if it was a slime/magma cube.
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(victim.getWorld() instanceof ServerWorld world)) return;

            if (victim instanceof MagmaCubeEntity mc && mc.getSize() >= 2) {
                POOLS.add(new Pool(world, mc.getPos(), world.getTime() + POOL_DURATION_TICKS, true));
            } else if (victim instanceof SlimeEntity slime && slime.getSize() >= 2) {
                POOLS.add(new Pool(world, slime.getPos(), world.getTime() + POOL_DURATION_TICKS, false));
            }
        });

        // Tick all pools.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (POOLS.isEmpty()) return;
            long now = server.getOverworld().getTime();
            Iterator<Pool> it = POOLS.iterator();
            while (it.hasNext()) {
                Pool pool = it.next();
                if (now >= pool.expireTick) {
                    it.remove();
                    continue;
                }
                tickPool(pool);
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] SlimeOilPoolHandler registered.");
    }

    private static void tickPool(Pool pool) {
        // Particles — bubbles for slime, lava drips for magma.
        if (pool.world.getTime() % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = pool.world.random.nextDouble() * Math.PI * 2;
                double r = pool.world.random.nextDouble() * POOL_RADIUS;
                double px = pool.center.x + Math.cos(angle) * r;
                double pz = pool.center.z + Math.sin(angle) * r;
                pool.world.spawnParticles(
                    pool.fiery ? ParticleTypes.LAVA : ParticleTypes.ITEM_SLIME,
                    px, pool.center.y + 0.1, pz, 1, 0.1, 0.05, 0.1, 0.01
                );
            }
        }

        // Affect entities standing in the pool.
        var box = pool.world.getOtherEntities(null,
            new net.minecraft.util.math.Box(
                pool.center.x - POOL_RADIUS, pool.center.y - 0.5, pool.center.z - POOL_RADIUS,
                pool.center.x + POOL_RADIUS, pool.center.y + 1.5, pool.center.z + POOL_RADIUS
            ),
            e -> e instanceof LivingEntity && !(e instanceof SlimeEntity) && !(e instanceof MagmaCubeEntity)
        );
        for (Entity e : box) {
            if (!(e instanceof LivingEntity living)) continue;
            living.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 30, 2, false, false, true));
            if (pool.fiery) {
                living.setOnFireFor(2);
            }
        }
    }
}
