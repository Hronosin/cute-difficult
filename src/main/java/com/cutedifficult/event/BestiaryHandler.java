package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.item.ModItems;
import com.cutedifficult.spirit.BestiaryData;
import com.cutedifficult.spirit.Element;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Opens the Bestiary via {@link UseItemCallback}.
 *
 * <p>v0.5.2: previous version put the open-book logic in
 * {@code BestiaryOfInariItem.use()}, but that method was never reached —
 * {@code UseItemCallback} consumers (e.g. {@code TotemEffectHandler})
 * fire before {@code Item.use()} and one of them apparently intercepts
 * the event chain even when returning PASS. Moving the open-book logic
 * directly into a high-priority callback bypasses the issue.
 *
 * <p>This handler is registered AFTER TotemEffectHandler in init order
 * but both check the held stack item — the totem handler bails for
 * non-totem items, so our callback effectively runs in parallel without
 * collision.
 */
public final class BestiaryHandler {

    private BestiaryHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(ModItems.BESTIARY_OF_INARI)) {
                return TypedActionResult.pass(stack);
            }
            if (world.isClient) {
                // Client must claim "success" to play the right-click animation
                // and to NOT swing the arm awkwardly.
                return TypedActionResult.success(stack);
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(stack);
            }

            openBestiary(serverPlayer, hand);
            return TypedActionResult.success(stack);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] BestiaryHandler registered.");
    }

    private static void openBestiary(ServerPlayerEntity player, Hand hand) {
        ItemStack book = buildBestiaryBook(player);

        // Swap into offhand temporarily so we can send the open-book packet
        // for OFF_HAND. The packet's hand parameter selects which slot
        // the client renders the book from.
        PlayerInventory inv = player.getInventory();
        ItemStack originalOffhand = inv.offHand.get(0).copy();
        inv.offHand.set(0, book);

        player.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(Hand.OFF_HAND));

        // Restore offhand contents on the next server task. The client
        // already has the book content cached for the open screen.
        player.getServer().execute(() -> {
            inv.offHand.set(0, originalOffhand);
            player.playerScreenHandler.syncState();
        });
    }

    private static ItemStack buildBestiaryBook(ServerPlayerEntity player) {
        Set<String> entries = BestiaryData.getEntries(player);
        int count = entries.size();

        List<RawFilteredPair<Text>> pages = new ArrayList<>();

        pages.add(RawFilteredPair.of(
            Text.literal("Bestiary of Inari\n\n")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Discovered: " + count + " / " + BestiaryData.MAX_ENTRIES + "\n\n")
                    .formatted(Formatting.RESET))
                .append(Text.literal("The kitsune walk among us. Those whom you have inquired upon are recorded here.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC))
        ));

        for (Element element : Element.values()) {
            pages.add(RawFilteredPair.of(buildElementPage(element, entries)));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContentComponent content = new WrittenBookContentComponent(
            RawFilteredPair.of("Bestiary of Inari"),
            "Kitsune Spirit",
            0,
            pages,
            true
        );
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static Text buildElementPage(Element element, Set<String> entries) {
        var page = Text.literal(element.kamiName() + "\n")
            .formatted(element.color(), Formatting.BOLD)
            .copy()
            .append(Text.literal(element.flavor() + "\n\n")
                .formatted(Formatting.RESET, Formatting.ITALIC));

        String[] tiers = {"young", "matured", "venerable", "ancient", "Kyuubi"};
        for (String tier : tiers) {
            String key = element.name() + ":" + tier;
            if (entries.contains(key)) {
                page = page.copy().append(Text.literal("✓ ").formatted(Formatting.GREEN))
                    .append(Text.literal(tier + "\n").formatted(Formatting.WHITE));
            } else {
                page = page.copy().append(Text.literal("✗ ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("(unknown)\n").formatted(Formatting.DARK_GRAY));
            }
        }

        boolean anyDiscovered = false;
        for (String tier : tiers) {
            if (entries.contains(element.name() + ":" + tier)) {
                anyDiscovered = true;
                break;
            }
        }
        if (anyDiscovered) {
            page = page.copy()
                .append(Text.literal("\nFavored offering: ").formatted(Formatting.GRAY))
                .append(Text.literal(element.correctOffering().getName().getString())
                    .formatted(element.color()));
        }
        return page;
    }
}
