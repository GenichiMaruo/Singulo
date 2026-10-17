package io.github.genichimaruo.singulo.forgecompat;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Forge 版: NeoForge の BlockEntityRenderer（描く範囲 getRenderBoundingBox を描き手が決める）と同じ形。
 * Forge では描く範囲はブロックエンティティ側が決めるので、{@link RenderBounds} がブロックエンティティから描き手のこれを呼ぶ。
 */
public interface BlockEntityRenderer<T extends BlockEntity> extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {
    /** 描く範囲（null ならブロックエンティティの既定の範囲）。 */
    @Nullable
    default AABB getRenderBoundingBox(T be) {
        return null;
    }
}
