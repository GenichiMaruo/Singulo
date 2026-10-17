package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Forge 版: NeoForge の FakePlayerFactory の代わり（Forge 1.21.1 には偽のプレイヤーがない）。
 * 「だれとして」掘ってよいか・使ってよいかを確かめるための、世界に出ないプレイヤー。通信は送らずに捨てる。
 */
public final class FakePlayerFactory {
    private FakePlayerFactory() {}

    private static final Map<ServerLevel, Map<GameProfile, FakePlayer>> PLAYERS = new WeakHashMap<>();

    public static synchronized ServerPlayer get(ServerLevel level, GameProfile profile) {
        return PLAYERS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(profile, p -> new FakePlayer(level, p));
    }

    public static final class FakePlayer extends ServerPlayer {
        FakePlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            this.connection = new NetHandler(this);
        }

        @Override
        public void tick() {}

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return true;
        }

        @Override
        public boolean canHarmPlayer(Player player) {
            return false;
        }

        @Override
        public void die(DamageSource source) {}
    }

    private static final class NetHandler extends ServerGamePacketListenerImpl {
        NetHandler(ServerPlayer player) {
            super(player.getServer(), new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}
