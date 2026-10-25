package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.registry.SinguloSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * 画面のボタンを押したときの音。Panel が描いた押せるもの（ボタン・電源・レールのタブ）の上で左クリックしたら鳴らす。
 * 押せるものの場所は描くたびに覚え直すので、各画面のクリックの処理には手を入れない。
 */
final class UiClick {
    private UiClick() {}

    static void onRender(ScreenEvent.Render.Pre event) {
        Panel.CLICKABLE.clear();
    }

    static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        double x = event.getMouseX();
        double y = event.getMouseY();
        for (int[] r : Panel.CLICKABLE) {
            if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SinguloSounds.get("ui.click"), 1.0F, 0.5F));
                return;
            }
        }
    }
}
