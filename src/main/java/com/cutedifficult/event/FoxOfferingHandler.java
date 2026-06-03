package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Blessings;
import com.cutedifficult.spirit.Curses;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.FoxPersonality;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
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
 * Right-click-fox-with-item interaction. The mechanical core of the
 * spiritual loop.
 *
 * <p>v0.4.1 additions:
 * <ul>
 *   <li>Successful offerings cross trust thresholds → grant element
 *       blessing (vanilla status effects, see {@link Blessings}).</li>
 *   <li>Offensive offerings can also trigger a curse on the player
 *       (see {@link Curses}) if the fox is angry enough.</li>
 *   <li>Trust gates: blessing only granted when trust crosses a
 *       multiple of {@link #BLESSING_TRUST_INTERVAL}, preventing
 *       blessing-spam on every single offering.</li>
 * </ul>
 */
public final class FoxOfferingHandler {

    private static final long FOX_COOLDOWN_TICKS = 200;
    private static final int BASE_SPIRIT_REWARD = 3;
    private static final int MAX_TRUST_GAIN = 8;
    private static final int KARMA_PER_OFFENSE = 5;
    private static final int SPIRIT_PENALTY_OFFENSE = 4;

    /** Grant a blessing every time trust crosses one of these milestones. */
    private static final int BLESSING_TRUST_INTERVAL = 20;

    /** If offending fox has Pride above this AND trust below this, apply curse. */
    private static final int CURSE_PRIDE_THRESHOLD = 70;
    private static final int CURSE_TRUST_CEILING = 20;

    private static final Random RANDOM = new Random();

    private FoxOfferingHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return ActionResult.PASS;
            if (!(entity instanceof FoxEntity fox)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (world.isClient || !(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) return ActionResult.PASS;

            return handleOffering(serverPlayer, serverWorld, fox, stack);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxOfferingHandler registered.");
    }

    private static ActionResult handleOffering(
        ServerPlayerEntity player, ServerWorld world, FoxEntity fox, ItemStack stack
    ) {
        KitsuneData data = FoxStorage.getOrCreate(fox, RANDOM);
        long now = world.getTime();

        if (now - data.lastFedTickStamp < FOX_COOLDOWN_TICKS) {
            return ActionResult.FAIL;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return ActionResult.PASS;

        Element element = data.element;
        FoxPersonality personality = data.personality;

        if (element.isAccepted(stack.getItem())) {
            // Kegare check: a polluted player's offerings are often rejected —
            // the spirits sense the stain. 50% failure at TAINTED and above.
            SpiritData.KarmaTier ktier = SpiritData.karmaTier(server, player);
            if (ktier != SpiritData.KarmaTier.PURE && world.random.nextFloat() < 0.5f) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                    fox.getX(), fox.getY() + 0.5, fox.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                player.sendMessage(net.minecraft.text.Text.literal(
                    "The kitsune recoils from your offering — it senses the stain on you.")
                    .formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.ITALIC), true);
                stack.decrement(1); // offering consumed but wasted
                return ActionResult.SUCCESS;
            }
            Element.OfferingTier tier = element.offeringTier(stack.getItem());
            return handleCorrect(player, world, fox, data, element, personality, stack, server, now, tier);
        } else if (element.isOffended(stack.getItem())) {
            return handleOffense(player, world, fox, data, element, personality, server, now);
        } else {
            return handleNeutral(player, world, fox);
        }
    }

    private static ActionResult handleCorrect(
        ServerPlayerEntity player, ServerWorld world, FoxEntity fox, KitsuneData data,
        Element element, FoxPersonality personality, ItemStack stack,
        MinecraftServer server, long now, Element.OfferingTier tier
    ) {
        boolean satisfied = (now - data.lastFedTickStamp < 24000)
            && (data.trustLevel > 50)
            && (personality.greed() > 60);

        if (satisfied) {
            sendMurmur(player, "The fox sniffs the offering, takes it, but does not look at you.", element);
            stack.decrement(1);
            FoxStorage.store(fox, data.withLastFed(now));
            return ActionResult.SUCCESS;
        }

        double trustFactor = 0.5 + (data.trustLevel / 100.0);
        double greedFactor = 1.0 - (personality.greed() / 200.0);

        // New moon night: the spirits listen closely — extra spirit gain.
        double lunarFactor = com.cutedifficult.event.LunarCycleHandler.isNewMoon(world) ? 1.5 : 1.0;

        // Astrology: sign-of-day / alignment / comet multiplier for this element.
        double astroFactor = com.cutedifficult.event.AstrologyHandler.spiritMultiplier(element);

        // Retrograde: the spirits may misread a perfectly good offering and
        // take it as an insult. Players will hate this. That's the point.
        // Tense aspect on this element's celestial body may misfire the offering.
        if (com.cutedifficult.event.AstrologyHandler.offeringMisfire(element)) {
            player.sendMessage(Text.literal("The stars are wrong for this element. The kitsune recoils — your gift felt like a slight.")
                .formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
            return handleOffense(player, world, fox, data, element, personality, server, now);
        }

        int spiritGain = Math.max(1, (int) Math.round(
            BASE_SPIRIT_REWARD * trustFactor * greedFactor * tier.multiplier * lunarFactor * astroFactor));

        SpiritData.add(server, player, element, spiritGain);

        int trustGain = Math.max(1, (int) Math.round(
            MAX_TRUST_GAIN * (1.0 - personality.trauma() / 200.0) * tier.multiplier));
        int oldTrust = data.trustLevel;
        int newTrust = oldTrust + trustGain;
        KitsuneData updated = data.withTrust(newTrust).withLastFed(now);
        FoxStorage.store(fox, updated);

        stack.decrement(1);

        // Tier-scaled visual feedback — premium offerings feel special.
        int particleCount = tier == Element.OfferingTier.PREMIUM ? 20
            : (tier == Element.OfferingTier.STANDARD ? 8 : 4);
        world.spawnParticles(
            ParticleTypes.HAPPY_VILLAGER,
            fox.getX(), fox.getY() + 0.6, fox.getZ(),
            particleCount, 0.3, 0.3, 0.3, 0.1
        );
        if (tier == Element.OfferingTier.PREMIUM) {
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                fox.getX(), fox.getY() + 0.8, fox.getZ(), 15, 0.4, 0.4, 0.4, 0.2);
        }
        world.playSound(
            null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_EAT,
            SoundCategory.NEUTRAL,
            1.0f, 1.0f + RANDOM.nextFloat() * 0.2f
        );

        String murmur = switch (tier) {
            case PREMIUM -> "The fox's eyes widen. This is a treasure. It will not forget this.";
            case CHEAP -> "The fox nibbles the humble offering. A small kindness, noted.";
            default -> "The fox accepts your offering. Something in the air shifts.";
        };
        sendMurmur(player, murmur, element);

        // Blessing milestone: cross a multiple of BLESSING_TRUST_INTERVAL.
        int oldMilestone = oldTrust / BLESSING_TRUST_INTERVAL;
        int newMilestone = updated.trustLevel / BLESSING_TRUST_INTERVAL;
        if (newMilestone > oldMilestone) {
            Blessings.grant(player, element, data.tails);
            sendMurmur(player,
                "A warmth settles over you. The kitsune of " + element.kamiName() + " favors you.",
                element
            );
            world.playSound(
                null, fox.getX(), fox.getY(), fox.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.NEUTRAL,
                0.6f, 1.4f
            );
        }

        return ActionResult.SUCCESS;
    }

    private static ActionResult handleOffense(
        ServerPlayerEntity player, ServerWorld world, FoxEntity fox, KitsuneData data,
        Element element, FoxPersonality personality,
        MinecraftServer server, long now
    ) {
        boolean offended = RANDOM.nextInt(100) < personality.pride();
        if (!offended) {
            sendMurmur(player, "The fox sniffs the offering, then walks away.", element);
            FoxStorage.store(fox, data.withLastFed(now));
            return ActionResult.PASS;
        }

        SpiritData.add(server, player, element, -SPIRIT_PENALTY_OFFENSE);
        SpiritData.addKarma(server, player, KARMA_PER_OFFENSE);
        KitsuneData updated = data.withTrust(data.trustLevel - 10).withLastFed(now);
        FoxStorage.store(fox, updated);

        world.spawnParticles(
            ParticleTypes.ANGRY_VILLAGER,
            fox.getX(), fox.getY() + 0.6, fox.getZ(),
            8, 0.3, 0.3, 0.3, 0.1
        );
        world.playSound(
            null, fox.getX(), fox.getY(), fox.getZ(),
            SoundEvents.ENTITY_FOX_SCREECH,
            SoundCategory.NEUTRAL,
            1.0f, 0.9f
        );

        sendMurmur(player, "The fox bares its teeth. You have offended it.", element);

        // Curse trigger: only proud, distrustful foxes curse the player.
        if (personality.pride() >= CURSE_PRIDE_THRESHOLD
            && updated.trustLevel <= CURSE_TRUST_CEILING) {
            Curses.inflict(player, element, data.tails);
            sendMurmur(player,
                "You feel something cold settle into your bones. The kitsune has cursed you.",
                element
            );
            world.playSound(
                null, fox.getX(), fox.getY(), fox.getZ(),
                SoundEvents.ENTITY_WITHER_HURT,
                SoundCategory.NEUTRAL,
                0.7f, 1.2f
            );
        }

        return ActionResult.FAIL;
    }

    private static ActionResult handleNeutral(
        ServerPlayerEntity player, ServerWorld world, FoxEntity fox
    ) {
        world.spawnParticles(
            ParticleTypes.SMOKE,
            fox.getX(), fox.getY() + 0.6, fox.getZ(),
            2, 0.1, 0.1, 0.1, 0.0
        );
        return ActionResult.PASS;
    }

    private static void sendMurmur(ServerPlayerEntity player, String message, Element element) {
        player.sendMessage(
            Text.literal(message).formatted(element.color(), Formatting.ITALIC),
            true
        );
    }
}
