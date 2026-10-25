package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.AndroidStationMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;
import net.minecraft.world.inventory.Slot;

/**
 * アンドロイド組立ステーションの画面（横長。アンドロイドのスキン: 設計メモリの型の色で染める）。左に設計メモリとキットなどの
 * スロット（空のときは入れる物の見本）、中ほどにアンドロイドの名前・体力・充電、その下に動き方（迎撃・徘徊・追従・巡回）と、
 * 戻ってくる体力の割合、呼び戻すボタン、追従中に戦うか（ついていくだけ・護衛する）、許可証の登録の様子。右端は蓄電量。
 * 状態はヘッダーの記号、経路のページは右レールの設定の段で切り替える。長い文字は枠に収まるよう縮める。
 */
public class AndroidStationScreen extends FramedScreen<AndroidStationMenu> {
    private static final int INFO_X = 52;
    private static final int INFO_W = 162;
    private static final int BAR_LABEL_W = 30;
    private static final int MODE_Y = 62;
    /** 動き方のボタン（迎撃・徘徊・追従・巡回）。 */
    private static final int MODES = 4;
    private static final int MODE_W = (INFO_W - 3 * (MODES - 1)) / MODES;
    /** 巡回の設定のボタン（周回/往復・夜だけ・立ち止まる時間）。追従の「追従する人」の段に並べる。 */
    private static final int PATROL_W = (INFO_W - 6) / 3;
    private static final int RETREAT_Y = 82;
    private static final int MINUS_X = INFO_X;
    private static final int PLUS_X = INFO_X + 92;
    private static final int RECALL_X = INFO_X + 110;
    private static final int RECALL_W = 52;
    private static final int BAR_X = 228;
    /** 2体を受け持つときの、見るアンドロイドを選ぶ札（名前の左）。 */
    private static final int UNIT_W = 12, UNIT_GAP = 2;
    private static final int ESCORT_Y = 102;
    private static final int ESCORT_W = (INFO_W - 3) / 2;
    private static final int LEADER_Y = 123;
    private static final int NAME_Y = 22;
    /** 体力と充電のバー（左に記号）。 */
    private static final int HP_Y = 37, CHARGE_Y = 49;
    /** 右端の蓄電のゲージ。 */
    private static final int ENERGY_Y = 22, ENERGY_H = 100;

    private final NameEditor names = new NameEditor();

    public AndroidStationScreen(AndroidStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AndroidStationMenu.WIDTH, AndroidStationMenu.HEIGHT);
        skin = GuiSkin.android(accent());
    }

    /** 設計メモリの型の色（なければ光刃型の色）。 */
    private int accent() {
        int kind = menu.value(AndroidStationBlockEntity.D_KIND);
        if (kind < 0 || kind >= AndroidKind.values().length) {
            return io.github.genichimaruo.singulo.android.BladeAndroid.ACCENT;
        }
        return switch (AndroidKind.values()[kind]) {
            case BLADE -> io.github.genichimaruo.singulo.android.BladeAndroid.ACCENT;
            case WHIP -> io.github.genichimaruo.singulo.android.WhipAndroid.ACCENT;
            case SABER -> io.github.genichimaruo.singulo.android.SaberAndroid.ACCENT;
            case AXE -> io.github.genichimaruo.singulo.android.AxeAndroid.ACCENT;
            case BIT -> io.github.genichimaruo.singulo.android.BitAndroid.ACCENT;
            case GUNNER -> io.github.genichimaruo.singulo.android.GunnerAndroid.ACCENT;
        };
    }

    // ------------------------------------------------------------------ ヘッダー（状態）と右レール（経路のページ）

    @Override
    protected Panel.Status status() {
        int status = menu.value(AndroidStationBlockEntity.D_STATUS);
        return switch (status) {
            case AndroidStationBlockEntity.S_ACTIVE, AndroidStationBlockEntity.S_PRINTING, AndroidStationBlockEntity.S_ORPHAN -> Panel.Status.RUN;
            case AndroidStationBlockEntity.S_CHARGING, AndroidStationBlockEntity.S_RETURNING, AndroidStationBlockEntity.S_WAITING,
                    AndroidStationBlockEntity.S_AWAY -> Panel.Status.IDLE;
            default -> Panel.Status.WARN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case RUN -> GuiSkin.OK;
            case IDLE -> GuiSkin.WARN;
            default -> GuiSkin.BAD;
        };
    }

    @Override
    protected Component statusText() {
        int status = menu.value(AndroidStationBlockEntity.D_STATUS);
        return status == AndroidStationBlockEntity.S_WAITING
                ? Component.translatable("gui.singulo.android_station.status.waiting", (menu.value(AndroidStationBlockEntity.D_DELAY) + 19) / 20)
                : Component.translatable("gui.singulo.android_station.status." + status);
    }

    @Override
    protected boolean railUsed(int row) {
        return row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_SETTINGS;
    }

    @Override
    protected boolean railConnected(int row) {
        return menu.ruleView();
    }

    @Override
    protected void drawRail(GuiGraphics g, int row, int x, int y, boolean hover) {
        Panel.gearIcon(g, x + 2, y + 2, menu.ruleView() ? skin.accent() : skin.text());
    }

    @Override
    protected boolean clickRail(int row, int button) {
        menu.setRuleView(!menu.ruleView());          // 様子 ⇔ 経路
        return true;
    }

    @Override
    protected Component railTooltip(int row) {
        return Component.translatable(menu.ruleView() ? "gui.singulo.android_station.rules.back" : "gui.singulo.android_station.rules");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (menu.ruleView()) {
            if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index >= AndroidStationBlockEntity.SLOT_RULES
                    && hoveredSlot.index < AndroidStationBlockEntity.SLOTS) {
                int col = (hoveredSlot.index - AndroidStationBlockEntity.SLOT_RULES) % io.github.genichimaruo.singulo.android.work.PathRule.COLS;
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.route." + WorkConsoleScreen.ROUTE_COL_KEYS[col] + ".hint"), 180),
                        mouseX, mouseY);
            }
            return;                                      // 経路のページでは、様子・動き方の段は出していない
        }
        if (Panel.inside(mouseX, mouseY, leftPos + BAR_X, topPos + ENERGY_Y, 10, ENERGY_H)) {
            g.renderComponentTooltip(font, Panel.energyLines(menu.value(AndroidStationBlockEntity.D_ENERGY),
                    menu.value(AndroidStationBlockEntity.D_CAPACITY)), mouseX, mouseY);
        }
        for (int i = 0; i < MODES; i++) {
            if (Panel.inside(mouseX, mouseY, modeX(i), topPos + MODE_Y, modeW(i), 16)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.mode." + i + ".hint"), 180),
                        mouseX, mouseY);
            }
        }
        if (menu.patrolling()) {
            for (int i = 0; i < 3; i++) {
                if (Panel.inside(mouseX, mouseY, patrolX(i), topPos + LEADER_Y, PATROL_W, 16)) {
                    g.renderTooltip(font, font.split(Component.translatable(PATROL_KEYS[i] + ".hint"), 180), mouseX, mouseY);
                }
            }
            if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index >= AndroidStationBlockEntity.SLOT_ROUTE
                    && hoveredSlot.index < AndroidStationBlockEntity.SLOT_ROUTE + AndroidStationBlockEntity.ROUTE_SLOTS) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.route.hint"), 180), mouseX, mouseY);
            }
        }
        if (named() && Panel.inside(mouseX, mouseY, leftPos + INFO_X + INFO_W - 11, topPos + NAME_Y - 1, 11, 11)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && Panel.inside(mouseX, mouseY, leftPos + nameX(), topPos + NAME_Y, nameW() - 12, 11)
                && menu.value(AndroidStationBlockEntity.D_KIND) >= 0) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + MINUS_X + 16, topPos + RETREAT_Y, PLUS_X - MINUS_X - 18, 16)) {
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>(
                    font.split(Component.translatable("gui.singulo.android_station.retreat.hint"), 180));
            if (menu.value(AndroidStationBlockEntity.D_KIND) >= 0) {
                // 目安: 見ているアンドロイドの今の体力と充電（戻る体力と比べられるように）
                lines.addAll(font.split(Component.translatable("gui.singulo.android_station.retreat.now", menu.value(AndroidStationBlockEntity.D_HP),
                        menu.value(AndroidStationBlockEntity.D_CHARGE)).withStyle(net.minecraft.ChatFormatting.AQUA), 180));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
        if (!orphanWaiting() && Panel.inside(mouseX, mouseY, leftPos + RECALL_X, topPos + RETREAT_Y, RECALL_W, 16)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.recall.hint", AndroidStationBlockEntity.RECALL_ALL_RANGE), 180),
                    mouseX, mouseY);
        }
        if (menu.patrolling()) {
            return;                                  // 追従の設定（追従中に戦うか・追従する人）の段は、巡回の設定に置き換わっている
        }
        for (int i = 0; i < 2; i++) {
            if (Panel.inside(mouseX, mouseY, escortX(i), topPos + ESCORT_Y, escortW(i), 16)) {
                g.renderTooltip(font, font.split(Component.translatable(ESCORT_KEYS[i] + ".hint"), 180), mouseX, mouseY);
            }
        }
        if (Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + LEADER_Y, INFO_W, 16)) {
            int members = permitMembers();
            if (members > 0) {
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
                lines.addAll(font.split(Component.translatable("gui.singulo.android_station.permit.members", members), 180));
                // 登録した人の名前（許可証のスロットを見に行かなくても、だれが対象外かが分かるように）
                var list = io.github.genichimaruo.singulo.item.ShieldPermitItem.members(menu.getSlot(AndroidStationBlockEntity.SLOT_PERMIT).getItem());
                for (int i = 0; i < Math.min(8, list.size()); i++) {
                    lines.add(Component.literal(" ・" + list.get(i).name()).withStyle(net.minecraft.ChatFormatting.AQUA).getVisualOrderText());
                }
                if (list.size() > 8) {
                    lines.add(Component.translatable("item.singulo.shield_permit.more", list.size() - 8)
                            .withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
                }
                lines.addAll(font.split(Component.translatable("gui.singulo.android_station.permit.others").withStyle(net.minecraft.ChatFormatting.GOLD), 180));
                lines.addAll(font.split(Component.translatable("gui.singulo.android_station.leader.hint"), 180));
                g.renderTooltip(font, lines, mouseX, mouseY);
            } else {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.permit.hint"), 180), mouseX, mouseY);
            }
        }
    }

    private int modeX(int i) {
        return leftPos + INFO_X + i * (MODE_W + 3);
    }

    /** 割り切れずに余った分は最後のボタンが受け持つ（右端を情報欄の右端に揃える）。 */
    private static int modeW(int i) {
        return i == MODES - 1 ? INFO_W - i * (MODE_W + 3) : MODE_W;
    }

    /** 巡回の設定のボタンの文字の鍵（周回/往復・夜だけ・立ち止まる時間）。 */
    private static final String[] PATROL_KEYS = {"gui.singulo.android_station.patrol.order", "gui.singulo.android_station.patrol.night",
            "gui.singulo.android_station.patrol.dwell"};
    private static final int[] PATROL_BUTTONS = {AndroidStationBlockEntity.BUTTON_ROUTE_ORDER, AndroidStationBlockEntity.BUTTON_NIGHT,
            AndroidStationBlockEntity.BUTTON_DWELL};

    private int patrolX(int i) {
        return leftPos + INFO_X + i * (PATROL_W + 3);
    }

    /** 巡回の設定のボタンの文字。 */
    private Component patrolLabel(int i) {
        int flags = menu.value(AndroidStationBlockEntity.D_PATROL);
        return switch (i) {
            case 0 -> Component.translatable(PATROL_KEYS[0] + ((flags & AndroidStationBlockEntity.PATROL_BACK_AND_FORTH) != 0 ? ".back_and_forth" : ".loop"));
            case 1 -> Component.translatable(PATROL_KEYS[1] + ((flags & AndroidStationBlockEntity.PATROL_NIGHT) != 0 ? ".on" : ".off"));
            default -> Component.translatable(PATROL_KEYS[2], menu.value(AndroidStationBlockEntity.D_DWELL));
        };
    }

    /** 追従中に戦うかのボタン（0: ついていくだけ、1: 護衛する）。 */
    private static final String[] ESCORT_KEYS = {"gui.singulo.android_station.follow_only", "gui.singulo.android_station.escort"};
    private static final int[] ESCORT_BUTTONS = {AndroidStationBlockEntity.BUTTON_FOLLOW_ONLY, AndroidStationBlockEntity.BUTTON_ESCORT};

    private int escortX(int i) {
        return leftPos + INFO_X + i * (ESCORT_W + 3);
    }

    private static int escortW(int i) {
        return i == 1 ? INFO_W - (ESCORT_W + 3) : ESCORT_W;
    }

    /** 許可証に登録された人数。 */
    private int permitMembers() {
        return io.github.genichimaruo.singulo.item.ShieldPermitItem.members(menu.getSlot(AndroidStationBlockEntity.SLOT_PERMIT).getItem()).size();
    }

    /** 追従する人の名前（0 は持ち主）。 */
    private Component leaderName() {
        int i = menu.value(AndroidStationBlockEntity.D_LEADER);
        java.util.UUID owner = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station ? station.owner() : null;
        var list = AndroidStationBlockEntity.leaders(menu.getSlot(AndroidStationBlockEntity.SLOT_PERMIT).getItem(), owner);
        return i > 0 && i <= list.size() ? Component.literal(list.get(i - 1).name())
                : Component.translatable("gui.singulo.android_station.leader.owner");
    }

    /** 追従モードか（追従中に戦うかのボタンが効く）。 */
    private boolean following() {
        return menu.value(AndroidStationBlockEntity.D_MODE) == io.github.genichimaruo.singulo.android.RuinAndroid.MODE_FOLLOW;
    }

    private int selected() {
        return menu.value(AndroidStationBlockEntity.D_SELECTED);
    }

    /** 2体を受け持つか（見るアンドロイドを選ぶ札を出す）。 */
    private boolean twoUnits() {
        return menu.value(AndroidStationBlockEntity.D_UNITS) > 1;
    }

    /** 名前の欄の左端（2体のときは、選ぶ札の右）。 */
    private int nameX() {
        return INFO_X + (twoUnits() ? 2 * (UNIT_W + UNIT_GAP) : 0);
    }

    private int nameW() {
        return INFO_W - (nameX() - INFO_X);
    }

    /** 付けた名前（なければ型の愛称、設計メモリがなければ「設計メモリなし」）。 */
    private Component androidName() {
        int kind = menu.value(AndroidStationBlockEntity.D_KIND);
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station
                && !station.name(selected()).isEmpty()) {
            return Component.literal(station.name(selected()));
        }
        return kind >= 0 && kind < AndroidKind.values().length ? Component.translatable(AndroidKind.values()[kind].nicknameKey())
                : Component.translatable("gui.singulo.android_station.no_design");
    }

    /** 名前を付けているか（元に戻すボタンを出す）。 */
    private boolean named() {
        return !names.editing() && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station && !station.name(selected()).isEmpty();
    }

    private boolean orphanWaiting() {
        return menu.value(AndroidStationBlockEntity.D_STATUS) == AndroidStationBlockEntity.S_ORPHAN;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (names.editing()) {
            var box = names.box();
            if (box != null && !box.isMouseOver(mouseX, mouseY)) {
                removeWidget(names.commit(menu.containerId));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        if (menu.ruleView()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (named() && Panel.inside(mouseX, mouseY, leftPos + INFO_X + INFO_W - 11, topPos + NAME_Y - 1, 11, 11)) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        if (twoUnits() && minecraft != null && minecraft.gameMode != null) {
            for (int i = 0; i < AndroidStationBlockEntity.MAX_UNITS; i++) {
                if (Panel.inside(mouseX, mouseY, leftPos + INFO_X + i * (UNIT_W + UNIT_GAP), topPos + NAME_Y - 1, UNIT_W, 11)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AndroidStationBlockEntity.BUTTON_SELECT + i);
                    return true;
                }
            }
        }
        if (Panel.inside(mouseX, mouseY, leftPos + nameX(), topPos + NAME_Y, nameW() - 12, 11)
                && menu.value(AndroidStationBlockEntity.D_KIND) >= 0) {
            String current = minecraft != null && minecraft.level != null
                    && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station ? station.name(selected()) : "";
            var box = names.open(font, leftPos + nameX() - 2, topPos + NAME_Y - 3, nameW(), current);
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        if (minecraft != null && minecraft.gameMode != null) {
            int id = -1;
            for (int i = 0; i < MODES; i++) {
                if (Panel.inside(mouseX, mouseY, modeX(i), topPos + MODE_Y, modeW(i), 16)) {
                    id = i == io.github.genichimaruo.singulo.android.RuinAndroid.MODE_PATROL ? AndroidStationBlockEntity.BUTTON_MODE_PATROL : i;
                }
            }
            if (Panel.inside(mouseX, mouseY, leftPos + MINUS_X, topPos + RETREAT_Y, 16, 16)) {
                id = AndroidStationBlockEntity.BUTTON_RETREAT_DOWN;
            } else if (Panel.inside(mouseX, mouseY, leftPos + PLUS_X, topPos + RETREAT_Y, 16, 16)) {
                id = AndroidStationBlockEntity.BUTTON_RETREAT_UP;
            } else if (Panel.inside(mouseX, mouseY, leftPos + RECALL_X, topPos + RETREAT_Y, RECALL_W, 16)) {
                id = orphanWaiting() ? AndroidStationBlockEntity.BUTTON_LINK
                        : hasShiftDown() ? AndroidStationBlockEntity.BUTTON_RECALL_ALL : AndroidStationBlockEntity.BUTTON_RECALL;
            }
            if (menu.patrolling()) {
                for (int i = 0; i < 3; i++) {
                    if (Panel.inside(mouseX, mouseY, patrolX(i), topPos + LEADER_Y, PATROL_W, 16)) {
                        id = PATROL_BUTTONS[i];
                    }
                }
            } else {
                for (int i = 0; i < 2; i++) {
                    if (Panel.inside(mouseX, mouseY, escortX(i), topPos + ESCORT_Y, escortW(i), 16)) {
                        id = ESCORT_BUTTONS[i];
                    }
                }
                if (permitMembers() > 0 && Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + LEADER_Y, INFO_W, 16)) {
                    id = AndroidStationBlockEntity.BUTTON_LEADER;
                }
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static net.minecraft.world.item.ItemStack item(String id) {
        return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id)));
    }

    /** 左の列の専用スロット（空のときは入れる物の見本と、重ねると名前）。 */
    private static final int[] SIDE_SLOTS = {AndroidStationBlockEntity.SLOT_MEMORY, AndroidStationBlockEntity.SLOT_KIT,
            AndroidStationBlockEntity.SLOT_PRINT_UNIT, AndroidStationBlockEntity.SLOT_UPGRADE, AndroidStationBlockEntity.SLOT_PRODUCTION,
            AndroidStationBlockEntity.SLOT_REPAIR_STOCK, AndroidStationBlockEntity.SLOT_PERMIT};
    private static final String[] SIDE_GHOSTS = {"android_memory_blade", "android_kit", "android_factory_print_unit", "android_transfer_upgrade",
            "android_production_upgrade", "android_repair_kit", "shield_permit"};
    private static final String[] SIDE_NAMES = {"gui.singulo.android_station.memory", "gui.singulo.android_station.kit",
            "item.singulo.android_factory_print_unit", "gui.singulo.android_station.upgrade", "item.singulo.android_production_upgrade",
            "gui.singulo.android_station.stock", "gui.singulo.android_station.permit"};

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        skin = GuiSkin.android(accent());
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos;
        int y = topPos;
        for (int i = 0; i < SIDE_SLOTS.length; i++) {
            Slot s = menu.getSlot(SIDE_SLOTS[i]);
            ghostSlot(g, x + s.x, y + s.y, s, item(SIDE_GHOSTS[i]));
            if (!s.hasItem()) {
                area(s.x, s.y, 16, 16, List.of(Component.translatable(SIDE_NAMES[i])));
            }
        }
        for (Slot s : menu.slots) {
            if (s.isActive() && s.index >= AndroidStationBlockEntity.SLOT_ROUTE && s.index < AndroidStationBlockEntity.SLOTS) {
                Panel.slot(g, skin, x + s.x, y + s.y);
            }
        }
        int cap = Math.max(1, menu.value(AndroidStationBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, x + BAR_X, y + ENERGY_Y, 10, ENERGY_H, (double) menu.value(AndroidStationBlockEntity.D_ENERGY) / cap, Panel.GLOW);
        if (menu.ruleView()) {
            return;
        }
        if (named()) {
            int rx = x + INFO_X + INFO_W - 11;
            boolean hover = Panel.inside(mouseX, mouseY, rx, y + NAME_Y - 1, 11, 11);
            int d = Panel.button(g, skin, rx, y + NAME_Y - 1, 11, 11, hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            NameEditor.drawReset(g, rx, y + NAME_Y - 1 + d, false, 0, skin.text());
        }
        if (twoUnits()) {
            for (int i = 0; i < AndroidStationBlockEntity.MAX_UNITS; i++) {
                int ux = x + INFO_X + i * (UNIT_W + UNIT_GAP);
                button(g, ux, y + NAME_Y - 1, UNIT_W, 11, i == selected(), Panel.inside(mouseX, mouseY, ux, y + NAME_Y - 1, UNIT_W, 11));
            }
        }
        int status = menu.value(AndroidStationBlockEntity.D_STATUS);
        boolean present = status >= AndroidStationBlockEntity.S_PRINTING && status != AndroidStationBlockEntity.S_WAITING
                && status != AndroidStationBlockEntity.S_BLOCKED;
        int hp = present ? menu.value(AndroidStationBlockEntity.D_HP) : 0;
        int charge = present ? menu.value(AndroidStationBlockEntity.D_CHARGE) : 0;
        statBar(g, INFO_X, HP_Y, Panel.Icon.STAR, 0xFFD8506C, INFO_W - 12, hp / 100.0, 0xFF60C080,
                List.of(Component.translatable("gui.singulo.android_station.hp").copy().append(" " + hp + "%")));
        statBar(g, INFO_X, CHARGE_Y, Panel.Icon.BOLT, Panel.GLOW, INFO_W - 12, charge / 100.0, Panel.GLOW,
                List.of(Component.translatable("gui.singulo.android_station.charge").copy().append(" " + charge + "%")));
        int mode = menu.value(AndroidStationBlockEntity.D_MODE);
        for (int i = 0; i < MODES; i++) {
            button(g, modeX(i), y + MODE_Y, modeW(i), 16, i == mode, Panel.inside(mouseX, mouseY, modeX(i), y + MODE_Y, modeW(i), 16));
        }
        button(g, x + MINUS_X, y + RETREAT_Y, 16, 16, false, Panel.inside(mouseX, mouseY, x + MINUS_X, y + RETREAT_Y, 16, 16));
        Panel.minusIcon(g, x + MINUS_X + 6, y + RETREAT_Y + 6, skin.text());
        button(g, x + PLUS_X, y + RETREAT_Y, 16, 16, false, Panel.inside(mouseX, mouseY, x + PLUS_X, y + RETREAT_Y, 16, 16));
        Panel.plusIcon(g, x + PLUS_X + 6, y + RETREAT_Y + 6, skin.text());
        button(g, x + RECALL_X, y + RETREAT_Y, RECALL_W, 16, orphanWaiting(),
                Panel.inside(mouseX, mouseY, x + RECALL_X, y + RETREAT_Y, RECALL_W, 16));
        if (menu.patrolling()) {
            // 巡回: 巡回路の欄（スロット）と、その下に巡回の設定のボタン。警報中は欄の枠を赤く
            if ((menu.value(AndroidStationBlockEntity.D_PATROL) & AndroidStationBlockEntity.PATROL_ALARM) != 0) {
                g.renderOutline(x + AndroidStationMenu.ROUTE_X - 2, y + AndroidStationMenu.ROUTE_Y - 2,
                        AndroidStationBlockEntity.ROUTE_SLOTS * 18 + 2, 20, GuiSkin.BAD);
            }
            int flags = menu.value(AndroidStationBlockEntity.D_PATROL);
            boolean[] on = {false, (flags & AndroidStationBlockEntity.PATROL_NIGHT) != 0, false};
            for (int i = 0; i < 3; i++) {
                button(g, patrolX(i), y + LEADER_Y, PATROL_W, 16, on[i], Panel.inside(mouseX, mouseY, patrolX(i), y + LEADER_Y, PATROL_W, 16));
            }
            return;
        }
        // 追従中に戦うか（追従でないときは押せない形で出す。設定は覚えておく）
        int escort = menu.value(AndroidStationBlockEntity.D_ESCORT);
        for (int i = 0; i < 2; i++) {
            boolean on = i == escort;
            boolean hover = Panel.inside(mouseX, mouseY, escortX(i), y + ESCORT_Y, escortW(i), 16);
            if (following()) {
                button(g, escortX(i), y + ESCORT_Y, escortW(i), 16, on, hover);
            } else {
                Panel.button(g, skin, escortX(i), y + ESCORT_Y, escortW(i), 16, Panel.Press.DISABLED);
            }
        }
        if (permitMembers() > 0) {
            button(g, x + INFO_X, y + LEADER_Y, INFO_W, 16, false, Panel.inside(mouseX, mouseY, x + INFO_X, y + LEADER_Y, INFO_W, 16));
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover) {
        Panel.button(g, skin, x, y, w, h, on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
    }

    /** 幅 maxWidth に収まらなければ縮めて描く（左寄せ）。 */
    static void fit(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float k = (float) maxWidth / w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - k) * font.lineHeight / 2, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** 幅 width の中央に、収まらなければ縮めて描く。 */
    static void fitCentered(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        int w = Math.min(font.width(text), width);
        fit(g, font, text, x + (width - w) / 2, y, width, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
        if (menu.ruleView()) {
            WorkConsoleScreen.routeLabels(g, font, AndroidStationMenu.RULE_COL_X, AndroidStationMenu.RULE_Y, AndroidStationMenu.RULE_ROW, INFO_X,
                    skin.dim());
            return;
        }
        int kind = menu.value(AndroidStationBlockEntity.D_KIND);
        if (!names.editing()) {
            fit(g, font, androidName(), nameX(), NAME_Y + 1, nameW() - 13, kind >= 0 ? skin.text() : skin.dim());
        }
        if (twoUnits()) {
            for (int i = 0; i < AndroidStationBlockEntity.MAX_UNITS; i++) {
                fitCentered(g, font, Component.literal(String.valueOf(i + 1)), INFO_X + i * (UNIT_W + UNIT_GAP), NAME_Y + 1, UNIT_W,
                        i == selected() ? skin.onAccent() : skin.text());
            }
        }
        int escort = menu.value(AndroidStationBlockEntity.D_ESCORT);
        if (menu.patrolling()) {
            int flags = menu.value(AndroidStationBlockEntity.D_PATROL);
            for (int i = 0; i < 3; i++) {
                boolean on = i == 1 && (flags & AndroidStationBlockEntity.PATROL_NIGHT) != 0;
                fitCentered(g, font, patrolLabel(i), INFO_X + i * (PATROL_W + 3) + 2, LEADER_Y + 4, PATROL_W - 4, on ? skin.onAccent() : skin.text());
            }
            // 巡回路の欄の番号（左から回る順）
            for (int i = 0; i < AndroidStationBlockEntity.ROUTE_SLOTS; i++) {
                if (!menu.getSlot(AndroidStationBlockEntity.SLOT_ROUTE + i).hasItem()) {
                    fitCentered(g, font, Component.literal(String.valueOf(i + 1)), AndroidStationMenu.ROUTE_X + i * 18, AndroidStationMenu.ROUTE_Y + 4,
                            16, skin.dim());
                }
            }
        } else {
            for (int i = 0; i < 2; i++) {
                int color = !following() ? skin.dim() : i == escort ? skin.onAccent() : skin.text();
                fitCentered(g, font, Component.translatable(ESCORT_KEYS[i]), INFO_X + i * (ESCORT_W + 3) + 2, ESCORT_Y + 4, escortW(i) - 4, color);
            }
            if (permitMembers() > 0) {
                fitCentered(g, font, Component.translatable("gui.singulo.android_station.leader", leaderName()), INFO_X + 2, LEADER_Y + 4,
                        INFO_W - 4, skin.text());
            } else {
                fit(g, font, Component.translatable("gui.singulo.android_station.permit.none"), INFO_X, LEADER_Y + 4, INFO_W, skin.dim());
            }
        }
        int mode = menu.value(AndroidStationBlockEntity.D_MODE);
        for (int i = 0; i < MODES; i++) {
            fitCentered(g, font, Component.translatable("gui.singulo.android_station.mode." + i), INFO_X + i * (MODE_W + 3) + 2,
                    MODE_Y + 4, modeW(i) - 4, i == mode ? skin.onAccent() : skin.text());
        }
        boolean low = menu.value(AndroidStationBlockEntity.D_KIND) >= 0 && menu.value(AndroidStationBlockEntity.D_HP) > 0
                && menu.value(AndroidStationBlockEntity.D_HP) < menu.value(AndroidStationBlockEntity.D_RETREAT);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.retreat", menu.value(AndroidStationBlockEntity.D_RETREAT)),
                MINUS_X + 18, RETREAT_Y + 4, PLUS_X - MINUS_X - 20, low ? 0xFFC06A20 : skin.text());
        fitCentered(g, font, Component.translatable(orphanWaiting() ? "gui.singulo.android_station.link" : "gui.singulo.android_station.recall"),
                RECALL_X + 2, RETREAT_Y + 4, RECALL_W - 4, orphanWaiting() ? skin.onAccent() : skin.text());
    }

    private static int statusColor(int status) {
        return switch (status) {
            case AndroidStationBlockEntity.S_ACTIVE, AndroidStationBlockEntity.S_PRINTING, AndroidStationBlockEntity.S_ORPHAN -> 0xFF2A8FB8;
            case AndroidStationBlockEntity.S_CHARGING, AndroidStationBlockEntity.S_RETURNING, AndroidStationBlockEntity.S_WAITING,
                    AndroidStationBlockEntity.S_AWAY -> 0xFFC08020;
            default -> 0xFFB05050;
        };
    }
}
