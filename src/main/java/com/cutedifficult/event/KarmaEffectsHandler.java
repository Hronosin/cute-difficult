package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.spirit.SpiritData.KarmaTier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Kegare — ritual pollution. The consequences of carrying Karma. The higher the
 * tier, the heavier the world leans against you:
 *
 * <ul>
 *   <li><b>TAINTED (50+)</b> — faint dark particles; occasional bad luck.</li>
 *   <li><b>DEFILED (100+)</b> — passive Unluck; hostile mobs drawn to you;
 *       blessings suppressed (handled where blessings apply).</li>
 *   <li><b>CURSED (150+)</b> — Unluck + Weakness; frequent mob pressure; the
 *       air itself recoils (heavier particles, ambient dread sound).</li>
 * </ul>
 *
 * <p>Offering failure, item-quality penalties, kitsune hatred, and weather/moon
 * bias are applied in their own systems by reading {@link SpiritData#karmaTier};
 * this handler owns the per-tick personal afflictions and the mob pressure.
 */
public final class KarmaEffectsHandler {

    private static long tick = 0;

    private KarmaEffectsHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 40 != 0) return; // twice/sec is plenty for status upkeep
            MinecraftServer srv = server;
            for (ServerPlayerEntity player : srv.getPlayerManager().getPlayerList()) {
                if (player.isSpectator() || player.isCreative()) continue;
                KarmaTier tier = SpiritData.karmaTier(srv, player);
                if (tier == KarmaTier.PURE) continue;
                applyAfflictions(player, tier);
                if (player.getWorld() instanceof ServerWorld sw) {
                    ambient(sw, player, tier);
                }
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] KarmaEffectsHandler registered.");
    }

    private static void applyAfflictions(ServerPlayerEntity player, KarmaTier tier) {
        // Status durations slightly longer than the 40-tick cadence so they
        // don't visibly flicker (60 ticks).
        switch (tier) {
            case TAINTED -> {
                // Mild: occasional unluck.
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.UNLUCK, 60, 0, true, false, false));
            }
            case DEFILED -> {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.UNLUCK, 60, 1, true, false, false));
            }
            case CURSED -> {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.UNLUCK, 60, 2, true, false, false));
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS, 60, 0, true, false, false));
            }
            default -> {}
        }
    }

    private static void ambient(ServerWorld world, ServerPlayerEntity player, KarmaTier tier) {
        int count = switch (tier) {
            case TAINTED -> 2;
            case DEFILED -> 5;
            case CURSED -> 10;
            default -> 0;
        };
        if (count > 0) {
            world.spawnParticles(player, ParticleTypes.SCULK_SOUL, false,
                player.getX(), player.getY() + 1.0, player.getZ(),
                count, 0.4, 0.6, 0.4, 0.01);
        }
        if (tier == KarmaTier.CURSED && tick % 200 == 0) {
            world.playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.AMBIENT_CAVE.value(),
                net.minecraft.sound.SoundCategory.AMBIENT, 0.6f, 0.5f);
        }
    }

    /** Mob-pressure multiplier for spawn logic to read (1.0 = normal). */
    public static double mobPressure(KarmaTier tier) {
        return switch (tier) {
            case PURE -> 1.0;
            case TAINTED -> 1.15;
            case DEFILED -> 1.4;
            case CURSED -> 1.8;
        };
    }
}
