package io.github.genichimaruo.singulo.android.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 化学物質の見本（Mekanism のガス・注入材・顔料・スラリー）: 作業の計算・予約・画面で、化学物質を物と同じように扱う目印
 * （{@link ChemicalSampleItem}）。流体の見本（{@link FluidSamples}）と同じく、見本1個が化学物質の種類と量（mB）を持つ。
 */
public final class ChemicalSamples {
    private ChemicalSamples() {}

    /** 見本の中身: 化学物質の ID と量。 */
    public record Content(String id, long amount) {
        public static final Codec<Content> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Content::id),
                Codec.LONG.fieldOf("amount").forGetter(Content::amount)).apply(i, Content::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Content::id, ByteBufCodecs.VAR_LONG, Content::amount, Content::new);
    }

    /** 化学物質の見本（量つき）。ID が空か量が0以下なら空。 */
    public static ItemStack of(String id, long amount) {
        if (id.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(SinguloItems.CHEMICAL_SAMPLE.get());
        s.set(SinguloComponents.CHEMICAL_SAMPLE.get(), new Content(id, amount));
        return s;
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ChemicalSampleItem;
    }

    /** 見本の化学物質の ID（見本でなければ空）。 */
    public static String id(ItemStack stack) {
        Content c = is(stack) ? stack.get(SinguloComponents.CHEMICAL_SAMPLE.get()) : null;
        return c == null ? "" : c.id();
    }

    /** 見本の量（見本でなければ 0）。 */
    public static long amount(ItemStack stack) {
        Content c = is(stack) ? stack.get(SinguloComponents.CHEMICAL_SAMPLE.get()) : null;
        return c == null ? 0 : c.amount();
    }
}
