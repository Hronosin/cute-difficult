package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Zombies grab + (at low HP) summon ONE reinforcement.
 *
 * <p><b>Anti-exponential-spawn safety:</b> reinforcement summoning is
 * gated by two rules to prevent runaway zombie hordes:
 * <ol>
 *   <li>Each zombie can only summon ONCE in its lifetime — tracked via
 *       {@link #HAS_SUMMONED} set.</li>
 *   <li>Summoned zombies are flagged via {@link #IS_SUMMONED_CHILD} and
 *       can NEVER summon themselves — chain depth is hard-capped at 1.</li>
 * </ol>
 * Maximum population growth per encounter: original zombie + 1 child = 2.
 *
 * <p>Grab logic is independent and unaffected.
 */
public final class ZombieGrabHandler {

    private static final double GRAB_RADIUS = 2.5;
    private static final int GRAB_DURATION_TICKS = 40;
    private static final int GRAB_COOLDOWN_TICKS = 200;
    private static final double PULL_STRENGTH = 0.08;

    /** HP fraction below which a zombie may summon reinforcement. */
    private static final float SUMMON_HP_THRESHOLD = 0.3f;

    private static final WeakHashMap<UUID, Long> GRAB_START = new WeakHashMap<>();
    private static final WeakHashMap<UUID, Long> LAST_GRAB_END = new WeakHashMap<>();
    private static final WeakHashMap<UUID, UUID> GRAB_TARGET = new WeakHashMap<>();

    /** Zombies that have already used their one summon — anti-exponential. */
    private static final Set<UUID> HAS_SUMMONED = new HashSet<>();
    /** Zombies that themselves came from a summon — can never summon. */
    private static final Set<UUID> IS_SUMMONED_CHILD = new HashSet<>();

    private ZombieGrabHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof ZombieEntity zombie && zombie.isAlive()) {
                        tickZombie(world, zombie);
                    }
                }
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] ZombieGrabHandler registered (grab + capped reinforcement).");
    }

    private static void tickZombie(ServerWorld world, ZombieEntity zombie) {
        UUID id = zombie.getUuid();
        long now = world.getTime();
        Long grabStart = GRAB_START.get(id);

        // --- Active grab tick ---
        if (grabStart != null && grabStart > 0) {
            UUID targetId = GRAB_TARGET.get(id);
            ServerPlayerEntity target = findPlayerByUuid(world, targetId);
            long elapsed = now - grabStart;

            if (target == null || !target.isAlive() || elapsed >= GRAB_DURATION_TICKS) {
                GRAB_START.remove(id);
                GRAB_TARGET.remove(id);
                LAST_GRAB_END.put(id, now);
                return;
            }
            Vec3d toZombie = zombie.getPos().subtract(target.getPos());
            double dist = toZombie.length();
            if (dist > 0.5 && dist < 10.0) {
                Vec3d pull = toZombie.multiply(PULL_STRENGTH / dist);
                target.setVelocity(target.getVelocity().add(pull.x, 0, pull.z));
                target.velocityModified = true;
            }
            if (elapsed % 20 == 0) {
                target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 30, 1, false, false, true));
            }
            if (elapsed % 5 == 0) {
                Vec3d mid = zombie.getPos().add(target.getPos()).multiply(0.5);
                world.spawnParticles(ParticleTypes.SMOKE, mid.x, mid.y + 1, mid.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }

        // --- Summon-reinforcement check ---
        tickSummon(world, zombie, id);

        // --- Idle: try to start a grab ---
        Long lastEnd = LAST_GRAB_END.get(id);
        if (lastEnd != null && now - lastEnd < GRAB_COOLDOWN_TICKS) return;

        LivingEntity target = zombie.getTarget();
        if (!(target instanceof ServerPlayerEntity sp)) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        if (zombie.distanceTo(sp) > GRAB_RADIUS) return;

        GRAB_START.put(id, now);
        GRAB_TARGET.put(id, sp.getUuid());

        world.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(),
            SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 1.5f, 0.6f);
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
            zombie.getX(), zombie.getY() + 1.5, zombie.getZ(), 5, 0.3, 0.2, 0.3, 0.05);
    }

    /**
     * Conditions for summon: (1) hasn't summoned yet, (2) isn't itself a
     * summoned child, (3) HP below threshold, (4) has a player target.
     * If all true, spawn one reinforcement zombie and mark this zombie
     * as having spent its summon.
     */
    private static void tickSummon(ServerWorld world, ZombieEntity zombie, UUID id) {
        if (HAS_SUMMONED.contains(id)) return;
        if (IS_SUMMONED_CHILD.contains(id)) return;
        if (zombie.getHealth() / zombie.getMaxHealth() > SUMMON_HP_THRESHOLD) return;
        if (!(zombie.getTarget() instanceof ServerPlayerEntity)) return;

        // Spawn a new zombie next to this one.
        ZombieEntity child = net.minecraft.entity.EntityType.ZOMBIE.create(world);
        if (child == null) return;
        double dx = (world.random.nextDouble() - 0.5) * 2;
        double dz = (world.random.nextDouble() - 0.5) * 2;
        child.refreshPositionAndAngles(zombie.getX() + dx, zombie.getY(), zombie.getZ() + dz,
            zombie.getYaw(), 0);
        world.spawnEntity(child);

        // Mark the child as un-summonable so it can NEVER spawn another.
        IS_SUMMONED_CHILD.add(child.getUuid());
        HAS_SUMMONED.add(id);

        // FX.
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
            zombie.getX(), zombie.getY() + 1, zombie.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
        world.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(),
            SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1.2f, 0.7f);
    }

    private static ServerPlayerEntity findPlayerByUuid(ServerWorld world, UUID id) {
        if (id == null) return null;
        Entity e = world.getEntity(id);
        return e instanceof ServerPlayerEntity sp ? sp : null;
    }
}
