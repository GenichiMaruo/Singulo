package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

/** 白縁の強化ガラス。中の部品が見える観察窓。 */
public class GlassPartBlock extends TransparentBlock implements MultiblockPart {
    public static final MapCodec<GlassPartBlock> CODEC = simpleCodec(GlassPartBlock::new);

    public GlassPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Role role() {
        return Role.TOWER_GLASS;
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
}
