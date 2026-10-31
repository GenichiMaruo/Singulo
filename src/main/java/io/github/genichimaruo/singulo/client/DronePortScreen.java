package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.DronePortMenu;
import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.network.SideConfigPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ドローン発着ポートの画面。ヘッダーの名前をクリックすると変えられる（行き先の一覧に出る名前）。
 * 中身の上のタブ（記号だけ。重ねると名前）で、荷物・発送・輸送中のページを切り替える（DronePortMenu を参照）。
 * 右レールの設定の段で、加工装置と同じ面の設定（入出力と自動排出）の窓を開く。
 */
public class DronePortScreen extends FramedScreen<DronePortMenu> {
    private final NameEditor names = new NameEditor();
    /** 一覧の最初に見えている行。 */
    private int scroll;
    /** 最後に見ていたページ（開き直しても同じページに出す。ゲームを閉じるまで）。 */
    private static int lastPage = DronePortMenu.PAGE_CARGO;
    private static final Panel.Icon[] TAB_ICONS = {Panel.Icon.BOX, Panel.Icon.OUT, Panel.Icon.SPEED};

    /** 面の設定の窓と、ドラッグで回せる立方体（加工装置の画面と同じ操作）。 */
    private final Popup sides = new Popup("drone_port.sides");
    private final SideCube cube = new SideCube();
    private static final float CUBE_HALF = 13;
    private static final int OVERLAY_H = 66;
    private int cubePress = -1;
    private double pressX;
    private double pressY;
    private boolean cubeDragged;

    public DronePortScreen(DronePortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DronePortMenu.WIDTH, DronePortMenu.HEIGHT);
        skin = GuiSkin.android(DroneStationScreen.ACCENT);
        menu.setPage(lastPage);
    }

    private int[] tabRect(int page) {
        return new int[]{leftPos + DronePortMenu.TAB_X + page * (DronePortMenu.TAB_W + DronePortMenu.TAB_GAP), topPos + DronePortMenu.TAB_Y,
                DronePortMenu.TAB_W, DronePortMenu.TAB_H};
    }

    private boolean anyShipment() {
        for (int row = 0; row < DronePortMenu.SHIP_ROWS; row++) {
            if (menu.ship(row).phase() != 0) {
                return true;
            }
        }
        return false;
    }

    /** ページのタブ。輸送中の便があれば、輸送中のタブの角に橙の点。 */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int p = 0; p < DronePortMenu.PAGES; p++) {
            int[] r = tabRect(p);
            boolean on = menu.page() == p;
            boolean hover = !overPopup(mouseX, mouseY) && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            int d = Panel.button(g, skin, r[0], r[1], r[2], r[3], on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            Panel.icon(g, r[0] + (r[2] - 7) / 2, r[1] + 3 + d, TAB_ICONS[p], on ? skin.onAccent() : skin.text());
            if (p == DronePortMenu.PAGE_SHIPS && anyShipment()) {
                g.fill(r[0] + r[2] - 5, r[1] + 2 + d, r[0] + r[2] - 3, r[1] + 4 + d, 0xFF000000 | DroneStationScreen.ACCENT);
            }
            area(r[0] - leftPos, r[1] - topPos, r[2], r[3], List.of(Component.translatable("gui.singulo.drone_port.tab." + p)));
        }
    }

    private String portName() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof DronePortBlockEntity port ? port.name() : "";
    }

    /** 名前（なければ「発着ポート（x, y, z）」）。 */
    static Component placeName(String name, BlockPos pos) {
        return name.isEmpty() ? Component.translatable("gui.singulo.drone_port.place", pos.getX(), pos.getY(), pos.getZ())
                : Component.literal(name);
    }

    /** 一覧の番号の行き先の名前（わからなければ「ほかのポート」）。 */
    private Component partnerName(int index) {
        return index >= 0 && index < menu.targets().size() ? placeName(menu.names().get(index), menu.targets().get(index))
                : Component.translatable("gui.singulo.drone_port.elsewhere");
    }

    private boolean named() {
        return !names.editing() && !portName().isEmpty();
    }

    private int[] resetRect() {
        return new int[]{leftPos + GuiFrame.powerX(imageWidth), topPos + GuiFrame.POWER_Y, GuiFrame.POWER_SIZE, GuiFrame.POWER_SIZE};
    }

    @Override
    protected int headerReserved() {
        return named() ? GuiFrame.POWER_SIZE + 2 : 0;
    }

    private boolean overName(double mouseX, double mouseY) {
        return Panel.inside(mouseX, mouseY, leftPos + titleLabelX, topPos + titleLabelY - 2, titleWidth(), 11);
    }

    private int portStatus() {
        return menu.value(DronePortBlockEntity.D_STATUS);
    }

    @Override
    protected Panel.Status status() {
        return switch (portStatus()) {
            case DronePortBlockEntity.S_IDLE -> Panel.Status.IDLE;
            case DronePortBlockEntity.S_NO_ENERGY -> Panel.Status.NO_POWER;
            case DronePortBlockEntity.S_WAITING, DronePortBlockEntity.S_COMING, DronePortBlockEntity.S_LOADING,
                 DronePortBlockEntity.S_UNLOADING -> Panel.Status.RUN;
            default -> Panel.Status.WARN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case RUN -> GuiSkin.OK;
            case IDLE -> GuiSkin.IDLE;
            default -> GuiSkin.WARN;
        };
    }

    @Override
    protected Component statusText() {
        return Component.translatable("gui.singulo.drone_port.status." + portStatus());
    }

    private int crateTier() {
        return DroneCrateItem.tier(menu.getSlot(0).getItem());
    }

    private int distance(BlockPos to) {
        return (int) Math.round(Math.sqrt(to.distSqr(menu.pos())));
    }

    private boolean staged() {
        int s = portStatus();
        return s == DronePortBlockEntity.S_WAITING || s == DronePortBlockEntity.S_COMING || s == DronePortBlockEntity.S_LOADING;
    }

    /** 発送できるか（行き先・荷物・箱・射程・電力・呼べるドローン。サーバーが計る）。 */
    private boolean canSend() {
        return !staged() && menu.value(DronePortBlockEntity.D_PROBLEM) == DronePortBlockEntity.S_IDLE;
    }

    private boolean canCancel() {
        return staged() && portStatus() != DronePortBlockEntity.S_LOADING;
    }

    private int[] sendRect() {
        return new int[]{leftPos + DronePortMenu.LIST_X, topPos + DronePortMenu.BUTTON_Y, DronePortMenu.BUTTON_W, DronePortMenu.BUTTON_H};
    }

    private int[] cancelRect() {
        return new int[]{leftPos + DronePortMenu.LIST_X + DronePortMenu.LIST_W - DronePortMenu.BUTTON_W, topPos + DronePortMenu.BUTTON_Y,
                DronePortMenu.BUTTON_W, DronePortMenu.BUTTON_H};
    }

    // ------------------------------------------------------------------ 右レール（面の設定）

    @Override
    protected boolean railUsed(int row) {
        return row == GuiFrame.RAIL_SETTINGS;
    }

    @Override
    protected boolean railConnected(int row) {
        return sides.open;
    }

    @Override
    protected void drawRail(GuiGraphics g, int row, int x, int y, boolean hover) {
        Panel.sidesIcon(g, x, y);
    }

    @Override
    protected boolean clickRail(int row, int button) {
        sides.open = !sides.open;
        return true;
    }

    @Override
    protected Component railTooltip(int row) {
        return Component.translatable("gui.singulo.sides.button");
    }

    @Override
    protected List<Popup> popups() {
        return List.of(sides);
    }

    private void layoutPopups() {
        int bottom = GuiFrame.contentBottom(imageHeight);
        int sh = Popup.BAR + 1 + OVERLAY_H;
        sides.layout(leftPos, topPos, SideCube.POPUP_W, sh, 4, bottom - sh, width, height);
    }

    private float cubeX() {
        return sides.x + sides.w / 2F;
    }

    private float cubeY() {
        return sides.bodyY() + OVERLAY_H / 2F;
    }

    private List<SideCube.Quad> cubeQuads() {
        return cube.visible(cubeX(), cubeY(), CUBE_HALF);
    }

    private int sidesPacked() {
        return menu.value(DronePortBlockEntity.D_SIDES);
    }

    private static int modeColor(int mode) {
        return switch (mode) {
            case 1 -> 0xFF4A8FE0;        // 入力: 青
            case 2 -> 0xFFF0A040;        // 出力: 橙
            case 3 -> 0xFF50B870;        // 入出力: 緑
            default -> 0xFF9AA2AA;       // 無効: 灰
        };
    }

    private int[] masterPos() {
        return new int[]{sides.x + sides.w - 17, sides.bodyY() + 4, 13, 13};
    }

    private int[] legendRect(int index) {
        return new int[]{sides.x + sides.w - 15, sides.bodyY() + 20 + index * 11, 9, 9};
    }

    private void renderSides(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        sides.draw(g, skin, mouseX, mouseY);
        int packed = sidesPacked();
        var quads = cubeQuads();
        SideConfig.Face hovered = cube.pick(quads, mouseX, mouseY);
        cube.draw(g, font, quads, f -> modeColor(SideConfig.mode(packed, f)), f -> SideConfig.eject(packed, f),
                f -> Component.translatable("gui.singulo.sides.face." + f.name().toLowerCase()).getString().substring(0, 1), hovered);
        boolean master = SideConfig.ejectEnabled(packed);
        int[] mp = masterPos();
        boolean mh = Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3]);
        int d = Panel.button(g, skin, mp[0], mp[1], mp[2], mp[3], master ? Panel.Press.ON : mh ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.ejectIcon(g, mp[0] + 2, mp[1] + 2 + d, master ? skin.onAccent() : skin.text());
        for (int i = 0; i < 4; i++) {
            int[] r = legendRect(i);
            Panel.inset(g, skin, r[0], r[1], r[2], r[3], modeColor(i));
        }
        List<Component> tip = new ArrayList<>();
        if (mh) {
            tip.add(Component.translatable(master ? "gui.singulo.sides.master_on" : "gui.singulo.sides.master_off"));
        }
        for (int i = 0; i < 4; i++) {
            int[] r = legendRect(i);
            if (Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                tip.add(Component.translatable("gui.singulo.sides.mode." + i));
            }
        }
        if (hovered != null) {
            tip.add(Component.translatable("gui.singulo.sides.face." + hovered.name().toLowerCase()));
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var state = level.getBlockState(menu.pos());
                var facing = state.hasProperty(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING)
                        ? state.getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING) : net.minecraft.core.Direction.NORTH;
                var adjacent = menu.pos().relative(SideConfig.directionOf(facing, hovered));
                tip.add(Component.translatable("gui.singulo.sides.adjacent", level.getBlockState(adjacent).getBlock().getName()));
            }
            tip.add(Component.translatable("gui.singulo.sides.mode." + SideConfig.mode(packed, hovered)));
            tip.add(Component.translatable(SideConfig.eject(packed, hovered) ? "gui.singulo.sides.eject_on" : "gui.singulo.sides.eject_off"));
            tip.add(Component.translatable(master ? "gui.singulo.sides.master_on" : "gui.singulo.sides.master_off"));
            tip.add(Component.translatable("gui.singulo.sides.hint").withStyle(s -> s.withColor(0xFF8A949E)));
        }
        if (!tip.isEmpty()) {
            Panel.tooltip(g, font, tip, 230, mouseX, mouseY);
        }
        g.pose().popPose();
    }

    private boolean clickSides(double mouseX, double mouseY, int button) {
        int packed = sidesPacked();
        int[] mp = masterPos();
        if (Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3])) {
            PacketDistributor.sendToServer(new SideConfigPayload(menu.pos(), 0, SideConfigPayload.MASTER_FACE,
                    SideConfig.ejectEnabled(packed) ? 0 : 4));
            return true;
        }
        if (Math.abs(mouseX - cubeX()) < CUBE_HALF * 2 && Math.abs(mouseY - cubeY()) < CUBE_HALF * 2) {
            cubePress = button;
            pressX = mouseX;
            pressY = mouseY;
            cubeDragged = false;
            return true;
        }
        return false;
    }

    /** 面をクリックした: 左は入出力の切り替え、右は自動排出の切り替え。 */
    private void clickFace(SideConfig.Face f, int button) {
        int packed = sidesPacked();
        int mode = SideConfig.mode(packed, f);
        boolean eject = SideConfig.eject(packed, f);
        if (button == 1) {
            eject = !eject;
        } else {
            mode = (mode + 1) % 4;
        }
        PacketDistributor.sendToServer(new SideConfigPayload(menu.pos(), 0, f.ordinal(), mode | (eject ? 4 : 0)));
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layoutPopups();
        super.render(g, mouseX, mouseY, partialTick);
        if (sides.open) {
            renderSides(g, mouseX, mouseY);
        }
        if (overPopup(mouseX, mouseY)) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int[] r = resetRect();
        if (named() && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && overName(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
        } else if (!renderFrameTooltips(g, mouseX, mouseY) && !renderStatTooltips(g, mouseX, mouseY)) {
            renderTooltip(g, mouseX, mouseY);
        }
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos;
        int y = topPos;
        drawTabs(g, mouseX, mouseY);
        switch (menu.page()) {
            case DronePortMenu.PAGE_SEND -> {
                drawList(g, mouseX, mouseY);
                drawButtons(g, mouseX, mouseY);
                drawAuto(g, mouseX, mouseY);
            }
            case DronePortMenu.PAGE_SHIPS -> drawShipments(g);
            default -> drawCargo(g);
        }
        if (named()) {
            int[] r = resetRect();
            boolean hover = Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            int d = Panel.button(g, skin, r[0], r[1], r[2], r[3], hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            NameEditor.drawReset(g, r[0], r[1] + d, false, 0, skin.text());
        }
    }

    /** 荷物のページ: 空き箱（見本つき）と荷物、蓄電、着荷箱 → 受け取り。 */
    private void drawCargo(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        var crate = new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("packing_crate")));
        for (int i = 0; i < DronePortBlockEntity.SLOTS; i++) {
            var s = menu.getSlot(i);
            if (i == DronePortBlockEntity.SLOT_CRATES) {
                ghostSlot(g, x + s.x, y + s.y, s, crate);
                if (!s.hasItem()) {
                    area(s.x, s.y, 16, 16, List.of(Component.translatable("gui.singulo.drone_port.crates")));
                }
            } else {
                Panel.slot(g, skin, x + s.x, y + s.y);
            }
        }
        // 着荷箱から受け取りの欄へ開梱する向きの矢印（どちらの枠からも離して、真ん中の段に）
        Panel.icon(g, x + DronePortMenu.ARROW_X, y + DronePortMenu.ARRIVED_Y + 18 + 4, Panel.Icon.OUT, skin.dim());
        area(DronePortMenu.ARRIVED_X - 1, DronePortMenu.ARRIVED_Y - 1, 18, 3 * 18,
                List.of(Component.translatable("gui.singulo.drone_port.arrived")));
        area(DronePortMenu.RECEIVE_X - 1, DronePortMenu.RECEIVE_Y - 1, 3 * 18, 3 * 18,
                List.of(Component.translatable("gui.singulo.drone_port.receive")));
        int cap = Math.max(1, menu.value(DronePortBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, x + DronePortMenu.ENERGY_X, y + DronePortMenu.ENERGY_Y, 6, DronePortMenu.ENERGY_H,
                (double) menu.value(DronePortBlockEntity.D_ENERGY) / cap, Panel.GLOW);
        area(DronePortMenu.ENERGY_X, DronePortMenu.ENERGY_Y, 6, DronePortMenu.ENERGY_H,
                Panel.energyLines(menu.value(DronePortBlockEntity.D_ENERGY), cap));
    }

    /** 行き先の一覧: 名前と距離。選んだ行は光り、今の箱で届かない行は距離が赤い。 */
    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + DronePortMenu.LIST_X;
        int y = topPos + DronePortMenu.LIST_Y;
        int h = DronePortMenu.ROWS * DronePortMenu.ROW_H;
        Panel.well(g, skin, x, y, DronePortMenu.LIST_W, h);
        List<BlockPos> targets = menu.targets();
        scroll = Mth.clamp(scroll, 0, Math.max(0, targets.size() - DronePortMenu.ROWS));
        if (targets.isEmpty()) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.drone_port.no_targets"), x + 2, y + (h - 8) / 2,
                    DronePortMenu.LIST_W - 4, skin.dim());
            return;
        }
        int range = DroneCrateItem.range(crateTier());
        int textW = DronePortMenu.LIST_W - (targets.size() > DronePortMenu.ROWS ? 5 : 2);
        for (int row = 0; row < DronePortMenu.ROWS && scroll + row < targets.size(); row++) {
            int i = scroll + row;
            BlockPos to = targets.get(i);
            int ry = y + row * DronePortMenu.ROW_H;
            boolean selected = i == menu.selected();
            boolean hover = !overPopup(mouseX, mouseY) && Panel.inside(mouseX, mouseY, x, ry, textW, DronePortMenu.ROW_H);
            if (selected) {
                g.fill(x + 1, ry + 1, x + textW, ry + DronePortMenu.ROW_H - 1, skin.accent());
            } else if (hover) {
                g.fill(x + 1, ry + 1, x + textW, ry + DronePortMenu.ROW_H - 1, 0x30FFFFFF);
            }
            int text = selected ? skin.onAccent() : skin.text();
            int d = distance(to);
            String dist = d + "m";
            boolean reach = range > 0 && d <= range;
            int dw = font.width(dist);
            AndroidStationScreen.fit(g, font, placeName(menu.names().get(i), to), x + 3, ry + 3, textW - dw - 8, text);
            g.drawString(font, dist, x + textW - dw - 2, ry + 3, reach ? text : GuiSkin.BAD, false);
            List<Component> tip = new ArrayList<>();
            tip.add(placeName(menu.names().get(i), to));
            tip.add(Component.translatable("gui.singulo.drone_port.target", to.getX(), to.getY(), to.getZ()).withStyle(st -> st.withColor(0xFF8A949E)));
            tip.add(Component.translatable("gui.singulo.drone_port.cost", DroneCrateItem.cost(Math.sqrt(to.distSqr(menu.pos())))));
            if (!reach) {
                tip.add(Component.translatable(range == 0 ? "gui.singulo.drone_port.status.6" : "gui.singulo.drone_port.status.7")
                        .withStyle(st -> st.withColor(GuiSkin.BAD)));
            }
            area(DronePortMenu.LIST_X, DronePortMenu.LIST_Y + row * DronePortMenu.ROW_H, textW, DronePortMenu.ROW_H, tip);
        }
        if (targets.size() > DronePortMenu.ROWS) {
            // 右端の細いつまみ
            int track = h - 2;
            int knob = Math.max(6, track * DronePortMenu.ROWS / targets.size());
            int ky = y + 1 + (track - knob) * scroll / Math.max(1, targets.size() - DronePortMenu.ROWS);
            g.fill(x + DronePortMenu.LIST_W - 3, ky, x + DronePortMenu.LIST_W - 1, ky + knob, skin.dim());
        }
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean free = !overPopup(mouseX, mouseY);
        int[] s = sendRect();
        Panel.labelButton(g, font, skin, s[0], s[1], s[2], s[3], Component.translatable("gui.singulo.drone_port.send"),
                !canSend() ? Panel.Press.DISABLED : free && Panel.inside(mouseX, mouseY, s[0], s[1], s[2], s[3]) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        List<Component> tip = new ArrayList<>();
        int problem = menu.value(DronePortBlockEntity.D_PROBLEM);
        if (menu.selected() >= 0) {
            tip.add(Component.translatable("gui.singulo.drone_port.cost", menu.value(DronePortBlockEntity.D_COST)));
        }
        if (!staged() && problem != DronePortBlockEntity.S_IDLE) {
            tip.add(Component.translatable("gui.singulo.drone_port.status." + problem).withStyle(st -> st.withColor(GuiSkin.WARN)));
        }
        area(DronePortMenu.LIST_X, DronePortMenu.BUTTON_Y, DronePortMenu.BUTTON_W, DronePortMenu.BUTTON_H, tip);
        int[] c = cancelRect();
        Panel.labelButton(g, font, skin, c[0], c[1], c[2], c[3], Component.translatable("gui.singulo.drone_port.cancel"),
                !canCancel() ? Panel.Press.DISABLED : free && Panel.inside(mouseX, mouseY, c[0], c[1], c[2], c[3]) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        if (canCancel()) {
            area(c[0] - leftPos, c[1] - topPos, c[2], c[3], List.of(Component.translatable("gui.singulo.drone_port.cancel.hint")));
        }
    }

    private int[] autoRect() {
        return new int[]{leftPos + DronePortMenu.LIST_X, topPos + DronePortMenu.AUTO_Y, DronePortMenu.AUTO_W, DronePortMenu.BUTTON_H};
    }

    private int[] registerRect() {
        return new int[]{leftPos + DronePortMenu.LIST_X + DronePortMenu.AUTO_W + 4, topPos + DronePortMenu.AUTO_Y,
                DronePortMenu.REGISTER_W, DronePortMenu.BUTTON_H};
    }

    /** 自動発送の段: しきい値のボタン（押すたびに次へ）・登録のボタン・登録した行き先。 */
    private void drawAuto(GuiGraphics g, int mouseX, int mouseY) {
        boolean free = !overPopup(mouseX, mouseY);
        int mode = Mth.clamp(menu.value(DronePortBlockEntity.D_AUTO), 0, DronePortBlockEntity.AUTO_THRESHOLDS.length - 1);
        int threshold = DronePortBlockEntity.AUTO_THRESHOLDS[mode];
        Component label = threshold == 0 ? Component.translatable("gui.singulo.drone_port.auto.off")
                : threshold == DronePortBlockEntity.AUTO_FULL ? Component.translatable("gui.singulo.drone_port.auto.full")
                : Component.translatable("gui.singulo.drone_port.auto.count", threshold);
        int[] a = autoRect();
        boolean ah = free && Panel.inside(mouseX, mouseY, a[0], a[1], a[2], a[3]);
        Panel.labelButton(g, font, skin, a[0], a[1], a[2], a[3], label, threshold != 0 ? Panel.Press.ON : ah ? Panel.Press.HOVER : Panel.Press.NORMAL);
        area(a[0] - leftPos, a[1] - topPos, a[2], a[3], List.of(Component.translatable("gui.singulo.drone_port.auto.hint")));
        int[] r = registerRect();
        boolean canRegister = menu.selected() >= 0;
        boolean rh = free && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        Panel.labelButton(g, font, skin, r[0], r[1], r[2], r[3], Component.translatable("gui.singulo.drone_port.auto.register"),
                !canRegister ? Panel.Press.DISABLED : rh ? Panel.Press.HOVER : Panel.Press.NORMAL);
        area(r[0] - leftPos, r[1] - topPos, r[2], r[3], List.of(Component.translatable("gui.singulo.drone_port.auto.register.hint")));
        int tx = r[0] + r[2] + 4;
        int tw = leftPos + DronePortMenu.LIST_X + DronePortMenu.LIST_W - tx;
        int target = menu.autoTarget();
        Component name = target >= 0 ? Component.translatable("gui.singulo.drone_port.auto.target", partnerName(target))
                : Component.translatable("gui.singulo.drone_port.auto.none");
        AndroidStationScreen.fit(g, font, name, tx, a[1] + 3, tw, target >= 0 ? skin.text() : skin.dim());
        area(tx - leftPos, a[1] - topPos, tw, a[3], List.of(name));
    }

    /** 輸送中の便: 向きの印・相手の名前・進みのバー（荷降ろしが済むまで）・段階の短い言葉。 */
    private void drawShipments(GuiGraphics g) {
        int x = leftPos + DronePortMenu.SHIP_X;
        int y = topPos + DronePortMenu.SHIP_Y;
        int h = DronePortMenu.SHIP_ROWS * DronePortMenu.SHIP_ROW + 2;
        Panel.well(g, skin, x, y, DronePortMenu.SHIP_W, h);
        boolean any = false;
        int nameW = 64, barX = x + 13 + nameW + 4, barW = DronePortMenu.SHIP_W - (barX - x) - 46;
        for (int row = 0; row < DronePortMenu.SHIP_ROWS; row++) {
            DronePortMenu.Ship s = menu.ship(row);
            if (s.phase() == 0) {
                continue;
            }
            any = true;
            int ry = y + 3 + row * DronePortMenu.SHIP_ROW;
            boolean out = s.dir() == DronePortMenu.DIR_OUT;
            Panel.icon(g, x + 3, ry, out ? Panel.Icon.OUT : Panel.Icon.IN, out ? 0xFF000000 | DroneStationScreen.ACCENT : GuiSkin.OK);
            Component name = partnerName(s.partner());
            AndroidStationScreen.fit(g, font, name, x + 13, ry, nameW, skin.text());
            Panel.well(g, skin, barX, ry + 1, barW, 6);
            int filled = Math.round(barW * Mth.clamp(s.permille(), 0, 1000) / 1000F);
            if (s.phase() == DronePortMenu.PHASE_WAITING || s.phase() == DronePortMenu.PHASE_COMING) {
                filled = 0;
            }
            if (filled > 0) {
                g.fill(barX, ry + 1, barX + filled, ry + 7, out ? 0xFF000000 | DroneStationScreen.ACCENT : GuiSkin.OK);
                Panel.glass(g, barX, ry + 1, filled, 6);
            }
            Component phase = Component.translatable("gui.singulo.drone_port.phase." + s.phase());
            AndroidStationScreen.fit(g, font, phase, barX + barW + 4, ry, 40, s.phase() == DronePortMenu.PHASE_WAITING ? GuiSkin.WARN : skin.dim());
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(out ? "gui.singulo.drone_port.ship.out" : "gui.singulo.drone_port.ship.in", name));
            tip.add(Component.translatable("gui.singulo.drone_port.phase." + s.phase()).withStyle(st -> st.withColor(0xFF8A949E)));
            if (s.phase() == DronePortMenu.PHASE_FLYING) {
                tip.add(Component.translatable("gui.singulo.drone_port.ship.progress", s.permille() / 10));
            }
            area(DronePortMenu.SHIP_X, DronePortMenu.SHIP_Y + 2 + row * DronePortMenu.SHIP_ROW, DronePortMenu.SHIP_W, DronePortMenu.SHIP_ROW, tip);
        }
        if (!any) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.drone_port.no_ships"), x + 2, y + (h - 8) / 2,
                    DronePortMenu.SHIP_W - 4, skin.dim());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!names.editing()) {
            AndroidStationScreen.fit(g, font, placeName(portName(), menu.pos()), titleLabelX, titleLabelY, titleWidth(), skin.text());
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
    }

    // ------------------------------------------------------------------ 操作

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (menu.page() == DronePortMenu.PAGE_SEND && !overPopup(mouseX, mouseY)
                && Panel.inside(mouseX, mouseY, leftPos + DronePortMenu.LIST_X, topPos + DronePortMenu.LIST_Y,
                DronePortMenu.LIST_W, DronePortMenu.ROWS * DronePortMenu.ROW_H)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, menu.targets().size() - DronePortMenu.ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (sides.drag(leftPos, topPos, mouseX, mouseY)) {
            return true;
        }
        if (sides.open && cubePress >= 0) {
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
                cubeDragged = true;
            }
            if (cubeDragged) {
                cube.rotate(dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sides.release()) {
            return true;
        }
        if (sides.open && cubePress >= 0 && button == cubePress) {
            if (!cubeDragged) {
                SideConfig.Face face = cube.pick(cubeQuads(), mouseX, mouseY);
                if (face != null) {
                    clickFace(face, button);
                }
            }
            cubePress = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layoutPopups();
        // 窓の中のクリックは下の画面に届かない
        if (sides.open && (sides.clickChrome(mouseX, mouseY) || sides.contains(mouseX, mouseY))) {
            if (sides.open && mouseY >= sides.bodyY()) {
                clickSides(mouseX, mouseY, button);
            }
            return true;
        }
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
        int[] r = resetRect();
        if (named() && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        if (overName(mouseX, mouseY)) {
            var box = names.open(font, leftPos + titleLabelX - 2, topPos + titleLabelY - 3, titleWidth() + 4, portName());
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        for (int p = 0; p < DronePortMenu.PAGES; p++) {
            int[] t = tabRect(p);
            if (Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3])) {
                menu.setPage(p);
                lastPage = p;
                return true;
            }
        }
        if (menu.page() != DronePortMenu.PAGE_SEND) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int lx = leftPos + DronePortMenu.LIST_X;
        int ly = topPos + DronePortMenu.LIST_Y;
        if (Panel.inside(mouseX, mouseY, lx, ly, DronePortMenu.LIST_W, DronePortMenu.ROWS * DronePortMenu.ROW_H)) {
            int i = scroll + (int) ((mouseY - ly) / DronePortMenu.ROW_H);
            if (i >= 0 && i < menu.targets().size()) {
                press(DronePortBlockEntity.BUTTON_SELECT + i);
                return true;
            }
        }
        int[] s = sendRect();
        if (Panel.inside(mouseX, mouseY, s[0], s[1], s[2], s[3])) {
            if (canSend()) {
                press(DronePortBlockEntity.BUTTON_SEND);
            }
            return true;
        }
        int[] c = cancelRect();
        if (Panel.inside(mouseX, mouseY, c[0], c[1], c[2], c[3])) {
            if (canCancel()) {
                press(DronePortBlockEntity.BUTTON_CANCEL);
            }
            return true;
        }
        int[] a = autoRect();
        if (Panel.inside(mouseX, mouseY, a[0], a[1], a[2], a[3])) {
            press(DronePortBlockEntity.BUTTON_AUTO);
            return true;
        }
        int[] rg = registerRect();
        if (Panel.inside(mouseX, mouseY, rg[0], rg[1], rg[2], rg[3])) {
            if (menu.selected() >= 0) {
                press(DronePortBlockEntity.BUTTON_AUTO_REGISTER);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
