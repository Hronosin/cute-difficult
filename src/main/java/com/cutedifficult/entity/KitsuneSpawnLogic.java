package com.cutedifficult.entity;

import com.cutedifficult.spirit.Element;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Decides a naturally-spawned kitsune's element (from its biome) and tail count
 * (weighted so young foxes are common and ancient nine-tails are rare).
 *
 * <p>Elements are grouped into biome families rather than one strict biome
 * each: e.g. any hot/dry biome favors Kasai, any frozen biome favors Kori. A
 * biome's dominant element is chosen, with a small chance of a different
 * element so encounters aren't fully predictable.
 */
public final class KitsuneSpawnLogic {

    private KitsuneSpawnLogic() {}

    /** Pick an element for the given biome, with a small chance of a wildcard. */
    public static Element elementFor(RegistryEntry<Biome> biome, Random random) {
        // ~15% wildcard: any element regardless of biome.
        if (random.nextInt(100) < 15) {
            Element[] all = Element.values();
            return all[random.nextInt(all.length)];
        }
        return dominantElement(biome);
    }

    /** The biome's thematic element, by tag families. */
    private static Element dominantElement(RegistryEntry<Biome> biome) {
        // Frozen / snowy → Kori (ice).
        if (biome.isIn(BiomeTags.IS_OCEAN) && isCold(biome)) return Element.MIZU;
        if (isCold(biome)) return Element.KORI;
        // Oceans / rivers / beaches → Mizu (water).
        if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)
            || biome.isIn(BiomeTags.IS_BEACH)) return Element.MIZU;
        // Forests / jungles / taiga → Mori (forest).
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)
            || biome.isIn(BiomeTags.IS_TAIGA)) return Element.MORI;
        // Hills / mountains → Daichi (earth); peaks lean Tengoku (sky).
        if (biome.isIn(BiomeTags.IS_MOUNTAIN)) {
            return Element.DAICHI;
        }
        // Badlands / savanna / hot dry → Kasai (fire) or Kaminari (storm).
        if (biome.isIn(BiomeTags.IS_BADLANDS)) return Element.KASAI;
        if (biome.isIn(BiomeTags.IS_SAVANNA)) return Element.KAMINARI;
        // Dark / spooky → Yurei (spirit). 1.21.1 lacks a single "spooky" tag,
        // so we approximate elsewhere (handled by wildcard / fallback).
        // Plains / meadows / open → Kaze (wind).
        return Element.KAZE;
    }

    private static boolean isCold(RegistryEntry<Biome> biome) {
        // Snowy biomes carry the spawns-cold-variant or freeze tags; the most
        // portable check in 1.21.1 is the temperature via the biome value.
        return biome.value().getTemperature() < 0.2f;
    }

    /**
     * Weighted tail count: young (1-2 tails) common, ancient (8-9) very rare.
     * Smooth falloff — each extra tail is progressively less likely.
     */
    public static int rollTails(Random random) {
        // Geometric-ish: keep rolling to go higher, ~45% chance to stop each step.
        int tails = 1;
        while (tails < 9 && random.nextInt(100) < 45) {
            tails++;
        }
        return tails;
    }
}
