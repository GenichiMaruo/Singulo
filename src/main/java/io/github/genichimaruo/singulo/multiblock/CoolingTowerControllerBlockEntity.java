package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 極低温冷却塔のコントローラ。双曲面の塔（高さ7〜15、形は Shapes.tower）。高いほど速い（高さ÷5倍）。
 * 高さ10以上で液体ヘリウムを作れる。形は20 tick ごとに確かめ、崩れたら止まる。
 */
public class CoolingTowerControllerBlockEntity extends MachineBlockEntity {
    static final int CHECK_INTERVAL = 20;

    private int height;
    private final PortLinks ports = new PortLinks();
    private long nextCheck;
    private boolean firstCheck = true;

    public CoolingTowerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.COOLING_TOWER_CONTROLLER.get(), pos, state);
    }

    @Override
    protected boolean canOperate(Level level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            Shapes.Found tower = Shapes.find(Blueprints.Kind.COOLING_TOWER, level, worldPosition);
            int newHeight = tower == null ? 0 : tower.size();
            boolean relinked = ports.update(level, worldPosition, tower == null ? List.of() : tower.ports());
            if (newHeight != height || relinked) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    FormationEffect.onChange(server, worldPosition, height > 0, newHeight > 0, firstCheck, 4, 1, 15);
                }
                height = newHeight;
                markDirty();
            }
            firstCheck = false;
        }
        return height > 0;
    }

    @Override
    public int structureSize() {
        return height;
    }

    /** 高さ÷5 倍（高さ7で1.4倍、高さ10で2倍、高さ15で3倍）。 */
    @Override
    protected double speedMultiplier() {
        return height / 5.0;
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
}
