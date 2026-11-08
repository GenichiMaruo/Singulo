package io.github.genichimaruo.singulo.pipe;

import net.minecraftforge.fluids.FluidStack;

/**
 * 液体パイプから受け取る液体を選ぶ口。装置の入力タンクは空ならどの液体でも入ってしまうので、いろいろな液体が通るパイプから
 * 使わない液体を入れて詰まらせないようにする。手やほかの mod からの搬入には効かない。
 */
public interface PipeFluidFilter {
    boolean pipeAccepts(FluidStack fluid);
}
