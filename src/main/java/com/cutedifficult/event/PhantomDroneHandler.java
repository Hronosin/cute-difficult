package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * FPV phantom drones with fairness pass (v0.1.5).
 *
 * <p><b>Changes from v0.1.1:</b>
 * <ul>
 *   <li>Phantoms now require line-of-sight to acquire and keep a target —
 *       raycast against block colliders. Hide behind a wall = phantom loses
 *       lock, drops to vanilla AI behavior (bobbing/swooping).</li>
 *   <li>Smoke trail respects LOS implicitly: trail only spawns while the
 *       phantom has a player target, so the player never sees a trail
 *       crossing through walls.</li>
 *   <li>Phantoms that collide with a block while charging (high velocity)
 *       detonate on impact. The explosion now leaves fire. This creates
 *       the "phantom slams into the wall you're hiding behind" moment —
 *       wall explodes inward, you may still die, but you got a chance.</li>
 * </ul>
 *
 * <p><b>Design rationale:</b> the old "phantoms see through everything"
 * behavior was unfair in a way that broke the mod's tone. Cute Difficult
 * is cruel, but every cruelty should be readable — player should always
 * understand why they died. LOS-respecting phantoms give the player a
 * legible counter (cover) without making the mechanic toothless: the
 * explosion-on-impact rule punishes panic-hiding inside thin walls.
 *
 * <p><b>The collision-detonation speed threshold</b> ({@code COLLIDE_SPEED_SQ})
 * exists so that idle phantoms which gently brush a ceiling or wall while
 * drifting don't randomly explode. Only phantoms travelling at our drone
 * speed (set by piloting) trip the rule.
 */
public final class PhantomDroneHandler {
    private static final double DETECTION_RADIUS = 64.0;
    private static final double DRONE_SPEED = 0.55;
    private static final double DETONATION_DISTANCE = 1.8;
    private static final float EXPLOSION_POWER = 2.5f;
    private static final int MAX_TRAIL_PARTICLES = 24;

    /** Squared speed above which a collision counts as a "charge impact". */
    private static final double COLLIDE_SPEED_SQ = 0.16; // (0.4)^2

    private PhantomDroneHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof PhantomEntity phantom && phantom.isAlive()) {
                        pilotPhantom(world, phantom);
                    }
                }
            }
        });
    }

    private static void pilotPhantom(ServerWorld world, PhantomEntity phantom) {
        // === Step 1: if the phantom was charging and hit a block, detonate. ===
        // Vanilla sets these flags during move() which runs before our tick.
        boolean collided = phantom.horizontalCollision || phantom.verticalCollision;
        double speedSq = phantom.getVelocity().lengthSquared();
        if (collided && speedSq > COLLIDE_SPEED_SQ) {
            detonate(world, phantom);
            return;
        }

        // === Step 2: find nearest valid player in range. ===
        var nearest = world.getClosestPlayer(
            phantom.getX(), phantom.getY(), phantom.getZ(),
            DETECTION_RADIUS,
            player -> player instanceof ServerPlayerEntity sp
                && !sp.isCreative()
                && !sp.isSpectator()
                && sp.isAlive()
        );

        if (!(nearest instanceof ServerPlayerEntity target)) return;

        // === Step 3: line-of-sight check. ===
        Vec3d aimPoint = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d phantomCenter = phantom.getPos().add(0, phantom.getHeight() * 0.5, 0);

        if (!hasLineOfSight(world, phantomCenter, aimPoint, phantom)) {
            // Player is behind cover. Phantom drops to vanilla AI this tick.
            // Importantly: we don't zero its velocity — it keeps any momentum
            // from a prior charge, which means panic-hiding in thin cover
            // can result in the phantom slamming the wall (handled in step 1
            // on the next tick).
            return;
        }

        // === Step 4: pilot. ===
        Vec3d toTarget = aimPoint.subtract(phantomCenter);
        double distance = toTarget.length();
        if (distance < 0.01) return;

        Vec3d velocity = toTarget.normalize().multiply(DRONE_SPEED);
        phantom.setVelocity(velocity);
        phantom.velocityModified = true;

        // Detonate on direct hit.
        if (distance < DETONATION_DISTANCE) {
            detonate(world, phantom);
            return;
        }

        // Visual warning. Only spawned while LOS is clear, which is the
        // fairness property: if you've broken LOS you don't see the trail.
        spawnWarningTrail(world, phantomCenter, aimPoint, distance);
        spawnDroneMarker(world, phantomCenter);
    }

    /**
     * Raycast from phantom to target. Returns true if no block obstructs
     * the line. Uses COLLIDER shape (solid blocks block sight, transparent
     * things like grass do not) and ignores fluids.
     */
    private static boolean hasLineOfSight(ServerWorld world, Vec3d from, Vec3d to, PhantomEntity phantom) {
        RaycastContext context = new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            phantom
        );
        BlockHitResult hit = world.raycast(context);
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void spawnWarningTrail(ServerWorld world, Vec3d from, Vec3d to, double distance) {
        int particleCount = (int) Math.min(MAX_TRAIL_PARTICLES, Math.max(4, distance / 2.0));
        Vec3d step = to.subtract(from).multiply(1.0 / particleCount);

        for (int i = 1; i <= particleCount; i++) {
            Vec3d point = from.add(step.multiply(i));
            world.spawnParticles(
                ParticleTypes.SMOKE,
                point.x, point.y, point.z,
                1,
                0.05, 0.05, 0.05,
                0.0
            );
        }
    }

    private static void spawnDroneMarker(ServerWorld world, Vec3d center) {
        world.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            center.x, center.y, center.z,
            3,
            0.3, 0.3, 0.3,
            0.01
        );
    }

    private static void detonate(ServerWorld world, PhantomEntity phantom) {
        world.createExplosion(
            phantom,
            phantom.getX(), phantom.getY(), phantom.getZ(),
            EXPLOSION_POWER,
            true,  // createFire — leaves fire behind, per v0.1.5 fairness pass
            World.ExplosionSourceType.MOB
        );
        phantom.discard();
    }
}
