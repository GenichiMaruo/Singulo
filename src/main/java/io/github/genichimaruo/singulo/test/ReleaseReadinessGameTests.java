package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 0.8.0をmainへ統合する際に見つかった境界条件の回帰試験。 */
@GameTestHolder("singulo_progression")
@PrefixGameTestTemplate(false)
public final class ReleaseReadinessGameTests {
    private static Object field(Object object, Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static RuinAndroid guard(GameTestHelper helper) {
        var android = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        android.setNoAi(true);
        android.setNoGravity(true);
        android.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        android.makeGuard(null, helper.absolutePos(new BlockPos(2, 1, 2)), RuinAndroid.MODE_GUARD, 0);
        helper.getLevel().addFreshEntity(android);
        return android;
    }

    /** 前の注文の余りを戻す際、別注文の「素材を取りに行かない作業」を借りっぱなしにしない。 */
    @GameTest(template = "empty", batch = "release_lease")
    public static void leftoverDepositDoesNotAbandonNextLease(GameTestHelper helper) throws Exception {
        var at = new BlockPos(1, 1, 1);
        helper.setBlock(at, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = helper.getBlockEntity(at);
        RuinAndroid android = guard(helper);
        android.assignWork(helper.absolutePos(at), 1); // 注文1は完了して一覧から消えている
        android.addToCarry(new ItemStack(Items.DIRT)); // 作業で出た余り。私物にはしない
        var harvest = RecipeData.procedure(List.of(new ProcedureStep(ProcedureStep.HARVEST,
                helper.absolutePos(new BlockPos(4, 1, 4)), null, new ItemStack(Items.WHEAT))), new ItemStack(Items.WHEAT));
        var order = new WorkOrder(2, new ItemStack(Items.WHEAT), 1, null);
        order.workers.add(android.getUUID());
        var constructor = WorkOrder.StepState.class.getDeclaredConstructor(RecipeData.class, BlockPos.class, net.minecraft.world.level.block.Block.class, long.class);
        constructor.setAccessible(true);
        order.steps.add(constructor.newInstance(harvest, helper.absolutePos(new BlockPos(4, 1, 4)), Blocks.WHEAT, 1L));
        console.orders().add(order);
        Object goal = field(android, RuinAndroid.class, "workGoal");
        var after = goal.getClass().getDeclaredMethod("afterJob", WorkConsoleBlockEntity.class, WorkJob.class);
        after.setAccessible(true);
        try {
            after.invoke(goal, console, WorkJob.simple(WorkJob.Type.CRAFT, 1));
            WorkJob next = console.requestJob(android, true);
            helper.assertTrue(next.type() == WorkJob.Type.CRAFT, "着手しない次の作業が借りっぱなしで再取得できない: " + next.type());
            console.fail(next, WorkConsoleBlockEntity.Failure.DEFERRED);
            helper.assertTrue(order.steps.getFirst().inProgress == 0, "保留した作業の進行中回数を解放しない");
            helper.succeed();
        } finally {
            android.discard();
        }
    }

    /** 片付けで拾い、まだしまっていない物は、護衛が倒れても消えない。 */
    @GameTest(template = "empty", batch = "release_tidy", timeoutTicks = 60)
    public static void tidyItemsSurviveAndroidDeath(GameTestHelper helper) throws Exception {
        RuinAndroid android = guard(helper);
        Object life = field(android, RuinAndroid.class, "life");
        var drop = new ItemEntity(helper.getLevel(), android.getX(), android.getY(), android.getZ(), new ItemStack(Items.DIAMOND, 7));
        helper.getLevel().addFreshEntity(drop);
        set(life, "item", drop);
        set(life, "chest", helper.absolutePos(new BlockPos(6, 1, 6)));
        var tidy = life.getClass().getDeclaredMethod("tidy", net.minecraft.server.level.ServerLevel.class);
        tidy.setAccessible(true);
        tidy.invoke(life, helper.getLevel());
        helper.assertTrue(drop.isRemoved(), "片付けで拾う前提が成立していない");
        android.kill();
        helper.runAtTickTime(30, () -> {
            int count = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(android.blockPosition()).inflate(3),
                    e -> e.getItem().is(Items.DIAMOND)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(count == 7, "片付け中の死亡で拾った物が消失・重複した: " + count);
            helper.succeed();
        });
    }

    /** 同種の討伐をまとめても、報告済みの分や別の古い行を二重に数えない。 */
    @GameTest(template = "empty", batch = "release_report")
    public static void patrolReportCountsOnlyUnreportedKills(GameTestHelper helper) throws Exception {
        RuinAndroid android = guard(helper);
        Object patrol = field(android, RuinAndroid.class, "patrol");
        var kill = patrol.getClass().getDeclaredMethod("onKill", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.entity.LivingEntity.class);
        var report = patrol.getClass().getDeclaredMethod("report");
        kill.setAccessible(true);
        report.setAccessible(true);
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        var skeleton = net.minecraft.world.entity.EntityType.SKELETON.create(helper.getLevel());
        zombie.setPos(android.position());
        skeleton.setPos(android.position());
        try {
            kill.invoke(patrol, helper.getLevel(), skeleton);
            report.invoke(patrol); // 古い別行を報告済みにする
            kill.invoke(patrol, helper.getLevel(), zombie);
            kill.invoke(patrol, helper.getLevel(), zombie);
            var first = (net.minecraft.network.chat.Component) report.invoke(patrol);
            var firstText = (net.minecraft.network.chat.contents.TranslatableContents) first.getContents();
            helper.assertTrue(firstText.getArgs()[1].equals(2), "まとめた討伐の報告に古い別行を加算した: " + first);
            kill.invoke(patrol, helper.getLevel(), zombie);
            var next = (net.minecraft.network.chat.Component) report.invoke(patrol);
            var nextText = (net.minecraft.network.chat.contents.TranslatableContents) next.getContents();
            helper.assertTrue(nextText.getArgs()[1].equals(1), "報告済みの同種の討伐を再加算した: " + next);
            helper.assertTrue(report.invoke(patrol) == null, "報告済みなのに再度報告した");
            helper.succeed();
        } finally {
            android.discard();
        }
    }

    /** 頭上を塞がれた椅子を選んで、体をブロック内へ移動しない。空けた後は使える。 */
    @GameTest(template = "empty", batch = "release_seat")
    public static void sittingRequiresSpaceForTheAndroid(GameTestHelper helper) throws Exception {
        RuinAndroid android = guard(helper);
        var at = new BlockPos(4, 1, 2);
        helper.setBlock(at, Blocks.OAK_STAIRS);
        helper.setBlock(at.above(), Blocks.STONE);
        Object life = field(android, RuinAndroid.class, "life");
        var find = life.getClass().getDeclaredMethod("findSeat", net.minecraft.server.level.ServerLevel.class);
        find.setAccessible(true);
        try {
            helper.assertTrue(find.invoke(life, helper.getLevel()) == null, "頭上の塞がれた椅子を選んだ");
            helper.setBlock(at.above(), Blocks.AIR);
            helper.assertTrue(helper.absolutePos(at).equals(find.invoke(life, helper.getLevel())), "空間を空けても椅子を使えない");
            helper.succeed();
        } finally {
            android.discard();
        }
    }
}
