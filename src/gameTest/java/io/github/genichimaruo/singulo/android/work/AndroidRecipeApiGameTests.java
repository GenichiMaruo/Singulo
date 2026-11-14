package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidRecipeApiGameTests {
    @GameTest(template="empty")
    public static void recipeAccessRequiresOwnerAndAvailableConsole(GameTestHelper h) {
        var p = new BlockPos(1,1,1);
        h.setBlock(p, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity c = h.getBlockEntity(p);
        UUID owner = TestScope.player(h).getUUID(); c.setOwner(owner);
        var ok = AndroidOrders.recipes(h.getLevel(), c.getBlockPos(), owner);
        h.assertTrue(ok.state() == AndroidOrders.SnapshotState.AVAILABLE && ok.recipes().isEmpty(), "Empty owned console unavailable");
        var denied = AndroidOrders.recipes(h.getLevel(), c.getBlockPos(), UUID.randomUUID());
        h.assertTrue(denied.state() == AndroidOrders.SnapshotState.REJECTED && denied.recipes().isEmpty(), "Non-owner read recipes");
        var missing = AndroidOrders.recipes(h.getLevel(), c.getBlockPos().above(), owner);
        h.assertTrue(missing.state() == AndroidOrders.SnapshotState.UNAVAILABLE, "Missing console not unavailable");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void recipeValuesDefensivelyCopyStacksAndPositions(GameTestHelper h) {
        var source = new ItemStack(Items.COAL, 4);
        var pos = new BlockPos.MutableBlockPos(1,2,3);
        var material = new AndroidRecipe.Material(source, 4, List.of(source), "", false, true, false);
        var product = new AndroidRecipe.Product(source, 4, false);
        var recipe = new AndroidRecipe("processing", pos, List.of(product), List.of(material), true);
        source.setCount(17); pos.set(8,8,8);
        material.sample().setCount(23); material.alternatives().get(0).setCount(24); product.sample().setCount(25);
        h.assertTrue(material.sample().getCount() == 1 && material.alternatives().get(0).getCount() == 4
                && product.sample().getCount() == 1 && recipe.location().equals(new BlockPos(1,2,3)), "Snapshot leaked mutable state");
        boolean rejected = false;
        try { recipe.materials().clear(); } catch (UnsupportedOperationException expected) { rejected = true; }
        h.assertTrue(rejected, "Mutable material list"); h.succeed();
    }
    @GameTest(template="empty")
    public static void linkedRecipeIsExportedWithoutExposingInternalStacks(GameTestHelper h) {
        var cp = new BlockPos(1,1,1); var rp = new BlockPos(2,1,1);
        h.setBlock(cp, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(rp, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        var player = TestScope.player(h);
        WorkConsoleBlockEntity c = h.getBlockEntity(cp); c.setOwner(player.getUUID());
        RecipeRackBlockEntity rack = h.getBlockEntity(rp);
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var link = new ItemStack(registry.get(net.minecraft.resources.ResourceLocation.parse("singulo:work_location_card")));
        WorkLocationCardItem.record(link, h.getLevel(), c.getBlockPos(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        rack.useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        var location = link.copy();
        WorkLocationCardItem.record(location, h.getLevel(), c.getBlockPos().above(), net.minecraft.world.level.block.Blocks.FURNACE);
        var card = new ItemStack(registry.get(net.minecraft.resources.ResourceLocation.parse("singulo:recipe_card")));
        RecipeData.processing(List.of(new ItemStack(Items.IRON_ORE, 2)), new ItemStack(Items.IRON_INGOT, 2),
                List.of(new ItemStack(Items.STONE)), new ItemStack(Items.COAL, 3)).write(card, h.getLevel().registryAccess());
        rack.items().setStackInSlot(0, location); rack.items().setStackInSlot(1, card);
        var snapshot = AndroidOrders.recipes(h.getLevel(), c.getBlockPos(), player.getUUID());
        h.assertTrue(snapshot.recipes().size() == 1, "Linked recipe missing");
        var recipe = snapshot.recipes().get(0);
        h.assertTrue(recipe.outputs().size() == 2 && recipe.outputs().get(0).count() == 2, "Output counts lost");
        h.assertTrue(recipe.materials().size() == 2 && recipe.materials().get(0).count() == 2
                && recipe.materials().get(1).fuel() && recipe.materials().get(1).count() == 3, "Material/fuel semantics lost");
        recipe.outputs().get(0).sample().setCount(60);
        h.assertTrue(AndroidOrders.recipes(h.getLevel(), c.getBlockPos(), player.getUUID()).recipes().get(0).outputs().get(0).count() == 2,
                "Snapshot mutation changed source");
        h.succeed();
    }}
