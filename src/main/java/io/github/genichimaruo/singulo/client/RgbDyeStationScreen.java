package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.dye.RgbDyeStationBlockEntity;
import io.github.genichimaruo.singulo.dye.RgbDyeStationMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RGB染色台の画面。マイクラらしいドットの見た目で、色の輪（32×32 のマス。1マス2ドット。中心ほど白に近い）から色みと
 * 鮮やかさを、右の明るさのつまみ（16段）から明るさを選ぶ。右上に染める物の枠・「染める」ボタン・色見本、下に5色の
 * 染料の枠とインクの目盛り。細かい数は、ボタン・見本・目盛りに重ねたときだけ出す。
 */
public class RgbDyeStationScreen extends FramedScreen<RgbDyeStationMenu> {
    private static final int CELLS = 32, CELL = RgbDyeStationMenu.WHEEL_SIZE / CELLS, STEPS = 16;
    private static final int[] INK_COLORS = {0xFFD83A3A, 0xFF4CB04C, 0xFF3A62D8, 0xFFF2F2F2, 0xFF2A2A30};
    private static final net.minecraft.world.item.Item[] DYES = {Items.RED_DYE, Items.GREEN_DYE, Items.BLUE_DYE, Items.WHITE_DYE, Items.BLACK_DYE};

    /** 画面で選んでいる色（色み・鮮やかさ・明るさ。灰色でも色みを覚えておく）。 */
    private float hue, sat, val;
    /** 最後にサーバーへ送った色（ほかの人が変えたときだけ、画面の色を合わせ直す）。 */
    private int sent = -1;
    private boolean dragWheel, dragSlider;

    public RgbDyeStationScreen(RgbDyeStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RgbDyeStationMenu.WIDTH, RgbDyeStationMenu.HEIGHT);
        syncFrom(menu.color());
    }

    private void syncFrom(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        val = max;
        sat = max <= 0 ? 0 : d / max;
        if (d > 0) {
            float h = max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
            hue = Mth.positiveModulo(h / 6, 1);
        }
        sent = rgb;
    }

    private int rgb() {
        return Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
    }

    private void send() {
        int c = rgb();
        if (c != sent && minecraft != null && minecraft.gameMode != null) {
            sent = c;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RgbDyeStationMenu.SET_COLOR | c);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int server = menu.color();
        if (!dragWheel && !dragSlider && server != sent && server != rgb()) {
            syncFrom(server);                                     // ほかの人が色を変えた
        }
    }

    // ------------------------------------------------------------------ 入力

    private boolean pickWheel(double mx, double my) {
        float cx = leftPos + RgbDyeStationMenu.WHEEL_X + RgbDyeStationMenu.WHEEL_SIZE / 2F;
        float cy = topPos + RgbDyeStationMenu.WHEEL_Y + RgbDyeStationMenu.WHEEL_SIZE / 2F;
        // マスの中心にそろえる（ドットの見た目どおりの色になる）
        int i = Mth.floor((mx - (cx - RgbDyeStationMenu.WHEEL_SIZE / 2F)) / CELL), j = Mth.floor((my - (cy - RgbDyeStationMenu.WHEEL_SIZE / 2F)) / CELL);
        float[] hs = cellHueSat(Mth.clamp(i, 0, CELLS - 1), Mth.clamp(j, 0, CELLS - 1));
        if (hs == null && !dragWheel) {
            return false;
        }
        if (hs == null) {
            // 輪の外へドラッグしたら、縁の色のまま
            float dx = (float) (mx - cx), dy = (float) (my - cy);
            hue = Mth.positiveModulo((float) (Math.atan2(dy, dx) / (2 * Math.PI)), 1);
            sat = 1;
        } else {
            hue = hs[0];
            sat = hs[1];
        }
        send();
        return true;
    }

    private void pickSlider(double my) {
        int step = Mth.clamp(Mth.floor((my - topPos - RgbDyeStationMenu.WHEEL_Y) / (RgbDyeStationMenu.WHEEL_SIZE / (float) STEPS)), 0, STEPS - 1);
        val = 1 - step / (float) (STEPS - 1);
        send();
    }

    /** マス (i, j) の色みと鮮やかさ（輪の外なら null）。 */
    private static float[] cellHueSat(int i, int j) {
        float dx = (i + 0.5F) / CELLS * 2 - 1, dy = (j + 0.5F) / CELLS * 2 - 1;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        if (r > 1) {
            return null;
        }
        return new float[] {Mth.positiveModulo((float) (Math.atan2(dy, dx) / (2 * Math.PI)), 1), Math.min(1, r * 1.04F)};
    }

    private boolean overWheel(double mx, double my) {
        return Panel.inside(mx, my, leftPos + RgbDyeStationMenu.WHEEL_X, topPos + RgbDyeStationMenu.WHEEL_Y,
                RgbDyeStationMenu.WHEEL_SIZE, RgbDyeStationMenu.WHEEL_SIZE);
    }

    private boolean overSlider(double mx, double my) {
        return Panel.inside(mx, my, leftPos + RgbDyeStationMenu.SLIDER_X, topPos + RgbDyeStationMenu.WHEEL_Y,
                RgbDyeStationMenu.SLIDER_W, RgbDyeStationMenu.WHEEL_SIZE);
    }

    private boolean overApply(double mx, double my) {
        return Panel.inside(mx, my, leftPos + RgbDyeStationMenu.APPLY_X, topPos + RgbDyeStationMenu.APPLY_Y,
                RgbDyeStationMenu.APPLY_W, RgbDyeStationMenu.APPLY_H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overWheel(mx, my) && pickWheel(mx, my)) {
            dragWheel = true;
            return true;
        }
        if (button == 0 && overSlider(mx, my)) {
            dragSlider = true;
            pickSlider(my);
            return true;
        }
        if (button == 0 && overApply(mx, my) && canApply() && minecraft != null && minecraft.gameMode != null) {
            send();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RgbDyeStationMenu.APPLY);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragWheel) {
            pickWheel(mx, my);
            return true;
        }
        if (dragSlider) {
            pickSlider(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragWheel = dragSlider = false;
        return super.mouseReleased(mx, my, button);
    }

    /** 画面の値で染められるか（染める物があって、インクが足りる）。 */
    private boolean canApply() {
        ItemStack target = menu.getSlot(RgbDyeStationBlockEntity.TARGET).getItem();
        if (target.isEmpty()) {
            return false;
        }
        int[] need = RgbDyeStationBlockEntity.cost(rgb());
        for (int i = 0; i < RgbDyeStationBlockEntity.INKS; i++) {
            if (menu.ink(i) < need[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 描画

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawFrame(g, mouseX, mouseY);
        int x = leftPos, y = topPos;
        int wx = x + RgbDyeStationMenu.WHEEL_X, wy = y + RgbDyeStationMenu.WHEEL_Y, size = RgbDyeStationMenu.WHEEL_SIZE;
        Panel.well(g, skin, wx - 2, wy - 2, size + 4, size + 4);
        // 色の輪（ドットのマス）
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                float[] hs = cellHueSat(i, j);
                if (hs != null) {
                    g.fill(wx + i * CELL, wy + j * CELL, wx + (i + 1) * CELL, wy + (j + 1) * CELL, 0xFF000000 | Mth.hsvToRgb(hs[0], hs[1], val));
                }
            }
        }
        // 選んでいる所の印（白黒の小さな四角）
        float angle = hue * 2 * Mth.PI;
        int mx = wx + Math.round(size / 2F + (float) Math.cos(angle) * sat * size / 2F / 1.04F);
        int my = wy + Math.round(size / 2F + (float) Math.sin(angle) * sat * size / 2F / 1.04F);
        g.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
        g.fill(mx - 2, my - 2, mx + 2, my + 2, 0xFFFFFFFF);
        g.fill(mx - 1, my - 1, mx + 1, my + 1, 0xFF000000 | rgb());
        // 明るさのつまみ（16段）
        int sx = x + RgbDyeStationMenu.SLIDER_X, sw = RgbDyeStationMenu.SLIDER_W;
        Panel.well(g, skin, sx - 1, wy - 1, sw + 2, size + 2);
        float stepH = size / (float) STEPS;
        for (int k = 0; k < STEPS; k++) {
            float v = 1 - k / (float) (STEPS - 1);
            g.fill(sx, wy + Math.round(k * stepH), sx + sw, wy + Math.round((k + 1) * stepH), 0xFF000000 | Mth.hsvToRgb(hue, sat, v));
        }
        int step = Math.round((1 - val) * (STEPS - 1));
        int ky = wy + Math.round(step * stepH);
        g.fill(sx - 3, ky - 1, sx + sw + 3, ky + Math.round(stepH) + 1, 0xFF000000);
        g.fill(sx - 2, ky, sx + sw + 2, ky + Math.round(stepH), 0xFFFFFFFF);
        g.fill(sx, ky + 1, sx + sw, ky + Math.round(stepH) - 1, 0xFF000000 | Mth.hsvToRgb(hue, sat, val));
        // 染める物の枠、ボタン、色見本
        var target = menu.getSlot(RgbDyeStationBlockEntity.TARGET);
        ghostSlot(g, x + target.x, y + target.y, target, new ItemStack(Items.LEATHER_CHESTPLATE));
        boolean ok = canApply();
        Panel.labelButton(g, font, skin, x + RgbDyeStationMenu.APPLY_X, y + RgbDyeStationMenu.APPLY_Y, RgbDyeStationMenu.APPLY_W,
                RgbDyeStationMenu.APPLY_H, Component.translatable("gui.singulo.rgb_dye_station.apply"),
                !ok ? Panel.Press.DISABLED : overApply(mouseX, mouseY) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        int qx = x + RgbDyeStationMenu.SWATCH_X, qy = y + RgbDyeStationMenu.SWATCH_Y, q = RgbDyeStationMenu.SWATCH_SIZE;
        Panel.well(g, skin, qx - 1, qy - 1, q + 2, q + 2);
        g.fill(qx, qy, qx + q, qy + q, 0xFF000000 | rgb());
        g.fill(qx, qy, qx + q, qy + 1, 0x40FFFFFF);                                        // 上の縁の光（ドットの立体感）
        // 染料の枠とインクの目盛り（枠の下の横長の棒）
        for (int i = 0; i < RgbDyeStationBlockEntity.INKS; i++) {
            var s = menu.getSlot(i);
            ghostSlot(g, x + s.x, y + s.y, s, new ItemStack(DYES[i]));
            int bx = x + s.x, by = y + s.y + 19;
            Panel.well(g, skin, bx - 1, by - 1, 18, 6);
            int filled = Math.round(16F * menu.ink(i) / RgbDyeStationBlockEntity.MAX_INK);
            if (filled > 0) {
                g.fill(bx, by, bx + filled, by + 4, INK_COLORS[i]);
                g.fill(bx, by, bx + filled, by + 1, GuiSkin.mix(INK_COLORS[i], 0xFFFFFFFF, 0.4F));
            }
            int need = RgbDyeStationBlockEntity.cost(rgb())[i];
            if (need > 0) {                                                                   // 1回に使う分の印
                int nx = bx + Math.round(16F * need / RgbDyeStationBlockEntity.MAX_INK);
                g.fill(bx, by + 3, Math.min(bx + 16, nx), by + 4, 0xFFFFE070);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (overApply(mouseX, mouseY)) {
            g.renderComponentTooltip(font, applyTooltip(), mouseX, mouseY);
            return;
        }
        int qx = leftPos + RgbDyeStationMenu.SWATCH_X, qy = topPos + RgbDyeStationMenu.SWATCH_Y;
        if (Panel.inside(mouseX, mouseY, qx, qy, RgbDyeStationMenu.SWATCH_SIZE, RgbDyeStationMenu.SWATCH_SIZE)) {
            g.renderTooltip(font, Component.literal(String.format("#%06X", rgb())), mouseX, mouseY);
            return;
        }
        if (overWheel(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.rgb_dye_station.wheel"), mouseX, mouseY);
            return;
        }
        if (overSlider(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.rgb_dye_station.brightness"), mouseX, mouseY);
            return;
        }
        for (int i = 0; i < RgbDyeStationBlockEntity.INKS; i++) {
            var s = menu.getSlot(i);
            if (Panel.inside(mouseX, mouseY, leftPos + s.x - 1, topPos + s.y + 18, 18, 6)) {
                g.renderTooltip(font, Component.translatable("gui.singulo.rgb_dye_station.ink." + i, menu.ink(i), RgbDyeStationBlockEntity.MAX_INK),
                        mouseX, mouseY);
                return;
            }
        }
    }

    /** ボタンの説明: 使うインク（足りない色は赤く）と、染められない理由。 */
    private List<Component> applyTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.singulo.rgb_dye_station.cost"));
        int[] need = RgbDyeStationBlockEntity.cost(rgb());
        for (int i = 0; i < RgbDyeStationBlockEntity.INKS; i++) {
            if (need[i] > 0) {
                boolean enough = menu.ink(i) >= need[i];
                int c = enough ? 0xFFC8C8C8 : 0xFFFF6060;
                lines.add(Component.translatable("gui.singulo.rgb_dye_station.cost." + i, need[i]).withStyle(st -> st.withColor(c)));
            }
        }
        if (menu.getSlot(RgbDyeStationBlockEntity.TARGET).getItem().isEmpty()) {
            lines.add(Component.translatable("gui.singulo.rgb_dye_station.no_target").withStyle(st -> st.withColor(0xFFFF9A50)));
        }
        return lines;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        AndroidStationScreen.fit(g, font, title, titleLabelX, titleLabelY, titleWidth(), skin.text());
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
    }
}
