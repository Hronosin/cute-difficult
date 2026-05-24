package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Spawn-time mob modifications. Reacts to {@link ServerEntityEvents#ENTITY_LOAD}
 * which fires both for freshly spawned and chunk-loaded entities, so existing
 * mobs in a world get caught the next time their chunk loads.
 *
 * <p><b>Mechanics:</b>
 * <ul>
 *   <li>Creepers: charged + invisible.</li>
 *   <li>Horses (regular only — donkeys/mules already have fixed stats):
 *       max HP, movement speed, and jump strength forced to the lowest
 *       values that vanilla random would produce. Effectively "the
 *       breeding system never selected for anything good".</li>
 * </ul>
 *
 * <p><b>Why HorseEntity only:</b> only {@link HorseEntity} has randomized
 * stats in vanilla. DonkeyEntity, MuleEntity, ZombieHorseEntity, and
 * SkeletonHorseEntity have fixed stats and don't need our intervention.
 * Llamas and camels are intentionally left alone — llamas are caravan
 * utility, not racing mounts, and nerfing them breaks gameplay loops
 * the player needs.
 *
 * <p><b>Vanilla horse stat ranges</b> (for reference, source: 1.21.1 vanilla):
 * <ul>
 *   <li>Max HP: 15-30 (we force 15)</li>
 *   <li>Movement speed: 0.1125-0.3375 (we force 0.1125)</li>
 *   <li>Jump strength: 0.4-1.0 (we force 0.4)</li>
 * </ul>
 * At these floors, a horse is slower than a sprinting player, jumps lower
 * than a player without the jump-boost effect, and dies in two hits from
 * a basic mob. Mounted travel becomes a punishment, not a convenience.
 */
public final class MobSpawnHandler {
    private MobSpawnHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            if (entity.getType() == EntityType.CREEPER && entity instanceof CreeperEntity creeper) {
                makeChargedAndInvisible(creeper);
            } else if (entity instanceof HorseEntity horse) {
                makePathetic(horse);
            }
        });
    }

    // === Creepers ===

    private static void makeChargedAndInvisible(CreeperEntity creeper) {
        NbtCompound nbt = new NbtCompound();
        creeper.writeNbt(nbt);
        if (!nbt.getBoolean("powered")) {
            nbt.putBoolean("powered", true);
            creeper.readNbt(nbt);
        }
        applyInvisibility(creeper);
    }

    private static void applyInvisibility(LivingEntity entity) {
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                StatusEffectInstance.INFINITE,
                0,
                true,
                false,
                false
        ));
    }

    // === Horses ===

    /**
     * Forces a horse's stats to the worst values that vanilla random would
     * naturally roll. Uses {@code setBaseValue} so the change is permanent
     * and serialized (won't be re-rolled on save/load).
     *
     * <p><b>Attribute names in 1.21.1:</b> {@code GENERIC_MAX_HEALTH} and
     * {@code GENERIC_MOVEMENT_SPEED} are confirmed. For jump strength,
     * Mojang renamed this between 1.20 ({@code HORSE_JUMP_STRENGTH}) and
     * 1.21.2 ({@code JUMP_STRENGTH}, no prefix). 1.21.1 is in between; I'm
     * using {@code GENERIC_JUMP_STRENGTH} as the most likely transitional
     * name. If your IDE flags it red, see the comment in
     * {@link #setHorseJumpFloor} for alternatives.
     */
    private static void makePathetic(HorseEntity horse) {
        setBaseValueSafe(horse, EntityAttributes.GENERIC_MAX_HEALTH, 15.0);
        setBaseValueSafe(horse, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1125);
        setHorseJumpFloor(horse);

        // Heal to the new (lower) max so we don't see a brief overheal.
        horse.setHealth(15.0f);
    }

    /**
     * Sets the jump strength attribute. Isolated in its own method because
     * the attribute name is the most likely thing to differ between
     * yarn builds. If the compiler complains about
     * {@code EntityAttributes.GENERIC_JUMP_STRENGTH}, try:
     * <ul>
     *   <li>{@code EntityAttributes.HORSE_JUMP_STRENGTH} (older yarn)</li>
     *   <li>{@code EntityAttributes.JUMP_STRENGTH} (newer yarn, 1.21.2+)</li>
     * </ul>
     * In IntelliJ: type {@code EntityAttributes.} and Ctrl+Space to see
     * what's actually available.
     */
    private static void setHorseJumpFloor(HorseEntity horse) {
        setBaseValueSafe(horse, EntityAttributes.GENERIC_JUMP_STRENGTH, 0.4);
    }

    private static void setBaseValueSafe(
            LivingEntity entity,
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
            double value
    ) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}