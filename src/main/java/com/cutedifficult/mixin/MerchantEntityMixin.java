package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every trade more expensive for the next time it's used.
 *
 * <p>{@code MerchantEntity.afterUsing(TradeOffer)} is the choke point through
 * which both villagers and wandering traders pass after a successful trade.
 * {@code VillagerEntity} overrides it but calls {@code super.afterUsing()} at
 * the end, so injecting at the TAIL of the parent fires for both:
 * <ul>
 *   <li>Wandering trader: their {@code afterUsing} IS this method.</li>
 *   <li>Villager: vanilla runs villager-specific code (XP, etc.), then calls
 *       super, then our TAIL injection fires.</li>
 * </ul>
 *
 * <p>We use {@link TradeOffer#increaseSpecialPrice(int)} with a positive
 * argument — this is the public API for raising a trade's cost. The +1 is
 * permanent until restock (when uses reset). Combined with normal demand
 * mechanics that also push price up over time, this makes regular
 * customers pay progressively more.
 *
 * <p>The bump is intentionally small (+1) so that a single trade isn't
 * catastrophic, but a player who farms 20 sticks from the toolsmith ends
 * up paying 20 extra emeralds compared to a fresh start. The cruelty is
 * in the aggregate.
 *
 * <p>Tunable: change the constant to scale. With +2 or +3 it becomes
 * brutal quickly. With +1 it feels like a slow-creeping inflation.
 */
@Mixin(MerchantEntity.class)
public abstract class MerchantEntityMixin {

    private static final int PRICE_BUMP_PER_TRADE = 1;

    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void cuteDifficult$applyGreed(TradeOffer offer, CallbackInfo ci) {
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        offer.increaseSpecialPrice(PRICE_BUMP_PER_TRADE);
    }
}
