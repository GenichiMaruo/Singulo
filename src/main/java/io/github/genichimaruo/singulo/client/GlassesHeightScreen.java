package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import io.github.genichimaruo.singulo.network.GlassesHeightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * 測量メガネを掛ける高さを決める画面（メガネを持ってスニークして使うと開く）。▲▼で1ピクセルずつ動かし、
 * 左に自分の顔を大きく映して、掛けたときの見た目を確かめられる。決めた高さはメガネに残る（ほかの人にも同じ高さで見える）。
 */
public final class GlassesHeightScreen extends Screen {
    private final InteractionHand hand;
    /** 掛ける高さ（ピクセル。上が正）。 */
    private int pixels;

    private GlassesHeightScreen(InteractionHand hand, int pixels) {
        super(Component.translatable("gui.singulo.glasses.title"));
        this.hand = hand;
        this.pixels = pixels;
    }

    public static void open(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new GlassesHeightScreen(hand, SurveyGlassesItem.height(mc.player.getItemInHand(hand))));
        }
    }

    private int left() {
        return width / 2 - 100;
    }

    private int top() {
        return height / 2 - 60;
    }

    @Override
    protected void init() {
        int x = left() + 112;
        int y = top() + 26;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> change(1)).bounds(x, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> change(-1)).bounds(x, y + 48, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.singulo.glasses.reset"), b -> change(-pixels))
                .bounds(x + 44, y + 24, 44, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left() + 60, top() + 104, 80, 20).build());
    }

    private void change(int delta) {
        int next = Math.max(SurveyGlassesItem.MIN_HEIGHT, Math.min(SurveyGlassesItem.MAX_HEIGHT, pixels + delta));
        if (next == pixels) {
            return;
        }
        pixels = next;
        // 手元のメガネもすぐ変える（顔の見本に映すため。サーバーからの同期で同じ値に上書きされる）
        if (minecraft != null && minecraft.player != null) {
            SurveyGlassesItem.setHeight(minecraft.player.getItemInHand(hand), pixels);
        }
        io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new GlassesHeightPayload(hand == InteractionHand.OFF_HAND, pixels));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.singulo.glasses.height", (pixels > 0 ? "+" : "") + pixels),
                x + 132, y + 54, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.singulo.glasses.hint"), width / 2, y + 128, 0xA0A8B0);
        // 顔の見本: 手に持ったメガネを一時的に頭に掛けて、自分を大きく映す
        if (minecraft != null && minecraft.player != null) {
            var player = minecraft.player;
            ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack held = player.getItemInHand(hand);
            boolean swap = held.getItem() instanceof SurveyGlassesItem;
            if (swap) {
                player.setItemSlot(EquipmentSlot.HEAD, held);
            }
            try {
                g.fill(x + 4, y + 14, x + 100, y + 100, 0x60000000);
                // 顔のあたりだけを、正面から大きく映す（マウスの位置を枠の真ん中にして、顔をこちらへ向ける）。
                // yOffset は体の真ん中から映す中心までの高さ（ブロック）: 目の高さの少し上
                float face = player.getEyeHeight() - player.getBbHeight() / 2 + 0.05F;
                g.enableScissor(x + 4, y + 14, x + 100, y + 100);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 52, y + 57 + (int) (player.getEyeHeight() * 120), 120, 0, 0, player);
                g.disableScissor();
            } finally {
                if (swap) {
                    player.setItemSlot(EquipmentSlot.HEAD, worn);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
