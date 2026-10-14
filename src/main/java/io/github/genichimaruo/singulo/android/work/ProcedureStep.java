package io.github.genichimaruo.singulo.android.work;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 手順カードの1手順。
 * <ul>
 * <li>投入（INSERT）: pos の装置へ、上の面から item を（数つき）入れる。</li>
 * <li>取り出し（EXTRACT）: pos の装置の下の面から、item を（数つき）取り出す（そろうまで待つ）。</li>
 * <li>採掘（MINE）: pos（と corner があれば、その2点で囲った範囲）のブロックを掘る。item がそろうまで掘り続ける
 * （item が空なら、カードの完成品）。</li>
 * </ul>
 * 数は1回分。何回分かまとめて作るときは、その回数を掛ける。
 */
public record ProcedureStep(int kind, BlockPos pos, @Nullable BlockPos corner, ItemStack item) {
    public static final int INSERT = 0, EXTRACT = 1, MINE = 2, KINDS = 3;
    public static final String[] KEYS = {"insert", "extract", "mine"};

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("kind", kind);
        t.putLong("pos", pos.asLong());
        if (corner != null) {
            t.putLong("corner", corner.asLong());
        }
        if (!item.isEmpty()) {
            t.put("item", item.save(registries));
        }
        return t;
    }

    public static ProcedureStep load(CompoundTag t, HolderLookup.Provider registries) {
        return new ProcedureStep(Math.floorMod(t.getInt("kind"), KINDS), BlockPos.of(t.getLong("pos")),
                t.contains("corner") ? BlockPos.of(t.getLong("corner")) : null,
                t.contains("item") ? ItemStack.parseOptional(registries, t.getCompound("item")) : ItemStack.EMPTY);
    }

    /** 採掘の範囲（1か所なら、そのブロックだけ）。 */
    public net.minecraft.world.level.levelgen.structure.BoundingBox area() {
        BlockPos b = corner == null ? pos : corner;
        return net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(pos, b);
    }
}
