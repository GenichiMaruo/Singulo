package io.github.genichimaruo.singulo.android.work;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 化学物質（Mekanism のガス・注入材・顔料・スラリー）を扱う窓口。Mekanism が入っていれば
 * {@code compat.mekanism.MekanismChemicals} が中身を受け持ち、入っていなければ何もしない（化学物質はないものとして動く）。
 * 化学物質は、ID（{@code mekanism:hydrogen} など）と量（mB）で表し、ここより外では Mekanism の型を使わない
 * （Mekanism のない環境で、Mekanism のクラスを読み込まないように）。
 */
public final class Chemicals {
    private Chemicals() {}

    /** 化学物質の入れ物（ブロックのある面）。 */
    public interface Store {
        /** この化学物質の量。 */
        long amount(String id);

        /** 入れる。入った量（simulate なら、入る量）。 */
        long insert(String id, long amount, boolean simulate);

        /** 出す。出た量（simulate なら、出せる量）。 */
        long extract(String id, long amount, boolean simulate);

        /** 中身（ID → 量。空の入れ物は含めない）。 */
        Map<String, Long> contents();
    }

    /** 中身を受け持つもの。 */
    public interface Access {
        @Nullable
        Store store(Level level, BlockPos pos, @Nullable Direction side);

        /** 物（ケミカルタンクなど）に入っている化学物質（ID と量。なければ null）。 */
        @Nullable
        Map.Entry<String, Long> contained(ItemStack stack);

        /** 物（ケミカルタンクなど）の化学物質の入れ口（なければ null。stack は1個の写し）。 */
        @Nullable
        Store itemStore(ItemStack stack);

        boolean exists(String id);

        Component name(String id);

        /** 色（RGB）。 */
        int tint(String id);

        /** 絵（ブロックの絵の束の中。なければ null）。 */
        @Nullable
        ResourceLocation icon(String id);

        /** 空気より軽い（ガス）か。 */
        boolean gaseous(String id);
    }

    private static final Access ACCESS = load();

    /** Mekanism が入っていれば、その受け持ちを読み込む（入っていなければ、Mekanism のクラスに触れない）。 */
    private static Access load() {
        if (net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            try {
                return (Access) Class.forName("io.github.genichimaruo.singulo.compat.mekanism.MekanismChemicals").getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                io.github.genichimaruo.singulo.Singulo.LOGGER.warn("Mekanism の化学物質に対応できない（API が合わない）", e);
            }
        }
        return null;
    }

    /** 化学物質を扱えるか（Mekanism が入っているか）。 */
    public static boolean present() {
        return ACCESS != null;
    }

    @Nullable
    public static Store store(Level level, BlockPos pos, @Nullable Direction side) {
        return ACCESS == null ? null : ACCESS.store(level, pos, side);
    }

    @Nullable
    public static Map.Entry<String, Long> contained(ItemStack stack) {
        return ACCESS == null || stack.isEmpty() ? null : ACCESS.contained(stack);
    }

    @Nullable
    public static Store itemStore(ItemStack stack) {
        return ACCESS == null || stack.isEmpty() ? null : ACCESS.itemStore(stack);
    }

    public static boolean exists(String id) {
        return ACCESS != null && ACCESS.exists(id);
    }

    public static Component name(String id) {
        return ACCESS == null ? Component.literal(id) : ACCESS.name(id);
    }

    public static int tint(String id) {
        return ACCESS == null ? 0xB0B8C0 : ACCESS.tint(id);
    }

    @Nullable
    public static ResourceLocation icon(String id) {
        return ACCESS == null ? null : ACCESS.icon(id);
    }

    public static boolean gaseous(String id) {
        return ACCESS == null || ACCESS.gaseous(id);
    }
}
