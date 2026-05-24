package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Active combat abilities for spiders, replacing the "x-ray vision" idea
 * that ran into mixin descriptor issues.
 *
 * <p><b>Two abilities, both LOS-gated:</b>
 * <ul>
 *   <li><b>Pounce</b> — when a spider has line-of-sight to a player at
 *       4-12 blocks, it can launch itself in a ballistic arc toward the
 *       player. Cooldown 5 seconds.</li>
 *   <li><b>Web shot</b> — at 5-16 blocks with LOS, the spider spits a
 *       "web glob" (particle line + cobweb burst at player). On hit, the
 *       player gets Slowness II for 4 seconds. Cooldown 8 seconds.</li>
 * </ul>
 *
 * <p>Web shot is a hitscan — no flying projectile entity. The particle
 * line shows where the web went, and slowness applies at the moment of
 * fire. Cheaper than a custom projectile and feels responsive.
 *
 * <p>Per-spider cooldowns via {@link WeakHashMap} keyed by entity UUID.
 * Entries naturally die when spiders die.
 *
 * <p>Active only in CRUEL mode.
 */
public final class SpiderAbilitiesHandler {

    private static final double POUNCE_MIN_DIST = 4.0;
    private static final double POUNCE_MAX_DIST = 12.0;
    private static final long POUNCE_COOLDOWN_MS = 5_000L;
    private static final double POUNCE_CHANCE_PER_TICK = 0.04;

    private static final double WEB_MIN_DIST = 5.0;
    private static final double WEB_MAX_DIST = 16.0;
    private static final long WEB_COOLDOWN_MS = 8_000L;
    private static final double WEB_CHANCE_PER_TICK = 0.03;

    private static final int WEB_SLOWNESS_AMPLIFIER = 1;
    private static final int WEB_SLOWNESS_TICKS = 80;

    private static final double POUNCE_SPEED = 1.4;
    private static final double POUNCE_UP = 0.6;

    private static final Random RANDOM = new Random();

    /** Per-spider cooldown tracking. WeakHashMap so dead spiders GC out. */
    private static final WeakHashMap<UUID, Long> LAST_POUNCE = new WeakHashMap<>();
    private static final WeakHashMap<UUID, Long> LAST_WEB = new WeakHashMap<>();

    private SpiderAbilitiesHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof SpiderEntity spider && spider.isAlive()) {
                        tickSpider(world, spider);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] SpiderAbilitiesHandler registered.");
    }

    private static void tickSpider(ServerWorld world, SpiderEntity spider) {
        LivingEntity target = spider.getTarget();
        if (!(target instanceof ServerPlayerEntity player)) return;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;

        Vec3d spiderEyes = spider.getPos().add(0, spider.getStandingEyeHeight(), 0);
        Vec3d playerCenter = player.getPos().add(0, player.getHeight() * 0.5, 0);
        double distance = spiderEyes.distanceTo(playerCenter);

        if (!hasLineOfSight(world, spiderEyes, playerCenter, spider)) return;

        long now = System.currentTimeMillis();

        if (distance >= POUNCE_MIN_DIST && distance <= POUNCE_MAX_DIST
            && RANDOM.nextDouble() < POUNCE_CHANCE_PER_TICK)
        {
            Long lastPounce = LAST_POUNCE.get(spider.getUuid());
            if (lastPounce == null || now - lastPounce >= POUNCE_COOLDOWN_MS) {
                doPounce(world, spider, playerCenter);
                LAST_POUNCE.put(spider.getUuid(), now);
                return;
            }
        }

        if (distance >= WEB_MIN_DIST && distance <= WEB_MAX_DIST
            && RANDOM.nextDouble() < WEB_CHANCE_PER_TICK)
        {
            Long lastWeb = LAST_WEB.get(spider.getUuid());
            if (lastWeb == null || now - lastWeb >= WEB_COOLDOWN_MS) {
                doWebShot(world, spider, spiderEyes, playerCenter, player);
                LAST_WEB.put(spider.getUuid(), now);
            }
        }
    }

    private static void doPounce(ServerWorld world, SpiderEntity spider, Vec3d targetPos) {
        Vec3d spiderPos = spider.getPos();
        Vec3d toTarget = targetPos.subtract(spiderPos);
        Vec3d horizontal = new Vec3d(toTarget.x, 0, toTarget.z).normalize();

        Vec3d velocity = new Vec3d(
            horizontal.x * POUNCE_SPEED,
            POUNCE_UP,
            horizontal.z * POUNCE_SPEED
        );
        spider.setVelocity(velocity);
        spider.velocityModified = true;

        world.spawnParticles(
            ParticleTypes.CRIT,
            spiderPos.x, spiderPos.y + 0.5, spiderPos.z,
            12,
            0.3, 0.2, 0.3,
            0.3
        );
        world.playSound(
            null,
            spiderPos.x, spiderPos.y, spiderPos.z,
            SoundEvents.ENTITY_SPIDER_AMBIENT,
            SoundCategory.HOSTILE,
            1.5f,
            0.7f
        );
    }

    private static void doWebShot(
        ServerWorld world,
        SpiderEntity spider,
        Vec3d from,
        Vec3d to,
        ServerPlayerEntity player
    ) {
        int segments = 16;
        Vec3d step = to.subtract(from).multiply(1.0 / segments);
        for (int i = 1; i <= segments; i++) {
            Vec3d point = from.add(step.multiply(i));
            world.spawnParticles(
                ParticleTypes.ITEM_COBWEB,
                point.x, point.y, point.z,
                2,
                0.1, 0.1, 0.1,
                0.0
            );
        }

        world.playSound(
            null,
            from.x, from.y, from.z,
            SoundEvents.ENTITY_SPIDER_HURT,
            SoundCategory.HOSTILE,
            1.0f,
            1.5f
        );

        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.SLOWNESS,
            WEB_SLOWNESS_TICKS,
            WEB_SLOWNESS_AMPLIFIER,
            false,
            true,
            true
        ));

        world.spawnParticles(
            ParticleTypes.ITEM_COBWEB,
            player.getX(), player.getY() + 1.0, player.getZ(),
            20,
            0.4, 0.5, 0.4,
            0.05
        );
    }

    private static boolean hasLineOfSight(ServerWorld world, Vec3d from, Vec3d to, Entity ignore) {
        RaycastContext ctx = new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ignore
        );
        BlockHitResult hit = world.raycast(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }
}
