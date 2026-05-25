package com.cutedifficult.item;

import com.cutedifficult.spirit.BestiaryData;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.Random;

/**
 * The Scroll of Inquiry — single-use item that records a fox's spiritual
 * data into the player's Bestiary.
 *
 * <p>Right-click on a fox while holding the scroll:
 * <ol>
 *   <li>If the fox's element + tail bucket is already recorded, the
 *       scroll is wasted (no benefit).</li>
 *   <li>If the entry is new, the scroll is consumed, a particle burst
 *       around the fox confirms success, and the bestiary entry is
 *       added with element name, tail count tier, and a brief flavor
 *       summary.</li>
 * </ol>
 *
 * <p>The scroll deliberately doesn't reveal the fox's personality
 * traits — those remain hidden forever. The Bestiary is "what kind of
 * kitsune exist", not "what is this specific one thinking".
 */
public class ScrollOfInquiryItem extends Item {

    private static final Random RANDOM = new Random();

    public ScrollOfInquiryItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
        if (!(user.getWorld() instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        KitsuneData data = FoxStorage.getOrCreate(fox, RANDOM);
        boolean isNew = BestiaryData.recordEntry(serverPlayer, data);

        if (!isNew) {
            // Already recorded — give a hint, don't consume the scroll.
            serverPlayer.sendMessage(
                Text.literal("This kitsune's nature is already known to you.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC),
                true
            );
            return ActionResult.FAIL;
        }

        // New entry — consume scroll, celebrate.
        stack.decrement(1);

        serverWorld.spawnParticles(
            ParticleTypes.ENCHANT,
            fox.getX(), fox.getY() + 0.5, fox.getZ(),
            20, 0.4, 0.4, 0.4, 0.5
        );
        serverWorld.playSound(
            null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS,
            0.8f, 1.3f
        );

        serverPlayer.sendMessage(
            Text.literal("Recorded: ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(data.element.kamiName() + " kitsune (" + tailTier(data.tails) + ")")
                    .formatted(data.element.color())),
            false
        );

        return ActionResult.SUCCESS;
    }

    /**
     * Tail count tier label — we don't reveal exact number, only a
     * descriptive bucket. This preserves some mystery — a "young" kitsune
     * could be 1 or 2 tails, you have to look more carefully (or count
     * from the visual aura intensity) to know.
     */
    public static String tailTier(int tails) {
        if (tails <= 2) return "young";
        if (tails <= 4) return "matured";
        if (tails <= 6) return "venerable";
        if (tails <= 8) return "ancient";
        return "Kyuubi";
    }
}
