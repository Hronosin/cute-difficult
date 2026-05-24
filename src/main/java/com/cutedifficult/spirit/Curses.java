package com.cutedifficult.spirit;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Element-specific curses applied by enraged foxes (offended via wrong
 * offering, or attacking the fox).
 *
 * <p>Mirror of {@link Blessings} — same dispatch shape, opposite intent.
 * Each element thematic in its punishment.
 *
 * <p>Duration scales with the fox's tail count: a 9-tail Kyuubi's curse
 * lasts MUCH longer than a 2-tail's. Amplifier follows same tiering.
 *
 * <p>Mechanic: curses are vanilla negative status effects, so milk
 * removes them, totem of undying does not interact (we already disabled
 * its save-from-death effect), and natural duration applies. Player can
 * cleanse with milk; that's intentional — curses should hurt but not
 * be permanently uncleansable.
 */
public final class Curses {

    private Curses() {}

    public static void inflict(ServerPlayerEntity player, Element element, int tails) {
        // Curse duration is shorter than blessing — 400..2200 ticks (20s..110s).
        int duration = 400 + tails * 200;
        int amplifier = Math.min(2, (tails - 1) / 3);

        switch (element) {
            case KASAI -> applyMulti(player, duration, amplifier,
                StatusEffects.WEAKNESS, StatusEffects.HUNGER);
            case MIZU -> applyMulti(player, duration, amplifier,
                StatusEffects.SLOWNESS, StatusEffects.MINING_FATIGUE);
            case DAICHI -> applyMulti(player, duration, amplifier,
                StatusEffects.SLOWNESS, StatusEffects.MINING_FATIGUE);
            case KAZE -> applyMulti(player, duration, amplifier,
                StatusEffects.SLOWNESS, StatusEffects.WEAKNESS);
            case KAMINARI -> applyMulti(player, duration, amplifier,
                StatusEffects.BLINDNESS, StatusEffects.WEAKNESS);
            case MORI -> applyMulti(player, duration, amplifier,
                StatusEffects.POISON, StatusEffects.UNLUCK);
            case KORI -> applyMulti(player, duration, amplifier,
                StatusEffects.SLOWNESS, StatusEffects.MINING_FATIGUE);
            case YUREI -> applyMulti(player, duration, amplifier,
                StatusEffects.BLINDNESS, StatusEffects.DARKNESS);
            case TENGOKU -> applyMulti(player, duration, amplifier,
                StatusEffects.GLOWING, StatusEffects.LEVITATION);
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
