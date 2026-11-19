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
    public static java.util.UUID uuid(net.minecraft.resources.ResourceLocation id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean sameFluid(net.minecraftforge.fluids.FluidStack a, net.minecraftforge.fluids.FluidStack b) { return a.isFluidEqual(b); }
    public static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    public static long ceilDiv(long x, long y) {
        long quotient = Math.floorDiv(x, y);
        return quotient + (x % y == 0 ? 0 : 1);
    }
    private Legacy() {}
}
