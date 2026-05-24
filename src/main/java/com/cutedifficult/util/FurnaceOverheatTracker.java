package com.cutedifficult.util;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-furnace heat tracking and explosion logic.
 *
 * <p><b>Mechanic:</b>
 * <ul>
 *   <li>While a furnace is actively burning ({@code LIT=true}), heat
 *       accumulates at {@link #HEAT_PER_TICK_BURNING} per tick.</li>
 *   <li>While idle, heat dissipates at {@link #HEAT_PER_TICK_IDLE} per tick
 *       (faster than it builds — short cooks are safe).</li>
 *   <li>Below {@link #HEAT_THRESHOLD}: nothing happens.</li>
 *   <li>Between threshold and max: increasing chance per tick to explode,
 *       plus visual smoke pouring from the top.</li>
 *   <li>At {@link #HEAT_MAX}: dramatic crackling sounds + explosion is
 *       imminent.</li>
 *   <li>Explosion: TNT-grade ({@link #EXPLOSION_POWER}), removes the
 *       furnace block, sets nearby blocks on fire.</li>
 * </ul>
 *
 * <p><b>Tuning rationale:</b> with default numbers, a furnace must burn for
 * ~2 minutes straight to even start being at risk, and ~5 minutes to peak
 * risk. Average crafting (5-10 items at 10s each) never reaches threshold.
 * Industrial setups (auto-smelters on coal blocks, lava buckets) hit it
 * eventually, which is thematically appropriate — the punishment scales
 * with player ambition.
 *
 * <p><b>Storage:</b> heat is held in a static in-memory map keyed by world
 * + block position. We do NOT persist this across server restarts; on
 * restart all furnaces start cold, which is a forgivable simplification
 * (the alternative would be NBT integration via mixin accessors, which
 * adds complexity for marginal value — heat dissipates fast anyway).
 */
public final class FurnaceOverheatTracker {
    /** Tick of burning needed before any risk exists. ~2 minutes. */
    private static final int HEAT_THRESHOLD = 2400;

    /** Tick of burning where explosion chance peaks. ~5 minutes. */
    private static final int HEAT_MAX = 6000;

    /** Heat gained per tick while burning. */
    private static final int HEAT_PER_TICK_BURNING = 1;

    /** Heat lost per tick while idle. Negative => dissipation. */
    private static final int HEAT_PER_TICK_IDLE = -3;

    /** Peak chance of explosion per tick at max heat. */
    private static final double MAX_EXPLOSION_CHANCE = 0.001; // ~1 in 1000 per tick ~ once per 50s

    /** Explosion power. TNT is 4.0. */
    private static final float EXPLOSION_POWER = 4.0f;

    /** Smoke at this fraction of (max - threshold) heat range. */
    private static final double SMOKE_VISIBLE_FRACTION = 0.0; // always visible past threshold

    /** Crackling sound at this fraction. */
    private static final double CRACKLE_FRACTION = 0.5;

    /** heat[worldKey][pos] = current heat value. */
    private static final Map<RegistryKey<World>, Map<BlockPos, Integer>> HEAT_BY_WORLD = new HashMap<>();

    private FurnaceOverheatTracker() {}

    /**
     * Called every furnace tick (from the mixin). Updates heat, spawns
     * visuals, rolls for explosion.
     */
    public static void tickFurnace(ServerWorld world, BlockPos pos, BlockState state) {
        Map<BlockPos, Integer> worldHeat = HEAT_BY_WORLD.computeIfAbsent(
            world.getRegistryKey(), k -> new HashMap<>()
        );

        boolean isLit = state.contains(AbstractFurnaceBlock.LIT) && state.get(AbstractFurnaceBlock.LIT);
        int currentHeat = worldHeat.getOrDefault(pos, 0);
        int delta = isLit ? HEAT_PER_TICK_BURNING : HEAT_PER_TICK_IDLE;
        int newHeat = Math.max(0, Math.min(HEAT_MAX, currentHeat + delta));

        if (newHeat == 0) {
            worldHeat.remove(pos);
            return;
        }

        worldHeat.put(pos, newHeat);

        if (newHeat <= HEAT_THRESHOLD) {
            return;
        }

        // Heat is in the danger zone — compute fraction (0..1) within zone.
        double dangerFraction = (double)(newHeat - HEAT_THRESHOLD) / (HEAT_MAX - HEAT_THRESHOLD);

        // Visual: pour smoke out the top.
        if (dangerFraction >= SMOKE_VISIBLE_FRACTION && world.getRandom().nextInt(8) == 0) {
            world.spawnParticles(
                ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5,
                pos.getY() + 1.05,
                pos.getZ() + 0.5,
                2,
                0.2, 0.05, 0.2,
                0.02
            );
        }

        // Audio: crackling at high heat, more frequent as it climbs.
        if (dangerFraction >= CRACKLE_FRACTION) {
            int crackleChance = (int)(80 * (1.0 - dangerFraction)) + 10; // 50..10 as danger grows
            if (world.getRandom().nextInt(crackleChance) == 0) {
                world.playSound(
                    null,
                    pos,
                    SoundEvents.BLOCK_FIRE_AMBIENT,
                    SoundCategory.BLOCKS,
                    1.2f,
                    0.5f + (float)(dangerFraction * 0.5)
                );
            }
        }

        // The roll. Linear scaling from 0 at threshold to MAX_EXPLOSION_CHANCE at max.
        double chance = dangerFraction * MAX_EXPLOSION_CHANCE;
        if (world.getRandom().nextDouble() < chance) {
            detonate(world, pos);
            worldHeat.remove(pos);
        }
    }

    /**
     * Blows up the furnace and the area around it.
     */
    private static void detonate(ServerWorld world, BlockPos pos) {
        // Remove the furnace block first so the explosion source isn't blocked by it.
        world.removeBlock(pos, false);

        world.createExplosion(
            null,                              // no entity attacker
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            EXPLOSION_POWER,
            true,                              // create fire (it WAS a furnace)
            World.ExplosionSourceType.TNT
        );
    }

    /**
     * Called when the server stops, to free memory. Not strictly necessary
     * for correctness — Java GC handles it — but tidy.
     */
    public static void clear() {
        HEAT_BY_WORLD.clear();
    }
}
