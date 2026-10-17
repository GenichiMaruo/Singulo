package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 版: NeoForge の材料「neoforge:components」（データ部品の値も見て合わせる）の代わり。
 * 書き方は同じ（"items"・"components"・"strict"）で、種類の名前だけ「singulo:components」（レシピの JSON は変換で置き換える）。
 */
public final class ComponentsIngredient extends AbstractIngredient {
    private final HolderSet<Item> items;
    private final DataComponentPredicate components;
    private final boolean strict;

    private ComponentsIngredient(HolderSet<Item> items, DataComponentPredicate components, boolean strict) {
        super(items.stream().map(item -> {
            ItemStack stack = new ItemStack(item);
            stack.applyComponents(components.asPatch());
            return new Ingredient.ItemValue(stack);
        }));
        this.items = items;
        this.components = components;
        this.strict = strict;
    }

    public static final MapCodec<ComponentsIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(c -> c.items),
            DataComponentPredicate.CODEC.fieldOf("components").forGetter(c -> c.components),
            Codec.BOOL.optionalFieldOf("strict", false).forGetter(c -> c.strict)
    ).apply(i, ComponentsIngredient::new));

    public static final IIngredientSerializer<ComponentsIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public MapCodec<? extends ComponentsIngredient> codec() {
            return CODEC;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, ComponentsIngredient value) {
            ByteBufCodecs.holderSet(Registries.ITEM).encode(buf, value.items);
            DataComponentPredicate.STREAM_CODEC.encode(buf, value.components);
            buf.writeBoolean(value.strict);
        }

        @Override
        public ComponentsIngredient read(RegistryFriendlyByteBuf buf) {
            return new ComponentsIngredient(ByteBufCodecs.holderSet(Registries.ITEM).decode(buf),
                    DataComponentPredicate.STREAM_CODEC.decode(buf), buf.readBoolean());
        }
    };

    private static final DeferredRegister<IIngredientSerializer<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, Singulo.MODID);

    static {
        REGISTER.register("components", () -> SERIALIZER);
    }

    /** Mod の読み込み中に一度だけ呼ぶ。 */
    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || !items.contains(stack.getItemHolder())) {
            return false;
        }
        if (strict) {
            return stack.getComponentsPatch().equals(components.asPatch());
        }
        return components.test(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }
}
