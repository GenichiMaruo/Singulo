package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.AndroidMenu;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 護衛のアンドロイドの画面。落ち着いた灰青の地に、型の色を少しだけ差す。上の帯に名前（クリックで変えられる）、
 * 左にアンドロイドの姿（マウスの方を向く）と吹き出しのひとこと（敵を見つけた・ひま・けが・充電などで変わる）、
 * 右にハートの体力、電池の充電、動き方（追従中はクリックで「追従のみ」と「追従・護衛」を切り替える）、装甲の値、
 * 自己修理キットと装甲板のスロット。右下に小さな解体のボタン（2回押すと解体）。
 * 名前の帯の左端のボタンで「装備と持ち物」のページに切り替える（アップグレード・作業のオン／オフ・持ち物・バックパック）。
 */
public class AndroidScreen extends AbstractContainerScreen<AndroidMenu> {
    private static final int PORTRAIT_X = 8, PORTRAIT_Y = 24, PORTRAIT_W = 72, PORTRAIT_H = 96;
    private static final int RIGHT_X = 88;
    private static final int NAME_Y = 4, NAME_H = 14;
    private static final int DISMANTLE_X = AndroidMenu.DISMANTLE_X, DISMANTLE_Y = AndroidMenu.DISMANTLE_Y;
    private static final int RESET_X = 190, RESET_Y = NAME_Y + 2;
    private static final int MODE_X = RIGHT_X, MODE_Y = 65, MODE_W = 78, MODE_H = 13;
    /** ページを切り替えるボタン（名前の帯の左端）。 */
    private static final int PAGE_X = 6, PAGE_Y = NAME_Y + 1, PAGE_S = 12;
    /** バックパックの欄の左端（スロットの1マス手前）。 */
    private static final int PACK_X_FRAME = AndroidMenu.PACK_X - 4;

    private static final int PAPER = 0xFFE9ECF1;
    private static final int BORDER = 0xFF5C6475;
    private static final int INK = 0xFF3C4350;
    private static final int SLOT_EDGE = 0xFF373C48;
    private static final int SLOT_FILL = 0xFF8B8F98;

    private final NameEditor names = new NameEditor();
    /** 解体のボタンを1回押した時刻（もう一度押すと解体。3秒で戻る）。 */
    private long dismantleArmed;

    public AndroidScreen(AndroidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = AndroidMenu.WIDTH;
        imageHeight = AndroidMenu.HEIGHT;
        inventoryLabelX = AndroidMenu.INV_X;
        inventoryLabelY = AndroidMenu.INV_Y - 11;
    }

    private int accent() {
        RuinAndroid a = menu.android();
        return a == null ? 0x78E1FF : a.accent();
    }

    private static int mix(int a, int b, float k) {
        return 0xFF000000 | FxDraw.lerpRgb(a, b, k);
    }

    private Component name() {
        RuinAndroid a = menu.android();
        return a == null ? title : a.nickname();
    }

    /** 名前を付けているか（元に戻すボタンを出す）。 */
    private boolean named() {
        RuinAndroid a = menu.android();
        return a != null && a.hasCustomName() && !names.editing();
    }

    /** 追従中か（動き方の札で、戦うかを切り替えられる）。 */
    private boolean following() {
        int mode = menu.value(AndroidMenu.D_MODE);
        return mode == RuinAndroid.MODE_FOLLOW || mode == io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.MODE_FOLLOW_ONLY;
    }

    /** 動き方の札の文字。 */
    private Component modeName() {
        int mode = menu.value(AndroidMenu.D_MODE);
        if (following()) {
            return Component.translatable(mode == RuinAndroid.MODE_FOLLOW ? "gui.singulo.android.follow.escort" : "gui.singulo.android.follow.only");
        }
        return Component.translatable("gui.singulo.android.mode", Component.translatable("gui.singulo.android_station.mode." + mode));
    }

    private boolean gearPage() {
        return menu.page() == AndroidMenu.PAGE_GEAR;
    }

    private boolean armed() {
        return Util.getMillis() - dismantleArmed < 3000;
    }

    // ------------------------------------------------------------------ 入力

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (names.editing()) {
            EditBox box = names.box();
            if (box != null && !box.isMouseOver(mouseX, mouseY)) {
                removeWidget(names.commit(menu.containerId));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (named() && Panel.inside(mouseX, mouseY, leftPos + RESET_X, topPos + RESET_Y, 10, 10)) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + PAGE_X, topPos + PAGE_Y, PAGE_S, PAGE_S)) {
            menu.setPage(gearPage() ? AndroidMenu.PAGE_STATUS : AndroidMenu.PAGE_GEAR);
            dismantleArmed = 0;
            return true;
        }
        if (gearPage() && Panel.inside(mouseX, mouseY, leftPos + AndroidMenu.WORK_X, topPos + AndroidMenu.WORK_Y, AndroidMenu.WORK_W,
                AndroidMenu.WORK_H) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_WORK);
            return true;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + 24, topPos + NAME_Y, imageWidth - 48, NAME_H)) {
            RuinAndroid a = menu.android();
            String current = a != null && a.hasCustomName() ? a.getCustomName().getString() : "";
            EditBox box = names.open(font, leftPos + 30, topPos + NAME_Y, imageWidth - 60, current);
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        if (!gearPage() && following() && Panel.inside(mouseX, mouseY, leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H)
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_ESCORT);
            return true;
        }
        if (!gearPage() && Panel.inside(mouseX, mouseY, leftPos + DISMANTLE_X, topPos + DISMANTLE_Y, 12, 12) && minecraft != null
                && minecraft.gameMode != null) {
            if (armed()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_DISMANTLE);
            } else {
                dismantleArmed = Util.getMillis();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (names.key(keyCode, scanCode, modifiers)) {
            case 1 -> {
                removeWidget(names.commit(menu.containerId));
                return true;
            }
            case 2 -> {
                removeWidget(names.cancel());
                return true;
            }
            case 3 -> {
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (named() && Panel.inside(mouseX, mouseY, leftPos + RESET_X, topPos + RESET_Y, 10, 10)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && Panel.inside(mouseX, mouseY, leftPos + 24, topPos + NAME_Y, imageWidth - 48, NAME_H)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + PAGE_X, topPos + PAGE_Y, PAGE_S, PAGE_S)) {
            g.renderTooltip(font, Component.translatable(gearPage() ? "gui.singulo.android.page.status" : "gui.singulo.android.page.gear"),
                    mouseX, mouseY);
        }
        if (gearPage()) {
            if (Panel.inside(mouseX, mouseY, leftPos + AndroidMenu.WORK_X, topPos + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android.work.hint"), 180), mouseX, mouseY);
            }
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + DISMANTLE_X, topPos + DISMANTLE_Y, 12, 12)) {
            g.renderTooltip(font, font.split(Component.translatable(armed() ? "gui.singulo.android.dismantle.confirm"
                    : "gui.singulo.android.dismantle"), 180), mouseX, mouseY);
        }
        if (following() && Panel.inside(mouseX, mouseY, leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android.follow.hint"), 180), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + RIGHT_X + 82, topPos + 64, 28, 14)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.armor"), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ 描画

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int accent = accent();
        int band = mix(accent, 0x2A3040, 0.55F);
        roundRect(g, x, y, imageWidth, imageHeight, BORDER);
        roundRect(g, x + 1, y + 1, imageWidth - 2, imageHeight - 2, PAPER);
        // 名前の帯（落ち着いた濃い色に白い字）と、両端の小さな星
        roundRect(g, x + 4, y + NAME_Y, imageWidth - 8, NAME_H, band);
        pageButton(g, x + PAGE_X, y + PAGE_Y, Panel.inside(mouseX, mouseY, x + PAGE_X, y + PAGE_Y, PAGE_S, PAGE_S), accent);
        star(g, x + imageWidth - 14, y + NAME_Y + 5, mix(accent, 0xFFFFFF, 0.5F));
        if (named()) {
            NameEditor.drawReset(g, x + RESET_X, y + RESET_Y, Panel.inside(mouseX, mouseY, x + RESET_X, y + RESET_Y, 10, 10),
                    mix(accent, 0x2A3040, 0.3F), mix(accent, 0xFFFFFF, 0.6F));
        }
        if (gearPage()) {
            renderGear(g, x, y, mouseX, mouseY, accent);
            return;
        }
        // 姿の枠
        int px = x + PORTRAIT_X;
        int py = y + PORTRAIT_Y;
        roundRect(g, px - 1, py - 1, PORTRAIT_W + 2, PORTRAIT_H + 2, BORDER);
        g.fillGradient(px, py, px + PORTRAIT_W, py + PORTRAIT_H, mix(accent, PAPER, 0.88F), mix(accent, PAPER, 0.7F));
        for (int i = 0; i < 5; i++) {
            star(g, px + 6 + (i * 23) % (PORTRAIT_W - 10), py + 8 + (i * 37) % (PORTRAIT_H - 34), mix(accent, PAPER, 0.5F));
        }
        RuinAndroid android = menu.android();
        if (android != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 2, py + 2, px + PORTRAIT_W - 2, py + PORTRAIT_H - 18, 32, 0.0625F,
                    mouseX, mouseY, android);
        }
        // 吹き出し（姿の下）
        roundRect(g, px + 2, py + PORTRAIT_H - 16, PORTRAIT_W - 4, 14, 0xFFFFFFFF);
        // 体力（ハート10個）
        int hp = menu.value(AndroidMenu.D_HP);
        int max = Math.max(1, menu.value(AndroidMenu.D_MAX_HP));
        float hearts = 10F * hp / max;
        for (int i = 0; i < 10; i++) {
            heart(g, x + RIGHT_X + i * 11, y + 35, Mth.clamp(hearts - i, 0, 1));
        }
        // 充電（電池）
        int bx = x + RIGHT_X;
        int by = y + 49;
        g.fill(bx, by, bx + 60, by + 12, BORDER);
        g.fill(bx + 1, by + 1, bx + 59, by + 11, 0xFFF8F9FB);
        g.fill(bx + 60, by + 3, bx + 63, by + 9, BORDER);
        int cw = 56 * Mth.clamp(menu.value(AndroidMenu.D_CHARGE), 0, 100) / 100;
        g.fill(bx + 2, by + 2, bx + 2 + cw, by + 10, mix(accent, 0x404858, 0.15F));
        g.fill(bx + 2, by + 2, bx + 2 + cw, by + 4, mix(accent, 0xFFFFFF, 0.45F));
        // 動き方の札と、装甲（盾）
        boolean modeHover = following() && Panel.inside(mouseX, mouseY, x + MODE_X, y + MODE_Y, MODE_W, MODE_H);
        roundRect(g, x + MODE_X, y + MODE_Y, MODE_W, MODE_H, mix(accent, PAPER, modeHover ? 0.55F : 0.75F));
        if (following()) {
            // 切り替えられる印（右端の小さな両向きの矢印）
            int ax = x + MODE_X + MODE_W - 8;
            int ay = y + MODE_Y + 3;
            int ac = mix(accent, 0x2A3040, 0.5F);
            g.fill(ax, ay + 1, ax + 5, ay + 2, ac);
            g.fill(ax + 3, ay, ax + 4, ay + 3, ac);
            g.fill(ax, ay + 5, ax + 5, ay + 6, ac);
            g.fill(ax + 1, ay + 4, ax + 2, ay + 7, ac);
        }
        shield(g, x + RIGHT_X + 84, y + 66, BORDER);
        // スロット（ふつうの持ち物と同じ落ち着いた灰色。個数の白い数字が読めるように）
        slots(g, x, y, accent, 0, 2);
        // 解体のボタン（小さく、目立たない）
        boolean hover = Panel.inside(mouseX, mouseY, x + DISMANTLE_X, y + DISMANTLE_Y, 12, 12);
        int dc = armed() ? 0xFFD05050 : hover ? 0xFF9AA0AC : 0xFFC4C9D2;
        roundRect(g, x + DISMANTLE_X, y + DISMANTLE_Y, 12, 12, dc);
        // スパナの形
        int wx = x + DISMANTLE_X + 3;
        int wy = y + DISMANTLE_Y + 3;
        int wc = armed() ? 0xFFFFFFFF : 0xFF5C6475;
        g.fill(wx, wy, wx + 2, wy + 2, wc);
        g.fill(wx + 4, wy, wx + 6, wy + 2, wc);
        g.fill(wx + 1, wy + 1, wx + 5, wy + 3, wc);
        g.fill(wx + 2, wy + 3, wx + 4, wy + 6, wc);
    }

    /** 見えているスロットの枠を描き、first から count 個（大事なスロット）は少し太くして型の色を差す。 */
    private void slots(GuiGraphics g, int x, int y, int accent, int first, int count) {
        for (Slot s : menu.slots) {
            if (!s.isActive()) {
                continue;
            }
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT_EDGE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT_FILL);
        }
        int c = mix(accent, BORDER, 0.5F);
        for (int i = first; i < first + count; i++) {
            Slot s = menu.slots.get(i);
            g.fill(x + s.x - 2, y + s.y - 2, x + s.x + 18, y + s.y - 1, c);
            g.fill(x + s.x - 2, y + s.y + 17, x + s.x + 18, y + s.y + 18, c);
            g.fill(x + s.x - 2, y + s.y - 1, x + s.x - 1, y + s.y + 17, c);
            g.fill(x + s.x + 17, y + s.y - 1, x + s.x + 18, y + s.y + 17, c);
        }
    }

    /** 装備と持ち物のページ: アップグレードの列、作業のオン／オフ、バックパック（ないときは覆う）、持ち物の列。 */
    private void renderGear(GuiGraphics g, int x, int y, int mouseX, int mouseY, int accent) {
        // 区切りの薄い面（アップグレード / バックパック / 持ち物）
        roundRect(g, x + 5, y + 22, 50, 104, mix(accent, PAPER, 0.88F));
        roundRect(g, x + PACK_X_FRAME, y + 22, 116, 86, mix(accent, PAPER, 0.92F));
        roundRect(g, x + AndroidMenu.CARRY_X - 4, y + 22, 24, 104, mix(accent, PAPER, 0.88F));
        slots(g, x, y, accent, 2, 3);
        if (!menu.hasBackpack()) {
            // バックパックがないときは、中のスロットを覆う
            g.fill(x + AndroidMenu.PACK_X - 1, y + AndroidMenu.PACK_Y - 1, x + AndroidMenu.PACK_X + 6 * 18 - 1,
                    y + AndroidMenu.PACK_Y + 4 * 18 - 1, 0xC8E9ECF1);
        }
        // 作業のオン／オフ
        boolean on = menu.value(AndroidMenu.D_WORK) != 0;
        boolean hover = Panel.inside(mouseX, mouseY, x + AndroidMenu.WORK_X, y + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H);
        roundRect(g, x + AndroidMenu.WORK_X, y + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H, BORDER);
        roundRect(g, x + AndroidMenu.WORK_X + 1, y + AndroidMenu.WORK_Y + 1, AndroidMenu.WORK_W - 2, AndroidMenu.WORK_H - 2,
                on ? mix(accent, 0x2A3040, hover ? 0.2F : 0.35F) : hover ? 0xFFF4F6F8 : 0xFFD4D8DF);
    }

    /** ページ切り替えのボタン: 様子のページではバックパックの絵、装備のページではハートの絵。 */
    private void pageButton(GuiGraphics g, int x, int y, boolean hover, int accent) {
        roundRect(g, x, y, PAGE_S, PAGE_S, hover ? mix(accent, 0xFFFFFF, 0.55F) : mix(accent, 0xFFFFFF, 0.35F));
        int c = 0xFF3C4350;
        if (gearPage()) {
            heart(g, x + 2, y + 2, 1);
            return;
        }
        // バックパック: 角の丸い袋と、ふた、留め具
        g.fill(x + 3, y + 3, x + 9, y + 10, c);
        g.fill(x + 4, y + 2, x + 8, y + 3, c);
        g.fill(x + 4, y + 4, x + 8, y + 6, mix(accent, 0xFFFFFF, 0.3F));
        g.fill(x + 5, y + 7, x + 7, y + 8, 0xFFE9ECF1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!names.editing()) {
            AndroidStationScreen.fitCentered(g, font, name(), 24, NAME_Y + 3, imageWidth - 48, 0xFFFFFFFF);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
        if (gearPage()) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.battery"), 6, AndroidMenu.BATTERY_Y - 9, 48, INK);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.backpack"), 6, AndroidMenu.BACKPACK_Y - 9, 48,
                    INK);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.mining"), 6, AndroidMenu.MINING_Y - 9, 48, INK);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.pack"), PACK_X_FRAME, 24, 116, INK);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.carry"), AndroidMenu.CARRY_X - 4, 24, 24, INK);
            if (!menu.hasBackpack()) {
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.pack.none"), AndroidMenu.PACK_X,
                        AndroidMenu.PACK_Y + 32, 6 * 18 - 2, 0xFF8A949E);
            }
            boolean on = menu.value(AndroidMenu.D_WORK) != 0;
            AndroidStationScreen.fitCentered(g, font, Component.translatable(on ? "gui.singulo.android.work.on" : "gui.singulo.android.work.off"),
                    AndroidMenu.WORK_X + 2, AndroidMenu.WORK_Y + 3, AndroidMenu.WORK_W - 4, on ? 0xFFFFFFFF : INK);
            return;
        }
        int hp = menu.value(AndroidMenu.D_HP);
        int max = menu.value(AndroidMenu.D_MAX_HP);
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.android.hp", hp / 10, max / 10), RIGHT_X, 24, 110, INK);
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.android.charge", menu.value(AndroidMenu.D_CHARGE)),
                RIGHT_X + 67, 51, 46, INK);
        AndroidStationScreen.fitCentered(g, font, modeName(), MODE_X + 2, MODE_Y + 3, following() ? MODE_W - 12 : MODE_W - 4, INK);
        g.drawString(font, String.valueOf(menu.value(AndroidMenu.D_ARMOR)), RIGHT_X + 97, 68, INK, false);
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.repair"), AndroidMenu.REPAIR_X - 10,
                AndroidMenu.REPAIR_Y - 12, 36, INK);
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android.plate"), AndroidMenu.PLATE_X - 10,
                AndroidMenu.PLATE_Y - 12, 36, INK);
        AndroidStationScreen.fitCentered(g, font, Component.translatable(speech(hp, max)), PORTRAIT_X + 4, PORTRAIT_Y + PORTRAIT_H - 13,
                PORTRAIT_W - 8, INK);
    }

    /** 吹き出しのひとこと（状態ごとに数通り。5秒ごとに入れ替わる）。 */
    private String speech(int hp, int max) {
        int state = menu.value(AndroidMenu.D_STATE);
        String kind;
        int count;
        switch (state) {
            case RuinAndroid.STATE_LOW_POWER -> {
                kind = "low_power";
                count = 3;
            }
            case RuinAndroid.STATE_RETREAT -> {
                kind = "retreat";
                count = 2;
            }
            case RuinAndroid.STATE_CHARGING, RuinAndroid.STATE_SOLAR, RuinAndroid.STATE_RESTING -> {
                kind = "charging";
                count = 3;
            }
            case RuinAndroid.STATE_CHARGING_FOLLOW -> {
                kind = "charging_follow";
                count = 2;
            }
            default -> {
                if (menu.value(AndroidMenu.D_ALERT) != 0) {
                    kind = "alert";
                    count = 6;
                } else if (hp * 2 < max) {
                    kind = "hurt";
                    count = 3;
                } else {
                    kind = "idle";
                    count = 10;
                }
            }
        }
        long slot = Util.getMillis() / 5000 + menu.containerId * 7L;
        return "gui.singulo.android.say." + kind + "." + Math.floorMod(slot, count);
    }

    /** 角を1ドットずつ落とした四角。 */
    private static void roundRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    /** 小さな星（5×5）。 */
    private static void star(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 3, y + 5, color);
        g.fill(x, y + 2, x + 5, y + 3, color);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
    }

    /** ドット絵のハート（9×8）。fill の割合だけ左から赤く塗る。 */
    private static void heart(GuiGraphics g, int x, int y, float fill) {
        int[] rows = {0b011000110, 0b111101111, 0b111111111, 0b111111111, 0b011111110, 0b001111100, 0b000111000, 0b000010000};
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < 9; c++) {
                if ((rows[r] >> (8 - c) & 1) == 0) {
                    continue;
                }
                boolean on = c < Math.round(fill * 9);
                int color = on ? (r == 1 && (c == 1 || c == 2) ? 0xFFF4C0CC : 0xFFD8506C) : 0xFFD4D0D6;
                g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    /** 盾の形（装甲の値の目印）。 */
    private static void shield(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 9, y + 7, color);
        g.fill(x + 1, y + 7, x + 8, y + 9, color);
        g.fill(x + 3, y + 9, x + 6, y + 11, color);
        g.fill(x + 2, y + 1, x + 7, y + 6, 0xFFFFFFFF);
        g.fill(x + 3, y + 6, x + 6, y + 8, 0xFFFFFFFF);
    }
}
