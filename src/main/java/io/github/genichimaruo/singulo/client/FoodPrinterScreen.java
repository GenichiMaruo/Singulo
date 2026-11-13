package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.food.FoodPaste;
import io.github.genichimaruo.singulo.food.FoodPrinterBlockEntity;
import io.github.genichimaruo.singulo.food.FoodPrinterMenu;
import io.github.genichimaruo.singulo.food.PrintedFood;
import io.github.genichimaruo.singulo.food.PrintedFoodItem;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * フードプリンターの画面（ほかの装置と同じ骨格: ヘッダーの右に電源と状態、左端に蓄電）。
 * 上からタンパク質・炭水化物・甘味の行（食材の枠 → 小さな矢印 → カートリッジ → 縦長のペーストの目盛り）→ 印刷室の窓
 * （選んだ食品が下から積み上がる）→ 矢印 → 出口。
 * 出口の上の「作るもの」ボタンで、ほかの装置と同じ選ぶ窓を開く（先頭は自動、印刷できる食品は緑の枠）。右クリックで自動に戻す。
 * 細かい数（ペースト・時間・電力）は、重ねたときだけ出す。
 */
public class FoodPrinterScreen extends FramedScreen<FoodPrinterMenu> {
    /** 食材の枠の見本（種類ごとに、順に入れ替わる）。 */
    private static final ItemStack[][] INGREDIENT_HINTS = {
            {new ItemStack(Items.BEEF), new ItemStack(Items.COD), new ItemStack(Items.EGG)},
            {new ItemStack(Items.WHEAT), new ItemStack(Items.POTATO), new ItemStack(Items.CARROT)},
            {new ItemStack(Items.APPLE), new ItemStack(Items.SWEET_BERRIES), new ItemStack(Items.SUGAR)}};
    private static final int GREY = 0xFF8A949E;

    private final Popup selector = new Popup("food_printer.select");

    public FoodPrinterScreen(FoodPrinterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FoodPrinterMenu.WIDTH, FoodPrinterMenu.HEIGHT);
    }

    private int value(int i) {
        return menu.value(i);
    }

    /** 選んでいる食品（自動なら null）。 */
    private PrintedFood selected() {
        return PrintedFood.byIndex(value(FoodPrinterBlockEntity.D_SELECTED));
    }

    /** 印刷室の窓に見せる食品: 印刷している食品 → 選んだ食品 → 出口にある食品。 */
    private PrintedFood shown() {
        PrintedFood printing = PrintedFood.byIndex(value(FoodPrinterBlockEntity.D_PRINTING));
        if (printing != null) {
            return printing;
        }
        if (selected() != null) {
            return selected();
        }
        return menu.getSlot(FoodPrinterBlockEntity.OUTPUT).getItem().getItem() instanceof PrintedFoodItem p ? p.printedFood() : null;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new io.github.genichimaruo.singulo.network.LegacyMenuButtonPayload(menu.containerId, id));
        }
    }

    // ------------------------------------------------------------------ ヘッダー（電源と状態）

    @Override
    protected Boolean powered() {
        return value(FoodPrinterBlockEntity.D_ENABLED) != 0;
    }

    @Override
    protected void togglePower() {
        send(FoodPrinterMenu.BUTTON_POWER);
    }

    @Override
    protected Panel.Status status() {
        return switch (value(FoodPrinterBlockEntity.D_STATE)) {
            case FoodPrinterBlockEntity.S_RUNNING -> Panel.Status.RUN;
            case FoodPrinterBlockEntity.S_NO_POWER -> Panel.Status.NO_POWER;
            case FoodPrinterBlockEntity.S_FULL -> Panel.Status.FULL;
            case FoodPrinterBlockEntity.S_OFF -> Panel.Status.OFF;
            case FoodPrinterBlockEntity.S_SHORT -> Panel.Status.WARN;
            default -> Panel.Status.IDLE;
        };
    }

    @Override
    protected Component statusText() {
        return Component.translatable(switch (value(FoodPrinterBlockEntity.D_STATE)) {
            case FoodPrinterBlockEntity.S_RUNNING -> "gui.singulo.status.running";
            case FoodPrinterBlockEntity.S_NO_POWER -> "gui.singulo.status.no_power";
            case FoodPrinterBlockEntity.S_FULL -> "gui.singulo.status.output_full";
            case FoodPrinterBlockEntity.S_OFF -> "gui.singulo.status.off";
            case FoodPrinterBlockEntity.S_SHORT -> "gui.singulo.food_printer.status.short";
            default -> "gui.singulo.status.idle";
        });
    }

    @Override
    protected int statusColor() {
        return switch (value(FoodPrinterBlockEntity.D_STATE)) {
            case FoodPrinterBlockEntity.S_RUNNING -> GuiSkin.OK;
            case FoodPrinterBlockEntity.S_OFF, FoodPrinterBlockEntity.S_IDLE -> GuiSkin.IDLE;
            default -> GuiSkin.WARN;
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return switch (value(FoodPrinterBlockEntity.D_STATE)) {
            case FoodPrinterBlockEntity.S_NO_POWER -> List.of(Component.translatable("gui.singulo.reason.no_power",
                    EnergyText.of(PrintedFood.ENERGY_PER_TICK), EnergyText.of(value(FoodPrinterBlockEntity.D_ENERGY))));
            case FoodPrinterBlockEntity.S_FULL -> List.of(Component.translatable("gui.singulo.reason.output_full"));
            case FoodPrinterBlockEntity.S_OFF -> List.of(Component.translatable("gui.singulo.reason.off"));
            case FoodPrinterBlockEntity.S_SHORT -> selected() == null ? List.of() : costLines(selected());
            case FoodPrinterBlockEntity.S_IDLE -> List.of(Component.translatable("gui.singulo.food_printer.nothing"));
            default -> List.of();
        };
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layoutSelector();
        super.render(g, mouseX, mouseY, partialTick);
        if (selector.open) {
            renderSelector(g, mouseX, mouseY);
        }
        if (overPopup(mouseX, mouseY)) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        renderTooltip(g, mouseX, mouseY);
        if (!(hoveredSlot != null && hoveredSlot.hasItem()) && !renderFrameTooltips(g, mouseX, mouseY)) {
            if (overSelect(mouseX, mouseY)) {
                Panel.tooltip(g, font, selectTooltip(), 220, mouseX, mouseY);
            } else {
                renderStatTooltips(g, mouseX, mouseY);
            }
        }
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos, y = topPos;
        // 蓄電
        int energy = value(FoodPrinterBlockEntity.D_ENERGY);
        Panel.gauge(g, skin, x + FoodPrinterMenu.ENERGY_X, y + FoodPrinterMenu.ENERGY_Y, FoodPrinterMenu.ENERGY_W, FoodPrinterMenu.ENERGY_H,
                energy / (double) FoodPrinterBlockEntity.ENERGY_CAPACITY, Panel.GLOW);
        area(FoodPrinterMenu.ENERGY_X, FoodPrinterMenu.ENERGY_Y, FoodPrinterMenu.ENERGY_W, FoodPrinterMenu.ENERGY_H,
                Panel.energyLines(energy, FoodPrinterBlockEntity.ENERGY_CAPACITY));
        // 種類ごとの行: 食材の枠（空なら見本）→ 小さな矢印（ペーストにしている間はその色）→ カートリッジ → ペースト
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            var feed = menu.getSlot(FoodPrinterBlockEntity.INGREDIENT + k);
            ItemStack[] hints = INGREDIENT_HINTS[k];
            ghostSlot(g, x + feed.x, y + feed.y, feed, hints[(int) (System.currentTimeMillis() / 1500 % hints.length)]);
            if (!feed.hasItem()) {
                area(feed.x, feed.y, 16, 16, List.of(Component.translatable("gui.singulo.food_printer.ingredient", FoodPaste.name(k)),
                        Component.translatable("gui.singulo.food_printer.ingredient." + FoodPaste.NAMES[k]).withStyle(s -> s.withColor(GREY)),
                        Component.translatable("gui.singulo.food_printer.ingredient.hint").withStyle(s -> s.withColor(GREY))));
            }
            drawFlow(g, k, feed.hasItem());
            var slot = menu.getSlot(FoodPrinterBlockEntity.CARTRIDGE + k);
            ghostSlot(g, x + slot.x, y + slot.y, slot, new ItemStack(SinguloItems.FOOD_CARTRIDGE.get()));
            if (!slot.hasItem()) {
                area(slot.x, slot.y, 16, 16, List.of(Component.translatable("gui.singulo.food_printer.cartridge", FoodPaste.name(k))));
            }
            pasteGauge(g, k, slot.y - 1);
        }
        drawBed(g);
        // 矢印と出口
        Panel.arrow(g, skin, x + (FoodPrinterMenu.BED_X + FoodPrinterMenu.BED_W + FoodPrinterMenu.OUT_X - 1 - 22) / 2, y + FoodPrinterMenu.OUT_Y,
                progress());
        var out = menu.getSlot(FoodPrinterBlockEntity.OUTPUT);
        Panel.slot(g, skin, x + out.x, y + out.y);
        if (!out.hasItem()) {
            area(out.x, out.y, 16, 16, List.of(Component.translatable("gui.singulo.food_printer.output")));
        }
        drawSelectButton(g, mouseX, mouseY);
    }

    /** 印刷の進み（0〜1）。 */
    private float progress() {
        PrintedFood printing = PrintedFood.byIndex(value(FoodPrinterBlockEntity.D_PRINTING));
        return printing == null ? 0 : Math.min(1, value(FoodPrinterBlockEntity.D_PROGRESS) / (float) printing.ticks());
    }

    /**
     * 食材の枠とカートリッジの間の小さな矢印（5×7）。食材が入っていて、カートリッジにその種類のペーストを足せるときは、
     * ペーストの色で光る。
     */
    private void drawFlow(GuiGraphics g, int kind, boolean hasIngredient) {
        ItemStack cartridge = menu.cartridge(kind);
        boolean flowing = hasIngredient && FoodPaste.fitsSlot(cartridge, kind) && FoodPaste.amount(cartridge) < FoodPaste.CAPACITY;
        int color = flowing ? 0xFF000000 | FoodPaste.COLORS[kind] : skin.dim();
        int ax = leftPos + FoodPrinterMenu.FLOW_X, ay = topPos + FoodPrinterMenu.ROW_Y + kind * FoodPrinterMenu.ROW_STEP + 4;
        for (int i = 0; i < 4; i++) {
            g.fill(ax + i, ay + i, ax + i + 2, ay + i + 1, color);
            g.fill(ax + i, ay + 6 - i, ax + i + 2, ay + 7 - i, color);
        }
    }

    /** カートリッジの右の縦長のペーストの目盛り（スロットと同じ高さ。選んだ食品の1個に使う分の印つき）。 */
    private void pasteGauge(GuiGraphics g, int kind, int gy) {
        int gx = leftPos + FoodPrinterMenu.PASTE_X, w = FoodPrinterMenu.PASTE_W, h = 18;
        int top = topPos + gy;
        Panel.well(g, skin, gx, top, w, h);
        ItemStack cartridge = menu.cartridge(kind);
        int amount = cartridge.isEmpty() ? 0 : FoodPaste.amount(cartridge);
        int color = 0xFF000000 | FoodPaste.COLORS[kind];
        int filled = Math.round((float) h * amount / FoodPaste.CAPACITY);
        if (filled > 0) {
            g.fill(gx, top + h - filled, gx + w, top + h, color);
            g.fill(gx, top + h - filled, gx + 1, top + h, GuiSkin.mix(color, 0xFFFFFFFF, 0.45F));
        }
        PrintedFood food = shown();
        if (food != null && food.cost(kind) > 0) {
            int ny = top + h - Math.round((float) h * food.cost(kind) / FoodPaste.CAPACITY);
            g.fill(gx - 1, ny, gx + w + 1, ny + 1, amount >= food.cost(kind) ? 0xFFFFE070 : GuiSkin.BAD);
        }
        area(FoodPrinterMenu.PASTE_X - 1, gy - 1, w + 2, h + 2, List.of(cartridge.isEmpty()
                ? Component.translatable("gui.singulo.food_printer.no_cartridge")
                : Component.translatable("gui.singulo.food_printer.paste", FoodPaste.name(kind), amount, FoodPaste.CAPACITY)));
    }

    /** 印刷室の窓: 暗い室と台に、食品の絵（2倍）を、進んだ分だけ下から見せる。上の分はうすい影、いまの高さにノズルの線。 */
    private void drawBed(GuiGraphics g) {
        int bx = leftPos + FoodPrinterMenu.BED_X, by = topPos + FoodPrinterMenu.BED_Y, w = FoodPrinterMenu.BED_W, h = FoodPrinterMenu.BED_H;
        Panel.well(g, skin, bx, by, w, h);
        g.fill(bx, by, bx + w, by + h, 0xFF22262E);
        // 天井のレールと台
        g.fill(bx + 2, by + 3, bx + w - 2, by + 4, 0xFF4A505C);
        g.fill(bx + 3, by + h - 5, bx + w - 3, by + h - 3, 0xFF4A505C);
        for (int i = 6; i < w - 4; i += 4) {
            g.fill(bx + i, by + h - 3, bx + i + 1, by + h, 0xFF343A44);
        }
        PrintedFood food = shown();
        if (food != null) {
            ItemStack stack = new ItemStack(food.item());
            int ix = bx + (w - 32) / 2, iy = by + h - 5 - 32;
            boolean printing = value(FoodPrinterBlockEntity.D_PRINTING) >= 0;
            // 印刷していないときは、出口にあればできあがった形、なければ影だけ
            int done = printing ? Math.round(32 * progress()) : menu.getSlot(FoodPrinterBlockEntity.OUTPUT).getItem().is(food.item()) ? 32 : 0;
            // 影（まだ印刷していない形）
            drawLarge(g, stack, ix, iy);
            if (done < 32) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(ix, iy, ix + 32, iy + 32 - done, 0xC022262E);
                g.pose().popPose();
            }
            // ノズル（いま印刷している高さ）
            if (printing && done < 32) {
                int ny = iy + 32 - done;
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.fill(bx + 2, ny - 1, bx + w - 2, ny, 0xFF000000 | pasteColor(food));
                int nx = bx + w / 2;
                g.fill(nx - 1, by + 4, nx + 1, ny - 2, 0xFFAEB4BE);
                g.fill(nx - 2, ny - 3, nx + 2, ny - 1, 0xFFCE7E4E);
                g.pose().popPose();
            }
            area(FoodPrinterMenu.BED_X, FoodPrinterMenu.BED_Y, w, h, printing
                    ? List.of(stack.getHoverName(), Component.translatable("gui.singulo.food_printer.progress", Math.round(100 * progress()))
                    .withStyle(s -> s.withColor(GREY)))
                    : List.of(stack.getHoverName()));
        }
        Panel.glass(g, bx, by, w, h);
    }

    private static void drawLarge(GuiGraphics g, ItemStack stack, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2, 2, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static int pasteColor(PrintedFood food) {
        int best = 0;
        for (int k = 1; k < FoodPaste.KINDS; k++) {
            if (food.cost(k) > food.cost(best)) {
                best = k;
            }
        }
        return FoodPaste.COLORS[best];
    }

    // ------------------------------------------------------------------ 作るものの指定（ほかの装置と同じ選ぶ窓）

    private boolean overSelect(double mx, double my) {
        return !overPopup(mx, my) && Panel.inside(mx, my, leftPos + FoodPrinterMenu.SELECT_X - 2, topPos + FoodPrinterMenu.SELECT_Y - 2, 20, 20);
    }

    private void drawSelectButton(GuiGraphics g, int mouseX, int mouseY) {
        int sx = leftPos + FoodPrinterMenu.SELECT_X, sy = topPos + FoodPrinterMenu.SELECT_Y;
        PrintedFood food = selected();
        Panel.button(g, skin, sx - 2, sy - 2, 20, 20, food != null ? Panel.Press.ON : overSelect(mouseX, mouseY) ? Panel.Press.HOVER
                : Panel.Press.NORMAL);
        Panel.slot(g, skin, sx, sy);
        if (food != null) {
            g.renderItem(new ItemStack(food.item()), sx, sy);
        } else {
            Panel.autoIcon(g, sx + 4, sy + 3, 0xFFB8C2CC);
        }
    }

    private List<Component> selectTooltip() {
        PrintedFood food = selected();
        List<Component> lines = new ArrayList<>();
        lines.add(food == null ? Component.translatable("gui.singulo.select.auto")
                : Component.translatable("gui.singulo.select.fixed", new ItemStack(food.item()).getHoverName()));
        lines.add(Component.translatable(food == null ? "gui.singulo.food_printer.auto.hint" : "gui.singulo.food_printer.fixed.hint")
                .withStyle(s -> s.withColor(GREY)));
        lines.add(Component.translatable("gui.singulo.select.button_hint").withStyle(s -> s.withColor(GREY)));
        return lines;
    }

    /** 選ぶ窓の並び: 先頭は自動（null）、続いて食品。 */
    private static List<PrintedFood> entries() {
        List<PrintedFood> out = new ArrayList<>();
        out.add(null);
        out.addAll(List.of(PrintedFood.values()));
        return out;
    }

    @Override
    protected List<Popup> popups() {
        return List.of(selector);
    }

    /** 1行に全部が入る（行の数を変えるほどの数はない）。初めて開くときは、下端を装置の部分の下端に、右端を枠の右に合わせる。 */
    private void layoutSelector() {
        int w = entries().size() * 18 + 8;
        int h = Popup.BAR + 1 + 18 + 8;
        int bottom = io.github.genichimaruo.singulo.machine.GuiFrame.contentBottom(imageHeight);
        selector.layout(leftPos, topPos, w, h, imageWidth - 4 - w, bottom - h, width, height);
    }

    private int[] cellPos(int index) {
        return new int[]{selector.x + 4 + index * 18, selector.bodyY() + 4};
    }

    private int entryAt(double mx, double my) {
        if (!selector.open) {
            return -1;
        }
        for (int i = 0; i < entries().size(); i++) {
            int[] c = cellPos(i);
            if (Panel.inside(mx, my, c[0], c[1], 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    private void renderSelector(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        selector.draw(g, skin, mouseX, mouseY);
        List<PrintedFood> entries = entries();
        PrintedFood selected = selected();
        int hovered = entryAt(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            PrintedFood food = entries.get(i);
            int[] c = cellPos(i);
            Panel.slot(g, skin, c[0] + 1, c[1] + 1);
            int ring = food == selected ? skin.accent() : i == hovered ? Panel.GLOW : food != null && hasPaste(food) ? GuiSkin.OK : 0;
            if (ring != 0) {
                g.renderOutline(c[0], c[1], 18, 18, ring);
            }
            if (food == null) {
                Panel.autoIcon(g, c[0] + 5, c[1] + 4, 0xFFE9EDF0);
            } else {
                g.renderItem(new ItemStack(food.item()), c[0] + 1, c[1] + 1);
            }
        }
        if (hovered >= 0) {
            PrintedFood food = entries.get(hovered);
            Panel.tooltip(g, font, food == null ? List.of(Component.translatable("gui.singulo.select.auto"),
                    Component.translatable("gui.singulo.food_printer.auto.hint").withStyle(s -> s.withColor(GREY))) : entryTooltip(food), 220,
                    mouseX, mouseY);
        }
        g.pose().popPose();
    }

    /** 画面に見えているカートリッジで、ペーストが足りるか。 */
    private boolean hasPaste(PrintedFood food) {
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            ItemStack c = menu.cartridge(k);
            if (food.cost(k) > 0 && (c.isEmpty() || FoodPaste.kind(c) != k || FoodPaste.amount(c) < food.cost(k))) {
                return false;
            }
        }
        return true;
    }

    /** 1個に使うペースト（足りない分は赤く）。 */
    private List<Component> costLines(PrintedFood food) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.singulo.food_printer.cost").withStyle(s -> s.withColor(GREY)));
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            if (food.cost(k) > 0) {
                ItemStack c = menu.cartridge(k);
                boolean enough = !c.isEmpty() && FoodPaste.kind(c) == k && FoodPaste.amount(c) >= food.cost(k);
                int color = enough ? 0xFFC8C8C8 : 0xFFFF6060;
                lines.add(Component.translatable("gui.singulo.food_printer.cost.line", FoodPaste.name(k), food.cost(k))
                        .withStyle(s -> s.withColor(color)));
            }
        }
        return lines;
    }

    /** 選ぶ窓の食品の説明: 名前、使うペースト、時間と電力。 */
    private List<Component> entryTooltip(PrintedFood food) {
        List<Component> lines = new ArrayList<>();
        lines.add(new ItemStack(food.item()).getHoverName());
        lines.addAll(costLines(food));
        lines.add(Component.translatable("gui.singulo.food_printer.time", food.ticks() / 20, EnergyText.of(food.energy()))
                .withStyle(s -> s.withColor(GREY)));
        return lines;
    }

    // ------------------------------------------------------------------ 操作

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layoutSelector();
        // 窓の中のクリックは下の画面に届かない
        if (selector.open && (selector.clickChrome(mx, my) || selector.contains(mx, my))) {
            int i = entryAt(mx, my);
            if (selector.open && !selector.overClose(mx, my) && i >= 0) {
                PrintedFood food = entries().get(i);
                send(food == null ? FoodPrinterMenu.BUTTON_AUTO : food.ordinal());
                playClick();
            }
            return true;
        }
        if (clickFrame(mx, my, button)) {
            return true;
        }
        if (overSelect(mx, my)) {
            if (button == 1) {
                send(FoodPrinterMenu.BUTTON_AUTO);
            } else {
                selector.open = !selector.open;
            }
            playClick();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return selector.drag(leftPos, topPos, mx, my) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return selector.release() || super.mouseReleased(mx, my, button);
    }
}
