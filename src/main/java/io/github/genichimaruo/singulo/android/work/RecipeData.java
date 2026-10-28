package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * レシピカードに記録したレシピ。
 * <ul>
 * <li>作業台モード（crafting）: 3×3 の並び（各1個）と、作業台のレシピでできる物。</li>
 * <li>加工モード（processing）: 入れる物（最大9種、数つき）と、出てくる物（主な物 output と、ほかに最大2つ extras）。
 * かまどや装置に、上から入れて下から取り出す。</li>
 * <li>手順モード（procedure、手順カード）: 投入・取り出し・採掘の手順（最大8つ、場所はカードの中）と、できる物。
 * 素材は投入する物。</li>
 * </ul>
 * 素材は置いたとおりの物（同じアイテム・同じ付加情報）で数える。
 * 手順カードも同じ記録を使う（{@link ProcedureCardItem}）。
 */
public record RecipeData(String mode, List<ItemStack> grid, ItemStack output, List<ItemStack> extras, List<ProcedureStep> steps, ItemStack fuel,
                         int faces, List<String> alts) {
    public static final String MODE_CRAFTING = "crafting", MODE_PROCESSING = "processing", MODE_PROCEDURE = "procedure";
    public static final int MAX_STEPS = 8;
    private static final String KEY = "android_recipe";

    /** 作業台モードのレシピ。 */
    public RecipeData(List<ItemStack> grid, ItemStack output) {
        this(MODE_CRAFTING, grid, output, List.of(), List.of(), ItemStack.EMPTY, DEFAULT_FACES, List.of());
    }

    public RecipeData(String mode, List<ItemStack> grid, ItemStack output, List<ItemStack> extras) {
        this(mode, grid, output, extras, List.of(), ItemStack.EMPTY, DEFAULT_FACES, List.of());
    }

    /**
     * 作業台モードのレシピで、同じ種類の別の物も使うもの（alts: 見本の欄ごとの、使ってよい物の材料。空なら、その欄は見本と同じ物だけ）。
     * いろいろな木の板材から棒を作る、など。
     */
    public static RecipeData crafting(List<ItemStack> grid, ItemStack output, List<String> alts) {
        return new RecipeData(MODE_CRAFTING, grid, output, List.of(), List.of(), ItemStack.EMPTY, DEFAULT_FACES, List.copyOf(alts));
    }

    /** 同じ種類の別の物も使うか。 */
    public boolean variants() {
        for (String a : alts) {
            if (!a.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 見本の欄 i で使ってよい物（同じ種類の別の物も使わないなら null）。 */
    @Nullable
    public net.minecraft.world.item.crafting.Ingredient alt(int i) {
        if (i >= alts.size() || alts.get(i).isEmpty()) {
            return null;
        }
        return ALT_CACHE.computeIfAbsent(alts.get(i), k -> java.util.Optional.ofNullable(ItemCount.parse(k))).orElse(null);
    }

    /** 材料の文字を読んだもの（何度も読まないように）。 */
    private static final java.util.Map<String, java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> ALT_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> e) {
                    return size() > 1024;
                }
            });

    public static void clearAlternatives() {
        ALT_CACHE.clear();
    }

    /**
     * 加工モードで、装置のどの面から入れ・取り出し・燃料を入れるか（役目ごとに3ビット: 入れる面、取り出す面、燃料の面）。
     * 値は Direction の番号（上・下・東西南北）か {@link #FACE_AUTO}。役目の {@link #RELATIVE} ビットが立っていれば、値 0〜3 は
     * 装置の向きから見た面（正面・背面・左・右。左右は正面から見て）。燃料の面が自動なら、アンドロイドの立っている横の面。既定は 自動・自動・自動。
     */
    public static final int FACE_AUTO = 7;
    public static final int DEFAULT_FACES = faces(FACE_AUTO, FACE_AUTO, FACE_AUTO);
    /** faces の10ビット目: 燃料を、装置に入るだけ持ってくる（なければ、燃料の欄の数ずつ。入りきらない分は持ってこない）。 */
    public static final int FUEL_FILL = 1 << 9;
    /** faces の11〜13ビット目: 役目（0 入れる、1 取り出す、2 燃料）の面が、装置の向きから見た面か（RELATIVE << 役目）。 */
    public static final int RELATIVE = 1 << 10;
    /** 装置の向きから見た面の名前の鍵（値 0〜3）。 */
    public static final String[] RELATIVE_KEYS = {"front", "back", "left", "right"};
    /** 面の選び方の数（自動・上・下・正面・背面・左・右・北・南・西・東）。{@link #faceChoice} の番号。 */
    public static final int FACE_CHOICES = 11;

    public static int faces(int in, int out, int fuel) {
        return (in & 7) | (out & 7) << 3 | (fuel & 7) << 6;
    }

    /** 役目 role の面の選び方の番号（0 自動、1 上、2 下、3〜6 正面・背面・左・右、7〜10 北・南・西・東）。 */
    public static int faceChoice(int faces, int role) {
        int v = faces >> (role * 3) & 7;
        if ((faces & RELATIVE << role) != 0) {
            return 3 + (v & 3);
        }
        return v == FACE_AUTO || v > 5 ? 0 : v == 1 ? 1 : v == 0 ? 2 : 5 + v;
    }

    /** faces の役目 role を、選び方の番号 choice（{@link #faceChoice}）にしたもの。 */
    public static int withFaceChoice(int faces, int role, int choice) {
        int v = choice == 0 ? FACE_AUTO : choice == 1 ? 1 : choice == 2 ? 0 : choice <= 6 ? choice - 3 : choice - 5;
        int out = faces & ~(7 << (role * 3)) & ~(RELATIVE << role) | v << (role * 3);
        return choice >= 3 && choice <= 6 ? out | RELATIVE << role : out;
    }

    /** 選び方の番号の名前の鍵（auto・up・down・front…・north…）。 */
    public static String faceChoiceKey(int choice) {
        return switch (choice) {
            case 0 -> "auto";
            case 1 -> "up";
            case 2 -> "down";
            case 3, 4, 5, 6 -> RELATIVE_KEYS[choice - 3];
            default -> net.minecraft.core.Direction.from3DDataValue(choice - 5).getName();
        };
    }

    /** 役目 role の面（自動なら fallback）。装置の向きから見た面は、装置のブロックの向き（なければ北が正面）で決める。 */
    private net.minecraft.core.Direction face(int role, net.minecraft.world.level.block.state.BlockState machine,
                                              net.minecraft.core.Direction fallback) {
        int v = faces >> (role * 3) & 7;
        if ((faces & RELATIVE << role) != 0) {
            net.minecraft.core.Direction front = facing(machine);
            return switch (v & 3) {
                case 0 -> front;
                case 1 -> front.getOpposite();
                case 2 -> front.getClockWise();                // 正面から見て左（正面に向き合う人の左手）
                default -> front.getCounterClockWise();
            };
        }
        return v < 6 ? net.minecraft.core.Direction.from3DDataValue(v) : fallback;
    }

    /** 装置の正面の向き（横向きの向きを持たないブロックは北）。 */
    private static net.minecraft.core.Direction facing(net.minecraft.world.level.block.state.BlockState state) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
            net.minecraft.core.Direction d = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            if (d.getAxis().isHorizontal()) {
                return d;
            }
        }
        return net.minecraft.core.Direction.NORTH;
    }

    private boolean auto(int role) {
        return (faces & RELATIVE << role) == 0 && (faces >> (role * 3) & 7) == FACE_AUTO;
    }

    /** 素材を入れる面（自動なら上。実際に入れる面は {@link MachineWatch#inputFace} で決める）。machine: 装置のブロック。 */
    public net.minecraft.core.Direction inputFace(net.minecraft.world.level.block.state.BlockState machine) {
        return face(0, machine, net.minecraft.core.Direction.UP);
    }

    /** 素材を入れる面が自動（上・横・下の順に、その素材を受け付ける面から入れる）か。 */
    public boolean inputAuto() {
        return auto(0);
    }

    /** 素材を入れる面の名前の鍵（auto・up・front・north…）。 */
    public String inputFaceKey() {
        return faceChoiceKey(faceChoice(faces, 0));
    }

    /** できた物を取り出す面（自動なら下。実際に取り出す面は {@link MachineWatch#outputFace} で決める）。 */
    public net.minecraft.core.Direction outputFace(net.minecraft.world.level.block.state.BlockState machine) {
        return face(1, machine, net.minecraft.core.Direction.DOWN);
    }

    /** 取り出す面が自動（下・横・上の順に、できた物が取り出せる面から。かまどの仲間は下）か。 */
    public boolean outputAuto() {
        return auto(1);
    }

    /** 取り出す面の名前の鍵。 */
    public String outputFaceKey() {
        return faceChoiceKey(faceChoice(faces, 1));
    }

    /** 燃料を、装置に入るだけ持ってくるか。 */
    public boolean fuelFill() {
        return (faces & FUEL_FILL) != 0;
    }

    /** 燃料を入れる面（自動なら null）。 */
    @Nullable
    public net.minecraft.core.Direction fuelFace(net.minecraft.world.level.block.state.BlockState machine) {
        return auto(2) ? null : face(2, machine, net.minecraft.core.Direction.NORTH);
    }

    /** 燃料を入れる面の名前の鍵。 */
    public String fuelFaceKey() {
        return faceChoiceKey(faceChoice(faces, 2));
    }

    /** 加工モードのレシピ（fuel: 燃料が切れたら取りに行く燃料と、1回に持ってくる数。空なら取りに行かない）。 */
    public static RecipeData processing(List<ItemStack> grid, ItemStack output, List<ItemStack> extras, ItemStack fuel) {
        return processing(grid, output, extras, fuel, DEFAULT_FACES);
    }

    public static RecipeData processing(List<ItemStack> grid, ItemStack output, List<ItemStack> extras, ItemStack fuel, int faces) {
        return new RecipeData(MODE_PROCESSING, grid, output, extras, List.of(), fuel.copy(), faces, List.of());
    }

    /** 手順カードの記録。 */
    public static RecipeData procedure(List<ProcedureStep> steps, ItemStack output) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return new RecipeData(MODE_PROCEDURE, grid, output, List.of(), List.copyOf(steps), ItemStack.EMPTY, DEFAULT_FACES, List.of());
    }

    public boolean processing() {
        return MODE_PROCESSING.equals(mode);
    }

    public boolean procedure() {
        return MODE_PROCEDURE.equals(mode);
    }

    /** 採掘の手順があるか（採掘アップグレードを積んだアンドロイドだけができる）。 */
    public boolean needsMining() {
        return has(ProcedureStep.MINE);
    }

    /** 伐採の手順があるか（伐採アップグレードを積んだアンドロイドだけができる）。 */
    public boolean needsFelling() {
        return has(ProcedureStep.FELL);
    }

    private boolean has(int kind) {
        for (ProcedureStep s : steps) {
            if (s.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** 1回でできる数（流体の見本なら mB）。 */
    public int outputCount() {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, FluidSamples.amount(output)));
    }

    /** できる物の目印（数え方。流体の見本は量 1）。 */
    public ItemStack outputKey() {
        return FluidSamples.key(output);
    }

    /** 出てくる物（主な物と、ほかの物）。 */
    public List<ItemStack> outputs() {
        List<ItemStack> out = new ArrayList<>();
        out.add(output);
        for (ItemStack e : extras) {
            if (!e.isEmpty()) {
                out.add(e);
            }
        }
        return out;
    }

    /** カードの記録（空のカードなら null）。 */
    @Nullable
    public static RecipeData read(ItemStack card, HolderLookup.Provider registries) {
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) {
            return null;
        }
        return load(tag.getCompound(KEY), registries);
    }

    /** カードに記録する。 */
    public void write(ItemStack card, HolderLookup.Provider registries) {
        CompoundTag r = save(registries);
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.put(KEY, r));
        card.set(DataComponents.MAX_STACK_SIZE, 1);           // 記録したカードは重ならない
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag r = new CompoundTag();
        r.putString("mode", mode);
        ListTag list = new ListTag();
        for (ItemStack s : grid) {
            list.add(s.isEmpty() ? new CompoundTag() : s.save(registries));
        }
        r.put("grid", list);
        r.put("output", output.save(registries));
        ListTag ex = new ListTag();
        for (ItemStack s : extras) {
            ex.add(s.isEmpty() ? new CompoundTag() : s.save(registries));
        }
        r.put("extras", ex);
        if (!steps.isEmpty()) {
            ListTag st = new ListTag();
            for (ProcedureStep s : steps) {
                st.add(s.save(registries));
            }
            r.put("steps", st);
        }
        if (!fuel.isEmpty()) {
            r.put("fuel", fuel.save(registries));
        }
        r.putInt("faces", faces);
        if (variants()) {
            ListTag al = new ListTag();
            for (String a : alts) {
                al.add(net.minecraft.nbt.StringTag.valueOf(a));
            }
            r.put("alts", al);
        }
        return r;
    }

    @Nullable
    public static RecipeData load(CompoundTag r, HolderLookup.Provider registries) {
        List<ItemStack> grid = new ArrayList<>(9);
        ListTag list = r.getList("grid", Tag.TAG_COMPOUND);
        for (int i = 0; i < 9; i++) {
            CompoundTag t = i < list.size() ? list.getCompound(i) : new CompoundTag();
            grid.add(t.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, t));
        }
        ItemStack output = ItemStack.parseOptional(registries, r.getCompound("output"));
        List<ItemStack> extras = new ArrayList<>();
        for (Tag t : r.getList("extras", Tag.TAG_COMPOUND)) {
            ItemStack s = ((CompoundTag) t).isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) {
                extras.add(s);
            }
        }
        List<ProcedureStep> steps = new ArrayList<>();
        for (Tag t : r.getList("steps", Tag.TAG_COMPOUND)) {
            if (steps.size() < MAX_STEPS) {
                steps.add(ProcedureStep.load((CompoundTag) t, registries));
            }
        }
        String mode = r.contains("mode") ? r.getString("mode") : MODE_CRAFTING;
        ItemStack fuel = r.contains("fuel") ? ItemStack.parseOptional(registries, r.getCompound("fuel")) : ItemStack.EMPTY;
        int faces = r.contains("faces") ? r.getInt("faces") : DEFAULT_FACES;
        List<String> alts = new ArrayList<>();
        for (Tag t : r.getList("alts", Tag.TAG_STRING)) {
            alts.add(t.getAsString());
        }
        return output.isEmpty() ? null : new RecipeData(mode, grid, output, extras, steps, fuel, faces, alts);
    }

    /** 記録を消す（新しいカードと同じに戻すので、未記録のカードとまた重なる）。 */
    public static void clear(ItemStack card) {
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.remove(KEY));
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            card.remove(DataComponents.CUSTOM_DATA);
        }
        card.set(DataComponents.MAX_STACK_SIZE, card.getItem().getDefaultMaxStackSize());
    }

    /** 1回分の素材（同じ物はまとめる。加工モードは置いた数、作業台モードは1つずつ）。 */
    public List<ItemCount> inputs() {
        List<ItemCount> out = new ArrayList<>();
        if (procedure()) {
            // 手順カード: 投入する物
            for (ProcedureStep s : steps) {
                if ((s.kind() == ProcedureStep.INSERT || s.kind() == ProcedureStep.BREED) && !s.item().isEmpty()) {
                    ItemCount.add(out, FluidSamples.key(s.item()), FluidSamples.amount(s.item()));     // 入れる物（流体も）、繁殖のえさ
                } else if (s.kind() == ProcedureStep.MILK) {
                    // 搾乳: 取る牛乳の数だけ、空のバケツを持っていく
                    int n = s.item().isEmpty() ? outputCount() : s.item().getCount();
                    ItemCount.add(out, new ItemStack(net.minecraft.world.item.Items.BUCKET), n);
                }
            }
            return out;
        }
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            if (s.isEmpty()) {
                continue;
            }
            net.minecraft.world.item.crafting.Ingredient alt = processing() ? null : alt(i);
            if (alt != null) {
                ItemCount.addAlt(out, s, 1, alt);          // 同じ種類の別の物も使う
            } else {
                ItemCount.add(out, FluidSamples.key(s), processing() ? FluidSamples.amount(s) : 1);
            }
        }
        return out;
    }

    /** 3×3 の並び（作業台に渡す形）。 */
    public NonNullList<ItemStack> gridList() {
        NonNullList<ItemStack> list = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            list.set(i, grid.get(i).copyWithCount(1));
        }
        return list;
    }
}
