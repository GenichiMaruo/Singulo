package io.github.genichimaruo.singulo.registry;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
public final class SinguloDataSerializers {
    private SinguloDataSerializers() {}
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, Singulo.MODID);
    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG = new EntityDataSerializer<>() {
        public StreamCodec<? super RegistryFriendlyByteBuf, CompoundTag> codec() { return ByteBufCodecs.COMPOUND_TAG; }
        public CompoundTag copy(CompoundTag value) { return value.copy(); }
    };
    static { REGISTER.register("compound_tag", () -> COMPOUND_TAG); }
}
