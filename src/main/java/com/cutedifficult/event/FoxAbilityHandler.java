package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.FoxHostility;
import com.cutedifficult.spirit.FoxStats;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Combat retaliation + central elemental-ability dispatch.
 *
 * <p><b>v0.6.3 expansion:</b> each element now has THREE attack moves:
 * a basic single-target attack, an area-of-effect (AOE) attack, and a
 * piercing beam. {@link #castElementalAbility} randomly picks among them
 * (weighted toward basic), giving combat encounters more variety.
 *
 * <p>All attacks now go through {@link FoxHostility#canAttackWithLineOfSight}
 * before firing — no more wall-piercing damage, and friendly foxes never
 * attack regardless of which handler is calling.
 */
public final class FoxAbilityHandler {

    private static final WeakHashMap<UUID, Float> LAST_HP = new WeakHashMap<>();
    private static final WeakHashMap<UUID, Long> LAST_REACTIVE_CAST = new WeakHashMap<>();

    private static final double TARGET_RADIUS = 16.0;
    private static final int ABILITY_MIN_TAILS = 2;

    /** AOE radius for elemental burst attacks. */
    private static final double AOE_RADIUS = 4.0;

    /** Piercing beam: how far it extends past the target. */
    private static final double BEAM_RANGE = 14.0;
    /** Piercing beam: how wide the damage cone is (block radius around line). */
    private static final double BEAM_WIDTH = 1.0;

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
        KitsuneData data = FoxStorage.getOrCreate(fox, RANDOM);
        if (data.tails < ABILITY_MIN_TAILS) return;

        UUID id = fox.getUuid();
        float currentHp = fox.getHealth();
        Float previousHp = LAST_HP.get(id);
        LAST_HP.put(id, currentHp);

        if (previousHp == null) return;
        if (currentHp >= previousHp) return;

        long now = world.getTime();
        Long lastCast = LAST_REACTIVE_CAST.get(id);
        int cooldown = FoxStats.abilityCooldownTicks(data.tails);
        if (lastCast != null && now - lastCast < cooldown) return;

        ServerPlayerEntity target = (ServerPlayerEntity) world.getClosestPlayer(
            fox.getX(), fox.getY(), fox.getZ(),
            TARGET_RADIUS,
            p -> p instanceof ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator() && sp.isAlive()
        );
        if (target == null) return;

        if (!FoxHostility.canAttackWithLineOfSight(fox, target)) return;

        castElementalAbility(world, fox, data, target);
        LAST_REACTIVE_CAST.put(id, now);
    }

    /**
     * Public elemental dispatch. Called from this handler (reactive),
     * {@link FoxAggressionHandler} (proactive), and from the
     * {@link com.cutedifficult.entity.ai.KitsuneAttackGoal}.
     *
     * <p>Selects randomly among basic / AOE / beam variants, weighted
     * toward basic. Higher-tail foxes are MORE likely to use the
     * spectacular variants (AOE and beam).
     */
    public static void castElementalAbility(
        ServerWorld world, FoxEntity fox, KitsuneData data, ServerPlayerEntity target
    ) {
        // Final gate — never cast on a friendly/blocked target.
        if (!FoxHostility.canAttackWithLineOfSight(fox, target)) return;

        Element element = data.element;
        double power = data.tails / 9.0;

        // Roll attack variant. Weights:
        //   Basic: 60% always
        //   AOE: 25% (scaled up with tails)
        //   Beam: 15% (scaled up with tails)
        double roll = RANDOM.nextDouble();
        double aoeWeight = 0.25 * (0.5 + power * 0.5);
        double beamWeight = 0.15 * (0.5 + power * 0.5);

        AttackVariant variant;
        if (roll < beamWeight) variant = AttackVariant.BEAM;
        else if (roll < beamWeight + aoeWeight) variant = AttackVariant.AOE;
        else variant = AttackVariant.BASIC;

        switch (variant) {
            case BASIC -> castBasic(world, fox, target, element, power);
            case AOE -> castAoe(world, fox, target, element, power);
            case BEAM -> castBeam(world, fox, target, element, power);
        }

        world.playSound(
            null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_SCREECH,
            SoundCategory.HOSTILE,
            1.2f, 0.7f + (float) power * 0.5f
        );
    }

    private enum AttackVariant { BASIC, AOE, BEAM }

    // === BASIC (original single-target) ===

    private static void castBasic(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, Element element, double power) {
        switch (element) {
            case KASAI -> {
                drawLine(world, fox, target, ParticleTypes.FLAME, 16);
                target.damage(world.getDamageSources().onFire(), (float) (4.0 * power));
                target.setOnFireFor((int) Math.max(1, 3 * power));
            }
            case MIZU -> {
                drawLine(world, fox, target, ParticleTypes.SPLASH, 20);
                target.damage(world.getDamageSources().drown(), (float) (3.0 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(20, (int) (60 * power)), 1));
            }
            case DAICHI -> {
                Vec3d hitPos = target.getPos();
                world.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y + 0.3, hitPos.z, 30, 0.5, 0.8, 0.5, 0.4);
                target.damage(world.getDamageSources().generic(), (float) (5.0 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
            }
            case KAZE -> {
                Vec3d away = target.getPos().subtract(fox.getPos());
                double len = away.length();
                if (len < 0.01) away = new Vec3d(1, 0, 0); else away = away.multiply(1.0 / len);
                Vec3d kb = new Vec3d(away.x * 1.5 * power, 0.6 * power, away.z * 1.5 * power);
                target.setVelocity(target.getVelocity().add(kb));
                target.velocityModified = true;
                world.spawnParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1, target.getZ(), 16, 0.5, 0.5, 0.5, 0.1);
                target.damage(world.getDamageSources().generic(), (float) (2.0 * power));
            }
            case KAMINARI -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
                    world.spawnEntity(bolt);
                }
            }
            case MORI -> {
                drawLine(world, fox, target, ParticleTypes.HAPPY_VILLAGER, 16);
                target.damage(world.getDamageSources().generic(), (float) (2.0 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, Math.max(20, (int) (80 * power)), 1));
            }
            case KORI -> {
                drawLine(world, fox, target, ParticleTypes.SNOWFLAKE, 20);
                target.damage(world.getDamageSources().freeze(), (float) (3.5 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(20, (int) (80 * power)), 2));
            }
            case YUREI -> {
                Vec3d behindDir = target.getRotationVec(1.0f).multiply(-1).normalize();
                Vec3d teleportTo = target.getPos().add(behindDir.multiply(1.5));
                fox.refreshPositionAfterTeleport(teleportTo.x, teleportTo.y, teleportTo.z);
                for (int i = 0; i < 20; i++) {
                    world.spawnParticles(
                        new DustParticleEffect(new Vector3f(0.8f, 0.4f, 1.0f), 1.5f),
                        fox.getX() + (RANDOM.nextDouble() - 0.5) * 1.5,
                        fox.getY() + RANDOM.nextDouble() * 1.5,
                        fox.getZ() + (RANDOM.nextDouble() - 0.5) * 1.5,
                        1, 0, 0, 0, 0);
                }
                target.damage(world.getDamageSources().magic(), (float) (6.0 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, Math.max(20, (int) (100 * power)), 0));
            }
            case TENGOKU -> {
                drawLine(world, fox, target, ParticleTypes.END_ROD, 24);
                target.damage(world.getDamageSources().magic(), (float) (8.0 * power));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
                target.setOnFireFor((int) Math.max(1, 2 * power));
            }
        }
    }

    // === AOE (area-of-effect, damages all enemies in a radius around the target) ===

    private static void castAoe(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, Element element, double power) {
        Vec3d center = target.getPos();
        Box box = new Box(
            center.x - AOE_RADIUS, center.y - 2, center.z - AOE_RADIUS,
            center.x + AOE_RADIUS, center.y + 4, center.z + AOE_RADIUS
        );
        List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, box,
            e -> e != fox && e.isAlive() && e.distanceTo(target) <= AOE_RADIUS + 1);

        // Element-specific AOE visuals + effects.
        switch (element) {
            case KASAI -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.FLAME, 60);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().onFire(), (float) (6.0 * power));
                    v.setOnFireFor((int) Math.max(2, 5 * power));
                }
            }
            case MIZU -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.SPLASH, 80);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().drown(), (float) (4.0 * power));
                    if (v instanceof ServerPlayerEntity p) p.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
                }
            }
            case DAICHI -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.CRIT, 100);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().generic(), (float) (7.0 * power));
                    if (v instanceof ServerPlayerEntity p) p.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
                }
            }
            case KAZE -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.CLOUD, 80);
                for (LivingEntity v : victims) {
                    Vec3d away = v.getPos().subtract(center);
                    double len = away.length();
                    if (len > 0.01) {
                        Vec3d kb = away.multiply(1.5 * power / len);
                        v.setVelocity(v.getVelocity().add(kb.x, 0.8 * power, kb.z));
                        v.velocityModified = true;
                    }
                    v.damage(world.getDamageSources().generic(), (float) (3.0 * power));
                }
            }
            case KAMINARI -> {
                // Multiple lightning bolts in a circle around target.
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI / 2 + RANDOM.nextDouble() * 0.5;
                    double bx = center.x + Math.cos(angle) * AOE_RADIUS * 0.7;
                    double bz = center.z + Math.sin(angle) * AOE_RADIUS * 0.7;
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(bx, center.y, bz);
                        world.spawnEntity(bolt);
                    }
                }
            }
            case MORI -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.HAPPY_VILLAGER, 80);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().generic(), (float) (3.0 * power));
                    if (v instanceof ServerPlayerEntity p) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1));
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 100, 1));
                    }
                }
            }
            case KORI -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.SNOWFLAKE, 100);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().freeze(), (float) (5.0 * power));
                    if (v instanceof ServerPlayerEntity p) p.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
                }
            }
            case YUREI -> {
                spawnRing(world, center, AOE_RADIUS,
                    new DustParticleEffect(new Vector3f(0.8f, 0.4f, 1.0f), 1.5f), 100);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().magic(), (float) (8.0 * power));
                    if (v instanceof ServerPlayerEntity p) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
                    }
                }
            }
            case TENGOKU -> {
                spawnRing(world, center, AOE_RADIUS, ParticleTypes.END_ROD, 100);
                for (LivingEntity v : victims) {
                    v.damage(world.getDamageSources().magic(), (float) (10.0 * power));
                    v.setOnFireFor((int) Math.max(2, 3 * power));
                    if (v instanceof ServerPlayerEntity p) p.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
                }
            }
        }
    }

    // === BEAM (piercing line attack that hits multiple targets in line) ===

    private static void castBeam(ServerWorld world, FoxEntity fox, ServerPlayerEntity target, Element element, double power) {
        Vec3d from = fox.getPos().add(0, fox.getHeight() * 0.6, 0);
        Vec3d dir = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from).normalize();
        Vec3d to = from.add(dir.multiply(BEAM_RANGE));

        // Particle visualization.
        ParticleEffect beamParticle = beamParticleFor(element);
        int segments = 40;
        for (int i = 1; i <= segments; i++) {
            Vec3d p = from.add(dir.multiply(BEAM_RANGE * i / segments));
            world.spawnParticles(beamParticle, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }

        // Damage all living entities within BEAM_WIDTH of the beam line.
        Box scanBox = new Box(
            Math.min(from.x, to.x) - BEAM_WIDTH, Math.min(from.y, to.y) - BEAM_WIDTH, Math.min(from.z, to.z) - BEAM_WIDTH,
            Math.max(from.x, to.x) + BEAM_WIDTH, Math.max(from.y, to.y) + BEAM_WIDTH, Math.max(from.z, to.z) + BEAM_WIDTH
        );
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, scanBox, e -> e != fox && e.isAlive());
        for (LivingEntity entity : candidates) {
            Vec3d entityPos = entity.getPos().add(0, entity.getHeight() * 0.5, 0);
            // Distance from point to line.
            Vec3d toEntity = entityPos.subtract(from);
            double projection = toEntity.dotProduct(dir);
            if (projection < 0 || projection > BEAM_RANGE) continue;
            Vec3d closest = from.add(dir.multiply(projection));
            double distFromLine = entityPos.distanceTo(closest);
            if (distFromLine > BEAM_WIDTH) continue;

            applyBeamDamage(world, entity, element, power);
        }
    }

    private static void applyBeamDamage(ServerWorld world, LivingEntity victim, Element element, double power) {
        double dmg = 7.0 * power; // Beam hits hard but cooldown applies.
        switch (element) {
            case KASAI -> { victim.damage(world.getDamageSources().onFire(), (float) dmg); victim.setOnFireFor((int)(4 * power)); }
            case MIZU -> victim.damage(world.getDamageSources().drown(), (float) dmg);
            case DAICHI -> victim.damage(world.getDamageSources().generic(), (float)(dmg * 1.2));
            case KAZE -> victim.damage(world.getDamageSources().generic(), (float) dmg);
            case KAMINARI -> {
                victim.damage(world.getDamageSources().lightningBolt(), (float)(dmg * 1.1));
            }
            case MORI -> {
                victim.damage(world.getDamageSources().generic(), (float) dmg);
                if (victim instanceof ServerPlayerEntity p) p.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.POISON, 100, 2));
            }
            case KORI -> {
                victim.damage(world.getDamageSources().freeze(), (float) dmg);
                if (victim instanceof ServerPlayerEntity p) p.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3));
            }
            case YUREI -> {
                victim.damage(world.getDamageSources().magic(), (float)(dmg * 1.2));
                if (victim instanceof ServerPlayerEntity p) p.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.WITHER, 80, 0));
            }
            case TENGOKU -> {
                victim.damage(world.getDamageSources().magic(), (float)(dmg * 1.3));
                victim.setOnFireFor((int)(3 * power));
                if (victim instanceof ServerPlayerEntity p) p.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
            }
        }
    }

    private static ParticleEffect beamParticleFor(Element element) {
        return switch (element) {
            case KASAI -> ParticleTypes.FLAME;
            case MIZU -> ParticleTypes.SPLASH;
            case DAICHI -> ParticleTypes.CRIT;
            case KAZE -> ParticleTypes.CLOUD;
            case KAMINARI -> ParticleTypes.ELECTRIC_SPARK;
            case MORI -> ParticleTypes.HAPPY_VILLAGER;
            case KORI -> ParticleTypes.SNOWFLAKE;
            case YUREI -> new DustParticleEffect(new Vector3f(0.8f, 0.4f, 1.0f), 1.5f);
            case TENGOKU -> ParticleTypes.END_ROD;
        };
    }

    private static void spawnRing(ServerWorld world, Vec3d center, double radius, ParticleEffect particle, int count) {
        for (int i = 0; i < count; i++) {
            double angle = i * 2 * Math.PI / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            world.spawnParticles(particle, x, center.y + 0.5, z, 1, 0.1, 0.3, 0.1, 0.05);
        }
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
