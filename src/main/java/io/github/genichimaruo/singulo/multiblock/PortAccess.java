package io.github.genichimaruo.singulo.multiblock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;

/** ポートごとに入出力の向きを決められるコントローラ。ポートはコントローラの能力の代わりに、ここで返す物を見せる。 */
public interface PortAccess {
    @Nullable
    <T> T portCapability(BlockPos port, Capability<T> capability);
}
