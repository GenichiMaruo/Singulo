package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PenroseReactorScreen extends AbstractContainerScreen<PenroseReactorMenu> {
    private static final int TEXT_X = 54;
    private static final int BUTTON_Y = 84;
    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 12;
    private static final int IGNITE_X = 54;
    private static final int SPIN_X = 102;

    public PenroseReactorScreen(PenroseReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = PenroseReactorMenu.HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        for (int[] p : PenroseReactorMenu.SLOT_POS) {
            Panel.slot(g, leftPos + p[0], topPos + p[1]);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, leftPos + 8 + col * 18, topPos + 104 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, leftPos + 8 + col * 18, topPos + 162);
        }
        button(g, IGNITE_X, mouseX, mouseY);
        button(g, SPIN_X, mouseX, mouseY);
    }

    private void button(GuiGraphics g, int x, int mouseX, int mouseY) {
        boolean hover = Panel.inside(mouseX, mouseY, leftPos + x, topPos + BUTTON_Y, BUTTON_W, BUTTON_H);
        g.fill(leftPos + x, topPos + BUTTON_Y, leftPos + x + BUTTON_W, topPos + BUTTON_Y + BUTTON_H,
                hover ? Panel.GLOW : Panel.WELL_EDGE);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        int y = 20;
        for (Component line : lines()) {
            g.drawString(font, line, TEXT_X, y, Panel.TEXT, false);
            y += 10;
        }
        Component ignite = Component.translatable("gui.singulo.reactor.ignite");
        Component spin = Component.translatable("gui.singulo.reactor.spin_target");
        g.drawString(font, ignite, IGNITE_X + (BUTTON_W - font.width(ignite)) / 2, BUTTON_Y + 2, 0xFFFFFFFF, false);
        g.drawString(font, spin, SPIN_X + (BUTTON_W - font.width(spin)) / 2, BUTTON_Y + 2, 0xFFFFFFFF, false);
    }

    private List<Component> lines() {
        List<Component> out = new ArrayList<>();
        PenroseReactorBlockEntity.State state =
                PenroseReactorBlockEntity.State.values()[menu.value(PenroseReactorBlockEntity.D_STATE)];
        switch (state) {
            case IGNITING -> out.add(Component.translatable("gui.singulo.reactor.state.igniting",
                    menu.value(PenroseReactorBlockEntity.D_IGNITION_PCT), menu.value(PenroseReactorBlockEntity.D_IGNITION_SECS)));
            default -> out.add(Component.translatable("gui.singulo.reactor.state." + state.name().toLowerCase()));
        }
        double target = PenroseReactorBlockEntity.SPIN_TARGETS[menu.value(PenroseReactorBlockEntity.D_TARGET)];
        if (state == PenroseReactorBlockEntity.State.RUNNING) {
            PenroseReactorBlockEntity.Mode mode =
                    PenroseReactorBlockEntity.Mode.values()[menu.value(PenroseReactorBlockEntity.D_MODE)];
            out.add(Component.translatable("gui.singulo.reactor.mode." + mode.name().toLowerCase()));
            out.add(Component.translatable("gui.singulo.reactor.mass",
                    String.format("%.1f", menu.value(PenroseReactorBlockEntity.D_MASS_X10) / 10.0)));
            out.add(Component.translatable("gui.singulo.reactor.spin",
                    String.format("%.3f", menu.value(PenroseReactorBlockEntity.D_SPIN_X1000) / 1000.0), target));
            out.add(Component.translatable("gui.singulo.reactor.eta",
                    String.format("%.1f", menu.value(PenroseReactorBlockEntity.D_ETA_X10000) / 100.0)));
            out.add(Component.translatable("gui.singulo.reactor.output", String.format("%,d", menu.output())));
        } else {
            out.add(Component.translatable("gui.singulo.reactor.spin", "-", target));
        }
        out.add(Component.translatable("gui.singulo.reactor.buffer",
                String.format("%.2f", menu.value(PenroseReactorBlockEntity.D_BUFFER_MFE) / 1000.0)));
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            if (Panel.inside(mouseX, mouseY, leftPos + IGNITE_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_IGNITE);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + SPIN_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_SPIN);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
