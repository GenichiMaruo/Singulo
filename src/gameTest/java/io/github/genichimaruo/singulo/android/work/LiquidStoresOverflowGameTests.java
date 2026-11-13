package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class LiquidStoresOverflowGameTests {
    private LiquidStoresOverflowGameTests() {}

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty")
    public static void largeMultipleTanks(GameTestHelper helper) throws ReflectiveOperationException {
        var constructor = LiquidStores.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LiquidStores stores = constructor.newInstance();
        var field = LiquidStores.class.getDeclaredField("fluids");
        field.setAccessible(true);
        ((List<IFluidHandler>) field.get(stores)).add(new IFluidHandler() {
            public int getTanks() { return 2; }
            public FluidStack getFluidInTank(int tank) { return new FluidStack(Fluids.WATER, 1_500_000_000); }
            public int getTankCapacity(int tank) { return 1_500_000_000; }
            public boolean isFluidValid(int tank, FluidStack fluid) { return true; }
            public int fill(FluidStack fluid, FluidAction action) { return 0; }
            public FluidStack drain(FluidStack fluid, FluidAction action) { return fluid.copy(); }
            public FluidStack drain(int amount, FluidAction action) { return new FluidStack(Fluids.WATER, amount); }
        });
        var sample = FluidSamples.of(new FluidStack(Fluids.WATER, 1));
        helper.assertTrue(stores.amount(sample) == 3_000_000_000L, "Machine contents must retain long totals");
        List<ItemCount> inventory = new ArrayList<>();
        stores.addTo(inventory);
        helper.assertTrue(ItemCount.get(inventory, sample) == 3_000_000_000L, "Inventory aggregation must not overflow");
        var storage = LiquidStores.class.getDeclaredField("storage");
        storage.setAccessible(true);
        storage.setBoolean(stores, true);
        helper.assertTrue(stores.amount(sample) == Integer.MAX_VALUE,
                "Storage must retain the int-sized simulated extractable amount, not wrap negative");
        helper.succeed();
    }
}