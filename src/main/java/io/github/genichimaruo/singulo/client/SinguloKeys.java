package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.network.ToggleAreaPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import io.github.genichimaruo.singulo.network.SinguloNetwork;
import org.lwjgl.glfw.GLFW;

/** キー割り当て。重力操作道具の範囲切り替え（既定 G）。 */
public final class SinguloKeys {
    public static final KeyMapping TOGGLE_AREA = new KeyMapping("key.singulo.toggle_area", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.singulo");

    private SinguloKeys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AREA);
    }

    /** グラビトン・マニピュレーターの左右のクリックの状態（変わったときだけサーバーへ送る）。 */
    private static boolean leftDown;
    private static boolean rightDown;

    /**
     * マニピュレーターを持っている間は、左クリックで叩いたり壊したりせず、重力の操作に使う。
     * 右クリックも（スニーク中のモード切替・充電を除いて）ブロックを開いたりせず、ために使う。
     */
    static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getHand() != InteractionHand.MAIN_HAND
                || !(mc.player.getMainHandItem().getItem() instanceof io.github.genichimaruo.singulo.item.GravitonManipulatorItem)) {
            return;
        }
        if (event.isAttack() || (event.isUseItem() && !mc.player.isShiftKeyDown())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null && mc.screen == null
                && mc.player.getMainHandItem().getItem() instanceof io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
        boolean down = holding && mc.options.keyAttack.isDown();
        if (down != leftDown && mc.getConnection() != null) {
            leftDown = down;
            SinguloNetwork.sendToServer(new io.github.genichimaruo.singulo.network.ManipulatorInputPayload(false, down));
        }
        boolean right = holding && mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown();
        if (right != rightDown && mc.getConnection() != null) {
            rightDown = right;
            SinguloNetwork.sendToServer(new io.github.genichimaruo.singulo.network.ManipulatorInputPayload(true, right));
        }
        while (TOGGLE_AREA.consumeClick()) {
            if (mc.player == null) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (mc.player.getItemInHand(hand).getItem() instanceof GravityGauntletItem g && g.supportsCone()) {
                    SinguloNetwork.sendToServer(ToggleAreaPayload.INSTANCE);
                    break;
                }
            }
        }
    }
}
