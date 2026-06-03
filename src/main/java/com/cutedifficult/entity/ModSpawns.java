package com.cutedifficult.entity;

import com.cutedifficult.CuteDifficult;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Heightmap;

/**
 * Registers natural spawning for the kitsune. Two parts:
 * <ul>
 *   <li><b>SpawnRestriction</b> — the rules for a valid spawn position (on
 *       ground, animal-style light/world checks), mirroring vanilla foxes.</li>
 *   <li><b>BiomeModifications</b> — which biomes the kitsune spawns in, and how
 *       often. We add them broadly across the Overworld so every element's
 *       themed biome is covered (the entity picks its element from the biome at
 *       spawn — see {@link KitsuneSpawnLogic}).</li>
 * </ul>
 */
public final class ModSpawns {

    private ModSpawns() {}

    public static void init() {
        // Valid-position rule: on ground, like other passive animals.
        SpawnRestriction.register(ModEntities.KITSUNE,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                MobEntity::canMobSpawn);

        // Spawn across the Overworld. Weight ~20 (uncommon but present), groups
        // of 1-2. The element is decided per-biome at spawn time.
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                SpawnGroup.CREATURE,
                ModEntities.KITSUNE,
                20,  // spawn weight
                1,   // min group size
                2);  // max group size

        CuteDifficult.LOGGER.info("[CuteDifficult] Kitsune natural spawns registered.");
    }
}