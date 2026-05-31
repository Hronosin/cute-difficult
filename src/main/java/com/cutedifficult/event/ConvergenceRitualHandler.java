package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

/**
 * The hidden Rite of the Nine. During a {@link AstrologyHandler#isGreatConvergence
 * Great Convergence}, a player who drops one PREMIUM offering of every one of the
 * nine elements together (within a small radius) consummates the rite.
 *
 * <p>This is never documented in-game — it's discovered by the kind of player
 * who tracks the ephemeris, notices the "a rite is possible" line, and
 * experiments. The reward is permanent and significant: the Mark of the Nine,
 * granting a small standing boost to ALL nine spirits at once, repeatable only
 * on future convergences.
 *
 * <p>Detection runs cheaply: only while a convergence is active, and only
 * scans dropped items near players every second.
 */
public final class ConvergenceRitualHandler {

    private static long tickCounter = 0;

    private ConvergenceRitualHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            tickCounter++;
            if (tickCounter % 20 != 0) return; // once a second
            if (!AstrologyHandler.isGreatConvergence(AstrologyHandler.now())) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                checkRiteNear(world, player);
            }
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] ConvergenceRitualHandler registered.");
    }

    private static void checkRiteNear(ServerWorld world, ServerPlayerEntity player) {
        Box box = new Box(player.getBlockPos()).expand(3);
        var drops = world.getEntitiesByClass(ItemEntity.class, box, ie -> true);
        if (drops.isEmpty()) return;

        // Collect which elements' PREMIUM offerings are present among the drops.
        Set<Element> present = new HashSet<>();
        for (ItemEntity ie : drops) {
            Item item = ie.getStack().getItem();
            for (Element e : Element.values()) {
                if (e.premiumOffering() == item) present.add(e);
            }
        }

        if (present.size() < Element.values().length) return; // need all nine

        // Rite consummated — consume one of each premium offering.
        Set<Element> consumed = new HashSet<>();
        for (ItemEntity ie : drops) {
            Item item = ie.getStack().getItem();
            for (Element e : Element.values()) {
                if (e.premiumOffering() == item && !consumed.contains(e)) {
                    ie.getStack().decrement(1);
                    if (ie.getStack().isEmpty()) ie.discard();
                    consumed.add(e);
                }
            }
        }

        grantMarkOfTheNine(world, player);
    }

    private static void grantMarkOfTheNine(ServerWorld world, ServerPlayerEntity player) {
        // Permanent-ish standing boost: +5 spirit to every element at once.
        for (Element e : Element.values()) {
            SpiritData.add(world.getServer(), player, e, 5);
        }

        // A keepsake item: an enchanted-glint nether star renamed as the Mark.
        ItemStack mark = new ItemStack(Items.NETHER_STAR);
        mark.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
            Text.literal("Mark of the Nine").formatted(Formatting.GOLD, Formatting.BOLD));
        if (!player.getInventory().insertStack(mark)) {
            player.dropItem(mark, false);
        }

        // Blessing burst.
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.REGENERATION, 200, 2, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.ABSORPTION, 1200, 2, false, true, true));

        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
            player.getX(), player.getY() + 1, player.getZ(), 80, 0.6, 1.0, 0.6, 0.4);
        world.playSound(null, player.getBlockPos(),
            SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2f, 1.0f);

        player.sendMessage(Text.literal("THE RITE OF THE NINE IS COMPLETE. Inari's whole house turns to face you.")
            .formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.sendMessage(Text.literal("All nine spirits rise a little. The Mark of the Nine is yours.")
            .formatted(Formatting.YELLOW, Formatting.ITALIC), false);

        CuteDifficult.LOGGER.info("[CuteDifficult] {} completed the Rite of the Nine.",
            player.getName().getString());
    }
}
