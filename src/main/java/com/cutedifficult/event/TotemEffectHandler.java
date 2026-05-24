package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.List;
import java.util.Random;

/**
 * v0.5.2: stripped the verbose per-callback debug logs. They served their
 * purpose during the initial wiring debugging — now they just noise.
 *
 * Behavior unchanged: right-clicking a Totem of Undying applies a random
 * positive effect and consumes the totem (in CRUEL mode).
 */
public final class TotemEffectHandler {

    private static final List<RegistryEntry<StatusEffect>> POSITIVE_EFFECTS = List.of(
        StatusEffects.REGENERATION,
        StatusEffects.RESISTANCE,
        StatusEffects.STRENGTH,
        StatusEffects.SPEED,
        StatusEffects.ABSORPTION,
        StatusEffects.FIRE_RESISTANCE,
        StatusEffects.HASTE,
        StatusEffects.JUMP_BOOST,
        StatusEffects.NIGHT_VISION,
        StatusEffects.WATER_BREATHING
    );

    private static final int MIN_DURATION_TICKS = 400;
    private static final int MAX_DURATION_TICKS = 6000;
    private static final int MAX_AMPLIFIER = 2;
    private static final Random RANDOM = new Random();

    private TotemEffectHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return TypedActionResult.pass(stack);
            }
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) {
                return TypedActionResult.pass(stack);
            }
            if (world.isClient) {
                return TypedActionResult.success(stack);
            }

            RegistryEntry<StatusEffect> effect = POSITIVE_EFFECTS.get(
                RANDOM.nextInt(POSITIVE_EFFECTS.size())
            );
            int amplifier = RANDOM.nextInt(MAX_AMPLIFIER + 1);
            int duration = MIN_DURATION_TICKS
                + RANDOM.nextInt(MAX_DURATION_TICKS - MIN_DURATION_TICKS);

            player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));

            if (!player.isCreative()) {
                stack.decrement(1);
            }

            world.playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TOTEM_USE,
                SoundCategory.PLAYERS,
                1.0f, 1.2f + RANDOM.nextFloat() * 0.2f
            );

            Text effectName = Text.translatable(effect.value().getTranslationKey());
            String tierStr = romanNumeral(amplifier + 1);
            int seconds = duration / 20;

            player.sendMessage(
                Text.literal("The totem grants you ").formatted(Formatting.GOLD)
                    .append(effectName.copy().formatted(Formatting.YELLOW))
                    .append(Text.literal(" " + tierStr).formatted(Formatting.YELLOW))
                    .append(Text.literal(" for " + seconds + "s.").formatted(Formatting.GOLD)),
                false
            );

            return TypedActionResult.success(stack);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] TotemEffectHandler registered.");
    }

    private static String romanNumeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}
