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
    /** ニュートリノ・スキャナーの感度の段階2・3でないと映らない鉱石（それ以外の鉱石は段階1で映る）。 */
    public static final TagKey<net.minecraft.world.level.block.Block> SCANNER_TIER_2 =
            TagKey.create(Registries.BLOCK, Singulo.id("scanner_tier_2"));
    public static final TagKey<net.minecraft.world.level.block.Block> SCANNER_TIER_3 =
            TagKey.create(Registries.BLOCK, Singulo.id("scanner_tier_3"));
    public static final TagKey<EntityType<?>> GRAVITY_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Singulo.id("gravity_immune"));

    private SinguloTags() {}
}
