package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 衝撃発電パネル。上に落ちてきたものの衝撃を電気に変えてため（落ちた高さ1ブロックにつき ENERGY_PER_BLOCK）、
 * 重力パネル受電器と同じように、接している重力パネルの集まりへ配る（同じ集まりの受電器とは電力を出し合う）。
 * 既定（パネル1枚 40 FE/t）では、8ブロックほどの落下1回で、2枚の重力パネルを5秒ほど働かせられる。
 * <p>
 * ケーブルなど（受電器以外）の電力を受け取る装置が隣にあれば、自分が働かせているパネルの RESERVE_TICKS tick 分を残して、
 * 余りを毎tick PUSH_PER_TICK まで送り出す。ケーブルから電力を受け取ることはない。
 */
public class ImpactGeneratorBlockEntity extends GravityReceiverBlockEntity {
    public static final long ENERGY_PER_BLOCK = 1_000;
    public static final long CAPACITY = 20_000;
    /** これより短い落下では発電しない（歩いて段差を降りたくらい）。 */
    public static final float MIN_FALL = 1.0F;
    /** 1 tick に外へ送り出せる量。 */
    public static final int PUSH_PER_TICK = 1_000;
    /** 働かせているパネルのために残しておく時間（tick）。 */
    public static final int RESERVE_TICKS = 100;

    /** 外向きの電力の口（取り出すだけ）。 */
    private final IEnergyStorage output = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int take = (int) Math.max(0, Math.min(amount, surplus()));
            if (!simulate && take > 0) {
                stored -= take;
                setChanged();
            }
            return take;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, stored);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public ImpactGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.IMPACT_GENERATOR.get(), pos, state);
    }

    @Override
    protected long capacity() {
        return CAPACITY;
    }

    public IEnergyStorage output() {
        return output;
    }

    /** パネルのために残す分を除いた、外へ出せる量。 */
    public long surplus() {
        long reserve = (long) perPanel() * supplyingPanels() * RESERVE_TICKS;
        return Math.max(0, stored - reserve);
    }

    @Override
    protected void afterDistribute(Level level) {
        for (Direction dir : Direction.values()) {
            long room = Math.min(surplus(), PUSH_PER_TICK);
            if (room <= 0) {
                return;
            }
            BlockPos n = worldPosition.relative(dir);
            if (!level.isLoaded(n) || level.getBlockEntity(n) instanceof GravityReceiverBlockEntity) {
                continue;
            }
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
            if (target != null && target.canReceive()) {
                long sent = LongEnergyStorage.receive(target, room, false);
                if (sent > 0) {
                    stored -= sent;
                    setChanged();
                }
            }
        }
    }

    /** 落ちてきた（fallDistance ブロック）。ためた量を返す。 */
    public long absorb(float fallDistance) {
        if (fallDistance < MIN_FALL) {
            return 0;
        }
        long gain = Math.min(CAPACITY - stored, Math.round(fallDistance * ENERGY_PER_BLOCK));
        if (gain > 0) {
            stored += gain;
            setChanged();
        }
        return Math.max(0, gain);
    }
}
