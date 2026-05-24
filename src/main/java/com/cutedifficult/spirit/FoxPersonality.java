package com.cutedifficult.spirit;

import net.minecraft.nbt.NbtCompound;

import java.util.Random;

/**
 * A fox's hidden personality. Seven traits, each 0..100.
 *
 * <p>These are NOT shown to the player anywhere — not in tooltips, not in
 * the Codex, not in death messages. The player learns about them indirectly
 * by observing fox behavior over time. Two foxes of the same element can
 * have wildly different "preferences" because their hidden traits differ.
 *
 * <p>Each trait drives different behaviors elsewhere in the spirit system:
 *
 * <ul>
 *   <li><b>Pride</b> — how quickly the fox takes offense. High pride =
 *       wrong offering triggers Lesser Mark more reliably; low pride =
 *       wrong offerings just bounce off.</li>
 *   <li><b>Trust</b> — how much Spirit a correct offering grants. High
 *       trust = full Spirit bonus. Trust grows with consecutive correct
 *       offerings, drops on offense.</li>
 *   <li><b>Curiosity</b> — how willing the fox is to approach the player.
 *       High curiosity = closer max approach distance, faster initial bonding.</li>
 *   <li><b>Memory</b> — how long the fox remembers actions. High memory
 *       = killing a fox witnessed by this one will be remembered for many
 *       days; low memory forgives in hours.</li>
 *   <li><b>Greed</b> — the fox demands progressively richer offerings to
 *       advance trust. High greed = single offering plateaus quickly.</li>
 *   <li><b>Sensitivity</b> — sensitivity to the player's current Spirit /
 *       Karma state. High sensitivity = a Hollow player is rejected even
 *       at distance; low sensitivity = the fox cares only about your
 *       actions today.</li>
 *   <li><b>Trauma</b> — initial debuff from a "wounded" past. High trauma
 *       = the fox starts with poor stats and is harder to befriend, but
 *       successful bonding eventually yields higher max reward.</li>
 * </ul>
 *
 * <p><b>Distribution at spawn:</b> each trait is independently rolled
 * from a beta distribution (approximated). Most foxes are moderate
 * across the board; outliers are rare but memorable.
 *
 * <p>Persisted in entity NBT under the {@code cd_personality} compound.
 * Generated once on first spawn-into-tracked-region; never re-randomized.
 */
public record FoxPersonality(
    int pride,
    int trust,
    int curiosity,
    int memory,
    int greed,
    int sensitivity,
    int trauma
) {
    /** NBT key under which a personality is stored in an entity. */
    public static final String NBT_KEY = "cd_personality";

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("pride", pride);
        nbt.putInt("trust", trust);
        nbt.putInt("curiosity", curiosity);
        nbt.putInt("memory", memory);
        nbt.putInt("greed", greed);
        nbt.putInt("sensitivity", sensitivity);
        nbt.putInt("trauma", trauma);
        return nbt;
    }

    public static FoxPersonality fromNbt(NbtCompound nbt) {
        return new FoxPersonality(
            nbt.getInt("pride"),
            nbt.getInt("trust"),
            nbt.getInt("curiosity"),
            nbt.getInt("memory"),
            nbt.getInt("greed"),
            nbt.getInt("sensitivity"),
            nbt.getInt("trauma")
        );
    }

    /**
     * Generate a random personality. Each trait is roughly normal-distributed
     * via "sum of two uniforms" — fast and statistically close enough to a
     * beta for our purposes. Means around 50, with rare values at extremes.
     */
    public static FoxPersonality random(Random rng) {
        return new FoxPersonality(
            rollTrait(rng),
            rollTrait(rng),
            rollTrait(rng),
            rollTrait(rng),
            rollTrait(rng),
            rollTrait(rng),
            rollTrait(rng)
        );
    }

    private static int rollTrait(Random rng) {
        // Two uniforms sum to triangular distribution centered at 50.
        return (rng.nextInt(51) + rng.nextInt(51));
    }
}
