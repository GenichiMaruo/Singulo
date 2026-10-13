package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.network.RenameAndroidPayload;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * GUI の名前の欄をクリックしたときの入力欄。Enter か欄の外のクリックで決め、Esc でやめる。
 * 入力中は、持ち物を閉じるキー（E など）が効かないように、キーをすべて入力欄へ渡す。
 */
final class NameEditor {
    @Nullable
    private EditBox box;

    boolean editing() {
        return box != null;
    }

    /** 入力欄を開く（画面に加える部品を返す）。 */
    EditBox open(Font font, int x, int y, int w, String current) {
        box = new EditBox(font, x, y, w, 14, Component.translatable("gui.singulo.android.rename"));
        box.setMaxLength(RenameAndroidPayload.MAX_LENGTH);
        box.setValue(current);
        box.setFocused(true);
        box.setCanLoseFocus(false);
        box.setBordered(true);
        return box;
    }

    @Nullable
    EditBox box() {
        return box;
    }

    /** 決める（サーバーへ送る）。閉じた入力欄を返す（画面から外す）。 */
    @Nullable
    EditBox commit(int containerId) {
        EditBox b = box;
        if (b != null) {
            io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new RenameAndroidPayload(containerId, b.getValue()));
        }
        box = null;
        return b;
    }

    @Nullable
    EditBox cancel() {
        EditBox b = box;
        box = null;
        return b;
    }

    /** 名前を元に戻す（型の愛称へ）。 */
    static void reset(int containerId) {
        io.github.genichimaruo.singulo.network.SinguloNetwork.sendToServer(new RenameAndroidPayload(containerId, ""));
    }

    /** 小さな「元に戻す」ボタン（10×10。丸い矢印）。 */
    static void drawReset(net.minecraft.client.gui.GuiGraphics g, int x, int y, boolean hover, int face, int ink) {
        g.fill(x, y + 1, x + 10, y + 9, face);
        g.fill(x + 1, y, x + 9, y + 10, face);
        int c = hover ? 0xFFFFFFFF : ink;
        // 丸い矢印（右上が開いた輪と、矢じり）
        g.fill(x + 3, y + 2, x + 6, y + 3, c);
        g.fill(x + 2, y + 3, x + 3, y + 7, c);
        g.fill(x + 3, y + 7, x + 7, y + 8, c);
        g.fill(x + 7, y + 5, x + 8, y + 7, c);
        g.fill(x + 6, y + 1, x + 7, y + 4, c);
        g.fill(x + 5, y + 3, x + 8, y + 4, c);
    }

    /** キーを入力欄へ。Enter なら決め、Esc ならやめる。戻り値: 0 なにもしない、1 決めた、2 やめた、3 入力欄が使った。 */
    int key(int keyCode, int scanCode, int modifiers) {
        if (box == null) {
            return 0;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return 1;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return 2;
        }
        box.keyPressed(keyCode, scanCode, modifiers);
        return 3;
    }
}
