package io.github.genichimaruo.singulo.compat;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

public final class Legacy {
    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key, 10) ? Optional.of(net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound(key))) : Optional.empty();
    }
    public static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy(); copy.setAmount(amount); return copy;
    }
    @SuppressWarnings("unchecked")
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> T blockEntity(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos pos) {
        return (T)helper.getBlockEntity(pos);
    }
    public static java.util.UUID uuid(net.minecraft.resources.ResourceLocation id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private Legacy() {}
}
