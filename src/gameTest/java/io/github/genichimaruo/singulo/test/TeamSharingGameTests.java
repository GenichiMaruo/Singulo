package io.github.genichimaruo.singulo.test;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/** 0.20.0: 持ち主と同じバニラのチームの人は、許可証で絞られたステーションも、持ち主だけのドローン発着ポートも使える（持ち主がオフラインでも）。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class TeamSharingGameTests {
    private static final BlockPos STATION = new BlockPos(1, 1, 1), PORT = new BlockPos(3, 1, 1);

    @GameTest(template = "empty")
    public static void teammatesOfTheOwnerShareDevices(GameTestHelper helper) {
        var level = helper.getLevel();
        // 持ち主（名前の違う本物のプレイヤー。GameTest のサーバーには名前の控えがないので、つないでおく）
        var server = level.getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "share_owner"), false);
        var ownerPlayer = new net.minecraft.server.level.ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, ownerPlayer, cookie);
        TestScope.ownPlayer(helper, ownerPlayer);
        UUID owner = ownerPlayer.getUUID();
        String ownerName = ownerPlayer.getScoreboardName();
        var mate = TestScope.player(helper);

        helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = helper.getBlockEntity(STATION, AndroidStationBlockEntity.class);
        station.setOwner(owner);
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PERMIT, permitFor(UUID.randomUUID()));    // ほかの人だけ登録
        helper.setBlock(PORT, SinguloBlocks.DRONE_PORT.get());
        DronePortBlockEntity port = helper.getBlockEntity(PORT, DronePortBlockEntity.class);
        port.setOwner(owner);
        helper.assertFalse(station.mayOperate(mate), "Unlisted player used a permit-restricted station");
        helper.assertFalse(port.mayOperate(mate), "Stranger used the owner's drone port");

        var scoreboard = level.getScoreboard();
        var team = scoreboard.addPlayerTeam("share_" + owner.toString().substring(9, 13));
        try {
            scoreboard.addPlayerToTeam(ownerName, team);
            helper.assertFalse(station.mayOperate(mate), "Player outside the owner's team used the station");
            scoreboard.addPlayerToTeam(mate.getScoreboardName(), team);
            helper.assertTrue(station.mayOperate(mate), "Teammate could not use the station");
            helper.assertTrue(port.mayOperate(mate), "Teammate could not use the drone port");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    private static ItemStack permitFor(UUID member) {
        ItemStack permit = new ItemStack(SinguloItems.SHIELD_PERMIT.get());
        CompoundTag m = new CompoundTag();
        m.store("id", net.minecraft.core.UUIDUtil.CODEC, member);
        m.putString("name", "someone");
        ListTag list = new ListTag();
        list.add(m);
        CompoundTag data = new CompoundTag();
        data.put("permit_members", list);
        permit.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return permit;
    }
}
