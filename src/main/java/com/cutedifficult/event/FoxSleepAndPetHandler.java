package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.Random;

/**
 * Two milestones of the friendship arc:
 *
 * <p><b>Sleeping pose:</b> at night and with trust ≥ {@link #SLEEP_TRUST},
 * a friendly kitsune curls up near the player. Implementation: forces
 * the sitting flag at night for eligible foxes, with little "zzz" smoke
 * particles above them periodically.
 *
 * <p><b>Petting:</b> right-click an eligible fox with an empty hand to
 * give it a pet. Each pet grants +1 trust, but only one per
 * {@link #PET_COOLDOWN_TICKS} per fox to keep it from being a grind
 * farm. Particles + a soft ambient sound.
 *
 * <p>The cooldown is per-fox (stored in KitsuneData.lastPettedTickStamp),
 * not per-player, so multiple players can pet the same fox without
 * stepping on each other but each fox limits its own affection budget.
 */
public final class FoxSleepAndPetHandler {

    private static final int SLEEP_TRUST = 50;
    /** 24000 ticks per Minecraft day; night is roughly 13000-23000. */
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;

    private static final int PET_COOLDOWN_TICKS = 72000; // 1 hour real time
    private static final int PET_TRUST_GAIN = 1;

    /** How close the player must be for the fox to consider sleeping. */
    private static final double SLEEP_PLAYER_DISTANCE = 6.0;

    private static final Random RANDOM = new Random();

    private FoxSleepAndPetHandler() {}

    public static void register() {
        // Tick: manage sleeping pose for eligible foxes.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            for (ServerWorld world : server.getWorlds()) {
                long timeOfDay = world.getTimeOfDay() % 24000;
                boolean isNight = timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END;
                if (!isNight) continue;
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        tickSleep(world, fox);
                    }
                }
            }
        });

        // Petting: empty-hand right-click on eligible fox.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;

            return handlePet(serverWorld, sp, fox);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxSleepAndPetHandler registered.");
    }

    private static void tickSleep(ServerWorld world, FoxEntity fox) {
        KitsuneData data = FoxStorage.peekCache(fox);
        if (data == null || data.trustLevel < SLEEP_TRUST) {
            return;
        }
        // Is a friendly player nearby?
        var nearest = world.getClosestPlayer(fox.getX(), fox.getY(), fox.getZ(),
            SLEEP_PLAYER_DISTANCE,
            p -> p instanceof ServerPlayerEntity sp && !sp.isSpectator());
        if (nearest == null) return;

        // Set the sitting flag to use vanilla sleeping-fox pose.
        if (!fox.isSitting()) {
            fox.setSitting(true);
        }
        fox.getNavigation().stop();

        // Occasionally spawn zzz smoke particles above the fox.
        if (world.getTime() % 30 == 0 && RANDOM.nextInt(3) == 0) {
            world.spawnParticles(ParticleTypes.POOF,
                fox.getX(), fox.getY() + 0.8, fox.getZ(),
                1, 0.05, 0.1, 0.05, 0.01);
        }
    }

    private static ActionResult handlePet(ServerWorld world, ServerPlayerEntity player, FoxEntity fox) {
        KitsuneData data = FoxStorage.getOrCreate(fox, RANDOM);
        // Only pet eligible foxes — must have some baseline trust to enjoy it.
        if (data.trustLevel < 30) {
            player.sendMessage(
                Text.literal("The kitsune flinches away. You haven't earned its trust.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                true
            );
            return ActionResult.FAIL;
        }

        long now = world.getTime();
        if (now - data.lastPettedTickStamp < PET_COOLDOWN_TICKS) {
            // Show particles anyway for the cuddle feeling, but no trust gain.
            world.spawnParticles(ParticleTypes.HEART,
                fox.getX(), fox.getY() + 0.6, fox.getZ(),
                3, 0.2, 0.2, 0.2, 0.05);
            return ActionResult.SUCCESS;
        }

        // Apply pet.
        KitsuneData updated = data.withTrust(data.trustLevel + PET_TRUST_GAIN)
                               .withLastPetted(now);
        FoxStorage.store(fox, updated);

        world.spawnParticles(ParticleTypes.HEART,
            fox.getX(), fox.getY() + 0.8, fox.getZ(),
            5, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.NEUTRAL, 0.7f, 1.5f);

        String displayName = data.customName.isEmpty()
            ? data.element.kamiName() + " kitsune"
            : data.customName;
        player.sendMessage(
            Text.literal("You pet ")
                .formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(displayName).formatted(data.element.color()))
                .append(Text.literal(". It accepts gladly.").formatted(Formatting.LIGHT_PURPLE)),
            true
        );
        return ActionResult.SUCCESS;
    }
}
