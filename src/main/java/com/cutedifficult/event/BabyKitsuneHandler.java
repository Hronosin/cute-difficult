package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.entity.ModEntities;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.FoxPersonality;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Baby kitsune (kits) — both spawn and mourning logic.
 *
 * <p><b>Spawn:</b> when a kitsune is loaded with trust ≥ {@link #BABY_TRUST_THRESHOLD},
 * there's a {@link #BABY_SPAWN_CHANCE}% chance per fox-per-load to spawn
 * a baby kitsune at her side. The baby inherits the parent's element
 * and a copy of her personality (but with reduced trauma — kits are
 * trauma-free until life teaches them).
 *
 * <p>Baby kitsune use vanilla fox baby age (-24000), which gives the
 * smaller scaled model. They grow up naturally over 1 in-game day
 * because we leave vanilla's age-progression alone.
 *
 * <p><b>Mourning:</b> when an adult kitsune dies, any baby kitsune
 * within {@link #MOURNING_RADIUS} sit at her body. They emit blue
 * "tears" particles for {@link #MOURNING_TICKS} (30 seconds), then
 * stand up and flee. This is a visual-only thing — no gameplay impact —
 * but the emotional weight matters.
 *
 * <p>To avoid exponentially repeating spawns on chunk-loads, we track
 * "babies spawned for this parent" using a set keyed by parent UUID,
 * which lives for the server session. New mothers in fresh chunks
 * always roll the chance, but the same loaded mother won't spawn
 * additional babies on subsequent loads.
 */
public final class BabyKitsuneHandler {

    private static final int BABY_TRUST_THRESHOLD = 50;
    private static final int BABY_SPAWN_CHANCE = 30; // percent

    private static final double MOURNING_RADIUS = 8.0;
    private static final int MOURNING_TICKS = 600; // 30s

    /** Parent UUIDs we've already rolled — prevents repeat rolls. */
    private static final java.util.Set<UUID> ALREADY_ROLLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Active mourners: baby UUID → ticks remaining. */
    private static final Map<UUID, Integer> MOURNERS = new HashMap<>();

    private static final Random RANDOM = new Random();

    private BabyKitsuneHandler() {}

    public static void register() {
        // Per-load chance to spawn a baby for an eligible adult.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(entity instanceof KitsuneEntity adult)) return;
            if (adult.isBaby()) return;
            UUID id = adult.getUuid();
            if (ALREADY_ROLLED.contains(id)) return;
            ALREADY_ROLLED.add(id);

            KitsuneData data = FoxStorage.peekCache(adult);
            if (data == null || data.trustLevel < BABY_TRUST_THRESHOLD) return;
            if (RANDOM.nextInt(100) >= BABY_SPAWN_CHANCE) return;

            spawnBaby(world, adult, data);
        });

        // When an adult kitsune dies, mark nearby babies as mourners.
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(victim instanceof KitsuneEntity dead)) return;
            if (dead.isBaby()) return;
            if (!(dead.getWorld() instanceof ServerWorld world)) return;
            markMourners(world, dead);
        });

        // Tick mourners: emit tears, decrement, eventually free them.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (MOURNERS.isEmpty()) return;
            var it = MOURNERS.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    it.remove();
                    continue;
                }
                entry.setValue(remaining);
                emitTears(server, entry.getKey());
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] BabyKitsuneHandler registered.");
    }

    private static void spawnBaby(ServerWorld world, KitsuneEntity adult, KitsuneData adultData) {
        KitsuneEntity baby = ModEntities.KITSUNE.create(world);
        if (baby == null) return;

        double dx = (RANDOM.nextDouble() - 0.5) * 2;
        double dz = (RANDOM.nextDouble() - 0.5) * 2;
        baby.refreshPositionAndAngles(adult.getX() + dx, adult.getY(), adult.getZ() + dz,
            adult.getYaw(), 0);
        baby.setBreedingAge(-24000); // 1 in-game day to grow up
        world.spawnEntity(baby);

        // Inherit parent's element + personality (with reduced trauma).
        FoxPersonality parentPers = adultData.personality;
        FoxPersonality babyPers = new FoxPersonality(
            parentPers.pride(),
            Math.min(100, parentPers.trust() + 10), // kits are trustful
            Math.min(100, parentPers.curiosity() + 15), // and curious
            parentPers.memory(),
            parentPers.greed(),
            parentPers.sensitivity(),
            Math.max(0, parentPers.trauma() - 30) // less trauma
        );
        KitsuneData babyData = new KitsuneData(
            adultData.element,
            babyPers,
            1, // start with 1 tail
            0, 0L, 0, "", 0L
        );
        FoxStorage.injectIntoCache(baby, babyData);

        world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
            baby.getX(), baby.getY() + 0.5, baby.getZ(),
            5, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, baby.getX(), baby.getY(), baby.getZ(),
            SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.6f);
    }

    private static void markMourners(ServerWorld world, KitsuneEntity dead) {
        var box = new net.minecraft.util.math.Box(
            dead.getX() - MOURNING_RADIUS, dead.getY() - MOURNING_RADIUS, dead.getZ() - MOURNING_RADIUS,
            dead.getX() + MOURNING_RADIUS, dead.getY() + MOURNING_RADIUS, dead.getZ() + MOURNING_RADIUS
        );
        var nearbyBabies = world.getEntitiesByClass(KitsuneEntity.class, box,
            f -> f.isBaby() && f.isAlive());
        for (KitsuneEntity baby : nearbyBabies) {
            MOURNERS.put(baby.getUuid(), MOURNING_TICKS);
            // Stop the baby in place to "sit at" the body.
            baby.getNavigation().stop();
            baby.setSitting(true);
        }
    }

    private static void emitTears(net.minecraft.server.MinecraftServer server, UUID babyId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity e = world.getEntity(babyId);
            if (!(e instanceof KitsuneEntity baby) || !baby.isAlive()) continue;
            if (world.getTime() % 6 != 0) continue;
            world.spawnParticles(ParticleTypes.SPLASH,
                baby.getX(), baby.getY() + 0.4, baby.getZ(),
                1, 0.1, 0.05, 0.1, 0.02);
        }
    }
}
