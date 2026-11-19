package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.ItemCharging;
import io.github.genichimaruo.singulo.item.NeutrinoScannerItem;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ChargeSlotGameTests {
    private static final BlockPos AT = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void smesSharesItsInputRateAcrossSources(GameTestHelper h) {
        int rate = SmesCellBlockEntity.Module.MODULE_RATE;
        SmesCellBlockEntity west = module(h, AT.west(), Direction.EAST);
        SmesCellBlockEntity target = module(h, AT, Direction.NORTH);
        SmesCellBlockEntity east = module(h, AT.east(), Direction.WEST);
        west.energy().setEnergy(rate * 4);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(target.energy().getEnergyStored() > 0, "No energy from first source"))
                .thenExecute(() -> {
                    h.assertTrue(target.energy().getEnergyStored() == rate, "Single source did not fill one tick's budget");
                    east.energy().setEnergy(rate * 4);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    h.assertTrue(target.energy().getEnergyStored() == 2 * rate, "Two sources exceeded the input rate");
                    h.assertTrue(west.energy().getEnergyStored() + east.energy().getEnergyStored() == 6 * rate,
                            "Sources lost more than the target received");
                })
                .thenSucceed();
    }

    private static SmesCellBlockEntity module(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, SinguloBlocks.SMES_MODULE.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, facing));
        return h.getBlockEntity(pos);
    }

    @GameTest(template = "empty")
    public static void smesSharesItsOutputAcrossPullPushAndCharging(GameTestHelper h) {
        int rate = SmesCellBlockEntity.Module.MODULE_RATE;
        SmesCellBlockEntity source = module(h, AT, Direction.EAST);
        SmesCellBlockEntity sink = module(h, AT.east(), Direction.NORTH);
        source.energy().setEnergy(2 * rate);
        var scanner = new ItemStack(SinguloItems.NEUTRINO_SCANNER.get());
        source.chargeSlots().setStackInSlot(SmesCellBlockEntity.CHARGE_IN, scanner);
        var front = source.energyFor(Direction.EAST);
        h.assertTrue(front.extractEnergy(rate, true) == rate, "Could not simulate extraction");
        h.assertTrue(source.energyFor(null).extractEnergy(rate / 2, false) == rate / 2, "Could not pull from storage");
        SmesCellBlockEntity.serverTick(h.getLevel(), source.getBlockPos(), source.getBlockState(), source);
        h.assertTrue(sink.energy().getEnergyStored() == rate / 2 && source.energy().getEnergyStored() == rate,
                "Automatic output did not respect the preceding external pull");
        h.assertTrue(front.extractEnergy(rate, false) == 0 && ItemCharging.fraction(scanner) == 0,
                "Externally extracting or charging bypassed the output rate");
        h.succeed();
    }

    /**
     * SMES の充電の枠: 入れる枠の物を蓄えた電力で充電し、満タンになったら出てくる枠へ移す。使った分だけ蓄電が減る。
     * 電力を受け取れない物は入らない。ホッパーなどは入れる枠へ入れるだけ、出てくる枠から取り出すだけ。
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void smesChargesItemsInItsChargeSlots(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.SMES_CELL.get());
        SmesCellBlockEntity smes = h.getBlockEntity(AT);
        smes.energy().setEnergy(1_000_000);
        var slots = smes.chargeSlots();
        var automation = smes.chargeAutomation();
        h.assertFalse(slots.isItemValid(SmesCellBlockEntity.CHARGE_IN, new ItemStack(Items.STONE)), "Stone accepted for charging");
        h.assertFalse(automation.insertItem(SmesCellBlockEntity.CHARGE_OUT, new ItemStack(SinguloItems.NEUTRINO_SCANNER.get()), false).isEmpty(),
                "Automation inserted into the output slot");
        h.assertTrue(automation.insertItem(SmesCellBlockEntity.CHARGE_IN, new ItemStack(SinguloItems.NEUTRINO_SCANNER.get()), false).isEmpty(),
                "Automation could not insert into the charge slot");
        h.assertTrue(automation.extractItem(SmesCellBlockEntity.CHARGE_IN, 1, true).isEmpty(), "Automation extracted an uncharged item");
        h.succeedWhen(() -> {
            ItemStack out = slots.getStackInSlot(SmesCellBlockEntity.CHARGE_OUT);
            h.assertTrue(slots.getStackInSlot(SmesCellBlockEntity.CHARGE_IN).isEmpty() && out.is(SinguloItems.NEUTRINO_SCANNER.get()),
                    "Scanner did not move to the output slot");
            h.assertTrue(ItemCharging.fraction(out) >= 1, "Scanner not full: " + ItemCharging.fraction(out));
            h.assertTrue(smes.energy().getEnergyStored() == 1_000_000 - NeutrinoScannerItem.CAPACITY,
                    "SMES energy did not drop by the charge: " + smes.energy().getEnergyStored());
            h.assertFalse(automation.extractItem(SmesCellBlockEntity.CHARGE_OUT, 1, true).isEmpty(), "Automation cannot take the charged item");
        });
    }
}
