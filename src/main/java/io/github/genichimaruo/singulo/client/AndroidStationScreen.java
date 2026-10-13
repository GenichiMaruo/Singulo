package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.AndroidStationMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * アンドロイド組立ステーションの画面（横長）。左に設計メモリとキットのスロット、中ほどにアンドロイドの様子（型・状態・体力・充電）、
 * その下に動き方（迎撃・徘徊・追従）と、戻ってくる体力の割合、呼び戻すボタン、追従中に戦うか（ついていくだけ・護衛する）、
 * 許可証の登録の様子。右端は蓄電量。長い文字は枠に収まるよう縮める。
 */
public class AndroidStationScreen extends AbstractContainerScreen<AndroidStationMenu> {
    private static final int INFO_X = 46;
    private static final int INFO_W = 162;
    private static final int BAR_LABEL_W = 30;
    private static final int MODE_Y = 82;
    private static final int MODE_W = 52;
    private static final int RETREAT_Y = 104;
    private static final int MINUS_X = INFO_X;
    private static final int PLUS_X = INFO_X + 92;
    private static final int RECALL_X = INFO_X + 110;
    private static final int RECALL_W = 52;
    private static final int BAR_X = 222;
    private static final int ESCORT_Y = 126;
    private static final int ESCORT_W = (INFO_W - 3) / 2;
    private static final int LEADER_Y = 142;
    private static final int NAME_Y = 20;

    private final NameEditor names = new NameEditor();

    public AndroidStationScreen(AndroidStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = AndroidStationMenu.WIDTH;
        imageHeight = AndroidStationMenu.HEIGHT;
        inventoryLabelX = AndroidStationMenu.INV_X;
        inventoryLabelY = AndroidStationMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (Panel.inside(mouseX, mouseY, leftPos + BAR_X, topPos + 18, 10, 104)) {
            g.renderTooltip(font, Component.literal(menu.value(AndroidStationBlockEntity.D_ENERGY) + " / "
                    + menu.value(AndroidStationBlockEntity.D_CAPACITY) + " FE"), mouseX, mouseY);
        }
        for (int i = 0; i < 3; i++) {
            if (Panel.inside(mouseX, mouseY, modeX(i), topPos + MODE_Y, MODE_W, 16)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.mode." + i + ".hint"), 180),
                        mouseX, mouseY);
            }
        }
        if (named() && Panel.inside(mouseX, mouseY, leftPos + INFO_X + INFO_W - 10, topPos + NAME_Y, 10, 10)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + NAME_Y, INFO_W - 12, 11)
                && menu.value(AndroidStationBlockEntity.D_KIND) >= 0) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + MINUS_X + 16, topPos + RETREAT_Y, PLUS_X - MINUS_X - 18, 16)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.android_station.retreat.hint"), 180), mouseX, mouseY);
        }
        for (int i = 0; i < 2; i++) {
            if (Panel.inside(mouseX, mouseY, escortX(i), topPos + ESCORT_Y, ESCORT_W, 16)) {
                g.renderTooltip(font, font.split(Component.translatable(ESCORT_KEYS[i] + ".hint"), 180), mouseX, mouseY);
            }
        }
        if (Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + LEADER_Y, INFO_W, 16)) {
            int members = permitMembers();
            if (members > 0) {
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
                lines.addAll(font.split(Component.translatable("gui.singulo.android_station.permit.members", members), 180));
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

    /** 追従中に戦うかのボタン（0: ついていくだけ、1: 護衛する）。 */
    private static final String[] ESCORT_KEYS = {"gui.singulo.android_station.follow_only", "gui.singulo.android_station.escort"};
    private static final int[] ESCORT_BUTTONS = {AndroidStationBlockEntity.BUTTON_FOLLOW_ONLY, AndroidStationBlockEntity.BUTTON_ESCORT};

    private int escortX(int i) {
        return leftPos + INFO_X + i * (ESCORT_W + 3);
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

    /** 付けた名前（なければ型の愛称、設計メモリがなければ「設計メモリなし」）。 */
    private Component androidName() {
        int kind = menu.value(AndroidStationBlockEntity.D_KIND);
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station && !station.name().isEmpty()) {
            return Component.literal(station.name());
        }
        return kind >= 0 && kind < AndroidKind.values().length ? Component.translatable(AndroidKind.values()[kind].nicknameKey())
                : Component.translatable("gui.singulo.android_station.no_design");
    }

    /** 名前を付けているか（元に戻すボタンを出す）。 */
    private boolean named() {
        return !names.editing() && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station && !station.name().isEmpty();
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
        if (named() && Panel.inside(mouseX, mouseY, leftPos + INFO_X + INFO_W - 10, topPos + NAME_Y, 10, 10)) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + NAME_Y, INFO_W - 12, 11)
                && menu.value(AndroidStationBlockEntity.D_KIND) >= 0) {
            String current = minecraft != null && minecraft.level != null
                    && minecraft.level.getBlockEntity(menu.pos()) instanceof AndroidStationBlockEntity station ? station.name() : "";
            var box = names.open(font, leftPos + INFO_X - 2, topPos + NAME_Y - 3, INFO_W, current);
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        if (minecraft != null && minecraft.gameMode != null) {
            int id = -1;
            for (int i = 0; i < 3; i++) {
                if (Panel.inside(mouseX, mouseY, modeX(i), topPos + MODE_Y, MODE_W, 16)) {
                    id = i;
                }
            }
            if (Panel.inside(mouseX, mouseY, leftPos + MINUS_X, topPos + RETREAT_Y, 16, 16)) {
                id = AndroidStationBlockEntity.BUTTON_RETREAT_DOWN;
            } else if (Panel.inside(mouseX, mouseY, leftPos + PLUS_X, topPos + RETREAT_Y, 16, 16)) {
                id = AndroidStationBlockEntity.BUTTON_RETREAT_UP;
            } else if (Panel.inside(mouseX, mouseY, leftPos + RECALL_X, topPos + RETREAT_Y, RECALL_W, 16)) {
                id = orphanWaiting() ? AndroidStationBlockEntity.BUTTON_LINK : AndroidStationBlockEntity.BUTTON_RECALL;
            }
            for (int i = 0; i < 2; i++) {
                if (Panel.inside(mouseX, mouseY, escortX(i), topPos + ESCORT_Y, ESCORT_W, 16)) {
                    id = ESCORT_BUTTONS[i];
                }
            }
            if (permitMembers() > 0 && Panel.inside(mouseX, mouseY, leftPos + INFO_X, topPos + LEADER_Y, INFO_W, 16)) {
                id = AndroidStationBlockEntity.BUTTON_LEADER;
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panel.background(g, x, y, imageWidth, imageHeight);
        for (Slot s : menu.slots) {
            Panel.slot(g, x + s.x, y + s.y);
        }
        int cap = Math.max(1, menu.value(AndroidStationBlockEntity.D_CAPACITY));
        if (named()) {
            NameEditor.drawReset(g, x + INFO_X + INFO_W - 10, y + NAME_Y, Panel.inside(mouseX, mouseY, x + INFO_X + INFO_W - 10, y + NAME_Y, 10, 10),
                    0xFFD6DBE0, Panel.TEXT);
        }
        Panel.verticalBar(g, x + BAR_X, y + 18, 10, 104, (double) menu.value(AndroidStationBlockEntity.D_ENERGY) / cap, Panel.GLOW);
        int status = menu.value(AndroidStationBlockEntity.D_STATUS);
        boolean present = status >= AndroidStationBlockEntity.S_PRINTING && status != AndroidStationBlockEntity.S_WAITING
                && status != AndroidStationBlockEntity.S_BLOCKED;
        bar(g, x + INFO_X + BAR_LABEL_W, y + 52, present ? menu.value(AndroidStationBlockEntity.D_HP) : 0, 0xFF60C080);
        bar(g, x + INFO_X + BAR_LABEL_W, y + 66, present ? menu.value(AndroidStationBlockEntity.D_CHARGE) : 0, Panel.GLOW);
        int mode = menu.value(AndroidStationBlockEntity.D_MODE);
        for (int i = 0; i < 3; i++) {
            button(g, modeX(i), y + MODE_Y, MODE_W, 16, i == mode, Panel.inside(mouseX, mouseY, modeX(i), y + MODE_Y, MODE_W, 16));
        }
        button(g, x + MINUS_X, y + RETREAT_Y, 16, 16, false, Panel.inside(mouseX, mouseY, x + MINUS_X, y + RETREAT_Y, 16, 16));
        button(g, x + PLUS_X, y + RETREAT_Y, 16, 16, false, Panel.inside(mouseX, mouseY, x + PLUS_X, y + RETREAT_Y, 16, 16));
        button(g, x + RECALL_X, y + RETREAT_Y, RECALL_W, 16, orphanWaiting(),
                Panel.inside(mouseX, mouseY, x + RECALL_X, y + RETREAT_Y, RECALL_W, 16));
        // 追従中に戦うか（追従でないときは薄く出す。設定は覚えておく）
        int escort = menu.value(AndroidStationBlockEntity.D_ESCORT);
        for (int i = 0; i < 2; i++) {
            boolean on = i == escort;
            boolean hover = Panel.inside(mouseX, mouseY, escortX(i), y + ESCORT_Y, ESCORT_W, 16);
            if (following()) {
                button(g, escortX(i), y + ESCORT_Y, ESCORT_W, 16, on, hover);
            } else {
                g.fill(escortX(i) - 1, y + ESCORT_Y - 1, escortX(i) + ESCORT_W + 1, y + ESCORT_Y + 17, 0xFFC4CBD2);
                g.fill(escortX(i), y + ESCORT_Y, escortX(i) + ESCORT_W, y + ESCORT_Y + 16, on ? 0xFFAFC9D6 : hover ? 0xFFE6EDF1 : 0xFFF4F6F8);
            }
        }
        if (permitMembers() > 0) {
            button(g, x + INFO_X, y + LEADER_Y, INFO_W, 16, false, Panel.inside(mouseX, mouseY, x + INFO_X, y + LEADER_Y, INFO_W, 16));
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int percent, int color) {
        int w = INFO_W - BAR_LABEL_W;
        Panel.well(g, x, y, w, 7);
        g.fill(x, y, x + w * Math.max(0, Math.min(100, percent)) / 100, y + 7, color);
    }

    private static void button(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Panel.WELL_EDGE);
        g.fill(x, y, x + w, y + h, on ? 0xFF3A8FB0 : hover ? 0xFFD6E6EE : 0xFFF4F6F8);
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
        fit(g, font, title, 8, 7, imageWidth - 16, Panel.TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        int kind = menu.value(AndroidStationBlockEntity.D_KIND);
        if (!names.editing()) {
            fit(g, font, androidName(), INFO_X, NAME_Y + 2, INFO_W - 13, kind >= 0 ? Panel.TEXT : 0xFF8A949E);
        }
        int status = menu.value(AndroidStationBlockEntity.D_STATUS);
        Component state = status == AndroidStationBlockEntity.S_WAITING
                ? Component.translatable("gui.singulo.android_station.status.waiting", (menu.value(AndroidStationBlockEntity.D_DELAY) + 19) / 20)
                : Component.translatable("gui.singulo.android_station.status." + status);
        fit(g, font, state, INFO_X, 35, INFO_W, statusColor(status));
        fit(g, font, Component.translatable("gui.singulo.android_station.hp"), INFO_X, 51, BAR_LABEL_W - 3, Panel.TEXT);
        fit(g, font, Component.translatable("gui.singulo.android_station.charge"), INFO_X, 65, BAR_LABEL_W - 3, Panel.TEXT);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.memory"), 6, AndroidStationMenu.MEMORY_Y - 11, 32, 0xFF8A949E);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.kit"), 6, AndroidStationMenu.KIT_Y - 11, 32, 0xFF8A949E);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.upgrade"), 6, AndroidStationMenu.UPGRADE_Y - 11, 32,
                0xFF8A949E);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.stock"), 6, AndroidStationMenu.STOCK_Y - 11, 32,
                0xFF8A949E);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.permit"), 6, AndroidStationMenu.PERMIT_Y - 11, 32,
                0xFF8A949E);
        int escort = menu.value(AndroidStationBlockEntity.D_ESCORT);
        for (int i = 0; i < 2; i++) {
            int color = !following() ? 0xFF9AA3AC : i == escort ? 0xFFFFFFFF : Panel.TEXT;
            fitCentered(g, font, Component.translatable(ESCORT_KEYS[i]), INFO_X + i * (ESCORT_W + 3) + 2, ESCORT_Y + 4, ESCORT_W - 4, color);
        }
        if (permitMembers() > 0) {
            fitCentered(g, font, Component.translatable("gui.singulo.android_station.leader", leaderName()), INFO_X + 2, LEADER_Y + 4,
                    INFO_W - 4, Panel.TEXT);
        } else {
            fit(g, font, Component.translatable("gui.singulo.android_station.permit.none"), INFO_X, LEADER_Y + 4, INFO_W, 0xFF8A949E);
        }
        int mode = menu.value(AndroidStationBlockEntity.D_MODE);
        for (int i = 0; i < 3; i++) {
            fitCentered(g, font, Component.translatable("gui.singulo.android_station.mode." + i), INFO_X + i * (MODE_W + 3) + 2,
                    MODE_Y + 4, MODE_W - 4, i == mode ? 0xFFFFFFFF : Panel.TEXT);
        }
        g.drawString(font, "-", MINUS_X + 6, RETREAT_Y + 4, Panel.TEXT, false);
        g.drawString(font, "+", PLUS_X + 5, RETREAT_Y + 4, Panel.TEXT, false);
        fitCentered(g, font, Component.translatable("gui.singulo.android_station.retreat", menu.value(AndroidStationBlockEntity.D_RETREAT)),
                MINUS_X + 18, RETREAT_Y + 4, PLUS_X - MINUS_X - 20, Panel.TEXT);
        fitCentered(g, font, Component.translatable(orphanWaiting() ? "gui.singulo.android_station.link" : "gui.singulo.android_station.recall"),
                RECALL_X + 2, RETREAT_Y + 4, RECALL_W - 4, Panel.TEXT);
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
