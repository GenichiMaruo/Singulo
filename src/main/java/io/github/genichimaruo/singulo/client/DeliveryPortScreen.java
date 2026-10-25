package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity;
import io.github.genichimaruo.singulo.android.work.DeliveryPortMenu;
import io.github.genichimaruo.singulo.machine.GuiFrame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * アンドロイド納品口の画面。ヘッダーの名前をクリックすると変えられる（右上の↺で場所の呼び名に戻す）。名前は、作業指令台で
 * 納品先を選ぶときに出る。
 */
public class DeliveryPortScreen extends FramedScreen<DeliveryPortMenu> {
    private final NameEditor names = new NameEditor();

    public DeliveryPortScreen(DeliveryPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DeliveryPortMenu.WIDTH, DeliveryPortMenu.HEIGHT);
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

    /** 名前を元に戻すボタン（ヘッダー右上。ほかの画面の電源と同じ場所）。 */
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int[] r = resetRect();
        if (named() && Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
            g.renderTooltip(font, Component.translatable("gui.singulo.android.rename.reset"), mouseX, mouseY);
        } else if (!names.editing() && overName(mouseX, mouseY)) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawFrame(g, mouseX, mouseY);
        for (int i = 0; i < 27; i++) {
            var s = menu.getSlot(i);
            Panel.slot(g, skin, leftPos + s.x, topPos + s.y);
        }
        if (named()) {
            int[] r = resetRect();
            boolean hover = Panel.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            int d = Panel.button(g, skin, r[0], r[1], r[2], r[3], hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            NameEditor.drawReset(g, r[0], r[1] + d, false, 0, skin.text());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!names.editing()) {
            AndroidStationScreen.fit(g, font, displayName(), titleLabelX, titleLabelY, titleWidth(), skin.text());
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, skin.text(), false);
    }
}
