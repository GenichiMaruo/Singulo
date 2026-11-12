package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.generated.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 公式アドオン Singulo Arsenal の公開の告知。初めてワールドに入ったとき、少し待ってから（参加の知らせに埋もれないように）
 * チャットに1回だけ出す。CurseForge のページへのリンクを押せる。
 * <ul>
 *   <li>出したらクライアント設定 arsenalNotice を false にして保存する（次からは出さない。true に戻せばもう一度出る）。</li>
 *   <li>Singulo Arsenal を入れていれば出さない。</li>
 * </ul>
 */
final class ArsenalNotice {
    static final String MOD_ID = "singulo_arsenal";
    static final String URL = "https://www.curseforge.com/minecraft/mc-mods/singulo-arsenal";
    /** ワールドに入ってから出すまでの間（tick）。 */
    private static final int DELAY = 60;

    /** 出すまでの残り tick（出さないなら −1）。 */
    private static int pending = -1;

    private ArsenalNotice() {}

    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        pending = shouldShow() ? DELAY : -1;
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = -1;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        if (pending < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || --pending > 0) {
            return;
        }
        pending = -1;
        if (!shouldShow()) {
            return;
        }
        ClientConfig.ARSENAL_NOTICE.set(false);
        ClientConfig.ARSENAL_NOTICE.save();
        mc.player.displayClientMessage(message(), false);
    }

    static boolean shouldShow() {
        return ClientConfig.ARSENAL_NOTICE.get() && !ModList.get().isLoaded(MOD_ID);
    }

    static Component message() {
        Component link = Component.translatable("message.singulo.arsenal_notice.link").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(URL))));
        return Component.translatable("message.singulo.arsenal_notice", link).withStyle(ChatFormatting.GOLD);
    }
}
