package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxStats;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Reactive combat retaliation: when a fox loses HP this tick (= someone
 * hit it), it casts an element-themed ability at the nearest player.
 *
 * <p><b>v0.3.6 refactor:</b> the elemental dispatch is now exposed as
 * {@link #castElementalAbility} so {@link FoxAggressionHandler} can
 * invoke it for proactive casts. Cooldown enforcement still happens
 * per-handler — both handlers track their own per-fox LAST_CAST maps —
 * to avoid them stepping on each other.
 */
public final class FoxAbilityHandler {

    private static final WeakHashMap<UUID, Float> LAST_HP = new WeakHashMap<>();
    private static final WeakHashMap<UUID, Long> LAST_REACTIVE_CAST = new WeakHashMap<>();

    private static final double TARGET_RADIUS = 16.0;
    private static final int ABILITY_MIN_TAILS = 2;

    private static final Random RANDOM = new Random();

    private FoxAbilityHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        tickFox(world, fox);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxAbilityHandler registered.");
    }

    private static void tickFox(ServerWorld world, FoxEntity fox) {
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        if (data.tails() < ABILITY_MIN_TAILS) return;

        UUID id = fox.getUuid();
        float currentHp = fox.getHealth();
        Float previousHp = LAST_HP.get(id);
        LAST_HP.put(id, currentHp);

        if (previousHp == null) return;
        if (currentHp >= previousHp) return;

        long now = world.getTime();
        Long lastCast = LAST_REACTIVE_CAST.get(id);
        int cooldown = FoxStats.abilityCooldownTicks(data.tails());
        if (lastCast != null && now - lastCast < cooldown) return;

        ServerPlayerEntity target = (ServerPlayerEntity) world.getClosestPlayer(
            fox.getX(), fox.getY(), fox.getZ(),
            TARGET_RADIUS,
            p -> p instanceof ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator() && sp.isAlive()
        );
        if (target == null) return;

        castElementalAbility(world, fox, data, target);
        LAST_REACTIVE_CAST.put(id, now);
    }

    /**
     * Public elemental dispatch. Called from this handler (reactive) and
     * from {@link FoxAggressionHandler} (proactive). Plays the screech
     * sound at the fox, then performs the per-element attack on the target.
     */
    public static void castElementalAbility(
        ServerWorld world,
        FoxEntity fox,
        FoxData data,
        ServerPlayerEntity target
    ) {
        Element element = data.element();
        double power = data.tails() / 9.0;

        switch (element) {
            case KASAI    -> castFireball(world, fox, target, power);
            case MIZU     -> castWaterJet(world, fox, target, power);
            case DAICHI   -> castStoneSpike(world, fox, target, power);
            case KAZE     -> castWindPush(world, fox, target, power);
            case KAMINARI -> castLightning(world, target);
            case MORI     -> castPoisonThorns(world, fox, target, power);
            case KORI     -> castIceShard(world, fox, target, power);
            case YUREI    -> castPhantomStrike(world, fox, target, power);
            case TENGOKU  -> castSolarBeam(world, fox, target, power);
        }

        world.playSound(
            null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_SCREECH,
            SoundCategory.HOSTILE,
            1.2f, 0.7f + (float) power * 0.5f
        );
    }

    private static void castFireball(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        drawLine(world, fox, target, ParticleTypes.FLAME, 16);
        target.damage(world.getDamageSources().onFire(), (float) (4.0 * power));
        target.setOnFireFor((int) Math.max(1, 3 * power));
    }

    private static void castWaterJet(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        drawLine(world, fox, target, ParticleTypes.SPLASH, 20);
        target.damage(world.getDamageSources().drown(), (float) (3.0 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(20, (int) (60 * power)), 1));
    }

    private static void castStoneSpike(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        Vec3d hitPos = target.getPos();
        world.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y + 0.3, hitPos.z, 30, 0.5, 0.8, 0.5, 0.4);
        world.spawnParticles(ParticleTypes.SMOKE, hitPos.x, hitPos.y + 0.1, hitPos.z, 15, 0.4, 0.1, 0.4, 0.05);
        target.damage(world.getDamageSources().generic(), (float) (5.0 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
    }

    private static void castWindPush(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        Vec3d away = target.getPos().subtract(fox.getPos());
        double len = away.length();
        if (len < 0.01) away = new Vec3d(1, 0, 0);
        else away = away.multiply(1.0 / len);
        Vec3d kb = new Vec3d(away.x * 1.5 * power, 0.6 * power, away.z * 1.5 * power);
        target.setVelocity(target.getVelocity().add(kb));
        target.velocityModified = true;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            double dx = Math.cos(angle) * 1.5;
            double dz = Math.sin(angle) * 1.5;
            world.spawnParticles(ParticleTypes.CLOUD, fox.getX() + dx, fox.getY() + 0.5, fox.getZ() + dz, 2, 0.1, 0.1, 0.1, 0.05);
        }
        target.damage(world.getDamageSources().generic(), (float) (2.0 * power));
    }

    private static void castLightning(ServerWorld world, ServerPlayerEntity target) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
            world.spawnEntity(bolt);
        }
    }

    private static void castPoisonThorns(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        drawLine(world, fox, target, ParticleTypes.HAPPY_VILLAGER, 16);
        target.damage(world.getDamageSources().generic(), (float) (2.0 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, Math.max(20, (int) (80 * power)), 1));
    }

    private static void castIceShard(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        drawLine(world, fox, target, ParticleTypes.SNOWFLAKE, 20);
        target.damage(world.getDamageSources().freeze(), (float) (3.5 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(20, (int) (80 * power)), 2));
    }

    private static void castPhantomStrike(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        Vec3d behindDir = target.getRotationVec(1.0f).multiply(-1).normalize();
        Vec3d teleportTo = target.getPos().add(behindDir.multiply(1.5));
        fox.refreshPositionAfterTeleport(teleportTo.x, teleportTo.y, teleportTo.z);
        for (int i = 0; i < 20; i++) {
            world.spawnParticles(
                new DustParticleEffect(new Vector3f(0.8f, 0.4f, 1.0f), 1.5f),
                fox.getX() + (RANDOM.nextDouble() - 0.5) * 1.5,
                fox.getY() + RANDOM.nextDouble() * 1.5,
                fox.getZ() + (RANDOM.nextDouble() - 0.5) * 1.5,
                1, 0, 0, 0, 0
            );
        }
        target.damage(world.getDamageSources().magic(), (float) (6.0 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, Math.max(20, (int) (100 * power)), 0));
    }

    private static void castSolarBeam(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, double power) {
        drawLine(world, fox, target, ParticleTypes.END_ROD, 24);
        target.damage(world.getDamageSources().magic(), (float) (8.0 * power));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
        target.setOnFireFor((int) Math.max(1, 2 * power));
    }

    private static void drawLine(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, ParticleEffect particle, int segments) {
        Vec3d from = fox.getPos().add(0, fox.getHeight() * 0.6, 0);
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d step = to.subtract(from).multiply(1.0 / segments);
        for (int i = 1; i <= segments; i++) {
            Vec3d point = from.add(step.multiply(i));
            world.spawnParticles(particle, point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }
}
