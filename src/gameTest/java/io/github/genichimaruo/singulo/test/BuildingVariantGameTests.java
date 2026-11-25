package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 0.20.0 の建材の階段・ハーフブロック・塀: 元の建材の性質を写し、新しく生まれる研究所・遺構にも置かれる。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class BuildingVariantGameTests {
    @GameTest(template = "empty")
    public static void variantsCopyTheirBaseAndAppearInNewStructures(GameTestHelper helper) {
        helper.assertTrue(GeneratedContent.BLOCK_VARIANTS.size() == 45, "Expected 45 building variants (0.21.0: 4 glass slabs, 0.23.0: collapsar panel and glass sets)");
        for (GeneratedContent.BlockVariant v : GeneratedContent.BLOCK_VARIANTS) {
            Block block = SinguloBlocks.VARIANTS.get(v.id()).get();
            Block base = BuiltInRegistries.BLOCK.get(Singulo.id(v.base()));
            boolean shape = switch (v.shape()) {
                case "stairs" -> block instanceof StairBlock;
                case "slab" -> block instanceof SlabBlock;
                default -> block instanceof WallBlock;
            };
            helper.assertTrue(shape, "Wrong block class for " + v.id());
            helper.assertTrue(block.defaultDestroyTime() == base.defaultDestroyTime()
                    && block.getExplosionResistance() == base.getExplosionResistance(), "Hardness not copied: " + v.id());
            helper.assertTrue(block.asItem() != net.minecraft.world.item.Items.AIR, "No item for " + v.id());
        }

        // 2つ重ねたハーフブロックは2個落とす
        ServerLevel level = helper.getLevel();
        Block slab = SinguloBlocks.VARIANTS.get("lab_panel_slab").get();
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withParameter(LootContextParams.TOOL, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE))
                .withParameter(LootContextParams.BLOCK_STATE, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        var drops = slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE).getDrops(params);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(slab.asItem()) && drops.get(0).getCount() == 2,
                "Double slab should drop two slabs: " + drops);
        var singleDrops = slab.defaultBlockState().getDrops(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withParameter(LootContextParams.TOOL, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE))
                .withParameter(LootContextParams.BLOCK_STATE, slab.defaultBlockState()));
        helper.assertTrue(singleDrops.size() == 1 && singleDrops.get(0).is(slab.asItem()) && singleDrops.get(0).getCount() == 1,
                "Single slab should drop only one slab: " + singleDrops);

        // 同じガラスどうしの境目は描かない（階段の部分だけの面に接する所は、穴が見えるので描く）
        var glass = SinguloBlocks.WHITE_GLASS_PANEL.get().defaultBlockState();
        var stairs = SinguloBlocks.VARIANTS.get("white_glass_panel_stairs").get().defaultBlockState()
                .setValue(StairBlock.FACING, net.minecraft.core.Direction.EAST);     // 東が背面（全面）、西は下半分だけ
        helper.assertTrue(stairs.skipRendering(glass, net.minecraft.core.Direction.WEST), "Stairs draw their face against the glass");
        helper.assertTrue(glass.skipRendering(stairs, net.minecraft.core.Direction.WEST), "Glass draws its face against the stairs' full back");
        helper.assertFalse(glass.skipRendering(stairs, net.minecraft.core.Direction.EAST), "Glass hid its face against the stairs' half face");
        helper.assertFalse(SinguloBlocks.WHITE_PANEL.get().defaultBlockState().skipRendering(stairs, net.minecraft.core.Direction.WEST),
                "Opaque panel changed");

        // アンドロイド透過ガラスの階段: アンドロイドだけが通り抜け、ほかは階段の形でぶつかる。元のガラスとの境目は描かない
        var gateGlass = SinguloBlocks.ANDROID_GATE_GLASS.get().defaultBlockState();
        var gateStairs = SinguloBlocks.VARIANTS.get("android_gate_glass_stairs").get().defaultBlockState()
                .setValue(StairBlock.FACING, net.minecraft.core.Direction.EAST);
        var android = io.github.genichimaruo.singulo.registry.SinguloEntities.BLADE_ANDROID.get().create(level);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(gateStairs.getCollisionShape(level, at, net.minecraft.world.phys.shapes.CollisionContext.of(android)).isEmpty(),
                "An android bumps into gate glass stairs");
        helper.assertFalse(gateStairs.getCollisionShape(level, at, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),
                "Others pass through gate glass stairs");
        helper.assertTrue(gateStairs.skipRendering(gateGlass, net.minecraft.core.Direction.WEST), "Gate stairs draw their face against the glass");
        helper.assertTrue(gateGlass.skipRendering(gateStairs, net.minecraft.core.Direction.WEST), "Gate glass draws its face against the stairs");
        // 遮断ガラスの階段は逆: アンドロイドだけがぶつかり、ほかはすり抜ける
        var barrierStairs = SinguloBlocks.VARIANTS.get("android_barrier_glass_stairs").get().defaultBlockState();
        helper.assertFalse(barrierStairs.getCollisionShape(level, at, net.minecraft.world.phys.shapes.CollisionContext.of(android)).isEmpty(),
                "An android passes through barrier glass stairs");
        helper.assertTrue(barrierStairs.getCollisionShape(level, at, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),
                "Others bump into barrier glass stairs");
        android.discard();

        // 新しく生まれる施設には新しい建材が置かれる（それまでのクォーツの代わり）
        var templates = level.getStructureManager();
        Map<String, String> expected = Map.of(
                "lab/cafeteria", "lab_panel_stairs",
                "ruins/observation_post", "ruin_panel_wall",
                "ruins/research_building", "ruin_panel_stairs");
        expected.forEach((structure, id) -> {
            var template = templates.get(Singulo.id(structure)).orElseThrow();
            var found = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.VARIANTS.get(id).get());
            helper.assertTrue(!found.isEmpty(), structure + " does not contain " + id);
            helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                    net.minecraft.world.level.block.Blocks.QUARTZ_STAIRS).isEmpty(), structure + " still uses quartz stairs");
        });
        helper.succeed();
    }
}
