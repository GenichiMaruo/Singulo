package io.github.genichimaruo.singulo.client;

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

    private final List<Component> lines = new ArrayList<>();
    private final long seed;
    private final float[][] field;
    private int ticks;

    public static void open(boolean archivist) {
        Minecraft.getInstance().setScreen(new EndingScreen(archivist));
    }

    EndingScreen(boolean archivist) {
        super(Component.translatable("ending.singulo.thanks"));
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

    private int thanksAt() {
        return FIRST_LINE + lines.size() * LINE_GAP + 40;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks > thanksAt() + 900) {
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
        if (ticks > 100) {
            int alpha = (int) (120 * Mth.clamp((t - 100) / 40F, 0, 1));
            Component hint = Component.translatable("ending.singulo.skip");
            g.drawString(font, hint, width - font.width(hint) - 8, height - 14, (Math.max(4, alpha) << 24) | 0xA0A0A8);
        }
        // 部品はないので Screen#render は呼ばない（呼ぶと背景がもう一度、上から塗られる）
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
