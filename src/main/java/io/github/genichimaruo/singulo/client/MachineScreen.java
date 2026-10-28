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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;

public class MachineScreen extends FramedScreen<MachineMenu> {
    private static final int MODE_SIZE = 16;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout.width, MachineLayout.HEIGHT);
    }

    private MachineLayout layout() {
        return menu.layout;
    }

    private MachineType type() {
        return menu.type();
    }

    /** この画面の装置（JEI の連携用）。 */
    public MachineType machineType() {
        return type();
    }

    /** 進捗の矢印の範囲（画面の左上からの位置。JEI の連携で、クリックするとレシピを出す）。 */
    public net.minecraft.client.renderer.Rect2i arrowArea() {
        return new net.minecraft.client.renderer.Rect2i(layout().arrowX, layout().arrowY, 22, 16);
    }

    /** 待機している理由（待機中だけ、何 tick かごとに調べ直す）。 */
    @javax.annotation.Nullable
    private io.github.genichimaruo.singulo.machine.MachineDiagnosis.Result idleReason;
    private int reasonTimer;

    private MachineBlockEntity.Status machineStatus() {
        return MachineBlockEntity.Status.values()[menu.value(MachineBlockEntity.D_STATUS)];
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (machineStatus() != MachineBlockEntity.Status.IDLE || menu.value(MachineBlockEntity.D_MODE) > 0 || Minecraft.getInstance().level == null) {
            idleReason = null;
            return;
        }
        if (idleReason != null && reasonTimer++ % 5 != 0) {
            return;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < type().recipeSlots(); i++) {
            inputs.add(menu.getSlot(type().recipeSlot(i)).getItem());
        }
        List<net.neoforged.neoforge.fluids.FluidStack> tanks = new ArrayList<>();
        for (int i = 0; i < type().tanks(); i++) {
            int fluidId = menu.value(MachineBlockEntity.D_TANKS + i * 2);
            tanks.add(fluidId > 0 ? new net.neoforged.neoforge.fluids.FluidStack(BuiltInRegistries.FLUID.byId(fluidId - 1),
                    menu.value(MachineBlockEntity.D_TANKS + i * 2 + 1)) : net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        }
        idleReason = io.github.genichimaruo.singulo.machine.MachineDiagnosis.idle(Minecraft.getInstance().level, type(), inputs, tanks,
                menu.value(MachineBlockEntity.D_STRUCTURE), type().selectsRecipe() ? selectedHolder() : null);
    }

    /** ヘッダー右の状態。待機中で理由が分かれば、理由を短く出す。 */
    @Override
    protected Component statusText() {
        MachineBlockEntity.Status status = machineStatus();
        if (status == MachineBlockEntity.Status.IDLE && idleReason != null
                && idleReason.kind() != io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.EMPTY) {
            return Component.translatable("gui.singulo.reason.label." + idleReason.kind().name().toLowerCase());
        }
        return Component.translatable("gui.singulo.status." + status.name().toLowerCase());
    }

    @Override
    protected int statusColor() {
        boolean waiting = idleReason == null || idleReason.kind() == io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.EMPTY;
        return switch (machineStatus()) {
            case RUNNING -> GuiSkin.OK;
            case NOT_FORMED -> GuiSkin.BAD;
            case OFF -> GuiSkin.IDLE;
            case IDLE -> waiting ? GuiSkin.IDLE : GuiSkin.WARN;
            default -> GuiSkin.WARN;
        };
    }

    @Override
    protected Panel.Status status() {
        boolean waiting = idleReason == null || idleReason.kind() == io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.EMPTY;
        return switch (machineStatus()) {
            case RUNNING -> Panel.Status.RUN;
            case IDLE -> waiting ? Panel.Status.IDLE : Panel.Status.WARN;
            case NO_POWER -> Panel.Status.NO_POWER;
            case OUTPUT_FULL -> Panel.Status.FULL;
            case NOT_FORMED -> Panel.Status.BROKEN;
            case OFF -> Panel.Status.OFF;
        };
    }

    /** 状態に重ねたときの説明（どうすれば動くか）。 */
    @Override
    protected List<Component> statusTooltip() {
        return switch (machineStatus()) {
            case IDLE -> idleReason == null ? List.of() : idleReason.lines();
            case NO_POWER -> List.of(Component.translatable("gui.singulo.reason.no_power", io.github.genichimaruo.singulo.energy.EnergyText.of(menu.value(MachineBlockEntity.D_USAGE)),
                    io.github.genichimaruo.singulo.energy.EnergyText.of(menu.value(MachineBlockEntity.D_ENERGY))));
            case OUTPUT_FULL -> List.of(Component.translatable("gui.singulo.reason.output_full"));
            case NOT_FORMED -> List.of(Component.translatable("gui.singulo.reason.not_formed"));
            case OFF -> List.of(Component.translatable("gui.singulo.reason.off"));
            case RUNNING -> List.of();
        };
    }

    /** 面の設定の窓。 */
    private final Popup sides = new Popup("machine.sides");
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
        return sides.x + sides.w / 2F;
    }

    private float cubeY() {
        return sides.bodyY() + OVERLAY_H / 2F;
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
        layoutPopups();
        super.render(g, mouseX, mouseY, partialTick);
        if (sides.open) {
            renderSides(g, mouseX, mouseY);
        }
        if (selector.open) {
            renderSelector(g, mouseX, mouseY);
        }
        if (!overPopup(mouseX, mouseY)) {
            // 窓より手前に出す
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            renderTooltip(g, mouseX, mouseY);
            renderOwnTooltips(g, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    @Override
    protected List<Popup> popups() {
        return List.of(selector, sides);
    }

    /** 窓の大きさと位置。初めて開くときは、下端を装置の部分の下端（インベントリの見出しの上）に合わせる。 */
    private void layoutPopups() {
        int bottom = io.github.genichimaruo.singulo.machine.GuiFrame.contentBottom(imageHeight);
        int sh = Popup.BAR + 1 + OVERLAY_H;
        sides.layout(leftPos, topPos, imageWidth - 8, sh, 4, bottom - sh, width, height);
        int ph = Popup.BAR + 1 + selectRows() * 18 + 8;
        selector.layout(leftPos, topPos, imageWidth - 8, ph, 4, bottom - ph, width, height);
    }

    // ------------------------------------------------------------------ 面の設定

    /** 面のボタンの位置（十字の展開図。中央が正面）。 */
    private int[] facePos(io.github.genichimaruo.singulo.machine.SideConfig.Face f) {
        int cx = leftPos + imageWidth / 2 - FACE / 2;
        int cy = topPos + 38;
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

    /** 面の設定のタブ（板の左に縦に並ぶ記号のボタン）。 */
    private int[] tabRect(int index) {
        return new int[]{sides.x + 4, sides.bodyY() + 4 + index * 15, 13, 13};
    }

    /** 凡例の色見本（板の右に縦に並ぶ。重ねると意味が出る）。 */
    private int[] legendRect(int index) {
        return new int[]{sides.x + sides.w - 15, sides.bodyY() + 20 + index * 11, 9, 9};
    }

    /** 今のタブで選べるモード（アイテムは4つ、タンクは無効と入力か出力）。 */
    private int[] legendModes() {
        if (channelTank() < 0) {
            return new int[]{0, 1, 2, 3};
        }
        return new int[]{0, channelIsInputTank() ? io.github.genichimaruo.singulo.machine.SideConfig.INPUT : io.github.genichimaruo.singulo.machine.SideConfig.OUTPUT};
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

    @Override
    protected Boolean powered() {
        return (flags() & MachineBlockEntity.FLAG_ENABLED) != 0;
    }

    @Override
    protected void togglePower() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_POWER);
        }
    }

    /** 材料なしのレシピのスイッチ（ヘッダーの電源の左）。 */
    private int[] freePos() {
        return new int[]{leftPos + io.github.genichimaruo.singulo.machine.GuiFrame.powerX(imageWidth) - 13,
                topPos + io.github.genichimaruo.singulo.machine.GuiFrame.POWER_Y, 11, 11};
    }

    private boolean showsFreeSwitch() {
        return (flags() & MachineBlockEntity.FLAG_HAS_FREE) != 0;
    }

    @Override
    protected int headerReserved() {
        return showsFreeSwitch() ? 13 : 0;
    }

    /** 稲妻の印（電力だけで作る）。作らないときは斜線を重ねる。 */
    private void drawFreeSwitch(GuiGraphics g, int mouseX, int mouseY) {
        if (!showsFreeSwitch()) {
            return;
        }
        boolean free = (flags() & MachineBlockEntity.FLAG_MAKE_FREE) != 0;
        int[] f = freePos();
        boolean hover = Panel.inside(mouseX, mouseY, f[0], f[1], f[2], f[3]);
        int d = Panel.button(g, skin, f[0], f[1], f[2], f[3], free ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.boltIcon(g, f[0] + 2, f[1] + 2 + d, free ? skin.onAccent() : skin.text());
        if (!free) {
            for (int i = 0; i < 7; i++) {
                g.fill(f[0] + 2 + i, f[1] + 8 - i + d, f[0] + 3 + i, f[1] + 9 - i + d, 0xFFD85050);
            }
        }
    }

    // ------------------------------------------------------------------ 右レール

    @Override
    protected boolean railUsed(int row) {
        return row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_UPGRADE
                || row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_SETTINGS && hasSides();
    }

    @Override
    protected boolean railConnected(int row) {
        return row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_UPGRADE || sides.open;
    }

    @Override
    protected void drawRail(GuiGraphics g, int row, int x, int y, boolean hover) {
        if (row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_SETTINGS) {
            Panel.sidesIcon(g, x, y);
            return;
        }
        drawSpecialSlot(g, leftPos + layout().upgradeX, topPos + layout().upgradeY, 0, type().upgradeSlot(), "monopole_upgrade");
    }

    @Override
    protected boolean clickRail(int row, int button) {
        if (row != io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_SETTINGS) {
            return false;
        }
        sides.open = !sides.open;
        selector.open = false;
        return true;
    }

    @Override
    protected Component railTooltip(int row) {
        if (row == io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_SETTINGS) {
            return Component.translatable("gui.singulo.sides.button");
        }
        return menu.getSlot(type().upgradeSlot()).getItem().isEmpty() ? Component.translatable("gui.singulo.slot.upgrade") : null;
    }

    /** 自動排出の全体スイッチの位置（板の右上）。 */
    private int[] masterPos() {
        return new int[]{sides.x + sides.w - 17, sides.bodyY() + 4, 13, 13};
    }

    /** 面の設定の窓の中身の高さ。 */
    private static final int OVERLAY_H = 66;

    private int sidesPacked() {
        return menu.value(MachineBlockEntity.sidesIndex(type()) + sidesChannel);
    }

    private void renderSides(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        sides.draw(g, skin, mouseX, mouseY);
        // タブ: アイテムは箱、タンクはそのタンクの色のしずく（入れるタンクは下向き、出すタンクは上向きの矢印）
        int tabs = sideTabs().size();
        for (int i = 0; i < tabs; i++) {
            int[] t = tabRect(i);
            boolean on = i == sidesChannel;
            boolean hover = Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3]);
            int d = Panel.button(g, skin, t[0], t[1], t[2], t[3], on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            if (i == 0) {
                Panel.boxIcon(g, t[0] + 2, t[1] + 3 + d, on ? skin.onAccent() : skin.text());
            } else {
                Panel.dropIcon(g, t[0] + 3, t[1] + 2 + d, tankColor(i - 1), i - 1 < type().fluidInputs());
            }
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
            int d = Panel.button(g, skin, mp[0], mp[1], mp[2], mp[3], master ? Panel.Press.ON : mh ? Panel.Press.HOVER : Panel.Press.NORMAL);
            Panel.ejectIcon(g, mp[0] + 2, mp[1] + 2 + d, master ? skin.onAccent() : skin.text());
        }
        // 凡例: 色見本だけ（重ねるとモードの名前）
        int[] modes = legendModes();
        for (int i = 0; i < modes.length; i++) {
            int[] r = legendRect(i);
            Panel.inset(g, skin, r[0], r[1], r[2], r[3], faceColor(modes[i]));
        }
        List<Component> tip = sidesTooltip(mouseX, mouseY, master);
        if (!tip.isEmpty()) {
            Panel.tooltip(g, font, tip, 200, mouseX, mouseY);
        }
        if (hovered != null) {
            int mode = io.github.genichimaruo.singulo.machine.SideConfig.mode(packed, hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.singulo.sides.face." + hovered.name().toLowerCase()));
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var state = level.getBlockState(menu.pos());
                var facing = state.hasProperty(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING)
                        ? state.getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING)
                        : net.minecraft.core.Direction.NORTH;
                var direction = io.github.genichimaruo.singulo.machine.SideConfig.directionOf(facing, hovered);
                var adjacent = menu.pos().relative(direction);
                lines.add(Component.translatable("gui.singulo.sides.adjacent", level.getBlockState(adjacent).getBlock().getName()));
            }
            lines.add(Component.translatable("gui.singulo.sides.mode." + mode));
            lines.add(Component.translatable(io.github.genichimaruo.singulo.machine.SideConfig.eject(packed, hovered)
                    ? "gui.singulo.sides.eject_on" : "gui.singulo.sides.eject_off"));
            if (!channelIsInputTank()) lines.add(Component.translatable(master ? "gui.singulo.sides.master_on" : "gui.singulo.sides.master_off"));
            lines.add(Component.translatable("gui.singulo.sides.hint").withStyle(s -> s.withColor(0xFF8A949E)));
            Panel.tooltip(g, font, lines, 230, mouseX, mouseY);
        }
        g.pose().popPose();
    }

    /** 面の設定の板の記号に重ねたときの名前。 */
    private List<Component> sidesTooltip(int mouseX, int mouseY, boolean master) {
        List<String> tabs = sideTabs();
        for (int i = 0; i < tabs.size(); i++) {
            int[] t = tabRect(i);
            if (Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3])) {
                return List.of(Component.literal(tabs.get(i)));
            }
        }
        int[] mp = masterPos();
        if (!channelIsInputTank() && Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3])) {
            return List.of(Component.translatable(master ? "gui.singulo.sides.master_on" : "gui.singulo.sides.master_off"));
        }
        int[] modes = legendModes();
        for (int i = 0; i < modes.length; i++) {
            int[] r = legendRect(i);
            if (Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                return List.of(Component.translatable("gui.singulo.sides.mode." + modes[i]));
            }
        }
        return List.of();
    }

    private boolean clickSides(double mouseX, double mouseY, int button) {
        List<String> tabs = sideTabs();
        for (int i = 0; i < tabs.size(); i++) {
            int[] t = tabRect(i);
            if (Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3])) {
                sidesChannel = i;
                return true;
            }
        }
        int packed = sidesPacked();
        int[] mp = masterPos();
        if (!channelIsInputTank() && Panel.inside(mouseX, mouseY, mp[0], mp[1], mp[2], mp[3])) {
            boolean master = io.github.genichimaruo.singulo.machine.SideConfig.ejectEnabled(packed);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new io.github.genichimaruo.singulo.network.SideConfigPayload(
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
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new io.github.genichimaruo.singulo.network.SideConfigPayload(
                menu.pos(), sidesChannel, f.ordinal(), mode | (eject ? 4 : 0)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (selector.drag(leftPos, topPos, mouseX, mouseY) || sides.drag(leftPos, topPos, mouseX, mouseY)) {
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
        if (selector.release() | sides.release()) {
            return true;
        }
        if (sides.open && cubePress >= 0 && button == cubePress) {
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
        drawFrame(g, mouseX, mouseY);
        drawFreeSwitch(g, mouseX, mouseY);
        MachineLayout l = layout();

        int energy = menu.value(MachineBlockEntity.D_ENERGY);
        int capacity = Math.max(1, menu.value(MachineBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, x + MachineLayout.ENERGY_X, y + MachineLayout.TOP, 8, MachineLayout.BAR_HEIGHT,
                (double) energy / capacity, Panel.GLOW);

        for (int i = 0; i < l.fluidInX.length; i++) {
            drawTank(g, x + l.fluidInX[i], y + MachineLayout.TOP, i);
        }
        for (int i = 0; i < l.fluidOutX.length; i++) {
            drawTank(g, x + l.fluidOutX[i], y + MachineLayout.TOP, type().fluidInputs() + i);
        }
        for (int i = 0; i < type().inputSlots(); i++) {
            Panel.slot(g, skin, x + l.inputX[i], y + l.inputY[i]);
        }
        for (int i = 0; i < type().outputSlots(); i++) {
            Panel.slot(g, skin, x + l.outputX, y + l.outputY[i]);
        }
        if (type().hasCatalystSlot()) {
            boolean empty = menu.getSlot(menuIndexOf(type().catalystSlot())).getItem().isEmpty();
            int loaded = menu.value(MachineBlockEntity.D_LOADED_ITEM);
            if (!empty || loaded < 0) {
                drawSpecialSlot(g, x + l.catalystX, y + l.catalystY, Panel.AMBER, type().catalystSlot(), "time_crystal_catalyst");
            } else {
                g.renderOutline(x + l.catalystX - 2, y + l.catalystY - 2, 20, 20, Panel.AMBER);
                Panel.slot(g, skin, x + l.catalystX, y + l.catalystY);
            }
            drawLoadedCatalyst(g, x + l.catalystX, y + l.catalystY, loaded, menu.value(MachineBlockEntity.D_LOADED_FILL), empty);
        }
        int progress = menu.value(MachineBlockEntity.D_PROGRESS);
        int max = Math.max(1, menu.value(MachineBlockEntity.D_MAX_PROGRESS));
        Panel.arrow(g, skin, x + l.arrowX, y + l.arrowY, (double) progress / max);

        if (type().massMode()) {
            int bx = x + l.modeX;
            int by = y + l.modeY;
            boolean hover = Panel.inside(mouseX, mouseY, bx, by, MODE_SIZE, MODE_SIZE);
            Panel.button(g, skin, bx - 1, by - 1, MODE_SIZE + 2, MODE_SIZE + 2, hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            g.renderItem(modeIcon(), bx, by);
            int target = menu.value(MachineBlockEntity.D_MASS_TARGET);
            if (target > 0) {
                int mx = x + l.massBarX;
                Panel.well(g, skin, mx, by + 5, l.massBarWidth, 6);
                double f = (double) menu.value(MachineBlockEntity.D_MASS) / target;
                int filled = (int) Math.round(l.massBarWidth * Math.min(1, f));
                g.fill(mx, by + 5, mx + filled, by + 11, Panel.GLOW);
                Panel.glass(g, mx, by + 5, filled, 6);
            }
        }
        if (type().selectsRecipe()) {
            drawSelectButton(g, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ 作るものの指定

    /** 作るものを選ぶ窓。 */
    private final Popup selector = new Popup("machine.select");
    /** 選ぶ画面の、上から隠れている行の数。 */
    private int selectorScroll;
    /** 作るものボタン（出力スロットの右）。 */
    private int[] selectPos() {
        return new int[]{leftPos + layout().selectX, topPos + layout().selectY, 16, 16};
    }

    /** 選ぶ板の行数（設定の値を、画面に収まる数までにする）。 */
    private int selectRows() {
        return Math.max(2, Math.min(io.github.genichimaruo.singulo.generated.ClientConfig.SELECTOR_ROWS.get(), maxSelectRows()));
    }

    /** 画面の高さに入る行数。 */
    private int maxSelectRows() {
        return Math.max(2, Math.min(8, (height - 4 - Popup.BAR - 1 - 8) / 18));
    }

    private int selectColumns() {
        return (imageWidth - 8 - 8 - 14) / 18;
    }

    private void setSelectRows(int rows) {
        var value = io.github.genichimaruo.singulo.generated.ClientConfig.SELECTOR_ROWS;
        value.set(Math.max(2, Math.min(rows, maxSelectRows())));
        value.save();
    }

    @javax.annotation.Nullable
    private RecipeHolder<MachineRecipe> selectedHolder() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : io.github.genichimaruo.singulo.machine.RecipeSelection.byIndex(level, type(),
                menu.value(MachineBlockEntity.D_SELECTED) - 1);
    }

    private List<ItemStack> recipeInputs() {
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < type().recipeSlots(); i++) {
            inputs.add(menu.getSlot(type().recipeSlot(i)).getItem());
        }
        return inputs;
    }

    private void drawSelectButton(GuiGraphics g, int mouseX, int mouseY) {
        int[] p = selectPos();
        boolean hover = Panel.inside(mouseX, mouseY, p[0], p[1], p[2], p[3]);
        RecipeHolder<MachineRecipe> selected = selectedHolder();
        Panel.button(g, skin, p[0] - 2, p[1] - 2, 20, 20, selected != null ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.slot(g, skin, p[0], p[1]);
        if (selected != null) {
            g.renderItem(selected.value().result(), p[0], p[1]);
        } else {
            Panel.autoIcon(g, p[0] + 4, p[1] + 3, 0xFFB8C2CC);
        }
    }

    /** 選ぶ画面の並び: 先頭は「自動」（null）、続いて入っている材料で作れるもの → 材料の一部が入っているもの → そのほか。 */
    private List<RecipeHolder<MachineRecipe>> selectorEntries() {
        List<RecipeHolder<MachineRecipe>> out = new ArrayList<>();
        out.add(null);
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return out;
        }
        List<ItemStack> inputs = recipeInputs();
        List<RecipeHolder<MachineRecipe>> all = new ArrayList<>(io.github.genichimaruo.singulo.machine.RecipeSelection.selectable(level, type()));
        java.util.Map<net.minecraft.resources.ResourceLocation, Integer> score = new java.util.HashMap<>();
        for (RecipeHolder<MachineRecipe> h : all) {
            score.put(h.id(), matchScore(h.value(), inputs));
        }
        all.sort(java.util.Comparator.comparingInt((RecipeHolder<MachineRecipe> h) -> -score.get(h.id()))
                .thenComparingInt(h -> h.value().stage()).thenComparing(h -> h.id().toString()));
        out.addAll(all);
        return out;
    }

    /** 2000 = 今の材料で作れる、それ未満は入っている材料の種類の数（多いほど上）。 */
    private static int matchScore(MachineRecipe r, List<ItemStack> inputs) {
        if (r.plan(inputs) != null) {
            return 2000;
        }
        int matched = 0;
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            if (inputs.stream().anyMatch(s -> !s.isEmpty() && in.ingredient().test(s))) {
                matched++;
            }
        }
        return matched;
    }

    /** 選ぶ窓の中身（帯の下）。行を増やすと窓は下端を保ったまま上へ伸びる。 */
    private int[] selectorBox() {
        return new int[]{selector.x, selector.bodyY(), selector.w, selectRows() * 18 + 8};
    }

    private int[] cellPos(int visibleIndex) {
        int[] box = selectorBox();
        int cols = selectColumns();
        return new int[]{box[0] + 4 + (visibleIndex % cols) * 18, box[1] + 4 + (visibleIndex / cols) * 18};
    }

    /** 板の右の列: 行を増やす・減らすボタンと、スクロールバー。 */
    private int[] rowsMorePos() {
        int[] box = selectorBox();
        return new int[]{box[0] + box[2] - 15, box[1] + 4, 11, 10};
    }

    private int[] rowsLessPos() {
        int[] box = selectorBox();
        return new int[]{box[0] + box[2] - 15, box[1] + 15, 11, 10};
    }

    /** 画面上の位置にある並びの番号（なければ -1）。 */
    private int entryAt(double mouseX, double mouseY, int count) {
        int cols = selectColumns();
        for (int v = 0; v < cols * selectRows(); v++) {
            int index = selectorScroll * cols + v;
            if (index >= count) {
                break;
            }
            int[] c = cellPos(v);
            if (Panel.inside(mouseX, mouseY, c[0], c[1], 18, 18)) {
                return index;
            }
        }
        return -1;
    }

    private void renderSelector(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int[] box = selectorBox();
        selector.draw(g, skin, mouseX, mouseY);
        List<RecipeHolder<MachineRecipe>> entries = selectorEntries();
        int cols = selectColumns();
        int visibleRows = selectRows();
        int rows = (entries.size() + cols - 1) / cols;
        selectorScroll = Math.max(0, Math.min(selectorScroll, rows - visibleRows));
        // 行を増やす・減らす
        int[] more = rowsMorePos();
        int[] less = rowsLessPos();
        boolean canMore = visibleRows < maxSelectRows();
        boolean canLess = visibleRows > 2;
        int dm = Panel.button(g, skin, more[0], more[1], more[2], more[3], !canMore ? Panel.Press.DISABLED
                : Panel.inside(mouseX, mouseY, more[0], more[1], more[2], more[3]) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.plusIcon(g, more[0] + 3, more[1] + 2 + dm, canMore ? skin.text() : skin.dim());
        int dl = Panel.button(g, skin, less[0], less[1], less[2], less[3], !canLess ? Panel.Press.DISABLED
                : Panel.inside(mouseX, mouseY, less[0], less[1], less[2], less[3]) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.minusIcon(g, less[0] + 3, less[1] + 2 + dl, canLess ? skin.text() : skin.dim());
        // スクロールバー（隠れている行があるときだけつまみを出す）
        int trackX = more[0] + 3;
        int trackY = less[1] + less[3] + 3;
        int trackH = box[1] + box[3] - 5 - trackY;
        if (trackH > 4) {
            Panel.well(g, skin, trackX, trackY, 5, trackH);
            if (rows > visibleRows) {
                int thumb = Math.max(4, trackH * visibleRows / rows);
                int ty = trackY + (trackH - thumb) * selectorScroll / Math.max(1, rows - visibleRows);
                Panel.button(g, skin, trackX, ty, 5, thumb, Panel.Press.NORMAL);
            }
        }
        RecipeHolder<MachineRecipe> selected = selectedHolder();
        List<ItemStack> inputs = recipeInputs();
        int hovered = entryAt(mouseX, mouseY, entries.size());
        for (int v = 0; v < cols * visibleRows; v++) {
            int index = selectorScroll * cols + v;
            if (index >= entries.size()) {
                break;
            }
            RecipeHolder<MachineRecipe> h = entries.get(index);
            int[] c = cellPos(v);
            boolean isSelected = h == null ? selected == null : selected != null && selected.id().equals(h.id());
            boolean ready = h != null && h.value().plan(inputs) != null;
            Panel.slot(g, skin, c[0] + 1, c[1] + 1);
            int ring = isSelected ? skin.accent() : index == hovered ? Panel.GLOW : ready ? GuiSkin.OK : 0;
            if (ring != 0) {
                g.renderOutline(c[0], c[1], 18, 18, ring);
            }
            if (h == null) {
                Panel.autoIcon(g, c[0] + 5, c[1] + 4, 0xFFE9EDF0);
            } else {
                g.renderItem(h.value().result(), c[0] + 1, c[1] + 1);
                g.renderItemDecorations(font, h.value().result(), c[0] + 1, c[1] + 1);
            }
        }
        if (hovered >= 0) {
            Panel.tooltip(g, font, selectorTooltip(entries.get(hovered)), 230, mouseX, mouseY);
        } else if (canMore && Panel.inside(mouseX, mouseY, more[0], more[1], more[2], more[3])) {
            g.renderTooltip(font, Component.translatable("gui.singulo.select.rows_more"), mouseX, mouseY);
        } else if (canLess && Panel.inside(mouseX, mouseY, less[0], less[1], less[2], less[3])) {
            g.renderTooltip(font, Component.translatable("gui.singulo.select.rows_less"), mouseX, mouseY);
        }
        g.pose().popPose();
    }

    private List<Component> selectorTooltip(@javax.annotation.Nullable RecipeHolder<MachineRecipe> h) {
        List<Component> lines = new ArrayList<>();
        if (h == null) {
            lines.add(Component.translatable("gui.singulo.select.auto"));
            lines.add(Component.translatable("gui.singulo.select.auto_hint").withStyle(s -> s.withColor(0xFF8A949E)));
            return lines;
        }
        MachineRecipe r = h.value();
        lines.add(r.result().getHoverName());
        r.restore().ifPresent(in -> {
            ItemStack[] options = in.getItems();
            lines.add(Component.translatable("gui.singulo.select.restore", options.length > 0 ? options[0].getHoverName()
                    : Component.literal("?")).withStyle(s -> s.withColor(0xFF8A949E)));
        });
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            ItemStack[] options = in.ingredient().getItems();
            Component name = options.length > 0 ? options[0].getHoverName() : Component.literal("?");
            boolean catalyst = in.uses() > 0 && options.length > 0 && io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(options[0]);
            Component line = catalyst ? Component.translatable("gui.singulo.select.lifetime", name,
                    io.github.genichimaruo.singulo.item.UsesItem.lifetime(in.uses()))
                    : Component.translatable(in.uses() > 0 ? "gui.singulo.select.uses" : "gui.singulo.select.item", name,
                    in.uses() > 0 ? in.uses() : in.count());
            lines.add(line.copy().withStyle(s -> s.withColor(0xFF8A949E)));
        }
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            lines.add(Component.translatable("gui.singulo.select.fluid", in.fluid().getFluidType().getDescription(), in.amount())
                    .withStyle(s -> s.withColor(0xFF8A949E)));
        }
        return lines;
    }

    private boolean clickSelector(double mouseX, double mouseY) {
        int[] more = rowsMorePos();
        if (Panel.inside(mouseX, mouseY, more[0], more[1], more[2], more[3])) {
            setSelectRows(selectRows() + 1);
            return true;
        }
        int[] less = rowsLessPos();
        if (Panel.inside(mouseX, mouseY, less[0], less[1], less[2], less[3])) {
            setSelectRows(selectRows() - 1);
            return true;
        }
        List<RecipeHolder<MachineRecipe>> entries = selectorEntries();
        int index = entryAt(mouseX, mouseY, entries.size());
        if (index < 0 || minecraft == null || minecraft.gameMode == null) {
            return false;
        }
        RecipeHolder<MachineRecipe> h = entries.get(index);
        var level = Minecraft.getInstance().level;
        int id = h == null || level == null ? -1 : io.github.genichimaruo.singulo.machine.RecipeSelection.indexOf(level, type(), h.id());
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id < 0 ? MachineMenu.BUTTON_CLEAR_SELECTION
                : MachineMenu.BUTTON_SELECT + id);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selector.contains(mouseX, mouseY)) {
            selectorScroll = Math.max(0, selectorScroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
        int[] r = structureRect();
        if (r != null) {
            Panel.structureIcon(g, r[0] - leftPos, r[1] - topPos + 1, skin.dim());
            g.drawString(font, String.valueOf(menu.value(MachineBlockEntity.D_STRUCTURE)), r[0] - leftPos + 10, inventoryLabelY, skin.dim(), false);
        }
    }

    /** マルチブロックの大きさ（格子の記号と数。見出しの行の右端。説明はツールチップ）。 */
    @javax.annotation.Nullable
    private int[] structureRect() {
        int size = menu.value(MachineBlockEntity.D_STRUCTURE);
        if (size <= 0) {
            return null;
        }
        int w = 10 + font.width(String.valueOf(size));
        return new int[]{leftPos + imageWidth - 8 - w, topPos + inventoryLabelY - 1, w, 10};
    }

    private void drawTank(GuiGraphics g, int x, int y, int tank) {
        int fluidId = menu.value(MachineBlockEntity.D_TANKS + tank * 2);
        int amount = menu.value(MachineBlockEntity.D_TANKS + tank * 2 + 1);
        if (hasSides()) {
            // 面の設定のタブと同じ色の縁（このタンクがどの面を使うかを色で対応させる）
            g.renderOutline(x - 2, y - 2, MachineLayout.TANK_WIDTH + 4, MachineLayout.BAR_HEIGHT + 4, tankColor(tank));
        }
        if (fluidId > 0) {
            Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId - 1);
            double fraction = (double) amount / MachineType.TANK_CAPACITY;
            FluidGauge.draw(g, x, y, MachineLayout.TANK_WIDTH, MachineLayout.BAR_HEIGHT, fraction, fluid);
            int filled = (int) Math.round(MachineLayout.BAR_HEIGHT * Math.min(1, fraction));
            Panel.glass(g, x, y + MachineLayout.BAR_HEIGHT - filled, MachineLayout.TANK_WIDTH, filled);
        } else {
            Panel.well(g, skin, x, y, MachineLayout.TANK_WIDTH, MachineLayout.BAR_HEIGHT);
        }
    }

    private List<RecipeHolder<MachineRecipe>> massRecipes() {
        return Minecraft.getInstance().level == null ? List.of()
                : MachineBlockEntity.massRecipes(Minecraft.getInstance().level, type());
    }

    private ItemStack modeIcon() {
        int mode = menu.value(MachineBlockEntity.D_MODE);
        List<RecipeHolder<MachineRecipe>> list = massRecipes();
        if (mode > 0 && mode <= list.size()) {
            return list.get(mode - 1).value().result();
        }
        return new ItemStack(Items.PISTON);
    }

    private void renderOwnTooltips(GuiGraphics g, int mouseX, int mouseY) {
        MachineLayout l = layout();
        int x = leftPos;
        int y = topPos;
        if (renderFrameTooltips(g, mouseX, mouseY)) {
            return;
        }
        int[] sr = structureRect();
        if (sr != null && Panel.inside(mouseX, mouseY, sr[0], sr[1], sr[2], sr[3])) {
            g.renderTooltip(font, Component.translatable("gui.singulo.structure." + type().id(), menu.value(MachineBlockEntity.D_STRUCTURE)),
                    mouseX, mouseY);
            return;
        }
        int[] sp = selectPos();
        if (type().selectsRecipe() && Panel.inside(mouseX, mouseY, sp[0], sp[1], sp[2], sp[3])) {
            RecipeHolder<MachineRecipe> selected = selectedHolder();
            List<Component> lines = new ArrayList<>();
            lines.add(selected == null ? Component.translatable("gui.singulo.select.auto")
                    : Component.translatable("gui.singulo.select.fixed", selected.value().result().getHoverName()));
            lines.add(Component.translatable(selected == null ? "gui.singulo.select.auto_hint" : "gui.singulo.select.fixed_hint")
                    .withStyle(s -> s.withColor(0xFF8A949E)));
            lines.add(Component.translatable("gui.singulo.select.button_hint").withStyle(s -> s.withColor(0xFF8A949E)));
            Panel.tooltip(g, font, lines, 220, mouseX, mouseY);
            return;
        }
        int[] fp = freePos();
        if (showsFreeSwitch() && Panel.inside(mouseX, mouseY, fp[0], fp[1], fp[2], fp[3])) {
            g.renderTooltip(font, Component.translatable((flags() & MachineBlockEntity.FLAG_MAKE_FREE) != 0
                    ? "gui.singulo.make_free.on" : "gui.singulo.make_free.off"), mouseX, mouseY);
            return;
        }
        if (type().hasCatalystSlot() && Panel.inside(mouseX, mouseY, x + l.catalystX, y + l.catalystY, 16, 22)) {
            boolean empty = menu.getSlot(menuIndexOf(type().catalystSlot())).getItem().isEmpty();
            List<Component> loaded = loadedCatalystLines(menu.value(MachineBlockEntity.D_LOADED_ITEM), menu.value(MachineBlockEntity.D_LOADED_FILL), empty);
            if (loaded != null && (empty || mouseY >= y + l.catalystY + 17)) {
                g.renderComponentTooltip(font, loaded, mouseX, mouseY);
                return;
            }
            if (empty) {
                g.renderTooltip(font, Component.translatable("gui.singulo.slot.catalyst"), mouseX, mouseY);
                return;
            }
        }
        if (Panel.inside(mouseX, mouseY, x + MachineLayout.ENERGY_X, y + MachineLayout.TOP, 8, MachineLayout.BAR_HEIGHT)) {
            int usage = menu.value(MachineBlockEntity.D_USAGE);
            List<Component> lines = new ArrayList<>(Panel.energyLines(menu.value(MachineBlockEntity.D_ENERGY),
                    menu.value(MachineBlockEntity.D_CAPACITY)));
            if (usage > 0) {
                lines.add(1, Component.translatable("gui.singulo.energy_rate", io.github.genichimaruo.singulo.energy.EnergyText.of(usage)));
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
        if (type().massMode() && Panel.inside(mouseX, mouseY, x + l.modeX, y + l.modeY, MODE_SIZE, MODE_SIZE)) {
            int mode = menu.value(MachineBlockEntity.D_MODE);
            List<Component> lines = new ArrayList<>();
            List<RecipeHolder<MachineRecipe>> list = massRecipes();
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

    /** 専用スロット: 色つきの縁（frame が 0 なら縁なし）と、空のときは薄い見本のアイコン。 */
    private void drawSpecialSlot(GuiGraphics g, int sx, int sy, int frame, int slotIndex, String ghost) {
        if (frame != 0) {
            g.renderOutline(sx - 2, sy - 2, 20, 20, frame);
        }
        Panel.slot(g, skin, sx, sy);
        if (menu.getSlot(menuIndexOf(slotIndex)).getItem().isEmpty()) {
            g.pose().pushPose();
            g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(ghost))), sx, sy);
            g.pose().translate(0, 0, 200);
            g.fill(sx, sy, sx + 16, sy + 16, 0xA0000000 | (skin.fill() & 0xFFFFFF));
            g.pose().popPose();
        }
    }

    /** 装置のスロット番号から、メニュー上の番号へ（入力・出力・触媒・アップグレードの順に並んでいる）。 */
    private int menuIndexOf(int slotIndex) {
        return slotIndex;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layoutPopups();
        // 窓の中のクリックは下の画面に届かない（手前の窓から順に）
        if (selector.open && (selector.clickChrome(mouseX, mouseY) || selector.contains(mouseX, mouseY))) {
            if (selector.open && !selector.overClose(mouseX, mouseY)) {
                clickSelector(mouseX, mouseY);
            }
            return true;
        }
        if (sides.open && (sides.clickChrome(mouseX, mouseY) || sides.contains(mouseX, mouseY))) {
            if (sides.open && mouseY >= sides.bodyY()) {
                clickSides(mouseX, mouseY, button);
            }
            return true;
        }
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        if (type().hasCatalystSlot() && menu.getCarried().isEmpty() && minecraft != null && minecraft.gameMode != null
                && menu.getSlot(menuIndexOf(type().catalystSlot())).getItem().isEmpty() && menu.value(MachineBlockEntity.D_LOADED_ITEM) >= 0
                && Panel.inside(mouseX, mouseY, leftPos + layout().catalystX, topPos + layout().catalystY, 16, 16)) {
            // 予備が空の触媒スロットをクリック: 装填中の触媒を取り出す
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_EJECT_CATALYST);
            return true;
        }
        int[] fp = freePos();
        if (showsFreeSwitch() && Panel.inside(mouseX, mouseY, fp[0], fp[1], fp[2], fp[3]) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_MAKE_FREE);
            return true;
        }
        if (type().selectsRecipe()) {
            int[] sp = selectPos();
            if (Panel.inside(mouseX, mouseY, sp[0], sp[1], sp[2], sp[3])) {
                if (button == 1 && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_CLEAR_SELECTION);
                } else {
                    selector.open = !selector.open;
                    sides.open = false;
                    selectorScroll = 0;
                }
                return true;
            }
        }
        if (type().massMode() && Panel.inside(mouseX, mouseY, leftPos + layout().modeX, topPos + layout().modeY,
                MODE_SIZE, MODE_SIZE) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_CYCLE_MODE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
