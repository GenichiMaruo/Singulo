package io.github.genichimaruo.singulo.android.work;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 手順カードの1手順。数は1回分（何回分かまとめて作るときは、その回数を掛ける）。
 * <ul>
 * <li>投入（INSERT）: pos の装置へ、上の面から item を入れる。</li>
 * <li>取り出し（EXTRACT）: pos の装置の下の面から、item を取り出す（そろうまで待つ）。</li>
 * <li>採掘（MINE）: 範囲のブロックを掘り、item（空ならカードの完成品）がそろうまで続ける。採掘アップグレードが要る。</li>
 * <li>収穫（HARVEST）: 範囲の育ちきった作物を取り、植え直す。サトウキビ・サボテンは根元を残し、カボチャ・スイカは実だけ取る。</li>
 * <li>伐採（FELL）: 範囲の木を、つながった原木ごと切り（葉も落とす）、苗木を植え直す。伐採アップグレードが要る。</li>
 * <li>繁殖（BREED）: 範囲の動物に、item（えさ）を与えて増やす（大人の数が option まで）。1回通りやって終わる。</li>
 * <li>毛刈り（SHEAR）・搾乳（MILK）: 範囲の羊の毛を刈る・牛から牛乳を取る（空のバケツを持っていく）。そろうまで続ける。</li>
 * <li>処理（CULL）: 範囲の動物のうち、大人の数が option を超えた分を処理して、落ちた物を拾う。そろうまで続ける。</li>
 * <li>操作（USE）: pos のブロックを使う。レバーは option（0 切り替え・1 オン・2 オフ）。ボタン・ドアなどは右クリックと同じ。</li>
 * </ul>
 * 範囲の手順は、pos だけなら1か所、corner があれば2点で囲った範囲。
 */
public record ProcedureStep(int kind, BlockPos pos, @Nullable BlockPos corner, ItemStack item, int option) {
    public static final int INSERT = 0, EXTRACT = 1, MINE = 2, HARVEST = 3, FELL = 4, BREED = 5, SHEAR = 6, MILK = 7, CULL = 8, USE = 9,
            KINDS = 10;
    public static final String[] KEYS = {"insert", "extract", "mine", "harvest", "fell", "breed", "shear", "milk", "cull", "use"};
    /** 繁殖・処理の、大人の数の既定の上限。 */
    public static final int DEFAULT_LIMIT = 8;
    /** 操作のレバーの扱い。 */
    public static final int USE_TOGGLE = 0, USE_ON = 1, USE_OFF = 2, USE_MODES = 3;

    public ProcedureStep(int kind, BlockPos pos, @Nullable BlockPos corner, ItemStack item) {
        this(kind, pos, corner, item, defaultOption(kind));
    }

    public static int defaultOption(int kind) {
        return kind == BREED || kind == CULL ? DEFAULT_LIMIT : 0;
    }

    /** 範囲（2点目）を使う手順か。 */
    public static boolean usesArea(int kind) {
        return kind == MINE || kind == HARVEST || kind == FELL || kind == BREED || kind == SHEAR || kind == MILK || kind == CULL;
    }

    /** 物の欄が要る手順か（投入・取り出しの物、繁殖のえさ）。 */
    public static boolean needsItem(int kind) {
        return kind == INSERT || kind == EXTRACT || kind == BREED;
    }

    /** 数の上限（繁殖・処理）か、レバーの扱い（操作）を持つ手順か。 */
    public static boolean hasOption(int kind) {
        return kind == BREED || kind == CULL || kind == USE;
    }

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
        t.putInt("option", option);
        return t;
    }

    public static ProcedureStep load(CompoundTag t, HolderLookup.Provider registries) {
        int kind = Math.floorMod(t.getInt("kind"), KINDS);
        return new ProcedureStep(kind, BlockPos.of(t.getLong("pos")),
                t.contains("corner") ? BlockPos.of(t.getLong("corner")) : null,
                t.contains("item") ? ItemStack.parseOptional(registries, t.getCompound("item")) : ItemStack.EMPTY,
                t.contains("option") ? t.getInt("option") : defaultOption(kind));
    }

    /** 範囲（1か所なら、そのブロックだけ）。 */
    public net.minecraft.world.level.levelgen.structure.BoundingBox area() {
        BlockPos b = corner == null ? pos : corner;
        return net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(pos, b);
    }
}
