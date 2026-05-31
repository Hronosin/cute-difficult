package com.cutedifficult.spirit;

import net.minecraft.util.Formatting;

/**
 * The enhanced enchantments — kitsune-transformed versions of vanilla
 * enchantments. Give a kitsune an enchanted item whose enchantment matches
 * its element, wait 30 seconds, and it transforms into the meme-named
 * enhanced version.
 *
 * <p>Multiple per element. Stacking via {@link EnchantMarkers}.
 *
 * <p><b>Effect status</b> (as of this pass):
 * <ul>
 *   <li>EFFECT_LIVE — buffed behavior is wired up in a handler</li>
 *   <li>EFFECT_TODO — name + lore only; currently just the vanilla enchant
 *       plus a cool title. Effect to be implemented in a later pass.</li>
 * </ul>
 * The {@code effectLive} flag documents which is which.
 */
public enum EnhancedEnchant {
    // ===== Kaminari (lightning) =====
    KAMINARI_CHANNEL(Element.KAMINARI, "channeling", "kaminari", "Thor, Son of Odin",
        "Lightning strikes along the trident's entire flight", true),
    KAMINARI_SHARP(Element.KAMINARI, "sharpness", "kaminari_raiden", "Raiden",
        "Each strike calls down a bolt of lightning", true),

    // ===== Kasai (fire) =====
    KASAI_FIRE(Element.KASAI, "fire_aspect", "kasai", "Hephaestus Had Enough",
        "Ignites everything within the strike radius", true),
    KASAI_FLAME(Element.KASAI, "flame", "kasai_plagueis", "The Tragedy of Darth Plagueis",
        "Flaming arrows explode into a burst of fire", true),
    KASAI_FIREPROT(Element.KASAI, "fire_protection", "kasai_fine", "This Is Fine",
        "Stand in fire and feel nothing", true),

    // ===== Mizu (water) =====
    MIZU_RIPTIDE(Element.MIZU, "riptide", "mizu", "Now This Is Water Bending",
        "Riptide works without rain or water, and launches farther", true),
    MIZU_DEPTH(Element.MIZU, "depth_strider", "mizu_phelps", "Michael Phelps",
        "Move through water as if it were air", true),
    MIZU_RESPIRATION(Element.MIZU, "respiration", "mizu_breath", "Holding Breath Simulator",
        "Breathe underwater indefinitely", true),

    // ===== Daichi (earth) =====
    DAICHI_UNBREAK(Element.DAICHI, "unbreaking", "daichi", "Built Different",
        "Almost never breaks and slowly repairs itself", true),
    DAICHI_PROTECTION(Element.DAICHI, "protection", "daichi_nano", "Nanomachines, son!",
        "Hardens in response to damage — brief Resistance when hit", true),
    DAICHI_BLASTPROT(Element.DAICHI, "blast_protection", "daichi_inevitable", "I Am Inevitable",
        "Explosions barely scratch you", true),

    // ===== Kaze (wind) =====
    KAZE_FEATHER(Element.KAZE, "feather_falling", "kaze", "I Believe I Can Fly",
        "Full fall protection and a double jump", true),
    KAZE_SNEAK(Element.KAZE, "swift_sneak", "kaze_sneaky", "Sneaky Beaky Like",
        "Sneak at full walking speed", true),

    // ===== Mori (forest) =====
    MORI_FORTUNE(Element.MORI, "fortune", "mori", "I'll farm up the last item and get out of the forest, I promise",
        "Fortune works on every block, even plain ones", true),
    MORI_LOOTING(Element.MORI, "looting", "mori_goblin", "Loot Goblin",
        "Mobs drop far more loot", true),
    MORI_SWEEP(Element.MORI, "sweeping_edge", "mori_dance", "Let's dance!",
        "Sweeping strikes hit harder and reach farther", true),

    // ===== Kori (ice) =====
    KORI_FROST(Element.KORI, "frost_walker", "kori", "Absolute Zero",
        "Freezes enemies that come close", true),
    KORI_SHARP(Element.KORI, "sharpness", "kori_subzero", "Sub-Zero",
        "Each strike freezes the target solid", true),

    // ===== Yurei (spirit) =====
    YUREI_SOUL(Element.YUREI, "soul_speed", "yurei", "Ghost in the Shell",
        "Soul Speed everywhere, walk through cobwebs", true),
    YUREI_SWEEP(Element.YUREI, "sweeping_edge", "yurei_omnislash", "Omnislash",
        "Attacks pass through multiple enemies at once", true),

    // ===== Tengoku (sky) =====
    TENGOKU_MENDING(Element.TENGOKU, "mending", "tengoku", "Photosynthesis Respecter",
        "Repairs itself from sunlight", true),
    TENGOKU_POWER(Element.TENGOKU, "power", "tengoku_sunshot", "Sunshot",
        "Arrows ignite and glow, lighting their path", true),
    TENGOKU_INFINITY(Element.TENGOKU, "infinity", "tengoku_glowstick", "Infinite Glowstick",
        "Never run out, and light up the dark", true);

    public final Element element;
    /** The vanilla enchantment path that triggers this transformation. */
    public final String vanillaEnchantPath;
    /** Unique marker id stored on the item (allows multiple per element). */
    public final String markerId;
    /** Meme display name applied to the transformed item. */
    public final String displayName;
    /** Lore line describing the buff. */
    public final String loreDescription;
    /** True if the buffed effect is actually wired up in a handler. */
    public final boolean effectLive;

    EnhancedEnchant(Element element, String vanillaEnchantPath, String markerId,
                    String displayName, String loreDescription, boolean effectLive) {
        this.element = element;
        this.vanillaEnchantPath = vanillaEnchantPath;
        this.markerId = markerId;
        this.displayName = displayName;
        this.loreDescription = loreDescription;
        this.effectLive = effectLive;
    }

    public Formatting color() {
        return element.color();
    }

    /**
     * Find an enhanced enchant matching this element AND containing the given
     * vanilla enchantment path. Returns null if no match.
     */
    public static EnhancedEnchant match(Element element, String vanillaPath) {
        for (EnhancedEnchant e : values()) {
            if (e.element == element && e.vanillaEnchantPath.equals(vanillaPath)) {
                return e;
            }
        }
        return null;
    }

    /** All enhanced enchants for a given element. */
    public static java.util.List<EnhancedEnchant> forElement(Element element) {
        java.util.List<EnhancedEnchant> out = new java.util.ArrayList<>();
        for (EnhancedEnchant e : values()) {
            if (e.element == element) out.add(e);
        }
        return out;
    }

    /** Find by marker id, or null. */
    public static EnhancedEnchant byMarker(String marker) {
        for (EnhancedEnchant e : values()) {
            if (e.markerId.equals(marker)) return e;
        }
        return null;
    }
}
