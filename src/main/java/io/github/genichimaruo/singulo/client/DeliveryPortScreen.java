package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity;
import io.github.genichimaruo.singulo.android.work.DeliveryPortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * アンドロイド納品口の画面。上の行の名前をクリックすると変えられる（↺で場所の呼び名に戻す）。名前は、作業指令台で
 * 納品先を選ぶときに出る。
 */
public class DeliveryPortScreen extends AbstractContainerScreen<DeliveryPortMenu> {
    private static final int NAME_X = 8, NAME_Y = 6, NAME_W = 160;

    private final NameEditor names = new NameEditor();

    public DeliveryPortScreen(DeliveryPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = DeliveryPortMenu.WIDTH;
        imageHeight = DeliveryPortMenu.HEIGHT;
        inventoryLabelX = DeliveryPortMenu.INV_X;
        inventoryLabelY = DeliveryPortMenu.INV_Y - 11;
    }

    private String portName() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof DeliveryPortBlockEntity port ? port.name() : "";
    }

    /** 名前（なければ「納品口（x, y, z）」）。 */
    private Component displayName() {
        String n = portName();
        return n.isEmpty() ? Component.translatable("gui.singulo.work_console.delivery.port", menu.pos().getX(), menu.pos().getY(),
                menu.pos().getZ()) : Component.literal(n);
    }

    private boolean named() {
        return !names.editing() && !portName().isEmpty();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (named() && Panel.inside(mouseX, mouseY, leftPos + NAME_X + NAME_W - 10, topPos + NAME_Y, 10, 10)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && Panel.inside(mouseX, mouseY, leftPos + NAME_X, topPos + NAME_Y, NAME_W - 12, 11)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename"), mouseX, mouseY);
        }
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
        if (named() && Panel.inside(mouseX, mouseY, leftPos + NAME_X + NAME_W - 10, topPos + NAME_Y, 10, 10)) {
            NameEditor.reset(menu.containerId);
            return true;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + NAME_X, topPos + NAME_Y, NAME_W - 12, 11)) {
            var box = names.open(font, leftPos + NAME_X - 2, topPos + NAME_Y - 3, NAME_W, portName());
            addRenderableWidget(box);
            setFocused(box);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) {
            Panel.slot(g, leftPos + s.x, topPos + s.y);
        }
        if (named()) {
            NameEditor.drawReset(g, leftPos + NAME_X + NAME_W - 10, topPos + NAME_Y,
                    Panel.inside(mouseX, mouseY, leftPos + NAME_X + NAME_W - 10, topPos + NAME_Y, 10, 10), 0xFFD6DBE0, Panel.TEXT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!names.editing()) {
            AndroidStationScreen.fit(g, font, displayName(), NAME_X, NAME_Y + 1, NAME_W - 13, Panel.TEXT);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
    }
}
