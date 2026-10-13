package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.supporter.SupporterCredits;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** A framed nameplate, shared by the handbook and ending, without an external texture. */
final class SupporterPlaque {
    static final int HEIGHT = 38;
    static final int GAP = 4;

    private SupporterPlaque() {}

    static void draw(GuiGraphics g, Font font, SupporterCredits.Entry entry, int x, int y, int w, int h, float time) {
        boolean rainbow = entry.tier() == SupporterCredits.Tier.RAINBOW;
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x55000000);
        g.fill(x, y, x + w, y + h, 0xFF242333);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF705541);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC8A478);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF6B5141);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF151C2B);
        g.fill(x + 6, y + 6, x + w - 6, y + 7, 0xFF343C4D);
        if (rainbow) {
            border(g, x, y, w, h, time);
        } else {
            for (int dx : new int[]{2, w - 4}) {
                for (int dy : new int[]{2, h - 4}) {
                    g.fill(x + dx, y + dy, x + dx + 2, y + dy + 2, 0xFFE8CE9F);
                }
            }
        }

        Component name = rainbow ? rainbowName(entry.name(), time) : Component.literal(entry.name());
        float scale = 1;
        List<FormattedCharSequence> lines = font.split(name, w - 24);
        while (lines.size() * font.lineHeight * scale > h - 12 && scale > 0.65F) {
            scale = Math.max(0.65F, scale - 0.05F);
            lines = font.split(name, (int) ((w - 24) / scale));
        }
        g.pose().pushPose();
        g.pose().translate(x + w / 2F, y + (h - lines.size() * font.lineHeight * scale) / 2F, 0);
        g.pose().scale(scale, scale, 1);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            int lineX = -font.width(line) / 2;
            if (rainbow) {
                // A soft halo around the animated, individually colored glyphs.
                g.drawString(font, line, lineX - 1, lineY, 0x38FFFFFF, false);
                g.drawString(font, line, lineX + 1, lineY, 0x38FFFFFF, false);
                g.drawString(font, line, lineX, lineY - 1, 0x38FFFFFF, false);
                g.drawString(font, line, lineX, lineY + 1, 0x38FFFFFF, false);
            }
            g.drawString(font, line, lineX, lineY, rainbow ? 0xFFFFFFFF : 0xFFF6E9CE, true);
            lineY += font.lineHeight;
        }
        g.pose().popPose();
        if (rainbow) {
            sparkles(g, entry.id().hashCode(), x, y, w, h, time);
        }
    }

    static Component tooltip(SupporterCredits.Entry entry) {
        return Component.literal(entry.name()).append("\n").append(Component.translatable(
                entry.tier() == SupporterCredits.Tier.RAINBOW ? "gui.singulo.supporters.rainbow" : "gui.singulo.supporters.standard"));
    }

    private static MutableComponent rainbowName(String name, float time) {
        MutableComponent result = Component.empty();
        int index = 0;
        for (int cp : name.codePoints().toArray()) {
            int color = rainbow(time, index++ * 0.055F);
            result.append(Component.literal(new String(Character.toChars(cp))).withStyle(s -> s.withColor(color)));
        }
        return result;
    }

    private static int rainbow(float time, float offset) {
        float hue = time / 160F + offset;
        return Mth.hsvToRgb(hue - Mth.floor(hue), 0.6F, 1);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, float time) {
        int perimeter = 2 * (w + h);
        for (int dx = 0; dx < w; dx += 4) {
            int n = Math.min(4, w - dx);
            g.fill(x + dx, y + 1, x + dx + n, y + 4, 0xFF000000 | rainbow(time, dx / (float) perimeter));
            g.fill(x + dx, y + h - 4, x + dx + n, y + h - 1,
                    0xFF000000 | rainbow(time, (2 * w + h - dx) / (float) perimeter));
        }
        for (int dy = 4; dy < h - 4; dy += 4) {
            int n = Math.min(4, h - 4 - dy);
            g.fill(x + 1, y + dy, x + 4, y + dy + n,
                    0xFF000000 | rainbow(time, (perimeter - dy) / (float) perimeter));
            g.fill(x + w - 4, y + dy, x + w - 1, y + dy + n,
                    0xFF000000 | rainbow(time, (w + dy) / (float) perimeter));
        }
    }

    private static void sparkles(GuiGraphics g, int seed, int x, int y, int w, int h, float time) {
        for (int i = 0; i < 4; i++) {
            float phase = time * 0.06F + i * 1.7F + (seed & 255) * 0.02F;
            float bright = Math.max(0, Mth.sin(phase));
            if (bright < 0.7F) {
                continue;
            }
            int sx = x + 9 + Math.floorMod(seed + i * 43, Math.max(1, w - 18));
            int sy = y + (i % 2 == 0 ? 7 : h - 9);
            int color = ((int) (bright * 220) << 24) | 0xFFFFFF;
            g.fill(sx - 2, sy, sx + 3, sy + 1, color);
            g.fill(sx, sy - 2, sx + 1, sy + 3, color);
        }
    }
}
