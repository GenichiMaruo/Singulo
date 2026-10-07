package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 量子熱機関（ティア3の発電機）。ボース凝縮体を作業物質にした量子熱サイクルで、quantumEngineOutput（既定 200 kFE/t）を出す。
 * 出力は触媒の速度倍率に比例する（上位の触媒で最大+50%、1段下のミュオン触媒なら半分）。燃料は触媒だけ。
 */
public class QuantumHeatEngineBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 3;
    public static final int PUSH_PER_TICK = 400_000;

    private int output;

    public QuantumHeatEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.QUANTUM_HEAT_ENGINE.get(), pos, state, 4_000_000, 0, PUSH_PER_TICK);
    }

    @Override
    public Kind kind() {
        return Kind.QUANTUM_HEAT_ENGINE;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return 0;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        output = active ? (int) Math.round(ServerConfig.QUANTUM_ENGINE_OUTPUT.get() * effect.speed()
                * ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get()) : 0;
        if (output > 0) {
            energy.generate(output);
        }
        pushEnergy(level, PUSH_PER_TICK);
    }

    @Override
    protected int displayValue() {
        return output;
    }
}
