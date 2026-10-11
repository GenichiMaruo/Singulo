package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import io.github.genichimaruo.singulo.genesis.MassSynthesis;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 創世炉: 形・原初の種・投入の止め方・質量生成の電力。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class GenesisGameTests {
    private static final String LARGE = "large";
    private static final BlockPos CORE = new BlockPos(4, 1, 4);

    private GenesisGameTests() {}

    /** 核と、まわりの枠8・柱12を置く（missingPylon が true なら柱を1本欠く）。 */
    private static GenesisCoreBlockEntity build(GameTestHelper helper, boolean missingPylon) {
        helper.setBlock(CORE, SinguloBlocks.GENESIS_CORE.get());
        for (BlockPos p : GenesisCoreBlockEntity.FRAMES) {
            helper.setBlock(CORE.offset(p), SinguloBlocks.SIMPLE.get("genesis_frame").get());
        }
        for (int i = missingPylon ? 1 : 0; i < GenesisCoreBlockEntity.PYLONS.length; i++) {
            helper.setBlock(CORE.offset(GenesisCoreBlockEntity.PYLONS[i]), SinguloBlocks.SIMPLE.get("genesis_pylon").get());
        }
        return helper.getBlockEntity(CORE);
    }

    /** 形ができると揺籃になり、原初の種で種火が始まる。種火は 1 tick に genesisMaxInput までしか受け取らない。 */
    @GameTest(template = LARGE, timeoutTicks = 120)
    public static void formsAndKindlesWithSeed(GameTestHelper helper) {
        GenesisCoreBlockEntity core = build(helper, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.succeedWhen(() -> {
            helper.assertTrue(core.formed() && core.stage() == GenesisCoreBlockEntity.CRADLE, "形ができて揺籃になる");
            if (core.stage() == GenesisCoreBlockEntity.CRADLE) {
                ItemStack seed = new ItemStack(GenesisCoreBlockEntity.primordialSeed());
                helper.assertTrue(core.useItem(player, seed), "原初の種を使える");
                helper.assertTrue(core.stage() == GenesisCoreBlockEntity.KINDLING, "種火が始まる");
                long max = GenesisCoreBlockEntity.maxInput();
                long first = core.energy().receiveLong(max * 3, false);
                long second = core.energy().receiveLong(max, false);
                helper.assertTrue(first == max && second == 0, "1 tick に受け取るのは上限まで: " + first + ", " + second);
            }
        });
    }

    /** 柱が1本でも欠けていると形ができず、原初の種も受け付けない。 */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void missingPylonStaysUnformed(GameTestHelper helper) {
        GenesisCoreBlockEntity core = build(helper, true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(core.formed(), "柱が欠けていると形ができない");
            helper.assertFalse(core.useItem(player, new ItemStack(GenesisCoreBlockEntity.primordialSeed())), "種を受け付けない");
            helper.assertTrue(core.stage() == GenesisCoreBlockEntity.UNFORMED, "未形成のまま");
            helper.succeed();
        });
    }

    /** 核に赤石信号を入れると投入が止まり、電力を受け取らない（暴走の兆しへの備え）。 */
    @GameTest(template = LARGE, timeoutTicks = 140)
    public static void redstoneStopsTheFeed(GameTestHelper helper) {
        GenesisCoreBlockEntity core = build(helper, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(core.useItem(player, new ItemStack(GenesisCoreBlockEntity.primordialSeed())), "種火が始まる");
            helper.setBlock(CORE.below(), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(2, () -> {
                helper.assertFalse(core.feeding(), "赤石信号で投入が止まる");
                helper.assertTrue(core.energy().receiveLong(1_000_000, false) == 0, "止めている間は受け取らない");
                helper.setBlock(CORE.below(), Blocks.AIR);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(core.feeding(), "信号を切ると再開する");
                    helper.succeed();
                });
            });
        });
    }

    /** 質量生成の電力は希少度ごとに10倍。表にない物は作れず、ネザースターは伝説（希少度4）。 */
    @GameTest(template = LARGE)
    public static void synthesisCostScalesByRarity(GameTestHelper helper) {
        MassSynthesis.Entry stone = MassSynthesis.INSTANCE.entry(new ItemStack(Items.STONE));
        MassSynthesis.Entry diamond = MassSynthesis.INSTANCE.entry(new ItemStack(Items.DIAMOND));
        MassSynthesis.Entry star = MassSynthesis.INSTANCE.entry(new ItemStack(Items.NETHER_STAR));
        helper.assertTrue(stone != null && stone.tier() == 0, "石は希少度0");
        helper.assertTrue(diamond != null && diamond.tier() == 2, "ダイヤモンドは希少度2");
        helper.assertTrue(star != null && star.tier() == MassSynthesis.MAX_TIER, "ネザースターは希少度4");
        helper.assertTrue(MassSynthesis.INSTANCE.entry(new ItemStack(Items.BEDROCK)) == null, "表にない物は作れない");
        double base = MassSynthesis.baseCost();
        helper.assertTrue(Math.abs(MassSynthesis.cost(stone) - stone.mass() * base) < 1, "希少度0は質量×基本の電力");
        helper.assertTrue(Math.abs(MassSynthesis.cost(diamond) - diamond.mass() * base * 100) < 1, "希少度2は100倍");
        helper.assertTrue(MassSynthesis.cost(star) >= star.mass() * base * 10_000 - 1, "希少度4は1万倍");
        // 質量1あたり 1 GFE 以上: Pリアクターが質量から取り出せる電力（質量1あたり最大約 0.5 GFE）より重い
        helper.assertTrue(base >= 1_000_000_000L, "生成した物を炉に戻しても電力は増えない");
        helper.succeed();
    }
}
