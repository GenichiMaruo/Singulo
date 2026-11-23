package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 異常場の発生器。最終実験施設の前室の床に埋め込まれた板で、壊せない。上の空間に場を張る（{@link AnomalyEmitterBlockEntity}）。
 * <ul>
 *   <li>lift: 重力が反転し、浮き上がる（浮遊）</li>
 *   <li>heavy: 重力が強く、速く落ち、動きが鈍る</li>
 *   <li>slow: 時間が遅れる（鈍足・採掘速度低下。敵も鈍る）</li>
 *   <li>fast: 時間が速まる（移動速度上昇・採掘速度上昇。敵も速くなる）</li>
 * </ul>
 */
public class AnomalyEmitterBlock extends BaseEntityBlock {
    public static final EnumProperty<Field> FIELD = EnumProperty.create("field", Field.class);

    public enum Field implements StringRepresentable {
        LIFT("lift"), HEAVY("heavy"), SLOW("slow"), FAST("fast");

        private final String name;

        Field(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** 重力の場か（重力に動じないものには効かない）。 */
        public boolean gravity() {
            return this == LIFT || this == HEAVY;
        }
    }

    public AnomalyEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FIELD, Field.LIFT));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FIELD);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnomalyEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, SinguloBlockEntities.ANOMALY_EMITTER.get(), AnomalyEmitterBlockEntity::serverTick);
    }
}
