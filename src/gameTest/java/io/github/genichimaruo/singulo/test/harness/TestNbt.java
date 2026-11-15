package io.github.genichimaruo.singulo.test.harness;
public final class TestNbt {
 private TestNbt(){}
 public static net.minecraft.nbt.CompoundTag save(net.minecraft.world.entity.Entity entity) {
  var out=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,entity.registryAccess());
  entity.saveWithoutId(out); return out.buildResult();
 }
 public static void load(net.minecraft.world.entity.Entity entity,net.minecraft.nbt.CompoundTag tag) {
  entity.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,entity.registryAccess(),tag));
 }
 public static void load(net.minecraft.world.level.block.entity.BlockEntity entity,net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries) {
  entity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,registries,tag));
 }
}