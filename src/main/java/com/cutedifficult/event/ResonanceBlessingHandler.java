package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-element passive blessings, with a "Great Blessing of Inari"
 * unlock when all nine are simultaneously active.
 *
 * <p><b>Mechanic:</b>
 * <ul>
 *   <li>Each element has its own threshold ({@link #BLESSING_THRESHOLD})
 *       on its Spirit value. When Spirit ≥ threshold for an element,
 *       that element's blessing is active.</li>
 *   <li>Each active blessing grants its own pair of status effects.
 *       All 9 can stack simultaneously.</li>
 *   <li>When ALL 9 are active, "Great Blessing of Inari" engages —
 *       a dramatic chat announcement plus the
 *       {@link FoxPeaceMode} flag is set on the player, making all
 *       kitsune neutral to them regardless of trust/witness status.</li>
 *   <li>When the player drops below 9 simultaneously, the great
 *       blessing disengages and kitsune resume normal behavior toward
 *       them (witnessed killings etc. take effect again).</li>
 * </ul>
 *
 * <p>Refresh interval {@link #REFRESH_INTERVAL} = 200 ticks (10s). All
 * status effects renewed with duration > refresh to avoid flicker.
 * Chat announcements only fire on state change.
 */
public final class ResonanceBlessingHandler {

    /** Spirit threshold per element to enable its blessing. */
    public static final int BLESSING_THRESHOLD = 10;

    private static final int REFRESH_INTERVAL = 200;
    private static final int EFFECT_DURATION = 300;

    /** Last-seen set of active elements per player — for change detection. */
    private static final Map<UUID, EnumSet<Element>> LAST_ACTIVE = new HashMap<>();

    /** Players currently holding the Great Blessing of Inari. */
    public static final Set<UUID> GREAT_BLESSING_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static long tickCounter = 0;

    private ResonanceBlessingHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            tickCounter++;
            if (tickCounter % REFRESH_INTERVAL != 0) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // v0.9.6: removed isCreative() skip — creative players should
                // still see blessings (essential for testing the system at all).
                // Spectator stays skipped — they're invisible observers.
                if (player.isSpectator()) continue;
                refreshPlayer(server, player);
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] ResonanceBlessingHandler registered (9-element model).");
    }

    private static void refreshPlayer(MinecraftServer server, ServerPlayerEntity player) {
        EnumSet<Element> active = EnumSet.noneOf(Element.class);
        StringBuilder debugSpirits = new StringBuilder();

        // Kegare gate: at DEFILED karma and above, the spirits withdraw their
        // favor entirely — no blessings can hold. Active set stays empty.
        SpiritData.KarmaTier ktier = SpiritData.karmaTier(server, player);
        boolean blessingsSuppressed =
            ktier == SpiritData.KarmaTier.DEFILED || ktier == SpiritData.KarmaTier.CURSED;

        for (Element element : Element.values()) {
            int value = SpiritData.get(server, player, element);
            debugSpirits.append(element.name()).append("=").append(value).append(" ");
            if (!blessingsSuppressed && value >= BLESSING_THRESHOLD) {
                active.add(element);
            }
        }

        // Debug log once per refresh cycle.
        CuteDifficult.LOGGER.info(
            "[CuteDifficult] Blessing refresh for {}: {} | active={} | threshold={}",
            player.getName().getString(), debugSpirits.toString().trim(),
            active, BLESSING_THRESHOLD);

        EnumSet<Element> previous = LAST_ACTIVE.computeIfAbsent(player.getUuid(), k -> EnumSet.noneOf(Element.class));

        // Apply effects for each active element.
        for (Element element : active) {
            applyElementBlessing(player, element);
        }

        // Detect newly-activated blessings for chat announcement.
        EnumSet<Element> justActivated = EnumSet.copyOf(active);
        justActivated.removeAll(previous);
        for (Element e : justActivated) {
            player.sendMessage(
                Text.literal("✦ Blessing of Inari — " + e.kamiName() + " ✦")
                    .formatted(e.color(), Formatting.BOLD),
                false
            );
        }

        // Detect just-lost blessings for muted "fade" message.
        EnumSet<Element> justLost = EnumSet.copyOf(previous);
        justLost.removeAll(active);
        for (Element e : justLost) {
            player.sendMessage(
                Text.literal(e.kamiName() + "'s favor fades.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                false
            );
        }

        // Great Blessing — all 9 active.
        boolean wasGreat = GREAT_BLESSING_PLAYERS.contains(player.getUuid());
        boolean isGreat = active.size() == Element.values().length;

        if (isGreat) {
            GREAT_BLESSING_PLAYERS.add(player.getUuid());
            // Apply persistent regen/saturation on top of the per-element effects.
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.REGENERATION, EFFECT_DURATION, 1, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SATURATION, EFFECT_DURATION, 0, false, false, true));
            if (!wasGreat) {
                player.sendMessage(Text.literal("").formatted(Formatting.GOLD), false);
                player.sendMessage(
                    Text.literal("══════════════════════").formatted(Formatting.GOLD),
                    false
                );
                player.sendMessage(
                    Text.literal("✦ GREAT BLESSING OF INARI ✦")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                    false
                );
                player.sendMessage(
                    Text.literal("All kitsune now walk in peace with you.")
                        .formatted(Formatting.YELLOW, Formatting.ITALIC),
                    false
                );
                player.sendMessage(
                    Text.literal("══════════════════════").formatted(Formatting.GOLD),
                    false
                );
            }
        } else {
            GREAT_BLESSING_PLAYERS.remove(player.getUuid());
            if (wasGreat) {
                player.sendMessage(
                    Text.literal("The Great Blessing wanes. The kitsune watch you again.")
                        .formatted(Formatting.DARK_RED, Formatting.ITALIC),
                    false
                );
            }
        }

        LAST_ACTIVE.put(player.getUuid(), active);
    }

    /**
     * Public query for other handlers — does this player currently have
     * the Great Blessing (and thus deserve kitsune peace)?
     */
    public static boolean hasGreatBlessing(ServerPlayerEntity player) {
        return GREAT_BLESSING_PLAYERS.contains(player.getUuid());
    }

    private static void applyElementBlessing(ServerPlayerEntity player, Element element) {
        switch (element) {
            case KASAI -> apply(player, StatusEffects.FIRE_RESISTANCE, 0);
            case MIZU -> {
                apply(player, StatusEffects.WATER_BREATHING, 0);
                apply(player, StatusEffects.DOLPHINS_GRACE, 0);
            }
            case DAICHI -> apply(player, StatusEffects.RESISTANCE, 0);
            case KAZE -> {
                apply(player, StatusEffects.SPEED, 0);
                apply(player, StatusEffects.JUMP_BOOST, 0);
            }
            case KAMINARI -> apply(player, StatusEffects.STRENGTH, 0);
            case MORI -> apply(player, StatusEffects.REGENERATION, 0);
            case KORI -> apply(player, StatusEffects.SLOW_FALLING, 0);
            case YUREI -> apply(player, StatusEffects.NIGHT_VISION, 0);
            case TENGOKU -> apply(player, StatusEffects.HERO_OF_THE_VILLAGE, 0);
        }
    }

    private static void apply(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amp) {
        boolean result = player.addStatusEffect(new StatusEffectInstance(
            effect, EFFECT_DURATION, amp, false, false, true));
        CuteDifficult.LOGGER.info(
            "[CuteDifficult] Applied effect to {}: result={}",
            player.getName().getString(), result);
    }
}
