package io.github.genichimaruo.singulo.compat;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
public final class NbtCompat {
 private NbtCompat() {}
 public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
  return (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
 }
 public static Optional<ItemStack> parse(HolderLookup.Provider registries, Tag tag) {
  return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result();
 }
 public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
  return tag.isEmpty() ? ItemStack.EMPTY : parse(registries,tag).orElse(ItemStack.EMPTY);
 }
 public static CompoundTag serialize(ValueIOSerializable value, HolderLookup.Provider registries) {
  var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
  value.serialize(output); return output.buildResult();
 }
 public static void deserialize(ValueIOSerializable value, HolderLookup.Provider registries, CompoundTag tag) {
  value.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
 }
 public static Tag writeBlockPos(BlockPos pos) { return BlockPos.CODEC.encodeStart(NbtOps.INSTANCE,pos).getOrThrow(); }
 public static Optional<BlockPos> readBlockPos(CompoundTag tag,String name) {return tag.read(name,BlockPos.CODEC);}

 public static CompoundTag save(net.neoforged.neoforge.fluids.FluidStack stack, HolderLookup.Provider registries) {
  return stack.isEmpty() ? new CompoundTag() : (CompoundTag) net.neoforged.neoforge.fluids.FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();
 }
 public static CompoundTag writeTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, HolderLookup.Provider registries, CompoundTag tag) {
  tag.merge(save(tank.getFluid(), registries)); return tag;
 }
 public static void readTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, HolderLookup.Provider registries, CompoundTag tag) {
  tank.setFluid(net.neoforged.neoforge.fluids.FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).result().orElse(net.neoforged.neoforge.fluids.FluidStack.EMPTY));
 }
 public static CompoundTag saveAllItems(CompoundTag tag, net.minecraft.core.NonNullList<ItemStack> items, HolderLookup.Provider registries) {
  var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
  net.minecraft.world.ContainerHelper.saveAllItems(output,items); tag.merge(output.buildResult()); return tag;
 }
 public static void loadAllItems(CompoundTag tag, net.minecraft.core.NonNullList<ItemStack> items, HolderLookup.Provider registries) {
  net.minecraft.world.ContainerHelper.loadAllItems(TagValueInput.create(ProblemReporter.DISCARDING,registries,tag),items);
 }
 public static Tag createUUID(java.util.UUID uuid) {return net.minecraft.core.UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,uuid).getOrThrow();}
 public static java.util.UUID loadUUID(Tag tag) {return net.minecraft.core.UUIDUtil.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();}
}
