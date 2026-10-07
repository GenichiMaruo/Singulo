package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.network.ToggleAreaPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** キー割り当て。重力操作道具の範囲切り替え（既定 G）。 */
public final class SinguloKeys {
    public static final KeyMapping TOGGLE_AREA = new KeyMapping("key.singulo.toggle_area", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.singulo");

    private SinguloKeys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AREA);
    }

    /** グラビトン・マニピュレーターの左クリックの状態（変わったときだけサーバーへ送る）。 */
    private static boolean leftDown;

    /** マニピュレーターを持っている間は、左クリックで叩いたり壊したりせず、重力の操作に使う。 */
    static void onInteraction(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof io.github.genichimaruo.singulo.item.GravitonManipulatorItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null && mc.screen == null
                && mc.player.getMainHandItem().getItem() instanceof io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
        boolean down = holding && mc.options.keyAttack.isDown();
        if (down != leftDown && mc.getConnection() != null) {
            leftDown = down;
            PacketDistributor.sendToServer(new io.github.genichimaruo.singulo.network.ManipulatorLeftPayload(down));
        }
        while (TOGGLE_AREA.consumeClick()) {
            if (mc.player == null) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (mc.player.getItemInHand(hand).getItem() instanceof GravityGauntletItem g && g.supportsCone()) {
                    PacketDistributor.sendToServer(ToggleAreaPayload.INSTANCE);
                    break;
                }
            }
        }
    }
}
