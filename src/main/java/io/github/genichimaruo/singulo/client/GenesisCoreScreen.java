package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import io.github.genichimaruo.singulo.genesis.GenesisCoreMenu;
import io.github.genichimaruo.singulo.genesis.MassSynthesis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 創世炉の画面。左に「生まれかけの宇宙」をのぞく窓、右に今の段階と次にすること、進みのバー、投入のスイッチ、出来上がりのスロット。
 * <ul>
 *   <li>窓: 揺籃は種を待つ小さな光、種火は膨らむ火の玉、安定化は渦を巻く原始の宇宙（暴走の兆しで赤く震える）、
 *       誕生は閃光、完成は自分の銀河がゆっくり回る</li>
 *   <li>暴走の兆しが来ると、窓に赤い帯で「投入を止めろ」と出る（止めていれば水色の「抑えている」）</li>
 * </ul>
 * 様子は核のブロックエンティティ（クライアントに同期されている写し）から読む。
 */
public class GenesisCoreScreen extends AbstractContainerScreen<GenesisCoreMenu> {
    private static final int BG = 0xFF090C17;
    private static final int BG2 = 0xFF0E1324;
    private static final int FRAME = 0xFF2B2350;
    private static final int FRAME_HI = 0xFF5A4BA0;
    private static final int TEXT = 0xFFD8E4FF;
    private static final int DIM = 0xFF7F8AB0;
    private static final int GOLD = 0xFFF2CC6A;
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int EMBER = 0xFFFF9A4A;
    private static final int RED = 0xFFFF5A5A;
    private static final int SLOT = 0xFF05070E;
    /** 窓（宇宙をのぞく）の位置と大きさ。 */
    private static final int VIEW_X = 8, VIEW_Y = 16, VIEW = 84;
    /** 右の欄の左端と幅。 */
    private static final int PX = 100, PW = 128;
    /** 投入のスイッチ。 */
    private static final int FEED_X = PX, FEED_Y = 96;
    /** 出来上がりスロットの左端（作る物の名前はここまで）。 */
    private static final int OUTPUT_LEFT = GenesisCoreMenu.OUTPUT_X - 2;

    public GenesisCoreScreen(GenesisCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GenesisCoreMenu.WIDTH;
        imageHeight = GenesisCoreMenu.HEIGHT;
        inventoryLabelX = GenesisCoreMenu.INVENTORY_X;
        inventoryLabelY = GenesisCoreMenu.INVENTORY_Y - 11;
    }

    private GenesisCoreBlockEntity core() {
        return minecraft == null || minecraft.player == null ? null : menu.core(minecraft.player);
    }

    private float time(float partialTick) {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + partialTick : 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        GenesisCoreBlockEntity core = core();
        if (core != null && core.canToggleFeed() && Panel.inside(mouseX, mouseY, leftPos + FEED_X - 1, topPos + FEED_Y - 1, 12, 12)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.genesis.screen.feed_tip"), mouseX, mouseY);
        }
        Slot out = menu.getSlot(0);
        if (out.isActive() && !out.hasItem() && Panel.inside(mouseX, mouseY, leftPos + out.x, topPos + out.y, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.genesis.screen.output"), mouseX, mouseY);
        }
        Slot pick = menu.getSlot(GenesisCoreMenu.TEMPLATE_SLOT);
        if (pick.isActive() && Panel.inside(mouseX, mouseY, leftPos + pick.x, topPos + pick.y, 16, 16)) {
            g.renderTooltip(font, java.util.List.of(Component.translatable("gui.singulo.genesis.screen.template_tip_1").getVisualOrderText(),
                    Component.translatable("gui.singulo.genesis.screen.template_tip_2").getVisualOrderText()), mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 背景

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        float t = time(partialTick);
        background(g, x, y, t);
        GenesisCoreBlockEntity core = core();
        int stage = core == null ? GenesisCoreBlockEntity.UNFORMED : core.stage();
        // 窓
        int vx = x + VIEW_X;
        int vy = y + VIEW_Y;
        g.fill(vx - 1, vy - 1, vx + VIEW + 1, vy + VIEW + 1, FRAME_HI);
        g.fill(vx, vy, vx + VIEW, vy + VIEW, 0xFF03040A);
        g.enableScissor(vx, vy, vx + VIEW, vy + VIEW);
        if (core != null) {
            drawCosmos(g, core, vx + VIEW / 2, vy + VIEW / 2, t);
        }
        g.disableScissor();
        // 右の欄のバー
        if (core != null) {
            switch (stage) {
                case GenesisCoreBlockEntity.KINDLING -> bar(g, x + PX, y + 62, PW, core.chargeFraction(), EMBER, 0xFFFFE0A0);
                case GenesisCoreBlockEntity.STABILIZING -> {
                    bar(g, x + PX, y + 60, PW, core.progressFraction(), CYAN, 0xFFC8F4FF);
                    float k = core.instability() / 100F;
                    bar(g, x + PX, y + 84, PW, k, Mth.hsvToRgb(0.33F * (1 - k), 0.8F, 1) | 0xFF000000, 0xFFFFFFFF);
                }
                case GenesisCoreBlockEntity.BIRTH -> bar(g, x + PX, y + 62, PW,
                        Math.min(1, core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_TICKS), GOLD, 0xFFFFF4D0);
                case GenesisCoreBlockEntity.COMPLETE -> bar(g, x + PX, y + 84, 100, core.synthFraction(), MAGENTA, 0xFFFFD0FF);
                default -> { }
            }
            if (core.canToggleFeed()) {
                boolean hover = Panel.inside(mouseX, mouseY, x + FEED_X - 1, y + FEED_Y - 1, 12, 12);
                Panel.powerButton(g, x + FEED_X, y + FEED_Y, !core.manualStop(), hover);
            }
        }
        // スロット
        for (Slot s : menu.slots) {
            if (!s.isActive()) {
                continue;
            }
            int edge = s.index == 0 ? GOLD : s.index == GenesisCoreMenu.TEMPLATE_SLOT ? MAGENTA : 0xFF2E3658;
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, edge);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
    }

    private void background(GuiGraphics g, int x, int y, float t) {
        int w = imageWidth;
        int h = imageHeight;
        g.fill(x, y, x + w, y + h, FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_HI);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BG);
        g.fillGradient(x + 2, y + 2, x + w - 2, y + 112, BG2, BG);
        for (int i = 0; i < w - 12; i++) {
            float k = 0.5F + 0.5F * Mth.sin(i * 0.05F - t * 0.06F);
            g.fill(x + 6 + i, y + 3, x + 7 + i, y + 4, lerp(GOLD, MAGENTA, k));
        }
        long seed = 0x5EED0F6E4E5157L;
        for (int i = 0; i < 70; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int) Math.floorMod(seed >>> 20, (long) (w - 8)) + 4;
            int sy = (int) Math.floorMod(seed >>> 40, (long) (h - 10)) + 6;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.07F + i * 1.7F);
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, ((int) (30 + 110 * tw) << 24) | 0xCFE0FF);
        }
        g.fill(x + 8, y + 112, x + w - 8, y + 113, FRAME_HI);
    }

    /** 横長のバー（6px）。上の縁を明るくする。 */
    private static void bar(GuiGraphics g, int x, int y, int w, float fraction, int color, int highlight) {
        g.fill(x - 1, y - 1, x + w + 1, y + 7, FRAME_HI);
        g.fill(x, y, x + w, y + 6, SLOT);
        int filled = Math.round(w * Mth.clamp(fraction, 0, 1));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 6, color);
            g.fill(x, y, x + filled, y + 1, highlight);
        }
    }

    // ------------------------------------------------------------------ 窓の中の宇宙

    private static void drawCosmos(GuiGraphics g, GenesisCoreBlockEntity core, int cx, int cy, float t) {
        switch (core.stage()) {
            case GenesisCoreBlockEntity.UNFORMED -> dust(g, cx, cy, t, 0x40, 30);
            case GenesisCoreBlockEntity.CRADLE -> {
                dust(g, cx, cy, t, 0x60, 30);
                float pulse = 0.5F + 0.5F * Mth.sin(t * 0.1F);
                glow(g, cx, cy, 2 + 2 * pulse, 0xFFF4D890, 0.6F + 0.4F * pulse);
            }
            case GenesisCoreBlockEntity.KINDLING -> {
                float f = core.chargeFraction();
                dust(g, cx, cy, t, 0x70, 30);
                // 吸い込まれていく光の粒
                for (int i = 0; i < 24; i++) {
                    float a = i * 2.4F + t * 0.02F;
                    float r = 38 - ((t * 0.6F + i * 13) % 34);
                    plot(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, 0xFFFFC080, 0.3F + 0.7F * (1 - r / 38));
                }
                float flicker = 0.9F + 0.1F * Mth.sin(t * 0.7F);
                glow(g, cx, cy, (3 + 13 * f) * flicker, EMBER, 1);
                glow(g, cx, cy, (1.5F + 6 * f) * flicker, 0xFFFFF0C0, 1);
            }
            case GenesisCoreBlockEntity.STABILIZING -> {
                boolean surge = core.surgeTicks() > 0 && core.feeding();
                float jitter = surge ? 1.5F : 0;
                float shake = jitter * Mth.sin(t * 2.3F);
                swirl(g, cx + shake, cy - shake, 12 + 26 * core.progressFraction(), t, core.galaxySeed(),
                        surge ? RED : CYAN, surge ? 0xFFFFA060 : MAGENTA, 3, 0.04F + 0.03F * core.instability() / 100F);
                glow(g, cx + shake, cy - shake, 4, surge ? 0xFFFFD0C0 : 0xFFE8F8FF, 1);
            }
            case GenesisCoreBlockEntity.BIRTH -> {
                float k = core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_TICKS;
                if (core.birthTicks() < GenesisCoreBlockEntity.BIRTH_COLLAPSE) {
                    glow(g, cx, cy, 14 * (1 - core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_COLLAPSE) + 1, 0xFFFFFFFF, 1);
                } else if (core.birthTicks() < GenesisCoreBlockEntity.BIRTH_BANG) {
                    plot(g, cx, cy, 0xFFFFFFFF, 1);
                } else {
                    float flash = Math.max(0, 1 - (core.birthTicks() - GenesisCoreBlockEntity.BIRTH_BANG) / 40F);
                    if (flash > 0) {
                        g.fill(cx - VIEW / 2, cy - VIEW / 2, cx + VIEW / 2, cy + VIEW / 2, ((int) (flash * 230) << 24) | 0xFFFFFF);
                    }
                    galaxy(g, cx, cy, 6 + 30 * Math.min(1, k * 1.4F), t, core.galaxySeed());
                }
            }
            case GenesisCoreBlockEntity.COMPLETE -> {
                dust(g, cx, cy, t, 0x50, 30);
                galaxy(g, cx, cy, 36, t, core.galaxySeed());
            }
            default -> { }
        }
    }

    /** うっすら瞬く塵（窓の背景）。 */
    private static void dust(GuiGraphics g, int cx, int cy, float t, int alpha, int n) {
        long seed = 0xD057L;
        for (int i = 0; i < n; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int) Math.floorMod(seed >>> 20, (long) VIEW) - VIEW / 2;
            int sy = (int) Math.floorMod(seed >>> 40, (long) VIEW) - VIEW / 2;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.09F + i);
            g.fill(cx + sx, cy + sy, cx + sx + 1, cy + sy + 1, ((int) (alpha * tw) << 24) | 0xB8C8FF);
        }
    }

    /** 渦を巻く原始の宇宙（腕 arms 本、内側 inner 色から外側 outer 色へ）。 */
    private static void swirl(GuiGraphics g, float cx, float cy, float radius, float t, long seed, int inner, int outer, int arms,
                              float speed) {
        java.util.Random rng = new java.util.Random(seed ^ 0x51A7L);
        for (int i = 0; i < 260; i++) {
            int arm = i % arms;
            float u = rng.nextFloat();
            float r = radius * (0.12F + 0.88F * u);
            float a = arm * Mth.TWO_PI / arms + u * 4.2F + t * speed * (1.6F - u) + (rng.nextFloat() - 0.5F) * 0.7F;
            float px = cx + Mth.cos(a) * r;
            float py = cy + Mth.sin(a) * r * 0.78F;
            plot(g, px, py, lerp(inner, outer, u), 0.35F + 0.65F * (1 - u));
        }
    }

    /** 生まれた銀河（種で形と色が決まり、ゆっくり回る）。 */
    private static void galaxy(GuiGraphics g, int cx, int cy, float radius, float t, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        float hue = rng.nextFloat();
        int arms = 2 + rng.nextInt(3);
        float twist = 3 + rng.nextFloat() * 3;
        int core = Mth.hsvToRgb(hue, 0.25F, 1) | 0xFF000000;
        int edge = Mth.hsvToRgb((hue + 0.55F) % 1, 0.7F, 1) | 0xFF000000;
        for (int i = 0; i < 360; i++) {
            int arm = i % arms;
            float u = (float) Math.sqrt(rng.nextFloat());
            float r = radius * u;
            float a = arm * Mth.TWO_PI / arms + u * twist + t * 0.008F + (rng.nextFloat() - 0.5F) * (0.9F - 0.5F * u);
            plot(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r * 0.62F, lerp(core, edge, u), 0.3F + 0.7F * (1 - u * 0.8F));
        }
        glow(g, cx, cy, 3.5F, core, 1);
    }

    /** 丸い光（中心ほど明るい）。 */
    private static void glow(GuiGraphics g, float cx, float cy, float radius, int rgb, float bright) {
        int r = (int) Math.ceil(radius);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                float d = Mth.sqrt(dx * dx + dy * dy) / Math.max(0.5F, radius);
                if (d <= 1) {
                    plot(g, cx + dx, cy + dy, rgb, bright * (1 - d * d));
                }
            }
        }
    }

    private static void plot(GuiGraphics g, float x, float y, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 0) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, (a << 24) | (rgb & 0xFFFFFF));
    }

    private static int lerp(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gg = (int) Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bb = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------------ 文字

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, VIEW_X, 6, GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
        GenesisCoreBlockEntity core = core();
        if (core == null) {
            return;
        }
        int stage = core.stage();
        float t = time(0);
        // 段階と、次にすること
        g.drawString(font, Component.translatable("gui.singulo.genesis.phase." + stage), PX, 18, phaseColor(stage), false);
        int y = 30;
        for (var seq : font.split(Component.translatable("gui.singulo.genesis.hint." + stage), PW)) {
            if (y > 42) {
                break;
            }
            g.drawString(font, seq, PX, y, DIM, false);
            y += 10;
        }
        String intake = GenesisCoreBlockEntity.formatFe(core.intake());
        switch (stage) {
            case GenesisCoreBlockEntity.KINDLING -> {
                pair(g, "gui.singulo.genesis.screen.charge", percent(core.chargeFraction()), 52, EMBER);
                g.drawString(font, GenesisCoreBlockEntity.formatFe(core.chargeFraction() * GenesisCoreBlockEntity.seedEnergy())
                        + " / " + GenesisCoreBlockEntity.formatFe(GenesisCoreBlockEntity.seedEnergy()), PX, 72, DIM, false);
                g.drawString(font, Component.translatable("gui.singulo.genesis.screen.intake_cap",
                        GenesisCoreBlockEntity.formatFe(GenesisCoreBlockEntity.maxInput())), PX, 82, DIM, false);
            }
            case GenesisCoreBlockEntity.STABILIZING -> {
                pair(g, "gui.singulo.genesis.screen.progress", percent(core.progressFraction()), 50, CYAN);
                pair(g, "gui.singulo.genesis.screen.instability", percent(core.instability() / 100F), 74,
                        core.instability() > 60 ? RED : TEXT);
            }
            case GenesisCoreBlockEntity.BIRTH -> pair(g, "gui.singulo.genesis.screen.birth",
                    percent(Math.min(1, core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_TICKS)), 52, GOLD);
            case GenesisCoreBlockEntity.COMPLETE -> {
                ItemStack template = core.template();
                MassSynthesis.Entry e = MassSynthesis.INSTANCE.entry(template);
                if (template.isEmpty() || e == null) {
                    g.drawString(font, Component.translatable("gui.singulo.genesis.screen.no_template"), PX + 20, 54, DIM, false);
                } else {
                    g.drawString(font, fit(template.getHoverName().getString(), OUTPUT_LEFT - PX - 24), PX + 20, 50, TEXT, false);
                    g.drawString(font, fit(Component.translatable("gui.singulo.genesis.screen.cost",
                            GenesisCoreBlockEntity.formatFe(MassSynthesis.cost(e))).getString(), OUTPUT_LEFT - PX - 24), PX + 20, 60, DIM, false);
                    g.drawString(font, Component.translatable("gui.singulo.genesis.screen.synth", percent(core.synthFraction())),
                            PX, 74, MAGENTA, false);
                }
            }
            default -> { }
        }
        // 投入のスイッチ
        if (core.canToggleFeed()) {
            String key = core.manualStop() ? "gui.singulo.genesis.screen.feed_off"
                    : core.feeding() ? "gui.singulo.genesis.screen.feed_on" : "gui.singulo.genesis.screen.feed_redstone";
            g.drawString(font, Component.translatable(key), FEED_X + 15, FEED_Y + 1, core.feeding() ? 0xFF7CE0A0 : 0xFFFF9090, false);
        }
        // 窓の下: 受電と維持
        if (stage == GenesisCoreBlockEntity.KINDLING) {
            g.drawString(font, Component.translatable("gui.singulo.genesis.screen.intake", intake), VIEW_X, VIEW_Y + VIEW + 3, TEXT, false);
        } else if (stage == GenesisCoreBlockEntity.STABILIZING) {
            g.drawString(font, Component.translatable("gui.singulo.genesis.screen.intake", intake), VIEW_X, VIEW_Y + VIEW + 3,
                    core.intake() + 1 >= GenesisCoreBlockEntity.upkeep() ? TEXT : RED, false);
        } else if (stage == GenesisCoreBlockEntity.COMPLETE) {
            g.drawString(font, Component.translatable("gui.singulo.genesis.screen.intake", intake), VIEW_X, VIEW_Y + VIEW + 3, TEXT, false);
        }
        // 暴走の兆し
        if (stage == GenesisCoreBlockEntity.STABILIZING && core.surgeTicks() > 0) {
            boolean held = !core.feeding();
            if (held || ((int) (t / 6)) % 2 == 0) {
                Component c = Component.translatable(held ? "gui.singulo.genesis.screen.surge_held" : "gui.singulo.genesis.screen.surge");
                var lines = font.split(c, VIEW - 6);
                int top = VIEW_Y + VIEW - 3 - lines.size() * 10;
                g.fill(VIEW_X, top - 2, VIEW_X + VIEW, VIEW_Y + VIEW - 1, held ? 0xC0103848 : 0xC0601010);
                for (var seq : lines) {
                    g.drawString(font, seq, VIEW_X + VIEW / 2 - font.width(seq) / 2, top, held ? CYAN : 0xFFFFE0E0, false);
                    top += 10;
                }
            }
        }
    }

    /** 幅に収まらない文字列は末尾を「…」で切る。 */
    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    /** 「名前　値」を1行に（値は右寄せ）。 */
    private void pair(GuiGraphics g, String key, String value, int y, int color) {
        g.drawString(font, Component.translatable(key), PX, y, TEXT, false);
        g.drawString(font, value, PX + PW - font.width(value), y, color, false);
    }

    private static int phaseColor(int stage) {
        return switch (stage) {
            case GenesisCoreBlockEntity.UNFORMED -> 0xFF9AA0B8;
            case GenesisCoreBlockEntity.CRADLE -> 0xFFF4D890;
            case GenesisCoreBlockEntity.KINDLING -> EMBER;
            case GenesisCoreBlockEntity.STABILIZING -> CYAN;
            case GenesisCoreBlockEntity.BIRTH -> 0xFFFFFFFF;
            default -> MAGENTA;
        };
    }

    private static String percent(float f) {
        return String.format("%.0f%%", f * 100);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GenesisCoreBlockEntity core = core();
        if (button == 0 && core != null && core.canToggleFeed()
                && Panel.inside(mouseX, mouseY, leftPos + FEED_X - 1, topPos + FEED_Y - 1, 12, 12)
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GenesisCoreMenu.BUTTON_FEED);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
