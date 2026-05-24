package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.util.Identifier;

/**
 * "Greedy chests" mechanic — every naturally generated chest gives roughly
 * half its vanilla loot.
 *
 * <p><b>Note on API version:</b> v0.1.8 attempted to use Fabric API
 * {@code loot.v3} but the signature differed across yarn builds and was
 * fragile. v0.1.9 (this) uses {@code loot.v2} which has been stable
 * across 1.20–1.21+. Both versions ship in current Fabric API; v2 has
 * a deprecation note but works fine.
 *
 * <p>On {@link LootTableEvents#MODIFY}, intercept every loot table whose
 * identifier path starts with {@code chests/}. For each pool in the
 * table, attach a {@link RandomChanceLootCondition} at 50%. The entire
 * pool then rolls with 50% chance — when it doesn't roll, none of its
 * entries drop.
 *
 * <p><b>Side effect — multi-pool tables are extra-stingy:</b> chests with
 * multiple pools (richer structures) get 50% applied to EACH pool
 * independently. That's intentional: the richer the structure, the more
 * it holds back. Matches the "greedy" flavor.
 *
 * <p><b>What we DON'T touch:</b> mob drops, block drops, fishing,
 * archeology, advancement rewards. Only {@code chests/*} paths.
 *
 * <p><b>Active only in CRUEL mode</b>; toggling Path of Peace at runtime
 * doesn't reload tables, but newly-loaded worlds will respect the mode.
 */
public final class GreedyLootHandler {

    private static final float POOL_ROLL_CHANCE = 0.5f;
    private static final String CHEST_PATH_PREFIX = "chests/";

    private GreedyLootHandler() {}

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            Identifier id = key.getValue();
            if (id == null || !id.getPath().startsWith(CHEST_PATH_PREFIX)) {
                return;
            }

            tableBuilder.modifyPools(poolBuilder ->
                    poolBuilder.conditionally(RandomChanceLootCondition.builder(POOL_ROLL_CHANCE))
            );

            CuteDifficult.LOGGER.debug(
                    "[CuteDifficult] Applied greedy loot modifier to {}", id
            );
        });

        CuteDifficult.LOGGER.info(
                "[CuteDifficult] GreedyLootHandler registered (chest loot at {}% per pool).",
                (int)(POOL_ROLL_CHANCE * 100)
        );
    }
}