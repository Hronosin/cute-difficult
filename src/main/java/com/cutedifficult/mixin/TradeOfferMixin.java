package com.cutedifficult.mixin;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Greedy villager pricing — replaces vanilla discount/demand calculation
 * entirely with our own "base price + uses count" formula.
 *
 * <p>Approach: intercept {@code getDisplayedFirstBuyItem()} at HEAD and
 * replace the return value with a freshly-built ItemStack whose count
 * is {@code base_count + uses}. Every code path that asks "what's the
 * current cost?" — both UI and trade-matching — gets our answer.
 *
 * <p>Vanilla's discount/demand machinery still runs and writes to
 * {@code specialPrice}/{@code demandBonus}, but we never read those fields,
 * so the writes are wasted CPU but harmless.
 *
 * <p>The "uses" counter is naturally incremented by vanilla's
 * {@code offer.use()} after each successful trade — no manual bookkeeping
 * required. The counter resets on restock; brief relief, then prices
 * climb again from base.
 *
 * <p><b>On {@code TradedItem.components()}:</b> this returns a
 * {@code ComponentPredicate} — a filter used to check whether a player's
 * offered item matches the expected components (e.g. "must have Sharpness 3").
 * It can't be applied to a display stack, and we don't need to: vanilla
 * still runs its own matching logic against the predicate when the player
 * actually attempts to trade. Our job is only to show the right cost in
 * the UI and have the right count compared. A plain {@code new ItemStack(item, count)}
 * is correct for both.
 *
 * <p><b>Active only in CRUEL mode</b>; in Path of Peace, vanilla pricing
 * (including discounts) is restored.
 */
@Mixin(TradeOffer.class)
public abstract class TradeOfferMixin {

    @Shadow public abstract TradedItem getFirstBuyItem();

    @Shadow public abstract int getUses();

    @Inject(method = "getDisplayedFirstBuyItem", at = @At("HEAD"), cancellable = true)
    private void cuteDifficult$enforceGreedyPrice(CallbackInfoReturnable<ItemStack> cir) {
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

        TradedItem base = this.getFirstBuyItem();
        int newCount = base.count() + this.getUses();

        cir.setReturnValue(new ItemStack(base.item(), newCount));
    }
}