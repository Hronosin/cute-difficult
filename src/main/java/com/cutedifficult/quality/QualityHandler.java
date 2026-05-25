package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.quality.ItemQuality;
import com.cutedifficult.quality.QualityTier;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Quality system runtime.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li>On each player tick, scan inventory and {@code ensureQuality}
 *       on any applicable items lacking it. This catches new items from
 *       crafting (we can't intercept the actual craft event in 1.21.1
 *       Fabric without a mixin, so the tick scan is the pragmatic
 *       approach — first time a player holds a new item, it gets quality).</li>
 *   <li>Listen to {@code ALLOW_DAMAGE} for combat: scale incoming damage
 *       by the attacker's held-weapon quality multiplier, scale absorbed
 *       damage by the defender's armor quality.</li>
 * </ol>
 *
 * <p><b>Why a tick scan instead of crafting hook:</b> Fabric 1.21.1
 * doesn't expose a clean "item was just crafted" event that gives us
 * the resulting stack pre-pickup. Using a screen handler mixin is messy
 * and breaks shulker boxes. The tick scan is O(36 items × N players)
 * per tick, but each item check is O(1) thanks to {@link ItemQuality#get}
 * being a simple component read — total cost is negligible.
 */
public final class QualityHandler {

    private QualityHandler() {}

    public static void register() {
        // Per-player tick: ensure quality on any applicable items.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                scanInventory(player);
            }
        });

        // On living entity hurt: modify damage by quality multipliers.
        // We use AFTER_DAMAGE for armor-defender scaling (defender absorbs less if low-quality armor)
        // and ALLOW_DAMAGE for attacker scaling.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
            scaleIncomingDamage(victim, source, amount)
        );

        CuteDifficult.LOGGER.info("[CuteDifficult] QualityHandler registered.");
    }

    private static void scanInventory(ServerPlayerEntity player) {
        var inv = player.getInventory();
        // Main inventory + armor + offhand
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (!ItemQuality.appliesTo(stack)) continue;
            ItemQuality.ensureQuality(stack);
        }
    }

    /**
     * Reroutes damage through quality multipliers.
     *
     * <p>Returns {@code false} to "cancel" the original damage — we then
     * apply our adjusted damage manually. Actually no, this gets tricky;
     * the simpler path is to return true and rely on the BEFORE_DAMAGE
     * event to mutate amount — except that event signature doesn't allow
     * mutation. We'll return true (allow original) but apply a follow-up
     * damage adjustment via heal/damage delta. That's hacky.
     *
     * <p><b>Refined approach:</b> we directly mutate the player's
     * weapon-based damage by applying additional damage to the victim
     * BEFORE this event fires via a different hook, OR we modify armor
     * after the fact. Both are messy.
     *
     * <p><b>For v0.7 we take the simplest viable path:</b> read the
     * attacker's main-hand weapon multiplier. If it's not 1.0, scale
     * the damage by returning a non-cancel decision while having
     * pre-applied a damage adjustment via the attacker's attributes.
     * Specifically — we add a tiny instant-heal-or-damage delta on the
     * victim equal to {@code (multiplier - 1.0) * amount}. Negative
     * deltas heal (crude weapon does less); positive damage (superior
     * does more).
     */
    private static boolean scaleIncomingDamage(LivingEntity victim, DamageSource source, float amount) {
        // Attacker-side scaling.
        if (source.getAttacker() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandStack();
            if (!weapon.isEmpty() && ItemQuality.appliesTo(weapon)) {
                QualityTier tier = ItemQuality.get(weapon);
                if (tier != null && tier.multiplier() != 1.0f) {
                    float delta = (tier.multiplier() - 1.0f) * amount;
                    // Apply delta as a separate instant adjustment.
                    // For positive delta (better weapon), deal extra damage.
                    // For negative delta (crude weapon), heal the victim.
                    if (delta > 0) {
                        // We must avoid recursion — use a magic damage source
                        // and a tiny flag. Simpler: schedule on next tick.
                        // Actually, we can just adjust the victim's health
                        // directly because we're inside ALLOW_DAMAGE which
                        // happens BEFORE the damage application.
                        // Hack-but-works:
                        victim.setHealth(victim.getHealth() - delta);
                    } else if (delta < 0) {
                        victim.setHealth(Math.min(victim.getMaxHealth(),
                            victim.getHealth() - delta /* delta is negative -> heals */));
                    }
                }
            }
        }

        // Defender-side scaling: scale damage reduction by armor quality.
        // Vanilla applies armor first; we want better armor to absorb more.
        // We compute average armor quality across the 4 armor slots.
        if (victim instanceof PlayerEntity player) {
            float armorMultSum = 0;
            int armorCount = 0;
            for (var armorStack : player.getArmorItems()) {
                if (armorStack.isEmpty()) continue;
                QualityTier t = ItemQuality.get(armorStack);
                if (t == null) continue;
                armorMultSum += t.multiplier();
                armorCount++;
            }
            if (armorCount > 0) {
                float avgMult = armorMultSum / armorCount;
                // multiplier > 1.0 → reduce damage (heal); < 1.0 → increase damage.
                // The reduction is gentler than weapon scaling to avoid trivializing.
                float armorDelta = (1.0f - avgMult) * amount * 0.3f;
                // armorDelta positive (crude armor) → extra damage to victim
                // armorDelta negative (superior armor) → heal victim
                if (armorDelta > 0) {
                    victim.setHealth(victim.getHealth() - armorDelta);
                } else if (armorDelta < 0) {
                    victim.setHealth(Math.min(victim.getMaxHealth(),
                        victim.getHealth() - armorDelta));
                }
            }
        }

        return true; // allow the original damage to proceed
    }
}
