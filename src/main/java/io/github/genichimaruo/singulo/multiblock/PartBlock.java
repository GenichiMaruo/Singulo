package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class PartBlock extends Block implements MultiblockPart {
    private final Role role;

    public PartBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    @Override
    public Role role() {
        return role;
    }

}
