package com.cutedifficult.client.gui;

import com.cutedifficult.spirit.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Set;

/**
 * The Bestiary of Inari custom GUI.
 *
 * <p><b>v0.6.1 fix:</b> the previous render() called {@code super.render()}
 * AT THE END, which re-applied the screen background blur on top of our
 * drawn content — making everything look smeared. Fix: call
 * {@link #renderBackground} at the very top, then draw book + text, then
 * delegate to super to draw widgets (buttons). The super call still
 * triggers a background render internally but at that point the book
 * texture acts as a foreground layer that's preserved.
 *
 * <p>Actually, simpler approach: override {@link #renderBackground} to
 * be a no-op (or to just draw the blur once at the start), and call
 * background + book + text + super.render in the correct order.
 *
 * <p>Final structure of render():
 * <ol>
 *   <li>{@code renderBackground} — vanilla translucent darkening.</li>
 *   <li>Draw book.png texture.</li>
 *   <li>Draw page-specific text content.</li>
 *   <li>{@code super.render} for buttons — but it'll re-render the
 *       background. To prevent that we override renderBackground to be
 *       a no-op after the first call, OR we just live with the buttons
 *       being drawn directly.</li>
 * </ol>
 *
 * <p>Cleanest fix: don't call super.render at all; manually draw the
 * drawables. We iterate this.children() for the visual rendering.
 */
public class BestiaryScreen extends Screen {

    private static final Identifier BOOK_TEXTURE = Identifier.ofVanilla("textures/gui/book.png");
    private static final int BOOK_WIDTH = 192;
    private static final int BOOK_HEIGHT = 192;
    private static final int TOTAL_PAGES = 10;

    private final Set<String> entries;
    private final int totalDiscovered;
    private final int maxEntries;
    private int pageIndex = 0;

    private ButtonWidget nextButton;
    private ButtonWidget prevButton;

    public BestiaryScreen(Set<String> entries, int maxEntries) {
        super(Text.literal("Bestiary of Inari"));
        this.entries = entries;
        this.totalDiscovered = entries.size();
        this.maxEntries = maxEntries;
    }

    @Override
    protected void init() {
        super.init();

        int bookLeft = (this.width - BOOK_WIDTH) / 2;
        int bookTop = (this.height - BOOK_HEIGHT) / 2;

        this.prevButton = ButtonWidget.builder(
                Text.literal("◀ Prev"),
                btn -> changePage(-1)
        ).dimensions(bookLeft + 20, bookTop + BOOK_HEIGHT - 24, 50, 18).build();

        this.nextButton = ButtonWidget.builder(
                Text.literal("Next ▶"),
                btn -> changePage(1)
        ).dimensions(bookLeft + BOOK_WIDTH - 70, bookTop + BOOK_HEIGHT - 24, 50, 18).build();

        ButtonWidget doneButton = ButtonWidget.builder(
                Text.literal("Done"),
                btn -> this.close()
        ).dimensions(bookLeft + (BOOK_WIDTH - 50) / 2, bookTop + BOOK_HEIGHT + 4, 50, 18).build();

        this.addDrawableChild(prevButton);
        this.addDrawableChild(nextButton);
        this.addDrawableChild(doneButton);

        updateButtonStates();
    }

    private void changePage(int delta) {
        pageIndex = Math.max(0, Math.min(TOTAL_PAGES - 1, pageIndex + delta));
        updateButtonStates();
    }

    private void updateButtonStates() {
        prevButton.active = pageIndex > 0;
        nextButton.active = pageIndex < TOTAL_PAGES - 1;
    }

    /**
     * v0.6.1: override the parent's renderBackground to a no-op so calling
     * super.render(...) at the END of our render() doesn't re-blur over
     * our book content. We handle background ourselves explicitly at the
     * top of render().
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No-op. Real background drawn from render() directly.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 1) Draw the screen darkening + blur ONCE, ourselves.
        super.renderBackground(ctx, mouseX, mouseY, delta);

        int bookLeft = (this.width - BOOK_WIDTH) / 2;
        int bookTop = (this.height - BOOK_HEIGHT) / 2;

        // 2) Draw the book texture as a foreground.
        ctx.drawTexture(BOOK_TEXTURE, bookLeft, bookTop, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, 256, 256);

        // 3) Page content overlay.
        if (pageIndex == 0) {
            renderCoverPage(ctx, bookLeft, bookTop);
        } else {
            Element element = Element.values()[pageIndex - 1];
            renderElementPage(ctx, bookLeft, bookTop, element);
        }

        // 4) Buttons — super.render handles them. Our overridden
        // renderBackground above is a no-op, so super won't re-blur.
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderCoverPage(DrawContext ctx, int bookLeft, int bookTop) {
        int textLeft = bookLeft + 36;
        int contentTop = bookTop + 30;

        ctx.drawText(this.textRenderer,
                Text.literal("Bestiary of Inari").formatted(Formatting.GOLD, Formatting.BOLD),
                textLeft, contentTop, 0x000000, false);

        ctx.drawText(this.textRenderer,
                Text.literal("Discovered: " + totalDiscovered + " / " + maxEntries)
                        .formatted(Formatting.DARK_GRAY),
                textLeft, contentTop + 16, 0x000000, false);

        String[] blurb = {
                "The kitsune walk among",
                "us. Those whom you have",
                "inquired upon are recorded",
                "here.",
                "",
                "Turn the pages to behold",
                "the nine kami of Inari."
        };
        for (int i = 0; i < blurb.length; i++) {
            ctx.drawText(this.textRenderer,
                    Text.literal(blurb[i]).formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                    textLeft, contentTop + 40 + i * 11, 0x000000, false);
        }

        ctx.drawText(this.textRenderer,
                Text.literal("Page " + (pageIndex + 1) + " / " + TOTAL_PAGES)
                        .formatted(Formatting.DARK_GRAY),
                textLeft, bookTop + BOOK_HEIGHT - 42, 0x000000, false);
    }

    private void renderElementPage(DrawContext ctx, int bookLeft, int bookTop, Element element) {
        int textLeft = bookLeft + 36;
        int contentTop = bookTop + 22;

        ctx.drawText(this.textRenderer,
                Text.literal(element.kamiName()).formatted(element.color(), Formatting.BOLD),
                textLeft, contentTop, 0x000000, false);

        List<net.minecraft.text.OrderedText> wrapped = this.textRenderer.wrapLines(
                Text.literal(element.flavor()).formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                BOOK_WIDTH - 70
        );
        int lineY = contentTop + 14;
        for (var line : wrapped) {
            ctx.drawText(this.textRenderer, line, textLeft, lineY, 0x000000, false);
            lineY += 10;
        }

        lineY += 6;
        String[] tiers = {"young", "matured", "venerable", "ancient", "Kyuubi"};
        boolean anyDiscovered = false;
        for (String tier : tiers) {
            String key = element.name() + ":" + tier;
            boolean known = entries.contains(key);
            if (known) anyDiscovered = true;

            Text line = known
                    ? Text.literal("✓ ").formatted(Formatting.DARK_GREEN)
                    .copy().append(Text.literal(tier).formatted(Formatting.BLACK))
                    : Text.literal("✗ ").formatted(Formatting.DARK_GRAY)
                    .copy().append(Text.literal("(unknown)").formatted(Formatting.DARK_GRAY));
            ctx.drawText(this.textRenderer, line, textLeft, lineY, 0x000000, false);
            lineY += 11;
        }

        if (anyDiscovered) {
            lineY += 6;
            ctx.drawText(this.textRenderer,
                    Text.literal("Favored offering:").formatted(Formatting.DARK_GRAY),
                    textLeft, lineY, 0x000000, false);
            ctx.drawText(this.textRenderer,
                    Text.literal(element.correctOffering().getName().getString())
                            .formatted(element.color()),
                    textLeft, lineY + 10, 0x000000, false);
        }

        ctx.drawText(this.textRenderer,
                Text.literal("Page " + (pageIndex + 1) + " / " + TOTAL_PAGES)
                        .formatted(Formatting.DARK_GRAY),
                textLeft, bookTop + BOOK_HEIGHT - 42, 0x000000, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void open(Set<String> entries, int maxEntries) {
        MinecraftClient.getInstance().setScreen(new BestiaryScreen(entries, maxEntries));
    }
}