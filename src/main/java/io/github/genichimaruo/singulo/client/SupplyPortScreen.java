package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.SupplyPortBlockEntity;
import io.github.genichimaruo.singulo.android.work.SupplyPortMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * アンドロイド資材口の画面。優先の登録ごとに、見本の欄・向きのボタン・優先順位（−/+）を並べる。説明はツールチップに出す。
 */
public class SupplyPortScreen extends FramedScreen<SupplyPortMenu> {
    private static final int BUTTON = 12;
    /** 見本の欄の左上からの、向き・−・順位・+ の位置。 */
    private static final int MODE_DX = 20, MINUS_DX = 34, RANK_DX = 46, PLUS_DX = 56, BUTTON_DY = 2;

    public SupplyPortScreen(SupplyPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SupplyPortMenu.WIDTH, SupplyPortMenu.HEIGHT);
    }

    private int ex(int i) {
        return leftPos + SupplyPortMenu.entryX(i);
    }

    private int ey(int i) {
        return topPos + SupplyPortMenu.entryY(i);
    }

    private boolean used(int i) {
        return !menu.entry(i).isEmpty();
    }

    private static Panel.Icon modeIcon(int mode) {
        return switch (mode) {
            case SupplyPortBlockEntity.MODE_TAKE -> Panel.Icon.OUT;
            case SupplyPortBlockEntity.MODE_RETURN -> Panel.Icon.IN;
            default -> Panel.Icon.IN_OUT;
        };
    }

    private Panel.Press press(int i, int x, int y, int mouseX, int mouseY) {
        if (!used(i)) {
            return Panel.Press.DISABLED;
        }
        return Panel.inside(mouseX, mouseY, x, y, BUTTON, BUTTON) ? Panel.Press.HOVER : Panel.Press.NORMAL;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawFrame(g, mouseX, mouseY);
        for (int i = 0; i < SupplyPortBlockEntity.ENTRIES; i++) {
            int x = ex(i);
            int y = ey(i);
            Panel.slot(g, skin, x, y);
            int by = y + BUTTON_DY;
            int c = used(i) ? skin.text() : skin.lo();
            int d = Panel.button(g, skin, x + MODE_DX, by, BUTTON, BUTTON, press(i, x + MODE_DX, by, mouseX, mouseY));
            Panel.icon(g, x + MODE_DX + 3, by + 3 + d, modeIcon(menu.mode(i)), c);
            d = Panel.button(g, skin, x + MINUS_DX, by, BUTTON, BUTTON, press(i, x + MINUS_DX, by, mouseX, mouseY));
            Panel.minusIcon(g, x + MINUS_DX + 4, by + 3 + d, c);
            d = Panel.button(g, skin, x + PLUS_DX, by, BUTTON, BUTTON, press(i, x + PLUS_DX, by, mouseX, mouseY));
            Panel.plusIcon(g, x + PLUS_DX + 4, by + 4 + d, c);
            if (used(i)) {
                String rank = Integer.toString(menu.rank(i));
                g.drawString(font, rank, x + RANK_DX + 5 - font.width(rank) / 2, by + 2, skin.text(), false);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < SupplyPortBlockEntity.ENTRIES; i++) {
            int x = ex(i);
            int by = ey(i) + BUTTON_DY;
            if (!used(i) && Panel.inside(mouseX, mouseY, x, ey(i), 16, 16)) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.singulo.supply_port.entry"),
                        Component.translatable("gui.singulo.supply_port.entry.hint").withStyle(s -> s.withColor(0xFF8A949E))), mouseX, mouseY);
                return;
            }
            if (!used(i)) {
                continue;
            }
            if (Panel.inside(mouseX, mouseY, x + MODE_DX, by, BUTTON, BUTTON)) {
                g.renderTooltip(font, Component.translatable("gui.singulo.supply_port.mode." + menu.mode(i)), mouseX, mouseY);
                return;
            }
            if (Panel.inside(mouseX, mouseY, x + MINUS_DX, by, PLUS_DX + BUTTON - MINUS_DX, BUTTON)) {
                g.renderTooltip(font, Component.translatable("gui.singulo.supply_port.rank", menu.rank(i)), mouseX, mouseY);
                return;
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < SupplyPortBlockEntity.ENTRIES && button == 0; i++) {
            if (!used(i)) {
                continue;
            }
            int x = ex(i);
            int by = ey(i) + BUTTON_DY;
            int action = Panel.inside(mouseX, mouseY, x + MODE_DX, by, BUTTON, BUTTON) ? SupplyPortMenu.MODE
                    : Panel.inside(mouseX, mouseY, x + MINUS_DX, by, BUTTON, BUTTON) ? SupplyPortMenu.RANK_DOWN
                    : Panel.inside(mouseX, mouseY, x + PLUS_DX, by, BUTTON, BUTTON) ? SupplyPortMenu.RANK_UP : -1;
            if (action >= 0 && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i * 3 + action);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        AndroidStationScreen.fit(g, font, title, titleLabelX, titleLabelY, titleWidth(), skin.text());
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
    }
}
