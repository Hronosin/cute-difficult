package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.util.DifficultyMode;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * The crafting table is a trickster. When the player closes a crafting
 * interface, there's a chance it sneaks in one extra craft using whatever
 * ingredients the player happens to have. The player gets no say in what
 * gets crafted.
 *
 * <p><b>How:</b> on screen-close, roll {@link #SABOTAGE_CHANCE}. If it
 * triggers, scan the {@link #SABOTAGE_RECIPES} list for entries whose
 * ingredients are all present in the player's inventory. Pick a random
 * matching one. Consume the ingredients, give the output (or drop near
 * player if inventory is full).
 *
 * <p><b>Per-player cooldown:</b> 30 seconds. Prevents the player from
 * being repeatedly punished when actively crafting many things in
 * sequence. Stored in a {@link WeakHashMap} keyed by player UUID — entries
 * are garbage-collected when no longer referenced anywhere, so we don't
 * leak memory across long sessions.
 *
 * <p><b>Recipe design:</b> outputs are chosen to be annoying but not
 * game-breaking. Common patterns:
 * <ul>
 *   <li>Conversions that waste materials (planks → sticks when you
 *       wanted planks; iron → cauldron when you wanted ingots).</li>
 *   <li>Duplicates of things you already have (another crafting table).</li>
 *   <li>Items with niche use (bowls, hay bales).</li>
 *   <li>Things that lock up materials you need for something else
 *       (paper + compass → empty map: wastes your compass).</li>
 * </ul>
 *
 * <p>No "destructive" sabotages (no eating diamonds, no destroying
 * netherite). The mod hurts, but it's not griefing.
 *
 * <p><b>Active only in CRUEL mode.</b>
 */
public final class CraftingSabotageHandler {

    private static final float SABOTAGE_CHANCE = 0.15f;
    private static final long COOLDOWN_MS = 30_000L;
    private static final Random RANDOM = new Random();

    /** Per-player cooldown tracking. WeakHashMap so dead players GC out. */
    private static final Map<UUID, Long> LAST_SABOTAGE = new WeakHashMap<>();

    /** Single requirement: matcher predicate + count. */
    private record Requirement(Predicate<ItemStack> matcher, int count) {}

    /** A trolling "recipe" the table can autonomously execute. */
    private record SabotageRecipe(String displayName, List<Requirement> requirements, ItemStack output) {}

    private static Requirement requireItem(Item item, int count) {
        return new Requirement(stack -> stack.isOf(item), count);
    }

    private static Requirement requireTag(TagKey<Item> tag, int count) {
        return new Requirement(stack -> stack.isIn(tag), count);
    }

    /** Curated list of trolling recipes. Each one is real-ish vanilla output. */
    private static final List<SabotageRecipe> SABOTAGE_RECIPES = List.of(
        new SabotageRecipe(
            "a fresh handful of sticks",
            List.of(requireTag(ItemTags.PLANKS, 2)),
            new ItemStack(Items.STICK, 4)
        ),
        new SabotageRecipe(
            "another crafting table (you can never have too many)",
            List.of(requireTag(ItemTags.PLANKS, 4)),
            new ItemStack(Items.CRAFTING_TABLE, 1)
        ),
        new SabotageRecipe(
            "a set of fine wooden bowls",
            List.of(requireTag(ItemTags.PLANKS, 3)),
            new ItemStack(Items.BOWL, 4)
        ),
        new SabotageRecipe(
            "a book (you needed paper for something else, didn't you?)",
            List.of(requireItem(Items.PAPER, 3), requireItem(Items.LEATHER, 1)),
            new ItemStack(Items.BOOK, 1)
        ),
        new SabotageRecipe(
            "a single piece of white wool",
            List.of(requireItem(Items.STRING, 4)),
            new ItemStack(Items.WHITE_WOOL, 1)
        ),
        new SabotageRecipe(
            "an entirely unwanted furnace",
            List.of(requireItem(Items.COBBLESTONE, 8)),
            new ItemStack(Items.FURNACE, 1)
        ),
        new SabotageRecipe(
            "a single loaf of bread",
            List.of(requireItem(Items.WHEAT, 3)),
            new ItemStack(Items.BREAD, 1)
        ),
        new SabotageRecipe(
            "a hay bale (there go your crops)",
            List.of(requireItem(Items.WHEAT, 9)),
            new ItemStack(Items.HAY_BLOCK, 1)
        ),
        new SabotageRecipe(
            "an iron ingot (compressed from your nuggets)",
            List.of(requireItem(Items.IRON_NUGGET, 9)),
            new ItemStack(Items.IRON_INGOT, 1)
        ),
        new SabotageRecipe(
            "an iron cauldron (a lot of iron for a water bucket alternative)",
            List.of(requireItem(Items.IRON_INGOT, 7)),
            new ItemStack(Items.CAULDRON, 1)
        ),
        new SabotageRecipe(
            "three ladders (going somewhere?)",
            List.of(requireItem(Items.STICK, 7)),
            new ItemStack(Items.LADDER, 3)
        ),
        new SabotageRecipe(
            "an empty map (your compass is gone, by the way)",
            List.of(requireItem(Items.PAPER, 8), requireItem(Items.COMPASS, 1)),
            new ItemStack(Items.MAP, 1)
        ),
        new SabotageRecipe(
            "a torch (just one, alone in the dark)",
            List.of(requireItem(Items.STICK, 1), requireItem(Items.COAL, 1)),
            new ItemStack(Items.TORCH, 4)
        )
    );

    private CraftingSabotageHandler() {}

    /**
     * Called from the {@code CraftingScreenHandlerMixin} when a crafting
     * UI closes. Rolls the sabotage chance, performs it if it lands.
     */
    public static void trySabotage(ServerPlayerEntity player) {
        if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
        if (RANDOM.nextFloat() >= SABOTAGE_CHANCE) return;

        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();
        Long last = LAST_SABOTAGE.get(uuid);
        if (last != null && now - last < COOLDOWN_MS) return;

        PlayerInventory inv = player.getInventory();
        List<SabotageRecipe> craftable = new ArrayList<>();
        for (SabotageRecipe recipe : SABOTAGE_RECIPES) {
            if (canCraft(inv, recipe)) {
                craftable.add(recipe);
            }
        }
        if (craftable.isEmpty()) return;

        SabotageRecipe chosen = craftable.get(RANDOM.nextInt(craftable.size()));
        execute(player, chosen);
        LAST_SABOTAGE.put(uuid, now);
    }

    private static boolean canCraft(PlayerInventory inv, SabotageRecipe recipe) {
        for (Requirement req : recipe.requirements()) {
            if (countMatching(inv, req.matcher()) < req.count()) {
                return false;
            }
        }
        return true;
    }

    private static int countMatching(PlayerInventory inv, Predicate<ItemStack> matcher) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consume(PlayerInventory inv, Predicate<ItemStack> matcher, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
            }
        }
    }

    private static void execute(ServerPlayerEntity player, SabotageRecipe recipe) {
        PlayerInventory inv = player.getInventory();

        for (Requirement req : recipe.requirements()) {
            consume(inv, req.matcher(), req.count());
        }

        ItemStack output = recipe.output().copy();
        if (!inv.insertStack(output)) {
            // No room — drop near the player. They'll see what was crafted.
            player.dropItem(output, false);
        }

        player.sendMessage(
            Text.literal("The crafting table giggled and fashioned ").formatted(Formatting.GOLD)
                .append(Text.literal(recipe.displayName()).formatted(Formatting.YELLOW))
                .append(Text.literal(".").formatted(Formatting.GOLD)),
            false
        );

        player.getWorld().playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_VILLAGER_NO,
            SoundCategory.PLAYERS,
            0.6f,
            1.6f
        );
    }
}
