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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 0.11.1 の修正の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BugFix0111GameTests {
    private static final String EMPTY = "empty";

    private BugFix0111GameTests() {}

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    @GameTest(template = "empty")
    public static void alternativesRespectTheActualCraftingSlot(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        var console = (io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, pos);
        var broad = net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS);
        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_PLANKS));
        grid.set(1, new ItemStack(Items.OAK_PLANKS));
        var method = console.getClass().getDeclaredMethod("validAlternatives", net.minecraft.world.item.crafting.CraftingRecipe.class,
                List.class, int.class, net.minecraft.world.item.crafting.Ingredient.class);
        method.setAccessible(true);
        // Vanilla permits mirrored shapes: keep a second row to disambiguate orientation.
        var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(Singulo.id("review_slot_alternatives"), "",
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC, 2, 2,
                net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY, broad,
                        net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_PLANKS),
                        net.minecraft.world.item.crafting.Ingredient.of(Items.COBBLESTONE),
                        net.minecraft.world.item.crafting.Ingredient.EMPTY), new ItemStack(Items.STICK));
        grid.set(3, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue((Boolean) method.invoke(console, recipe, grid, 0, broad), "Valid alternatives were rejected");
        helper.assertTrue(!(Boolean) method.invoke(console, recipe, grid, 1, broad), "A different slot's broad ingredient leaked into a strict slot");
        var assign = console.getClass().getDeclaredMethod("alternativeSlots", net.minecraft.world.item.crafting.CraftingRecipe.class, List.class);
        assign.setAccessible(true);
        @SuppressWarnings("unchecked")
        var slots = (List<net.minecraft.world.item.crafting.Ingredient>) assign.invoke(null, recipe, grid);
        helper.assertTrue(slots.get(0) == broad && slots.get(1).getItems().length == 1,
                "Shaped ingredients were not assigned to their actual positions");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void generatedRoomLightsUseTheirLevers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var level = helper.getLevel();
        var template = level.getStructureManager().getOrCreate(Singulo.id("lab/private_rooms"));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, level.random, 2);
        var lights = template.filterBlocks(origin, settings, SinguloBlocks.ROOM_LIGHT.get());
        helper.assertTrue(lights.size() == 8, "Expected a light in every private room");
        for (var light : lights) {
            BlockPos leverPos = light.pos().west().below();
            var leverState = level.getBlockState(leverPos);
            helper.assertTrue(leverState.getBlock() instanceof net.minecraft.world.level.block.LeverBlock, "Missing room lever");
            ((net.minecraft.world.level.block.LeverBlock) leverState.getBlock()).pull(leverState, level, leverPos);
            helper.assertTrue(level.getBlockState(light.pos()).getValue(RoomLightBlock.LIT), "Generated lever did not light its room");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void controllersShareHeightAcrossMixedPanels(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        helper.setBlock(new BlockPos(1, 1, 2), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.RAYLEIGH_PANEL.get());
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        BlockPos a = new BlockPos(1, 1, 3), b = new BlockPos(3, 1, 3);
        helper.setBlock(a, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        helper.setBlock(b, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ZeroGravityControllerBlockEntity first = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, a);
        ZeroGravityControllerBlockEntity second = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, b);
        first.setHeight(9);
        helper.assertTrue(second.height() == 9, "Mixed power group must share its height");
        second.setHeight(3);
        helper.assertTrue(first.height() == 3, "Either controller must be able to lower the group's height");
        helper.succeed();
    }

    /**
     * コントロールボックス: 同じ集まりのボックスは高さを共有する。壊すと高さがアイテムに残り、置き直してもその高さのまま
     * （高さ5の安い設定に戻って、足りない電力のまま無重力が働くことがない）。
     */
    @GameTest(template = EMPTY)
    public static void controllersShareAndKeepTheirHeight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(2, 1, 2), boxA = new BlockPos(1, 1, 3), boxB = new BlockPos(2, 1, 1);
        helper.setBlock(a, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(b, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(boxA, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        helper.setBlock(boxB, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ZeroGravityControllerBlockEntity first = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, boxA);
        first.setHeight(9);
        ZeroGravityControllerBlockEntity second = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, boxB);
        helper.assertTrue(second.height() == 9, "別のボックスに高さが共有されない: " + second.height());
        // 壊したときのアイテムに高さが残る
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(boxA);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs), null,
                new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1, "コントロールボックスが落ちない");
        ItemStack dropped = drops.get(0);
        var data = net.minecraft.world.item.BlockItem.getBlockEntityData(dropped);
        helper.assertTrue(data != null && data.getInt("height") == 9, "壊したアイテムに高さが残らない");
        // 両方壊して、ひとつだけ置き直す: 残っていた高さ 9 のまま
        helper.setBlock(boxA, Blocks.AIR);
        helper.setBlock(boxB, Blocks.AIR);
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
            BlockPos at = helper.absolutePos(boxA);
            ((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false))));
        } finally {
            player.discard();
        }
        ZeroGravityControllerBlockEntity again = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, boxA);
        helper.assertTrue(again != null && again.height() == 9, "置き直すと高さが戻ってしまう: " + (again == null ? -1 : again.height()));
        helper.succeed();
    }

    /** ボスが出現している演出の間は、残響投影器・製造ラインを壊せない（壊す速さが0）。 */
    @GameTest(template = EMPTY)
    public static void arenaFixturesHoldDuringEmergence(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        BlockPos projector = new BlockPos(1, 1, 1), line = new BlockPos(3, 1, 1);
        helper.setBlock(projector, SinguloBlocks.ECHO_PROJECTOR.get());
        helper.setBlock(line, SinguloBlocks.FACTORY_LINE.get());
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
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

    /**
     * パネル受電器は電力をためない: 集まり全体の1 tick 分を超えては受け取らず、電力が届かなくなると、すぐ無重力が止まる
     * （コントロールボックスを高くしてから壊しても、ためた電力で働き続けない）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void receiverHoldsNoBuffer(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        BlockPos zero = new BlockPos(2, 1, 2), box = new BlockPos(2, 1, 3), receiver = new BlockPos(3, 1, 2), source = new BlockPos(4, 1, 2);
        helper.setBlock(zero, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(box, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(source, SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        ZeroGravityControllerBlockEntity c = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, box);
        c.setHeight(12);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(zero);
        helper.runAtTickTime(30, () -> {
            io.github.genichimaruo.singulo.gravity.GravityReceiverBlockEntity r = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, receiver);
            helper.assertTrue(ZeroGravity.inZone(level, abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5), "電力があるのに無重力にならない");
            long demand = r.demand();
            long took = r.energy().receiveLong(Long.MAX_VALUE / 2, false);
            helper.assertTrue(r.stored() <= demand, "1 tick 分より多く受け取った: " + r.stored() + " / " + demand + "（" + took + "）");
            helper.setBlock(source, Blocks.AIR);                 // 電力を止め、ボックスも壊す（安い高さに戻る）
            helper.setBlock(box, Blocks.AIR);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertFalse(ZeroGravity.inZone(level, abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5), "電力がないのに無重力が続く");
            helper.assertFalse(level.getBlockState(abs).getValue(io.github.genichimaruo.singulo.gravity.GravityPanelBlock.POWERED), "電力がないのに働いている");
            helper.succeed();
        });
    }

    /** アンドロイドは無重力の空間では宙を飛び、上にある行き先へも上がっていける（重力の属性は変えない）。外に出れば歩く。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void androidsFlyInZeroGravity(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
            }
        }
        helper.setBlock(new BlockPos(4, 1, 2), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(new BlockPos(5, 1, 2), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        BladeAndroid android = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        // This test drives navigation itself; nearby fixtures must not supply
        // combat targets or random walking goals that replace its destination.
        for (String name : List.of("goalSelector", "targetSelector")) {
            var field = net.minecraft.world.entity.Mob.class.getDeclaredField(name);
            field.setAccessible(true);
            ((net.minecraft.world.entity.ai.goal.GoalSelector) field.get(android)).removeAllGoals(goal -> true);
        }
        android.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        android.setPersistenceRequired();
        helper.getLevel().addFreshEntity(android);
        Vec3 up = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 5, 3)));
        helper.onEachTick(() -> {
            if (android.floating() && android.getNavigation().isDone() && android.position().distanceToSqr(up) > 0.5) {
                android.getNavigation().moveTo(up.x, up.y, up.z, 1.0);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(android.floating(), "無重力の中で飛ばない: " + android.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))));
            helper.assertFalse(ZeroGravity.weightless(android), "アンドロイドの重力の属性が変わった");
            helper.assertTrue(android.position().distanceToSqr(up) < 1.5, "上の行き先まで飛べない: " + android.position() + " → " + up);
            android.discard();
        });
    }

    /** 水の池（ガラスで囲み、上はガラスの縁が水面より4マス高い。立てる陸はない）。下の角が (1, 1, 1)、水は y 1〜4。 */
    private static void pool(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 0; y <= 8; y++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 0;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS.defaultBlockState()
                            : y <= 4 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** 充電が切れるまで、池の底を行ったり来たり泳がせる（水中は泳いでいる間だけ充電が減る）。 */
    private static void swimUntilEmpty(GameTestHelper helper, BladeAndroid a) {
        Vec3[] goals = {Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 5))), Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)))};
        helper.onEachTick(() -> {
            if (a.state() == RuinAndroid.STATE_IDLE && a.getNavigation().isDone()) {
                Vec3 to = goals[(int) (helper.getTick() / 15 % 2)];
                a.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
        });
    }

    private static BladeAndroid swimmer(GameTestHelper helper, int charge) {
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))));
        a.setPersistenceRequired();
        net.minecraft.nbt.CompoundTag saved = a.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        saved.putInt("charge", charge);
        a.load(saved);
        helper.getLevel().addFreshEntity(a);
        return a;
    }

    /** アンドロイドは水の中でも自由に動け、おぼれない（空気も体力も減らない）。水中で止まっている間は充電が減らず、泳ぐと減る。 */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void androidsSwimWithoutDrowning(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        pool(helper);
        BladeAndroid a = swimmer(helper, RuinAndroid.CHARGE_MAX);
        a.makeGuard(null, helper.absolutePos(new BlockPos(3, 0, 3)), RuinAndroid.MODE_GUARD, 30);   // 持ち場にいて、うろうろ泳がない
        int[] at = new int[2];
        helper.runAtTickTime(40, () -> at[0] = a.charge());           // 置いた直後に落ち着くまでの動きは数えない
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(a.getAirSupply() == a.getMaxAirSupply(), "水の中で空気が減る: " + a.getAirSupply());
            helper.assertTrue(a.getHealth() == a.getMaxHealth(), "水の中で体力が減る: " + a.getHealth());
            helper.assertTrue(a.floating(), "水の中で自由に動けない");
            helper.assertTrue(a.charge() == at[0], "水中で止まっているのに充電が減る: " + at[0] + " → " + a.charge());
            at[1] = a.charge();
        });
        Vec3[] goals = {Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 5))), Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)))};
        helper.onEachTick(() -> {
            if (helper.getTick() > 70 && a.getNavigation().isDone()) {
                Vec3 to = goals[(int) (helper.getTick() / 15 % 2)];            // 池の中を行ったり来たり泳ぐ
                a.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(a.charge() < at[1], "水中を泳いでも充電が減らない: " + a.position() + " under=" + a.isUnderWater());
            a.discard();
            helper.succeed();
        });
    }

    /** 水中で充電が切れ、近くに陸がなければ、水面まで浮かび上がって充電する（日が出ていればソーラー、出ていなければあお向けに浮かぶ）。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void androidsSurfaceToChargeWithoutLand(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        pool(helper);
        BladeAndroid a = swimmer(helper, 2);
        swimUntilEmpty(helper, a);
        helper.succeedWhen(() -> {
            int s = a.state();
            helper.assertTrue(s == RuinAndroid.STATE_SOLAR || s == RuinAndroid.STATE_FLOAT_REST, "水面で充電しない: state=" + s);
            helper.assertTrue(Math.abs(a.getY() - a.floatY()) < 0.4, "水面に浮かんでいない: y=" + a.getY());
            a.discard();
        });
    }

    /** 上に水面がない水の中（ガラスの天井の下）で充電が切れると、浮かび上がらずに底まで沈み、座って充電する。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void androidsChargeOnSeabedUnderCeiling(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        pool(helper);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);         // 水のすぐ上を天井でふさぐ
            }
        }
        BladeAndroid a = swimmer(helper, 2);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 3, 3))));
        swimUntilEmpty(helper, a);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.state() == RuinAndroid.STATE_SEABED_REST, "水の底で充電しない: state=" + a.state());
            helper.assertTrue(a.getY() < helper.absolutePos(new BlockPos(3, 1, 3)).getY() + 0.3, "底まで沈まない: y=" + a.getY());
            a.discard();
        });
    }

    /** アンドロイドは無重力の中でも重力の属性が変わらない（宙を飛んで動く）。ふつうの生き物は無重力になる。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void androidsIgnoreZeroGravity(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        helper.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 4), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 3), SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(new BlockPos(4, 1, 2), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        BladeAndroid android = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        android.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        android.setPersistenceRequired();
        helper.getLevel().addFreshEntity(android);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 3, 4));   // 押し合わないよう、別の列に
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
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        helper.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity m = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, new BlockPos(1, 1, 1));
        m.selectFor(item("tipler_bearing"), List.of(item("steel_plate"), item("superconducting_coil")));
        helper.assertTrue(m.selectedRecipe() != null && m.selectedRecipe().getPath().equals("precision_assembler/tipler_bearing"),
                "アンドロイドのレシピが指定されない: " + m.selectedRecipe());
        helper.succeed();
    }

    /** 印刷台・警備機ドックの個体だけがドロップ表を持つ。ふつうに出した（スポーンエッグなどの）個体は持たない。 */
    @GameTest(template = EMPTY)
    public static void onlyFacilitySpawnsDropLoot(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
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
        helper.assertTrue(level.getServer().getLootData().getLootTable(RuinAndroid.FACILITY_LOOT)
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
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
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

    /** 動力炉の部屋の型紙を回して置くと、制御盤（とほかの向きのある物）も部屋に合わせて回る。 */
    @GameTest(template = EMPTY)
    public static void reactorConsoleTurnsWithItsRoom(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var level = helper.getLevel();
        var template = level.getStructureManager().getOrCreate(Singulo.id("lab/reactor"));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        for (net.minecraft.world.level.block.Rotation rotation : net.minecraft.world.level.block.Rotation.values()) {
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation);
            var found = template.filterBlocks(origin, settings, SinguloBlocks.REACTOR_CONSOLE.get());
            helper.assertTrue(found.size() == 1, "制御盤が型紙にない");
            var state = found.get(0).state();
            // 型紙では北向き（炉心のほう）。回した分だけ向きも回る
            helper.assertTrue(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == rotation.rotate(Direction.NORTH),
                    "回転 " + rotation + " で制御盤の向きが部屋に合わない: " + state);
        }
        helper.succeed();
    }
}
