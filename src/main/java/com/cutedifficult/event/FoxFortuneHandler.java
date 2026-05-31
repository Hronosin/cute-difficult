package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * "I'll farm up the last item and get out of the forest, I promise" — the
 * Mori enhanced Fortune. Vanilla Fortune only affects ores and a few special
 * blocks. This makes it drop bonus copies of **any** block broken, scaling
 * with the stored level.
 *
 * <p>Implemented via PlayerBlockBreakEvents.AFTER — after the block breaks,
 * we look at what would normally drop and spawn extra copies. To keep it
 * simple and robust, we drop extra copies of the block's own item form,
 * with the count rolled like vanilla Fortune (1 + random(level+1), biased).
 */
public final class FoxFortuneHandler {

    private static final Random RANDOM = new Random();

    private FoxFortuneHandler() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)) return;
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;
            if (sp.isCreative()) return;

            ItemStack tool = sp.getMainHandStack();
            int level = com.cutedifficult.spirit.EnchantMarkers.levelOf(tool, "mori");
            if (level <= 0) return;

            dropBonus(serverWorld, pos, state, level);
        });

        // Loot Goblin — Mori Looting: mobs drop bonus loot when killed with a marked weapon.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) return;
            if (!(entity.getWorld() instanceof ServerWorld serverWorld)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity sp)) return;
            int level = com.cutedifficult.spirit.EnchantMarkers.levelOf(sp.getMainHandStack(), "mori_goblin");
            if (level <= 0) return;
            // Bonus: a few emeralds + extra XP orbs as "goblin loot".
            int emeralds = RANDOM.nextInt(level + 1);
            if (emeralds > 0) {
                net.minecraft.entity.ItemEntity drop = new net.minecraft.entity.ItemEntity(
                    serverWorld, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    new ItemStack(net.minecraft.item.Items.EMERALD, emeralds));
                serverWorld.spawnEntity(drop);
            }
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                entity.getX(), entity.getY() + 0.5, entity.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxFortuneHandler registered.");
    }

    private static void dropBonus(ServerWorld world, BlockPos pos, BlockState state, int level) {
        Block block = state.getBlock();
        ItemStack blockItem = new ItemStack(block.asItem());
        if (blockItem.isEmpty()) return; // block has no item form (e.g. fire)

        // Vanilla-ish Fortune roll: each level adds a chance for +1..+level extra.
        int bonus = 0;
        for (int i = 0; i < level; i++) {
            if (RANDOM.nextInt(2) == 0) bonus++;
        }
        // Guarantee at least +1 so the enchant always feels rewarding.
        bonus = Math.max(1, bonus);

        ItemStack drop = new ItemStack(block.asItem(), bonus);
        Block.dropStack(world, pos, drop);
    }
}
