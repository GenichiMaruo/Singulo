package io.github.genichimaruo.singulo.compat;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/** Adapts the mod's sided factories to Forge's attachment lifecycle. */
public final class RegisterCapabilitiesEvent {
    private static final Map<Object, Map<Capability<?>, BiFunction<Object, Direction, Object>>> FACTORIES = new IdentityHashMap<>();
    @SuppressWarnings("unchecked")
    public <T, B extends BlockEntity> void registerBlockEntity(Capability<T> cap, BlockEntityType<B> type,
            BiFunction<B, Direction, T> factory) {
        FACTORIES.computeIfAbsent(type, k -> new IdentityHashMap<>()).put(cap, (b, d) -> factory.apply((B)b, d));
    }
    public <T> void registerItem(Capability<T> cap, BiFunction<ItemStack, Void, T> factory, Item... items) {
        for (Item item : items) FACTORIES.computeIfAbsent(item, k -> new IdentityHashMap<>()).put(cap, (s, d) -> factory.apply((ItemStack)s, null));
    }
    public static <O> void attach(AttachCapabilitiesEvent<O> event) {
        Object object = event.getObject();
        Object key = object instanceof BlockEntity be ? be.getType() : object instanceof ItemStack s ? s.getItem() : null;
        var factories = FACTORIES.get(key);
        if (factories == null) return;
        class Provider implements ICapabilityProvider {
            private record Entry(Object value, LazyOptional<?> optional) {}
            private final Map<Capability<?>, Map<Direction, Entry>> sides = new IdentityHashMap<>();
            private final Map<Capability<?>, Entry> unsided = new IdentityHashMap<>();
            @SuppressWarnings("unchecked")
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                var factory = factories.get(cap);
                if (factory == null) return LazyOptional.empty();
                Object value = factory.apply(object, side);
                Entry old = side == null ? unsided.get(cap) : sides.computeIfAbsent(cap, c -> new java.util.EnumMap<>(Direction.class)).get(side);
                if (old == null || old.value() != value) {
                    if (old != null) old.optional().invalidate();
                    old = new Entry(value, optional(value));
                    if (side == null) unsided.put(cap, old); else sides.get(cap).put(side, old);
                }
                return (LazyOptional<T>)old.optional();
            }
            private LazyOptional<?> optional(Object value) { return value == null ? LazyOptional.empty() : LazyOptional.of(() -> value); }
            void invalidate() {
                unsided.values().forEach(e -> e.optional().invalidate());
                sides.values().forEach(m -> m.values().forEach(e -> e.optional().invalidate()));
                unsided.clear();
                sides.clear();
            }
        }
        Provider provider = new Provider();
        event.addCapability(io.github.genichimaruo.singulo.Singulo.id("capabilities"), provider);
        event.addListener(provider::invalidate);
    }
}
