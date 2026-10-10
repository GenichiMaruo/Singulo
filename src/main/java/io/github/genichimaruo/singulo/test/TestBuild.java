package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.ShapeSpec;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/** テスト用: マルチブロックを設計図どおりに建てる。 */
final class TestBuild {
    private TestBuild() {}

    /** Forge 1.20.1's vanilla mock uses a connection without a Netty channel. */
    static net.minecraft.server.level.ServerPlayer mockPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new net.minecraftforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player")) {
            @Override public boolean isCreative() { return getAbilities().instabuild; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean setGameMode(net.minecraft.world.level.GameType mode) {
                return gameMode.changeGameModeForPlayer(mode);
            }
        };
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        java.util.List<net.minecraft.server.level.ServerPlayer> players = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class, level.getServer().getPlayerList(), "f_11196_");
        players.add(player);
        level.addNewPlayer(player);
        return player;
    }

    /** 設計図どおりに建てる（controllerRel はコントローラの相対位置、back は奥の向き）。 */
    static void build(GameTestHelper helper, Blueprints.Kind kind, BlockPos controllerRel, Direction back, int size) {
        BlockPos c = helper.absolutePos(controllerRel);
        for (var e : Blueprints.layout(kind, c, back, size).entrySet()) {
            helper.getLevel().setBlockAndUpdate(e.getKey(), e.getValue());
        }
    }

    /** 形の (x, y, z) の位置（ワールドの絶対位置。helper.relativePos は absolutePos の逆にならないので使わない）。 */
    static BlockPos at(GameTestHelper helper, Blueprints.Kind kind, BlockPos controllerRel, Direction back, int size, int x, int y, int z) {
        return Shapes.spec(kind, size).pos(helper.absolutePos(controllerRel), back, x, y, z);
    }

    /** 形の (x, y, z)（外装板の位置）をマルチブロック搬入出ポートに替え、その絶対位置を返す。 */
    static BlockPos port(GameTestHelper helper, Blueprints.Kind kind, BlockPos controllerRel, Direction back, int size, int x, int y, int z) {
        if (Shapes.spec(kind, size).slot(x, y, z).kind() != ShapeSpec.Kind.PANEL) {
            throw new IllegalArgumentException("not a panel: " + x + "," + y + "," + z);
        }
        BlockPos abs = at(helper, kind, controllerRel, back, size, x, y, z);
        helper.getLevel().setBlockAndUpdate(abs, SinguloBlocks.MULTIBLOCK_PORT.get().defaultBlockState());
        return abs;
    }
}
