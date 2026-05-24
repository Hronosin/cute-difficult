package com.cutedifficult.item;

import net.minecraft.item.Item;

/**
 * The Bestiary of Inari — just a marker item now. All the actual logic
 * (opening the generated book) lives in {@link com.cutedifficult.event.BestiaryHandler}.
 *
 * <p>v0.5.2: simplified after the {@code Item.use()} approach failed —
 * UseItemCallback consumers intercepted the event before reaching us.
 * Moved logic to a callback handler; this class is now just a registered
 * Item type with no behavior of its own.
 */
public class BestiaryOfInariItem extends Item {
    public BestiaryOfInariItem(Settings settings) {
        super(settings);
    }
}
