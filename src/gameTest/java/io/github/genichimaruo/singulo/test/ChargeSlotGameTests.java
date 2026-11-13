package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.ItemCharging;
import io.github.genichimaruo.singulo.item.NeutrinoScannerItem;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
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
