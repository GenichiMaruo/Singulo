package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 粒子加速器のコントローラ。リングの一辺8で標準速度、大きいほど比例して速い。
 * 衝突1回ごとに、一辺に比例した確率（一辺32で1/1000）で磁気単極子が生まれる。
 */
public class AcceleratorControllerBlockEntity extends MachineBlockEntity {
    static final int CHECK_INTERVAL = 20;
    static final double MONOPOLE_CHANCE_AT_MAX = 0.001;

    private int side;
    private final PortLinks ports = new PortLinks();
    private boolean firstCheck = true;
    private long nextCheck;

    public AcceleratorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ACCELERATOR_CONTROLLER.get(), pos, state);
    }

    @Override
    protected boolean canOperate(Level level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            // Refresh old saves too, even when no placement notification has occurred.
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos start = worldPosition.relative(direction);
                    if (level.getBlockState(start).getBlock() instanceof AcceleratorPartBlock) {
                        AcceleratorPartBlock.refresh(server, start);
                    }
                }
            }
            int newSide = Structures.findRing(level, worldPosition);
            net.minecraft.core.Direction toRing = newSide > 0 ? Structures.ringDirection(level, worldPosition) : null;
            ports.update(level, worldPosition, toRing == null ? java.util.List.of() : Structures.acceleratorPorts(level, worldPosition, toRing));
            if (newSide != side) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    FormationEffect.onChange(server, worldPosition, side > 0, newSide > 0, firstCheck, 33, 0, 0);
                }
                side = newSide;
                markDirty();
            }
            firstCheck = false;
        }
        return side > 0;
    }

    @Override
    public int structureSize() {
        return side;
    }

    @Override
    public void onChunkUnloaded() {
        ports.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        ports.onRemoved(level);
        super.setRemoved();
    }

    @Override
    protected double speedMultiplier() {
        return side / (double) Structures.RING_MIN_SIDE;
    }

    @Override
    protected void onCrafted(Level level, MachineRecipe recipe) {
        double chance = MONOPOLE_CHANCE_AT_MAX * side / Structures.RING_MAX_SIDE;
        if (level.random.nextDouble() < chance) {
            insertOutput(new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("magnetic_monopole"))), false);
        }
        maybeLeakStrangelet(level);
    }

    /** 大きなリング（一辺 STRANGELET_MIN_SIDE 以上）を回していて、封じ込め磁場の電力（蓄電）が2割を切っていると、まれにストレンジレットが漏れる。 */
    static final int STRANGELET_MIN_SIDE = 24;
    static final double STRANGELET_CHANCE = 1.0 / 400;

    private void maybeLeakStrangelet(Level level) {
        if (side < STRANGELET_MIN_SIDE || !io.github.genichimaruo.singulo.hazard.StrangeletBlockEntity.enabled()
                || energy().getEnergyStored() >= energy().getMaxEnergyStored() / 5 || level.random.nextDouble() >= STRANGELET_CHANCE) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            net.minecraft.core.BlockPos p = worldPosition.offset(level.random.nextInt(9) - 4, level.random.nextInt(3), level.random.nextInt(9) - 4);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, io.github.genichimaruo.singulo.registry.SinguloBlocks.STRANGELET.get().defaultBlockState(), 3);
                io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, p, 32, "strangelet");
                return;
            }
        }
    }
}
