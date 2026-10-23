package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.lab.LabPoweredBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import io.github.genichimaruo.singulo.compat.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 施設の建材の設計図: 作業台で素材と並べると建材ができ、設計図は残る。置いた施設灯は点いている。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BlueprintRecipeGameTests {
    private static final String EMPTY = "empty";

    private BlueprintRecipeGameTests() {}

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    private static CraftingInput grid(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>(List.of(stacks));
        while (list.size() < 9) {
            list.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, list);
    }

    @GameTest(template = EMPTY)
    public static void blueprintStaysAfterCrafting(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        CraftingInput input = grid(new ItemStack(SinguloItems.BLUEPRINT_LAB_FURNITURE.get()), new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(Items.PAPER));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(), "設計図と紙で書類の山が作れない");
        helper.assertTrue(recipe.get().assemble(input, level.registryAccess()).is(SinguloBlocks.PAPER_STACK.get().asItem()),
                "書類の山ができない");
        var rest = recipe.get().getRemainingItems(input);
        helper.assertTrue(rest.stream().anyMatch(s -> s.is(SinguloItems.BLUEPRINT_LAB_FURNITURE.get())), "設計図が残らない");
        // 設計図がなければ作れない
        CraftingInput without = grid(new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, without, level)
                .map(r -> !r.assemble(without, level.registryAccess()).is(SinguloBlocks.PAPER_STACK.get().asItem())).orElse(true),
                "設計図なしで作れてしまう");
        // レイリー散乱パネルも設計図から
        CraftingInput sky = grid(new ItemStack(SinguloItems.BLUEPRINT_RAYLEIGH.get()), new ItemStack(Items.GLASS), new ItemStack(Items.GLASS),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.LAPIS_LAZULI),
                item("basic_circuit"), item("steel_plate"));
        var skyRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, sky, level);
        helper.assertTrue(skyRecipe.isPresent() && skyRecipe.get().assemble(sky, level.registryAccess())
                .is(SinguloBlocks.RAYLEIGH_PANEL.get().asItem()), "設計図からレイリー散乱パネルが作れない");
        helper.succeed();
    }

    /** 置いた施設灯・サーバーラックは動いている状態、非常灯は赤く光る状態で置かれる。 */
    @GameTest(template = EMPTY)
    public static void placedLabLightsAreOn(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            BlockPos floor = new BlockPos(2, 1, 2);
            for (var block : List.of(SinguloBlocks.FACILITY_LIGHT.get(), SinguloBlocks.SERVER_RACK.get())) {
                helper.setBlock(floor, net.minecraft.world.level.block.Blocks.AIR);                 // 空気の所を押すと、そこに置かれる
                ItemStack stack = new ItemStack(block.asItem());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockPos abs = helper.absolutePos(floor);
                ((BlockItem) stack.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false))));
                helper.assertTrue(helper.getBlockState(floor).getValue(LabPoweredBlock.POWERED), block + " が消えた状態で置かれた");
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }
}
