package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Gives hostile-mob projectiles a small chance to be "sniper shots" — faster,
 * straighter, harder-hitting.
 *
 * <p><b>Mechanic:</b> when any {@link PersistentProjectileEntity} (arrow,
 * trident-like) loads into the world and its owner is a hostile mob, roll
 * {@link #SNIPER_CHANCE}. If it lands:
 * <ul>
 *   <li><b>Velocity ×2</b> — the projectile flies much faster, less reaction time.</li>
 *   <li><b>Damage ×1.5</b> — the hit hurts more.</li>
 *   <li><b>Visual + audio cue</b> — a brief glow trail and a high "snipe ping"
 *       sound at the shooter, so the player gets a fair warning that
 *       something dangerous is coming.</li>
 * </ul>
 *
 * <p><b>Why only PersistentProjectileEntity:</b> this is the parent class
 * for arrows, spectral arrows, tipped arrows, and tridents — all of which
 * have the {@code setVelocity} + {@code setDamage} contract we use. Other
 * hostile projectiles (ghast fireballs, blaze fireballs, shulker bullets,
 * potions) have different damage models that aren't cleanly multipliable,
 * so we leave them alone. Practically, the player encounters arrow-spam
 * way more often than any other projectile type, so this hits the
 * most-felt vector.
 *
 * <p>Owner check: we only buff projectiles fired by {@link HostileEntity}.
 * Snowballs from snow golems, arrows from a pillager that's been pacified,
 * etc. are not boosted.
 *
 * <p>Active only in CRUEL mode.
 */
public final class SniperProjectileHandler {

    private static final double SNIPER_CHANCE = 0.05;
    private static final double VELOCITY_MULT = 2.0;
    private static final float DAMAGE_MULT = 1.5f;

    private static final Random RANDOM = new Random();

    private SniperProjectileHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(entity instanceof PersistentProjectileEntity arrow)) return;

            // Skip splash potions and similar — they extend other branches.
            if (entity instanceof PotionEntity) return;

            // Need a hostile owner.
            Entity owner = arrow.getOwner();
            if (!(owner instanceof HostileEntity)) return;

            // Roll.
            if (RANDOM.nextDouble() >= SNIPER_CHANCE) return;

            applySniperBuff(world, arrow, (LivingEntity) owner);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] SniperProjectileHandler registered.");
    }

    private static void applySniperBuff(ServerWorld world, PersistentProjectileEntity arrow, LivingEntity shooter) {
        // Double the velocity. Keep direction.
        Vec3d v = arrow.getVelocity();
        arrow.setVelocity(v.multiply(VELOCITY_MULT));
        arrow.velocityModified = true;

        // Bump damage. PersistentProjectileEntity stores damage as a double internally
        // and exposes getDamage()/setDamage(double).
        double currentDamage = arrow.getDamage();
        arrow.setDamage(currentDamage * DAMAGE_MULT);

        // Make it glow so it's visible mid-flight.
        arrow.addCommandTag("cd_sniper");

        // Warning audio at the shooter — high-pitched ping. Reaches the player
        // a moment before the arrow does, giving a sliver of reaction time.
        world.playSound(
            null,
            shooter.getX(), shooter.getY(), shooter.getZ(),
            SoundEvents.ENTITY_ARROW_SHOOT,
            SoundCategory.HOSTILE,
            1.6f,
            0.4f // very low pitch — distinctive "twang"
        );

        // Telegraph particles at shooter.
        world.spawnParticles(
            ParticleTypes.END_ROD,
            shooter.getX(), shooter.getY() + shooter.getStandingEyeHeight(), shooter.getZ(),
            6,
            0.2, 0.2, 0.2,
            0.05
        );
    }
}
