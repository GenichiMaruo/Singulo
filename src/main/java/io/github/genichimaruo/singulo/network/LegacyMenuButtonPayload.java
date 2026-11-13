package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.dye.RgbDyeStationMenu;
import io.github.genichimaruo.singulo.food.FoodPrinterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Full-width menu actions: vanilla 1.20.1 button packets truncate the ID to a byte. */
public record LegacyMenuButtonPayload(int containerId, int buttonId) {
    public static void encode(LegacyMenuButtonPayload p, FriendlyByteBuf b) {
        b.writeVarInt(p.containerId());
        b.writeInt(p.buttonId());
    }
    public static LegacyMenuButtonPayload decode(FriendlyByteBuf b) {
        return new LegacyMenuButtonPayload(b.readVarInt(), b.readInt());
    }
    static void handle(LegacyMenuButtonPayload p, NetworkEvent.Context context) {
        apply(context.getSender(), p);
    }
    /** Runs only on the server game thread; validates the currently open menu and its range. */
    public static boolean apply(ServerPlayer player, LegacyMenuButtonPayload p) {
        if (player == null || player.isSpectator()) return false;
        var menu = player.containerMenu;
        if (menu.containerId != p.containerId() || !menu.stillValid(player)
                || !(menu instanceof RgbDyeStationMenu || menu instanceof FoodPrinterMenu)) return false;
        boolean accepted = menu.clickMenuButton(player, p.buttonId());
        if (accepted) menu.broadcastChanges();
        return accepted;
    }
}