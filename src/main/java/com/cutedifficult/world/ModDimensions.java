package com.cutedifficult.world;

import com.cutedifficult.CuteDifficult;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Registry keys for Cute Difficult's custom dimensions.
 *
 * <p>The actual dimension is defined in datapack JSON:
 * <ul>
 *   <li>{@code data/cutedifficult/dimension_type/horizon.json} — physics</li>
 *   <li>{@code data/cutedifficult/dimension/horizon.json} — generation
 *       (a flat void with The Void biome everywhere)</li>
 * </ul>
 *
 * <p>This class only holds the {@link RegistryKey} so Java code (the
 * Interstellar teleport item) can reference the dimension by key.
 */
public final class ModDimensions {

    /** The Horizon — an empty void dimension for building anything. */
    public static final RegistryKey<World> HORIZON = RegistryKey.of(
        RegistryKeys.WORLD,
        Identifier.of(CuteDifficult.MOD_ID, "horizon"));

    private ModDimensions() {}

    public static void init() {
        CuteDifficult.LOGGER.info("[CuteDifficult] Dimension keys registered: horizon");
    }
}
