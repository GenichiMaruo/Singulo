package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * レイリー散乱パネル: 天井に張る空の投影板。下の面だけが投影面で、点いている間は雲も太陽もない真昼の青空が見え、
 * 真下へ晴天・正午と同じ空の光を届ける（遮蔽物がなければSKY=15。減衰・日照判定は {@link RayleighSky}）。
 * 消えていると、星のない真夜中のように暗い。夜にはならない。
 * <ul>
 *   <li>研究所の天井のもの（lab）: 研究所の中央動力炉を起動すると点く（{@link LabPower#restore}）。壊すと何も残らない。</li>
 *   <li>設計図から作るもの: FE を送ると点く（{@link RayleighPanelBlockEntity}）。縁が鋼の色で、研究所のものと少し違う。</li>
 * </ul>
 * 同じ種類のパネルを並べると、境目の縁が消えて1枚の広い空に見える（空の色は見る向きで決まるので、つなぎ目が出ない）。
 */
public class RayleighPanelBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final boolean lab;

    public RayleighPanelBlock(Properties properties, boolean lab) {
        super(properties);
        this.lab = lab;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    /** 研究所の天井のもの（研究所の電力で点き、作れない・回収できない）か。 */
    public boolean lab() {
        return lab;
    }

    /** pos に、点いているパネルがあるか。 */
    public static boolean lit(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof RayleighPanelBlock && s.getValue(LIT);
    }

    /** 隣（dir の向き）が同じ種類のパネルで、空がつながるか。 */
    public boolean connects(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockState(pos.relative(dir)).is(this);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new RayleighPanelBlock(p, lab));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        RayleighSky.register(level, pos);
        RayleighSky.changed(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!(newState.getBlock() instanceof RayleighPanelBlock)) RayleighSky.unregister(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RayleighPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 研究所のものは研究所の電力で切り替わるだけ。作ったものは FE を使って点く
        return level.isClientSide || lab ? null
                : createTickerHelper(type, SinguloBlockEntities.RAYLEIGH_PANEL.get(), RayleighPanelBlockEntity::serverTick);
    }
}
