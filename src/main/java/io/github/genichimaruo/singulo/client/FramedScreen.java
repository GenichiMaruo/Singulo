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

    /** プレイヤーのインベントリを出すか（出さない画面は溝も見出しもない）。 */
    protected boolean hasInventory() {
        return true;
    }

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

    // ------------------------------------------------------------------ 装填中の触媒

    /** 装填中の触媒（同期された番号から。なければ空）。 */
    protected static net.minecraft.world.item.ItemStack loadedCatalyst(int itemId) {
        return itemId < 0 ? net.minecraft.world.item.ItemStack.EMPTY
                : new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(itemId));
    }

    /**
     * 触媒スロット（sx, sy）の下の細いゲージに、装填中の触媒の残りを出す。予備が空なら、スロットに装填中の触媒を薄く出す。
     * fillPermille は残りの千分率。
     */
    protected void drawLoadedCatalyst(GuiGraphics g, int sx, int sy, int itemId, int fillPermille, boolean supplyEmpty) {
        net.minecraft.world.item.ItemStack loaded = loadedCatalyst(itemId);
        Panel.well(g, skin, sx, sy + 19, 16, 2);
        if (loaded.isEmpty()) {
            return;
        }
        int w = Math.round(16 * Math.min(1000, Math.max(0, fillPermille)) / 1000F);
        if (w > 0) {
            g.fill(sx, sy + 19, sx + w, sy + 21, Panel.AMBER);
            Panel.glass(g, sx, sy + 19, w, 2);
        }
        if (supplyEmpty) {
            g.pose().pushPose();
            g.renderItem(loaded, sx, sy);
            g.pose().translate(0, 0, 200);
            g.fill(sx, sy, sx + 16, sy + 16, 0x80000000 | (skin.fill() & 0xFFFFFF));
            g.pose().popPose();
        }
    }

    /** 触媒スロットとゲージの上にカーソルがあるときの説明（装填中のものがなければ null）。 */
    @Nullable
    protected List<Component> loadedCatalystLines(int itemId, int fillPermille, boolean supplyEmpty) {
        net.minecraft.world.item.ItemStack loaded = loadedCatalyst(itemId);
        if (loaded.isEmpty()) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.singulo.catalyst.loaded", loaded.getHoverName(), Math.round(fillPermille / 10F)));
        lines.add(Component.translatable("gui.singulo.catalyst.loaded_hint").withStyle(st -> st.withColor(0xFF8A949E)));
        if (supplyEmpty) {
            lines.add(Component.translatable("gui.singulo.catalyst.eject_hint").withStyle(st -> st.withColor(0xFF8A949E)));
        }
        return lines;
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

    protected List<Component> powerTooltip() {
        return List.of(Component.translatable(Boolean.TRUE.equals(powered()) ? "gui.singulo.power.on" : "gui.singulo.power.off"));
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

    /** JEI supplies GUI-relative coordinates; popup bounds are in screen coordinates. */
    public boolean popupBlocksGuiInput(double guiMouseX, double guiMouseY) {
        return overPopup(leftPos + guiMouseX, topPos + guiMouseY);
    }

    /** 窓の下にあるスロットは、窓の上にマウスがある間は反応しない。 */
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return !overPopup(mouseX, mouseY) && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 位置

    /**
     * 枠を画面の箱の左右からどれだけ内側に描くか。中身が幅いっぱいに並ぶ画面（作業指令台）は、
     * 中身の座標を変えずに、枠だけを標準の幅にするのに使う（インベントリは枠の中央のまま）。
     */
    protected int frameInset() {
        return 0;
    }

    /** 枠の左端（画面の座標）と幅。 */
    protected int frameX() {
        return leftPos + frameInset();
    }

    protected int frameWidth() {
        return imageWidth - 2 * frameInset();
    }

    /** 枠の外に出す、画面ごとの部品の範囲（クリックしても物を捨てず、JEI の一覧も避ける）。 */
    protected List<int[]> outsideAreas() {
        return List.of();
    }

    /** ヘッダーの右側の部品が始まる位置（電源と、画面ごとの部品の左）。 */
    protected int headerRight() {
        int right = powered() != null ? GuiFrame.powerX(frameWidth()) - 4 : frameWidth() - 6;
        return frameInset() + right - headerReserved();
    }

    private int statusLeft() {
        return status() == null ? headerRight() : headerRight() - Panel.CHIP_WIDTH;
    }

    protected int titleWidth() {
        return statusLeft() - 4 - titleLabelX;
    }

    private int[] railRect(int row) {
        return new int[]{frameX() + GuiFrame.railX(frameWidth()), topPos + GuiFrame.railY(row), GuiFrame.RAIL_WIDTH - 1, GuiFrame.RAIL_TAB};
    }

    private int[] powerRect() {
        return new int[]{frameX() + GuiFrame.powerX(frameWidth()), topPos + GuiFrame.POWER_Y, GuiFrame.POWER_SIZE, GuiFrame.POWER_SIZE};
    }

    // ------------------------------------------------------------------ 描画

    /** 枠・ヘッダー・右レール・溝・インベントリの凹み・電源（画面の座標で描く）。 */
    protected void drawFrame(GuiGraphics g, int mouseX, int mouseY) {
        int x = frameX();
        int y = topPos;
        int w = frameWidth();
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            if (railUsed(row) && !railConnected(row)) {
                drawRailTab(g, row, mouseX, mouseY);
            }
        }
        Panel.frame(g, skin, x, y, w, imageHeight);
        Panel.header(g, skin, x, y, w);
        for (int row = 0; row < GuiFrame.RAIL_ROWS; row++) {
            if (railUsed(row) && railConnected(row)) {
                drawRailTab(g, row, mouseX, mouseY);
            }
        }
        if (hasInventory()) {
            Panel.groove(g, skin, x + 7, y + GuiFrame.grooveY(imageHeight), w - 14);
            int ix = x + GuiFrame.inventoryX(w);
            int iy = y + GuiFrame.inventoryY(imageHeight);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    Panel.slot(g, skin, ix + col * 18, iy + row * 18);
                }
            }
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, skin, ix + col * 18, y + GuiFrame.hotbarY(imageHeight));
            }
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
        if (hasInventory()) {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
        }
    }

    // ------------------------------------------------------------------ アイコンと値

    /** 画面に出した「アイコンと値」の場所と、重ねたときの説明（毎フレーム描き直す）。 */
    private final List<Stat> stats = new ArrayList<>();

    private record Stat(int x, int y, int w, int h, List<Component> tooltip) {}

    /** 「アイコンと値」の行の間隔。 */
    protected static final int STAT_PITCH = 11;

    /** 装置固有の部分で使える縦の範囲（ヘッダーの下から、溝の上か枠の下端の手前まで）の上端と下端（下端は含まない）。 */
    protected int contentTop() {
        return GuiFrame.HEADER - 1;
    }

    protected int contentEnd() {
        return hasInventory() ? GuiFrame.grooveY(imageHeight) : imageHeight - 2;
    }

    /** 高さ height の物を、装置固有の部分の上下中央に置くときの上端。 */
    protected int centeredY(int height) {
        return contentTop() + (contentEnd() - contentTop() - height + 1) / 2;
    }

    /** rows 行の「アイコンと値」を上下中央に並べるときの、1行目の高さ。 */
    protected int statsTop(int rows) {
        return centeredY(rows * STAT_PITCH - 3);
    }

    /** 毎フレームの描画の最初に呼ぶ。 */
    protected void clearStats() {
        stats.clear();
    }

    /** アイコンと短い値（文の代わり）。x, y は枠の左上からの位置。重ねると tooltip を出す。 */
    protected void stat(GuiGraphics g, int x, int y, Panel.Icon icon, int iconColor, String value, int color, List<Component> tooltip) {
        Panel.icon(g, leftPos + x, topPos + y, icon, iconColor);
        g.drawString(font, value, leftPos + x + 10, topPos + y, color, false);
        stats.add(new Stat(leftPos + x, topPos + y - 1, 10 + font.width(value), 10, tooltip));
    }

    /** アイコンと小さな横長のゲージ（液体の量など。数は重ねると出る）。 */
    protected void statBar(GuiGraphics g, int x, int y, Panel.Icon icon, int iconColor, int width, double fraction, int color,
                           List<Component> tooltip) {
        Panel.icon(g, leftPos + x, topPos + y, icon, iconColor);
        int bx = leftPos + x + 11;
        int by = topPos + y + 1;
        Panel.well(g, skin, bx, by, width, 5);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(bx, by, bx + filled, by + 5, color);
            Panel.glass(g, bx, by, filled, 5);
        }
        stats.add(new Stat(leftPos + x, topPos + y - 1, 12 + width, 10, tooltip));
    }

    /** 範囲（枠の左上からの位置）に重ねたときの説明（絵や記号の説明。毎フレーム描き直す）。 */
    protected void area(int x, int y, int w, int h, List<Component> tooltip) {
        stats.add(new Stat(leftPos + x, topPos + y, w, h, tooltip));
    }

    /** 重ねている「アイコンと値」と範囲のツールチップ。出したら true。 */
    protected boolean renderStatTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (Stat s : stats) {
            if (!s.tooltip().isEmpty() && Panel.inside(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                Panel.tooltip(g, font, s.tooltip(), 200, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    /** 専用スロットの凹みと、空のときに入れる物のグレーの見本。 */
    protected void ghostSlot(GuiGraphics g, int x, int y, net.minecraft.world.inventory.Slot slot, net.minecraft.world.item.ItemStack ghost) {
        Panel.slot(g, skin, x, y);
        if (!slot.hasItem() && !ghost.isEmpty()) {
            g.pose().pushPose();
            g.renderItem(ghost, x, y);
            g.pose().translate(0, 0, 200);
            g.fill(x, y, x + 16, y + 16, 0xA0000000 | (skin.fill() & 0xFFFFFF));
            g.pose().popPose();
        }
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
            Panel.tooltip(g, font, powerTooltip(), 200, mouseX, mouseY);
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

    /**
     * 電源と右レールのクリックは、どの画面でも受け付ける（自分で mouseClicked を書く画面は、先に clickFrame を呼ぶ）。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickFrame(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

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
        if (!super.hasClickedOutside(mouseX, mouseY, left, top, button) || onRail(mouseX, mouseY) || overPopup(mouseX, mouseY)) {
            return false;
        }
        for (int[] r : outsideAreas()) {
            if (Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                return false;
            }
        }
        return true;
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
        for (int[] r : outsideAreas()) {
            areas.add(new Rect2i(r[0], r[1], r[2] + 2, r[3] + 2));
        }
        return areas;
    }
}
