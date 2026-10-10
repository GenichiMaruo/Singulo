package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity;
import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.wormhole.UnstableMouthItem;
import io.github.genichimaruo.singulo.wormhole.WormholeData;
import io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity;
import net.minecraft.core.BlockPos;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** 段階5の残り（ワームホール・ダークマター・マニピュレーターの円錐範囲）の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Stage5WormholeGameTests {
    private static final String EMPTY = "empty";

    private Stage5WormholeGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    // ------------------------------------------------------------------ 生成・固定化

    /** ワームホール生成器（5×5×5 の球）を建てる。コントローラは (3,2,1)（下から2段目の手前の中央）、球は南（+Z）へ。 */
    private static final BlockPos GENERATOR = new BlockPos(3, 2, 1);

    @GameTest(template = EMPTY, timeoutTicks = 260)
    public static void wormholeGeneratorMakesPairWithOneGigawattForTenSeconds(GameTestHelper helper) {
        TestBuild.build(helper, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.WORMHOLE_GENERATOR, GENERATOR,
                net.minecraft.core.Direction.SOUTH, 5);
        WormholeGeneratorBlockEntity gen = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, GENERATOR);
        helper.onEachTick(() -> gen.energy().receiveEnergy(Integer.MAX_VALUE, false));
        helper.runAtTickTime(100, () -> helper.assertTrue(gen.output().getStackInSlot(0).isEmpty(), "10秒たつ前にできた"));
        helper.succeedWhen(() -> {
            ItemStack a = gen.output().getStackInSlot(0);
            ItemStack b = gen.output().getStackInSlot(1);
            helper.assertTrue(a.getItem() instanceof UnstableMouthItem && b.getItem() instanceof UnstableMouthItem, "口ができない");
            WormholeData da = SinguloComponents.get(a, SinguloComponents.WORMHOLE.get());
            WormholeData db = SinguloComponents.get(b, SinguloComponents.WORMHOLE.get());
            helper.assertTrue(da != null && db != null && da.pair() == db.pair(), "2つの口が対になっていない");
            helper.assertTrue(gen.core().equals(helper.absolutePos(new BlockPos(3, 3, 3))), "球の中心がずれている");
        });
    }

    /** 外殻の代わりにマルチブロック搬入出ポートを置いても形成でき、ポートから電力を入れ、できた口を取り出せる。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void wormholeGeneratorIoPortAnywhere(GameTestHelper helper) {
        TestBuild.build(helper, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.WORMHOLE_GENERATOR, GENERATOR,
                net.minecraft.core.Direction.SOUTH, 5);
        BlockPos portPos = (TestBuild.port(helper, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.WORMHOLE_GENERATOR,
                GENERATOR, net.minecraft.core.Direction.SOUTH, 5, 3, 4, 3));                // 天井の角寄り
        WormholeGeneratorBlockEntity gen = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, GENERATOR);
        helper.succeedWhen(() -> {
            helper.assertTrue(gen.formed(), "ポートを置くと形成されない");
            var energy = io.github.genichimaruo.singulo.compat.Capabilities.get(helper.getLevel(), io.github.genichimaruo.singulo.compat.Capabilities.EnergyStorage.BLOCK, portPos, null);
            helper.assertTrue(energy != null && energy.receiveEnergy(1000, true) > 0, "ポートから電力が入らない");
            var items = io.github.genichimaruo.singulo.compat.Capabilities.get(helper.getLevel(), io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK, portPos, null);
            helper.assertTrue(items != null, "ポートから口を取り出せない");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void stabilizerSealsMouthsAndRejectsExpiredOnes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.WORMHOLE_STABILIZER.get());
        WormholeStabilizerBlockEntity st = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        helper.onEachTick(() -> st.energy().setEnergy(st.energy().getMaxEnergyStored()));
        ItemStack[] pair = UnstableMouthItem.createPair(helper.getLevel());
        long pairId = SinguloComponents.get(pair[0], SinguloComponents.WORMHOLE.get()).pair();
        ItemStack old = pair[0].copy();
        SinguloComponents.set(old, SinguloComponents.WORMHOLE.get(), new WormholeData(1, helper.getLevel().getGameTime() - UnstableMouthItem.LIFETIME - 1));
        helper.assertTrue(UnstableMouthItem.expired(old, helper.getLevel()), "60秒過ぎた口が消えない");
        helper.assertTrue(st.automationItems().insertItem(0, old, false).getCount() == 1, "時間切れの口を受け付けた");
        st.automationItems().insertItem(0, pair[0], false);
        st.automationItems().insertItem(1, pair[1], false);
        st.automationItems().insertItem(2, new ItemStack(item("exotic_matter"), 4), false);
        st.automationItems().insertItem(WormholeStabilizerBlockEntity.SLOT_CASING, new ItemStack(item("wormhole_mouth_casing"), 2), false);
        helper.succeedWhen(() -> {
            for (int i = 0; i < 2; i++) {
                ItemStack s = st.items().getStackInSlot(i);
                helper.assertTrue(WormholeStabilizerBlockEntity.isSealed(s), "固定化されない");
                helper.assertTrue(SinguloComponents.get(s, SinguloComponents.WORMHOLE.get()).pair() == pairId, "対が変わった");
            }
            helper.assertTrue(st.items().getStackInSlot(2).isEmpty(), "エキゾチック物質を2個ずつ使っていない");
            helper.assertTrue(st.items().getStackInSlot(WormholeStabilizerBlockEntity.SLOT_CASING).isEmpty(), "筐体を1個ずつ使っていない");
        });
    }

    // ------------------------------------------------------------------ 口とポート

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void wormholePortsLinkEnergyItemsAndFluids(GameTestHelper helper) {
        long pair = helper.getLevel().random.nextLong();
        BlockPos mouthA = new BlockPos(1, 1, 1);
        BlockPos mouthB = new BlockPos(6, 1, 6);
        BlockPos portA = new BlockPos(2, 1, 1);
        BlockPos portB = new BlockPos(5, 1, 6);
        for (BlockPos m : new BlockPos[]{mouthA, mouthB}) {
            helper.setBlock(m, SinguloBlocks.WORMHOLE_MOUTH.get());
            WormholeMouthBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, m);
            be.setData(new WormholeData(pair, 0));
            be.fuel().insertItem(0, new ItemStack(item("exotic_matter"), 4), false);
        }
        helper.setBlock(portA, SinguloBlocks.WORMHOLE_PORT.get());
        helper.setBlock(portB, SinguloBlocks.WORMHOLE_PORT.get());
        // 向こう側（B のポートの隣）の入れ物
        helper.setBlock(portB.west(), Blocks.CHEST);
        helper.setBlock(portB.above(), SinguloBlocks.CONTAINMENT_TANK.get());
        helper.setBlock(portB.north(), SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
        helper.runAtTickTime(5, () -> {
            BlockPos a = helper.absolutePos(portA);
            IItemHandler items = Capabilities.get(helper.getLevel(), Capabilities.ItemHandler.BLOCK, a, null);
            IEnergyStorage energy = Capabilities.get(helper.getLevel(), Capabilities.EnergyStorage.BLOCK, a, null);
            IFluidHandler fluids = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, a, null);
            helper.assertTrue(items != null && energy != null && fluids != null, "ポートが能力を持たない");
            // 向こうのチェスト（27）とアンカーの触媒スロット（1）が並んで見える
            helper.assertTrue(items.getSlots() == 28, "向こうの入れ物のスロットが見えない: " + items.getSlots());
            ItemStack rest = new ItemStack(Items.IRON_INGOT, 16);
            for (int i = 0; i < items.getSlots() && !rest.isEmpty(); i++) {
                rest = items.insertItem(i, rest, false);
            }
            helper.assertTrue(rest.getCount() == 8, "喉3×3 の帯域（8個/tick）で止まらない: 残り " + rest.getCount());
            ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, portB.west());
            helper.assertTrue(chest.getItem(0).getCount() == 8, "向こうのチェストに届かない");
            // 向こう側で電力を受けられるのはタンクとアンカー
            int sent = energy.receiveEnergy(1000, false);
            WorldlineAnchorBlockEntity anchor = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, portB.north());
            ContainmentTankBlockEntity tank = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, portB.above());
            int arrived = anchor.energy().getEnergyStored() + tank.energy().getEnergyStored();
            helper.assertTrue(sent == 1000 && arrived == 1000, "電力が向こうへ届かない: " + sent + " / " + arrived);
            int filled = fluids.fill(ContainmentTankBlockEntity.darkMatter(500), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(filled == 500 && tank.amount() == 500, "液体が向こうへ届かない: " + filled);
            WormholeMouthBlockEntity ma = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, mouthA);
            helper.assertTrue(ma.partner() != null, "対の口が見つからない");
            helper.succeed();
        });
    }

    /** ポート番号がちがうとつながらず、そろえるとつながる。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void wormholePortsLinkOnlyMatchingChannels(GameTestHelper helper) {
        long pair = helper.getLevel().random.nextLong();
        BlockPos mouthA = new BlockPos(1, 1, 1);
        BlockPos mouthB = new BlockPos(6, 1, 6);
        BlockPos portA = new BlockPos(2, 1, 1);
        BlockPos portB = new BlockPos(5, 1, 6);
        for (BlockPos m : new BlockPos[]{mouthA, mouthB}) {
            helper.setBlock(m, SinguloBlocks.WORMHOLE_MOUTH.get());
            WormholeMouthBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, m);
            be.setData(new WormholeData(pair, 0));
            be.fuel().insertItem(0, new ItemStack(item("exotic_matter"), 4), false);
        }
        helper.setBlock(portA, SinguloBlocks.WORMHOLE_PORT.get());
        helper.setBlock(portB, SinguloBlocks.WORMHOLE_PORT.get());
        helper.setBlock(portB.west(), Blocks.CHEST);
        io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity a = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, portA);
        io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity b = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, portB);
        a.setChannel(42);
        b.setChannel(7);
        helper.runAtTickTime(5, () -> {
            IItemHandler items = Capabilities.get(helper.getLevel(), Capabilities.ItemHandler.BLOCK, helper.absolutePos(portA), null);
            helper.assertTrue(items != null && items.getSlots() == 0, "番号がちがうのにつながった");
            b.setChannel(42);
            helper.runAfterDelay(2, () -> {
                IItemHandler linked = Capabilities.get(helper.getLevel(), Capabilities.ItemHandler.BLOCK, helper.absolutePos(portA), null);
                helper.assertTrue(linked != null && linked.getSlots() == 27, "同じ番号でつながらない");
                helper.assertTrue(a.channel() == 42 && b.channel() == 42, "番号が保たれない");
                b.setChannel(128);
                helper.assertTrue(b.channel() == 0, "127を超えた番号が0に戻らない");
                helper.succeed();
            });
        });
    }

    /** 3×3 の喉で働けるポートは口に近い順に8個まで。9個目は止まり、光らない。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void wormholeMouthRunsOnlyNearestPorts(GameTestHelper helper) {
        long pair = helper.getLevel().random.nextLong();
        BlockPos mouthA = new BlockPos(1, 1, 1);
        BlockPos mouthB = new BlockPos(1, 3, 6);
        for (BlockPos m : new BlockPos[]{mouthA, mouthB}) {
            helper.setBlock(m, SinguloBlocks.WORMHOLE_MOUTH.get());
            WormholeMouthBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, m);
            be.setData(new WormholeData(pair, 0, true));
            be.fuel().insertItem(0, new ItemStack(item("exotic_matter"), 4), false);
        }
        // 口から近い順: (2..5,1,1) と (2..5,1,2) の8個、いちばん遠いのは (6,1,1)
        java.util.List<BlockPos> ports = new java.util.ArrayList<>();
        for (int x = 2; x <= 5; x++) {
            ports.add(new BlockPos(x, 1, 1));
            ports.add(new BlockPos(x, 1, 2));
        }
        BlockPos far = new BlockPos(6, 1, 1);
        ports.add(far);
        for (BlockPos p : ports) {
            helper.setBlock(p, SinguloBlocks.WORMHOLE_PORT.get());
        }
        helper.runAtTickTime(25, () -> {
            WormholeMouthBlockEntity a = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, mouthA);
            helper.assertTrue(a.portLimit() == 8 && a.rankedPorts().size() == 9, "ポートの数え方が違う: " + a.rankedPorts().size());
            for (BlockPos p : ports) {
                io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity port = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, p);
                boolean lit = helper.getBlockState(p).getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.LIT);
                if (p.equals(far)) {
                    helper.assertFalse(port.active() || lit, "上限を超えたポートが働いている");
                } else {
                    helper.assertTrue(port.active() && lit, "近いポートが働かない（光らない）: " + p);
                }
            }
            helper.succeed();
        });
    }

    /** 燃料が切れても口は消えずに閉じて休み、エキゾチック物質を入れるとまた開く。 */
    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void wormholeMouthClosesInsteadOfVanishing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.WORMHOLE_MOUTH.get());
        WormholeMouthBlockEntity m = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        m.setData(new WormholeData(helper.getLevel().random.nextLong(), 0, true));
        helper.runAtTickTime(WormholeMouthBlockEntity.SHRINK_TICKS + 20, () -> {
            helper.assertBlockPresent(SinguloBlocks.WORMHOLE_MOUTH.get(), pos);
            helper.assertTrue(m.size() == 0, "燃料が切れても閉じない: " + m.size());
            m.fuel().insertItem(0, new ItemStack(item("exotic_matter"), 1), false);
            helper.runAfterDelay(WormholeMouthBlockEntity.GROW_TICKS + 10, () -> {
                helper.assertTrue(m.size() == 1, "エキゾチック物質を入れても開かない: " + m.size());
                helper.succeed();
            });
        });
    }

    /** 初めて置いた口は、燃料なしでも5分開いている。置き直した口は閉じた状態から。 */
    @GameTest(template = EMPTY)
    public static void wormholeMouthFirstPlacementGrace(GameTestHelper helper) {
        ItemStack fresh = new ItemStack(SinguloBlocks.WORMHOLE_MOUTH.get());
        SinguloComponents.set(fresh, SinguloComponents.WORMHOLE.get(), new WormholeData(5, 0));
        BlockPos first = new BlockPos(2, 1, 2);
        helper.setBlock(first, SinguloBlocks.WORMHOLE_MOUTH.get());
        WormholeMouthBlockEntity a = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, first);
        a.applyComponentsFromItemStack(fresh);
        helper.assertTrue(a.grace() == WormholeMouthBlockEntity.GRACE_TICKS && a.size() == 1, "初めて置いた口に猶予がない");
        helper.assertTrue(a.data() != null && a.data().placed(), "置いた印がつかない");
        ItemStack again = new ItemStack(SinguloBlocks.WORMHOLE_MOUTH.get());
        SinguloComponents.set(again, SinguloComponents.WORMHOLE.get(), a.data());
        BlockPos second = new BlockPos(5, 1, 5);
        helper.setBlock(second, SinguloBlocks.WORMHOLE_MOUTH.get());
        WormholeMouthBlockEntity b = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, second);
        b.applyComponentsFromItemStack(again);
        helper.assertTrue(b.grace() == 0 && b.size() == 0, "置き直した口にまで猶予がつく");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wormholeMouthUpkeepScalesWithThroatArea(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.WORMHOLE_MOUTH.get());
        WormholeMouthBlockEntity m = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        helper.assertTrue(WormholeMouthBlockEntity.area(1) == 9 && WormholeMouthBlockEntity.area(3) == 49, "喉の面積が違う");
        helper.assertTrue(Math.abs(m.upkeepPerTick() - 1.0) < 1e-9, "3×3 の維持費が基準と違う");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 重力閉じ込めタンク

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void containmentTankLeaksWithoutPowerAndKeepsContentsAsItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.CONTAINMENT_TANK.get());
        ContainmentTankBlockEntity tank = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        helper.assertTrue(tank.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "ダークマター以外も入る");
        tank.tank().fill(ContainmentTankBlockEntity.darkMatter(5000), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(tank.amount() < 5000 && tank.amount() >= 4800, "電力なしで漏れない: " + tank.amount());
            int before = tank.amount();
            tank.energy().setEnergy(tank.energy().getMaxEnergyStored());
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(tank.amount() == before, "電力があるのに漏れる");
                ItemStack components = tank.collectComponents();
                helper.assertTrue(SinguloComponents.getOrDefault(components, SinguloComponents.DARK_MATTER.get(), 0) == before,
                        "壊したときに中身がアイテムへ移らない");
                ItemStack stack = new ItemStack(SinguloBlocks.CONTAINMENT_TANK.get());
                SinguloComponents.set(stack, SinguloComponents.DARK_MATTER.get(), before);
                BlockPos other = new BlockPos(5, 1, 5);
                helper.setBlock(other, SinguloBlocks.CONTAINMENT_TANK.get());
                ContainmentTankBlockEntity placed = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, other);
                placed.applyComponentsFromItemStack(stack);
                helper.assertTrue(placed.amount() == before, "置き直すと中身が戻らない");
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ マニピュレーター

    @GameTest(template = EMPTY)
    public static void manipulatorConeAndDarkMatterFuel(GameTestHelper helper) {
        ServerPlayer player = TestBuild.mockPlayer(helper);
        try {
            player.getAbilities().instabuild = false;
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 4));
            player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);   // +X を向く
            Zombie front1 = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
            Zombie front2 = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 5));
            Zombie behind = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 1));
            for (Zombie z : new Zombie[]{front1, front2, behind}) {
                z.setNoAi(true);
            }
            GravitonManipulatorItem manipulator = SinguloItems.GRAVITON_MANIPULATOR.get();
            var targets = manipulator.coneTargets(helper.getLevel(), player, GravityGauntletItem.Mode.CRUSH);
            helper.assertTrue(targets.size() == 2 && targets.contains(front1) && targets.contains(front2),
                    "前方の円錐の対象が違う: " + targets.size());
            ItemStack stack = new ItemStack(manipulator);
            manipulator.toggleCone(player, stack);
            helper.assertTrue(GravityGauntletItem.cone(stack), "円錐に切り替わらない");
            manipulator.toggleCone(player, stack);
            helper.assertTrue(!GravityGauntletItem.cone(stack), "1体に戻らない");

            // エキゾチック物質がなくても、タンクのダークマターで動く
            ItemStack tankItem = new ItemStack(SinguloBlocks.CONTAINMENT_TANK.get());
            SinguloComponents.set(tankItem, SinguloComponents.DARK_MATTER.get(), 500);
            player.getInventory().setItem(3, tankItem);
            helper.assertTrue(ExoticCharge.draw(player, stack, GravitonManipulatorItem.CHARGE_PER_MATTER, true),
                    "ダークマターを燃料にできない");
            helper.assertTrue(SinguloComponents.getOrDefault(player.getInventory().getItem(3), SinguloComponents.DARK_MATTER.get(), 0) == 250,
                    "ダークマターを250 mB 使っていない");
            for (Zombie z : new Zombie[]{front1, front2, behind}) {
                z.discard();
            }
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
