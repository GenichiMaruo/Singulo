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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 0.20.0 の建材の階段・ハーフブロック・塀: 元の建材の性質を写し、新しく生まれる研究所・遺構にも置かれる。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class BuildingVariantGameTests {
    @GameTest(template = "empty")
    public static void variantsCopyTheirBaseAndAppearInNewStructures(GameTestHelper helper) {
        helper.assertTrue(GeneratedContent.BLOCK_VARIANTS.size() == 32, "Expected 32 building variants");
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
