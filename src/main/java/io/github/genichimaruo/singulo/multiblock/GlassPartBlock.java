package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

/** 白縁の強化ガラス。中の部品が見える観察窓。 */
public class GlassPartBlock extends TransparentBlock implements MultiblockPart {
    private final Role role;

    public GlassPartBlock(Properties properties) {
        this(properties, Role.STRUCTURE);
    }

    public GlassPartBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    @Override
    public Role role() {
        return role;
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return simpleCodec(p -> new GlassPartBlock(p, role));
    }
}
