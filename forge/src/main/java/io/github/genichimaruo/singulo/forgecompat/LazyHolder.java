package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 版: 登録が済む前から渡せる Holder（NeoForge の DeferredHolder は Holder そのもの）。
 * 中身を聞かれた時点で、登録済みの Holder に任せる。
 */
final class LazyHolder<T> implements Holder<T> {
    private final RegistryObject<T> object;

    LazyHolder(RegistryObject<T> object) {
        this.object = object;
    }

    @SuppressWarnings("unchecked")
    private Holder<T> ref() {
        return (Holder<T>) object.getHolder().orElseThrow(() -> new IllegalStateException("Not registered yet: " + object.getId()));
    }

    @Override
    public T value() {
        return object.get();
    }

    @Override
    public T get() {
        return object.get();
    }

    @Override
    public boolean isBound() {
        return object.isPresent();
    }

    @Override
    public boolean is(ResourceLocation id) {
        return object.getId().equals(id);
    }

    @Override
    public boolean is(ResourceKey<T> key) {
        return object.getKey() != null && object.getKey().equals(key);
    }

    @Override
    public boolean is(Predicate<ResourceKey<T>> predicate) {
        return ref().is(predicate);
    }

    @Override
    public boolean is(TagKey<T> tag) {
        return ref().is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<T> other) {
        return ref().is(other);
    }

    @Override
    public Stream<TagKey<T>> tags() {
        return ref().tags();
    }

    @Override
    public Either<ResourceKey<T>, T> unwrap() {
        return ref().unwrap();
    }

    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
        return ref().unwrapKey();
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        return ref().canSerializeIn(owner);
    }

    @Override
    public String toString() {
        return "LazyHolder{" + object.getId() + "}";
    }
}
