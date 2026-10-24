package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 全画面共通の骨格（GuiFrame）を描く画面。各画面は「装置固有の部分」だけを描き、
 * ヘッダー（装置名・状態・電源）、右レール、溝、プレイヤーのインベントリはここで描く。
 */
public abstract class FramedScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected GuiSkin skin = GuiSkin.STANDARD;

    FramedScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = GuiFrame.inventoryX(width);
        inventoryLabelY = GuiFrame.inventoryLabelY(height);
    }

    // ------------------------------------------------------------------ 各画面が決めること

    /** 右レールのその段（GuiFrame.RAIL_*）を使うか。 */
    protected boolean railUsed(int row) {
        return false;
    }

    /** その段を枠とつながった形で描くか（開いている設定や、スロットを入れた段）。 */
    protected boolean railConnected(int row) {
        return false;
    }

    /** 段の中身（アイコンやスロットの凹み）。x, y はタブの左上。 */
    protected void drawRail(GuiGraphics g, int row, int x, int y, boolean hover) {}

    protected boolean clickRail(int row, int button) {
        return false;
    }

    @Nullable
    protected Component railTooltip(int row) {
        return null;
    }

    /** 電源が入っているか（null なら電源ボタンを出さない）。 */
    @Nullable
    protected Boolean powered() {
        return null;
    }

    protected void togglePower() {}

    protected Component powerTooltip() {
        return Component.translatable(Boolean.TRUE.equals(powered()) ? "gui.singulo.power.on" : "gui.singulo.power.off");
    }

    /** 電源の左に置く、その画面だけのヘッダーの部品の幅。この分だけ状態表示が左に寄る。 */
    protected int headerReserved() {
        return 0;
    }

    /** 状態の記号（null なら出さない）。 */
    @Nullable
    protected Panel.Status status() {
        return null;
    }

    /** 状態の名前（ツールチップの1行目）。 */
    protected Component statusText() {
        return Component.empty();
    }

    protected int statusColor() {
        return GuiSkin.IDLE;
    }

    protected List<Component> statusTooltip() {
        return List.of();
    }

    /** 画面の上に浮かぶ窓（開いているものだけが効く）。 */
    protected List<Popup> popups() {
        return List.of();
    }

    protected boolean overPopup(double mouseX, double mouseY) {
        for (Popup p : popups()) {
            if (p.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** 窓の下にあるスロットは、窓の上にマウスがある間は反応しない。 */
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return !overPopup(mouseX, mouseY) && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 位置

    /** ヘッダーの右側の部品が始まる位置（電源と、画面ごとの部品の左）。 */
    protected int headerRight() {
        int right = powered() != null ? GuiFrame.powerX(imageWidth) - 4 : imageWidth - 6;
        return right - headerReserved();
    }

    private int statusLeft() {
        return status() == null ? headerRight() : headerRight() - Panel.CHIP_WIDTH;
    }

    protected int titleWidth() {
        return statusLeft() - 4 - titleLabelX;
    }

    private int[] railRect(int row) {
        return new int[]{leftPos + GuiFrame.railX(imageWidth), topPos + GuiFrame.railY(row), GuiFrame.RAIL_WIDTH - 1, GuiFrame.RAIL_TAB};
    }

    private int[] powerRect() {
        return new int[]{leftPos + GuiFrame.powerX(imageWidth), topPos + GuiFrame.POWER_Y, GuiFrame.POWER_SIZE, GuiFrame.POWER_SIZE};
    }

    // ------------------------------------------------------------------ 描画

    /** 枠・ヘッダー・右レール・溝・インベントリの凹み・電源（画面の座標で描く）。 */
    protected void drawFrame(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            if (railUsed(row) && !railConnected(row)) {
                drawRailTab(g, row, mouseX, mouseY);
            }
        }
        Panel.frame(g, skin, x, y, imageWidth, imageHeight);
        Panel.header(g, skin, x, y, imageWidth);
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            if (railUsed(row) && railConnected(row)) {
                drawRailTab(g, row, mouseX, mouseY);
            }
        }
        Panel.groove(g, skin, x + 7, y + GuiFrame.grooveY(imageHeight), imageWidth - 14);
        int ix = x + GuiFrame.inventoryX(imageWidth);
        int iy = y + GuiFrame.inventoryY(imageHeight);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, skin, ix + col * 18, iy + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, skin, ix + col * 18, y + GuiFrame.hotbarY(imageHeight));
        }
        Boolean on = powered();
        if (on != null) {
            int[] p = powerRect();
            Panel.power(g, skin, p[0], p[1], p[2], on, Panel.inside(mouseX, mouseY, p[0], p[1], p[2], p[3]));
        }
    }

    private void drawRailTab(GuiGraphics g, int row, int mouseX, int mouseY) {
        int[] r = railRect(row);
        boolean hover = Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        boolean connected = railConnected(row);
        Panel.railTab(g, skin, r[0], r[1], connected, hover && !connected, topPos, imageHeight);
        drawRail(g, row, r[0] + (connected ? 3 : 2), r[1] + 3, hover);
    }

    /** 装置名・状態・「インベントリ」の見出し（renderLabels の座標で描く）。 */
    protected void drawLabels(GuiGraphics g) {
        AndroidStationScreen.fit(g, font, title, titleLabelX, titleLabelY, titleWidth(), skin.text());
        Panel.Status status = status();
        if (status != null) {
            Panel.chip(g, skin, headerRight(), GuiFrame.POWER_Y, status, statusColor());
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
    }

    /** 骨格の部品のツールチップ。出したら true。 */
    protected boolean renderFrameTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        if (Panel.inside(mouseX, mouseY, x + titleLabelX, y + titleLabelY - 1, titleWidth(), 10) && font.width(title) > titleWidth()) {
            Panel.tooltip(g, font, List.of(title), 200, mouseX, mouseY);
            return true;
        }
        if (status() != null && Panel.inside(mouseX, mouseY, x + statusLeft(), y + GuiFrame.POWER_Y, headerRight() - statusLeft(), 11)) {
            List<Component> lines = new ArrayList<>();
            lines.add(statusText().copy().withStyle(st -> st.withColor(statusColor())));
            lines.addAll(statusTooltip());
            Panel.tooltip(g, font, lines, 200, mouseX, mouseY);
            return true;
        }
        int[] p = powerRect();
        if (powered() != null && Panel.inside(mouseX, mouseY, p[0], p[1], p[2], p[3])) {
            g.renderTooltip(font, powerTooltip(), mouseX, mouseY);
            return true;
        }
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            int[] r = railRect(row);
            if (railUsed(row) && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                Component tip = railTooltip(row);
                if (tip != null) {
                    g.renderTooltip(font, tip, mouseX, mouseY);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 操作

    /** 電源と右レールのクリック。扱ったら true。 */
    protected boolean clickFrame(double mouseX, double mouseY, int button) {
        int[] p = powerRect();
        if (powered() != null && Panel.inside(mouseX, mouseY, p[0], p[1], p[2], p[3])) {
            togglePower();
            return true;
        }
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            int[] r = railRect(row);
            if (railUsed(row) && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]) && clickRail(row, button)) {
                return true;
            }
        }
        return false;
    }

    private boolean onRail(double mouseX, double mouseY) {
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            int[] r = railRect(row);
            if (railUsed(row) && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                return true;
            }
        }
        return false;
    }

    /** 右レールは枠の外にあるが、そこをクリックしても持っている物を捨てない。 */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return super.hasClickedOutside(mouseX, mouseY, left, top, button) && !onRail(mouseX, mouseY) && !overPopup(mouseX, mouseY);
    }

    /** 枠の外に出ている部分（JEI の一覧が重ならないようにする）。 */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            if (railUsed(row)) {
                int[] r = railRect(row);
                areas.add(new Rect2i(r[0], r[1], r[2] + 2, r[3] + 2));
            }
        }
        for (Popup p : popups()) {
            if (p.open) {
                areas.add(new Rect2i(p.x, p.y, p.w + 1, p.h + 1));
            }
        }
        return areas;
    }
}
