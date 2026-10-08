package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public final class SinguloTags {
    public static final TagKey<Item> METAL_CORE = TagKey.create(Registries.ITEM, Singulo.id("metal_core"));
    public static final TagKey<Item> STAR_CORE = TagKey.create(Registries.ITEM, Singulo.id("star_core"));
    /** 遺構の建材（ニュートリノ・スキャナーが映す）。 */
    public static final TagKey<net.minecraft.world.level.block.Block> RUIN_BLOCKS =
            TagKey.create(Registries.BLOCK, Singulo.id("ruin_blocks"));
    public static final TagKey<EntityType<?>> GRAVITY_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Singulo.id("gravity_immune"));

    private SinguloTags() {}
}
