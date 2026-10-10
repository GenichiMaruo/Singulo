package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 衝撃発電パネル。上に落ちてきたものの衝撃を電気に変えてため（落ちた高さ1ブロックにつき ENERGY_PER_BLOCK）、
 * 重力パネル受電器と同じように、接している重力パネルの集まりへ配る（同じ集まりの受電器とは電力を出し合う）。
 * 既定（パネル1枚 40 FE/t）では、8ブロックほどの落下1回で、2枚の重力パネルを5秒ほど働かせられる。
 */
public class ImpactGeneratorBlockEntity extends GravityReceiverBlockEntity {
    public static final long ENERGY_PER_BLOCK = 1_000;
    public static final long CAPACITY = 20_000;
    /** これより短い落下では発電しない（歩いて段差を降りたくらい）。 */
    public static final float MIN_FALL = 1.0F;

    public ImpactGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.IMPACT_GENERATOR.get(), pos, state);
    }

    @Override
    protected long capacity() {
        return CAPACITY;
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
