package com.cutedifficult.quality;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quality system glue.
 *
 * <p>Quality is stored on the ItemStack via the CUSTOM_DATA NBT component
 * — a simple integer index into {@link QualityTier#values()}. CUSTOM_DATA
 * is the right place because (1) it persists with the stack across all
 * vanilla operations (drops, inventory transfers, chest storage), and
 * (2) it doesn't require us to register a new DataComponentType (which
 * involves codecs and registry work and is overkill for a single int).
 *
 * <p>On read, if no quality is set we DO NOT default to anything — null
 * means "no quality applied". This lets us treat freshly-spawned items
 * (like creative-mode `/give`) as untouched until they go through a
 * craft/loot pipeline that calls {@link #ensureQuality}.
 *
 * <p>{@link #ensureQuality} is idempotent — calls it multiple times
 * doesn't re-roll. Call it from craft and loot hooks.
 */
public final class ItemQuality {

    /** Key inside CUSTOM_DATA NBT compound. */
    private static final String NBT_KEY = "cd_quality";

    private static final Random RANDOM = new Random();

    private ItemQuality() {}

    /** Get the tier of a stack, or null if no quality has been set. */
    public static QualityTier get(ItemStack stack) {
        var custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return null;
        NbtCompound nbt = custom.copyNbt();
        if (!nbt.contains(NBT_KEY, NbtElement.INT_TYPE)) return null;
        int idx = nbt.getInt(NBT_KEY);
        if (idx < 0 || idx >= QualityTier.values().length) return null;
        return QualityTier.values()[idx];
    }

    /** Force a tier onto a stack (overwriting). */
    public static void set(ItemStack stack, QualityTier tier) {
        var custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,
            net.minecraft.component.type.NbtComponent.DEFAULT);
        NbtCompound nbt = custom.copyNbt();
        nbt.putInt(NBT_KEY, tier.ordinal());
        stack.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));

        // Apply display changes: prefix name with tier, add lore line.
        applyDisplay(stack, tier);
    }

    /**
     * Roll a quality if not present. Returns the (possibly new) tier.
     * Safe to call on stacks that already have quality — it won't re-roll.
     */
    public static QualityTier ensureQuality(ItemStack stack) {
        QualityTier existing = get(stack);
        if (existing != null) return existing;
        if (!appliesTo(stack)) return null;
        QualityTier rolled = QualityTier.roll(RANDOM);
        set(stack, rolled);
        return rolled;
    }

    /** Whether the quality system applies to this item at all. */
    public static boolean appliesTo(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof SwordItem
            || item instanceof MiningToolItem
            || item instanceof PickaxeItem
            || item instanceof AxeItem
            || item instanceof ShovelItem
            || item instanceof HoeItem
            || item instanceof TridentItem
            || item instanceof ShearsItem
            || item instanceof ArmorItem;
    }

    /**
     * Multiplier to apply to damage/protection for this stack. Defaults
     * to 1.0 if no quality (i.e., quality-free stacks act vanilla).
     */
    public static float multiplier(ItemStack stack) {
        QualityTier tier = get(stack);
        return tier == null ? 1.0f : tier.multiplier();
    }

    /**
     * Sets the visible name + lore line to reflect quality.
     */
    private static void applyDisplay(ItemStack stack, QualityTier tier) {
        // Prefix the name with "<Tier> " in the tier's color.
        Text vanillaName = stack.getItem().getName();
        Text newName = Text.literal(tier.displayName() + " ")
            .formatted(tier.color())
            .copy()
            .append(vanillaName.copy().formatted(Formatting.RESET));
        stack.set(DataComponentTypes.CUSTOM_NAME, newName);

        // Add a lore line with the multiplier — replace any prior quality
        // lore if it exists.
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal(String.format("Quality multiplier: %.2fx", tier.multiplier()))
            .formatted(tier.color(), Formatting.ITALIC));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
    }
}
