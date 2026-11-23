package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ExplorerCompassGameTests {
    private ExplorerCompassGameTests() {}

    @GameTest(template = "empty")
    public static void compassDistinguishesRotatedLegacyAndExpandedRuinStarts(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getServer().getStructureManager();
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos found = origin.above(5);
        var chunk = ChunkPos.containing(origin);

        for (String ruin : List.of("final_lab", "sunken_reactor")) {
            boolean lab = ruin.equals("final_lab");
            var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, Singulo.id(ruin))).value();
            for (boolean expanded : new boolean[] {false, true}) {
                var templateId = Singulo.id("ruins/" + ruin + (expanded ? "_antechamber" : ""));
                Vec3i size = expanded ? (lab ? new Vec3i(104, 26, 92) : new Vec3i(68, 36, 112))
                        : (lab ? new Vec3i(35, 21, 35) : new Vec3i(47, 36, 56));
                helper.assertTrue(manager.get(templateId).orElseThrow().getSize().equals(size),
                        "Incorrect persisted template dimensions for " + templateId);
                var element = StructurePoolElement.single(templateId.toString()).apply(StructureTemplatePool.Projection.RIGID);
                var rotation = Rotation.CLOCKWISE_90;
                var piece = new PoolElementStructurePiece(manager, element, origin, 0, rotation,
                        element.getBoundingBox(manager, origin, rotation), LiquidSettings.APPLY_WATERLOGGING);
                var start = new StructureStart(structure, chunk, 0, new PiecesContainer(List.of(piece)));
                var restored = StructureStart.loadStaticStart(serialization, start.createTag(serialization, chunk), 0);
                helper.assertTrue(restored != null && restored.isValid(), "Cannot reload " + templateId);
                var savedPiece = (PoolElementStructurePiece) restored.getPieces().getFirst();
                Optional<BlockPos> entrance = ExplorerCompassItem.antechamberEntrance(level, savedPiece, found, ruin);
                BlockPos expected = (lab ? origin.offset(-65, 0, 0) : origin.offset(-82, 0, 48)).atY(found.getY());
                helper.assertTrue(entrance.equals(expanded ? Optional.of(expected) : Optional.empty()),
                        "Wrong compass entrance for " + templateId + ": " + entrance);
            }
        }
        helper.succeed();
    }
}
