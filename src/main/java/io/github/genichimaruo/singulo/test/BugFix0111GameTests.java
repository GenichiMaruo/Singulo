package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.gravity.ZeroGravity;
import io.github.genichimaruo.singulo.gravity.ZeroGravityControllerBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.lab.RoomLightBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 0.11.1 の修正の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BugFix0111GameTests {
    private static final String EMPTY = "empty";

    private BugFix0111GameTests() {}

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    /**
     * コントロールボックス: 同じ集まりのボックスは高さを共有する。壊すと高さがアイテムに残り、置き直してもその高さのまま
     * （高さ5の安い設定に戻って、足りない電力のまま無重力が働くことがない）。
     */
    @GameTest(template = EMPTY)
    public static void controllersShareAndKeepTheirHeight(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(2, 1, 2), boxA = new BlockPos(1, 1, 3), boxB = new BlockPos(2, 1, 1);
        helper.setBlock(a, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(b, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(boxA, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        helper.setBlock(boxB, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ZeroGravityControllerBlockEntity first = helper.getBlockEntity(boxA);
        first.setHeight(9);
        ZeroGravityControllerBlockEntity second = helper.getBlockEntity(boxB);
        helper.assertTrue(second.height() == 9, "別のボックスに高さが共有されない: " + second.height());
        // 壊したときのアイテムに高さが残る
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(boxA);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs), null,
                new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1, "コントロールボックスが落ちない");
        ItemStack dropped = drops.get(0);
        var data = dropped.get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(data != null && data.copyTag().getInt("height") == 9, "壊したアイテムに高さが残らない");
        // 両方壊して、ひとつだけ置き直す: 残っていた高さ 9 のまま
        helper.setBlock(boxA, Blocks.AIR);
        helper.setBlock(boxB, Blocks.AIR);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
            BlockPos at = helper.absolutePos(boxA);
            ((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false))));
        } finally {
            player.discard();
        }
        ZeroGravityControllerBlockEntity again = helper.getBlockEntity(boxA);
        helper.assertTrue(again != null && again.height() == 9, "置き直すと高さが戻ってしまう: " + (again == null ? -1 : again.height()));
        helper.succeed();
    }

    /** ボスが出現している演出の間は、残響投影器・製造ラインを壊せない（壊す速さが0）。 */
    @GameTest(template = EMPTY)
    public static void arenaFixturesHoldDuringEmergence(GameTestHelper helper) {
        BlockPos projector = new BlockPos(1, 1, 1), line = new BlockPos(3, 1, 1);
        helper.setBlock(projector, SinguloBlocks.ECHO_PROJECTOR.get());
        helper.setBlock(line, SinguloBlocks.FACTORY_LINE.get());
        var player = helper.makeMockServerPlayerInLevel();
        try {
            var level = helper.getLevel();
            BlockPos pa = helper.absolutePos(projector), pl = helper.absolutePos(line);
            helper.assertTrue(level.getBlockState(pa).getDestroyProgress(player, level, pa) > 0, "ふだんから壊せない");
            var boss = SinguloEntities.ECHO_SENTINEL.get().create(level);
            boss.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 4))));
            boss.setNoAi(true);
            level.addFreshEntity(boss);
            boss.startEmerging();
            helper.assertTrue(level.getBlockState(pa).getDestroyProgress(player, level, pa) == 0, "出現中に残響投影器を壊せる");
            helper.assertTrue(level.getBlockState(pl).getDestroyProgress(player, level, pl) == 0, "出現中に製造ラインを壊せる");
            boss.discard();
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** アンドロイドは無重力の中でも重力が変わらず、自分で歩ける。ふつうの生き物は無重力になる。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void androidsIgnoreZeroGravity(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(new BlockPos(4, 1, 2), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        BladeAndroid android = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        android.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        android.setPersistenceRequired();
        helper.getLevel().addFreshEntity(android);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 3, 2));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(ZeroGravity.weightless(zombie), "ふつうの生き物が無重力にならない");
            helper.assertFalse(ZeroGravity.weightless(android), "アンドロイドまで無重力になった");
            android.discard();
            zombie.discard();
            helper.succeed();
        });
    }

    /** アンドロイドが精密組立台に入れるときは、アンドロイドの作りたいレシピが指定される（材料の一部が別のレシピと同じでも）。 */
    @GameTest(template = EMPTY)
    public static void androidChoosesItsRecipe(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity m = helper.getBlockEntity(new BlockPos(1, 1, 1));
        m.selectFor(item("tipler_bearing"), List.of(item("steel_plate"), item("superconducting_coil")));
        helper.assertTrue(m.selectedRecipe() != null && m.selectedRecipe().getPath().equals("precision_assembler/tipler_bearing"),
                "アンドロイドのレシピが指定されない: " + m.selectedRecipe());
        helper.succeed();
    }

    /** 印刷台・警備機ドックの個体だけがドロップ表を持つ。ふつうに出した（スポーンエッグなどの）個体は持たない。 */
    @GameTest(template = EMPTY)
    public static void onlyFacilitySpawnsDropLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BladeAndroid plain = SinguloEntities.BLADE_ANDROID.get().create(level);
        BladeAndroid printed = SinguloEntities.BLADE_ANDROID.get().create(level);
        printed.markFacilitySpawned();
        helper.assertTrue(printed.getLootTable().equals(RuinAndroid.FACILITY_LOOT), "印刷台の個体のドロップ表が違う");
        helper.assertFalse(plain.getLootTable().equals(RuinAndroid.FACILITY_LOOT), "ふつうの個体まで部品を落とす");
        var drone = SinguloEntities.SECURITY_DRONE.get().create(level);
        var dockDrone = SinguloEntities.SECURITY_DRONE.get().create(level);
        dockDrone.markFacilitySpawned();
        helper.assertTrue(dockDrone.getLootTable().equals(io.github.genichimaruo.singulo.ruin.SecurityDrone.FACILITY_LOOT), "ドックのドローンのドロップ表が違う");
        helper.assertFalse(drone.getLootTable().equals(io.github.genichimaruo.singulo.ruin.SecurityDrone.FACILITY_LOOT), "ふつうのドローンまで部品を落とす");
        helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(RuinAndroid.FACILITY_LOOT)
                != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "アンドロイドのドロップ表がない");
        // 保存しても印は残る
        var tag = new net.minecraft.nbt.CompoundTag();
        printed.addAdditionalSaveData(tag);
        helper.assertTrue(tag.getBoolean("facility_spawned"), "印が保存されない");
        helper.succeed();
    }

    /** 居室灯: レッドストーンの信号（レバー）が入ると点き、切れると消える。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void roomLightFollowsRedstone(GameTestHelper helper) {
        BlockPos light = new BlockPos(2, 2, 2);
        helper.setBlock(light, SinguloBlocks.ROOM_LIGHT.get());
        helper.assertFalse(helper.getBlockState(light).getValue(RoomLightBlock.LIT), "信号がないのに点いている");
        helper.setBlock(light.east(), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(helper.getBlockState(light).getValue(RoomLightBlock.LIT), "信号で点かない");
        helper.setBlock(light.east(), Blocks.AIR);
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(helper.getBlockState(light).getValue(RoomLightBlock.LIT), "信号が切れても消えない");
            helper.succeed();
        });
    }
}
