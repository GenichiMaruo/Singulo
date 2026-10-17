package io.github.genichimaruo.singulo.forgecompat;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Forge 版: ブロックエンティティの描く範囲を、描き手（{@link BlockEntityRenderer#getRenderBoundingBox}）に聞く。
 * 各ブロックエンティティの getRenderBoundingBox から呼ばれる（描くときだけ、クライアントで）。
 */
public final class RenderBounds {
    private RenderBounds() {}

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static AABB of(BlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide) {
            return null;
        }
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        return renderer instanceof BlockEntityRenderer bounded ? bounded.getRenderBoundingBox(be) : null;
    }
}
