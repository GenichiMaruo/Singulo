package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.FluidCanisterItem;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Persistence and recipe synchronization paths specific to the Java 17 port. */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyRecipeGameTests {
    @GameTest(template = "empty", batch = "legacy_recipe_roundtrip")
    public static void byproductSurvivesJsonAndNetwork(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var serializer = new MachineRecipe.Serializer();
        var json = com.google.gson.JsonParser.parseString("""
                {"station":"catalytic_reactor","time":100,"energy":80,
                 "ingredients":[{"ingredient":{"item":"minecraft:raw_copper"},"count":8}],
                 "result":{"id":"minecraft:copper_ingot","count":6},
                 "byproduct":{"id":"singulo:bismuth_ingot","count":1}}
                """).getAsJsonObject();
        var recipe = serializer.fromJson(Singulo.id("roundtrip"), json);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            serializer.toNetwork(buf, recipe);
            var decoded = serializer.fromNetwork(recipe.id(), buf);
            helper.assertTrue(decoded.result().is(Items.COPPER_INGOT) && decoded.result().getCount() == 6,
                    "Recipe result changed during synchronization");
            helper.assertTrue(decoded.byproduct().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("bismuth_ingot")))
                    && decoded.byproduct().getCount() == 1, "Recipe byproduct was lost during synchronization");
            helper.assertTrue(decoded.ingredients().get(0).count() == 8 && decoded.time() == 100 && decoded.energy() == 80,
                    "Recipe costs changed during synchronization");
            helper.assertTrue(buf.readableBytes() == 0, "Recipe packet has unread bytes");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "legacy_canister_roundtrip")
    public static void canisterSurvivesSaveAndNetwork(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var fluid = new net.minecraftforge.fluids.FluidStack(SinguloFluids.get("hydrogen"), 731);
        var canister = FluidCanisterItem.filled(new ItemStack(SinguloItems.FLUID_CANISTER.get()), fluid);
        var loaded = ItemStack.of(canister.save(new net.minecraft.nbt.CompoundTag()));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            buf.writeItem(loaded);
            var decoded = buf.readItem();
            var contents = FluidCanisterItem.fluid(decoded);
            helper.assertTrue(contents.isFluidEqual(fluid) && contents.getAmount() == 731,
                    "Canister fluid changed during save/network roundtrip");
            var cap = decoded.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            helper.assertTrue(cap != null && cap.getFluidInTank(0).getAmount() == 731,
                    "Restored canister capability does not expose its contents");
        } finally {
            buf.release();
        }
        helper.succeed();
    }
    private LegacyRecipeGameTests() {}
}
