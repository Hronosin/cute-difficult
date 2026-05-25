package com.cutedifficult.client.hud;

import com.cutedifficult.network.SpiritOverlayPayload;
import com.cutedifficult.spirit.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumMap;
import java.util.Map;

/**
 * v0.9.7: replaced the verbose "✦ Great Blessing of Inari ✦" text badge
 * with a compact icon strip. Each element now has a small star icon in
 * its kami color shown next to its row when its blessing is active. A
 * golden ★ above the panel indicates the Great Blessing (all 9 active).
 *
 * <p>Threshold for "blessing active" mirrors {@code ResonanceBlessingHandler.BLESSING_THRESHOLD}
 * — when spirit ≥ 10 the icon turns on. We hardcode 10 here to avoid a
 * server-class dependency on the client side.
 */
public final class SpiritOverlayHud {

    private static final Map<Element, Integer> CURRENT_SPIRITS = new EnumMap<>(Element.class);
    private static int currentKarma = 0;
    private static boolean greatBlessing = false;
    private static boolean visible = true;
    private static boolean hasReceivedData = false;

    /** Same threshold as ResonanceBlessingHandler. Hardcoded here for client-side use. */
    private static final int BLESSING_THRESHOLD = 10;

    private static final int BG_COLOR = 0x99000000;
    private static final int BG_PADDING = 5;

    /** Icon character for an active blessing — a small star/sparkle. */
    private static final String ACTIVE_ICON = "✦";

    static {
        for (Element e : Element.values()) CURRENT_SPIRITS.put(e, 0);
    }

    private SpiritOverlayHud() {}

    public static void updateFromPayload(SpiritOverlayPayload payload) {
        for (Element e : Element.values()) {
            CURRENT_SPIRITS.put(e, payload.spirits().getOrDefault(e, 0));
        }
        currentKarma = payload.karma();
        greatBlessing = payload.greatBlessing();
        hasReceivedData = true;
    }

    public static boolean hasReceivedData() {
        return hasReceivedData;
    }

    public static void toggleVisibility() {
        visible = !visible;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!visible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        if (client.player == null) return;

        int screenWidth = ctx.getScaledWindowWidth();
        int screenHeight = ctx.getScaledWindowHeight();
        var textRenderer = client.textRenderer;

        final int panelInnerWidth = 110;
        final int rightMargin = 4;
        final int lineHeight = 10;

        // Content height: optional Great star line, 9 element rows, spacer, karma.
        int rows = Element.values().length + 1;
        int extraTop = greatBlessing ? lineHeight + 2 : 0;
        int contentHeight = extraTop + rows * lineHeight + 2;

        int contentX = screenWidth - panelInnerWidth - rightMargin;
        int contentY = (screenHeight - contentHeight) / 2;

        int bgX = contentX - BG_PADDING;
        int bgY = contentY - BG_PADDING;
        int bgW = panelInnerWidth + BG_PADDING * 2;
        int bgH = contentHeight + BG_PADDING * 2;
        ctx.fill(bgX, bgY, bgX + bgW, bgY + bgH, BG_COLOR);

        int x = contentX;
        int y = contentY;

        // Great Blessing — single bold gold star, right-aligned. Way more
        // compact than the old text badge that overflowed the panel.
        if (greatBlessing) {
            Text greatIcon = Text.literal("★ ★ ★")
                .formatted(Formatting.GOLD, Formatting.BOLD);
            int iconWidth = textRenderer.getWidth(greatIcon);
            ctx.drawTextWithShadow(textRenderer, greatIcon,
                contentX + (panelInnerWidth - iconWidth) / 2, y, 0xFFFFFF);
            y += lineHeight + 2;
        }

        // Per-element rows: [icon] [kami name] [: value]
        // The icon is colored in the kami color when blessing is active,
        // dark gray dot when not. This gives an at-a-glance overview of
        // which blessings are currently in effect.
        for (Element element : Element.values()) {
            int value = CURRENT_SPIRITS.getOrDefault(element, 0);
            boolean active = value >= BLESSING_THRESHOLD;

            Text icon;
            if (active) {
                icon = Text.literal(ACTIVE_ICON + " ").formatted(element.color());
            } else {
                icon = Text.literal("· ").formatted(Formatting.DARK_GRAY);
            }
            Text label = Text.literal(element.kamiName()).formatted(element.color());
            Text valueText = Text.literal(": " + value).formatted(Formatting.WHITE);

            int drawX = x;
            ctx.drawTextWithShadow(textRenderer, icon, drawX, y, 0xFFFFFF);
            drawX += textRenderer.getWidth(icon);
            ctx.drawTextWithShadow(textRenderer, label, drawX, y, 0xFFFFFF);
            drawX += textRenderer.getWidth(label);
            ctx.drawTextWithShadow(textRenderer, valueText, drawX, y, 0xFFFFFF);
            y += lineHeight;
        }

        y += 2;
        Formatting karmaColor = currentKarma > 0
            ? Formatting.DARK_RED
            : (currentKarma < 0 ? Formatting.AQUA : Formatting.GRAY);
        Text karmaLabel = Text.literal("  Karma").formatted(karmaColor);
        Text karmaValue = Text.literal(": " + currentKarma).formatted(Formatting.WHITE);
        ctx.drawTextWithShadow(textRenderer, karmaLabel, x, y, 0xFFFFFF);
        int karmaLabelWidth = textRenderer.getWidth(karmaLabel);
        ctx.drawTextWithShadow(textRenderer, karmaValue,
            x + karmaLabelWidth, y, 0xFFFFFF);
    }
}
