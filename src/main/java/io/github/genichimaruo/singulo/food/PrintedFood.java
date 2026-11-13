package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.Singulo;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * フードプリンターで印刷する宇宙食。1個に使うペースト（タンパク質・炭水化物・甘味）、印刷の時間（tick。電力は毎 tick
 * {@link #ENERGY_PER_TICK}）、食べたときの値と、印刷台に積む形。
 * <p>
 * 形は下の層から順に、層ごとに前（北）から奥への行、左から右への文字の並び。'.' は空き、ほかの文字は {@link #palette} の色の
 * 1ドット（1/16 ブロック）の立方体。印刷は各層を前から奥へ、行ごとに折り返しながら1ドットずつ置いていく。
 */
public enum PrintedFood {
    /** 栄養バー: 濃い焼き色の底、オーツの層（明暗のむら）、上にチョコの粒とオーツの粒。右の端は銀の包み。すぐに食べられる。 */
    NUTRIENT_BAR("nutrient_bar", new int[] {2, 4, 0}, 120, 6, 0.7F, true, false, null, 0, new String[][] {
            {"aAaaaFfF", "aaAaaFFf", "aaaAaFfF"},
            {"bBbcbfFf", "bcbBbFfF", "bBbbcfFf"},
            {"hcbhcfFf", "bhbcbFfF", "chbhbfFf"}},
            Map.of('a', 0xA8804C, 'A', 0x947040, 'b', 0xD6AC6E, 'B', 0xC49A5C, 'c', 0x5A3820, 'h', 0xEAC888,
                    'f', 0xDDE3EA, 'F', 0xA8B0BE)),
    /** 積層ステーキ: 焼き目の底、桃色と赤の身に白い脂の筋、上は網の跡（斜めの線）とつやのある焼き目。 */
    PRINTED_STEAK("printed_steak", new int[] {8, 0, 0}, 200, 9, 0.8F, false, false, null, 0, new String[][] {
            {".sssss.", "sssssss", "sssssss", "sssssss", ".sssss."},
            {".rmrrm.", "rrfrmrr", "mrrrrfr", "rfrmrrm", ".rrmrr."},
            {".SGSgS.", "SgSSgSS", "SSgSSgS", "GSSgSSg", ".SgSSS."}},
            Map.of('s', 0x5A2C1C, 'r', 0xB04038, 'm', 0xD06A60, 'f', 0xF0D8CC, 'S', 0x8C4A2C, 'g', 0x3C1E12, 'G', 0xB8764A)),
    /** らせんパスタ: ねじれたパスタ（明暗の筋）を丸く盛り、上にトマトソースの山、バジルと粉チーズ。食べると採掘速度上昇（1分）。 */
    SPIRAL_PASTA("spiral_pasta", new int[] {2, 6, 0}, 180, 8, 0.7F, false, false, MobEffects.DIG_SPEED, 20 * 60, new String[][] {
            {"..pqp..", ".pqpqp.", "pqpqpqp", "qpqpqpq", "pqpqpqp", ".pqpqp.", "..pqp.."},
            {"..qpq..", ".qPqPq.", "qPqpqPq", "pqrrrqp", "qPqrqPq", ".qPqPq.", "..qpq.."},
            {".......", "..pPp..", ".prRrp.", ".PrRsP.", ".prsrp.", "..pPp..", "......."},
            {".......", ".......", "...v...", "..vRc..", "...rc..", ".......", "......."}},
            Map.of('p', 0xF0C870, 'q', 0xD8A850, 'P', 0xFAE0A0, 'r', 0xC83228, 'R', 0xE85840, 's', 0x962018,
                    'v', 0x48A040, 'c', 0xFFF6DC)),
    /** ハニカムケーキ: スポンジ・クリーム・スポンジの層に、ハチミツの六角形のつや。食べると移動速度上昇（1分）。 */
    HONEYCOMB_CAKE("honeycomb_cake", new int[] {0, 3, 4}, 200, 7, 0.5F, false, false, MobEffects.MOVEMENT_SPEED, 20 * 60, new String[][] {
            {".kKkk.", "kkKkkK", "KkkkKk", "kKkkkk", ".kkKk."},
            {".mmmm.", "mmmmmm", "mmmmmm", "mmmmmm", ".mmmm."},
            {".KkkK.", "kKkkKk", "kkKkkK", "KkkKkk", ".kKkk."},
            {".hjh..", "hHjhHj", "jhHjhh", "hjhHjh", "..hjh."}},
            Map.of('k', 0xE6B870, 'K', 0xF2CC8A, 'm', 0xFAF0DE, 'h', 0xF0AA28, 'H', 0xFFD45A, 'j', 0xC47A14)),
    /** フルーツゼリーキューブ: 赤・橙・黄・緑の4層（層ごとに果肉の明るい粒）、上にミントの葉。満腹でも食べられ、再生（5秒）。 */
    FRUIT_GEL_CUBE("fruit_gel_cube", new int[] {0, 0, 4}, 100, 3, 0.4F, false, true, MobEffects.REGENERATION, 20 * 5, new String[][] {
            {"rrRr", "Rrrr", "rrrR", "rRrr"},
            {"oOoo", "oooO", "Oooo", "ooOo"},
            {"yyYy", "Yyyy", "yyyY", "yYyy"},
            {"gGgg", "gggG", "Gggg", "ggGg"},
            {"....", ".m..", "..m.", "...."}},
            Map.of('r', 0xDC3C5A, 'R', 0xF06478, 'o', 0xFA963C, 'O', 0xFFB464, 'y', 0xFADC50, 'Y', 0xFFEE88,
                    'g', 0x78D26E, 'G', 0xA0E896, 'm', 0x2E9A50));

    /** 印刷の電力（FE/tick）。 */
    public static final int ENERGY_PER_TICK = 20;

    private final String id;
    private final int[] cost;
    private final int ticks;
    private final int nutrition;
    private final float saturation;
    private final boolean fast;
    private final boolean alwaysEdible;
    @Nullable
    private final Holder<MobEffect> effect;
    private final int effectTicks;
    private final String[][] layers;
    private final Map<Character, Integer> palette;
    /** 印刷する順の立方体（x, 層, z, 色）。 */
    private final int[][] voxels;

    PrintedFood(String id, int[] cost, int ticks, int nutrition, float saturation, boolean fast, boolean alwaysEdible,
                @Nullable Holder<MobEffect> effect, int effectTicks, String[][] layers, Map<Character, Integer> palette) {
        this.id = id;
        this.cost = cost;
        this.ticks = ticks;
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.fast = fast;
        this.alwaysEdible = alwaysEdible;
        this.effect = effect;
        this.effectTicks = effectTicks;
        this.layers = layers;
        this.palette = palette;
        this.voxels = path(layers, palette);
    }

    /** 層ごとに、前から奥へ行を進め、行ごとに左右を折り返す順。 */
    private static int[][] path(String[][] layers, Map<Character, Integer> palette) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int turn = 0;
        for (int y = 0; y < layers.length; y++) {
            String[] rows = layers[y];
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                boolean reverse = (turn++ & 1) == 1;
                for (int i = 0; i < row.length(); i++) {
                    int x = reverse ? row.length() - 1 - i : i;
                    char c = row.charAt(x);
                    if (c != '.') {
                        out.add(new int[] {x, y, z, palette.get(c)});
                    }
                }
            }
        }
        return out.toArray(new int[0][]);
    }

    public String id() {
        return id;
    }

    /** 1個に使うペースト（{@link FoodPaste} の種類の順）。 */
    public int cost(int kind) {
        return cost[kind];
    }

    public int ticks() {
        return ticks;
    }

    public int energy() {
        return ticks * ENERGY_PER_TICK;
    }

    /** 形の幅（x）・奥行き（z）・層の数。 */
    public int width() {
        return layers[0][0].length();
    }

    public int depth() {
        return layers[0].length;
    }

    public int height() {
        return layers.length;
    }

    /** 印刷する順の立方体 {x, 層, z, 色}。 */
    public int[][] voxels() {
        return voxels;
    }

    @Nullable
    public Holder<MobEffect> effect() {
        return effect;
    }

    public Item item() {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    public FoodProperties food() {
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation);
        if (fast) {
            b.fast();
        }
        if (alwaysEdible) {
            b.alwaysEdible();
        }
        if (effect != null) {
            Holder<MobEffect> e = effect;
            b.effect(() -> new MobEffectInstance(e, effectTicks, 0), 1.0F);
        }
        return b.build();
    }

    @Nullable
    public static PrintedFood byId(String id) {
        for (PrintedFood f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }

    @Nullable
    public static PrintedFood byIndex(int i) {
        return i >= 0 && i < values().length ? values()[i] : null;
    }
}
