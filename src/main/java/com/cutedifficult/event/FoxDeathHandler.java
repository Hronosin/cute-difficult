package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Random;

/**
 * Punishes the killing of kitsune.
 *
 * <p>v0.4.3 implements two consequences when a fox dies by player hand:
 * <ol>
 *   <li>The killer's element-Spirit drops by {@link #SPIRIT_PENALTY},
 *       and karma rises by {@link #KARMA_PENALTY} per tail (a Kyuubi
 *       kill is dramatically worse than a 1-tail).</li>
 *   <li>Every other fox within {@link #WITNESS_RADIUS} of the death
 *       resets the killer's trust with them to zero AND increments
 *       their {@code witnessedKills} counter. Mature kitsune remember.
 *       Their attack goals respect the witness counter: a witnessing
 *       fox attacks the killer regardless of prior trust.</li>
 * </ol>
 *
 * <p>The witness mechanic creates a chilling consequence chain: kill
 * one fox in a forest, and other foxes around suddenly view you as an
 * enemy even if they previously took offerings. Their flame, water,
 * lightning will be your problem until you find a way to atone
 * (future: Greater Penance ritual). For now, witnessing is permanent.
 */
public final class FoxDeathHandler {

    private static final double WITNESS_RADIUS = 16.0;
    private static final int SPIRIT_PENALTY = 10;
    private static final int KARMA_PENALTY = 5;

    private FoxDeathHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(victim instanceof FoxEntity fox)) return;
            handleFoxDeath(fox, source);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxDeathHandler registered.");
    }

    private static void handleFoxDeath(FoxEntity fox, DamageSource source) {
        if (!(fox.getWorld() instanceof ServerWorld world)) return;

        // Identify the player responsible, if any.
        ServerPlayerEntity killer = null;
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity le ? le : null;
        if (attacker instanceof ServerPlayerEntity sp) {
            killer = sp;
        } else if (source.getSource() instanceof ServerPlayerEntity sp) {
            killer = sp;
        }
        if (killer == null) return;

        Random random = new Random();
        KitsuneData victimData = FoxStorage.getOrCreate(fox, random);

        // 1) Penalize killer.
        MinecraftServer server = killer.getServer();
        if (server != null) {
            SpiritData.add(server, killer, victimData.element, -SPIRIT_PENALTY);
            SpiritData.addKarma(server, killer, KARMA_PENALTY * victimData.tails);
        }

        killer.sendMessage(
            Text.literal("You have slain a ")
                .append(Text.literal(victimData.element.kamiName() + " kitsune")
                    .formatted(victimData.element.color()))
                .append(Text.literal(" (" + victimData.tails + " tails). The other foxes saw."))
                .formatted(Formatting.DARK_RED, Formatting.ITALIC),
            false
        );

        // 2) Propagate to witnesses.
        Box witnessBox = new Box(
            fox.getX() - WITNESS_RADIUS, fox.getY() - WITNESS_RADIUS, fox.getZ() - WITNESS_RADIUS,
            fox.getX() + WITNESS_RADIUS, fox.getY() + WITNESS_RADIUS, fox.getZ() + WITNESS_RADIUS
        );
        List<FoxEntity> witnesses = world.getEntitiesByClass(FoxEntity.class, witnessBox,
            f -> f != fox && f.isAlive());

        for (FoxEntity witness : witnesses) {
            KitsuneData wData = FoxStorage.getOrCreate(witness, random);
            KitsuneData updated = KitsuneData.of6(
                wData.element,
                wData.personality,
                wData.tails,
                0, // trust reset to zero
                wData.lastFedTickStamp,
                wData.witnessedKills + 1
            );
            FoxStorage.store(witness, updated);

            // Visual cue: angry-villager particles burst from each witness.
            world.spawnParticles(
                ParticleTypes.ANGRY_VILLAGER,
                witness.getX(), witness.getY() + 0.7, witness.getZ(),
                10, 0.3, 0.3, 0.3, 0.1
            );
        }

        if (!witnesses.isEmpty()) {
            world.playSound(
                null, fox.getX(), fox.getY(), fox.getZ(),
                SoundEvents.ENTITY_FOX_SCREECH,
                SoundCategory.HOSTILE,
                1.5f, 0.6f
            );
            CuteDifficult.LOGGER.debug(
                "[CuteDifficult] {} witness foxes registered the killing.",
                witnesses.size()
            );
        }
    }
}
