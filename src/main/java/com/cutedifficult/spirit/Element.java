package com.cutedifficult.spirit;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

/**
 * The nine elements of the kitsune spirit system, in fixed order.
 *
 * <p>Each element has:
 * <ul>
 *   <li>A Japanese name ({@link #kamiName()}) — used in lore and the codex.</li>
 *   <li>A flavor description ({@link #flavor()}).</li>
 *   <li>A color ({@link #color()}) used for particle effects, tail glow,
 *       and codex entries.</li>
 *   <li>A "correct" offering item ({@link #correctOffering()}) — the item
 *       a fox of this element wants. Other items either do nothing
 *       (neutral) or actively offend (see {@link OfferingResult}).</li>
 *   <li>A list of items considered "offensive" to this element —
 *       offering one of these triggers a Lesser Mark, slows future
 *       interactions, and may shift the local social network.</li>
 * </ul>
 *
 * <p>This enum is the source of truth for the entire elemental matrix.
 * Other systems (offering handler, codex generator, shrine generator,
 * fox spawn logic) all read from here. Adding/removing an element means
 * exactly one edit point.
 *
 * <p>Design note: we use a record-like pattern instead of pure data
 * methods because the metadata is conceptually constant. If Java let us
 * have constants attached to enum cases without per-method override
 * boilerplate, we'd use that — but this is the cleanest available
 * version in plain Java 21.
 */
public enum Element {
    KASAI(
            "Kasai", "fire",
            "The element of consumption, sun, and forge. Foxes of Kasai burn with constant inner heat.",
            Formatting.RED,
            Items.MAGMA_CREAM,
            new Item[]{Items.WATER_BUCKET, Items.ICE, Items.BLUE_ICE, Items.SNOWBALL}
    ),
    MIZU(
            "Mizu", "water",
            "The element of flow, depths, and reflection. Mizu foxes seek still waters.",
            Formatting.AQUA,
            Items.TROPICAL_FISH,
            new Item[]{Items.MAGMA_CREAM, Items.BLAZE_POWDER, Items.GUNPOWDER}
    ),
    DAICHI(
            "Daichi", "earth",
            "The element of stone, root, and patience. Daichi foxes are heavy-pawed and slow to anger.",
            Formatting.GOLD,
            Items.AMETHYST_SHARD,
            new Item[]{Items.FEATHER, Items.PHANTOM_MEMBRANE}
    ),
    KAZE(
            "Kaze", "wind",
            "The element of breath, height, and sudden change. Kaze foxes are restless.",
            Formatting.WHITE,
            Items.PHANTOM_MEMBRANE,
            new Item[]{Items.STONE, Items.COBBLESTONE, Items.DIRT}
    ),
    KAMINARI(
            "Kaminari", "thunder",
            "The element of storms and revelation. Kaminari foxes appear only with rain on the horizon.",
            Formatting.YELLOW,
            Items.COPPER_INGOT,
            new Item[]{Items.LEATHER, Items.WHITE_WOOL, Items.BLACK_WOOL}
    ),
    MORI(
            "Mori", "forest",
            "The element of green life. Mori foxes refuse meat — they remember every grain of pollen.",
            Formatting.GREEN,
            Items.SWEET_BERRIES,
            new Item[]{Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.ROTTEN_FLESH}
    ),
    KORI(
            "Kori", "ice",
            "The element of stillness and preservation. Kori foxes do not blink.",
            Formatting.BLUE,
            Items.BLUE_ICE,
            new Item[]{Items.MAGMA_CREAM, Items.BLAZE_POWDER, Items.LAVA_BUCKET, Items.FIRE_CHARGE}
    ),
    YUREI(
            "Yurei", "spirit",
            "The element of memory and what remains. Yurei foxes can only be seen by the awakened.",
            Formatting.LIGHT_PURPLE,
            Items.ECHO_SHARD,
            new Item[]{Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE} // they reject mortal medicine
    ),
    TENGOKU(
            "Tengoku", "sky",
            "The element of celestial fire. Tengoku foxes look only at the stars.",
            Formatting.AQUA,
            Items.DRAGON_BREATH,
            new Item[]{Items.NETHERRACK, Items.SOUL_SAND, Items.SOUL_SOIL} // they reject the underworld
    );

    private final String kamiName;
    private final String shortName;
    private final String flavor;
    private final Formatting color;
    private final Item correctOffering;
    private final Item[] offensiveOfferings;

    Element(
            String kamiName,
            String shortName,
            String flavor,
            Formatting color,
            Item correctOffering,
            Item[] offensiveOfferings
    ) {
        this.kamiName = kamiName;
        this.shortName = shortName;
        this.flavor = flavor;
        this.color = color;
        this.correctOffering = correctOffering;
        this.offensiveOfferings = offensiveOfferings;
    }

    public String kamiName() { return kamiName; }
    public String shortName() { return shortName; }
    public String flavor() { return flavor; }
    public Formatting color() { return color; }
    public Item correctOffering() { return correctOffering; }
    public Item[] offensiveOfferings() { return offensiveOfferings; }

    public boolean isOffended(Item item) {
        for (Item bad : offensiveOfferings) {
            if (bad == item) return true;
        }
        return false;
    }

    /**
     * Returns a random element. Used at fox spawn to assign an element if
     * the fox doesn't have one yet.
     */
    public static Element random(java.util.Random rng) {
        return values()[rng.nextInt(values().length)];
    }
}