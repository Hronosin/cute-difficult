package com.cutedifficult.spirit;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores and reads multiple enhanced-enchant markers on a single item.
 *
 * <p>Previously each item could hold only one marker (a single string in
 * CUSTOM_DATA). That stopped a sword from carrying, say, both the Kasai
 * fire-AOE blessing and the Kaminari lightning blessing. Now we store a
 * compound under {@code cd_enhanced} mapping markerId → level, so any
 * number of different-element blessings can stack on one item.
 *
 * <p>Structure inside CUSTOM_DATA:
 * <pre>
 *   cd_enhanced: {
 *     kasai: 2,
 *     kaminari: 1,
 *     ...
 *   }
 * </pre>
 *
 * <p>Same-element re-application overwrites (keeps the higher level).
 */
public final class EnchantMarkers {

    public static final String ROOT_KEY = "cd_enhanced";

    private EnchantMarkers() {}

    /** Read all markers on an item as markerId → level. Empty if none. */
    public static Map<String, Integer> read(ItemStack stack) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (stack.isEmpty()) return out;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return out;
        NbtCompound nbt = custom.copyNbt();
        if (!nbt.contains(ROOT_KEY)) return out;
        NbtCompound root = nbt.getCompound(ROOT_KEY);
        for (String key : root.getKeys()) {
            out.put(key, root.getInt(key));
        }
        return out;
    }

    /** Does the item carry a marker for this element id? */
    public static boolean has(ItemStack stack, String markerId) {
        return read(stack).containsKey(markerId);
    }

    /** Level for a given marker, or 0 if absent. */
    public static int levelOf(ItemStack stack, String markerId) {
        return read(stack).getOrDefault(markerId, 0);
    }

    /**
     * Add or upgrade a marker on the item. If the same element is already
     * present, keeps the higher level. Returns the modified stack (same
     * instance, mutated).
     */
    public static ItemStack add(ItemStack stack, String markerId, int level) {
        NbtComponent custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = custom.copyNbt();
        NbtCompound root = nbt.contains(ROOT_KEY) ? nbt.getCompound(ROOT_KEY) : new NbtCompound();
        int existing = root.contains(markerId) ? root.getInt(markerId) : 0;
        root.putInt(markerId, Math.max(existing, level));
        nbt.put(ROOT_KEY, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }
}
