package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 研究所の試作機（斧型・ビット型）の設計メモリを、プレイヤーごとに何を受け取ったか覚えておく（死んでも消えない）。
 * 統括AIを倒すたび、まだ受け取っていない型を先に渡す。全部そろったら、どれかをランダムに渡す。
 */
public final class PrototypeMemories {
    public static final String RECEIVED = "singulo:prototype_memories";

    private PrototypeMemories() {}

    /** 次に渡す型（まだ受け取っていないものを先に）を選び、受け取ったと記録する。 */
    public static AndroidKind next(ServerPlayer player, RandomSource random) {
        List<String> have = io.github.genichimaruo.singulo.compat.PlayerData.get(player, RECEIVED, Codec.STRING.listOf(), List.<String>of());
        List<AndroidKind> missing = new ArrayList<>();
        AndroidKind[] all = AndroidKind.prototypes();
        for (AndroidKind k : all) {
            if (!have.contains(k.id)) {
                missing.add(k);
            }
        }
        AndroidKind kind = missing.isEmpty() ? all[random.nextInt(all.length)] : missing.get(random.nextInt(missing.size()));
        if (!have.contains(kind.id)) {
            List<String> copy = new ArrayList<>(have);
            copy.add(kind.id);
            io.github.genichimaruo.singulo.compat.PlayerData.set(player, RECEIVED, Codec.STRING.listOf(), copy);
        }
        return kind;
    }

    public static ItemStack stack(AndroidKind kind) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(kind.memoryId())));
    }
}
