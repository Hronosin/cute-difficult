package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import com.cutedifficult.util.SmartMobsTuning;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;

/**
 * Spawn-time AI improvements for hostile mobs.
 *
 * <p><b>What this does</b> (delegating to per-class mixins for things that
 * can't be set as attributes/flags):
 * <ul>
 *   <li>{@code GENERIC_FOLLOW_RANGE} boost — all hostile mobs see roughly
 *       3x further than vanilla. Big enough that you can't safely "ignore"
 *       a base built within a chunk of you.</li>
 *   <li>Zombies: enables {@code canBreakDoors} flag so they break doors
 *       regardless of difficulty.</li>
 * </ul>
 *
 * <p><b>What this does NOT do</b> (handled in mixin files instead, because
 * they require modifying method behavior, not entity data):
 * <ul>
 *   <li>Spider x-ray vision — see {@link com.cutedifficult.mixin.SpiderEntityMixin}.</li>
 *   <li>Enderman water immunity — {@link com.cutedifficult.mixin.EndermanEntityMixin}.</li>
 *   <li>Creeper explosion commitment — {@link com.cutedifficult.mixin.CreeperEntityMixin}.</li>
 *   <li>Skeleton strafing — done by adjusting attack goals via mixin.</li>
 * </ul>
 */
public final class SmartMobsHandler {

    private SmartMobsHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            if (entity instanceof HostileEntity hostile) {
                extendFollowRange(hostile);
            }

            if (entity instanceof ZombieEntity zombie) {
                enableDoorBreaking(zombie);
            }

            if (entity instanceof EndermanEntity enderman) {
                grantWaterImmunity(enderman);
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] SmartMobsHandler registered.");
    }

    private static void extendFollowRange(HostileEntity mob) {
        EntityAttributeInstance follow = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (follow != null) {
            // Use setBaseValue so it persists across save/load.
            follow.setBaseValue(SmartMobsTuning.EXTENDED_FOLLOW_RANGE);
        }
    }

    /**
     * Forces the {@code CanBreakDoors} flag on. In vanilla, only zombies on
     * Hard difficulty get this; we apply it universally in CRUEL mode.
     *
     * <p>NBT-write trick: zombies have a "canBreakDoors" NBT field that
     * controls the door-breaking goal. We toggle it via NBT round-trip.
     */
    private static void enableDoorBreaking(ZombieEntity zombie) {
        if (!SmartMobsTuning.ZOMBIES_ALWAYS_BREAK_DOORS) return;
        zombie.setCanBreakDoors(true);
    }

    /**
     * Endermen normally take damage from water (and teleport away). With
     * this immunity, they keep advancing through rain and puddles. We use
     * a custom NBT tag the EndermanEntityMixin checks; alternatively the
     * mixin can blanket-disable water damage for all endermen, which is
     * simpler. We do the latter.
     *
     * <p>This method is a no-op stub — the actual immunity is applied
     * unconditionally in {@link com.cutedifficult.mixin.EndermanEntityMixin}.
     * Kept as a placeholder so future per-entity behavior can be hung
     * here if needed.
     */
    private static void grantWaterImmunity(EndermanEntity enderman) {
        // Intentionally empty. See EndermanEntityMixin.
    }
}
