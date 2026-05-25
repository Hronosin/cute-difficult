package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Buffs to Nether mobs — making them "actual devils" per design.
 *
 * <ul>
 *   <li><b>Blazes:</b> periodically fire a 3-ball fan instead of a single
 *       fireball — wider area threat, harder to dodge.</li>
 *   <li><b>Ghasts:</b> when HP drops below 50% the first time, spawn ONE
 *       smaller ghast as a child (mini-ghast = scaled-down HP). The
 *       mini-ghast cannot itself summon. Hard-capped chain.</li>
 *   <li><b>Magma cubes:</b> if airborne for &gt; 0.6s (long fall) and
 *       hit ground, briefly explode in a fiery puff dealing damage and
 *       knockback. Doesn't damage blocks.</li>
 *   <li><b>Piglin brutes:</b> at HP &lt; 30%, enter "blood frenzy" — gain
 *       Strength II for 10s. Visible red particle aura while active.</li>
 * </ul>
 *
 * <p><b>Anti-exponential safety:</b> ghasts can only summon ONCE. The
 * summoned mini-ghast is marked in {@link #IS_MINI_GHAST} and cannot
 * trigger its own summon.
 */
public final class NetherMobsHandler {

    /** Blaze fan-fire cooldown. */
    private static final int BLAZE_FAN_COOLDOWN = 100; // 5s
    private static final double BLAZE_TARGET_RANGE = 16.0;

    /** Ghast summon: only one mini per ghast lifetime. */
    private static final Set<UUID> GHAST_HAS_SUMMONED = new HashSet<>();
    private static final Set<UUID> IS_MINI_GHAST = new HashSet<>();

    /** Magma cube fall tracking — ticks airborne. */
    private static final WeakHashMap<UUID, Integer> MAGMA_AIRBORNE_TICKS = new WeakHashMap<>();
    private static final int MAGMA_AIRBORNE_THRESHOLD = 12; // 0.6s

    /** Brute frenzy: tracks last trigger so we don't spam. */
    private static final WeakHashMap<UUID, Long> BRUTE_LAST_FRENZY = new WeakHashMap<>();
    private static final int BRUTE_FRENZY_COOLDOWN = 600; // 30s

    private static final WeakHashMap<UUID, Long> BLAZE_LAST_FAN = new WeakHashMap<>();
    private static final Random RANDOM = new Random();

    private NetherMobsHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (!entity.isAlive()) continue;
                    if (entity instanceof BlazeEntity blaze) tickBlaze(world, blaze);
                    else if (entity instanceof GhastEntity ghast) tickGhast(world, ghast);
                    else if (entity instanceof MagmaCubeEntity mc) tickMagmaCube(world, mc);
                    else if (entity instanceof PiglinBruteEntity brute) tickBrute(world, brute);
                }
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] NetherMobsHandler registered.");
    }

    // ===== BLAZE =====

    private static void tickBlaze(ServerWorld world, BlazeEntity blaze) {
        LivingEntity target = blaze.getTarget();
        if (!(target instanceof ServerPlayerEntity sp)) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        double dist = blaze.distanceTo(sp);
        if (dist > BLAZE_TARGET_RANGE) return;

        UUID id = blaze.getUuid();
        long now = world.getTime();
        Long lastFan = BLAZE_LAST_FAN.get(id);
        if (lastFan != null && now - lastFan < BLAZE_FAN_COOLDOWN) return;
        BLAZE_LAST_FAN.put(id, now);

        // Fire 3 small fireballs in a fan toward target.
        Vec3d toTarget = sp.getPos().add(0, sp.getStandingEyeHeight() * 0.5, 0)
            .subtract(blaze.getPos().add(0, blaze.getStandingEyeHeight(), 0)).normalize();
        Vec3d perpendicular = new Vec3d(-toTarget.z, 0, toTarget.x);

        for (int i = -1; i <= 1; i++) {
            Vec3d dir = toTarget.add(perpendicular.multiply(0.15 * i)).normalize();
            SmallFireballEntity ball = new SmallFireballEntity(world, blaze,
                new Vec3d(dir.x, dir.y, dir.z).multiply(1.0));
            ball.setPosition(blaze.getX(), blaze.getY() + 1.0, blaze.getZ());
            world.spawnEntity(ball);
        }

        world.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(),
            SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.9f);
    }

    // ===== GHAST =====

    private static void tickGhast(ServerWorld world, GhastEntity ghast) {
        UUID id = ghast.getUuid();
        if (IS_MINI_GHAST.contains(id)) return; // mini ghasts never summon
        if (GHAST_HAS_SUMMONED.contains(id)) return;
        if (ghast.getHealth() / ghast.getMaxHealth() > 0.5f) return;

        // Spawn one mini-ghast nearby.
        GhastEntity mini = EntityType.GHAST.create(world);
        if (mini == null) return;
        double dx = (RANDOM.nextDouble() - 0.5) * 6;
        double dz = (RANDOM.nextDouble() - 0.5) * 6;
        mini.refreshPositionAndAngles(ghast.getX() + dx, ghast.getY(), ghast.getZ() + dz, 0, 0);
        // Reduce HP via attribute — set max to 5 instead of 10.
        var hpAttr = mini.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(5.0);
        mini.setHealth(5.0f);
        world.spawnEntity(mini);

        IS_MINI_GHAST.add(mini.getUuid());
        GHAST_HAS_SUMMONED.add(id);

        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
            ghast.getX(), ghast.getY(), ghast.getZ(), 30, 1, 1, 1, 0.1);
        world.playSound(null, ghast.getX(), ghast.getY(), ghast.getZ(),
            SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 2.0f, 1.4f);
    }

    // ===== MAGMA CUBE =====

    private static void tickMagmaCube(ServerWorld world, MagmaCubeEntity mc) {
        if (mc.getSize() < 2) return; // only medium/large
        UUID id = mc.getUuid();
        boolean onGround = mc.isOnGround();
        Integer airTicks = MAGMA_AIRBORNE_TICKS.getOrDefault(id, 0);

        if (!onGround) {
            MAGMA_AIRBORNE_TICKS.put(id, airTicks + 1);
            return;
        }

        // Just landed — was it a meaningful fall?
        if (airTicks >= MAGMA_AIRBORNE_THRESHOLD) {
            doFieryPuffExplosion(world, mc);
        }
        MAGMA_AIRBORNE_TICKS.put(id, 0);
    }

    private static void doFieryPuffExplosion(ServerWorld world, MagmaCubeEntity mc) {
        // Visual + sound (no block damage).
        world.spawnParticles(ParticleTypes.EXPLOSION,
            mc.getX(), mc.getY() + 0.5, mc.getZ(), 5, 0.5, 0.3, 0.5, 0);
        world.spawnParticles(ParticleTypes.LAVA,
            mc.getX(), mc.getY() + 0.5, mc.getZ(), 20, 1, 0.5, 1, 0.2);
        world.playSound(null, mc.getX(), mc.getY(), mc.getZ(),
            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.2f, 1.3f);

        double radius = 2.5 + mc.getSize() * 0.5;
        var box = new net.minecraft.util.math.Box(
            mc.getX() - radius, mc.getY() - 1, mc.getZ() - radius,
            mc.getX() + radius, mc.getY() + 2, mc.getZ() + radius);
        for (Entity e : world.getOtherEntities(mc, box)) {
            if (e instanceof MagmaCubeEntity) continue;
            if (e instanceof LivingEntity living) {
                living.damage(world.getDamageSources().onFire(), 4.0f);
                living.setOnFireFor(4);
                Vec3d kb = e.getPos().subtract(mc.getPos()).normalize().multiply(0.6);
                e.setVelocity(e.getVelocity().add(kb.x, 0.4, kb.z));
                e.velocityModified = true;
            }
        }
    }

    // ===== PIGLIN BRUTE =====

    private static void tickBrute(ServerWorld world, PiglinBruteEntity brute) {
        if (brute.getHealth() / brute.getMaxHealth() > 0.3f) return;

        UUID id = brute.getUuid();
        long now = world.getTime();
        Long lastFrenzy = BRUTE_LAST_FRENZY.get(id);
        if (lastFrenzy != null && now - lastFrenzy < BRUTE_FRENZY_COOLDOWN) return;

        // Engage frenzy.
        brute.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.STRENGTH, 200, 1, false, true, true));
        brute.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.SPEED, 200, 0, false, true, true));
        BRUTE_LAST_FRENZY.put(id, now);

        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
            brute.getX(), brute.getY() + 2, brute.getZ(), 15, 0.5, 0.3, 0.5, 0.05);
        world.playSound(null, brute.getX(), brute.getY(), brute.getZ(),
            SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY, SoundCategory.HOSTILE, 1.5f, 0.7f);
    }
}
