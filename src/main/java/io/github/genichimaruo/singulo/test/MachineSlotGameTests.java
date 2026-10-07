package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** 触媒専用スロット・単極子アップグレード・面の設定と自動排出の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class MachineSlotGameTests {
    private static final String EMPTY = "empty";

    private MachineSlotGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    @GameTest(template = EMPTY)
    public static void catalystsOnlyGoIntoCatalystSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity m = helper.getBlockEntity(pos);
        MachineType t = m.type();
        ItemStack catalyst = new ItemStack(item("time_crystal_catalyst"));
        helper.assertTrue(!m.items().insertItem(0, catalyst.copy(), true).isEmpty(), "触媒が普通の入力スロットに入る");
        helper.assertTrue(m.items().insertItem(t.catalystSlot(), catalyst.copy(), true).isEmpty(), "触媒スロットに触媒が入らない");
        helper.assertTrue(!m.items().insertItem(t.catalystSlot(), new ItemStack(Items.IRON_INGOT), true).isEmpty(),
                "触媒スロットに触媒以外が入る");
        helper.assertTrue(m.items().insertItem(t.upgradeSlot(), new ItemStack(item("monopole_upgrade"), 2), true).getCount() == 1,
                "アップグレード枠に2個入る");
        helper.assertTrue(!m.items().insertItem(t.upgradeSlot(), new ItemStack(Items.IRON_INGOT), true).isEmpty(),
                "アップグレード枠にほかの物が入る");
        // 搬入（ホッパーなど）からも触媒スロットへ入れられる
        IItemHandler auto = m.automationItems();
        helper.assertTrue(auto.insertItem(t.catalystSlot(), catalyst.copy(), false).isEmpty(), "搬入で触媒スロットに入らない");
        helper.assertTrue(auto.extractItem(t.catalystSlot(), 1, true).isEmpty(), "触媒スロットから搬出できてしまう");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void monopoleUpgradeDoublesSpeed(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 3);
        BlockPos b = new BlockPos(5, 1, 3);
        helper.setBlock(a, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        helper.setBlock(b, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        MachineBlockEntity plain = helper.getBlockEntity(a);
        MachineBlockEntity boosted = helper.getBlockEntity(b);
        boosted.items().setStackInSlot(boosted.type().upgradeSlot(), new ItemStack(item("monopole_upgrade")));
        helper.onEachTick(() -> {
            plain.energy().setEnergy(plain.energy().getMaxEnergyStored());
            boosted.energy().setEnergy(boosted.energy().getMaxEnergyStored());
        });
        plain.items().setStackInSlot(0, new ItemStack(item("steel_ingot"), 4));
        boosted.items().setStackInSlot(0, new ItemStack(item("steel_ingot"), 4));
        helper.runAtTickTime(60, () -> {
            int p = plain.items().getStackInSlot(plain.type().outputSlot()).getCount();
            int q = boosted.items().getStackInSlot(boosted.type().outputSlot()).getCount();
            helper.assertTrue(q >= 2 * p - 1 && q > p, "単極子アップグレードで速くならない: " + p + " → " + q);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void sideConfigAndAutoEject(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get()
                .defaultBlockState().setValue(AbstractMachineBlock.FACING, Direction.NORTH));
        MachineBlockEntity m = helper.getBlockEntity(pos);
        BlockPos abs = helper.absolutePos(pos);
        // 上の面を「入力だけ」に、後ろ（南）の面を「出力・自動排出」に
        m.setSide(0, SideConfig.Face.TOP, SideConfig.INPUT, false);
        m.setSide(0, SideConfig.Face.BACK, SideConfig.OUTPUT, true);
        m.setSide(0, SideConfig.Face.LEFT, SideConfig.NONE, false);
        IItemHandler top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
        IItemHandler left = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs,
                SideConfig.directionOf(Direction.NORTH, SideConfig.Face.LEFT));
        helper.assertTrue(top != null && left == null, "面の設定が能力に効いていない");
        m.items().setStackInSlot(m.type().outputSlot(), new ItemStack(item("steel_plate"), 5));
        helper.assertTrue(top.extractItem(m.type().outputSlot(), 1, true).isEmpty(), "入力だけの面から取り出せる");
        helper.setBlock(pos.south(), Blocks.CHEST);
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(pos.south());
            helper.assertTrue(chest.getItem(0).getCount() == 5, "後ろのチェストへ自動排出されない");
        });
    }
}
