package com.cutedifficult.entity;

import com.cutedifficult.CuteDifficult;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/**
 * Registry for custom entity types.
 *
 * <p>v0.3.5 fix: {@link EntityType.Builder#build(String)} in 1.21.1 takes
 * a string identifier path, not a {@link RegistryKey}. We pass the
 * stringified registry name. The Registry.register call still takes the
 * full {@code RegistryKey}, so that part is unchanged.
 */
public final class ModEntities {

    public static final RegistryKey<EntityType<?>> KITSUNE_KEY =
            RegistryKey.of(Registries.ENTITY_TYPE.getKey(),
                    Identifier.of(CuteDifficult.MOD_ID, "kitsune"));

    public static final EntityType<KitsuneEntity> KITSUNE = Registry.register(
            Registries.ENTITY_TYPE,
            KITSUNE_KEY,
            EntityType.Builder.<KitsuneEntity>create(KitsuneEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 0.7f)
                    .maxTrackingRange(8)
                    .build(CuteDifficult.MOD_ID + ":kitsune")
    );

    private ModEntities() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(KITSUNE, FoxEntity.createFoxAttributes());
        CuteDifficult.LOGGER.info("[CuteDifficult] Registered entity type: cutedifficult:kitsune");
    }
}