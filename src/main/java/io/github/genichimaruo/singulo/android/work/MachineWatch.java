package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.api.machine.MachineReports;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private static final List<Direction> OUT_ORDER = List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
            Direction.EAST, Direction.UP);

    private MachineWatch() {}

    /** The controller behind a linked multiblock port owns the recipe selection and timing. */
    @javax.annotation.Nullable
    public static BlockEntity controller(Level level, BlockPos at) {
        var machine = level.getBlockEntity(at);
        if (machine instanceof io.github.genichimaruo.singulo.multiblock.PortBlockEntity port) {
            var pos = port.controllerPos();
            return pos != null && level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
        }
        return machine;
    }

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
        s.eta = eta(level, at);
        long sig = (have * 1_000_003L + held) * 31 + s.eta;
        if (sig != s.watchSig) {
            s.watchSig = sig;
            s.watchChanged = now;
        }
        long quiet = now - s.watchChanged;
        boolean noFuel = outOfFuel(level, at, r);
        s.collectReady = have >= s.parkedOut || outputFull(out, r.output()) || noFuel && fuelInStock
                || held == 0 && s.eta <= 0 && !r.inputs().isEmpty() && quiet >= SETTLE_TICKS
                || quiet >= QUIET_TICKS && !(noFuel && !fuelInStock);
    }

    /**
     * 加工の残り時間の見込み（tick。分からなければ -1）。かまどは、焼く物の数と今の進み具合から（燃料が切れていれば -1）。
     */
    public static int eta(Level level, BlockPos at) {
        var machine = controller(level, at);
        if (machine instanceof io.github.genichimaruo.singulo.machine.CosmicMuonCollectorBlockEntity collector) {
            return collector.remainingTicks();
        }
        var source = MachineReports.source(machine);
        if (source != null) {
            int remaining = source.remainingTicks();
            if (remaining >= 0) return remaining;
        }
        if (machine instanceof AbstractFurnaceBlockEntity f) {
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
        return !lit && (f.getItem(1).isEmpty() || removableFuelContainer(f, r)) && !f.getItem(0).isEmpty();
    }

    public static boolean removableFuelContainer(AbstractFurnaceBlockEntity furnace, RecipeData recipe) {
        ItemStack stack = furnace.getItem(1);
        if (stack.is(net.minecraft.world.item.Items.BUCKET)) {
            // A lit furnace fills this bucket when it finishes drying the sponge.
            return !furnace.getItem(0).is(net.minecraft.world.item.Items.WET_SPONGE)
                    || !furnace.getBlockState().getOptionalValue(AbstractFurnaceBlock.LIT).orElse(false);
        }
        // Both vanilla bucket types may remain in the fuel slot when a different card takes over.
        return stack.is(net.minecraft.world.item.Items.WATER_BUCKET) || !stack.isEmpty() && recipe.fuel().hasCraftingRemainingItem()
                && ItemStack.isSameItemSameComponents(stack, recipe.fuel().getCraftingRemainingItem());
    }

    @javax.annotation.Nullable
    public static RecipeData.Tool missingTool(Level level, BlockPos at, RecipeData r) {
        var table = AndroidAssembly.table(level, at);
        if (table != null) {
            var assembly = AndroidAssembly.find(level, r);
            if (assembly != null) for (var tool : assembly.tools()) {
                if (tool.crafts(r.grid().get(tool.slot()), assembly.loadedTool(table, tool)) == 0) return tool;
            }
            return null;
        }
        if (!r.tools().isEmpty() && controller(level, at) instanceof io.github.genichimaruo.singulo.machine.MachineBlockEntity machine) {
            var loaded = machine.inputStacks();
            for (var tool : r.tools()) {
                if (loaded.stream().noneMatch(stack -> tool.crafts(r.grid().get(tool.slot()), stack) > 0)) return tool;
            }
        }
        return null;
    }

    public static long toolCraftLimit(Level level, BlockPos at, RecipeData r) {
        long crafts = Long.MAX_VALUE;
        var table = AndroidAssembly.table(level, at);
        if (table != null) {
            var assembly = AndroidAssembly.find(level, r);
            if (assembly != null) for (var tool : assembly.tools()) {
                crafts = Math.min(crafts, tool.crafts(r.grid().get(tool.slot()), assembly.loadedTool(table, tool)));
            }
            return Math.max(1, crafts);
        }
        if (!r.tools().isEmpty() && controller(level, at) instanceof io.github.genichimaruo.singulo.machine.MachineBlockEntity machine) {
            var loaded = machine.inputStacks();
            for (var tool : r.tools()) {
                long available = loaded.stream().mapToLong(stack -> tool.crafts(r.grid().get(tool.slot()), stack)).max().orElse(0);
                crafts = Math.min(crafts, available);
            }
        }
        return Math.max(1, crafts);
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
        var table = AndroidAssembly.table(level, at);
        if (table != null) {
            var facing = table.getBlockState().getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING);
            return outputFaces(level, at, r).stream().anyMatch(d -> table.itemSides().canExtract(
                    io.github.genichimaruo.singulo.machine.SideConfig.faceOf(facing, d)));
        }
        for (Direction d : outputFaces(level, at, r)) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, at, d) != null || LiquidStores.handles(level, at, d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * できた物を今取り出す面: 記録した面か、自動なら、かまどの仲間は下（横は燃料の欄なので）、ほかの装置は下・横・上の順に、
     * 主な物を今取り出せる最初の面（まだできていなければ null）。
     */
    @javax.annotation.Nullable
    public static net.minecraft.core.Direction outputFace(Level level, BlockPos at, RecipeData r) {
        for (Direction d : outputFaces(level, at, r)) {
            if (FluidSamples.liquid(r.output())) {
                if (LiquidStores.machine(level, at, d).drain(r.output(), 1, true) > 0) {
                    return d;
                }
                continue;
            }
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d);
            if (output(h, r.output()) > 0) {
                return d;
            }
        }
        return null;
    }

    /** 自動時は製品ごとの出口を調べる。かまどは下から製品と使用済みの燃料容器を取り出す。 */
    public static List<Direction> outputFaces(Level level, BlockPos at, RecipeData r) {
        if (!r.outputAuto()) return List.of(r.outputFace(level.getBlockState(at)));
        return level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity ? List.of(Direction.DOWN) : OUT_ORDER;
    }

    /** 素材を入れるときに試す面（記録した面か、自動なら上・横・下の順）。 */
    private static net.minecraft.core.Direction[] inputFaces(Level level, BlockPos at, RecipeData r) {
        return r.inputAuto() ? AUTO_ORDER : new net.minecraft.core.Direction[] {r.inputFace(level.getBlockState(at))};
    }

    /** 素材を入れる面のどれかから、物か流体を出し入れできるか（装置として使えるか）。 */
    public static boolean handlesInput(Level level, BlockPos at, RecipeData r) {
        if (AndroidAssembly.table(level, at) != null) return AndroidAssembly.find(level, r) != null;
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
        if (AndroidAssembly.table(level, at) != null) return AndroidAssembly.find(level, r) == null ? r.output() : ItemStack.EMPTY;
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
                    ok |= in != null && (!refuses(in, need.item)
                            || need.item.getItem() instanceof io.github.genichimaruo.singulo.wormhole.UnstableMouthItem
                            && WormholeOrders.pattern(need.item) && controller(level, at) instanceof io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity);
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
        var table = AndroidAssembly.table(level, at);
        if (table != null) {
            var board = table.assemblyGrid();
            for (int i = 0; i < board.getSlots(); i++) n += board.getStackInSlot(i).getCount();
            return n;
        }
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

    /** 出口にある、取り出せる主な物の数（物だけ。液体・化学物質は {@link LiquidStores#machine}。同じ物でも入れる欄の分は数えない）。 */
    private static long output(IItemHandler out, ItemStack main) {
        long n = 0;
        for (int i = 0; out != null && i < out.getSlots(); i++) {
            if (WormholeOrders.matches(main, out.getStackInSlot(i)) && !out.extractItem(i, 1, true).isEmpty()) {
                n += out.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    /** 出口の欄に、主な物でいっぱいになった欄がある（それ以上できない）。 */
    private static boolean outputFull(IItemHandler out, ItemStack main) {
        for (int i = 0; out != null && i < out.getSlots(); i++) {
            ItemStack s = out.getStackInSlot(i);
            if (WormholeOrders.matches(main, s) && s.getCount() >= Math.min(s.getMaxStackSize(), out.getSlotLimit(i))) {
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
                if (!s.isEmpty() && !FluidSamples.liquid(in.item) && WormholeOrders.matchesInput(in, s)) {
                    n += s.getCount();
                    break;
                }
            }
        }
        return n;
    }

}
