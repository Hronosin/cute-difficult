package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.entity.ModEntities;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxPersonality;
import com.cutedifficult.spirit.FoxStats;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Replaces vanilla foxes with our custom {@link KitsuneEntity} on spawn,
 * and assigns initial element/tails/personality data to fresh kitsunes.
 *
 * <p><b>v0.3.4 flow:</b>
 * <ol>
 *   <li>Vanilla {@link FoxEntity} spawns (natural, command, etc).</li>
 *   <li>Our ENTITY_LOAD listener fires.</li>
 *   <li>If the entity is FoxEntity but NOT KitsuneEntity, discard it and
 *       spawn a fresh KitsuneEntity at the same position with our spirit
 *       data already attached.</li>
 *   <li>If it's a KitsuneEntity (e.g. loaded from disk on chunk load),
 *       just ensure its HP matches its tail count.</li>
 * </ol>
 *
 * <p>Result: there are no vanilla foxes in the world. Every {@code /summon fox},
 * every natural spawn, every chunk that comes online with old foxes — all
 * become kitsune.
 */
public final class FoxSpawnHandler {

    private static final Random RANDOM = new Random();
    private static final double THUNDER_KAMINARI_CHANCE = 0.25;

    private FoxSpawnHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(entity instanceof FoxEntity fox)) return;

            // Already our kitsune — just sync HP if needed.
            if (fox instanceof KitsuneEntity) {
                NbtCompound nbt = new NbtCompound();
                fox.writeNbt(nbt);
                if (nbt.contains(FoxData.NBT_KEY)) {
                    FoxData data = FoxData.fromNbt(nbt.getCompound(FoxData.NBT_KEY));
                    FoxStats.applyHpForTails(fox, data.tails());
                }
                return;
            }

            // Vanilla fox — replace it with a kitsune at the same position.
            replaceWithKitsune(world, fox);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxSpawnHandler registered (kitsune replacement active).");
    }

    private static void replaceWithKitsune(ServerWorld world, FoxEntity fox) {
        double x = fox.getX();
        double y = fox.getY();
        double z = fox.getZ();
        float yaw = fox.getYaw();
        float pitch = fox.getPitch();
        BlockPos pos = fox.getBlockPos();

        // Get rid of the vanilla fox.
        fox.discard();

        // Spawn a kitsune.
        KitsuneEntity kitsune = ModEntities.KITSUNE.create(world);
        if (kitsune == null) {
            CuteDifficult.LOGGER.error("[CuteDifficult] Failed to create KitsuneEntity for replacement.");
            return;
        }
        kitsune.refreshPositionAndAngles(x, y, z, yaw, pitch);

        // Assign element, tails, personality.
        Element element = selectElement(world, pos);
        int tails = rollTails();
        FoxPersonality personality = FoxPersonality.random(RANDOM);
        FoxData data = new FoxData(element, personality, tails, 0, 0L, 0);
        FoxData.store(kitsune, data);
        FoxStats.applyHpForTails(kitsune, tails);

        world.spawnEntity(kitsune);

        if (tails >= 5) {
            CuteDifficult.LOGGER.info(
                "[CuteDifficult] Rare kitsune spawned: {} with {} tails ({} HP) at {}",
                element.kamiName(), tails, (int)FoxStats.hpForTails(tails),
                pos.toShortString()
            );
        }
    }

    private static int rollTails() {
        double r = RANDOM.nextDouble();
        if (r < 0.65)    return 1;
        if (r < 0.85)    return 2;
        if (r < 0.95)    return 3;
        if (r < 0.98)    return 4;
        if (r < 0.995)   return 5;
        if (r < 0.999)   return 6;
        if (r < 0.9998)  return 7;
        if (r < 0.99998) return 8;
        return 9;
    }

    private static Element selectElement(ServerWorld world, BlockPos pos) {
        if (world.isThundering() && RANDOM.nextDouble() < THUNDER_KAMINARI_CHANCE) {
            return Element.KAMINARI;
        }
        Element fromBiome = elementForBiome(world, pos);
        if (fromBiome != null) return fromBiome;
        return Element.random(RANDOM);
    }

    private static Element elementForBiome(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> entry = world.getBiome(pos);
        var keyOpt = entry.getKey();
        if (keyOpt.isEmpty()) return null;

        String path = keyOpt.get().getValue().getPath();

        if (path.contains("snowy") || path.contains("frozen")
            || path.equals("ice_spikes") || path.contains("grove")) return Element.KORI;
        if (path.contains("jungle") || path.contains("forest")
            || path.contains("taiga") || path.equals("flower_forest")
            || path.equals("birch_forest")) return Element.MORI;
        if (path.contains("savanna") || path.equals("desert")
            || path.contains("badlands")) return Element.KASAI;
        if (path.contains("nether") || path.equals("basalt_deltas")
            || path.equals("crimson_forest") || path.equals("warped_forest")
            || path.equals("soul_sand_valley")) return Element.KASAI;
        if (path.contains("peaks") || path.contains("hills")
            || path.equals("stony_shore") || path.equals("meadow")) return Element.DAICHI;
        if (path.equals("dripstone_caves")) return Element.DAICHI;
        if (path.equals("lush_caves")) return Element.MORI;
        if (path.equals("deep_dark")) return Element.YUREI;
        if (path.contains("windswept")) return Element.KAZE;
        if (path.contains("ocean") || path.equals("river")
            || path.equals("frozen_river") || path.contains("beach")
            || path.contains("swamp") || path.equals("mangrove_swamp")) return Element.MIZU;
        if (path.contains("end")) return Element.TENGOKU;

        return null;
    }
}
