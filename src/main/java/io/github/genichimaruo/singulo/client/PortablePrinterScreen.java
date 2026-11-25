package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.PortableAndroidPrinterItem;
import io.github.genichimaruo.singulo.android.PortablePrinterMenu;
import io.github.genichimaruo.singulo.energy.EnergyText;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 携帯型アンドロイド印刷機の画面（0.21.0）。アンドロイドの配色（次に印刷する組の型の色）。組ごとに1行
 * （メモリ・キット、少しあけて自己修理キット・装甲板。空なら見本）。Mk2 は3行で、行の左の印が次に印刷する組（押すと選ぶ）。
 * 右に印刷ボタン、下に充電残量のゲージ（重ねると電力と、あと何体印刷できるか）。印刷できないときは、ボタンを押せず理由をツールチップに出す。
 */
public class PortablePrinterScreen extends FramedScreen<PortablePrinterMenu> {
    private static final int PRINT_X = 126, PRINT_Y = 24, PRINT_W = 44, PRINT_H = 20;
    private static final int BAR_X = 8, BAR_W = 160;
    private static final String[] GHOSTS = {"android_memory_blade", "android_kit", "android_repair_kit", "android_armor_plate"};
    private static final String[] SLOT_TIPS = {"gui.singulo.portable_printer.memory", "gui.singulo.portable_printer.kit",
            "gui.singulo.portable_printer.repair", "gui.singulo.portable_printer.plate"};
    private static final int[] SLOT_X = {PortablePrinterMenu.MEMORY_X, PortablePrinterMenu.KIT_X, PortablePrinterMenu.REPAIR_X,
            PortablePrinterMenu.PLATE_X};

    public PortablePrinterScreen(PortablePrinterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PortablePrinterMenu.WIDTH, PortablePrinterMenu.height(menu.units()));
        skin = GuiSkin.android(AndroidStationScreen.accentOf(kind(menu.next())));
    }

    @Nullable
    private AndroidKind kind(int unit) {
        return unit < 0 ? null : AndroidStationBlockEntity.memoryKind(menu.item(unit, PortableAndroidPrinterItem.SLOT_MEMORY));
    }

    private int cost(int unit) {
        AndroidKind kind = kind(unit);
        return AndroidStationBlockEntity.PRINT_FE * PortableAndroidPrinterItem.COST_FACTOR * (kind == null ? 1 : kind.printCost);
    }

    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** 組 unit で印刷できない理由の翻訳キー（できるなら null）。画面に出ている値で判断する。 */
    @Nullable
    private String problem(int unit) {
        if (kind(unit) == null) {
            return "gui.singulo.portable_printer.no_memory";
        }
        if (!AndroidStationBlockEntity.isKit(menu.item(unit, PortableAndroidPrinterItem.SLOT_KIT))) {
            return "gui.singulo.portable_printer.no_kit";
        }
        if (menu.energy() < cost(unit)) {
            return "gui.singulo.portable_printer.no_energy";
        }
        return null;
    }

    /** 実際に次に印刷する組（選んだ組から順に、そろっている組）。なければ -1。 */
    private int printing() {
        for (int i = 0; i < menu.units(); i++) {
            int u = (menu.next() + i) % menu.units();
            if (problem(u) == null) {
                return u;
            }
        }
        return -1;
    }

    /** 印刷できない理由（できるなら null）: 選んだ組の理由。 */
    @Nullable
    private String problem() {
        return printing() >= 0 ? null : problem(menu.next());
    }

    @Override
    protected Panel.Status status() {
        String p = problem();
        return p == null ? Panel.Status.IDLE : p.endsWith("no_energy") ? Panel.Status.NO_POWER : Panel.Status.WARN;
    }

    @Override
    protected Component statusText() {
        String p = problem();
        if (p != null) {
            return Component.translatable(p);
        }
        AndroidKind kind = kind(printing());
        return Component.translatable("gui.singulo.portable_printer.next", kind == null ? Component.empty() : kind.type().getDescription());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int target = printing();
        skin = GuiSkin.android(AndroidStationScreen.accentOf(kind(target >= 0 ? target : menu.next())));
        clearStats();
        drawFrame(g, mouseX, mouseY);
        boolean many = menu.units() > 1;
        for (int u = 0; u < menu.units(); u++) {
            int ry = PortablePrinterMenu.rowY(u);
            for (int s = 0; s < GHOSTS.length; s++) {
                var slot = menu.slots.get(PortableAndroidPrinterItem.slot(u, s));
                ghostSlot(g, leftPos + SLOT_X[s], topPos + ry, slot, item(GHOSTS[s]));
                area(SLOT_X[s], ry, 16, 16, slot.hasItem() ? List.of() : List.of(Component.translatable(SLOT_TIPS[s])));
            }
            var weapon = menu.slots.get(PortableAndroidPrinterItem.weaponSlot(menu.units(), u));
            ghostSlot(g, leftPos + PortablePrinterMenu.WEAPON_X, topPos + ry, weapon, item("android_weapon_module"));
            area(PortablePrinterMenu.WEAPON_X, ry, 16, 16, weapon.hasItem() ? List.of()
                    : List.of(Component.translatable("gui.singulo.portable_printer.weapon")));
            if (many) {
                selector(g, u, ry, u == target, u == menu.next(), mouseX, mouseY);
            }
        }
        // 印刷ボタン
        boolean ready = target >= 0;
        int bx = leftPos + PRINT_X, by = topPos + PRINT_Y;
        Panel.Press press = !ready ? Panel.Press.DISABLED
                : Panel.inside(mouseX, mouseY, bx, by, PRINT_W, PRINT_H) ? Panel.Press.HOVER : Panel.Press.NORMAL;
        int d = Panel.button(g, skin, bx, by, PRINT_W, PRINT_H, press);
        Panel.icon(g, bx + 6, by + 6 + d, Panel.Icon.PERSON, ready ? skin.accent() : skin.lo());
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.portable_printer.print"), bx + 16, by + 6 + d, PRINT_W - 20,
                ready ? skin.text() : skin.lo());
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.singulo.portable_printer.print.tip"));
        if (!ready) {
            tip.add(Component.translatable(problem()).withStyle(s -> s.withColor(GuiSkin.WARN)));
        }
        area(PRINT_X, PRINT_Y, PRINT_W, PRINT_H, tip);
        // 充電残量
        int energy = menu.energy();
        int cost = Math.max(1, cost(target >= 0 ? target : menu.next()));
        statBar(g, BAR_X, PortablePrinterMenu.barY(menu.units()), Panel.Icon.BOLT, skin.accent(), BAR_W - 12,
                (double) energy / menu.capacity(), skin.accent(), List.of(
                        Component.translatable("tooltip.singulo.energy", EnergyText.of(energy), EnergyText.of(menu.capacity())),
                        Component.translatable("gui.singulo.portable_printer.prints", energy / cost, EnergyText.of(cost)),
                        Component.translatable("gui.singulo.portable_printer.charge_hint").withStyle(s -> s.withColor(0xFF8A949E))));
    }

    /** 行の左の印: 次に印刷する組は型の色で塗った▶、選んだがそろっていない組は枠だけ、ほかは暗い点。押すと次に印刷する組を選ぶ。 */
    private void selector(GuiGraphicsExtractor g, int unit, int ry, boolean printing, boolean chosen, int mouseX, int mouseY) {
        int x = leftPos + PortablePrinterMenu.SELECT_X, y = topPos + ry;
        boolean hover = Panel.inside(mouseX, mouseY, x, y, PortablePrinterMenu.SELECT_W, 16);
        int color = AndroidStationScreen.accentOf(kind(unit)) | 0xFF000000;
        if (printing) {
            for (int i = 0; i < 4; i++) {
                g.fill(x + 2 + i, y + 4 + i, x + 3 + i, y + 12 - i, color);           // ▶
            }
        } else if (chosen) {
            g.fill(x + 2, y + 4, x + 3, y + 12, color);
            g.fill(x + 2, y + 4, x + 6, y + 5, color);
            g.fill(x + 2, y + 11, x + 6, y + 12, color);
        } else {
            g.fill(x + 3, y + 7, x + 5, y + 9, hover ? skin.text() : skin.lo());
        }
        AndroidKind kind = kind(unit);
        List<Component> tip = new ArrayList<>();
        tip.add(printing ? Component.translatable("gui.singulo.portable_printer.next", kind == null ? Component.empty() : kind.type().getDescription())
                : Component.translatable("gui.singulo.portable_printer.select"));
        String p = problem(unit);
        if (p != null) {
            tip.add(Component.translatable(p).withStyle(s -> s.withColor(GuiSkin.WARN)));
        }
        area(PortablePrinterMenu.SELECT_X, ry, PortablePrinterMenu.SELECT_W, 16, tip);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!renderStatTooltips(g, mouseX, mouseY)) {
            extractTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            if (printing() >= 0 && Panel.inside(mouseX, mouseY, leftPos + PRINT_X, topPos + PRINT_Y, PRINT_W, PRINT_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortablePrinterMenu.BUTTON_PRINT);
                return true;
            }
            for (int u = 0; u < menu.units() && menu.units() > 1; u++) {
                if (Panel.inside(mouseX, mouseY, leftPos + PortablePrinterMenu.SELECT_X, topPos + PortablePrinterMenu.rowY(u),
                        PortablePrinterMenu.SELECT_W, 16)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortablePrinterMenu.BUTTON_SELECT + u);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
