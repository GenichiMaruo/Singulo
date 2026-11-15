package io.github.genichimaruo.singulo.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.mojang.serialization.JsonOps;

/** Registry-aware component JSON used by the existing compact console wire format. */
public final class JsonComponents {
    private JsonComponents() {}
    public static String toJson(Component component, HolderLookup.Provider registries) {
        return ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow().toString();
    }
}
