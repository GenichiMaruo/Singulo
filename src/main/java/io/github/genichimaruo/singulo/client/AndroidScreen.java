package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.AndroidMenu;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.machine.GuiFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 護衛のアンドロイドの画面（アンドロイドのスキン: その子のアクセント色で染める）。ヘッダーに顔と名前（クリックで変えられる）、
 * 右にページのタブ（様子・装備・装飾・記録）。様子のページは、左にアンドロイドの姿（マウスの方を向く）と吹き出しのひとこと、
 * 右にハートの体力、電池、動き方（追従中はクリックで「追従のみ」と「追従・護衛」を切り替える）、装甲、
 * 自己修理キットと装甲板のスロット、小さな解体のボタン（2回押すと解体）。
 * 文字は最小限にし、専用のスロットには入れる物のグレーの見本、説明は重ねると出す。
 */
public class AndroidScreen extends FramedScreen<AndroidMenu> {
    private static final int PORTRAIT_X = 8, PORTRAIT_Y = 22, PORTRAIT_W = 72, PORTRAIT_H = 94;
    private static final int RIGHT_X = 88;
    private static final int DISMANTLE_X = AndroidMenu.DISMANTLE_X, DISMANTLE_Y = AndroidMenu.DISMANTLE_Y;
    private static final int MODE_X = RIGHT_X, MODE_Y = 60, MODE_W = 78, MODE_H = 14;
    private static final int TAB = 12;
    /** バックパックの欄の左端（スロットの1マス手前）。 */
    private static final int PACK_X_FRAME = AndroidMenu.PACK_X - 4;
    /** タンクの目盛り（装備のページ、バックパックの下に2本）。 */
    private static final int TANK_Y = 101, TANK_W = 56, TANK_H = 8, TANK_GAP = 4;
    /** タンクの目盛りの幅と、その右の「捨てる」ボタン（シフト＋クリック）。 */
    private static final int BAR_W = 47, CLEAR_W = 8;
    /** 装備のページ: バックパックと持ち物の間の小さなボタン（並べ替え・近くの箱へ預ける）。 */
    private static final int TOOL_X = 170, SORT_Y = 24, STASH_Y = 36, TOOL_S = 10;

    /** 装備のページのタンクの段（chem: 化学物質のタンク。液体と化学物質の両方なら、2段に細く並べる）。 */
    private record TankRow(boolean chem, int y, int h) {}

    private List<TankRow> tankRows() {
        RuinAndroid a = menu.android();
        if (a == null) {
            return List.of();
        }
        boolean fluid = a.tankFill(0) >= 0, chem = a.chemFill(0) >= 0;
        if (fluid && chem) {
            return List.of(new TankRow(false, TANK_Y - 3, 6), new TankRow(true, TANK_Y + 5, 6));
        }
        return fluid ? List.of(new TankRow(false, TANK_Y, TANK_H)) : chem ? List.of(new TankRow(true, TANK_Y, TANK_H)) : List.of();
    }

    /** 積んでいるタンクのアップグレード（画面の欄の中身。タンクの大きさを決める）。 */
    private ItemStack tankUpgrade() {
        return menu.getSlot(RuinAndroid.SLOT_TANK).getItem();
    }

    /** 記録のページ: 右の欄の位置・幅、1件の高さ、見えている件数、下の件数の段の高さ、いま一番上に見えている記録の番号。 */
    private static final int LOG_X = RIGHT_X, LOG_Y = 24, LOG_W = AndroidMenu.WIDTH - RIGHT_X - 8, LOG_ROW = 19, LOG_ROWS = 4, LOG_FOOTER = 10;
    private int logScroll;

    private final NameEditor names = new NameEditor();
    /** 解体のボタンを1回押した時刻（もう一度押すと解体。3秒で戻る）。 */
    private long dismantleArmed;

    public AndroidScreen(AndroidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AndroidMenu.WIDTH, AndroidMenu.HEIGHT);
        titleLabelX = 21;
        skin = GuiSkin.android(accent());
    }

    private int accent() {
        RuinAndroid a = menu.android();
        return a == null ? 0x78E1FF : a.accent();
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
        return Component.translatable("gui.singulo.android_station.mode." + mode);
    }

    private boolean statusPage() {
        return menu.page() == AndroidMenu.PAGE_STATUS;
    }

    private boolean decorPage() {
        return menu.page() == AndroidMenu.PAGE_DECOR;
    }

    private boolean gearPage() {
        return menu.page() == AndroidMenu.PAGE_GEAR;
    }

    private boolean logPage() {
        return menu.page() == AndroidMenu.PAGE_LOG;
    }

    /** 巡回・警備の記録（新しい順）。 */
    private List<io.github.genichimaruo.singulo.android.AndroidPatrol.Entry> log() {
        RuinAndroid a = menu.android();
        return a == null ? List.of() : a.patrolLog();
    }

    private boolean armed() {
        return Util.getMillis() - dismantleArmed < 3000;
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id)));
    }

    // ------------------------------------------------------------------ ヘッダー（顔・名前・ページのタブ）

    /** 名前を元に戻すボタン（ヘッダー右上。ほかの画面の電源と同じ場所）。 */
    private int[] resetRect() {
        return new int[]{leftPos + GuiFrame.powerX(imageWidth), topPos + GuiFrame.POWER_Y, GuiFrame.POWER_SIZE, GuiFrame.POWER_SIZE};
    }

    /** ページのタブ（ヘッダーの右側に並ぶ記号のボタン）。 */
    private int[] tabRect(int page) {
        int right = named() ? GuiFrame.powerX(imageWidth) - 2 : imageWidth - 4;
        return new int[]{leftPos + right - (AndroidMenu.PAGES - page) * (TAB + 1), topPos + 3, TAB, TAB};
    }

    @Override
    protected int headerReserved() {
        return imageWidth - 6 - (tabRect(0)[0] - leftPos) + 2;
    }

    private boolean overName(double mouseX, double mouseY) {
        return Panel.inside(mouseX, mouseY, leftPos + titleLabelX, topPos + titleLabelY - 2, titleWidth(), 11);
    }

    private static final String[] PAGE_KEYS = {"gui.singulo.android.page.status", "gui.singulo.android.page.gear",
            "gui.singulo.android.page.decor", "gui.singulo.android.page.log"};

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int p = 0; p < AndroidMenu.PAGES; p++) {
            int[] t = tabRect(p);
            boolean on = menu.page() == p;
            boolean hover = Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3]);
            int d = Panel.button(g, skin, t[0], t[1], t[2], t[3], on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            pageIcon(g, t[0], t[1] + d, p, on);
            area(t[0] - leftPos, t[1] - topPos, t[2], t[3], List.of(Component.translatable(PAGE_KEYS[p])));
        }
        if (named()) {
            int[] r = resetRect();
            boolean hover = Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            int d = Panel.button(g, skin, r[0], r[1], r[2], r[3], hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            NameEditor.drawReset(g, r[0], r[1] + d, false, 0, skin.text());
            area(r[0] - leftPos, r[1] - topPos, r[2], r[3], List.of(Component.translatable("gui.singulo.android.rename.reset")));
        }
    }

    /** ページの絵（12×12 の中）: 様子はハート、装備はバックパック、装飾はリボン、記録は日誌。 */
    private void pageIcon(GuiGraphics g, int x, int y, int page, boolean on) {
        int ink = on ? skin.onAccent() : skin.text();
        int tint = on ? skin.onAccent() : GuiSkin.mix(skin.accent(), 0xFF3C4350, 0.4F);
        switch (page) {
            case AndroidMenu.PAGE_STATUS -> heart(g, x + 2, y + 2, 1);
            case AndroidMenu.PAGE_GEAR -> {
                g.fill(x + 3, y + 3, x + 9, y + 10, ink);
                g.fill(x + 4, y + 2, x + 8, y + 3, ink);
                g.fill(x + 4, y + 4, x + 8, y + 6, on ? skin.accent() : GuiSkin.mix(skin.accent(), 0xFFFFFFFF, 0.3F));
                g.fill(x + 5, y + 7, x + 7, y + 8, on ? skin.accent() : 0xFFE9ECF1);
            }
            case AndroidMenu.PAGE_DECOR -> {
                g.fill(x + 2, y + 3, x + 5, y + 7, tint);
                g.fill(x + 7, y + 3, x + 10, y + 7, tint);
                g.fill(x + 5, y + 4, x + 7, y + 6, ink);
                g.fill(x + 4, y + 7, x + 5, y + 10, tint);
                g.fill(x + 7, y + 7, x + 8, y + 10, tint);
            }
            default -> {
                g.fill(x + 3, y + 2, x + 9, y + 10, ink);
                g.fill(x + 4, y + 3, x + 9, y + 9, on ? skin.accent() : 0xFFE9ECF1);
                g.fill(x + 5, y + 4, x + 8, y + 5, tint);
                g.fill(x + 5, y + 6, x + 8, y + 7, tint);
            }
        }
    }

    // ------------------------------------------------------------------ 入力

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (logPage()) {
            logScroll = Mth.clamp(logScroll - (int) Math.signum(scrollY), 0, Math.max(0, log().size() - LOG_ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

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
        int[] r = resetRect();
        if (named() && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        for (int p = 0; p < AndroidMenu.PAGES; p++) {
            int[] t = tabRect(p);
            if (Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3])) {
                menu.setPage(p);
                logScroll = 0;
                dismantleArmed = 0;
                return true;
            }
        }
        if (overName(mouseX, mouseY)) {
            RuinAndroid a = menu.android();
            String current = a != null && a.hasCustomName() ? a.getCustomName().getString() : "";
            EditBox box = names.open(font, leftPos + titleLabelX - 2, topPos + titleLabelY - 3, titleWidth() + 4, current);
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        if (gearPage() && minecraft != null && minecraft.gameMode != null) {
            for (TankRow row : tankRows()) {
                for (int i = 0; i < RuinAndroid.TANKS; i++) {
                    int tx = leftPos + PACK_X_FRAME + i * (TANK_W + TANK_GAP);
                    if (Panel.inside(mouseX, mouseY, tx, topPos + row.y(), BAR_W, row.h()) && !menu.getCarried().isEmpty()) {
                        // バケツ・ケミカルタンクなどで、タンクに入れる・タンクから汲む
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                (row.chem() ? AndroidMenu.BUTTON_CHEM_USE : AndroidMenu.BUTTON_TANK_USE) + i);
                        return true;
                    }
                    if (Panel.inside(mouseX, mouseY, tx + BAR_W + 1, topPos + row.y(), CLEAR_W, row.h())) {
                        if (hasShiftDown()) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                    (row.chem() ? AndroidMenu.BUTTON_CHEM_CLEAR : AndroidMenu.BUTTON_TANK_CLEAR) + i);
                        }
                        return true;
                    }
                }
            }
            if (Panel.inside(mouseX, mouseY, leftPos + TOOL_X, topPos + SORT_Y, TOOL_S, TOOL_S)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_SORT);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + TOOL_X, topPos + STASH_Y, TOOL_S, TOOL_S)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_STASH);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + AndroidMenu.WORK_X, topPos + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_WORK);
                return true;
            }
        }
        if (statusPage() && minecraft != null && minecraft.gameMode != null) {
            if (following() && Panel.inside(mouseX, mouseY, leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_ESCORT);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + DISMANTLE_X, topPos + DISMANTLE_Y, 12, 12)) {
                if (armed()) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidMenu.BUTTON_DISMANTLE);
                } else {
                    dismantleArmed = Util.getMillis();
                }
                return true;
            }
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
        // アイテムの説明が出ている間は、ほかの説明を重ねない
        if (hoveredSlot != null && hoveredSlot.hasItem() || renderStatTooltips(g, mouseX, mouseY) || !menu.getCarried().isEmpty()) {
            return;
        }
        if (!names.editing() && overName(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
            return;
        }
        if (gearPage()) {
            renderTankTooltips(g, mouseX, mouseY);
        }
    }

    private void renderTankTooltips(GuiGraphics g, int mouseX, int mouseY) {
        RuinAndroid android = menu.android();
        for (TankRow row : android == null ? List.<TankRow>of() : tankRows()) {
            for (int i = 0; i < RuinAndroid.TANKS; i++) {
                int tx = leftPos + PACK_X_FRAME + i * (TANK_W + TANK_GAP);
                if (Panel.inside(mouseX, mouseY, tx, topPos + row.y(), BAR_W, row.h())) {
                    List<Component> lines = new ArrayList<>();
                    if (row.chem()) {
                        String id = android.chemId(i);
                        int cap = RuinAndroid.chemCapacity(tankUpgrade());
                        lines.add(id.isEmpty() ? Component.translatable("gui.singulo.android.chem.empty", i + 1)
                                : Component.translatable("gui.singulo.android.chem", i + 1, io.github.genichimaruo.singulo.android.work.Chemicals.name(id),
                                        Math.round(android.chemFill(i) * cap), cap));
                    } else {
                        net.minecraft.world.level.material.Fluid fluid = android.tankFluid(i);
                        int cap = RuinAndroid.fluidCapacity(tankUpgrade());
                        lines.add(fluid == null ? Component.translatable("gui.singulo.android.tank.empty", i + 1)
                                : Component.translatable("gui.singulo.android.tank", i + 1,
                                        new net.neoforged.neoforge.fluids.FluidStack(fluid, 1).getHoverName(), Math.round(android.tankFill(i) * cap), cap));
                    }
                    lines.add(Component.translatable(menu.working() ? "gui.singulo.android.tank.locked"
                            : row.chem() ? "gui.singulo.android.chem.hint" : "gui.singulo.android.tank.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                    return;
                }
                if (Panel.inside(mouseX, mouseY, tx + BAR_W + 1, topPos + row.y(), CLEAR_W, row.h())) {
                    g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android.tank.clear"), 160), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 描画

    /** 区切りの薄い面（色を少しだけ差した、わずかに凹んだ板）。 */
    private void section(GuiGraphics g, int x, int y, int w, int h) {
        Panel.inset(g, skin, leftPos + x, topPos + y, w, h, GuiSkin.mix(skin.face(), skin.accent(), 0.08F));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        skin = GuiSkin.android(accent());
        clearStats();
        drawFrame(g, mouseX, mouseY);
        AndroidFace.draw(g, leftPos + 7, topPos + 4, accent());
        drawTabs(g, mouseX, mouseY);
        if (gearPage()) {
            renderGear(g, mouseX, mouseY);
            return;
        }
        portrait(g, mouseX, mouseY);
        if (logPage()) {
            section(g, LOG_X - 4, LOG_Y - 4, LOG_W + 4, LOG_ROW * LOG_ROWS + 4 + LOG_FOOTER);
            for (int i = 1; i < LOG_ROWS; i++) {
                g.fill(leftPos + LOG_X, topPos + LOG_Y + i * LOG_ROW - 2, leftPos + LOG_X + LOG_W - 6, topPos + LOG_Y + i * LOG_ROW - 1, skin.seam());
            }
            return;
        }
        if (decorPage()) {
            section(g, AndroidMenu.DECOR_X - 6, 24, 112, 90);
            // 染料で染められることは、欄の右下の小さな虹色のしずくに重ねると出す（アイテムの説明と重ならないように）
            int dx = AndroidMenu.DECOR_X + 94;
            int dy = 104;
            Panel.icon(g, leftPos + dx, topPos + dy, Panel.Icon.DROP, 0xFFE06AC4);
            g.fill(leftPos + dx + 1, topPos + dy + 4, leftPos + dx + 6, topPos + dy + 6, 0xFF6AB8E8);
            g.fill(leftPos + dx + 2, topPos + dy + 6, leftPos + dx + 5, topPos + dy + 7, 0xFF7CD88A);
            area(dx - 1, dy - 1, 9, 10, List.of(Component.translatable("gui.singulo.android.decor.dye")));
            String[] ghosts = {"android_ribbon", "android_cape", "android_bouquet"};
            for (int i = 0; i < RuinAndroid.DECOR_SLOTS; i++) {
                Slot s = menu.getSlot(RuinAndroid.SLOT_DECOR + i);
                ghostSlot(g, leftPos + s.x, topPos + s.y, s, item(ghosts[i]));
                if (!s.hasItem()) {
                    area(s.x, s.y, 16, 16, List.of(Component.translatable("gui.singulo.android.decor." + i),
                            Component.translatable("gui.singulo.android.decor." + i + ".hint").withStyle(st -> st.withColor(0xFF8A949E))));
                }
            }
            return;
        }
        renderStatus(g, mouseX, mouseY);
    }

    /** 様子のページ: 体力・電池・動き方・装甲（数は重ねると出る）と、修理・装甲板のスロット、解体のボタン。 */
    private void renderStatus(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int hp = menu.value(AndroidMenu.D_HP);
        int max = Math.max(1, menu.value(AndroidMenu.D_MAX_HP));
        float hearts = 10F * hp / max;
        for (int i = 0; i < 10; i++) {
            heart(g, x + RIGHT_X + i * 11, y + 26, Mth.clamp(hearts - i, 0, 1));
        }
        area(RIGHT_X, 25, 110, 10, List.of(Component.translatable("gui.singulo.android.hp", hp / 10, max / 10)));
        // 充電（電池）
        int charge = Mth.clamp(menu.value(AndroidMenu.D_CHARGE), 0, 100);
        battery(g, x + RIGHT_X, y + 41, charge);
        area(RIGHT_X, 41, 64, 12, List.of(Component.translatable("gui.singulo.android.charge", charge)));
        // 動き方の札（追従中はボタン）と、装甲（盾と数）
        boolean modeHover = following() && Panel.inside(mouseX, mouseY, x + MODE_X, y + MODE_Y, MODE_W, MODE_H);
        int d = following() ? Panel.button(g, skin, x + MODE_X, y + MODE_Y, MODE_W, MODE_H, modeHover ? Panel.Press.HOVER : Panel.Press.NORMAL) : 0;
        if (!following()) {
            section(g, MODE_X, MODE_Y, MODE_W, MODE_H);
        }
        AndroidStationScreen.fitCentered(g, font, modeName(), x + MODE_X + 2, y + MODE_Y + 3 + d, following() ? MODE_W - 12 : MODE_W - 4, skin.text());
        if (following()) {
            // 切り替えられる印（右端の小さな両向きの矢印）
            int ax = x + MODE_X + MODE_W - 8;
            int ay = y + MODE_Y + 4 + d;
            int ac = GuiSkin.mix(skin.accent(), 0xFF2A3040, 0.5F);
            g.fill(ax, ay + 1, ax + 5, ay + 2, ac);
            g.fill(ax + 3, ay, ax + 4, ay + 3, ac);
            g.fill(ax, ay + 5, ax + 5, ay + 6, ac);
            g.fill(ax + 1, ay + 4, ax + 2, ay + 7, ac);
            area(MODE_X, MODE_Y, MODE_W, MODE_H, List.of(Component.translatable("gui.singulo.android.follow.hint")));
        }
        shield(g, x + RIGHT_X + 84, y + MODE_Y + 2, skin.outline());
        g.drawString(font, String.valueOf(menu.value(AndroidMenu.D_ARMOR)), x + RIGHT_X + 97, y + MODE_Y + 3, skin.text(), false);
        area(RIGHT_X + 82, MODE_Y, 28, MODE_H, List.of(Component.translatable("gui.singulo.android.armor")));
        // 修理キット・装甲板（空のときは見本）
        Slot repair = menu.getSlot(RuinAndroid.SLOT_REPAIR);
        ghostSlot(g, x + repair.x, y + repair.y, repair, item("android_repair_kit"));
        Slot plate = menu.getSlot(RuinAndroid.SLOT_PLATE);
        ghostSlot(g, x + plate.x, y + plate.y, plate, item("android_armor_plate"));
        if (!repair.hasItem()) {
            area(repair.x, repair.y, 16, 16, List.of(Component.translatable("gui.singulo.android.repair")));
        }
        if (!plate.hasItem()) {
            area(plate.x, plate.y, 16, 16, List.of(Component.translatable("gui.singulo.android.plate")));
        }
        // 解体のボタン（小さく、目立たない。押すと赤くなり、もう一度で解体）
        boolean hover = Panel.inside(mouseX, mouseY, x + DISMANTLE_X, y + DISMANTLE_Y, 12, 12);
        int dd = Panel.button(g, skin, x + DISMANTLE_X, y + DISMANTLE_Y, 12, 12, armed() ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        int wx = x + DISMANTLE_X + 3;
        int wy = y + DISMANTLE_Y + 3 + dd;
        int wc = armed() ? GuiSkin.BAD : skin.dim();
        g.fill(wx, wy, wx + 2, wy + 2, wc);
        g.fill(wx + 4, wy, wx + 6, wy + 2, wc);
        g.fill(wx + 1, wy + 1, wx + 5, wy + 3, wc);
        g.fill(wx + 2, wy + 3, wx + 4, wy + 6, wc);
        area(DISMANTLE_X, DISMANTLE_Y, 12, 12, List.of(Component.translatable(armed() ? "gui.singulo.android.dismantle.confirm"
                : "gui.singulo.android.dismantle")));
    }

    /** 装備と持ち物のページ: アップグレードの列、作業のオン／オフ、バックパック（ないときは覆う）、持ち物の列。 */
    private void renderGear(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        section(g, 5, 21, 46, 70);
        section(g, PACK_X_FRAME, 21, 116, 78);
        section(g, AndroidMenu.CARRY_X - 4, 21, 24, 96);
        area(AndroidMenu.CARRY_X - 4, 21, 24, 2, List.of(Component.translatable("gui.singulo.android.carry")));
        // 専用のスロット（空のときは見本）
        int[] dedicated = {RuinAndroid.SLOT_BATTERY, RuinAndroid.SLOT_BACKPACK, RuinAndroid.SLOT_MINING, RuinAndroid.SLOT_FELLING, RuinAndroid.SLOT_TANK};
        String[] ghostIds = {"android_battery_upgrade", "android_backpack_upgrade", "android_mining_upgrade", "android_felling_upgrade",
                "android_tank_upgrade"};
        String[] names = {"gui.singulo.android.battery", "gui.singulo.android.storage", "gui.singulo.android.tools", "gui.singulo.android.tools",
                "gui.singulo.android.storage"};
        for (int i = 0; i < dedicated.length; i++) {
            Slot s = menu.getSlot(dedicated[i]);
            ghostSlot(g, x + s.x, y + s.y, s, item(ghostIds[i]));
            if (!s.hasItem()) {
                area(s.x, s.y, 16, 16, List.of(Component.translatable(names[i])));
            }
        }
        for (Slot s : menu.slots) {
            if (s.isActive() && (s.index >= RuinAndroid.CARRY_START && s.index < RuinAndroid.CARRY_START + RuinAndroid.CARRY_SLOTS
                    || s.index >= RuinAndroid.PACK_START && s.index < RuinAndroid.PACK_START + RuinAndroid.PACK_SLOTS)) {
                Panel.slot(g, skin, x + s.x, y + s.y);
            }
        }
        if (menu.builtInPack()) {
            // バックパックを内蔵している型: アップグレードの枠をアクセントの札で覆う（アップグレードは入らない）
            int sx = x + AndroidMenu.BACKPACK_X;
            int sy = y + AndroidMenu.BACKPACK_Y;
            Panel.button(g, skin, sx - 1, sy - 1, 18, 18, Panel.Press.ON);
            pageIcon(g, sx + 2, sy + 2, AndroidMenu.PAGE_GEAR, true);
            area(AndroidMenu.BACKPACK_X, AndroidMenu.BACKPACK_Y, 16, 16, List.of(Component.translatable("gui.singulo.android.pack.builtin")));
        }
        int openRows = menu.packSlots() / 6;
        if (openRows < 4) {
            // バックパックがない段（ないか、ティアが低い）は、中のスロットを覆う（旧版で入れた物は見えて、取り出せる）
            g.fill(x + AndroidMenu.PACK_X - 1, y + AndroidMenu.PACK_Y + openRows * 18 - 1, x + AndroidMenu.PACK_X + 6 * 18 - 1,
                    y + AndroidMenu.PACK_Y + 4 * 18 - 1, 0xC0000000 | (skin.face() & 0xFFFFFF));
        }
        if (!menu.hasBackpack()) {
            pageIcon(g, x + AndroidMenu.PACK_X + 48, y + AndroidMenu.PACK_Y + 30, AndroidMenu.PAGE_GEAR, false);
            area(AndroidMenu.PACK_X, AndroidMenu.PACK_Y, 6 * 18, 4 * 18, List.of(Component.translatable("gui.singulo.android.pack.none")));
        }
        drawTanks(g, mouseX, mouseY);
        // 並べ替え（3本の横線）と、近くの箱へ預ける（箱と下向きの矢印）
        for (int i = 0; i < 2; i++) {
            int bx = x + TOOL_X;
            int by = y + (i == 0 ? SORT_Y : STASH_Y);
            boolean over = Panel.inside(mouseX, mouseY, bx, by, TOOL_S, TOOL_S);
            int d = Panel.button(g, skin, bx, by, TOOL_S, TOOL_S, menu.working() ? Panel.Press.DISABLED : over ? Panel.Press.HOVER : Panel.Press.NORMAL);
            int c = menu.working() ? skin.dim() : skin.text();
            by += d;
            if (i == 0) {
                g.fill(bx + 2, by + 2, bx + 8, by + 3, c);
                g.fill(bx + 2, by + 4, bx + 7, by + 5, c);
                g.fill(bx + 2, by + 6, bx + 6, by + 7, c);
            } else {
                g.fill(bx + 4, by + 2, bx + 6, by + 5, c);
                g.fill(bx + 3, by + 4, bx + 7, by + 5, c);
                g.fill(bx + 2, by + 6, bx + 8, by + 8, c);
            }
            area(TOOL_X, i == 0 ? SORT_Y : STASH_Y, TOOL_S, TOOL_S, List.of(Component.translatable(menu.working() ? "gui.singulo.android.stash.working"
                    : i == 0 ? "gui.singulo.android.sort.hint" : "gui.singulo.android.stash.hint")));
        }
        // 作業のオン／オフ（つるはしの記号のトグル）
        boolean on = menu.value(AndroidMenu.D_WORK) != 0;
        boolean hover = Panel.inside(mouseX, mouseY, x + AndroidMenu.WORK_X, y + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H);
        int d = Panel.button(g, skin, x + AndroidMenu.WORK_X, y + AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H,
                on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        pickaxe(g, x + AndroidMenu.WORK_X + AndroidMenu.WORK_W / 2 - 4, y + AndroidMenu.WORK_Y + 3 + d, on ? skin.onAccent() : skin.text());
        area(AndroidMenu.WORK_X, AndroidMenu.WORK_Y, AndroidMenu.WORK_W, AndroidMenu.WORK_H, List.of(
                Component.translatable(on ? "gui.singulo.android.work.on" : "gui.singulo.android.work.off"),
                Component.translatable("gui.singulo.android.work.hint").withStyle(st -> st.withColor(0xFF8A949E))));
    }

    /** タンク: 中身そのものの絵で、入っている割合まで（流れるアニメーションも動く）。液体と化学物質（Mekanism）の段。 */
    private void drawTanks(GuiGraphics g, int mouseX, int mouseY) {
        RuinAndroid android = menu.android();
        for (TankRow row : android == null ? List.<TankRow>of() : tankRows()) {
            int ty = topPos + row.y(), th = row.h();
            for (int i = 0; i < RuinAndroid.TANKS; i++) {
                int tx = leftPos + PACK_X_FRAME + i * (TANK_W + TANK_GAP);
                // 捨てるボタン（小さな ×。シフト＋クリック）
                int cx = tx + BAR_W + 1;
                boolean ch = Panel.inside(mouseX, mouseY, cx, ty, CLEAR_W, th);
                Panel.button(g, skin, cx, ty - 1, CLEAR_W, th + 2, ch && hasShiftDown() ? Panel.Press.ON : ch ? Panel.Press.HOVER : Panel.Press.NORMAL);
                int arm = Math.min(4, th - 2);
                int xc = ch && hasShiftDown() ? GuiSkin.BAD : skin.dim();
                for (int k = 0; k < arm; k++) {
                    int ox = (4 - arm) / 2;
                    g.fill(cx + 2 + ox + k, ty + 1 + k, cx + 3 + ox + k, ty + 2 + k, xc);
                    g.fill(cx + 1 + ox + arm - k, ty + 1 + k, cx + 2 + ox + arm - k, ty + 2 + k, xc);
                }
                Panel.well(g, skin, tx, ty, BAR_W, th);
                int filled = 0;
                if (row.chem()) {
                    String id = android.chemId(i);
                    float fill = android.chemFill(i);
                    if (!id.isEmpty() && fill > 0) {
                        filled = Math.max(1, Math.round(BAR_W * Math.min(1, fill)));
                        FluidDraw.fillChemical(g, id, tx, ty, filled, th);
                    }
                } else {
                    net.minecraft.world.level.material.Fluid fluid = android.tankFluid(i);
                    float fill = android.tankFill(i);
                    if (fluid != null && fill > 0) {
                        filled = Math.max(1, Math.round(BAR_W * Math.min(1, fill)));
                        FluidDraw.fill(g, fluid, tx, ty, filled, th);
                    }
                }
                Panel.glass(g, tx, ty, filled, th);
                for (int m = 1; m < 4; m++) {
                    g.fill(tx + BAR_W * m / 4, ty, tx + BAR_W * m / 4 + 1, ty + 2, 0x80FFFFFF);      // 目盛り
                }
            }
        }
    }

    /** 姿の枠: アンドロイドの姿（マウスの方を向く）と、下の吹き出し。 */
    private void portrait(GuiGraphics g, int mouseX, int mouseY) {
        int px = leftPos + PORTRAIT_X;
        int py = topPos + PORTRAIT_Y;
        Panel.inset(g, skin, px - 1, py - 1, PORTRAIT_W + 2, PORTRAIT_H + 2, skin.face());
        g.fillGradient(px, py, px + PORTRAIT_W, py + PORTRAIT_H, GuiSkin.mix(skin.accent(), 0xFFF3F5F7, 0.88F),
                GuiSkin.mix(skin.accent(), 0xFFF3F5F7, 0.7F));
        for (int i = 0; i < 5; i++) {
            star(g, px + 6 + (i * 23) % (PORTRAIT_W - 10), py + 8 + (i * 37) % (PORTRAIT_H - 34), GuiSkin.mix(skin.accent(), 0xFFF3F5F7, 0.5F));
        }
        RuinAndroid android = menu.android();
        if (android != null) {
            AndroidRenderer.inGui = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 2, py + 2, px + PORTRAIT_W - 2, py + PORTRAIT_H - 18, 32, 0.0625F,
                        mouseX, mouseY, android);
            } finally {
                AndroidRenderer.inGui = false;
            }
        }
        // 吹き出し（姿の下）
        g.fill(px + 3, py + PORTRAIT_H - 16, px + PORTRAIT_W - 3, py + PORTRAIT_H - 2, 0xFFFFFFFF);
        g.fill(px + 2, py + PORTRAIT_H - 15, px + PORTRAIT_W - 2, py + PORTRAIT_H - 3, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!names.editing()) {
            AndroidStationScreen.fit(g, font, name(), titleLabelX, titleLabelY, titleWidth(), skin.text());
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
        if (gearPage()) {
            return;
        }
        if (logPage()) {
            var log = log();
            if (log.isEmpty()) {
                for (int i = 0; i < 2; i++) {
                    AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.android.log.empty." + i), LOG_X, LOG_Y + 4 + i * 11,
                            LOG_W - 8, skin.dim());
                }
            }
            logScroll = Mth.clamp(logScroll, 0, Math.max(0, log.size() - LOG_ROWS));
            for (int i = 0; i < LOG_ROWS && i + logScroll < log.size(); i++) {
                var e = log.get(i + logScroll);
                int ry = LOG_Y + i * LOG_ROW;
                AndroidStationScreen.fit(g, font, e.when().copy().append("  " + e.pos().getX() + ", " + e.pos().getY() + ", " + e.pos().getZ()),
                        LOG_X, ry, LOG_W - 8, skin.dim());
                AndroidStationScreen.fit(g, font, e.describe(), LOG_X, ry + 9, LOG_W - 8, e.kind() == 0 ? skin.text() : 0xFFB04040);
            }
            if (log.size() > LOG_ROWS) {
                // 件数: 欄の中の下の段に、右寄せで
                Component count = Component.literal((logScroll + 1) + "-" + Math.min(log.size(), logScroll + LOG_ROWS) + " / " + log.size());
                int cw = Math.min(font.width(count), 40);
                AndroidStationScreen.fit(g, font, count, LOG_X + LOG_W - 6 - cw, LOG_Y + LOG_ROW * LOG_ROWS, 40, skin.dim());
            }
            speech(g, Component.translatable("gui.singulo.android.log.speech"), skin.text());
            return;
        }
        if (decorPage()) {
            speech(g, Component.translatable("gui.singulo.android.decor.speech"), skin.text());
            return;
        }
        int hp = menu.value(AndroidMenu.D_HP);
        int max = menu.value(AndroidMenu.D_MAX_HP);
        int stall = menu.value(AndroidMenu.D_STALL);
        if (stall > RuinAndroid.STALL_NONE && stall < RuinAndroid.STALL_KEYS.length) {
            // 止まっている理由があれば、吹き出しのひとことの代わりに出す（赤い字）
            speech(g, Component.translatable("gui.singulo.android.stall." + RuinAndroid.STALL_KEYS[stall]), 0xFFC0392B);
        } else {
            speech(g, Component.translatable(speechKey(hp, max)), skin.text());
        }
    }

    private void speech(GuiGraphics g, Component text, int color) {
        AndroidStationScreen.fitCentered(g, font, text, PORTRAIT_X + 4, PORTRAIT_Y + PORTRAIT_H - 13, PORTRAIT_W - 8, color);
    }

    /** 吹き出しのひとこと（状態ごとに数通り。5秒ごとに入れ替わる）。 */
    private String speechKey(int hp, int max) {
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

    /**
     * 電池（60×12 と右の太い＋極）。濃い紺の縁と暗い窓に、水色から青の丸いブロック5つで残りを出す
     * （最後のブロックは途中まで。2割を切るとピンク）。角を丸め、左上にきらめき。
     */
    private void battery(GuiGraphics g, int x, int y, int charge) {
        int edge = 0xFF2A3F66;
        // 縁（角を1px落とす）と、太めの＋極
        g.fill(x + 1, y, x + 59, y + 12, edge);
        g.fill(x, y + 1, x + 60, y + 11, edge);
        g.fill(x + 60, y + 3, x + 63, y + 9, edge);
        g.fill(x + 63, y + 4, x + 64, y + 8, edge);
        g.fill(x + 61, y + 4, x + 62, y + 5, 0xFF8FB4E8);
        // 縁の上の光（少し浮いて見える）
        g.fill(x + 2, y + 1, x + 58, y + 2, 0xFF4A6496);
        // 暗い窓
        g.fill(x + 2, y + 2, x + 58, y + 10, 0xFF16223A);
        boolean low = charge < 20;
        int top = low ? 0xFFFFA8C0 : 0xFFA8E2FF;
        int bottom = low ? 0xFFF0607E : 0xFF3A8FE0;
        for (int i = 0; i < 5; i++) {
            float level = charge / 20F - i;
            if (level <= 0) {
                break;
            }
            int cx = x + 3 + i * 11;
            int w = Math.max(2, Math.round(10 * Math.min(1, level)));
            g.fillGradient(cx, y + 3, cx + w, y + 9, top, bottom);
            g.fill(cx + 1, y + 4, cx + Math.max(2, w - 1), y + 5, 0xB0FFFFFF);
            // 丸いブロック（角を窓の色で落とす）
            g.fill(cx, y + 3, cx + 1, y + 4, 0xFF16223A);
            g.fill(cx + w - 1, y + 3, cx + w, y + 4, 0xFF16223A);
            g.fill(cx, y + 8, cx + 1, y + 9, 0xFF16223A);
            g.fill(cx + w - 1, y + 8, cx + w, y + 9, 0xFF16223A);
        }
        // きらめき
        g.fill(x + 3, y - 1, x + 4, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y, x + 5, y + 1, 0xFFFFFFFF);
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

    /** つるはし（作業のトグル、9×8）。 */
    private static void pickaxe(GuiGraphics g, int x, int y, int color) {
        Panel.sprite(g, x, y, new String[]{".xxxxxxx.", "x...x...x", "....x....", "....x....", "....x....", "....x....", "....x...."}, color);
    }
}
