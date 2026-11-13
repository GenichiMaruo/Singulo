package io.github.genichimaruo.singulo.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * フードプリンターのペースト（タンパク質・炭水化物・甘味）。食材はタグ singulo:food_paste/&lt;種類&gt; で分け、
 * 1個から、その食べ物の満腹度の分（食べ物でない物は {@link #NON_FOOD}、なければ1）のペーストになる。
 * ペーストはフードカートリッジに、物の情報 {@link SinguloComponents#FOOD_PASTE}（種類と量）として持つ。
 * 空のカートリッジはどの種類にも使え、1種類のペーストを {@link #CAPACITY} までためる。
 */
public final class FoodPaste {
    private FoodPaste() {}

    public static final int PROTEIN = 0, CARB = 1, SWEET = 2, KINDS = 3;
    public static final int CAPACITY = 64;
    public static final String[] NAMES = {"protein", "carb", "sweet"};
    /** GUI の目盛り・カートリッジの窓・印刷の光の色（RGB）。 */
    public static final int[] COLORS = {0xD66060, 0xE8C878, 0xEC78C8};
    @SuppressWarnings("unchecked")
    public static final TagKey<Item>[] TAGS = new TagKey[] {
            TagKey.create(Registries.ITEM, Singulo.id("food_paste/protein")),
            TagKey.create(Registries.ITEM, Singulo.id("food_paste/carb")),
            TagKey.create(Registries.ITEM, Singulo.id("food_paste/sweet"))};
    /** 食べ物でない食材1個のペースト。 */
    static final Map<Item, Integer> NON_FOOD = Map.of(Items.WHEAT, 2, Items.EGG, 2, Items.HONEYCOMB, 3);

    /** カートリッジの中身（種類と量）。 */
    public record Content(int kind, int amount) {
        // Normalize both persistent and network decoding, and direct component creation.
        // Invalid data must behave as an empty cartridge before any color/name indexing.
        public Content {
            if (kind < 0 || kind >= KINDS || amount <= 0 || amount > CAPACITY) {
                kind = -1;
                amount = 0;
            }
        }
        public static final Codec<Content> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("kind").forGetter(Content::kind),
                Codec.INT.fieldOf("amount").forGetter(Content::amount)).apply(i, Content::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Content::kind, ByteBufCodecs.VAR_INT, Content::amount, Content::new);
    }

    public static Component name(int kind) {
        return Component.translatable("food_paste.singulo." + NAMES[kind]);
    }

    /**
     * 食材のペーストの種類（食材でなければ -1）。印刷した食品と、食べると容器が残る物（ハチミツ入りの瓶・シチューなど）は入れない
     * （容器の行き場がない）。
     */
    public static int kindOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof PrintedFoodItem || stack.hasCraftingRemainingItem()) {
            return -1;
        }
        FoodProperties food = stack.getFoodProperties(null);
        if (food != null && food.usingConvertsTo().isPresent()) {
            return -1;
        }
        for (int k = 0; k < KINDS; k++) {
            if (stack.is(TAGS[k])) {
                return k;
            }
        }
        return -1;
    }

    /** 食材1個のペーストの量。 */
    public static int unitsOf(ItemStack stack) {
        FoodProperties food = stack.getFoodProperties(null);
        if (food != null) {
            return Math.max(1, food.nutrition());
        }
        return NON_FOOD.getOrDefault(stack.getItem(), 1);
    }

    /** カートリッジのペーストの種類（空なら -1）。 */
    public static int kind(ItemStack cartridge) {
        Content c = cartridge.get(SinguloComponents.FOOD_PASTE.get());
        return c == null || c.amount() <= 0 ? -1 : c.kind();
    }

    public static int amount(ItemStack cartridge) {
        Content c = cartridge.get(SinguloComponents.FOOD_PASTE.get());
        return c == null ? 0 : Math.max(0, c.amount());
    }

    /** カートリッジの中身を書き換える（0 なら空にする。空のカートリッジどうしは重なる）。 */
    public static void set(ItemStack cartridge, int kind, int amount) {
        if (kind < 0 || kind >= KINDS || amount <= 0 || amount > CAPACITY) {
            cartridge.remove(SinguloComponents.FOOD_PASTE.get());
        } else {
            cartridge.set(SinguloComponents.FOOD_PASTE.get(), new Content(kind, amount));
        }
    }

    /** 種類 kind の枠に入れられるカートリッジか（空か、同じ種類のペースト）。 */
    public static boolean fitsSlot(ItemStack stack, int kind) {
        return stack.getItem() instanceof FoodCartridgeItem && (kind(stack) < 0 || kind(stack) == kind);
    }
}
