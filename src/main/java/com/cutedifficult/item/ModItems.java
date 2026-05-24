package com.cutedifficult.item;

import com.cutedifficult.CuteDifficult;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/**
 * Registry for custom items.
 *
 * <p>v0.5 introduces:
 * <ul>
 *   <li>{@link #SCROLL_OF_INQUIRY} — consumable that, when right-clicked
 *       on a fox, records the fox's data into the player's Bestiary.</li>
 *   <li>{@link #BESTIARY_OF_INARI} — opens a read-only book showing all
 *       recorded kitsune knowledge so far.</li>
 * </ul>
 *
 * <p>Recipes are defined in {@code data/cutedifficult/recipes/*.json}.
 * Textures should live at {@code assets/cutedifficult/textures/item/}
 * — see the bottom of this file for filename conventions.
 */
public final class ModItems {

    public static final Item SCROLL_OF_INQUIRY = register("scroll_of_inquiry",
        new ScrollOfInquiryItem(new Item.Settings().maxCount(16)));

    public static final Item BESTIARY_OF_INARI = register("bestiary_of_inari",
        new BestiaryOfInariItem(new Item.Settings().maxCount(1)));

    private static Item register(String name, Item item) {
        RegistryKey<Item> key = RegistryKey.of(Registries.ITEM.getKey(),
            Identifier.of(CuteDifficult.MOD_ID, name));
        return Registry.register(Registries.ITEM, key, item);
    }

    public static void init() {
        CuteDifficult.LOGGER.info("[CuteDifficult] Items registered: scroll_of_inquiry, bestiary_of_inari");
    }

    private ModItems() {}

    /*
     * TEXTURE NOTES — both items need 16×16 PNG at:
     *   src/main/resources/assets/cutedifficult/textures/item/scroll_of_inquiry.png
     *   src/main/resources/assets/cutedifficult/textures/item/bestiary_of_inari.png
     *
     * MODEL JSONS go at:
     *   src/main/resources/assets/cutedifficult/models/item/scroll_of_inquiry.json
     *   src/main/resources/assets/cutedifficult/models/item/bestiary_of_inari.json
     *
     * Each model file is just:
     *   {"parent": "minecraft:item/generated",
     *    "textures": {"layer0": "cutedifficult:item/scroll_of_inquiry"}}
     *
     * Lang strings:
     *   src/main/resources/assets/cutedifficult/lang/en_us.json
     *     "item.cutedifficult.scroll_of_inquiry": "Scroll of Inquiry",
     *     "item.cutedifficult.bestiary_of_inari": "Bestiary of Inari",
     *
     * Without these, the items will appear as untextured purple/black checkered
     * blocks with raw translation key names — functional but ugly.
     */
}
