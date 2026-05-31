package com.cutedifficult.client.gui;

import com.cutedifficult.spirit.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

/**
 * The Bestiary of Inari — a clean scrollable list of all nine elements and
 * their five tail-tiers, showing which the player has discovered.
 *
 * <p>Replaces the old book.png page-flip layout with a standard dark panel
 * and mouse-wheel scrolling, so the player can see everything at a glance.
 * Each element block shows: kami name, flavor, tiered offerings (cheap /
 * standard / premium), and a row of five tail-tier badges (lit if discovered).
 */
public class BestiaryScreen extends Screen {

    private final Set<String> entries;
    private final int totalDiscovered;
    private final int maxEntries;

    private double scroll = 0;
    private int contentHeight = 0;

    private static final String[] TIERS = {"young", "matured", "venerable", "ancient", "Kyuubi"};
    private static final int PANEL_WIDTH = 340;
    private static final int ELEMENT_BLOCK_HEIGHT = 78;
    private static final int HEADER_HEIGHT = 40;

    public BestiaryScreen(Set<String> entries, int maxEntries) {
        super(Text.literal("Bestiary of Inari"));
        this.entries = entries;
        this.totalDiscovered = entries.size();
        this.maxEntries = maxEntries;
    }

    @Override
    protected void init() {
        this.contentHeight = HEADER_HEIGHT + Element.values().length * ELEMENT_BLOCK_HEIGHT + 20;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewport = this.height - 40;
        double maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 18));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // NOTE: do NOT call this.renderBackground(...) — in 1.21.1 it applies
        // a gaussian blur that smears over everything we draw. Instead draw a
        // plain translucent darkening fill ourselves.
        ctx.fill(0, 0, this.width, this.height, 0xC0000000);

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelRight = panelLeft + PANEL_WIDTH;
        int top = 20;
        int bottom = this.height - 20;

        // Panel background.
        ctx.fill(panelLeft - 6, top - 6, panelRight + 6, bottom + 6, 0xF0100018);
        ctx.fill(panelLeft - 4, top - 4, panelRight + 4, bottom + 4, 0xFF2A1A3A);

        // Enable scissor so content clips to the panel.
        ctx.enableScissor(panelLeft - 4, top, panelRight + 4, bottom);

        int y = top - (int) scroll;

        // Header.
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("✦ Bestiary of Inari ✦").formatted(Formatting.GOLD, Formatting.BOLD),
                this.width / 2, y + 4, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Discovered " + totalDiscovered + " / " + maxEntries)
                        .formatted(Formatting.GRAY),
                this.width / 2, y + 18, 0xFFFFFF);
        y += HEADER_HEIGHT;

        // Element blocks.
        for (Element element : Element.values()) {
            renderElementBlock(ctx, element, panelLeft, y);
            y += ELEMENT_BLOCK_HEIGHT;
        }

        ctx.disableScissor();

        // Scroll hint.
        if (contentHeight > (bottom - top)) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("scroll ↕").formatted(Formatting.DARK_GRAY),
                    this.width / 2, bottom - 2, 0xFFFFFF);
        }
        // No widgets to draw — this screen is pure scroll content, so we
        // intentionally skip super.render() to avoid re-applying the blur.
    }

    private void renderElementBlock(DrawContext ctx, Element element, int panelLeft, int y) {
        int x = panelLeft + 8;

        // Element name.
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(element.kamiName()).formatted(element.color(), Formatting.BOLD),
                x, y, 0xFFFFFF);

        // Discovered count for this element.
        int discovered = 0;
        for (String tier : TIERS) {
            if (entries.contains(element.name() + ":" + tier)) discovered++;
        }
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(discovered + "/5").formatted(
                        discovered == 5 ? Formatting.GREEN : Formatting.GRAY),
                panelLeft + PANEL_WIDTH - 32, y, 0xFFFFFF);

        // Flavor (wrapped to one short line).
        String flavor = element.flavor();
        if (flavor.length() > 58) flavor = flavor.substring(0, 55) + "...";
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(flavor).formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                x, y + 11, 0xFFFFFF);

        // Tier badges — five boxes, lit if discovered.
        int badgeY = y + 24;
        int badgeX = x;
        for (String tier : TIERS) {
            boolean known = entries.contains(element.name() + ":" + tier);
            int color = known ? colorOf(element.color()) : 0xFF333333;
            ctx.fill(badgeX, badgeY, badgeX + 60, badgeY + 11, color);
            ctx.drawText(this.textRenderer,
                    Text.literal(tier).formatted(known ? Formatting.WHITE : Formatting.DARK_GRAY),
                    badgeX + 3, badgeY + 2, 0xFFFFFF, false);
            badgeX += 64;
        }

        // Offerings row: cheap / standard / premium item icons.
        int offY = y + 40;
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Offerings:").formatted(Formatting.GRAY),
                x, offY + 4, 0xFFFFFF);
        int iconX = x + 64;
        drawOffering(ctx, new ItemStack(element.cheapOffering()), iconX, offY, "cheap");
        drawOffering(ctx, new ItemStack(element.standardOffering()), iconX + 90, offY, "standard");
        drawOffering(ctx, new ItemStack(element.premiumOffering()), iconX + 190, offY, "premium");

        // Separator line.
        ctx.fill(panelLeft + 6, y + ELEMENT_BLOCK_HEIGHT - 6,
                panelLeft + PANEL_WIDTH - 6, y + ELEMENT_BLOCK_HEIGHT - 5, 0xFF3D2A55);
    }

    private void drawOffering(DrawContext ctx, ItemStack stack, int x, int y, String label) {
        ctx.drawItem(stack, x, y);
        Formatting labelColor = switch (label) {
            case "cheap" -> Formatting.DARK_GRAY;
            case "premium" -> Formatting.GOLD;
            default -> Formatting.GRAY;
        };
        ctx.drawText(this.textRenderer,
                Text.literal(label).formatted(labelColor),
                x + 18, y + 4, 0xFFFFFF, false);
    }

    /** Map a Formatting color to an ARGB int with some transparency for badges. */
    private int colorOf(Formatting fmt) {
        Integer c = fmt.getColorValue();
        int rgb = (c == null) ? 0x888888 : c;
        return 0xCC000000 | rgb;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void open(Set<String> entries, int maxEntries) {
        MinecraftClient.getInstance().setScreen(new BestiaryScreen(entries, maxEntries));
    }
}