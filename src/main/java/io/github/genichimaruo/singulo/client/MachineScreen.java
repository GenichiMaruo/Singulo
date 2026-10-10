package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineLayout;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.level.material.Fluid;

public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int MODE_SIZE = 16;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.layout.width;
        imageHeight = MachineLayout.HEIGHT;
        inventoryLabelX = menu.layout.inventoryX;
        inventoryLabelY = imageHeight - 94;
    }

    private MachineLayout layout() {
        return menu.layout;
    }

    private MachineType type() {
        return menu.type();
    }

    /** 面の設定の画面を開いているか。開いている間は装置の部分にかぶせて描く。 */
    private boolean sidesOpen;
    /** 0 はアイテム、1 以降はタンクごと（1 + タンクの番号、MachineBlockEntity.sideChannels）。 */
    private int sidesChannel;
    private static final int FACE = 18;
    /** 面の設定の、ドラッグで回せる立方体。 */
    private final SideCube cube = new SideCube();
    private static final float CUBE_HALF = 13;
    /** 立方体の上で押したボタンと位置（離したときに動かしていなければ、面をクリックしたことにする）。 */
    private int cubePress = -1;
    private double pressX;
    private double pressY;
    private boolean cubeDragged;

    private float cubeX() {
        return leftPos + layout().sideColumnX / 2F;
    }

    private float cubeY() {
        return topPos + 46;
    }

    private java.util.List<SideCube.Quad> cubeQuads() {
        return cube.visible(cubeX(), cubeY(), CUBE_HALF);
    }

    private static int modeColor(int mode) {
        return switch (mode) {
            case 1 -> 0xFF4A8FE0;        // 入力: 青
            case 2 -> 0xFFF0A040;        // 出力: 橙
            case 3 -> 0xFF50B870;        // 入出力: 緑
            default -> 0xFF9AA2AA;       // 無効: 灰
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (sidesOpen) {
            renderSides(g, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        renderOwnTooltips(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 面の設定

    /** 面のボタンの位置（十字の展開図。中央が正面）。 */
    private int[] facePos(io.github.genichimaruo.singulo.machine.SideConfig.Face f) {
        int cx = leftPos + layout().sideColumnX / 2 - FACE / 2;
        int cy = topPos + 36;
        return switch (f) {
            case FRONT -> new int[]{cx, cy};
            case TOP -> new int[]{cx, cy - FACE - 2};
            case BOTTOM -> new int[]{cx, cy + FACE + 2};
            case LEFT -> new int[]{cx - FACE - 2, cy};
            case RIGHT -> new int[]{cx + FACE + 2, cy};
            case BACK -> new int[]{cx + 2 * (FACE + 2), cy};
        };
    }

    /** 面の設定があるか。マルチブロックのコントローラにはない（入出力はマルチブロック搬入出ポートで行う）。 */
    private boolean hasSides() {
        return !type().isMultiblock();
    }

    /** タンクの色（画面の枠と、面の設定でそのタンクが使う面の色）。タンクの番号順。 */
    private static final int[] TANK_COLORS = {0xFF3F8FE6, 0xFFE8962E, 0xFF45B86A, 0xFFB45FD8};

    static int tankColor(int tank) {
        return TANK_COLORS[tank % TANK_COLORS.length];
    }

    /** 面の設定のタブ: アイテムと、タンクごと（入力1・出力1・出力2…）。 */
    private List<String> sideTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add(Component.translatable("gui.singulo.sides.items").getString());
        for (int t = 0; t < type().tanks(); t++) {
            boolean input = t < type().fluidInputs();
            int n = input ? t + 1 : t - type().fluidInputs() + 1;
            tabs.add(Component.translatable(input ? "gui.singulo.sides.tank_in" : "gui.singulo.sides.tank_out", n).getString());
        }
        return tabs;
    }

    /** 選んでいるタブのタンク（アイテムのタブなら -1）。 */
    private int channelTank() {
        return sidesChannel - 1;
    }

    private boolean channelIsInputTank() {
        return channelTank() >= 0 && channelTank() < type().fluidInputs();
    }

    /** 面のモードの次の値。アイテムは4つを巡り、入力タンクは「無効・入力」、出力タンクは「無効・出力」を切り替える。 */
    private int nextMode(int mode) {
        if (channelTank() < 0) {
            return (mode + 1) % 4;
        }
        int on = channelIsInputTank() ? io.github.genichimaruo.singulo.machine.SideConfig.INPUT : io.github.genichimaruo.singulo.machine.SideConfig.OUTPUT;
        return mode == on ? 0 : on;
    }

    private int tabWidth(String label, int index) {
        return font.width(label) + 8 + (index > 0 ? 6 : 0);
    }

    /** 面の色: アイテムはモードの色、タンクは使う面だけそのタンクの色。 */
    private int faceColor(int mode) {
        if (channelTank() < 0) {
            return modeColor(mode);
        }
        return mode != 0 ? tankColor(channelTank()) : modeColor(0);
    }

    private int flags() {
        return menu.value(MachineBlockEntity.D_FLAGS);
    }

    /** 電源スイッチと、材料なしのレシピのスイッチ（右の列の下）。 */
    private int[] powerPos() {
        return new int[]{leftPos + layout().sideColumnX + 2, topPos + 60, 10, 10};
    }

    private int[] freePos() {
        return new int[]{leftPos + layout().sideColumnX + 13, topPos + 60, 10, 10};
    }

    private boolean showsFreeSwitch() {
        return (flags() & MachineBlockEntity.FLAG_HAS_FREE) != 0;
    }

    private void drawSwitches(GuiGraphics g, int mouseX, int mouseY) {
        int[] p = powerPos();
        Panel.powerButton(g, p[0], p[1], (flags() & MachineBlockEntity.FLAG_ENABLED) != 0,
                Panel.inside(mouseX, mouseY, p[0], p[1], p[2], p[3]));
        if (!showsFreeSwitch()) {
            return;
        }
        int c = 0xFFFFFFFF;
        boolean free = (flags() & MachineBlockEntity.FLAG_MAKE_FREE) != 0;
        int[] f = freePos();
        boolean fh = Panel.inside(mouseX, mouseY, f[0], f[1], f[2], f[3]);
        g.fill(f[0] - 1, f[1] - 1, f[0] + f[2] + 1, f[1] + f[3] + 1, Panel.WELL_EDGE);
        g.fill(f[0], f[1], f[0] + f[2], f[1] + f[3], free ? (fh ? Panel.GLOW : 0xFF3A9CC0) : (fh ? 0xFFB0B8C0 : 0xFF9AA2AA));
        // 稲妻の印（電力だけで作る）。作らないときは斜線を重ねる
        g.fill(f[0] + 5, f[1] + 1, f[0] + 7, f[1] + 3, c);
        g.fill(f[0] + 4, f[1] + 3, f[0] + 6, f[1] + 5, c);
        g.fill(f[0] + 3, f[1] + 5, f[0] + 7, f[1] + 6, c);
        g.fill(f[0] + 5, f[1] + 6, f[0] + 7, f[1] + 7, c);
        g.fill(f[0] + 4, f[1] + 7, f[0] + 6, f[1] + 9, c);
        if (!free) {
            for (int i = 0; i < 9; i++) {
                g.fill(f[0] + 1 + i, f[1] + 8 - i, f[0] + 2 + i, f[1] + 9 - i, 0xFFB05050);
            }
        }
    }

    /** 面の設定ボタンの左端（右の列の中央）。 */
    private int sidesButtonX() {
        return layout().sideColumnX + (MachineLayout.SIDE_COLUMN - MachineLayout.SIDES_SIZE) / 2;
    }

    /** 自動排出の全体スイッチの位置（設定の右下）。 */
    private int[] masterPos() {
        String text = Component.translatable("gui.singulo.sides.master_on").getString();
        int w = font.width(text) + 8;
        return new int[]{leftPos + layout().sideColumnX - 8 - w, topPos + 14 + 3, w, 11};
    }

    private int sidesPacked() {
        return menu.value(MachineBlockEntity.sidesIndex(type()) + sidesChannel);
    }

    private void renderSides(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int x = leftPos + 4;
        int y = topPos + 14;
        int w = layout().sideColumnX - 8;
        int h = 66;
        g.fill(x, y, x + w, y + h, 0xF0E9EDF0);
        g.fill(x, y, x + w, y + 1, Panel.SEAM);
        // タブ（タンクのタブはそのタンクの色。選んでいなければ左に色の印）
        List<String> tabs = sideTabs();
        int tx = x + 4;
        for (int i = 0; i < tabs.size(); i++) {
            int tw = tabWidth(tabs.get(i), i);
            int color = i == 0 ? 0xFF2A6F8A : tankColor(i - 1);
            g.fill(tx, y + 3, tx + tw, y + 14, i == sidesChannel ? color : Panel.SHADE);
            int text = tx + 4;
            if (i > 0) {
                g.fill(tx + 3, y + 5, tx + 7, y + 12, color);
                text += 6;
            }
            g.drawString(font, tabs.get(i), text, y + 5, i == sidesChannel ? 0xFFFFFFFF : Panel.TEXT, false);
            tx += tw + 2;
        }
        int packed = sidesPacked();
        var quads = cubeQuads();
        io.github.genichimaruo.singulo.machine.SideConfig.Face hovered = cube.pick(quads, mouseX, mouseY);
        cube.draw(g, font, quads, f -> faceColor(io.github.genichimaruo.singulo.machine.SideConfig.mode(packed, f)),
                f -> io.github.genichimaruo.singulo.machine.SideConfig.eject(packed, f),
                f -> Component.translatable("gui.singulo.sides.face." + f.name().toLowerCase()).getString().substring(0, 1), hovered);
        // 自動排出の全体スイッチ（入力タンクにはない）
        boolean master = io.github.genichimaruo.singulo.machine.SideConfig.ejectEnabled(packed);
        if (!channelIsInputTank()) {
            int[] mp = masterPos();
            boolean mh = Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3]);
            g.fill(mp[0], mp[1], mp[0] + mp[2], mp[1] + mp[3], master ? (mh ? 0xFF6ACB8A : 0xFF50B870) : (mh ? 0xFFB0B8C0 : 0xFF9AA2AA));
            g.drawString(font, Component.translatable(master ? "gui.singulo.sides.master_on" : "gui.singulo.sides.master_off"),
                    mp[0] + 4, mp[1] + 2, 0xFFFFFFFF, false);
        }
        // 凡例
        int ly = y + h - 10;
        String legend = channelTank() < 0 ? "gui.singulo.sides.legend"
                : channelIsInputTank() ? "gui.singulo.sides.legend_tank_in" : "gui.singulo.sides.legend_tank_out";
        g.drawString(font, Component.translatable(legend), x + 4, ly, Panel.TEXT, false);
        g.pose().popPose();
        if (hovered != null) {
            int mode = io.github.genichimaruo.singulo.machine.SideConfig.mode(packed, hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.singulo.sides.face." + hovered.name().toLowerCase()));
            lines.add(Component.translatable("gui.singulo.sides.mode." + mode));
            lines.add(Component.translatable(io.github.genichimaruo.singulo.machine.SideConfig.eject(packed, hovered)
                    ? "gui.singulo.sides.eject_on" : "gui.singulo.sides.eject_off"));
            lines.add(Component.translatable("gui.singulo.sides.hint").withStyle(s -> s.withColor(0xFF8A949E)));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private boolean clickSides(double mouseX, double mouseY, int button) {
        int x = leftPos + 4;
        int y = topPos + 14;
        int tx = x + 4;
        List<String> tabs = sideTabs();
        for (int i = 0; i < tabs.size(); i++) {
            int tw = tabWidth(tabs.get(i), i);
            if (Panel.inside(mouseX, mouseY, tx, y + 3, tw, 11)) {
                sidesChannel = i;
                return true;
            }
            tx += tw + 2;
        }
        int packed = sidesPacked();
        int[] mp = masterPos();
        if (!channelIsInputTank() && Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3])) {
            boolean master = io.github.genichimaruo.singulo.machine.SideConfig.ejectEnabled(packed);
            io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new io.github.genichimaruo.singulo.network.SideConfigPayload(
                    menu.pos(), sidesChannel, io.github.genichimaruo.singulo.network.SideConfigPayload.MASTER_FACE, master ? 0 : 4));
            return true;
        }
        // 立方体の上で押した: 離すまで待つ（動かしたら回すだけ、動かさなければ面をクリック）
        if (Math.abs(mouseX - cubeX()) < CUBE_HALF * 2 && Math.abs(mouseY - cubeY()) < CUBE_HALF * 2) {
            cubePress = button;
            pressX = mouseX;
            pressY = mouseY;
            cubeDragged = false;
            return true;
        }
        return false;
    }

    /** 立方体の面をクリックした: 左は入出力の切り替え、右は自動排出の切り替え。 */
    private void clickFace(io.github.genichimaruo.singulo.machine.SideConfig.Face f, int button) {
        int packed = sidesPacked();
        int mode = io.github.genichimaruo.singulo.machine.SideConfig.mode(packed, f);
        boolean eject = io.github.genichimaruo.singulo.machine.SideConfig.eject(packed, f);
        if (button == 1) {
            if (channelIsInputTank()) {
                return;                                            // 入力タンクに自動排出はない
            }
            eject = !eject;
        } else {
            mode = nextMode(mode);
        }
        io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new io.github.genichimaruo.singulo.network.SideConfigPayload(
                menu.pos(), sidesChannel, f.ordinal(), mode | (eject ? 4 : 0)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (sidesOpen && cubePress >= 0) {
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
        if (sidesOpen && cubePress >= 0 && button == cubePress) {
            if (!cubeDragged) {
                var face = cube.pick(cubeQuads(), mouseX, mouseY);
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
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panel.background(g, x, y, imageWidth, imageHeight);
        MachineLayout l = layout();

        int energy = menu.value(MachineBlockEntity.D_ENERGY);
        int capacity = Math.max(1, menu.value(MachineBlockEntity.D_CAPACITY));
        Panel.verticalBar(g, x + MachineLayout.ENERGY_X, y + MachineLayout.TOP, 8, MachineLayout.BAR_HEIGHT,
                (double) energy / capacity, Panel.GLOW);

        for (int i = 0; i < l.fluidInX.length; i++) {
            drawTank(g, x + l.fluidInX[i], y + MachineLayout.TOP, i);
        }
        for (int i = 0; i < l.fluidOutX.length; i++) {
            drawTank(g, x + l.fluidOutX[i], y + MachineLayout.TOP, type().fluidInputs() + i);
        }
        for (int i = 0; i < type().inputSlots(); i++) {
            Panel.slot(g, x + l.inputX[i], y + l.inputY[i]);
        }
        for (int i = 0; i < type().outputSlots(); i++) {
            Panel.slot(g, x + l.outputX, y + l.outputY[i]);
        }
        // 右端の列: 単極子アップグレードと面の設定ボタン
        g.fill(x + l.sideColumnX, y + 14, x + l.sideColumnX + 1, y + 76, Panel.SEAM);
        drawSpecialSlot(g, x + l.sideColumnX + 4, y + MachineLayout.UPGRADE_Y, 0xFFB48CFF, type().upgradeSlot(), "monopole_upgrade");
        if (hasSides()) {
            int sbx = x + sidesButtonX();
            int sby = y + MachineLayout.SIDES_Y;
            int bs = MachineLayout.SIDES_SIZE;
            boolean hover = Panel.inside(mouseX, mouseY, sbx, sby, bs, bs);
            g.fill(sbx - 1, sby - 1, sbx + bs + 1, sby + bs + 1, Panel.WELL_EDGE);
            g.fill(sbx, sby, sbx + bs, sby + bs, sidesOpen ? 0xFF2A6F8A : hover ? Panel.GLOW : Panel.SHADE);
            // 立方体の展開図の小さなアイコン
            int ic = sidesOpen ? 0xFFFFFFFF : Panel.TEXT;
            g.fill(sbx + 4, sby + 2, sbx + 6, sby + 4, ic);
            g.fill(sbx + 2, sby + 4, sbx + 8, sby + 6, ic);
            g.fill(sbx + 4, sby + 6, sbx + 6, sby + 8, ic);
        }
        drawSwitches(g, mouseX, mouseY);
        if (type().hasCatalystSlot()) {
            drawSpecialSlot(g, x + l.catalystX, y + l.catalystY, Panel.AMBER, type().catalystSlot(), "time_crystal_catalyst");
        }
        int progress = menu.value(MachineBlockEntity.D_PROGRESS);
        int max = Math.max(1, menu.value(MachineBlockEntity.D_MAX_PROGRESS));
        Panel.arrow(g, x + l.arrowX, y + MachineLayout.ARROW_Y, (double) progress / max);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, x + l.inventoryX + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, x + l.inventoryX + col * 18, y + 142);
        }

        if (type().massMode()) {
            int bx = x + l.modeX;
            int by = y + MachineLayout.MODE_Y;
            Panel.well(g, bx, by, MODE_SIZE, MODE_SIZE);
            g.renderItem(modeIcon(), bx, by);
            int target = menu.value(MachineBlockEntity.D_MASS_TARGET);
            if (target > 0) {
                Panel.well(g, bx + MODE_SIZE + 4, by + 5, 48, 6);
                double f = (double) menu.value(MachineBlockEntity.D_MASS) / target;
                g.fill(bx + MODE_SIZE + 4, by + 5, bx + MODE_SIZE + 4 + (int) Math.round(48 * Math.min(1, f)), by + 11, Panel.GLOW);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        MachineBlockEntity.Status status = MachineBlockEntity.Status.values()[menu.value(MachineBlockEntity.D_STATUS)];
        Component text = Component.translatable("gui.singulo.status." + status.name().toLowerCase());
        int color = switch (status) {
            case RUNNING -> 0xFF3A9CC0;
            case NOT_FORMED -> 0xFFC05050;
            case IDLE, OFF -> 0xFF8A949E;
            default -> 0xFFC08020;
        };
        g.drawString(font, text, imageWidth - 8 - font.width(text), titleLabelY, color, false);
        int size = menu.value(MachineBlockEntity.D_STRUCTURE);
        if (size > 0) {
            Component info = Component.translatable("gui.singulo.structure." + type().id(), size);
            g.drawString(font, info, imageWidth - 8 - font.width(info), inventoryLabelY, Panel.TEXT, false);
        }
    }

    private void drawTank(GuiGraphics g, int x, int y, int tank) {
        int fluidId = menu.value(MachineBlockEntity.D_TANKS + tank * 2);
        int amount = menu.value(MachineBlockEntity.D_TANKS + tank * 2 + 1);
        if (hasSides()) {
            // 面の設定のタブと同じ色の枠（このタンクがどの面を使うかを色で対応させる）
            g.fill(x - 2, y - 2, x + MachineLayout.TANK_WIDTH + 2, y + MachineLayout.BAR_HEIGHT + 2, tankColor(tank));
        }
        if (fluidId > 0) {
            Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId - 1);
            FluidGauge.draw(g, x, y, MachineLayout.TANK_WIDTH, MachineLayout.BAR_HEIGHT,
                    (double) amount / MachineType.TANK_CAPACITY, fluid);
        } else {
            Panel.well(g, x, y, MachineLayout.TANK_WIDTH, MachineLayout.BAR_HEIGHT);
        }
    }

    private List<MachineRecipe> massRecipes() {
        return Minecraft.getInstance().level == null ? List.of()
                : MachineBlockEntity.massRecipes(Minecraft.getInstance().level, type());
    }

    private ItemStack modeIcon() {
        int mode = menu.value(MachineBlockEntity.D_MODE);
        List<MachineRecipe> list = massRecipes();
        if (mode > 0 && mode <= list.size()) {
            return list.get(mode - 1).value().result();
        }
        return new ItemStack(Items.PISTON);
    }

    private void renderOwnTooltips(GuiGraphics g, int mouseX, int mouseY) {
        MachineLayout l = layout();
        int x = leftPos;
        int y = topPos;
        if (hasSides() && Panel.inside(mouseX, mouseY, x + sidesButtonX(), y + MachineLayout.SIDES_Y, MachineLayout.SIDES_SIZE,
                MachineLayout.SIDES_SIZE)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.sides.button"), mouseX, mouseY);
            return;
        }
        int[] pp = powerPos();
        if (Panel.inside(mouseX, mouseY, pp[0], pp[1], pp[2], pp[3])) {
            g.renderTooltip(font, Component.translatable((flags() & MachineBlockEntity.FLAG_ENABLED) != 0
                    ? "gui.singulo.power.on" : "gui.singulo.power.off"), mouseX, mouseY);
            return;
        }
        int[] fp = freePos();
        if (showsFreeSwitch() && Panel.inside(mouseX, mouseY, fp[0], fp[1], fp[2], fp[3])) {
            g.renderTooltip(font, Component.translatable((flags() & MachineBlockEntity.FLAG_MAKE_FREE) != 0
                    ? "gui.singulo.make_free.on" : "gui.singulo.make_free.off"), mouseX, mouseY);
            return;
        }
        if (menu.getSlot(type().upgradeSlot()).getItem().isEmpty()
                && Panel.inside(mouseX, mouseY, x + l.sideColumnX + 4, y + MachineLayout.UPGRADE_Y, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.slot.upgrade"), mouseX, mouseY);
            return;
        }
        if (type().hasCatalystSlot() && menu.getSlot(type().catalystSlot()).getItem().isEmpty()
                && Panel.inside(mouseX, mouseY, x + l.catalystX, y + l.catalystY, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.slot.catalyst"), mouseX, mouseY);
            return;
        }
        if (Panel.inside(mouseX, mouseY, x + MachineLayout.ENERGY_X, y + MachineLayout.TOP, 8, MachineLayout.BAR_HEIGHT)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.singulo.energy",
                    menu.value(MachineBlockEntity.D_ENERGY), menu.value(MachineBlockEntity.D_CAPACITY)));
            int usage = menu.value(MachineBlockEntity.D_USAGE);
            if (usage > 0) {
                lines.add(Component.translatable("gui.singulo.energy_rate", usage));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < type().tanks(); i++) {
            int tx = i < type().fluidInputs() ? l.fluidInX[i] : l.fluidOutX[i - type().fluidInputs()];
            if (Panel.inside(mouseX, mouseY, x + tx, y + MachineLayout.TOP, MachineLayout.TANK_WIDTH, MachineLayout.BAR_HEIGHT)) {
                int fluidId = menu.value(MachineBlockEntity.D_TANKS + i * 2);
                int amount = menu.value(MachineBlockEntity.D_TANKS + i * 2 + 1);
                Component name = fluidId > 0
                        ? BuiltInRegistries.FLUID.byId(fluidId - 1).getFluidType().getDescription()
                        : Component.translatable("gui.singulo.empty_tank");
                g.renderTooltip(font, Component.translatable("gui.singulo.fluid", name, amount, MachineType.TANK_CAPACITY),
                        mouseX, mouseY);
                return;
            }
        }
        if (type().massMode() && Panel.inside(mouseX, mouseY, x + l.modeX, y + MachineLayout.MODE_Y, MODE_SIZE, MODE_SIZE)) {
            int mode = menu.value(MachineBlockEntity.D_MODE);
            List<Component> lines = new ArrayList<>();
            List<MachineRecipe> list = massRecipes();
            lines.add(mode > 0 && mode <= list.size()
                    ? Component.translatable("gui.singulo.mode.mass", list.get(mode - 1).value().result().getHoverName())
                    : Component.translatable("gui.singulo.mode.normal"));
            int target = menu.value(MachineBlockEntity.D_MASS_TARGET);
            if (target > 0) {
                lines.add(Component.translatable("gui.singulo.mass_buffer",
                        String.format("%.2f", menu.value(MachineBlockEntity.D_MASS) / 100.0),
                        String.format("%.0f", target / 100.0)));
            }
            lines.add(Component.translatable("gui.singulo.mode.hint").withStyle(s -> s.withColor(0xFF8A949E)));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** 専用スロット: 色つきの枠と、空のときは薄い見本のアイコン。 */
    private void drawSpecialSlot(GuiGraphics g, int sx, int sy, int frame, int slotIndex, String ghost) {
        g.fill(sx - 2, sy - 2, sx + 18, sy + 18, frame);
        Panel.slot(g, sx, sy);
        if (menu.getSlot(menuIndexOf(slotIndex)).getItem().isEmpty()) {
            g.pose().pushPose();
            g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(ghost))), sx, sy);
            g.pose().translate(0, 0, 200);
            g.fill(sx, sy, sx + 16, sy + 16, 0xA039424A);
            g.pose().popPose();
        }
    }

    /** 装置のスロット番号から、メニュー上の番号へ（入力・出力・触媒・アップグレードの順に並んでいる）。 */
    private int menuIndexOf(int slotIndex) {
        return slotIndex;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasSides() && Panel.inside(mouseX, mouseY, leftPos + sidesButtonX(), topPos + MachineLayout.SIDES_Y,
                MachineLayout.SIDES_SIZE, MachineLayout.SIDES_SIZE)) {
            sidesOpen = !sidesOpen;
            return true;
        }
        if (sidesOpen) {
            if (clickSides(mouseX, mouseY, button)) {
                return true;
            }
            if (mouseY < topPos + 80) {
                return true;                                       // 設定中は装置のスロットを触らない
            }
        }
        if (!sidesOpen && minecraft != null && minecraft.gameMode != null) {
            int[] pp = powerPos();
            if (Panel.inside(mouseX, mouseY, pp[0], pp[1], pp[2], pp[3])) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_POWER);
                return true;
            }
            int[] fp = freePos();
            if (showsFreeSwitch() && Panel.inside(mouseX, mouseY, fp[0], fp[1], fp[2], fp[3])) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_MAKE_FREE);
                return true;
            }
        }
        if (type().massMode() && Panel.inside(mouseX, mouseY, leftPos + layout().modeX, topPos + MachineLayout.MODE_Y,
                MODE_SIZE, MODE_SIZE) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_CYCLE_MODE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
