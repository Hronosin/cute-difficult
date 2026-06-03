package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpiritData;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

/**
 * Redemption — cleansing kegare through peaceful, creative, life-giving acts.
 * Karma can only be lowered by living well, not by more violence:
 *
 * <ul>
 *   <li><b>Harvesting mature crops</b> — small cleanse (−1), the quiet work of
 *       tending the land.</li>
 *   <li><b>Breeding animals</b> — medium cleanse (−3), nurturing new life.</li>
 *   <li><b>Trading with villagers</b> — larger cleanse (−5), restoring bonds
 *       with the community.</li>
 *   <li><b>Slaying the Hollow Lord</b> — −100, a great purification (handled in
 *       the boss death code, which calls {@link #greatPurification}).</li>
 * </ul>
 */
public final class RedemptionHandler {

    private RedemptionHandler() {}

    public static void register() {
        // Harvesting mature crops cleanses a little.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                cleanse(sp, 1, false);
            }
        });

        // Breeding animals cleanses moderately. We detect the moment via the
        // interact callback when feeding breeding food to a love-ready animal.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            if (entity instanceof VillagerEntity villager) {
                // Trading: detect when the player opens trade with a villager
                // that has trades. (A light heuristic; full trade-complete hooks
                // need a mixin. Opening commerce already signals peaceful intent.)
                if (!villager.isBaby() && !villager.getOffers().isEmpty()) {
                    // Throttle so holding right-click doesn't spam cleanse.
                    long now = world.getTime();
                    Long last = LAST_TRADE.get(sp.getUuid());
                    if (last == null || now - last > 100) {
                        LAST_TRADE.put(sp.getUuid(), now);
                        cleanse(sp, 5, true);
                    }
                }
            } else if (entity instanceof AnimalEntity animal) {
                // Breeding: if the held item is this animal's breeding food and
                // the animal can fall in love, count it as a nurturing act.
                if (!animal.isBaby() && animal.isBreedingItem(sp.getStackInHand(hand))
                    && animal.getBreedingAge() == 0) {
                    long now = world.getTime();
                    Long last = LAST_BREED.get(sp.getUuid());
                    if (last == null || now - last > 60) {
                        LAST_BREED.put(sp.getUuid(), now);
                        cleanse(sp, 3, true);
                    }
                }
            }
            return ActionResult.PASS;
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] RedemptionHandler registered.");
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_TRADE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_BREED =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Lower karma by {@code amount}; optionally show a small feedback cue. */
    private static void cleanse(ServerPlayerEntity player, int amount, boolean notify) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        int before = SpiritData.getKarma(server, player);
        if (before <= 0) return; // already pure, nothing to cleanse
        SpiritData.addKarma(server, player, -amount);

        if (player.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, false,
                player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.4, 0.3, 0.02);
        }
        if (notify) {
            int after = SpiritData.getKarma(server, player);
            player.sendMessage(net.minecraft.text.Text.literal(
                "A small weight lifts. (karma " + before + " \u2192 " + after + ")")
                .formatted(net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.ITALIC), true);
        }
    }

    /** Great purification: slaying the Hollow Lord washes away deep stain. */
    public static void greatPurification(ServerWorld world, ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        int before = SpiritData.getKarma(server, player);
        SpiritData.addKarma(server, player, -100);
        int after = SpiritData.getKarma(server, player);

        world.spawnParticles(player, ParticleTypes.TOTEM_OF_UNDYING, false,
            player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.5, 0.8, 0.5, 0.3);
        player.sendMessage(net.minecraft.text.Text.literal(
            "The Hollow Lord's end purges the stain from your spirit. (karma " + before + " \u2192 " + after + ")")
            .formatted(net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.BOLD), false);
    }
}
