package com.cutedifficult.spirit;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

/**
 * The nine spiritual elements. Each kitsune belongs to exactly one.
 *
 * <p>Each element defines:
 * <ul>
 *   <li>Kami name, short id, flavor text, display color.</li>
 *   <li>Tiered offerings: a CHEAP item (small reward, easy to get), a
 *       STANDARD item (the classic mid reward), and a PREMIUM item (large
 *       reward, an endgame/rare resource). The offering handler scales
 *       trust/spirit gain by the tier multiplier.</li>
 *   <li>Offensive offerings: items that offend this element and apply a
 *       Lesser Mark / trust penalty.</li>
 * </ul>
 */
public enum Element {
    KASAI(
        "Kasai", "fire",
        "The element of consumption, sun, and forge. Foxes of Kasai burn with constant inner heat.",
        Formatting.RED,
        Items.COAL, Items.MAGMA_CREAM, Items.BLAZE_ROD,
        new Item[]{Items.WATER_BUCKET, Items.ICE, Items.BLUE_ICE, Items.SNOWBALL}
    ),
    MIZU(
        "Mizu", "water",
        "The element of flow, depths, and reflection. Mizu foxes seek still waters.",
        Formatting.AQUA,
        Items.COD, Items.TROPICAL_FISH, Items.HEART_OF_THE_SEA,
        new Item[]{Items.MAGMA_CREAM, Items.BLAZE_POWDER, Items.GUNPOWDER}
    ),
    DAICHI(
        "Daichi", "earth",
        "The element of stone, root, and patience. Daichi foxes are heavy-pawed and slow to anger.",
        Formatting.GOLD,
        Items.CALCITE, Items.AMETHYST_SHARD, Items.BUDDING_AMETHYST,
        new Item[]{Items.FEATHER, Items.PHANTOM_MEMBRANE}
    ),
    KAZE(
        "Kaze", "wind",
        "The element of breath, height, and sudden change. Kaze foxes are restless.",
        Formatting.WHITE,
        Items.FEATHER, Items.PHANTOM_MEMBRANE, Items.ELYTRA,
        new Item[]{Items.STONE, Items.COBBLESTONE, Items.DIRT}
    ),
    KAMINARI(
        "Kaminari", "thunder",
        "The element of storms and revelation. Kaminari foxes appear only with rain on the horizon.",
        Formatting.YELLOW,
        Items.COPPER_INGOT, Items.LIGHTNING_ROD, Items.TRIDENT,
        new Item[]{Items.LEATHER, Items.WHITE_WOOL, Items.BLACK_WOOL}
    ),
    MORI(
        "Mori", "forest",
        "The element of green life. Mori foxes refuse meat — they remember every grain of pollen.",
        Formatting.GREEN,
        Items.WHEAT_SEEDS, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
        new Item[]{Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.ROTTEN_FLESH}
    ),
    KORI(
        "Kori", "ice",
        "The element of stillness and preservation. Kori foxes do not blink.",
        Formatting.BLUE,
        Items.SNOWBALL, Items.BLUE_ICE, Items.PACKED_ICE,
        new Item[]{Items.MAGMA_CREAM, Items.BLAZE_POWDER, Items.LAVA_BUCKET, Items.FIRE_CHARGE}
    ),
    YUREI(
        "Yurei", "spirit",
        "The element of memory and what remains. Yurei foxes can only be seen by the awakened.",
        Formatting.LIGHT_PURPLE,
        Items.SOUL_SOIL, Items.ECHO_SHARD, Items.RECOVERY_COMPASS,
        new Item[]{Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE}
    ),
    TENGOKU(
        "Tengoku", "sky",
        "The element of celestial fire. Tengoku foxes look only at the stars.",
        Formatting.AQUA,
        Items.GLOWSTONE_DUST, Items.DRAGON_BREATH, Items.NETHER_STAR,
        new Item[]{Items.NETHERRACK, Items.SOUL_SAND, Items.SOUL_SOIL}
    );

    /** Offering quality tier with its reward multiplier. */
    public enum OfferingTier {
        NONE(0.0),
        CHEAP(0.5),
        STANDARD(1.0),
        PREMIUM(2.5);

        public final double multiplier;
        OfferingTier(double multiplier) { this.multiplier = multiplier; }
    }

    private final String kamiName;
    private final String shortName;
    private final String flavor;
    private final Formatting color;
    private final Item cheapOffering;
    private final Item standardOffering;
    private final Item premiumOffering;
    private final Item[] offensiveOfferings;

    Element(
        String kamiName,
        String shortName,
        String flavor,
        Formatting color,
        Item cheapOffering,
        Item standardOffering,
        Item premiumOffering,
        Item[] offensiveOfferings
    ) {
        this.kamiName = kamiName;
        this.shortName = shortName;
        this.flavor = flavor;
        this.color = color;
        this.cheapOffering = cheapOffering;
        this.standardOffering = standardOffering;
        this.premiumOffering = premiumOffering;
        this.offensiveOfferings = offensiveOfferings;
    }

    public String kamiName() { return kamiName; }
    public String shortName() { return shortName; }
    public String flavor() { return flavor; }
    public Formatting color() { return color; }

    public Item cheapOffering() { return cheapOffering; }
    public Item standardOffering() { return standardOffering; }
    public Item premiumOffering() { return premiumOffering; }

    /** Back-compat: the "canonical" offering shown in codex etc. */
    public Item correctOffering() { return standardOffering; }

    public Item[] offensiveOfferings() { return offensiveOfferings; }

    /** Which tier does this item represent for this element? */
    public OfferingTier offeringTier(Item item) {
        if (item == premiumOffering) return OfferingTier.PREMIUM;
        if (item == standardOffering) return OfferingTier.STANDARD;
        if (item == cheapOffering) return OfferingTier.CHEAP;
        return OfferingTier.NONE;
    }

    /** Is this item any accepted offering (cheap/standard/premium)? */
    public boolean isAccepted(Item item) {
        return offeringTier(item) != OfferingTier.NONE;
    }

    public boolean isOffended(Item item) {
        for (Item bad : offensiveOfferings) {
            if (bad == item) return true;
        }
        return false;
    }

    public static Element fromShortName(String name) {
        for (Element e : values()) {
            if (e.shortName.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    /** Pick a random element using the given RNG. */
    public static Element random(java.util.Random rng) {
        Element[] all = values();
        return all[rng.nextInt(all.length)];
    }
}
