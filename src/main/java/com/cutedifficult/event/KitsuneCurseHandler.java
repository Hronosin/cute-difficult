package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Curses;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.SpiritData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Defiled players are cursed by nearby kitsune. When a player at DEFILED karma
 * or worse lingers near a kitsune, the offended spirit periodically lays a
 * curse on them — using the element-specific {@link Curses} already defined.
 * The closer to CURSED, the more often it strikes.
 *
 * <p>This complements {@link com.cutedifficult.spirit.FoxHostility}, which makes
 * the same players physically attackable: hatred has both a melee face and a
 * spiritual one.
 */
public final class KitsuneCurseHandler {

    private static long tick = 0;

    private KitsuneCurseHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 100 != 0) return; // check every 5 seconds

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isCreative() || player.isSpectator()) continue;
                SpiritData.KarmaTier tier = SpiritData.karmaTier(server, player);
                if (tier != SpiritData.KarmaTier.DEFILED && tier != SpiritData.KarmaTier.CURSED) {
                    continue;
                }
                // CURSED strikes twice as often (skip the gate randomly for DEFILED).
                if (tier == SpiritData.KarmaTier.DEFILED && player.getRandom().nextBoolean()) {
                    continue;
                }
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                tryCurse(world, player);
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] KitsuneCurseHandler registered.");
    }

    private static void tryCurse(ServerWorld world, ServerPlayerEntity player) {
        // Find the nearest kitsune within 16 blocks.
        var foxes = world.getEntitiesByClass(FoxEntity.class,
            player.getBoundingBox().expand(16),
            f -> f instanceof com.cutedifficult.entity.KitsuneEntity && f.isAlive());
        if (foxes.isEmpty()) return;

        FoxEntity nearest = foxes.get(0);
        double best = nearest.squaredDistanceTo(player);
        for (FoxEntity f : foxes) {
            double d = f.squaredDistanceTo(player);
            if (d < best) { best = d; nearest = f; }
        }

        KitsuneData data = FoxStorage.peekCache(nearest);
        if (data == null) return;

        // The offended kitsune lays a curse of its element.
        Curses.inflict(player, data.element, data.tails);

        // Visual: a thread of sculk soul from fox to player.
        double fx = nearest.getX(), fy = nearest.getY() + 0.5, fz = nearest.getZ();
        double dx = player.getX() - fx, dy = (player.getY() + 1) - fy, dz = player.getZ() - fz;
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            world.spawnParticles(ParticleTypes.SCULK_SOUL,
                fx + dx * t, fy + dy * t, fz + dz * t, 1, 0.02, 0.02, 0.02, 0.0);
        }
        player.sendMessage(net.minecraft.text.Text.literal(
            "A nearby kitsune fixes you with a baleful stare. A curse settles over you.")
            .formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.ITALIC), true);
    }
}
