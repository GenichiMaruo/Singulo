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
    private static final int TEXT_X = 56;
    private static final int BUTTON_Y = 104;
    private static final int BUTTON_W = 54;
    private static final int BUTTON_H = 14;
    private static final int IGNITE_X = 56;
    private static final int SPIN_X = 114;

    public PenroseReactorScreen(PenroseReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PenroseReactorMenu.WIDTH;
        imageHeight = PenroseReactorMenu.HEIGHT;
        inventoryLabelX = PenroseReactorMenu.INV_X;
        inventoryLabelY = PenroseReactorMenu.INV_Y - 11;
    }

    /** 入力スロットに入れる物（空のときにグレーの影を描く）。 */
    private static final String[] SLOT_ITEMS = {"singularity_seed", "mass_pellet", "jet_collector", "hawking_collector", "ergosphere_ring"};
    private static final int INTERVAL_Y = 124;
    private net.minecraft.client.gui.components.EditBox interval;

    @Override
    protected void init() {
        super.init();
        interval = new net.minecraft.client.gui.components.EditBox(font, leftPos + 112, topPos + INTERVAL_Y - 2, 40, 12,
                Component.translatable("gui.singulo.reactor.interval"));
        interval.setMaxLength(4);
        interval.setFilter(t -> t.isEmpty() || t.chars().allMatch(Character::isDigit));
        interval.setValue(Integer.toString(menu.value(PenroseReactorBlockEntity.D_INTERVAL)));
        interval.setResponder(t -> {
            if (!t.isEmpty() && minecraft != null && minecraft.gameMode != null) {
                int v = Math.min(PenroseReactorBlockEntity.MAX_FEED_INTERVAL, Integer.parseInt(t));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_INTERVAL + v);
            }
        });
        addRenderableWidget(interval);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 入力していない間は、装置の値に合わせる
        if (interval != null && !interval.isFocused()) {
            String v = Integer.toString(menu.value(PenroseReactorBlockEntity.D_INTERVAL));
            if (!v.equals(interval.getValue())) {
                interval.setValue(v);
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        // 入力中は E などで画面が閉じないように
        if (interval != null && interval.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return interval.keyPressed(key, scan, modifiers) || interval.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (menu.getCarried().isEmpty()) {
            for (int i = 0; i < PenroseReactorMenu.SLOT_POS.length; i++) {
                int[] p = PenroseReactorMenu.SLOT_POS[i];
                if (!menu.getSlot(i).hasItem() && Panel.inside(mouseX, mouseY, leftPos + p[0], topPos + p[1], 16, 16)) {
                    int k = Math.min(i, PenroseReactorBlockEntity.SLOT_OUT);
                    g.renderTooltip(font, Component.translatable("gui.singulo.reactor.slot." + k), mouseX, mouseY);
                    return;
                }
            }
        }
        if (Panel.inside(mouseX, mouseY, leftPos + TEXT_X, topPos + INTERVAL_Y - 2, 100, 12)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.reactor.interval.hint"), mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        for (int i = 0; i < PenroseReactorMenu.SLOT_POS.length; i++) {
            int[] p = PenroseReactorMenu.SLOT_POS[i];
            Panel.slot(g, leftPos + p[0], topPos + p[1]);
            if (i < SLOT_ITEMS.length && !menu.getSlot(i).hasItem()) {
                Panel.ghost(g, leftPos + p[0], topPos + p[1], SLOT_ITEMS[i]);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, leftPos + PenroseReactorMenu.INV_X + col * 18, topPos + PenroseReactorMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, leftPos + PenroseReactorMenu.INV_X + col * 18, topPos + PenroseReactorMenu.HOTBAR_Y);
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
        int y = 22;
        for (Component line : lines()) {
            g.drawString(font, line, TEXT_X, y, Panel.TEXT, false);
            y += 11;
        }
        Component ignite = Component.translatable("gui.singulo.reactor.ignite");
        Component spin = Component.translatable("gui.singulo.reactor.spin_target");
        g.drawString(font, ignite, IGNITE_X + (BUTTON_W - font.width(ignite)) / 2, BUTTON_Y + 3, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("gui.singulo.reactor.interval"), TEXT_X, INTERVAL_Y, Panel.TEXT, false);
        g.drawString(font, Component.literal("tick"), 156, INTERVAL_Y, Panel.TEXT, false);
        g.drawString(font, spin, SPIN_X + (BUTTON_W - font.width(spin)) / 2, BUTTON_Y + 3, 0xFFFFFFFF, false);
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
