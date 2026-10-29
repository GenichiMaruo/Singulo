package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class CatalystPersistenceGameTests {
    @GameTest(template = "empty")
    public static void reloadPreservesLoadedReserveAndBlockedCatalystReturns(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity original = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("muon_catalyst")));
        int slot = original.type().catalystSlot(), max = UsesHelper.remaining(catalyst);
        original.items().setStackInSlot(slot, catalyst.copyWithCount(3));
        original.magazine().tick(original.items(), slot, s -> s);
        original.magazine().consume(max + 17, original.items(), slot);
        for (int i = 0; i < original.type().outputSlots(); i++)
            original.items().setStackInSlot(original.type().outputSlot() + i, new ItemStack(Items.COBBLESTONE, 64));
        var tag = original.saveWithoutMetadata();
        var next = new BlockPos(5, 1, 1);
        h.setBlock(next, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity restored = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, next);
        restored.load(tag);
        h.assertTrue(restored.items().getStackInSlot(slot).getCount() == 1
                && UsesHelper.remaining(restored.magazine().loaded()) == max - 17 && restored.magazine().hasReturns(),
                "Reload lost active life, reserve count or pending shell");
        MachineBlockEntity.serverTick(h.getLevel(), restored.getBlockPos(), restored.getBlockState(), restored);
        h.assertTrue(restored.magazine().hasReturns(), "Full outputs discarded a pending shell");
        for (int i = 0; i < restored.type().outputSlots(); i++)
            restored.items().setStackInSlot(restored.type().outputSlot() + i, ItemStack.EMPTY);
        for (int i = 0; i < 3; i++)
            MachineBlockEntity.serverTick(h.getLevel(), restored.getBlockPos(), restored.getBlockState(), restored);
        var spent = BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("spent_muon_catalyst"));
        int returned = 0;
        for (int i = 0; i < restored.type().outputSlots(); i++) {
            var stack = restored.items().getStackInSlot(restored.type().outputSlot() + i);
            if (stack.is(spent)) returned += stack.getCount();
        }
        h.assertTrue(returned == 1 && !restored.magazine().hasReturns(), "Unblocked output lost or duplicated the saved shell");
        h.succeed();
    }
}
