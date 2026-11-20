package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.android.work.Chemicals;
import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.LiquidStores;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.api.android.AndroidRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo_mekanism")
@PrefixGameTestTemplate(false)
public final class MekanismStockGameTests {
    private static final BlockPos CONSOLE = new BlockPos(3, 2, 3);

    private static BlockPos place(GameTestHelper h, BlockPos at, String name) {
        var block = BuiltInRegistries.BLOCK.get(Identifier.parse("mekanism:" + name));
        h.setBlock(at, block);
        var pos = h.absolutePos(at);
        h.getLevel().getBlockEntity(pos).applyComponentsFromItemStack(new ItemStack(block));
        return pos;
    }

    @GameTest(template = "empty")
    public static void androidFindsBothSeparatorOutputFaces(GameTestHelper h) {
        var level = h.getLevel();
        var separator = place(h, CONSOLE.above(), "electrolytic_separator");
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = h.getBlockEntity(CONSOLE);
        h.runAfterDelay(5, () -> {
            int filled = 0, energy = 0;
            for (var face : Direction.values()) {
                var fluid = level.getCapability(Capabilities.FluidHandler.BLOCK, separator, face);
                if (fluid != null && filled == 0) filled = fluid.fill(new FluidStack(Fluids.WATER, 10), FluidAction.EXECUTE);
                var power = level.getCapability(Capabilities.EnergyStorage.BLOCK, separator, face);
                if (power != null && energy == 0) energy = power.receiveEnergy(10_000, false);
            }
            h.assertTrue(filled == 10 && energy > 0, "Separator needs water and FE");
            h.succeedWhen(() -> {
                var stock = console.stock();
                var stores = LiquidStores.at(level, console.getBlockPos());
                for (var id : new String[]{"mekanism:hydrogen", "mekanism:oxygen"}) {
                    long visible = 0;
                    for (var face : Direction.values()) {
                        var store = Chemicals.store(level, separator, face);
                        if (store != null) visible = Math.max(visible, store.extract(id, Long.MAX_VALUE, true));
                    }
                    var sample = AndroidRecipe.chemicalSample(id, 1);
                    h.assertTrue(visible > 0, "Separator has not produced " + id);
                    h.assertTrue(ItemCount.get(stock, sample) == visible && stores.amount(sample) == visible
                            && stores.drain(sample, visible, true) == visible, "Console missed separator output " + id);
                }
                for (var id : new String[]{"mekanism:hydrogen", "mekanism:oxygen"}) {
                    var sample = AndroidRecipe.chemicalSample(id, 1);
                    long before = ItemCount.get(stock, sample);
                    h.assertTrue(stores.drain(sample, 1, false) == 1 && stores.amount(sample) == before - 1,
                            "Withdrawing from separator did not debit " + id);
                }
            });
        });
    }

    @GameTest(template = "empty")
    public static void creativeChemicalAndOrdinaryTankDoNotOverflowStock(GameTestHelper h) {
        var level = h.getLevel();
        var creative = place(h, CONSOLE.east(), "creative_chemical_tank");
        var basic = place(h, CONSOLE.west(), "basic_chemical_tank");
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = h.getBlockEntity(CONSOLE);
        h.runAfterDelay(5, () -> {
            String id = "mekanism:hydrogen";
            // Empty creative tanks void external input. Set the type by manually inserting hydrogen.
            var handler = (mekanism.api.chemical.IMekanismChemicalHandler) level.getBlockEntity(creative);
            var chemical = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getHolder(Identifier.parse(id)).orElseThrow();
            handler.getChemicalTanks(null).getFirst().insert(new mekanism.api.chemical.ChemicalStack(chemical, 1_000),
                    mekanism.api.Action.EXECUTE, mekanism.api.AutomationType.MANUAL);
            var input = Chemicals.store(level, basic, Direction.UP);
            h.assertTrue(input != null && input.insert(id, 1_000, false) == 1_000, "Ordinary tank did not accept hydrogen");
            var sample = AndroidRecipe.chemicalSample(id, 1);
            long unlimited = Chemicals.store(level, creative, Direction.UP).amount(id);
            h.assertTrue(unlimited == Long.MAX_VALUE, "Unseeded creative chemical tank");
            var stores = LiquidStores.at(level, console.getBlockPos());
            h.assertTrue(ItemCount.get(console.stock(), sample) == Long.MAX_VALUE && stores.amount(sample) == Long.MAX_VALUE,
                    "Creative plus regular chemical stock overflowed");
            h.succeed();
        });
    }
}
