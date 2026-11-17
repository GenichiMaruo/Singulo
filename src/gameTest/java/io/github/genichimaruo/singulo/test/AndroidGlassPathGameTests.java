package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/**
 * アンドロイド用のガラスと道探し（0.20.0）: 通り抜けられる生き物は、ガラスを避けずに道を引き、ガラスの上には登ろうとしない。
 * ぶつかる生き物は、ふつうのガラスと同じく避けて、上にも立つ。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidGlassPathGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void passableGlassIsNeitherAvoidedNorClimbed(GameTestHelper h) {
        BlockState gate = SinguloBlocks.ANDROID_GATE_GLASS.get().defaultBlockState();
        BlockState barrier = SinguloBlocks.ANDROID_BARRIER_GLASS.get().defaultBlockState();
        BlockState gateStairs = stairs("android_gate_glass_stairs");
        BlockState barrierStairs = stairs("android_barrier_glass_stairs");
        EntityType<? extends Mob> android = SinguloEntities.BLADE_ANDROID.get();
        EntityType<? extends Mob> zombie = EntityType.ZOMBIE;

        // 屋根つきの通路をガラスの壁がふさぐ。通り抜けられる側だけが向こうまで道を引く
        check(h, through(h, android, gate), "An android avoids gate glass");
        check(h, !through(h, android, barrier), "An android paths through barrier glass");
        check(h, through(h, zombie, barrier), "A zombie avoids barrier glass");
        check(h, !through(h, zombie, gate), "A zombie paths through gate glass");
        check(h, through(h, android, gateStairs), "An android avoids gate glass stairs");
        check(h, through(h, zombie, barrierStairs), "A zombie avoids barrier glass stairs");

        // 1つ置いたガラスの上: 通り抜けられる側は床と思わない
        check(h, !onto(h, android, gate), "An android tries to stand on gate glass");
        check(h, !onto(h, android, gateStairs), "An android tries to stand on gate glass stairs");
        check(h, onto(h, android, barrier), "An android cannot stand on barrier glass");
        check(h, !onto(h, zombie, barrier), "A zombie tries to stand on barrier glass");
        check(h, !onto(h, zombie, barrierStairs), "A zombie tries to stand on barrier glass stairs");
        check(h, onto(h, zombie, gate), "A zombie cannot stand on gate glass");
        h.succeed();
    }

    /** 透過ガラスの壁を、アンドロイドが跳ねずに歩いて抜ける（バニラの歩き方は、ガラスの中にいると思ってジャンプする）。 */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void androidWalksThroughGateGlassWithoutJumping(GameTestHelper h) {
        BlockPos goal = new BlockPos(5, 1, 1);
        through(h, SinguloEntities.BLADE_ANDROID.get(), SinguloBlocks.ANDROID_GATE_GLASS.get().defaultBlockState());
        Mob android = h.spawn(SinguloEntities.BLADE_ANDROID.get(), Vec3.atBottomCenterOf(START));
        android.goalSelector.removeAllGoals(g -> true);
        android.targetSelector.removeAllGoals(g -> true);
        double floor = h.absoluteVec(Vec3.atBottomCenterOf(START)).y;
        double[] highest = {floor};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], android.getY());
            if (android.onGround()) {
                BlockPos at = h.absolutePos(goal);
                android.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(highest[0] < floor + 0.1, "The android jumped inside gate glass: rose " + (highest[0] - floor));
            h.assertTrue(android.getX() > h.absolutePos(new BlockPos(4, 1, 1)).getX(), "Not through the glass yet: " + android.blockPosition());
        });
    }

    private static BlockState stairs(String id) {
        return SinguloBlocks.VARIANTS.get(id).get().defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
    }

    private static void check(GameTestHelper h, boolean ok, String message) {
        h.assertTrue(ok, message);
    }

    /** 床だけの広場に戻す。 */
    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y < 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** 屋根つきの1マス幅の通路（z = 1）を、x = 3 の高さ2のガラスの壁がふさぐ。向こう端まで道が届くか。 */
    private static boolean through(GameTestHelper h, EntityType<? extends Mob> type, BlockState wall) {
        clear(h);
        for (int x = 0; x < 7; x++) {
            for (int y = 1; y <= 3; y++) {
                h.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                h.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            }
            h.setBlock(new BlockPos(x, 3, 1), Blocks.STONE);
        }
        h.setBlock(new BlockPos(0, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(0, 2, 1), Blocks.STONE);
        h.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(6, 2, 1), Blocks.STONE);
        h.setBlock(new BlockPos(3, 1, 1), wall);
        h.setBlock(new BlockPos(3, 2, 1), wall);
        return reaches(h, type, new BlockPos(5, 1, 1));
    }

    /** 広場に1つだけ置いたガラスの真上へ道が届くか（そこを床と思うか）。 */
    private static boolean onto(GameTestHelper h, EntityType<? extends Mob> type, BlockState glass) {
        clear(h);
        h.setBlock(new BlockPos(3, 1, 1), glass);
        return reaches(h, type, new BlockPos(3, 2, 1));
    }

    private static boolean reaches(GameTestHelper h, EntityType<? extends Mob> type, BlockPos target) {
        Mob mob = h.spawnWithNoFreeWill(type, Vec3.atBottomCenterOf(START));
        mob.setOnGround(true);   // 出したばかりは宙に浮いた扱いで、道を引かない
        try {
            Path path = mob.getNavigation().createPath(h.absolutePos(target), 0);
            return path != null && path.canReach();
        } finally {
            mob.discard();
        }
    }
}
