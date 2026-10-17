package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Forge 版: NeoForge の RegisterMenuScreensEvent と同じ書き方で、画面を登録する（クライアントの準備のときに呼ぶ）。 */
public final class RegisterMenuScreensEvent {
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
            MenuScreens.ScreenConstructor<M, U> factory) {
        MenuScreens.register(type, factory);
    }
}
