package io.github.genichimaruo.singulo.forgecompat;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 版: NeoForge の DeferredHolder と同じ形で使える、登録した物への参照（Forge の RegistryObject を包む）。
 * Forge 1.21.1 の Holder は Supplier を継承していて型がぶつかるので、Holder が要る所では {@link #asHolder()} を使う。
 * Forge に登録先のないもの（データの付け足し）は、名前と作り方だけを持つ（{@link #local}）。
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    @Nullable
    protected final RegistryObject<T> object;
    private final ResourceLocation id;
    @Nullable
    private final Supplier<? extends T> factory;
    @Nullable
    private T made;

    public DeferredHolder(RegistryObject<T> object) {
        this.object = object;
        this.id = object.getId();
        this.factory = null;
    }

    private DeferredHolder(ResourceLocation id, Supplier<? extends T> factory) {
        this.object = null;
        this.id = id;
        this.factory = factory;
    }

    static <R, T extends R> DeferredHolder<R, T> local(ResourceLocation id, Supplier<? extends T> factory) {
        return new DeferredHolder<>(id, factory);
    }

    @Override
    public T get() {
        if (object != null) {
            return object.get();
        }
        synchronized (this) {
            if (made == null) {
                made = factory.get();
            }
            return made;
        }
    }

    public T value() {
        return get();
    }

    public ResourceLocation getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public ResourceKey<R> getKey() {
        if (object == null) {
            throw new IllegalStateException("No registry key: " + id);
        }
        return (ResourceKey<R>) object.getKey();
    }

    public boolean isBound() {
        return object == null || object.isPresent();
    }

    /** 登録が済んだあとの、中身の Holder。 */
    @SuppressWarnings("unchecked")
    public Holder<R> asHolder() {
        if (object == null) {
            throw new IllegalStateException("No registry holder: " + id);
        }
        return (Holder<R>) object.getHolder().orElseThrow(() -> new IllegalStateException("Not registered yet: " + id));
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + id + "}";
    }
}
