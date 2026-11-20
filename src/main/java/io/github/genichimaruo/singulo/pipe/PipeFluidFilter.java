package io.github.genichimaruo.singulo.pipe;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 液体パイプから受け取る液体を選ぶ口。装置の入力タンクは空ならどの液体でも入ってしまうので、いろいろな液体が通るパイプから
 * 使わない液体を入れて詰まらせないようにする。手やほかの mod からの搬入には効かない。
 */
public interface PipeFluidFilter {
    boolean pipeAccepts(FluidStack fluid);

    static boolean accepts(Object handler, FluidStack fluid) {
        return !(handler instanceof PipeFluidFilter filter) || filter.pipeAccepts(fluid);
    }
}
