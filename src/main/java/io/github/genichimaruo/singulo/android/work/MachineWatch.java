package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 加工を待っている装置の様子。アンドロイドが素材を入れてほかの作業へ行ったあと、指令台が毎秒見て、取り出しに行ってよいかを決める。
 * <ul>
 * <li>主な物が待っている数だけできた、出口の欄がいっぱい（それ以上できない）、かまどの燃料が切れた: すぐ取りに行く。</li>
 * <li>入れた素材がもう見えない（使い切った・装置の中へ取り込まれた）まま {@link #SETTLE_TICKS} 何も変わらない: 取りに行く
 * （確率で出る物が足りなければ、アンドロイドが作り直しを頼む）。</li>
 * <li>{@link #QUIET_TICKS} 何も変わらない: 止まっているかもしれないので、見に行く。</li>
 * </ul>
 */
public final class MachineWatch {
    static final int SETTLE_TICKS = 20 * 10, QUIET_TICKS = 20 * 120;
    /** 入れる面が自動のとき、試す順（上・横・下）。 */
    private static final net.minecraft.core.Direction[] AUTO_ORDER = {net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.DOWN};
    /** 取り出す面が自動のとき、試す順（下・横・上）。 */
    private static final net.minecraft.core.Direction[] OUT_ORDER = {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.UP};

    private MachineWatch() {}

    /**
     * 毎秒: 工程 s の装置を見て、取り出しに行ってよいか・残り時間の見込みを決める。fuelInStock: 記録した燃料が素材置き場にある
     * （ないのに燃料切れで呼んでも足せないので、燃料ができるまで待つ）。
     */
    static void watch(Level level, WorkOrder.StepState s, long now, boolean fuelInStock) {
        BlockPos at = s.location;
        if (!level.isLoaded(at)) {
            s.collectReady = false;
            return;
        }
        RecipeData r = s.recipe;
        net.minecraft.core.Direction outFace = outputFace(level, at, r);
        IItemHandler out = outFace == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, at, outFace);
        if (!handlesInput(level, at, r) || !handlesOutput(level, at, r)) {
            s.collectReady = true;                 // 装置が壊された: 見に行く（作れないので、注文を取り消すことになる）
            return;
        }
        long have = outFace == null ? 0 : FluidSamples.liquid(r.output()) ? LiquidStores.machine(level, at, outFace).amount(r.output())
                : output(out, r.output());
        long held = held(level, at, r);
        long sig = have * 1_000_003L + held;
        if (sig != s.watchSig) {
            s.watchSig = sig;
            s.watchChanged = now;
        }
        s.eta = eta(level, at);
        long quiet = now - s.watchChanged;
        boolean noFuel = outOfFuel(level, at, r);
        s.collectReady = have >= s.parkedOut || outputFull(out, r.output()) || noFuel && fuelInStock
                || held == 0 && quiet >= SETTLE_TICKS || quiet >= QUIET_TICKS && !(noFuel && !fuelInStock);
    }

    /**
     * 加工の残り時間の見込み（tick。分からなければ -1）。かまどは、焼く物の数と今の進み具合から（燃料が切れていれば -1）。
     */
    public static int eta(Level level, BlockPos at) {
        if (level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity f) {
            ItemStack cooking = f.getItem(0);
            if (cooking.isEmpty()) {
                return 0;
            }
            if (f.litTime <= 0 && f.getItem(1).isEmpty()) {
                return -1;
            }
            int each = Math.max(1, f.cookingTotalTime);
            return Math.max(0, each - f.cookingProgress) + (cooking.getCount() - 1) * each;
        }
        return -1;
    }

    /** かまどの燃料が切れて、焼く物が残ったまま止まっている（燃料を記録したレシピだけ。アンドロイドが足しに行く）。 */
    public static boolean outOfFuel(Level level, BlockPos at, RecipeData r) {
        if (r.fuel().isEmpty() || !(level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity f)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        boolean lit = state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
        return !lit && f.getItem(1).isEmpty() && !f.getItem(0).isEmpty();
    }

    /**
     * 入れる面が、この素材を受け付けない（記録した面の間違い。かまどの下の面など）: その面から見える欄がどれも空なのに、
     * どの欄にも入らない。欄が埋まっているだけ（加工中）なら false。
     */
    public static boolean refuses(IItemHandler in, ItemStack item) {
        if (in.getSlots() == 0) {
            return true;
        }
        for (int i = 0; i < in.getSlots(); i++) {
            if (!in.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < in.getSlots(); i++) {
            if (in.insertItem(i, item.copyWithCount(1), true).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 取り出す面のどれかから、物か流体を出し入れできるか。 */
    public static boolean handlesOutput(Level level, BlockPos at, RecipeData r) {
        for (net.minecraft.core.Direction d : r.outputAuto() ? OUT_ORDER : new net.minecraft.core.Direction[] {r.outputFace(level.getBlockState(at))}) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, at, d) != null || LiquidStores.handles(level, at, d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * できた物を今取り出す面: 記録した面か、自動なら、かまどの仲間は下（横は燃料の欄なので）、ほかの装置は下・横・上の順に、
     * 主な物か副産物を今取り出せる最初の面（まだ何もできていなければ null）。
     */
    @javax.annotation.Nullable
    public static net.minecraft.core.Direction outputFace(Level level, BlockPos at, RecipeData r) {
        if (!r.outputAuto()) {
            return r.outputFace(level.getBlockState(at));
        }
        if (level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity) {
            return net.minecraft.core.Direction.DOWN;
        }
        for (net.minecraft.core.Direction d : OUT_ORDER) {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d);
            for (int i = 0; h != null && i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && producedBy(r, s) && !h.extractItem(i, 1, true).isEmpty()) {
                    return d;
                }
            }
            LiquidStores liquids = LiquidStores.machine(level, at, d);
            for (ItemStack o : r.outputs()) {
                if (FluidSamples.liquid(o) && liquids.drain(o, 1, true) > 0) {
                    return d;
                }
            }
        }
        return null;
    }

    /** レシピで出てくる物（主な物か副産物）か。 */
    private static boolean producedBy(RecipeData r, ItemStack s) {
        for (ItemStack o : r.outputs()) {
            if (ItemStack.isSameItemSameComponents(o, s)) {
                return true;
            }
        }
        return false;
    }

    /** 素材を入れるときに試す面（記録した面か、自動なら上・横・下の順）。 */
    private static net.minecraft.core.Direction[] inputFaces(Level level, BlockPos at, RecipeData r) {
        return r.inputAuto() ? AUTO_ORDER : new net.minecraft.core.Direction[] {r.inputFace(level.getBlockState(at))};
    }

    /** 素材を入れる面のどれかから、物か流体を出し入れできるか（装置として使えるか）。 */
    public static boolean handlesInput(Level level, BlockPos at, RecipeData r) {
        for (net.minecraft.core.Direction d : inputFaces(level, at, r)) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, at, d) != null || LiquidStores.handles(level, at, d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 素材 item（流体の見本なら流体）を今入れる面: 記録した面か、自動なら上・横・下の順に、今その物を受け付ける面
     * （どこも受け付けなければ null。いっぱいなど）。かまどなら、焼く物は上、燃料にもなる物でも上から（焼く物の欄）入る。
     */
    @javax.annotation.Nullable
    public static net.minecraft.core.Direction inputFace(Level level, BlockPos at, RecipeData r, ItemStack item) {
        if (!r.inputAuto()) {
            return r.inputFace(level.getBlockState(at));
        }
        for (net.minecraft.core.Direction d : AUTO_ORDER) {
            if (accepts(level, at, d, item)) {
                return d;
            }
        }
        return null;
    }

    /** 面 d から、今 item を入れられるか。 */
    private static boolean accepts(Level level, BlockPos at, net.minecraft.core.Direction d, ItemStack item) {
        if (FluidSamples.liquid(item)) {
            return LiquidStores.machine(level, at, d).fill(item, 1, true) > 0;
        }
        if (level.getBlockEntity(at) instanceof net.minecraft.world.WorldlyContainer wc && !placeable(wc, d, item)) {
            return false;                          // かまどの横（燃料の欄）に焼く物を入れない
        }
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d);
        if (h == null) {
            return false;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            if (h.insertItem(i, item.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 面ごとに欄が決まった装置（かまどなど）で、面 d の欄に item を置けるか（中身に関わらず）。 */
    private static boolean placeable(net.minecraft.world.WorldlyContainer wc, net.minecraft.core.Direction d, ItemStack item) {
        for (int slot : wc.getSlotsForFace(d)) {
            if (wc.canPlaceItemThroughFace(slot, item, d) && wc.canPlaceItem(slot, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 加工モードのレシピの、物の素材のうち、装置 at がどの入れる面からも受け付けないもの（なければ空）。かまどなど面ごとに欄が決まった
     * 装置は、中身に関わらず、その面の欄に置けるかで見る。ほかの装置は {@link #refuses}（欄が埋まっているだけなら受け付ける扱い）。
     */
    public static ItemStack refusedInput(Level level, BlockPos at, RecipeData r) {
        for (ItemCount need : r.inputs()) {
            if (FluidSamples.liquid(need.item)) {
                continue;
            }
            boolean ok = false;
            for (net.minecraft.core.Direction face : inputFaces(level, at, r)) {
                if (level.getBlockEntity(at) instanceof net.minecraft.world.WorldlyContainer wc) {
                    ok |= placeable(wc, face, need.item);
                } else {
                    IItemHandler in = level.getCapability(Capabilities.ItemHandler.BLOCK, at, face);
                    ok |= in != null && !refuses(in, need.item);
                }
            }
            if (!ok) {
                return need.item;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 装置にまだ残っている、入れた素材の数（流体は量）。自動の面では、上・横・下の順に、その素材が見える最初の面で数える
     * （同じ欄を2つの面から数えないように）。
     */
    public static long held(Level level, BlockPos at, RecipeData r) {
        long n = 0;
        for (ItemCount need : r.inputs()) {
            for (net.minecraft.core.Direction d : inputFaces(level, at, r)) {
                long here = 0;
                if (FluidSamples.liquid(need.item)) {
                    here = LiquidStores.machine(level, at, d).amount(need.item);
                } else {
                    IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d);
                    if (h != null) {
                        here = heldInputs(h, List.of(need));
                    }
                }
                if (here > 0) {
                    n += here;
                    break;
                }
            }
        }
        return n;
    }

    /** 出口にある、主な物の数（物だけ。液体・化学物質は {@link LiquidStores#machine}）。 */
    private static long output(IItemHandler out, ItemStack main) {
        long n = 0;
        for (int i = 0; out != null && i < out.getSlots(); i++) {
            if (ItemStack.isSameItemSameComponents(out.getStackInSlot(i), main)) {
                n += out.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    /** 出口の欄に、主な物でいっぱいになった欄がある（それ以上できない）。 */
    private static boolean outputFull(IItemHandler out, ItemStack main) {
        for (int i = 0; out != null && i < out.getSlots(); i++) {
            ItemStack s = out.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(s, main) && s.getCount() >= Math.min(s.getMaxStackSize(), out.getSlotLimit(i))) {
                return true;
            }
        }
        return false;
    }

    /** 装置（入れる面）に残っている、入れた素材の数。 */
    private static long heldInputs(IItemHandler h, List<ItemCount> inputs) {
        long n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            for (ItemCount in : inputs) {
                if (!s.isEmpty() && !FluidSamples.liquid(in.item) && in.matches(s)) {
                    n += s.getCount();
                    break;
                }
            }
        }
        return n;
    }

}
