package com.cutedifficult.spirit;

import com.cutedifficult.event.ResonanceBlessingHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Centralized "should this fox attack this player?" decision.
 *
 * <p>v0.6.3 deduplication: previously trust/witness/great-blessing checks
 * were scattered across {@link com.cutedifficult.entity.ai.KitsuneAttackGoal},
 * {@link com.cutedifficult.event.FoxAbilityHandler},
 * {@link com.cutedifficult.event.FoxAggressionHandler}, and
 * {@link com.cutedifficult.entity.ai.KitsuneMeleeGoal} — and some of
 * them only had partial logic. Refactor: every code path that's about
 * to cast an ability or initiate combat MUST call {@link #canAttack}
 * first. One source of truth.
 *
 * <p>Also adds {@link #hasLineOfSight} so attacks don't happen through
 * walls — addresses the "kitsune shoots fireballs through a stone wall"
 * complaint.
 *
 * <p>Rules in priority order:
 * <ol>
 *   <li><b>Great Blessing of Inari</b> on player → always peaceful.</li>
 *   <li><b>Witnessed killings &gt; 0</b> → fox attacks regardless of trust.</li>
 *   <li><b>Trust ≥ {@link #FRIENDLY_TRUST_THRESHOLD}</b> → friendly, no attack.</li>
 *   <li>Otherwise → attack permitted.</li>
 * </ol>
 */
public final class FoxHostility {

    public static final int FRIENDLY_TRUST_THRESHOLD = 30;

    private FoxHostility() {}

    public static boolean canAttack(FoxEntity fox, ServerPlayerEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (target.isCreative() || target.isSpectator()) return false;

        // v0.9.7: if the player recently attacked THIS specific fox,
        // its rage overrides everything — even the Great Blessing of
        // Inari. Otherwise blessed players could massacre kitsune
        // without retaliation, which breaks the moral logic of the
        // whole system. The grudge times out after a few seconds
        // (see FoxRageHandler.RAGE_TICKS).
        if (com.cutedifficult.event.FoxRageHandler.isEnragedAt(fox, target)) {
            return true;
        }

        // Great Blessing overrides everything else.
        if (ResonanceBlessingHandler.hasGreatBlessing(target)) return false;

        // Kegare: a defiled player (karma 100+) is reviled by all kitsune —
        // the stain overrides trust entirely. Even a friendly fox turns.
        if (target.getServer() != null) {
            SpiritData.KarmaTier ktier = SpiritData.karmaTier(target.getServer(), target);
            if (ktier == SpiritData.KarmaTier.DEFILED || ktier == SpiritData.KarmaTier.CURSED) {
                return true;
            }
        }

        KitsuneData data = FoxStorage.peekCache(fox);
        if (data == null) {
            // No data cached yet — treat as hostile (default-aggressive new fox).
            return true;
        }

        // Witnessed killings override trust.
        if (data.witnessedKills > 0) return true;

        // Friendly foxes don't attack.
        if (data.trustLevel >= FRIENDLY_TRUST_THRESHOLD) return false;

        return true;
    }

    /**
     * Line-of-sight check: is there an unobstructed path from the fox's
     * eyes to the target's center? Returns false if a solid block is in
     * the way.
     */
    public static boolean hasLineOfSight(FoxEntity fox, Entity target) {
        World world = fox.getWorld();
        Vec3d from = fox.getPos().add(0, fox.getStandingEyeHeight(), 0);
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);

        RaycastContext ctx = new RaycastContext(
            from, to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            fox
        );
        BlockHitResult hit = world.raycast(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Convenience: both checks combined. Most callers want this.
     */
    public static boolean canAttackWithLineOfSight(FoxEntity fox, ServerPlayerEntity target) {
        return canAttack(fox, target) && hasLineOfSight(fox, target);
    }
}
