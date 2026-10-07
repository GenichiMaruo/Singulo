package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 極低温冷却塔のコントローラ。高さ5で標準速度、高いほど比例して速い。高さ10以上で液体ヘリウムを作れる。
 * 形は20 tick ごとに確かめ、崩れたら止まる。
 */
public class CoolingTowerControllerBlockEntity extends MachineBlockEntity {
    static final int CHECK_INTERVAL = 20;

    private int height;
    private List<BlockPos> ports = List.of();
    private long nextCheck;
    private boolean firstCheck = true;

    public CoolingTowerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.COOLING_TOWER_CONTROLLER.get(), pos, state);
    }

    @Override
    protected boolean canOperate(Level level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            Structures.Tower tower = Structures.findTower(level, worldPosition, getBlockState().getBlock());
            int newHeight = tower == null ? 0 : tower.height();
            List<BlockPos> newPorts = tower == null ? List.of() : tower.ports();
            if (newHeight != height || !newPorts.equals(ports)) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    FormationEffect.onChange(server, worldPosition, height > 0, newHeight > 0, firstCheck, 2, 15, 15);
                }
                linkPorts(level, ports, null);
                linkPorts(level, newPorts, worldPosition);
                height = newHeight;
                ports = newPorts;
                markDirty();
            }
            firstCheck = false;
        }
        return height > 0;
    }

    private static void linkPorts(Level level, List<BlockPos> ports, BlockPos controller) {
        for (BlockPos p : ports) {
            // 読み込まれていないチャンクには触れない（読み込み直しが起きる）
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PortBlockEntity port) {
                port.link(controller);
            }
        }
    }

    @Override
    public int structureSize() {
        return height;
    }

    @Override
    protected double speedMultiplier() {
        return height / 5.0;
    }

    /**
     * チャンクの読み込み解除でも setRemoved は呼ばれる。そのとき隣のチャンクのポートに触ると、解除中のチャンクを
     * 読み込み直して解除が終わらなくなるので、ポートの切り離しはブロックが本当に壊されたときだけ行う。
     */
    private boolean chunkUnloading;

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && !chunkUnloading) {
            linkPorts(level, ports, null);
        }
        super.setRemoved();
    }
}
