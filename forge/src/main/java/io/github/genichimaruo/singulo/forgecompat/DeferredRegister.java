package io.github.genichimaruo.singulo.forgecompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 版: NeoForge の DeferredRegister と同じ形で使える登録係（Forge の DeferredRegister を包む）。
 * アイテム・ブロック・データ部品の登録係（createItems・createBlocks・createDataComponents）も同じ名前で使える。
 */
public class DeferredRegister<T> {
    protected final net.minecraftforge.registries.DeferredRegister<T> inner;   // Forge に登録先がなければ null
    private final String modid;
    private final List<DeferredHolder<T, ? extends T>> entries = new ArrayList<>();

    protected DeferredRegister(ResourceKey<? extends Registry<T>> key, String modid) {
        this.inner = net.minecraftforge.registries.DeferredRegister.create(key, modid);
        this.modid = modid;
    }

    /** Forge に登録先のないもの（データの付け足しなど）の登録係。名前と作り方を覚えるだけ。 */
    protected DeferredRegister(String modid) {
        this.inner = null;
        this.modid = modid;
    }

    public static <T> DeferredRegister<T> local(String modid) {
        return new DeferredRegister<>(modid);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modid) {
        return new DeferredRegister<>(key, modid);
    }

    public static Items createItems(String modid) {
        return new Items(modid);
    }

    public static Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    public static DataComponents createDataComponents(String modid) {
        return new DataComponents(modid);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> sup) {
        DeferredHolder<T, I> h = inner == null
                ? DeferredHolder.local(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(modid, name), sup)
                : new DeferredHolder<>(inner.register(name, sup));
        entries.add(h);
        return h;
    }

    /** 登録が済む前から渡せる Holder で登録する（NeoForge 版の DeferredHolder を Holder として使う所）。 */
    public net.minecraft.core.Holder<T> registerHolder(String name, Supplier<? extends T> sup) {
        RegistryObject<T> object = inner.register(name, sup);
        entries.add(new DeferredHolder<>(object));
        return new LazyHolder<>(object);
    }

    protected <I extends T> RegistryObject<I> registerRaw(String name, Supplier<? extends I> sup) {
        return inner.register(name, sup);
    }

    protected void track(DeferredHolder<T, ? extends T> h) {
        entries.add(h);
    }

    public void register(IEventBus bus) {
        if (inner != null) {
            inner.register(bus);
        }
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return List.copyOf(entries);
    }

    /** アイテムの登録係。 */
    public static class Items extends DeferredRegister<Item> {
        protected Items(String modid) {
            super(Registries.ITEM, modid);
        }

        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> sup) {
            DeferredItem<I> h = new DeferredItem<>(registerRaw(name, sup));
            track(h);
            return h;
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, Item.Properties props) {
            return register(name, () -> factory.apply(props));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, new Item.Properties());
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return registerItem(name, Item::new);
        }
    }

    /** ブロックの登録係。 */
    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String modid) {
            super(Registries.BLOCK, modid);
        }

        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> sup) {
            DeferredBlock<B> h = new DeferredBlock<>(registerRaw(name, sup));
            track(h);
            return h;
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<Block.Properties, ? extends B> factory, Block.Properties props) {
            return register(name, () -> factory.apply(props));
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, Block.Properties props) {
            return registerBlock(name, Block::new, props);
        }
    }

    /** データ部品の登録係。 */
    public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
        protected DataComponents(String modid) {
            super(Registries.DATA_COMPONENT_TYPE, modid);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.<D>builder()).build());
        }
    }
}
