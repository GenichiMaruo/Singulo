package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.supporter.SupporterCredits;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * 創世のエピローグ。暗闇に星が灯り、生まれた銀河が渦を巻くなかで、言葉が一行ずつ浮かぶ。
 * 最後の記録者を眠らせた人には、記録者の一節が加わる。Esc で閉じる（ゲームは止めない）。
 */
public class EndingScreen extends Screen {
    private static final int FADE_IN = 50;
    private static final int FIRST_LINE = 70;
    private static final int LINE_GAP = 95;
    private static final int LINE_FADE = 35;
    private static final int SUPPORTER_PAGE_TICKS = 180;

    private final List<Component> lines = new ArrayList<>();
    private final List<SupporterCredits.Entry> supporters;
    private final long seed;
    private final float[][] field;
    private int ticks;
    private int supporterStart;
    private int supporterTicks;
    private int supporterColumns = 2;
    private int supporterRows = 3;

    public static void open(boolean archivist) {
        Minecraft.getInstance().setScreen(new EndingScreen(archivist));
    }

    EndingScreen(boolean archivist) {
        super(Component.translatable("ending.singulo.thanks"));
        supporters = SupporterRoster.load().entries();
        for (int i = 1; i <= 5; i++) {
            lines.add(Component.translatable("ending.singulo.line." + i));
        }
        if (archivist) {
            lines.add(Component.translatable("ending.singulo.archivist").withStyle(s -> s.withColor(0xE8D8FF).withItalic(true)));
        }
        long latest = ClientGalaxies.latest();
        this.seed = latest != 0 ? latest : 0x5EEDL;
        Random rnd = new Random(seed);
        field = new float[160][];
        for (int i = 0; i < field.length; i++) {
            field[i] = new float[]{rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 6.28F, 0.03F + rnd.nextFloat() * 0.08F,
                    rnd.nextFloat() < 0.12F ? 2 : 1};
        }
    }

    @Override
    protected void init() {
        super.init();
        supporterColumns = Math.max(1, Math.min(3, (width - 32) / 180));
        supporterRows = Math.max(1, Math.min(4, (height - 110) / (SupporterPlaque.HEIGHT + 8)));
        // Keep the first visible supporter on resize, and give the new layout time to be read.
        supporterTicks = 0;
    }

    private int thanksAt() {
        return FIRST_LINE + lines.size() * LINE_GAP + 40;
    }

    private int supportersAt() {
        return thanksAt() + 100;
    }

    private int supporterCapacity() {
        return supporterColumns * supporterRows;
    }

    private boolean showingSupporters() {
        return !supporters.isEmpty() && ticks >= supportersAt();
    }

    @Override
    public void tick() {
        ticks++;
        if (showingSupporters()) {
            supporterTicks++;
            if (supporterTicks >= SUPPORTER_PAGE_TICKS && supporterStart + supporterCapacity() < supporters.size()) {
                supporterStart += supporterCapacity();
                supporterTicks = 0;
            } else if (supporterTicks > SUPPORTER_PAGE_TICKS + 60) {
                onClose();
            }
        } else if (supporters.isEmpty() && ticks > thanksAt() + 900) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = ticks + partialTick;
        int a = (int) (255 * Mth.clamp(t / FADE_IN, 0, 1));
        g.fill(0, 0, width, height, a << 24);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float t = ticks + partialTick;
        float dawn = Mth.clamp((t - FADE_IN * 0.6F) / 60F, 0, 1);
        // 遠くの星
        for (float[] s : field) {
            float tw = 0.55F + 0.45F * Mth.sin(t * s[3] + s[2]);
            int alpha = (int) (200 * tw * dawn);
            int x = (int) (s[0] * width);
            int y = (int) (s[1] * height);
            g.fill(x, y, x + (int) s[4], y + (int) s[4], (alpha << 24) | 0xE8F0FF);
        }
        // 生まれた銀河（斜めから見た渦）
        galaxy(g, t, dawn);
        if (showingSupporters()) {
            renderSupporters(g, mouseX, mouseY, t);
        } else {
            renderStory(g, t);
        }
        if (ticks > 100) {
            int alpha = (int) (120 * Mth.clamp((t - 100) / 40F, 0, 1));
            Component hint = Component.translatable(showingSupporters() ? "ending.singulo.supporter_controls" : "ending.singulo.skip");
            g.drawString(font, hint, width - font.width(hint) - 8, height - 14, (Math.max(4, alpha) << 24) | 0xA0A0A8);
        }
        // 部品はないので Screen#render は呼ばない（呼ぶと背景がもう一度、上から塗られる）
    }

    private void renderStory(GuiGraphics g, float t) {
        // 言葉
        int maxWidth = Math.min(width - 40, 360);
        int y = height / 2 + 34;
        for (int i = 0; i < lines.size(); i++) {
            float k = Mth.clamp((t - FIRST_LINE - i * LINE_GAP) / LINE_FADE, 0, 1);
            if (k <= 0) {
                break;
            }
            // 新しい行が出たら、前の行はすこし薄くなる
            float dim = i < visibleLines(t) - 1 ? 0.55F : 1F;
            int alpha = Math.max(4, (int) (255 * k * dim));
            List<FormattedCharSequence> wrapped = font.split(lines.get(i), maxWidth);
            if (visibleLines(t) - i <= 2) {
                for (FormattedCharSequence seq : wrapped) {
                    g.drawCenteredString(font, seq, width / 2, y, (alpha << 24) | 0xF2EEE4);
                    y += 11;
                }
                y += 6;
            }
        }
        float thanks = Mth.clamp((t - thanksAt()) / 60F, 0, 1);
        if (thanks > 0) {
            int alpha = Math.max(4, (int) (255 * thanks));
            g.pose().pushPose();
            g.pose().translate(width / 2F, height / 2F - 70, 0);
            g.pose().scale(2, 2, 1);
            g.drawCenteredString(font, Component.translatable("ending.singulo.thanks"), 0, 0, (alpha << 24) | 0xFFE6B8);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("ending.singulo.credit"), width / 2, height / 2 - 44,
                    (Math.max(4, (int) (180 * thanks)) << 24) | 0xC8C8D8);
        }
    }

    private void renderSupporters(GuiGraphics g, int mx, int my, float time) {
        g.pose().pushPose();
        g.pose().translate(width / 2F, 16, 0);
        g.pose().scale(1.5F, 1.5F, 1);
        g.drawCenteredString(font, Component.translatable("ending.singulo.thanks"), 0, 0, 0xFFFFE6B8);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("ending.singulo.credit"), width / 2, 36, 0xFFCACAE0);
        g.drawCenteredString(font, Component.translatable("ending.singulo.supporters"), width / 2, 55, 0xFFE3D8C3);

        int to = Math.min(supporters.size(), supporterStart + supporterCapacity());
        int rows = (to - supporterStart + supporterColumns - 1) / supporterColumns;
        int gridWidth = Math.min(620, width - 40);
        int cardWidth = (gridWidth - (supporterColumns - 1) * 10) / supporterColumns;
        int gridHeight = rows * SupporterPlaque.HEIGHT + (rows - 1) * 8;
        int top = 72 + Math.max(0, (height - 110 - gridHeight) / 2);
        SupporterCredits.Entry hover = null;
        for (int i = supporterStart; i < to; i++) {
            int row = (i - supporterStart) / supporterColumns;
            int column = (i - supporterStart) % supporterColumns;
            int inRow = Math.min(supporterColumns, to - supporterStart - row * supporterColumns);
            int rowWidth = inRow * cardWidth + (inRow - 1) * 10;
            int x = (width - rowWidth) / 2 + column * (cardWidth + 10);
            int y = top + row * (SupporterPlaque.HEIGHT + 8);
            SupporterCredits.Entry entry = supporters.get(i);
            SupporterPlaque.draw(g, font, entry, x, y, cardWidth, SupporterPlaque.HEIGHT, time);
            if (Panel.inside(mx, my, x, y, cardWidth, SupporterPlaque.HEIGHT)) {
                hover = entry;
            }
        }
        g.drawCenteredString(font, Component.translatable("ending.singulo.supporter_range", supporterStart + 1, to, supporters.size()),
                width / 2, height - 34, 0xFFC7C8D4);
        if (hover != null) {
            g.renderTooltip(font, font.split(SupporterPlaque.tooltip(hover), 220), mx, my);
        }
    }

    private boolean turnSupporters(int direction) {
        if (!showingSupporters()) {
            return false;
        }
        int next = supporterStart + direction * supporterCapacity();
        if ((direction > 0 && next >= supporters.size()) || (direction < 0 && supporterStart == 0)) {
            return false;
        }
        supporterStart = Math.max(0, next);
        supporterTicks = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 262 && turnSupporters(1)) || (key == 263 && turnSupporters(-1))) {
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return (dy != 0 && turnSupporters(dy < 0 ? 1 : -1)) || super.mouseScrolled(mx, my, dx, dy);
    }

    /** いま見えている行の数。 */
    private int visibleLines(float t) {
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (t >= FIRST_LINE + i * LINE_GAP) {
                n = i + 1;
            }
        }
        return n;
    }

    private void galaxy(GuiGraphics g, float t, float dawn) {
        if (dawn <= 0) {
            return;
        }
        float cx = width / 2F;
        float cy = height / 2F - 40;
        float radius = Math.min(width, height) * 0.36F * (0.6F + 0.4F * dawn);
        float fadeForText = 1 - 0.55F * Mth.clamp((t - thanksAt()) / 60F, 0, 1);
        float spin = t * 0.004F;
        float cos = Mth.cos(0.35F);
        GalaxyDraw.Shape shape = GalaxyDraw.shape(seed);
        // 中心のふくらみ（同心の四角を重ねて、ぼんやりと）
        for (int k = 6; k >= 1; k--) {
            float r = radius * 0.05F * k;
            int alpha = (int) (26 * dawn * fadeForText);
            g.fill((int) (cx - r), (int) (cy - r * cos * 0.6F), (int) (cx + r), (int) (cy + r * cos * 0.6F),
                    (alpha << 24) | shape.coreRgb());
        }
        for (GalaxyDraw.Star s : shape.stars()) {
            float a = s.angle() + spin * (1.6F - s.r());
            float x = cx + Mth.cos(a) * s.r() * radius;
            float y = cy + (Mth.sin(a) * s.r() * cos * 0.6F + s.y()) * radius;
            float tw = 0.7F + 0.3F * Mth.sin(t * s.twinkle() + s.phase());
            int alpha = (int) (235 * tw * dawn * fadeForText);
            int size = s.size() > 0.024F ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + size, (int) y + size, (Math.max(0, alpha) << 24) | s.rgb());
        }
    }
}
