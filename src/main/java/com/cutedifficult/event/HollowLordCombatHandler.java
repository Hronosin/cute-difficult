package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.HollowLordEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat AI for the (now custom, FlyingEntity-based) Hollow Lord. The boss bar
 * is owned by the entity; this handler only drives attacks on cooldowns. On
 * death it opens the End exit portal and drops a reward.
 */
public final class HollowLordCombatHandler {

    private static final Random RANDOM = new Random();
    private static final double BATTLE_RADIUS = 64.0;

    private static final Map<UUID, int[]> COOLDOWNS = new ConcurrentHashMap<>();
    // Active death sequences, ticked each server tick.
    private static final java.util.List<HollowLordDeathSequence> SEQUENCES =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<PendingLaser> PENDING_LASERS =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<PendingStrike> PENDING_STRIKES =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    // cooldown indices
    private static final int FIREBALL = 0, BREATH = 1, CURSE = 2, LASER = 3, LIGHTNING = 4;

    private HollowLordCombatHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Advance any active death sequences.
            if (!SEQUENCES.isEmpty()) {
                for (HollowLordDeathSequence seq : SEQUENCES) {
                    seq.tick();
                    if (seq.isFinished()) SEQUENCES.remove(seq);
                }
            }
            // Advance telegraphed lasers and lightning strikes.
            for (PendingLaser pl : PENDING_LASERS) {
                pl.tick();
                if (pl.done) PENDING_LASERS.remove(pl);
            }
            for (PendingStrike ps : PENDING_STRIKES) {
                ps.tick();
                if (ps.done) PENDING_STRIKES.remove(ps);
            }

            for (ServerWorld world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (!(entity instanceof HollowLordEntity lord) || !lord.isAlive()) continue;

                    // Scripted death: once HP drops low, freeze the Lord and play
                    // the cinematic sequence (rather than relying on AFTER_DEATH,
                    // where the entity is already gone and timing breaks).
                    if (!lord.isDying() && lord.getHealth() <= 10.0f) {
                        startScriptedDeath(world, lord);
                        continue;
                    }
                    if (lord.isDying()) continue; // frozen; sequence handles it

                    if (lord.getSpawnInvuln() > 0) continue;
                    tickCombat(world, lord);
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] HollowLordCombatHandler registered.");
    }

    /** Begin the scripted death: freeze the Lord, lift it to centre, start the
     *  cinematic sequence. The sequence removes the Lord when it finishes. */
    private static void startScriptedDeath(ServerWorld world, HollowLordEntity lord) {
        COOLDOWNS.remove(lord.getUuid());
        lord.setDying(true);
        lord.setHealth(1.0f);              // keep it alive but pinned
        lord.setNoGravity(true);
        lord.setVelocity(0, 0, 0);
        lord.velocityModified = true;
        // Teleport to the arena centre, lifted, for the animation.
        lord.requestTeleport(0.5, 70, 0.5);
        SEQUENCES.add(new HollowLordDeathSequence(world, lord, new Vec3d(0.5, 70, 0.5)));
        CuteDifficult.LOGGER.info("[CuteDifficult] Hollow Lord scripted death started.");
    }

    private static void tickCombat(ServerWorld world, HollowLordEntity lord) {
        int[] cd = COOLDOWNS.computeIfAbsent(lord.getUuid(), u -> new int[]{40, 80, 120, 160, 200});
        for (int i = 0; i < cd.length; i++) if (cd[i] > 0) cd[i]--;

        ServerPlayerEntity target = nearestTarget(world, lord);
        if (target == null) return;

        Vec3d origin = lord.getPos().add(0, lord.getHeight() * 0.5, 0);
        Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0).subtract(origin);
        double dist = toTarget.length();
        Vec3d dir = toTarget.normalize();

        if (cd[FIREBALL] <= 0) {
            launchFireball(world, lord, origin, dir);
            cd[FIREBALL] = 60 + RANDOM.nextInt(20);
        }
        if (cd[BREATH] <= 0 && dist < 30) {
            fireBreath(world, lord, origin, dir, target);
            cd[BREATH] = 100 + RANDOM.nextInt(40);
        }
        if (cd[CURSE] <= 0) {
            hollowCurse(world, target);
            cd[CURSE] = 140 + RANDOM.nextInt(60);
        }

        int phase = lord.getPhase();

        // Phase 2+: telegraphed laser. We "aim" for a moment (shows the line),
        // then fire — giving the player time to break line of sight or dodge.
        if (phase >= 2 && cd[LASER] <= 0) {
            beginLaser(world, lord, origin, target);
            cd[LASER] = 120 + RANDOM.nextInt(40);
        }

        // Phase 2+: area lightning. Telegraphed strike markers appear, then
        // lightning falls a moment later at those spots.
        if (phase >= 2 && cd[LIGHTNING] <= 0) {
            beginLightning(world, target);
            cd[LIGHTNING] = 160 + RANDOM.nextInt(60);
        }

        // Crystal turrets fire at the player every so often (phase-independent,
        // but only the crystals that still exist).
        if (world.getTime() % 40 == 0) {
            crystalTurretsFire(world, target);
        }
    }

    // ===== Telegraphed laser =====

    /** Aim a laser at the target: draw the beam path as a warning for ~1s, then
     *  schedule the damaging strike. Implemented as a short scheduled task via a
     *  pending-laser list ticked alongside combat. */
    private static void beginLaser(ServerWorld world, HollowLordEntity lord, Vec3d origin, ServerPlayerEntity target) {
        Vec3d aim = target.getPos().add(0, target.getHeight() / 2, 0);
        PENDING_LASERS.add(new PendingLaser(world, lord, origin, aim, 25));
        world.playSound(null, lord.getBlockPos(),
            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 1.6f);
    }

    // ===== Telegraphed lightning =====

    private static void beginLightning(ServerWorld world, ServerPlayerEntity target) {
        // Mark 3 spots around the target; lightning falls after a telegraph.
        for (int i = 0; i < 3; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 8;
            double oz = (RANDOM.nextDouble() - 0.5) * 8;
            BlockPos spot = BlockPos.ofFloored(target.getX() + ox, target.getY(), target.getZ() + oz);
            PENDING_STRIKES.add(new PendingStrike(world, spot, 30));
        }
    }

    // ===== Crystal turrets =====

    private static void crystalTurretsFire(ServerWorld world, ServerPlayerEntity target) {
        var crystals = world.getEntitiesByClass(
            net.minecraft.entity.decoration.EndCrystalEntity.class,
            target.getBoundingBox().expand(80), c -> true);
        for (var crystal : crystals) {
            Vec3d from = crystal.getPos().add(0, 1, 0);
            Vec3d to = target.getPos().add(0, target.getHeight() / 2, 0);
            Vec3d dir = to.subtract(from).normalize();
            // Small fireball turret shot. Owner = the crystal (a LivingEntity is
            // not required for SmallFireball's velocity-only constructor path,
            // but the proven ctor takes an owner; use the target's nearest Lord
            // is overkill — pass null-safe via the (world, x,y,z, vx,vy,vz) ctor).
            net.minecraft.entity.projectile.SmallFireballEntity shot =
                new net.minecraft.entity.projectile.SmallFireballEntity(world,
                    from.x, from.y, from.z, dir);
            world.spawnEntity(shot);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                from.x, from.y, from.z, 5, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private static ServerPlayerEntity nearestTarget(ServerWorld world, HollowLordEntity lord) {
        ServerPlayerEntity best = null;
        double bestSq = BATTLE_RADIUS * BATTLE_RADIUS;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            if (!p.isAlive() || p.isDead()) continue;
            double d = p.squaredDistanceTo(lord);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }

    private static void launchFireball(ServerWorld world, HollowLordEntity lord, Vec3d origin, Vec3d dir) {
        FireballEntity fb = new FireballEntity(world, lord, dir.multiply(0.1), 2);
        fb.setPosition(origin.x + dir.x * 3, origin.y + dir.y * 3, origin.z + dir.z * 3);
        world.spawnEntity(fb);
        world.playSound(null, lord.getBlockPos(),
            SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.6f);
    }

    private static void fireBreath(ServerWorld world, HollowLordEntity lord, Vec3d origin, Vec3d dir,
                                   ServerPlayerEntity target) {
        for (double d = 1; d < 25; d += 0.6) {
            Vec3d p = origin.add(dir.multiply(d));
            double spread = d * 0.06;
            world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, spread, spread, spread, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, spread, spread, spread, 0.0);
        }
        Vec3d toT = target.getPos().add(0, target.getHeight() / 2, 0).subtract(origin).normalize();
        if (toT.dotProduct(dir) > 0.85) {
            target.setOnFireFor(5);
            target.damage(world.getDamageSources().mobAttack(lord), 6.0f);
        }
        world.playSound(null, lord.getBlockPos(),
            SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.5f);
    }

    private static void hollowCurse(ServerWorld world, ServerPlayerEntity target) {
        StatusEffectInstance effect = switch (RANDOM.nextInt(4)) {
            case 0 -> new StatusEffectInstance(StatusEffects.WITHER, 100, 0, false, true, true);
            case 1 -> new StatusEffectInstance(StatusEffects.POISON, 120, 0, false, true, true);
            case 2 -> new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 1, false, true, true);
            default -> new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, true, true);
        };
        target.addStatusEffect(effect);
        world.spawnParticles(ParticleTypes.SCULK_SOUL,
            target.getX(), target.getY() + 1, target.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
        world.playSound(null, target.getBlockPos(),
            SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.8f);
    }

    /**
     * A telegraphed laser: for {@code warn} ticks it draws a thin beam line as a
     * warning (no damage), then on the final tick it fires — damaging only if
     * the player is still on the line. Lets the player dodge / break LoS.
     */
    private static final class PendingLaser {
        final ServerWorld world;
        final HollowLordEntity lord;
        final Vec3d origin;
        final Vec3d aim;
        int warn;
        boolean done = false;

        PendingLaser(ServerWorld world, HollowLordEntity lord, Vec3d origin, Vec3d aim, int warn) {
            this.world = world; this.lord = lord; this.origin = origin; this.aim = aim; this.warn = warn;
        }

        void tick() {
            if (!lord.isAlive()) { done = true; return; }
            Vec3d dir = aim.subtract(origin).normalize();
            for (double d = 1; d < 40; d += 0.8) {
                Vec3d p = origin.add(dir.multiply(d));
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
            }
            warn--;
            if (warn <= 0) {
                fire(dir);
                done = true;
            }
        }

        private void fire(Vec3d dir) {
            for (double d = 1; d < 40; d += 0.5) {
                Vec3d p = origin.add(dir.multiply(d));
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                    p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            }
            for (ServerPlayerEntity pl : world.getPlayers()) {
                if (pl.isCreative() || pl.isSpectator()) continue;
                Vec3d toPlayer = pl.getPos().add(0, pl.getHeight() / 2, 0).subtract(origin);
                double along = toPlayer.dotProduct(dir);
                if (along < 0 || along > 40) continue;
                Vec3d closest = origin.add(dir.multiply(along));
                double perp = pl.getPos().add(0, pl.getHeight() / 2, 0).distanceTo(closest);
                if (perp < 2.0) {
                    pl.damage(world.getDamageSources().magic(), 10.0f);
                }
            }
            world.playSound(null, BlockPos.ofFloored(origin),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.0f, 0.9f);
        }
    }

    /**
     * A telegraphed lightning strike: shows particle markers at the spot for
     * {@code warn} ticks, then summons a lightning bolt — dodgeable by leaving.
     */
    private static final class PendingStrike {
        final ServerWorld world;
        final BlockPos pos;
        int warn;
        boolean done = false;

        PendingStrike(ServerWorld world, BlockPos pos, int warn) {
            this.world = world; this.pos = pos; this.warn = warn;
        }

        void tick() {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 6, 0.6, 0.1, 0.6, 0.0);
            warn--;
            if (warn <= 0) {
                var bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    world.spawnEntity(bolt);
                }
                done = true;
            }
        }
    }

}
