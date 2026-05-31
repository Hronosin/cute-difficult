package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.EnchantMarkers;
import com.cutedifficult.spirit.EnhancedEnchant;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.KitsuneData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The kitsune enchantment-transformation system.
 *
 * <p>Give a kitsune an enchanted item (weapon/tool/armor — NOT a book, since
 * anvils strip custom data) whose enchantment matches one of the fox's
 * element enchants. After {@link #TRANSFORM_TICKS} the item gains an enhanced
 * blessing marker (stackable — see {@link EnchantMarkers}) plus updated lore.
 *
 * <p>Multiple different-element blessings can stack on one item: take your
 * Kasai-blessed sword to a Kaminari fox and it'll add the lightning blessing
 * too.
 */
public final class FoxEnchantHandler {

    private static final int TRANSFORM_TICKS = 600; // 30 seconds
    private static final int MIN_TRUST = 20;

    private static final Map<UUID, TransformState> ACTIVE = new ConcurrentHashMap<>();

    private FoxEnchantHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack held = player.getStackInHand(hand);
            if (held.isEmpty()) return ActionResult.PASS;
            boolean hasAnyEnchant =
                held.get(DataComponentTypes.STORED_ENCHANTMENTS) != null
                || held.get(DataComponentTypes.ENCHANTMENTS) != null;
            if (!hasAnyEnchant) return ActionResult.PASS;

            return tryBeginTransform((ServerWorld) world, sp, fox, held);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) return;
            var it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                TransformState state = entry.getValue();
                state.ticksRemaining--;

                Entity e = null;
                for (ServerWorld w : server.getWorlds()) {
                    e = w.getEntity(entry.getKey());
                    if (e != null) break;
                }
                if (!(e instanceof FoxEntity fox) || !fox.isAlive()) {
                    it.remove();
                    continue;
                }

                ServerWorld world = (ServerWorld) fox.getWorld();
                if (state.ticksRemaining % 10 == 0) {
                    world.spawnParticles(ParticleTypes.ENCHANT,
                        fox.getX(), fox.getY() + 0.8, fox.getZ(),
                        6, 0.3, 0.3, 0.3, 0.5);
                }

                if (state.ticksRemaining <= 0) {
                    completeTransform(world, fox, state);
                    it.remove();
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxEnchantHandler registered.");
    }

    private static ActionResult tryBeginTransform(ServerWorld world, ServerPlayerEntity player,
                                                  FoxEntity fox, ItemStack item) {
        KitsuneData data = FoxStorage.peekCache(fox);
        if (data == null) return ActionResult.PASS;
        if (data.trustLevel < MIN_TRUST) {
            player.sendMessage(Text.literal("The kitsune doesn't trust you enough to enchant. (need trust ≥ 20)")
                .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.FAIL;
        }
        if (ACTIVE.containsKey(fox.getUuid())) {
            player.sendMessage(Text.literal("This kitsune is already transforming something.")
                .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.FAIL;
        }

        // Gather the item's enchantments (gear uses ENCHANTMENTS, books STORED_).
        ItemEnchantmentsComponent enchants = item.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) {
            enchants = item.get(DataComponentTypes.STORED_ENCHANTMENTS);
        }
        if (enchants == null) return ActionResult.PASS;

        // Find any enchantment on the item that matches one of THIS element's
        // enhanced enchants.
        EnhancedEnchant chosen = null;
        int chosenLevel = 1;
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            var key = entry.getKey().orElse(null);
            if (key == null) continue;
            String path = key.getValue().getPath();
            EnhancedEnchant candidate = EnhancedEnchant.match(data.element, path);
            if (candidate != null) {
                chosen = candidate;
                chosenLevel = enchants.getLevel(entry);
                break;
            }
        }

        if (chosen == null) {
            player.sendMessage(Text.literal("This kitsune has no blessing for that item's enchantments.")
                .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.FAIL;
        }

        // Already blessed with this exact marker?
        if (EnchantMarkers.has(item, chosen.markerId)) {
            player.sendMessage(Text.literal("This item already carries that blessing.")
                .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.FAIL;
        }

        ItemStack taken = item.copy();
        taken.setCount(1);
        if (taken.isOf(Items.ENCHANTED_BOOK)) {
            player.sendMessage(Text.literal("Tip: bless the actual gear, not a book — anvils strip the blessing.")
                .formatted(Formatting.YELLOW, Formatting.ITALIC), false);
        }
        if (!player.isCreative()) item.decrement(1);

        fox.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, taken);
        ACTIVE.put(fox.getUuid(), new TransformState(chosen, TRANSFORM_TICKS, taken, chosenLevel));

        world.spawnParticles(ParticleTypes.ENCHANT,
            fox.getX(), fox.getY() + 1, fox.getZ(), 20, 0.4, 0.4, 0.4, 1.0);
        world.playSound(null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        player.sendMessage(Text.literal("The kitsune takes it and begins to transform it...")
            .formatted(chosen.color(), Formatting.ITALIC), false);
        return ActionResult.SUCCESS;
    }

    private static void completeTransform(ServerWorld world, FoxEntity fox, TransformState state) {
        EnhancedEnchant ench = state.enchant;
        ItemStack result = state.original.copy();

        // Add the stacking marker.
        EnchantMarkers.add(result, ench.markerId, state.level);

        // Rebuild lore from ALL markers now on the item.
        Map<String, Integer> markers = EnchantMarkers.read(result);
        List<Text> lore = new ArrayList<>();
        for (Map.Entry<String, Integer> m : markers.entrySet()) {
            EnhancedEnchant e = EnhancedEnchant.byMarker(m.getKey());
            if (e == null) continue;
            String suffix = m.getValue() > 1 ? " " + toRoman(m.getValue()) : "";
            lore.add(Text.literal("✦ " + e.displayName + suffix).formatted(e.color(), Formatting.BOLD));
            lore.add(Text.literal("  " + e.loreDescription).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        }
        result.set(DataComponentTypes.LORE, new LoreComponent(lore));

        fox.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        ItemEntity drop = new ItemEntity(world, fox.getX(), fox.getY() + 0.5, fox.getZ(), result);
        drop.setPickupDelay(10);
        world.spawnEntity(drop);

        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
            fox.getX(), fox.getY() + 1, fox.getZ(), 30, 0.5, 0.5, 0.5, 0.3);
        world.playSound(null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.NEUTRAL, 1.0f, 1.5f);
    }

    private static final class TransformState {
        final EnhancedEnchant enchant;
        int ticksRemaining;
        final ItemStack original;
        final int level;

        TransformState(EnhancedEnchant enchant, int ticksRemaining, ItemStack original, int level) {
            this.enchant = enchant;
            this.ticksRemaining = ticksRemaining;
            this.original = original;
            this.level = level;
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }
}
