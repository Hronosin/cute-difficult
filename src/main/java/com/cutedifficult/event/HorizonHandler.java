package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.world.ModDimensions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The Horizon's cosmic ambience. Instead of decoration glued to the player, the
 * void is populated with persistent celestial OBJECTS pinned to world space —
 * stars, nebulae, and singularities sit on a deterministic grid, so flying
 * toward one actually approaches it. Comets are the exception: they streak
 * across the player's view.
 *
 * <p>Performance: objects live on a coarse grid (every {@link #GRID} blocks).
 * Each tick we only consider grid cells within {@link #VIEW} of each Horizon
 * player and render the object that cell deterministically contains (if any).
 */
public final class HorizonHandler {

    private static long tick = 0;
    private static final Random COMET_RNG = new Random();

    /** Spacing of the cosmic object grid, in blocks. */
    private static final int GRID = 48;
    /** How far around the player we render objects. */
    private static final int VIEW = 96;

    private HorizonHandler() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getWorld().getRegistryKey() != ModDimensions.HORIZON) return true;
            if (source.isOf(DamageTypes.FALL)) return false;
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            ServerWorld horizon = server.getWorld(ModDimensions.HORIZON);
            if (horizon == null) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld() != horizon) continue;
                renderCosmos(horizon, player);
                if (tick % 30 == 0 && COMET_RNG.nextInt(3) == 0) {
                    spawnComet(horizon, player);
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] HorizonHandler registered.");
    }

    /** Render all world-pinned cosmic objects near the player. */
    private static void renderCosmos(ServerWorld world, ServerPlayerEntity player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());

        int gx0 = Math.floorDiv(px - VIEW, GRID);
        int gx1 = Math.floorDiv(px + VIEW, GRID);
        int gy0 = Math.floorDiv(py - VIEW, GRID);
        int gy1 = Math.floorDiv(py + VIEW, GRID);
        int gz0 = Math.floorDiv(pz - VIEW, GRID);
        int gz1 = Math.floorDiv(pz + VIEW, GRID);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gy = gy0; gy <= gy1; gy++) {
                for (int gz = gz0; gz <= gz1; gz++) {
                    renderCell(world, player, gx, gy, gz);
                }
            }
        }
    }

    /**
     * A grid cell deterministically either holds nothing or one cosmic object,
     * decided by a hash of its coordinates so the cosmos is stable across
     * visits (and identical for everyone on a server).
     */
    private static void renderCell(ServerWorld world, ServerPlayerEntity player, int gx, int gy, int gz) {
        long seed = hash(gx, gy, gz);
        // ~45% of cells are empty void.
        int kind = (int) (seed % 100);
        if (kind < 45) return;

        // Object centre, jittered within the cell deterministically.
        double ox = gx * GRID + (seed >> 8 & 31);
        double oy = gy * GRID + (seed >> 13 & 31);
        double oz = gz * GRID + (seed >> 18 & 31);

        // Throttle by object type so we don't spam thousands of particles.
        if (kind < 80) {
            // STAR — a single steady mote (most common).
            if (tick % 12 == 0) {
                world.spawnParticles(player, ParticleTypes.END_ROD, true,
                        ox, oy, oz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else if (kind < 92) {
            // NEBULA — a soft colored cloud, color seeded by the cell.
            if (tick % 10 == 0) {
                renderNebula(world, player, ox, oy, oz, seed);
            }
        } else {
            // SINGULARITY — a spiral of particles drawn inward to a dark center.
            if (tick % 4 == 0) {
                renderSingularity(world, player, ox, oy, oz);
            }
        }
    }

    private static void renderNebula(ServerWorld world, ServerPlayerEntity player,
                                     double x, double y, double z, long seed) {
        float r = ((seed >> 24 & 0xFF) / 255f) * 0.6f + 0.2f;
        float g = ((seed >> 32 & 0xFF) / 255f) * 0.6f + 0.2f;
        float b = ((seed >> 40 & 0xFF) / 255f) * 0.6f + 0.4f;
        DustParticleEffect dust = new DustParticleEffect(new Vector3f(r, g, b), 2.5f);
        world.spawnParticles(player, dust, true,
                x, y, z, 12, 2.5, 2.5, 2.5, 0.0);
    }

    private static void renderSingularity(ServerWorld world, ServerPlayerEntity player,
                                          double cx, double cy, double cz) {
        // Inward spiral: particles on an Archimedean spiral pulled toward center,
        // with velocity pointing in so they appear to be sucked in.
        double phase = (tick % 80) * (Math.PI / 40.0);
        int arms = 16;
        for (int i = 0; i < arms; i++) {
            double t = i / (double) arms;
            double angle = phase + t * Math.PI * 4;
            double radius = 4.0 * (1.0 - t); // spiral inward
            double sx = cx + Math.cos(angle) * radius;
            double sy = cy + Math.sin(t * Math.PI) * 0.5;
            double sz = cz + Math.sin(angle) * radius;
            // Velocity toward center for the "accretion" feel.
            Vec3d toCenter = new Vec3d(cx - sx, cy - sy, cz - sz).normalize().multiply(0.08);
            world.spawnParticles(player, ParticleTypes.PORTAL, true,
                    sx, sy, sz, 0, toCenter.x, toCenter.y, toCenter.z, 1.0);
        }
        // A dark, dense core.
        world.spawnParticles(player, ParticleTypes.SQUID_INK, true,
                cx, cy, cz, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(player, ParticleTypes.REVERSE_PORTAL, true,
                cx, cy, cz, 4, 0.4, 0.4, 0.4, 0.02);
    }

    /** A comet that streaks across the player's view and fades. */
    private static void spawnComet(ServerWorld world, ServerPlayerEntity player) {
        // Start off to one side, high up, and streak past.
        Vec3d look = player.getRotationVector();
        Vec3d start = player.getPos().add(
                (COMET_RNG.nextDouble() - 0.5) * 60,
                20 + COMET_RNG.nextDouble() * 20,
                (COMET_RNG.nextDouble() - 0.5) * 60);
        Vec3d dir = new Vec3d(
                (COMET_RNG.nextDouble() - 0.5),
                -0.3 - COMET_RNG.nextDouble() * 0.3,
                (COMET_RNG.nextDouble() - 0.5)).normalize();
        // Draw the trail as a line of particles.
        for (int i = 0; i < 24; i++) {
            Vec3d p = start.add(dir.multiply(i * 1.2));
            world.spawnParticles(player, ParticleTypes.END_ROD, true,
                    p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                world.spawnParticles(player, ParticleTypes.FIREWORK, true,
                        p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** Stable per-cell hash (positive). */
    private static long hash(int x, int y, int z) {
        long h = 1125899906842597L;
        h = 31 * h + x;
        h = 31 * h + y;
        h = 31 * h + z;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return Math.abs(h);
    }
}