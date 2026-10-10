package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.reactor.MicroBlackHole;
import io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 小型ブラックホール・野良ブラックホールの蒸発・設定カード。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ExtrasGameTests {
    private static final String EMPTY = "empty";

    private ExtrasGameTests() {}

    /** ブラックホール爆弾の小さなブラックホールは、近くのモブを飲み込み、時間がたつと消える。 */
    @GameTest(template = EMPTY, timeoutTicks = MicroBlackHole.LIFE + 40)
    public static void microBlackHoleSwallowsAndVanishes(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(4, 3, 4));
        MicroBlackHole hole = new MicroBlackHole(SinguloEntities.MICRO_BLACK_HOLE.get(), helper.getLevel());
        hole.setPos(at);
        helper.getLevel().addFreshEntity(hole);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 4));
        helper.runAtTickTime(80, () -> helper.assertTrue(zombie.isRemoved(), "近くのモブが飲み込まれない"));
        helper.runAtTickTime(MicroBlackHole.LIFE + 10, () -> {
            helper.assertTrue(hole.isRemoved(), "時間がたっても消えない");
            helper.succeed();
        });
    }

    /** 野良ブラックホールは、質量が小さくなると蒸発しきって消える。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void rogueBlackHoleEvaporates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 4, 4);
        helper.setBlock(pos, SinguloBlocks.ROGUE_BLACK_HOLE.get());
        RogueBlackHoleBlockEntity hole = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        hole.setCore(RogueBlackHoleBlockEntity.VANISH_MASS + 5, 0);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(SinguloBlocks.ROGUE_BLACK_HOLE.get(), pos));
    }

    /** 野良ブラックホールの寿命は、質量5000でおよそ6時間。 */
    @GameTest(template = EMPTY)
    public static void rogueBlackHoleLifetimeIsAboutSixHours(GameTestHelper helper) {
        double m = 5000;
        double seconds = m * m * m / (3 * RogueBlackHoleBlockEntity.EVAPORATION_K);
        helper.assertTrue(Math.abs(seconds / 3600 - 6) < 0.1, "寿命が6時間でない: " + seconds / 3600);
        helper.succeed();
    }

    /** 設定カード: スニークして写し、ほかの装置に貼ると、面の設定が同じになる。 */
    @GameTest(template = EMPTY)
    public static void settingsCardCopiesSides(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 2);
        BlockPos b = new BlockPos(5, 1, 2);
        helper.setBlock(a, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        helper.setBlock(b, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        MachineBlockEntity from = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, a);
        MachineBlockEntity to = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, b);
        from.setSide(0, SideConfig.Face.TOP, SideConfig.INPUT, false);
        from.setSide(0, SideConfig.Face.BACK, SideConfig.OUTPUT, true);
        var player = TestBuild.mockPlayer(helper);
        try {
            ItemStack card = new ItemStack(SinguloItems.SETTINGS_CARD.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            player.setShiftKeyDown(true);
            card.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(a)), Direction.UP, helper.absolutePos(a), false)));
            player.setShiftKeyDown(false);
            card.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(b)), Direction.UP, helper.absolutePos(b), false)));
            helper.assertTrue(to.itemSides().packed() == from.itemSides().packed(), "面の設定が貼られない");
        } finally {
            player.discard();
        }
        helper.succeed();
    }
}
