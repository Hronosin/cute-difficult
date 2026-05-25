package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.FoxPersonality;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.Random;

/**
 * Bind a fox to a name via a vanilla name tag. The chosen name is
 * stored in {@link KitsuneData#customName} and persists in NBT.
 *
 * <p><b>Personality bias by name:</b> hashing the name yields a
 * deterministic shift in the fox's 7 personality traits. Different
 * names tilt the fox toward different archetypes:
 * <ul>
 *   <li>Hash bit 0 → pride bias</li>
 *   <li>Hash bit 1 → trust bias</li>
 *   <li>Hash bit 2 → curiosity bias</li>
 *   <li>Hash bit 3 → memory bias</li>
 *   <li>Hash bit 4 → greed bias</li>
 *   <li>Hash bit 5 → sensitivity bias</li>
 *   <li>Hash bit 6 → trauma bias</li>
 * </ul>
 *
 * <p>The bias is +15 per relevant bit, capped at 100. Same name = same
 * shift, so "Sakura" always nudges the same direction. This makes
 * naming a meaningful choice rather than just labeling.
 */
public final class NameTagHandler {

    private static final Random RANDOM = new Random();
    private static final int BIAS_AMOUNT = 15;

    private NameTagHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.NAME_TAG)) return ActionResult.PASS;

            // Vanilla requires the name tag to have a custom name set in an anvil.
            var customName = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
            if (customName == null) return ActionResult.PASS;

            String chosenName = customName.getString().trim();
            if (chosenName.isEmpty()) return ActionResult.PASS;

            applyNameToFox(serverWorld, sp, fox, chosenName, stack);
            return ActionResult.SUCCESS;
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] NameTagHandler registered.");
    }

    private static void applyNameToFox(ServerWorld world, ServerPlayerEntity player,
                                        FoxEntity fox, String name, ItemStack tag) {
        KitsuneData existing = FoxStorage.getOrCreate(fox, RANDOM);
        if (!existing.customName.isEmpty()) {
            player.sendMessage(
                Text.literal("This kitsune already has a name: " + existing.customName)
                    .formatted(Formatting.GRAY, Formatting.ITALIC),
                true
            );
            return;
        }

        // Compute hash-based bias.
        FoxPersonality biased = biasPersonality(existing.personality, name);
        KitsuneData updated = existing
            .withCustomName(name)
            .withPersonality(biased);
        FoxStorage.store(fox, updated);

        // Set the entity's custom display name too (so floating-name shows).
        fox.setCustomName(Text.literal(name).formatted(Formatting.GOLD, Formatting.ITALIC));
        fox.setCustomNameVisible(true);

        // Consume the tag.
        if (!player.isCreative()) tag.decrement(1);

        // FX.
        world.spawnParticles(ParticleTypes.HEART,
            fox.getX(), fox.getY() + 0.8, fox.getZ(),
            6, 0.3, 0.3, 0.3, 0.05);
        world.playSound(null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.4f);

        player.sendMessage(
            Text.literal("Named: ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(name).formatted(existing.element.color())),
            false
        );
    }

    /**
     * Deterministic bias based on name hash. Each bit of the hash flips
     * one personality trait upward by BIAS_AMOUNT. Same name → same bias,
     * so naming a fox "Sakura" anywhere gives the same character tilt.
     */
    private static FoxPersonality biasPersonality(FoxPersonality base, String name) {
        int hash = name.toLowerCase().hashCode();

        int pride = clamp(base.pride() + (((hash >> 0) & 1) == 1 ? BIAS_AMOUNT : 0));
        int trust = clamp(base.trust() + (((hash >> 1) & 1) == 1 ? BIAS_AMOUNT : 0));
        int curiosity = clamp(base.curiosity() + (((hash >> 2) & 1) == 1 ? BIAS_AMOUNT : 0));
        int memory = clamp(base.memory() + (((hash >> 3) & 1) == 1 ? BIAS_AMOUNT : 0));
        int greed = clamp(base.greed() + (((hash >> 4) & 1) == 1 ? BIAS_AMOUNT : 0));
        int sensitivity = clamp(base.sensitivity() + (((hash >> 5) & 1) == 1 ? BIAS_AMOUNT : 0));
        int trauma = clamp(base.trauma() + (((hash >> 6) & 1) == 1 ? BIAS_AMOUNT : 0));

        return new FoxPersonality(pride, trust, curiosity, memory, greed, sensitivity, trauma);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
