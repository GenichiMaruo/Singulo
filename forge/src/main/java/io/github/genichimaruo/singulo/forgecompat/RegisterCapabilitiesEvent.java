package io.github.genichimaruo.singulo.forgecompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Forge 版: NeoForge の RegisterCapabilitiesEvent と同じ書き方で、ブロックエンティティとアイテムの機能を登録する。
 * 登録した中身は {@link Caps} が Forge の AttachCapabilitiesEvent で各ブロックエンティティ・アイテムへ付ける。
 */
public final class RegisterCapabilitiesEvent {
    @FunctionalInterface
    public interface Provider<O, C, T> {
        @Nullable
        T getCapability(O object, C context);
    }

    record Entry<O>(Object forgeCap, Provider<O, Object, ?> provider) {}

    final Map<BlockEntityType<?>, List<Entry<BlockEntity>>> blocks = new HashMap<>();
    final Map<Item, List<Entry<ItemStack>>> items = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T, C, BE extends BlockEntity> void registerBlockEntity(BlockCapability<T, C> cap, BlockEntityType<BE> type,
                                                                   Provider<? super BE, C, T> provider) {
        blocks.computeIfAbsent(type, t -> new ArrayList<>())
                .add(new Entry<>(cap.forge(), (Provider<BlockEntity, Object, ?>) (Provider<?, ?, ?>) provider));
    }

    @SuppressWarnings("unchecked")
    public <T, C> void registerItem(ItemCapability<T, C> cap, Provider<ItemStack, C, T> provider, ItemLike... targets) {
        for (ItemLike target : targets) {
            items.computeIfAbsent(target.asItem(), t -> new ArrayList<>())
                    .add(new Entry<>(cap.forge(), (Provider<ItemStack, Object, ?>) (Provider<?, ?, ?>) provider));
        }
    }
}
