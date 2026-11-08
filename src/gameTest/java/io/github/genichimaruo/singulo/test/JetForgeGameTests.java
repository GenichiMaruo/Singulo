package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeLayout;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 降着ジェット鍛造炉: 形の判定（分けて確かめる・崩れたら止まる）、1回の加工（材料・冷媒・電力の総量と完成品1個）、
 * 電力不足・形の崩れ・完成品の置き場がないときの停止と再開、保存と読み込み、制御核を壊したときの返却、搬入出ポート。
 * 施設は 44×16×17 なので、48×18×20 の forge テンプレートに北向き（奥が −Z）で建てる。軸は東西で、制御核から東へ加工側が伸びる。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class JetForgeGameTests {
    private static final String FORGE = "forge";
    private static final String EMPTY = "empty";
    private static final BlockPos CONTROLLER = new BlockPos(15, 1, 16);
    private static final Direction BACK = Direction.NORTH;
    private static final int SIZE = JetForgeLayout.H;
    /** 1回の加工の長さ（既定のコラプサー・インゴットのレシピ: 照射60秒）と、余裕。 */
    private static final int RUN = JetForgeBlockEntity.PRIME_TICKS + JetForgeBlockEntity.IGNITE_TICKS + JetForgeBlockEntity.FOCUS_TICKS
            + 1200 + JetForgeBlockEntity.FINISH_TICKS + JetForgeBlockEntity.COOL_TICKS;

    private JetForgeGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static JetForgeBlockEntity build(GameTestHelper helper) {
        TestBuild.build(helper, Blueprints.Kind.JET_FORGE, CONTROLLER, BACK, SIZE);
        return (JetForgeBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    /** 形の (x, y, z) のワールドの位置。 */
    private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
        return TestBuild.at(helper, Blueprints.Kind.JET_FORGE, CONTROLLER, BACK, SIZE, x, y, z);
    }

    /** 炉心のブラックホール（点火は飛ばす）と、材料・冷媒を入れる（n 回ぶん）。 */
    private static void load(JetForgeBlockEntity be, int runs) {
        be.forceBlackHole();
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN, new ItemStack(item("collapsar_billet"), runs));
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN + 1, new ItemStack(item("mass_pellet"), 16 * runs));
        be.tank().fill(new FluidStack(SinguloFluids.get("liquid_helium"), 1000 * runs), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void power(GameTestHelper helper, JetForgeBlockEntity be, boolean[] on) {
        helper.onEachTick(() -> {
            if (on[0]) {
                be.energy().receiveLong(Long.MAX_VALUE, false);
            }
        });
    }

    @GameTest(template = FORGE, timeoutTicks = 500)
    public static void jetForgeParallelReservesScaledInputsAndPersistsBatch(GameTestHelper helper) {
        JetForgeBlockEntity be=build(helper);load(be,4);
        // Vanilla's GameTest mock overrides isCreative() to always return true.
        // This interaction needs an ordinary, unconnected survival player instead.
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.ownPlayer(helper,
                new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"cassette-test")));
        helper.assertFalse(player.isCreative(),"Survival interaction fixture is creative");
        ItemStack cassette=new ItemStack(item("collapsar_parallel_cassette"),2);
        helper.assertTrue(be.useItem(player,cassette,net.minecraft.world.InteractionHand.MAIN_HAND)&&cassette.getCount()==1&&be.parallelUpgrade(),"Cassette installation not charged once");
        be.useItem(player,cassette,net.minecraft.world.InteractionHand.MAIN_HAND);helper.assertTrue(cassette.getCount()==1,"Duplicate installation consumed another cassette");
        power(helper,be,new boolean[]{true});
        helper.succeedWhen(()->{
            helper.assertTrue(be.phase()!=JetForgeBlockEntity.IDLE,"Run has not started");
            helper.assertTrue(be.batchSize()==4&&be.result().getCount()==4,"Parallel count not four");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).isEmpty()&&be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN+1).isEmpty()&&be.tank().isEmpty(),"Scaled inputs/coolant not reserved");
            var saved=be.saveWithFullMetadata();
            var copy=(JetForgeBlockEntity)BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),saved);
            helper.assertTrue(copy.parallelUpgrade()&&copy.batchSize()==4&&copy.result().getCount()==4&&copy.charged().stream().mapToInt(ItemStack::getCount).sum()==68,"Reserved batch lost on reload");
            helper.assertTrue(saved.getInt("energy_per_tick")==1_000_000_000,"Parallel power not scaled");
        });
    }

    @GameTest(template = FORGE, timeoutTicks = 500)
    public static void jetForgeParallelRespectsOutputSpaceAndCoolant(GameTestHelper helper) {
        JetForgeBlockEntity be=build(helper);load(be,4);
        var tag=be.saveWithoutMetadata();tag.putBoolean("parallel_upgrade",true);be.load(tag);
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_OUT,new ItemStack(item("collapsar_ingot"),62));power(helper,be,new boolean[]{true});
        helper.succeedWhen(()->{
            helper.assertTrue(be.phase()!=JetForgeBlockEntity.IDLE,"Run has not started");
            helper.assertTrue(be.batchSize()==2&&be.result().getCount()==2,"Output capacity did not reduce batch");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).getCount()==2&&be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN+1).getCount()==32&&be.tank().getFluidAmount()==2000,"Reduced batch consumed wrong resources");
        });
    }

    @GameTest(template=FORGE,timeoutTicks=2200)
    public static void jetForgeParallelCompletesExactlyFourProducts(GameTestHelper helper){
        var be=build(helper);load(be,4);
        var tag=be.saveWithoutMetadata();tag.putBoolean("parallel_upgrade",true);be.load(tag);
        power(helper,be,new boolean[]{true});
        helper.runAtTickTime(RUN+100,()->{
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT).getCount()==4&&be.result().isEmpty()&&be.charged().isEmpty(),"Parallel completion duplicated/lost output");
            helper.assertTrue(be.consumedTotal()>=1_200_000_000_000L,"Four batches did not pay four irradiation costs");helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 形

    /** 部品の個数が tools/jet_forge.py の数（レシピ表と説明に使う）と同じ。 */
    @GameTest(template = EMPTY)
    public static void jetForgePartCountsMatchTools(GameTestHelper helper) {
        Map<Block, Integer> parts = Blueprints.partCounts(Blueprints.Kind.JET_FORGE);
        Map<Block, Integer> want = Map.of(SinguloBlocks.JET_FORGE_CORE.get(), 1, SinguloBlocks.JET_FORGE_FOUNDATION.get(), 199,
                SinguloBlocks.JET_FORGE_TRUSS.get(), 159, SinguloBlocks.JET_FORGE_HEAT_SINK.get(), 158,
                SinguloBlocks.JET_FORGE_CONFINEMENT_COIL.get(), 104, SinguloBlocks.REACTOR_STABILIZER.get(), 8, SinguloBlocks.JET_FORGE_COLLIMATOR.get(), 96,
                SinguloBlocks.JET_FORGE_COLLECTOR.get(), 1, SinguloBlocks.JET_FORGE_LEVITATOR.get(), 1);
        helper.assertTrue(parts.equals(want), "部品の個数が違う: " + parts);
        helper.succeed();
    }

    /** 建てると形成し、部品を1つ壊すと数 tick で崩れたと分かり、直すとまた形成する（分けて確かめても見落とさない）。 */
    @GameTest(template = FORGE, timeoutTicks = 200)
    public static void jetForgeFormsBreaksAndReforms(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        BlockPos coil = at(helper, JetForgeLayout.COIL_X[1], JetForgeLayout.AXIS_Y + JetForgeLayout.COIL_R, JetForgeLayout.AXIS_Z);
        BlockPos beam = at(helper, 30, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(be.formed() && be.back() == BACK, "建てたのに形成しない");
            helper.getLevel().setBlockAndUpdate(coil, Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(be.formed(), "コイルを外しても形成したまま");
            helper.getLevel().setBlockAndUpdate(coil, SinguloBlocks.JET_FORGE_CONFINEMENT_COIL.get().defaultBlockState());
            be.recheckNow();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.formed(), "直しても形成しない");
            // ビームの通り道をふさぐと崩れる
            helper.getLevel().setBlockAndUpdate(beam, Blocks.STONE.defaultBlockState());
        });
        helper.runAtTickTime(45, () -> {
            helper.assertFalse(be.formed(), "ビームの通り道をふさいでも形成したまま");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 加工

    /** 1回の加工: 材料と冷媒を全部使い、電力はレシピどおり、完成品は1個だけ。材料がなくなると次は始めない。 */
    @GameTest(template = FORGE, timeoutTicks = RUN + 200)
    public static void jetForgeFullRunMakesExactlyOneIngot(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 1);
        power(helper, be, new boolean[]{true});
        int[] seen = new int[7];
        helper.onEachTick(() -> seen[be.phase()]++);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.phase() != JetForgeBlockEntity.IDLE, "材料がそろっているのに始まらない");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).isEmpty()
                    && be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN + 1).isEmpty() && be.tank().isEmpty(), "始めたのに材料が残っている");
            helper.assertTrue(be.working().is(item("collapsar_billet")) && be.result().is(item("collapsar_ingot")), "加工中の物が違う");
        });
        helper.succeedWhen(() -> {
            ItemStack out = be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT);
            helper.assertTrue(out.is(item("collapsar_ingot")) && out.getCount() == 1, "完成品がない: " + out);
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IDLE && be.charged().isEmpty() && be.working().isEmpty(), "終わっていない");
            helper.assertTrue(be.consumedTotal() == be.energyPerRun(), "電力の合計が違う: " + be.consumedTotal() + " / " + be.energyPerRun());
            for (int p = JetForgeBlockEntity.PRIME; p <= JetForgeBlockEntity.COOL; p++) {
                helper.assertTrue(seen[p] > 0, "通らなかった段階: " + p);
            }
            helper.assertTrue(be.hasCore(), "加工のあとにブラックホールが消えた");
        });
    }

    /** 照射中に電力が切れると止まって待ち（進み・素材を失わない）、戻ると続きから進む。 */
    @GameTest(template = FORGE, timeoutTicks = RUN + 400)
    public static void jetForgePausesWithoutPowerAndResumes(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 1);
        boolean[] on = {true};
        power(helper, be, on);
        int[] frozen = new int[2];
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 100, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IRRADIATE, "照射に入っていない: " + be.phase());
            on[0] = false;
        });
        // 蓄え（1 GFE/t × 4）が尽きるまで少しかかる
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 130, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.NO_POWER, "電力が切れても止まらない: " + be.hold());
            frozen[0] = be.phaseTicks();
        });
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 230, () -> {
            helper.assertTrue(be.phaseTicks() == frozen[0] && be.phase() == JetForgeBlockEntity.IRRADIATE, "電力がないのに進んだ");
            helper.assertTrue(be.working().is(item("collapsar_billet")), "止まっている間に素材が消えた");
            on[0] = true;
        });
        helper.succeedWhen(() -> {
            ItemStack out = be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT);
            helper.assertTrue(out.is(item("collapsar_ingot")) && out.getCount() == 1, "再開しても完成しない");
            helper.assertTrue(be.consumedTotal() == be.energyPerRun(), "止まった分の電力を余計に使った");
        });
    }

    private static final int PRIME_TO_IRRADIATE = JetForgeBlockEntity.PRIME_TICKS + JetForgeBlockEntity.IGNITE_TICKS
            + JetForgeBlockEntity.FOCUS_TICKS;

    /** 照射中に形が崩れると止まり、直すと続きから進む。 */
    @GameTest(template = FORGE, timeoutTicks = RUN + 400)
    public static void jetForgeStructureBreakPausesAndResumes(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 1);
        power(helper, be, new boolean[]{true});
        BlockPos truss = at(helper, JetForgeLayout.STOP_X, 3, JetForgeLayout.AXIS_Z);
        int[] frozen = new int[1];
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 50, () -> helper.getLevel().setBlockAndUpdate(truss, Blocks.AIR.defaultBlockState()));
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 70, () -> {
            helper.assertTrue(!be.formed() && be.hold() == JetForgeBlockEntity.BROKEN, "崩れても止まらない");
            frozen[0] = be.phaseTicks();
        });
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 120, () -> {
            helper.assertTrue(be.phaseTicks() == frozen[0], "崩れたまま進んだ");
            helper.getLevel().setBlockAndUpdate(truss, SinguloBlocks.JET_FORGE_TRUSS.get().defaultBlockState());
        });
        helper.succeedWhen(() -> {
            ItemStack out = be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT);
            helper.assertTrue(out.is(item("collapsar_ingot")) && out.getCount() == 1, "直しても完成しない");
        });
    }

    /** 完成品の置き場がない・冷媒が足りない・ブラックホールがない・電力がない・赤石信号があるときは、材料を取らずに待つ。 */
    @GameTest(template = FORGE, timeoutTicks = 200)
    public static void jetForgeDoesNotStartWhenBlocked(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN, new ItemStack(item("collapsar_billet"), 1));
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN + 1, new ItemStack(item("mass_pellet"), 16));
        boolean[] on = {false};
        power(helper, be, on);
        // 1) ブラックホールがない（電力も受け取らない）
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IDLE && be.hold() == JetForgeBlockEntity.NO_CORE, "ブラックホールなしで始まった");
            helper.assertTrue(be.energy().receiveLong(1_000_000L, true) == 0, "ブラックホールなしで電力を受け取る");
            be.forceBlackHole();
            on[0] = true;
        });
        // 2) 冷媒がない
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IDLE, "冷媒なしで始まった");
            be.tank().fill(new FluidStack(SinguloFluids.get("liquid_helium"), 1000), IFluidHandler.FluidAction.EXECUTE);
            be.items().setStackInSlot(JetForgeBlockEntity.SLOT_OUT, new ItemStack(Items.DIRT, 64));
        });
        // 3) 完成品の置き場がない
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IDLE, "置き場がないのに始まった");
            be.items().setStackInSlot(JetForgeBlockEntity.SLOT_OUT, ItemStack.EMPTY);
            helper.setBlock(CONTROLLER.south(), Blocks.REDSTONE_BLOCK);
        });
        // 4) 赤石信号で待つ
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.IDLE && be.hold() == JetForgeBlockEntity.HELD, "赤石信号でも始まった");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).getCount() == 1
                    && be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN + 1).getCount() == 16
                    && be.tank().getFluidAmount() == 1000, "始めていないのに材料が減った");
            helper.setBlock(CONTROLLER.south(), Blocks.AIR);
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(be.phase() != JetForgeBlockEntity.IDLE, "条件がそろっても始まらない");
            helper.succeed();
        });
    }

    /** 加工の途中で保存して読み込むと、段階・進み・素材・完成品・電力がそのまま戻る（増えも減りもしない）。 */
    @GameTest(template = FORGE, timeoutTicks = 600)
    public static void jetForgeSaveLoadKeepsProgress(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 2);
        power(helper, be, new boolean[]{true});
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 80, () -> {
            CompoundTag tag = be.saveWithFullMetadata();
            BlockEntity copy = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag);
            helper.assertTrue(copy instanceof JetForgeBlockEntity, "読み込めない");
            JetForgeBlockEntity c = (JetForgeBlockEntity) copy;
            helper.assertTrue(c.phase() == be.phase() && c.phaseTicks() == be.phaseTicks() && c.buffer() == be.buffer()
                    && c.consumedTotal() == be.consumedTotal(), "段階・進み・電力が戻らない");
            helper.assertTrue(ItemStack.matches(c.working(), be.working()) && ItemStack.matches(c.result(), be.result()), "加工中の物が戻らない");
            helper.assertTrue(c.charged().size() == be.charged().size(), "取り出した材料の記録が戻らない");
            int billets = 0;
            int pellets = 0;
            for (ItemStack s : c.charged()) {
                billets += s.is(item("collapsar_billet")) ? s.getCount() : 0;
                pellets += s.is(item("mass_pellet")) ? s.getCount() : 0;
            }
            helper.assertTrue(billets == 1 && pellets == 16, "取り出した材料の数が違う: " + billets + ", " + pellets);
            helper.assertTrue(c.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).getCount() == 1
                    && c.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN + 1).getCount() == 16
                    && c.tank().getFluidAmount() == 1000, "残りの材料が戻らない");
            helper.succeed();
        });
    }

    /** 仕上げの前に制御核を壊すと、取り出した材料が落ち（完成品は出ない）、ブラックホールは炉心の中心に野良として残る。 */
    @GameTest(template = FORGE, timeoutTicks = 400)
    public static void jetForgeBreakingControllerReturnsMaterials(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 1);
        power(helper, be, new boolean[]{true});
        BlockPos abs = helper.absolutePos(CONTROLLER);
        BlockPos center = at(helper, JetForgeLayout.CORE_X, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        helper.runAtTickTime(PRIME_TO_IRRADIATE - 20, () -> {
            helper.assertTrue(be.phase() == JetForgeBlockEntity.FOCUS, "収束に入っていない");
            helper.getLevel().destroyBlock(abs, false);
        });
        helper.runAtTickTime(PRIME_TO_IRRADIATE - 15, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2));
            int billets = 0;
            int pellets = 0;
            int cores = 0;
            int ingots = 0;
            for (ItemEntity e : drops) {
                ItemStack s = e.getItem();
                billets += s.is(item("collapsar_billet")) ? s.getCount() : 0;
                pellets += s.is(item("mass_pellet")) ? s.getCount() : 0;
                cores += s.is(item("singularity_core")) ? s.getCount() : 0;
                ingots += s.is(item("collapsar_ingot")) ? s.getCount() : 0;
            }
            helper.assertTrue(billets == 1 && pellets == 16 && cores == 0 && ingots == 0,
                    "落ちた物が違う: " + billets + ", " + pellets + ", " + cores + ", " + ingots);
            helper.assertTrue(helper.getLevel().getBlockEntity(center)
                    instanceof io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity rogue && rogue.stable()
                    && rogue.mass() == JetForgeBlockEntity.CORE_MASS, "ブラックホールが炉心に残らない");
            // 残ったブラックホールは減らないので、ほかの検査を引き寄せないよう片付ける
            helper.getLevel().removeBlock(center, false);
            helper.succeed();
        });
    }

    /** 点火: コアを入れて点火し、時間内に電力を注ぐと、点火の演出のあとにブラックホールができる（Pリアクターと同じく特異点の種を使い切る）。 */
    @GameTest(template = FORGE, timeoutTicks = JetForgeBlockEntity.IGNITION_SEQUENCE_TICKS + 120)
    public static void jetForgeIgnitionFormsBlackHole(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_CORE, new ItemStack(item("singularity_seed")));
        power(helper, be, new boolean[]{true});
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(be.formed() && be.blackHole() == JetForgeBlockEntity.BH_NONE, "点火の前からブラックホールがある");
            helper.assertTrue(be.ignite(), "点火できない");
            helper.assertTrue(be.coreLocked(), "点火中にコアが外せる");
        });
        helper.runAtTickTime(60, () -> helper.assertTrue(be.blackHole() == JetForgeBlockEntity.BH_IGNITING && be.ignitionCharged(),
                "電力が満ちない"));
        helper.succeedWhen(() -> {
            helper.assertTrue(be.blackHole() == JetForgeBlockEntity.BH_ACTIVE, "ブラックホールができない");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_CORE).isEmpty(), "コアが残っている");
        });
    }

    /** 点火の時間のうちに電力が届かないと失敗し、コアは残る。 */
    @GameTest(template = FORGE, timeoutTicks = 400)
    public static void jetForgeIgnitionFailsWithoutPower(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_CORE, new ItemStack(item("singularity_core")));
        helper.runAtTickTime(5, () -> helper.assertTrue(be.ignite(), "点火できない"));
        // 少しだけ注ぐ（足りない）
        helper.runAtTickTime(10, () -> be.energy().receiveLong(1_000_000_000L, false));
        helper.runAtTickTime(5 + 10 * 20 + 20, () -> {
            helper.assertTrue(be.blackHole() == JetForgeBlockEntity.BH_NONE, "電力が足りないのに点火した: " + be.blackHole());
            helper.assertTrue(be.hasSeed(), "失敗したのにコアがなくなった");
            helper.succeed();
        });
    }

    /** 制御核を壊して残った野良ブラックホールは、組み直すと取り込まれる（もう一度点火しなくてよい）。 */
    @GameTest(template = FORGE, timeoutTicks = 200)
    public static void jetForgeRemnantIsAbsorbedOnRebuild(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        BlockPos abs = helper.absolutePos(CONTROLLER);
        BlockPos center = at(helper, JetForgeLayout.CORE_X, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        helper.runAtTickTime(5, () -> {
            be.forceBlackHole();
            helper.getLevel().destroyBlock(abs, false);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(center).is(SinguloBlocks.ROGUE_BLACK_HOLE.get()), "野良ブラックホールが残らない");
            TestBuild.build(helper, Blueprints.Kind.JET_FORGE, CONTROLLER, BACK, SIZE);
            // 設計図は中心に触れない（野良ブラックホールはそのまま）
            helper.assertTrue(helper.getLevel().getBlockState(center).is(SinguloBlocks.ROGUE_BLACK_HOLE.get()), "組み直しで消えた");
        });
        helper.runAtTickTime(60, () -> {
            JetForgeBlockEntity again = (JetForgeBlockEntity) helper.getBlockEntity(CONTROLLER);
            helper.assertTrue(again.formed() && again.hasCore(), "組み直しても取り込まない");
            helper.assertTrue(helper.getLevel().getBlockState(center).isAir(), "取り込んだのに野良が残っている");
            helper.succeed();
        });
    }

    /** ブラックホールはまわりのモブを引き寄せる。 */
    @GameTest(template = FORGE, timeoutTicks = 120)
    public static void jetForgeBlackHolePullsMobs(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        BlockPos spot = at(helper, JetForgeLayout.CORE_X, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z - 3);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
        pig.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        helper.getLevel().addFreshEntity(pig);
        double[] start = new double[1];
        helper.runAtTickTime(3, () -> {
            be.forceBlackHole();
            start[0] = pig.position().distanceTo(be.core());
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(pig.isRemoved() || pig.position().distanceTo(be.core()) < start[0] - 0.5,
                    "引き寄せない: " + start[0] + " → " + pig.position().distanceTo(be.core()));
            helper.succeed();
        });
    }

    /** 照射中のビームに入ると大けがをする（防具を無視）。 */
    @GameTest(template = FORGE, timeoutTicks = 500)
    public static void jetForgeBeamDealsHeavyDamage(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        load(be, 1);
        power(helper, be, new boolean[]{true});
        BlockPos inBeam = at(helper, 30, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        helper.runAtTickTime(PRIME_TO_IRRADIATE + 20, () -> {
            var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(helper.getLevel());
            golem.moveTo(inBeam.getX() + 0.5, inBeam.getY() - 1.2, inBeam.getZ() + 0.5);
            golem.setNoAi(true);
            golem.setNoGravity(true);
            helper.getLevel().addFreshEntity(golem);
            float before = golem.getHealth();
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(golem.isDeadOrDying() || before - golem.getHealth() >= JetForgeBlockEntity.BEAM_DAMAGE * 0.9F,
                        "ビームのダメージが小さい: " + before + " → " + golem.getHealth());
                helper.succeed();
            });
        });
    }

    /** 液体ヘリウム・カートリッジを材料の枠に入れると、タンクへ注いで空のカートリッジを返す（タンクが満ちたら注がない）。 */
    @GameTest(template = FORGE, timeoutTicks = 200)
    public static void jetForgeDrainsHeliumCartridges(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        helper.runAtTickTime(3, () -> {
            ItemStack cartridges = new ItemStack(item("helium_cartridge"), 70);
            ItemStack rest = be.automationItems().insertItem(JetForgeBlockEntity.SLOT_IN, cartridges, false);
            helper.assertTrue(rest.getCount() == 6, "材料の枠にカートリッジが入らない: " + rest);
        });
        helper.runAtTickTime(160, () -> {
            int tank = be.tank().getFluidAmount();
            ItemStack left = be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN);
            ItemStack empties = be.items().getStackInSlot(JetForgeBlockEntity.SLOT_EMPTY);
            helper.assertTrue(tank % 250 == 0 && tank / 250 == empties.getCount() && left.getCount() + empties.getCount() == 64,
                    "注いだ量と空の容器の数が合わない: " + tank + " mB, 残り " + left.getCount() + ", 空 " + empties.getCount());
            helper.assertTrue(empties.is(item("empty_coolant_cartridge")) && tank > 0, "注いでいない");
            // 空の容器は押し出す口から取り出せる
            helper.assertTrue(!be.ejectItems().extractItem(1, 64, true).isEmpty(), "空の容器を取り出せない");
            helper.succeed();
        });
    }

    /** 液体ヘリウム・カートリッジは液体ヘリウム250 mBの容器（空の冷媒カートリッジに詰める・空ける）。 */
    @GameTest(template = EMPTY)
    public static void heliumCartridgeIsAFluidContainer(GameTestHelper helper) {
        var helium = SinguloFluids.get("liquid_helium");
        var empty = new io.github.genichimaruo.singulo.machine.CoolantCartridges.Handler(new ItemStack(item("empty_coolant_cartridge")));
        helper.assertTrue(empty.fill(new FluidStack(helium, 200), IFluidHandler.FluidAction.EXECUTE) == 0, "200 mBで詰まった");
        helper.assertTrue(empty.fill(new FluidStack(helium, 1000), IFluidHandler.FluidAction.EXECUTE) == 250, "250 mB詰まらない");
        helper.assertTrue(empty.getContainer().is(item("helium_cartridge")), "液体ヘリウム・カートリッジにならない");
        var full = new io.github.genichimaruo.singulo.machine.CoolantCartridges.Handler(new ItemStack(item("helium_cartridge")));
        FluidStack out = full.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(out.getAmount() == 250 && out.getFluid().isSame(helium) && full.getContainer().is(item("empty_coolant_cartridge")),
                "空けられない: " + out.getAmount());
        helper.succeed();
    }

    /**
     * ポートは形ができる前から電力・物・液体の口を見せる（他MODのパイプやケーブルが「口がない」と覚えないように）。
     * 形ができる前に手に入れた口が、形ができたあとはそのままコントローラにつながる。
     */
    @GameTest(template = FORGE, timeoutTicks = 100)
    public static void jetForgePortExposesHandlersBeforeForming(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        BlockPos port = TestBuild.port(helper, Blueprints.Kind.JET_FORGE, CONTROLLER, BACK, SIZE, 30, 0, 8);
        BlockPos coil = at(helper, JetForgeLayout.COIL_X[1], JetForgeLayout.AXIS_Y + JetForgeLayout.COIL_R, JetForgeLayout.AXIS_Z);
        helper.getLevel().setBlockAndUpdate(coil, Blocks.AIR.defaultBlockState());
        Object[] cached = new Object[3];
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(be.formed(), "コイルを外したのに形成している");
            var energy = Capabilities.get(helper.getLevel(), Capabilities.EnergyStorage.BLOCK, port, Direction.DOWN);
            var items = Capabilities.get(helper.getLevel(), Capabilities.ItemHandler.BLOCK, port, Direction.DOWN);
            var fluids = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, port, Direction.DOWN);
            helper.assertTrue(energy != null && items != null && fluids != null, "形ができる前のポートに口がない");
            helper.assertTrue(energy.receiveEnergy(1000, false) == 0 && items.getSlots() == 0, "形ができる前に受け取った");
            cached[0] = energy;
            cached[2] = fluids;
            helper.getLevel().setBlockAndUpdate(coil, SinguloBlocks.JET_FORGE_CONFINEMENT_COIL.get().defaultBlockState());
            be.recheckNow();
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(be.formed(), "直しても形成しない");
            be.forceBlackHole();
        });
        helper.runAtTickTime(14, () -> {
            // 形ができる前に手に入れた口（パイプが覚えている口）のまま流せる
            var energy = (net.minecraftforge.energy.IEnergyStorage) cached[0];
            helper.assertTrue(energy.receiveEnergy(5000, false) == 5000 && be.buffer() == 5000, "形ができても覚えていた口から電力が入らない");
            var fluids = (IFluidHandler) cached[2];
            helper.assertTrue(fluids.fill(new FluidStack(SinguloFluids.get("liquid_helium"), 500), IFluidHandler.FluidAction.EXECUTE) == 500,
                    "覚えていた口から液体が入らない");
            helper.succeed();
        });
    }

    /** 基礎をマルチブロック搬入出ポートに替えると、そこから材料・冷媒・電力が入り、完成品は隣のチェストへ押し出される。 */
    @GameTest(template = FORGE, timeoutTicks = 120)
    public static void jetForgePortsFeedAndEject(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        BlockPos port = TestBuild.port(helper, Blueprints.Kind.JET_FORGE, CONTROLLER, BACK, SIZE, 30, 0, 8);
        BlockPos chestPos = port.below();
        helper.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.formed(), "ポートに替えると形成しない");
            IItemHandler items = Capabilities.get(helper.getLevel(), Capabilities.ItemHandler.BLOCK, port, Direction.DOWN);
            helper.assertTrue(items != null, "ポートに物の口がない");
            ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItem(items, new ItemStack(item("mass_pellet"), 16), false);
            helper.assertTrue(rest.isEmpty() && be.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN).getCount() == 16, "材料が入らない");
            ItemStack junk = net.minecraftforge.items.ItemHandlerHelper.insertItem(items, new ItemStack(Items.DIRT, 4), false);
            helper.assertTrue(junk.getCount() == 4, "材料でない物が入った");
            IFluidHandler fluids = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, port, Direction.DOWN);
            helper.assertTrue(fluids != null && fluids.fill(new FluidStack(SinguloFluids.get("liquid_helium"), 2000),
                    IFluidHandler.FluidAction.EXECUTE) == 2000, "冷媒が入らない");
            helper.assertTrue(fluids.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    IFluidHandler.FluidAction.EXECUTE) == 0, "冷媒でない液体が入った");
            be.items().setStackInSlot(JetForgeBlockEntity.SLOT_OUT, new ItemStack(item("collapsar_ingot"), 3));
        });
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
            int n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                n += chest.getItem(i).is(item("collapsar_ingot")) ? chest.getItem(i).getCount() : 0;
            }
            helper.assertTrue(n == 3 && be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT).isEmpty(), "完成品が押し出されない: " + n);
        });
    }

    /** A stopped beam must not hurt entities while consuming no power. */
    @GameTest(template = FORGE, timeoutTicks = 100)
    public static void jetForgeStoppedBeamDoesNotDamage(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        helper.runAtTickTime(5, () -> {
            CompoundTag saved = be.saveWithoutMetadata();
            saved.putInt("energy_per_tick", 1_000_000);
            be.load(saved);
            be.forceBlackHole();
            be.forcePhase(JetForgeBlockEntity.IRRADIATE, 100);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.NO_POWER, "Beam did not stop without power");
            BlockPos p = at(helper, 30, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
            var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(helper.getLevel());
            golem.moveTo(p.getX() + 0.5, p.getY() - 1.2, p.getZ() + 0.5);
            golem.setNoAi(true);
            golem.setNoGravity(true);
            helper.getLevel().addFreshEntity(golem);
            float health = golem.getHealth();
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(golem.getHealth() == health && !golem.isOnFire(), "Stopped beam still damages entities");
                helper.succeed();
            });
        });
    }

    /** A save immediately after a non-sync progress tick must include that progress. */
    @GameTest(template = FORGE, timeoutTicks = 100)
    public static void jetForgeProgressMarksChunkDirtyBetweenPackets(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        helper.runAtTickTime(5, () -> {
            be.forceBlackHole();
            be.forcePhase(JetForgeBlockEntity.PRIME, 1);
            JetForgeBlockEntity.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState(), be);
            var chunk = helper.getLevel().getChunkAt(be.getBlockPos());
            chunk.setUnsaved(false);
            int before = be.phaseTicks();
            JetForgeBlockEntity.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState(), be);
            helper.assertTrue(be.phaseTicks() == before + 1 && chunk.isUnsaved(), "Progress did not mark the chunk dirty");
            helper.succeed();
        });
    }

    /** An oversized data-pack result must wait instead of overfilling an empty output. */
    @GameTest(template = FORGE, timeoutTicks = 100)
    public static void jetForgeOversizedResultWaitsWithoutLoss(GameTestHelper helper) {
        JetForgeBlockEntity be = build(helper);
        helper.runAtTickTime(5, () -> {
            be.forceBlackHole();
            be.forcePhase(JetForgeBlockEntity.COOL, JetForgeBlockEntity.COOL_TICKS);
            CompoundTag saved = be.saveWithoutMetadata();
            saved.put("result", new ItemStack(item("collapsar_ingot"), 65).save(new CompoundTag()));
            be.load(saved);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.OUTPUT_FULL && be.phase() == JetForgeBlockEntity.COOL,
                    "Oversized result did not wait");
            helper.assertTrue(be.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT).isEmpty() && be.result().getCount() == 65,
                    "Oversized result was lost or overfilled output");
            helper.succeed();
        });
    }

    /** A missing footprint chunk must pause every tick, even across several cell sweeps. */
    @GameTest(template = FORGE, timeoutTicks = 180)
    public static void jetForgeMissingChunkStaysPausedAcrossSweeps(GameTestHelper helper) {
        JetForgeBlockEntity original = build(helper);
        boolean[] loaded = {true};
        JetForgeBlockEntity be = new JetForgeBlockEntity(original.getBlockPos(), original.getBlockState()) {
            @Override protected boolean shapeChunksLoaded(net.minecraft.server.level.ServerLevel level) {
                return loaded[0] && super.shapeChunksLoaded(level);
            }
        };
        helper.getLevel().setBlockEntity(be);
        load(be, 1);
        power(helper, be, new boolean[]{true});
        int[] frozen = new int[1];
        long[] consumed = new long[1];
        helper.runAtTickTime(20, () -> loaded[0] = false);
        helper.runAtTickTime(22, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.UNLOADED, "Missing chunk did not pause processing");
            frozen[0] = be.phaseTicks();
            consumed[0] = be.consumedTotal();
        });
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.UNLOADED && be.phase() == JetForgeBlockEntity.PRIME
                    && be.phaseTicks() == frozen[0] && be.consumedTotal() == consumed[0],
                    "A later validation slice resumed processing with a missing chunk");
            loaded[0] = true;
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(be.hold() == JetForgeBlockEntity.OK && be.phaseTicks() > frozen[0], "Reload did not resume processing");
            helper.succeed();
        });
    }
    /** カセットは実際の右クリックで、どの部品からでも積める。積めたら画面は開かない。画面の枠からも出し入れできる。 */
    @GameTest(template="forge",timeoutTicks=40)
    public static void jetForgeCassetteInstallsThroughRealClickOnAnyPart(GameTestHelper helper) {
        JetForgeBlockEntity be=build(helper);
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.ownPlayer(helper,
                new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"cassette-click")));
        ItemStack cassette=new ItemStack(item("collapsar_parallel_cassette"),2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,cassette);
        var shape=io.github.genichimaruo.singulo.forge.JetForgeLayout.SPEC;
        var part=shape.pos(be.getBlockPos(),be.getBlockState().getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING).getOpposite(),io.github.genichimaruo.singulo.forge.JetForgeLayout.COIL_X[0],8+io.github.genichimaruo.singulo.forge.JetForgeLayout.COIL_R,8);
        helper.assertTrue(io.github.genichimaruo.singulo.multiblock.Blueprints.controllerOf(helper.getLevel(),part)!=null,"Fixture must click a formed part");
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(part),net.minecraft.core.Direction.UP,part,false);
        player.gameMode.useItemOn(player,helper.getLevel(),cassette,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        helper.assertTrue(be.parallelUpgrade()&&cassette.getCount()==1,"Right-clicking a part did not install exactly one cassette");
        helper.assertTrue(player.containerMenu==player.inventoryMenu,"Installing must not also open the screen");
        // 2個目は受け取らない（受け取らなければ、右クリックはいつもどおり画面を開く）
        helper.assertFalse(be.useItem(player,cassette,net.minecraft.world.InteractionHand.MAIN_HAND),"A second cassette must not be taken");
        // 画面の枠: 取り出すと外れ、戻すと付く
        var menu=new io.github.genichimaruo.singulo.machine.DeviceMenu(0,player.getInventory(),io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.JET_FORGE,be.getBlockPos(),be.screenItems(),
                io.github.genichimaruo.singulo.machine.SyncedInts.client(io.github.genichimaruo.singulo.machine.DeviceMenu.Forge.COUNT),null);
        var slot=menu.getSlot(io.github.genichimaruo.singulo.machine.DeviceMenu.Forge.SLOT_CASSETTE);
        helper.assertTrue(slot.hasItem(),"Upgrade slot must show the installed cassette");
        helper.assertTrue(!slot.remove(1).isEmpty()&&!be.parallelUpgrade(),"Taking the cassette must uninstall it");
        helper.assertTrue(slot.mayPlace(new ItemStack(item("collapsar_parallel_cassette")))&&!slot.mayPlace(new ItemStack(item("collapsar_billet"))),"Upgrade slot only accepts cassettes");
        slot.set(new ItemStack(item("collapsar_parallel_cassette")));
        helper.assertTrue(be.parallelUpgrade(),"Placing the cassette must install it");
        helper.succeed();
    }
}
