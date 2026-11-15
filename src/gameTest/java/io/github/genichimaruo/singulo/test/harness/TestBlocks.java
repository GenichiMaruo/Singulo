package io.github.genichimaruo.singulo.test.harness;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Preserves the old helper's generic return while retaining its missing-block assertion. */
public final class TestBlocks {
 private TestBlocks() {}
 public static <T extends BlockEntity> T get(GameTestHelper helper,BlockPos relativePos,Class<T> type) { return helper.getBlockEntity(relativePos,type); }
 @SuppressWarnings("unchecked") public static <T extends BlockEntity> T get(GameTestHelper helper,BlockPos relativePos) {
  return (T)helper.getBlockEntity(relativePos,BlockEntity.class);
 }
}
