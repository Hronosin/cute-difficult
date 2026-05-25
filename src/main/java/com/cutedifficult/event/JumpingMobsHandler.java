package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Hostile mobs jump over single-block obstacles and small gaps.
 *
 * <p>Vanilla mobs are usually too stupid to handle this themselves —
 * they just walk into walls and get stuck. We patch that by ticking
 * every {@link HostileEntity}: if it has a target, is on the ground,
 * and there's a one-block-high obstacle in front of it (or a one-block
 * gap to cross), apply an upward velocity impulse.
 *
 * <p>Cooldown per mob ({@link #JUMP_COOLDOWN_TICKS}) so they don't
 * spam-bounce. The check is cheap: read the block in front + above
 * the mob's foot level.
 *
 * <p>This single change makes ALL hostile mobs (zombies, skeletons,
 * pillagers, creepers, spiders if they're not already, husks, drowned,
 * piglins, etc.) much harder to escape by exploiting terrain. No more
 * jumping on a 1-block ledge to safely shoot zombies forever.
 */
public final class JumpingMobsHandler {

    private static final int JUMP_COOLDOWN_TICKS = 20;
    private static final double JUMP_VELOCITY = 0.42;
    /** Don't trigger if target is too far — saves CPU. */
    private static final double TARGET_RANGE_SQUARED = 32 * 32;

    private static final WeakHashMap<UUID, Long> LAST_JUMP = new WeakHashMap<>();

    private JumpingMobsHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (!(entity instanceof HostileEntity hostile)) continue;
                    if (!hostile.isAlive() || !hostile.isOnGround()) continue;
                    tickHostile(world, hostile);
                }
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] JumpingMobsHandler registered.");
    }

    private static void tickHostile(ServerWorld world, HostileEntity mob) {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (mob.squaredDistanceTo(target) > TARGET_RANGE_SQUARED) return;

        UUID id = mob.getUuid();
        long now = world.getTime();
        Long lastJump = LAST_JUMP.get(id);
        if (lastJump != null && now - lastJump < JUMP_COOLDOWN_TICKS) return;

        // Check: is there an obstacle one block ahead, at foot level?
        Vec3d forward = directionTo(mob, target);
        BlockPos footAhead = BlockPos.ofFloored(
            mob.getX() + forward.x * 0.8,
            mob.getY(),
            mob.getZ() + forward.z * 0.8
        );
        BlockPos headAhead = footAhead.up();

        boolean blockedAtFoot = !world.getBlockState(footAhead).getCollisionShape(world, footAhead).isEmpty();
        boolean clearAtHead = world.getBlockState(headAhead).getCollisionShape(world, headAhead).isEmpty();
        BlockPos aboveHead = headAhead.up();
        boolean clearAboveHead = world.getBlockState(aboveHead).getCollisionShape(world, aboveHead).isEmpty();

        // Only jump if obstacle exists AND there's space above to land in.
        if (blockedAtFoot && clearAtHead && clearAboveHead) {
            mob.setVelocity(mob.getVelocity().x, JUMP_VELOCITY, mob.getVelocity().z);
            mob.velocityModified = true;
            LAST_JUMP.put(id, now);
            return;
        }

        // Also jump if the target is significantly above (e.g. on a hill).
        double dy = target.getY() - mob.getY();
        if (dy > 0.8 && dy < 2.5 && blockedAtFoot) {
            mob.setVelocity(mob.getVelocity().x, JUMP_VELOCITY, mob.getVelocity().z);
            mob.velocityModified = true;
            LAST_JUMP.put(id, now);
        }
    }

    private static Vec3d directionTo(Entity from, Entity to) {
        Vec3d d = to.getPos().subtract(from.getPos());
        double len = d.length();
        return len < 0.01 ? new Vec3d(1, 0, 0) : d.multiply(1.0 / len);
    }
}
