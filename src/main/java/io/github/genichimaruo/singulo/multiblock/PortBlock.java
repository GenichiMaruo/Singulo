package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** マルチブロックの搬入出口（冷却塔ポート、リアクターの抽出ポート）。形成済みなら、つないだパイプやケーブルがコントローラへ届く。 */
public class PortBlock extends BaseEntityBlock implements MultiblockPart {
    private final Role role;

    public PortBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    @Override
    public Role role() {
        return role;
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortBlockEntity(pos, state);
    }
}
