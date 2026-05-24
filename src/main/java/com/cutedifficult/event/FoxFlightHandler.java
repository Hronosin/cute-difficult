package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxStats;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Gives Yurei (spirit) and Tengoku (sky) foxes the ability to hover and
 * fly when they reach 5+ tails.
 *
 * <p><b>Implementation:</b> we don't replace navigation — that would
 * require swapping {@link net.minecraft.entity.ai.pathing.MobNavigation}
 * for {@link net.minecraft.entity.ai.pathing.BirdNavigation} which is
 * mostly inaccessible without reflection. Instead we:
 * <ol>
 *   <li>Set {@link Entity#setNoGravity(boolean)} = true so the fox doesn't
 *       fall.</li>
 *   <li>Maintain a target hover height of {@link #IDLE_HOVER_HEIGHT}
 *       blocks above ground. The fox is gently nudged toward this height
 *       each tick.</li>
 *   <li>When a player is in range, drift toward the player (slowly) while
 *       maintaining hover. This makes flying kitsune feel "stalking" —
 *       they don't attack but they follow you in the air.</li>
 * </ol>
 *
 * <p><b>Visual quirk:</b> vanilla fox model isn't drawn for flight, so a
 * flying Kyuubi will look like a fox standing in the air. Combined with
 * the dense particle aura at 5+ tails, this still reads as "supernatural
 * floating entity" — but a custom render model would be nicer. Out of
 * scope for v0.3.x.
 *
 * <p>Active only in CRUEL mode.
 */
public final class FoxFlightHandler {

    /** Target altitude above ground when hovering idly. */
    private static final double IDLE_HOVER_HEIGHT = 4.0;

    /** Vertical movement speed when adjusting height. */
    private static final double VERTICAL_SPEED = 0.1;

    /** Horizontal drift speed when following a player. */
    private static final double FOLLOW_SPEED = 0.08;

    /** Radius within which a flying fox starts following a player. */
    private static final double FOLLOW_RADIUS = 24.0;

    /** Minimum distance to keep from player — won't get closer than this. */
    private static final double MIN_FOLLOW_DISTANCE = 8.0;

    private static final Random RANDOM = new Random();

    private FoxFlightHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        maybeFly(world, fox);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxFlightHandler registered.");
    }

    private static void maybeFly(ServerWorld world, FoxEntity fox) {
        FoxData data = FoxData.getOrCreate(fox, RANDOM);

        if (!FoxStats.canFly(data.element(), data.tails())) {
            // Defensive: if this fox isn't supposed to fly but somehow has
            // no-gravity set (e.g., from a previous state), clear it.
            if (fox.hasNoGravity()) {
                fox.setNoGravity(false);
            }
            return;
        }

        // Ensure no-gravity is active.
        if (!fox.hasNoGravity()) {
            fox.setNoGravity(true);
        }

        // Determine target altitude: ground level + hover height.
        double groundY = findGroundY(world, fox);
        double targetY = groundY + IDLE_HOVER_HEIGHT;

        // Compute desired velocity.
        Vec3d currentVel = fox.getVelocity();
        double vy = 0;
        if (fox.getY() < targetY - 0.5) {
            vy = VERTICAL_SPEED;
        } else if (fox.getY() > targetY + 0.5) {
            vy = -VERTICAL_SPEED * 0.5;
        } else {
            // Within hover band — small sinusoidal bob for life.
            vy = Math.sin(world.getTime() * 0.05 + fox.getId()) * 0.02;
        }

        // Horizontal: follow nearest player at a respectful distance.
        double vx = 0;
        double vz = 0;
        PlayerEntity player = world.getClosestPlayer(fox, FOLLOW_RADIUS);
        if (player != null) {
            double dx = player.getX() - fox.getX();
            double dz = player.getZ() - fox.getZ();
            double dist = Math.sqrt(dx*dx + dz*dz);
            if (dist > MIN_FOLLOW_DISTANCE) {
                // Drift toward player.
                vx = (dx / dist) * FOLLOW_SPEED;
                vz = (dz / dist) * FOLLOW_SPEED;
            }
            // If within MIN_FOLLOW_DISTANCE, hover in place.
        }

        fox.setVelocity(vx, vy, vz);
        fox.velocityModified = true;
    }

    /**
     * Find the Y coordinate of the highest solid block at the fox's
     * X/Z column. Used to compute target hover altitude.
     */
    private static double findGroundY(ServerWorld world, FoxEntity fox) {
        int x = fox.getBlockX();
        int z = fox.getBlockZ();
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        return topY;
    }
}
