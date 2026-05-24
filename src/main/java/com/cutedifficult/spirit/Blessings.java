package com.cutedifficult.spirit;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Element-specific blessings granted by friendly foxes after offerings.
 *
 * <p>Each element has a thematic blessing — buff(s) appropriate to the
 * element. Duration and amplifier scale with tail count of the granting
 * fox: more tails = longer, stronger blessing.
 *
 * <p>Blessings are applied directly as vanilla status effects so they're
 * visible in the inventory effects panel and consumed by death etc.
 * normally. They stack-by-replacement: a new blessing of the same type
 * overrides duration, but doesn't compound amplifiers.
 *
 * <p>This is the player-facing reward for tending kitsune — concrete,
 * felt power in exchange for grinding spiritual capital.
 */
public final class Blessings {

    private Blessings() {}

    /**
     * Apply the appropriate blessing for the given element and tail
     * count to the player. Called from {@code FoxOfferingHandler} when
     * a successful offering happens and trust crosses a threshold.
     */
    public static void grant(ServerPlayerEntity player, Element element, int tails) {
        // Duration scales 600..3600 ticks (30s..3min) with tails.
        int duration = 600 + tails * 333; // 1 tail = 933 ticks ~46s; 9 tails = 3597 ~3min
        // Amplifier: 0 (Level I) for low tails, up to 2 (Level III) for Kyuubi.
        int amplifier = Math.min(2, (tails - 1) / 3);

        switch (element) {
            case KASAI -> applyMulti(player, duration, amplifier,
                StatusEffects.FIRE_RESISTANCE, StatusEffects.STRENGTH);
            case MIZU -> applyMulti(player, duration, amplifier,
                StatusEffects.WATER_BREATHING, StatusEffects.DOLPHINS_GRACE);
            case DAICHI -> applyMulti(player, duration, amplifier,
                StatusEffects.RESISTANCE, StatusEffects.HASTE);
            case KAZE -> applyMulti(player, duration, amplifier,
                StatusEffects.SPEED, StatusEffects.JUMP_BOOST);
            case KAMINARI -> applyMulti(player, duration, amplifier,
                StatusEffects.STRENGTH, StatusEffects.SPEED);
            case MORI -> applyMulti(player, duration, amplifier,
                StatusEffects.REGENERATION, StatusEffects.LUCK);
            case KORI -> applyMulti(player, duration, amplifier,
                StatusEffects.RESISTANCE, StatusEffects.SLOW_FALLING);
            case YUREI -> applyMulti(player, duration, amplifier,
                StatusEffects.NIGHT_VISION, StatusEffects.INVISIBILITY);
            case TENGOKU -> applyMulti(player, duration, amplifier,
                StatusEffects.REGENERATION, StatusEffects.SATURATION, StatusEffects.HERO_OF_THE_VILLAGE);
        }
    }

    @SafeVarargs
    private static void applyMulti(
        ServerPlayerEntity player,
        int duration,
        int amplifier,
        net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect>... effects
    ) {
        for (var effect : effects) {
            player.addStatusEffect(new StatusEffectInstance(
                effect, duration, amplifier, false, true, true
            ));
        }
    }
}
