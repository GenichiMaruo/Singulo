package io.github.genichimaruo.singulo.machine;

import com.mojang.serialization.MapCodec;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 汎用加工装置ではない1マスの装置（タービン・蓄電・収集器・アンカー）。
 * ブロックエンティティの作り方と毎tickの処理を渡して作る。
 */
public class SimpleMachineBlock<T extends BlockEntity> extends AbstractMachineBlock {
    /** 毎tickの処理。 */
    public interface Ticker<T> {
        void tick(Level level, BlockPos pos, BlockState state, T be);
    }

    private final Supplier<BlockEntityType<T>> type;
    private final BiFunction<BlockPos, BlockState, T> factory;
    private final Ticker<T> ticker;
    /** クライアント側の毎tickの処理（見た目用）。なければ null。 */
    @Nullable
    private final Ticker<T> clientTicker;

    public SimpleMachineBlock(Properties properties, Supplier<BlockEntityType<T>> type,
                              BiFunction<BlockPos, BlockState, T> factory, Ticker<T> ticker) {
        this(properties, type, factory, ticker, null);
    }

    public SimpleMachineBlock(Properties properties, Supplier<BlockEntityType<T>> type,
                              BiFunction<BlockPos, BlockState, T> factory, Ticker<T> ticker, @Nullable Ticker<T> clientTicker) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.ticker = ticker;
        this.clientTicker = clientTicker;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new SimpleMachineBlock<>(p, type, factory, ticker, clientTicker));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> beType) {
        if (beType != type.get()) {
            return null;
        }
        Ticker<T> t = level.isClientSide ? clientTicker : ticker;
        if (t == null) {
            return null;
        }
        BlockEntityTicker<E> inner = (l, p, s, be) -> t.tick(l, p, s, (T) be);
        return level.isClientSide ? inner : withBoost(inner);
    }
}
