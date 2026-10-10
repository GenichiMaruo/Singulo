package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.data.MassValues;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 汎用加工装置。マルチブロックのコントローラはこれを継承し、形成状態・大きさ・速度を差し込む。
 */
public class MachineBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        io.github.genichimaruo.singulo.multiblock.MultiblockPortBlockEntity.Outputs {
    public enum Status { IDLE, RUNNING, NO_POWER, OUTPUT_FULL, NOT_FORMED, OFF }

    /**
     * 同期する値の並び（SyncedInts の番号）。この後にタンクごとに 流体ID+1・量 が続く。
     * D_STRUCTURE はマルチブロックの大きさ（-1 = マルチブロックでない、0 = 未形成）。D_FLAGS は FLAG_* のビット。
     */
    public static final int D_PROGRESS = 0, D_MAX_PROGRESS = 1, D_ENERGY = 2, D_CAPACITY = 3, D_USAGE = 4,
            D_STATUS = 5, D_MODE = 6, D_MASS = 7, D_MASS_TARGET = 8, D_STRUCTURE = 9, D_FLAGS = 10, D_TANKS = 11;
    /** 動かす（電源スイッチ）・材料なしのレシピも作る・材料なしのレシピがある。 */
    public static final int FLAG_ENABLED = 1, FLAG_MAKE_FREE = 2, FLAG_HAS_FREE = 4;

    private final MachineType type;
    private final ItemStackHandler items;
    private final FluidTank[] tanks;
    private final SinguloEnergyStorage energy;
    private final IItemHandler automationItems;
    private final IFluidHandler automationFluids;

    private int progress;
    private int maxProgress;
    private int usage;
    private Status status = Status.IDLE;
    /** 0 = 通常加工、1以上 = 質量レシピ（massRecipes の番号+1）。 */
    private int mode;
    private double mass;
    private double metalMass;
    @Nullable
    private MachineRecipe current;
    /** 保存されていた処理途中のレシピ。読み込み後に同じレシピが選ばれたら進捗を引き継ぐ。 */
    @Nullable
    private ResourceLocation resumeRecipe;
    private boolean dirty = true;
    private int underpoweredTicks;
    /** 時間の場（TimeFields）で進む tick の端数。 */
    private double timeCarry;
    /** 質量バッファに入れた元ブロックの種類（混成ボーナス用）。 */
    private final java.util.Set<net.minecraft.world.item.Item> massSources = new java.util.HashSet<>();
    /** 電源スイッチ。切ると処理を止める（進み具合はそのまま残す）。 */
    private boolean enabled = true;
    /** 材料のいらないレシピ（電力だけで作るもの）も作るか。 */
    private boolean makeFree = true;
    /** この装置に材料のいらないレシピがあるか（画面にスイッチを出すか）。 */
    private boolean hasFree;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.MACHINE.get(), pos, state);
    }

    protected MachineBlockEntity(BlockEntityType<?> beType, BlockPos pos, BlockState state) {
        super(beType, pos, state);
        this.type = ((MachineBlock) state.getBlock()).type();
        this.items = new ItemStackHandler(type.itemSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                markDirty();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slotAccepts(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot == type.upgradeSlot() ? 1 : 64;
            }
        };
        this.tanks = new FluidTank[type.tanks()];
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] = new FluidTank(MachineType.TANK_CAPACITY) {
                @Override
                protected void onContentsChanged() {
                    markDirty();
                }
            };
        }
        int capacity = type.energyCapacity();
        this.energy = new SinguloEnergyStorage(capacity, capacity / 10, 0, this::setChanged);
        this.automationItems = new AutomationItems();
        this.automationFluids = new AutomationFluids();
        this.tankSides = createTankSides(type);
    }

    public MachineType type() {
        return type;
    }

    public ItemStackHandler items() {
        return items;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public IItemHandler automationItems() {
        return automationItems;
    }

    public IFluidHandler automationFluids() {
        return automationFluids;
    }

    // ------------------------------------------------------------------ スロットの決まり・アップグレード・面の設定

    public static boolean isCatalyst(ItemStack stack) {
        return io.github.genichimaruo.singulo.item.CatalystHelper.tierOf(stack) > 0;
    }

    public static boolean isMonopoleUpgrade(ItemStack stack) {
        return stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("monopole_upgrade")));
    }

    public static boolean isOverclockChip(ItemStack stack) {
        return stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("overclock_chip")));
    }

    /** アップグレード枠に入るもの（単極子アップグレードかオーバークロック・チップ）。 */
    public static boolean isUpgrade(ItemStack stack) {
        return isMonopoleUpgrade(stack) || isOverclockChip(stack);
    }

    /** オーバークロック・チップが挿さっているか（処理速度×3、電力×4）。 */
    public boolean hasOverclock() {
        return isOverclockChip(items.getStackInSlot(type.upgradeSlot()));
    }

    /** そのスロットにそのアイテムを入れてよいか（触媒は専用スロットだけ、アップグレードはアップグレード枠だけ）。 */
    public boolean slotAccepts(int slot, ItemStack stack) {
        if (slot == type.upgradeSlot()) {
            return isUpgrade(stack);
        }
        if (slot == type.catalystSlot()) {
            return isCatalyst(stack);
        }
        if (slot < type.inputSlots()) {
            return !(type.hasCatalystSlot() && isCatalyst(stack));
        }
        return true;
    }

    /** 単極子アップグレードが挿さっているか（処理速度×2、電力効率+50%）。 */
    public boolean hasMonopole() {
        return isMonopoleUpgrade(items.getStackInSlot(type.upgradeSlot()));
    }

    private final SideConfig itemSides = new SideConfig();
    /**
     * タンクごとの面の設定（タンクの番号順: 入力タンク → 出力タンク）。入力タンクは「無効・入力」、出力タンクは
     * 「無効・出力」と自動排出を持つ。画面ではタンクの枠の色と、面の設定の色をそろえて見せる。
     */
    private final SideConfig[] tankSides;

    /** はじめは、どの面からでも入力タンクへ入れられ、出力タンクから出せる（自動排出なし）。 */
    private static SideConfig[] createTankSides(MachineType type) {
        SideConfig[] out = new SideConfig[type.tanks()];
        for (int i = 0; i < out.length; i++) {
            out[i] = new SideConfig();
            int mode = i < type.fluidInputs() ? SideConfig.INPUT : SideConfig.OUTPUT;
            for (SideConfig.Face f : SideConfig.Face.values()) {
                out[i].set(f, mode, false);
            }
        }
        return out;
    }

    /** 面の設定の種類。0 はアイテム、1 以降はタンクごと（1 + タンクの番号）。 */
    public int sideChannels() {
        return 1 + tankSides.length;
    }

    private SideConfig sides(int channel) {
        return channel == 0 ? itemSides : tankSides[channel - 1];
    }

    public boolean validChannel(int channel) {
        return channel >= 0 && channel < sideChannels();
    }

    public SideConfig itemSides() {
        return itemSides;
    }

    /** タンク tank の面の設定。 */
    public SideConfig tankSides(int tank) {
        return tankSides[tank];
    }

    /** 自動排出の全体スイッチを切り替える。 */
    public void setEjectEnabled(int channel, boolean on) {
        sides(channel).setEjectEnabled(on);
        setChanged();
    }

    /** 面の設定を変える（GUIから）。channel は {@link #sideChannels()} の番号。 */
    public void setSide(int channel, SideConfig.Face face, int mode, boolean eject) {
        sides(channel).set(face, mode, eject);
        setChanged();
        if (level != null) {
        }
    }

    private SideConfig.Face faceOf(net.minecraft.core.Direction side) {
        BlockState s = getBlockState();
        net.minecraft.core.Direction facing = s.hasProperty(AbstractMachineBlock.FACING) ? s.getValue(AbstractMachineBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        return SideConfig.faceOf(facing, side);
    }

    /** タンクごとの面の設定（設定カード用）。 */
    public int[] tankSidesPacked() {
        int[] out = new int[tankSides.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = tankSides[i].packed();
        }
        return out;
    }

    /** 設定カードから面の設定を貼る。tanks はタンクごとの設定（同じ種類の装置なので数は合う。合わなければ貼らない）。 */
    public void pasteSides(int items, int[] tanks) {
        itemSides.setPacked(items);
        if (tanks.length == tankSides.length) {
            for (int i = 0; i < tanks.length; i++) {
                tankSides[i].setPacked(tanks[i]);
            }
        }
        setChanged();
        if (level != null) {
        }
    }

    /** マルチブロックのコントローラは面の設定を持たない（入出力は搬入出ポートで行い、どの面からでも出し入れできる）。 */
    public boolean usesSideConfig() {
        return !type.isMultiblock();
    }

    /** 搬入出ポートが押し出してよい出力（出力スロットと出力タンクだけ）。 */
    @Override
    public IItemHandler ejectItems() {
        return type.hasOutput() ? new SidedItems(false, true) : null;
    }

    @Override
    public IFluidHandler ejectFluids() {
        return type.fluidOutputs() > 0 ? new SidedFluids(0, -1) : null;
    }

    /** 面ごとの設定に従うアイテムの入れ物（side が null なら制限なし）。無効の面は null。 */
    @javax.annotation.Nullable
    public IItemHandler itemsFor(@javax.annotation.Nullable net.minecraft.core.Direction side) {
        if (side == null || !usesSideConfig()) {
            return automationItems;
        }
        SideConfig.Face f = faceOf(side);
        if (itemSides.mode(f) == SideConfig.NONE) {
            return null;
        }
        boolean in = itemSides.canInsert(f);
        boolean out = itemSides.canExtract(f);
        return in && out ? automationItems : new SidedItems(in, out);
    }

    /** 面ごとの設定に従う液体の入れ物。無効の面は null。 */
    @javax.annotation.Nullable
    public IFluidHandler fluidsFor(@javax.annotation.Nullable net.minecraft.core.Direction side) {
        if (type.tanks() == 0) {
            return null;
        }
        if (side == null || !usesSideConfig()) {
            return automationFluids;
        }
        SideConfig.Face f = faceOf(side);
        int inTanks = 0;
        int outTanks = 0;
        for (int i = 0; i < tankSides.length; i++) {
            if (i < type.fluidInputs() ? tankSides[i].canInsert(f) : tankSides[i].canExtract(f)) {
                if (i < type.fluidInputs()) {
                    inTanks |= 1 << i;
                } else {
                    outTanks |= 1 << i;
                }
            }
        }
        return inTanks == 0 && outTanks == 0 ? null : new SidedFluids(inTanks, outTanks);
    }

    /** 自動排出: 「出力」を含み自動排出がオンの面へ、出力スロットと出力タンクの中身を押し出す。 */
    private void autoEject(Level level) {
        BlockState s = getBlockState();
        if (!s.hasProperty(AbstractMachineBlock.FACING)) {
            return;
        }
        net.minecraft.core.Direction facing = s.getValue(AbstractMachineBlock.FACING);
        for (SideConfig.Face f : SideConfig.Face.values()) {
            net.minecraft.core.Direction dir = SideConfig.directionOf(facing, f);
            BlockPos target = worldPosition.relative(dir);
            if (itemSides.ejectEnabled() && itemSides.eject(f) && itemSides.canExtract(f) && type.hasOutput()) {
                IItemHandler dest = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK,
                        target, dir.getOpposite());
                if (dest != null) {
                    for (int i = 0; i < type.outputSlots(); i++) {
                        int slot = type.outputSlot() + i;
                        ItemStack stack = items.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItem(dest, stack.copy(), false);
                            items.setStackInSlot(slot, rest);
                        }
                    }
                }
            }
            if (type.fluidOutputs() > 0) {
                IFluidHandler dest = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.FluidHandler.BLOCK,
                        target, dir.getOpposite());
                if (dest != null) {
                    for (int i = type.fluidInputs(); i < tanks.length; i++) {
                        SideConfig c = tankSides[i];
                        if (c.ejectEnabled() && c.eject(f) && c.canExtract(f) && !tanks[i].isEmpty()) {
                            int moved = dest.fill(tanks[i].getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                            if (moved > 0) {
                                tanks[i].drain(moved, IFluidHandler.FluidAction.EXECUTE);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void markDirty() {
        dirty = true;
        setChanged();
    }

    // ------------------------------------------------------------------ 継承先が差し込むところ

    /** 動ける状態か（マルチブロックなら形成済みか）。 */
    protected boolean canOperate(Level level) {
        return true;
    }

    /** マルチブロックの大きさ。-1 はマルチブロックでない。 */
    public int structureSize() {
        return -1;
    }

    /** 処理速度の倍率（マルチブロックの大きさなど）。 */
    protected double speedMultiplier() {
        return 1.0;
    }

    /** レシピを1回完了した直後に呼ばれる。 */
    protected void onCrafted(Level level, MachineRecipe recipe) {}

    /**
     * 電力が足りなくても、ある分だけ使って進めるか（時間結晶育成槽）。進めた場合、要求の80%を割った tick は
     * 「不純」として数え、完成品の adjustResult に純度（不純でなかった割合）を渡す。
     */
    protected boolean runsUnderpowered() {
        return false;
    }

    /** 完成品を出力に入れる直前に手を加える。purity は 0〜1。 */
    protected ItemStack adjustResult(MachineRecipe recipe, ItemStack result, double purity) {
        return result;
    }

    // ------------------------------------------------------------------ 処理

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.tick(level, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 10 == 0 && usesSideConfig()) {
            autoEject(level);
        }
        boolean active = false;
        if (!enabled) {
            usage = 0;
            status = Status.OFF;
        } else if (!canOperate(level)) {
            progress = 0;
            usage = 0;
            current = null;
            dirty = true;
            status = Status.NOT_FORMED;
        } else {
            if (type.massMode() && mode > 0) {
                absorbMass(level);
            }
            if (dirty) {
                dirty = false;
                refreshRecipe(level);
            }
            if (current == null) {
                progress = 0;
                usage = 0;
                status = Status.IDLE;
            } else {
                MachineRecipe recipe = current.value();
                // 時間の場の中では1 tick に進む量が変わる（加速なら2 tick ぶん進み、電力も2倍使う）
                timeCarry += TimeFields.speed(level, pos);
                int steps = (int) timeCarry;
                if (!canOutput(recipe)) {
                    status = Status.OUTPUT_FULL;
                    timeCarry = 0;
                } else if (steps == 0) {
                    active = true;
                    status = Status.RUNNING;
                } else if (energy.consume(usage * steps) || runsUnderpowered() && drawPartial()) {
                    timeCarry -= steps;
                    active = true;
                    status = Status.RUNNING;
                    progress += steps;
                    if (progress >= maxProgress) {
                        progress = 0;
                        craft(level, recipe);
                        dirty = true;
                    }
                } else {
                    timeCarry = 0;
                    status = Status.NO_POWER;
                }
            }
        }
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    /** 電力が足りないとき、ある分だけ使う。要求の80%未満なら不純な tick として数える。何もなければ false。 */
    private boolean drawPartial() {
        int available = energy.getEnergyStored();
        if (available <= 0) {
            return false;
        }
        energy.consume(available);
        double threshold = ServerConfig.SPEC.isLoaded() ? ServerConfig.UNDERPOWER_THRESHOLD.get() : 0.8;
        if (available < usage * threshold) {
            underpoweredTicks++;
        }
        return true;
    }

    /**
     * この装置で処理できるレシピ。priority の大きいもの、次に材料の多いものを先に試すので、入力なしのレシピ
     * （冷却塔の液体窒素など）は、ほかに作れるものがないときだけ動く。
     */
    private List<MachineRecipe> stationRecipes(Level level) {
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe holder : level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            if (holder.value().station().equals(type.id())) {
                out.add(holder);
            }
        }
        out.sort(Comparator.comparingInt((MachineRecipe h) -> -h.value().priority())
                .thenComparingInt(h -> -(h.value().ingredients().size() + h.value().fluidIngredients().size()
                        + (h.value().restore().isPresent() ? 1 : 0))).thenComparing(h -> h.id().toString()));
        return out;
    }

    /** 質量モードで選べるレシピ（ID順）。クライアントの画面でも同じ順に並べる。 */
    public static List<MachineRecipe> massRecipes(Level level, MachineType type) {
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe holder : level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            if (holder.value().station().equals(type.id()) && holder.value().isMassRecipe()) {
                out.add(holder);
            }
        }
        out.sort(Comparator.comparing(h -> h.id().toString()));
        return out;
    }

    @Nullable
    private MachineRecipe selectedMassRecipe(Level level) {
        List<MachineRecipe> list = massRecipes(level, type);
        return mode > 0 && mode <= list.size() ? list.get(mode - 1) : null;
    }

    private boolean sizeAllows(MachineRecipe r) {
        return r.minSize() <= 0 || structureSize() >= r.minSize();
    }

    private void refreshRecipe(Level level) {
        MachineRecipe found = null;
        List<ItemStack> inputs = inputStacks();
        if (mode > 0) {
            MachineRecipe selected = selectedMassRecipe(level);
            if (selected != null && massAvailable(selected.value()) && selected.value().plan(inputsExceptMassSlot()) != null
                    && fluidsAvailable(selected.value())) {
                found = selected;
            }
        } else {
            MachineRecipe blocked = null;
            hasFree = false;
            for (MachineRecipe holder : stationRecipes(level)) {
                MachineRecipe r = holder.value();
                boolean free = isFree(r);
                hasFree |= free;
                if (r.isMassRecipe() || !sizeAllows(r) || r.plan(inputs) == null || !fluidsAvailable(r)
                        || free && !makeFree || found != null) {
                    continue;
                }
                if (canOutput(r)) {
                    found = holder;
                    continue;
                }
                if (blocked == null) {
                    blocked = holder;
                }
            }
            // どれも出力が詰まっているだけなら、それを選んで「出力がいっぱい」と表示する
            if (found == null) {
                found = blocked;
            }
        }
        ResourceLocation previous = current != null ? current.id() : resumeRecipe;
        resumeRecipe = null;
        if (found == null || !found.id().equals(previous)) {
            progress = 0;
            underpoweredTicks = 0;
        }
        current = found;
        if (found != null) {
            double speed = speedMultiplier();
            if (type == MachineType.COMPRESSOR) {
                speed *= ServerConfig.COMPRESSOR_SPEED_MULTIPLIER.get();
            }
            if (hasMonopole()) {
                speed *= 2;
            }
            if (hasOverclock()) {
                speed *= 3;
            }
            maxProgress = Math.max(1, (int) Math.round(found.value().time() / speed));
            usage = (int) Math.ceil(found.value().energy() * ServerConfig.MACHINE_ENERGY_MULTIPLIER.get()
                    / (hasMonopole() ? 1.5 : 1.0) * (hasOverclock() ? 4.0 : 1.0));
        }
    }

    /** 材料（アイテム・液体・質量・復元するもの）がいらず、電力だけで作るレシピか（冷却塔の液体窒素など）。 */
    public static boolean isFree(MachineRecipe r) {
        return r.ingredients().isEmpty() && r.fluidIngredients().isEmpty() && r.mass().isEmpty() && r.restore().isEmpty();
    }

    /** 電源スイッチ（GUIから）。 */
    public void togglePower() {
        enabled = !enabled;
        markDirty();
    }

    /** 材料なしのレシピを作るかのスイッチ（GUIから）。 */
    public void toggleMakeFree() {
        makeFree = !makeFree;
        markDirty();
    }

    public boolean enabled() {
        return enabled;
    }

    public Status status() {
        return status;
    }

    /** レシピの照合に使うスロット（入力 → 触媒）の中身。 */
    private List<ItemStack> inputStacks() {
        List<ItemStack> list = new ArrayList<>(type.recipeSlots());
        for (int i = 0; i < type.recipeSlots(); i++) {
            list.add(items.getStackInSlot(type.recipeSlot(i)));
        }
        return list;
    }

    /** 質量モードでは入力スロット0を質量の投入口に使うので、材料の照合から外す。 */
    private List<ItemStack> inputsExceptMassSlot() {
        List<ItemStack> list = inputStacks();
        if (!list.isEmpty()) {
            list.set(0, ItemStack.EMPTY);
        }
        return list;
    }

    private boolean massAvailable(MachineRecipe r) {
        return r.mass().map(m -> (m.metalOnly() ? metalMass : mass) >= m.amount() - 1e-6).orElse(true);
    }

    /** 液体材料は入力タンクを先に、足りなければ出力タンク（自分で作った液体窒素など）から取る。 */
    @Nullable
    private FluidTank tankFor(MachineRecipe.FluidInput in) {
        for (FluidTank tank : tanks) {
            if (in.matches(tank.getFluid())) {
                return tank;
            }
        }
        return null;
    }

    private boolean fluidsAvailable(MachineRecipe r) {
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            if (tankFor(in) == null) {
                return false;
            }
        }
        return true;
    }

    private void absorbMass(Level level) {
        MachineRecipe selected = selectedMassRecipe(level);
        if (selected == null || selected.value().mass().isEmpty()) {
            return;
        }
        MachineRecipe.MassInput need = selected.value().mass().get();
        for (int n = 0; n < 8; n++) {
            double have = need.metalOnly() ? metalMass : mass;
            if (have >= need.amount()) {
                return;
            }
            ItemStack stack = items.getStackInSlot(0);
            if (stack.isEmpty()) {
                return;
            }
            double m = MassValues.INSTANCE.massOf(stack);
            if (m <= 0 || need.metalOnly() && !MassValues.isMetal(stack)) {
                return;
            }
            ItemStack taken = items.extractItem(0, 1, false);
            if (need.metalOnly()) {
                metalMass += m;
            } else {
                mass += m;
                massSources.add(taken.getItem());
            }
            markDirty();
        }
    }

    private ItemStack previewResult(MachineRecipe r, MachineRecipe.Plan plan) {
        if (plan.restoreSlot() >= 0) {
            return UsesHelper.restore(items.getStackInSlot(type.recipeSlot(plan.restoreSlot())), r.result());
        }
        ItemStack out = r.result().copy();
        markMixed(r, out);
        return out;
    }

    private boolean canOutput(MachineRecipe r) {
        if (!r.result().isEmpty()) {
            if (!type.hasOutput()) {
                return false;
            }
            MachineRecipe.Plan plan = r.plan(r.isMassRecipe() ? inputsExceptMassSlot() : inputStacks());
            if (plan == null) {
                return false;
            }
            if (!insertOutput(previewResult(r, plan), true).isEmpty()) {
                return false;
            }
        }
        return assignFluidOutputs(r.fluidResults(), true);
    }

    /** 出力スロットへ順に入れ、入り切らなかった分を返す。 */
    protected ItemStack insertOutput(ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (int i = 0; i < type.outputSlots() && !rest.isEmpty(); i++) {
            rest = items.insertItem(type.outputSlot() + i, rest, simulate);
        }
        return rest;
    }

    /** 液体の成果物を出力タンクに入れる。同じ液体のタンクを優先し、なければ空のタンクへ。 */
    private boolean assignFluidOutputs(List<FluidStack> results, boolean simulate) {
        boolean[] used = new boolean[type.fluidOutputs()];
        for (FluidStack result : results) {
            int target = -1;
            for (int pass = 0; pass < 2 && target < 0; pass++) {
                for (int i = 0; i < type.fluidOutputs(); i++) {
                    FluidTank tank = tanks[type.fluidInputs() + i];
                    boolean candidate = pass == 0 ? tank.getFluid().isFluidEqual(result)
                            : tank.isEmpty();
                    if (!used[i] && candidate && tank.getSpace() >= result.getAmount()) {
                        target = i;
                        break;
                    }
                }
            }
            if (target < 0) {
                return false;
            }
            used[target] = true;
            if (!simulate) {
                tanks[type.fluidInputs() + target].fill(result.copy(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return true;
    }

    private void craft(Level level, MachineRecipe r) {
        MachineRecipe.Plan plan = r.plan(r.isMassRecipe() ? inputsExceptMassSlot() : inputStacks());
        if (plan == null || !massAvailable(r) || !fluidsAvailable(r)) {
            return;
        }
        ItemStack out = previewResult(r, plan);
        if (!out.isEmpty()) {
            out = adjustResult(r, out, 1.0 - Math.min(1.0, underpoweredTicks / (double) Math.max(1, maxProgress)));
        }
        underpoweredTicks = 0;
        if (r.isMassRecipe()) {
            massSources.clear();
        }
        for (int i = 0; i < type.recipeSlots(); i++) {
            int slot = type.recipeSlot(i);
            if (plan.uses()[i] > 0) {
                items.setStackInSlot(slot, UsesHelper.consume(items.getStackInSlot(slot), plan.uses()[i]));
            }
            if (plan.consume()[i] > 0) {
                items.extractItem(slot, plan.consume()[i], false);
            }
        }
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            FluidTank tank = tankFor(in);
            if (tank != null) {
                tank.drain(in.amount(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        r.mass().ifPresent(m -> {
            if (m.metalOnly()) {
                metalMass -= m.amount();
            } else {
                mass -= m.amount();
            }
        });
        if (!out.isEmpty()) {
            insertOutput(out, false);
        }
        assignFluidOutputs(r.fluidResults(), false);
        onCrafted(level, r);
    }

    /**
     * 混成ボーナス: 圧縮ブロックLv1 を作るのに使った質量が mixedBonusMinKinds 種類以上の元ブロックから来ていれば印をつける
     * （その Lv1 は8個で Lv2 にできる）。種類の記録は1個作るごとに消す（craft）。
     */
    private void markMixed(MachineRecipe r, ItemStack out) {
        if (!r.isMassRecipe() || r.mass().get().metalOnly()) {
            return;
        }
        boolean enabled = !ServerConfig.SPEC.isLoaded() || ServerConfig.MIXED_BONUS_ENABLED.get();
        int minKinds = ServerConfig.SPEC.isLoaded() ? ServerConfig.MIXED_BONUS_MIN_KINDS.get() : 3;
        if (enabled && massSources.size() >= minKinds
                && BuiltInRegistries.ITEM.getKey(out.getItem()).getPath().equals("compressed_block_1")) {
            SinguloComponents.set(out, SinguloComponents.MIXED_SOURCE.get(), true);
        }
    }

    /** GUIのボタンから。通常加工 → 質量レシピ1 → 質量レシピ2 … と巡る。 */
    public void cycleMode() {
        if (!type.massMode() || level == null) {
            return;
        }
        int count = massRecipes(level, type).size();
        mode = (mode + 1) % (count + 1);
        markDirty();
    }

    // ------------------------------------------------------------------ 同期・GUI

    /** 同期する値の数。最後は面の設定（アイテム・タンクごと）。 */
    public int syncedCount() {
        return syncedCount(type);
    }

    public static int syncedCount(MachineType type) {
        return sidesIndex(type) + 1 + type.tanks();
    }

    public static int sidesIndex(MachineType type) {
        return D_TANKS + type.tanks() * 2;
    }

    public SyncedInts syncData() {
        return SyncedInts.server(syncedCount(), this::syncedValue);
    }

    private int syncedValue(int index) {
        return switch (index) {
            case D_PROGRESS -> progress;
            case D_MAX_PROGRESS -> maxProgress;
            case D_ENERGY -> energy.getEnergyStored();
            case D_CAPACITY -> energy.getMaxEnergyStored();
            case D_USAGE -> current == null ? 0 : usage;
            case D_STATUS -> status.ordinal();
            case D_MODE -> mode;
            case D_MASS -> massTargetIsMetal() ? (int) Math.round(metalMass * 100) : (int) Math.round(mass * 100);
            case D_MASS_TARGET -> massTarget();
            case D_STRUCTURE -> structureSize();
            case D_FLAGS -> (enabled ? FLAG_ENABLED : 0) | (makeFree ? FLAG_MAKE_FREE : 0) | (hasFree ? FLAG_HAS_FREE : 0);
            default -> {
                if (index >= sidesIndex(type)) {
                    yield sides(index - sidesIndex(type)).packed();
                }
                int t = (index - D_TANKS) / 2;
                FluidStack fluid = tanks[t].getFluid();
                yield (index - D_TANKS) % 2 == 0
                        ? (fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid()) + 1)
                        : fluid.getAmount();
            }
        };
    }

    private boolean massTargetIsMetal() {
        if (level == null) {
            return false;
        }
        MachineRecipe r = selectedMassRecipe(level);
        return r != null && r.value().mass().map(MachineRecipe.MassInput::metalOnly).orElse(false);
    }

    private int massTarget() {
        if (level == null) {
            return 0;
        }
        MachineRecipe r = selectedMassRecipe(level);
        return r == null ? 0 : r.value().mass().map(m -> (int) Math.round(m.amount() * 100)).orElse(0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(containerId, inventory, this);
    }

    @Override
    public void openMenu(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(type.ordinal());
        });
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, pos, items.getStackInSlot(i));
        }
        hydrogenLeak(level, pos);
    }

    /** テスト用: 最初の出力タンクの中身を決める。 */
    public void forceOutputTankForTest(net.minecraftforge.fluids.FluidStack stack) {
        tanks[type.fluidInputs()].setFluid(stack);
    }

    /**
     * 水素の入った装置が壊れると水素が漏れ、半径 3 以内に火気（火・溶岩・燃えているかまどや焚き火）があると小爆発する
     * （設定 hydrogenLeakExplosion で無効にできる）。
     */
    private void hydrogenLeak(Level level, BlockPos pos) {
        if (level.isClientSide || ServerConfig.SPEC.isLoaded() && !ServerConfig.HYDROGEN_LEAK_EXPLOSION.get()) {
            return;
        }
        int hydrogen = 0;
        for (FluidTank tank : tanks) {
            if (tank.getFluid().getFluid().isSame(io.github.genichimaruo.singulo.registry.SinguloFluids.get("hydrogen"))) {
                hydrogen += tank.getFluidAmount();
            }
        }
        if (hydrogen < 100) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
            BlockState s = level.getBlockState(p);
            boolean fire = s.is(net.minecraft.tags.BlockTags.FIRE) || s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && !(s.getBlock() instanceof AbstractMachineBlock);
            if (fire) {
                float power = (float) Math.min(3.0, 1.0 + hydrogen / 4000.0);
                level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, Level.ExplosionInteraction.BLOCK);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("item_sides", itemSides.packed());
        tag.putBoolean("eject_master", true);
        tag.putIntArray("tank_sides", tankSidesPacked());
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("make_free", makeFree);
        ListTag tankList = new ListTag();
        for (FluidTank tank : tanks) {
            tankList.add(tank.writeToNBT(new CompoundTag()));
        }
        tag.put("tanks", tankList);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("progress", progress);
        tag.putInt("mode", mode);
        tag.putDouble("mass", mass);
        tag.putDouble("metal_mass", metalMass);
        if (current != null) {
            tag.putString("recipe", current.id().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // スロット数が変わった古いデータでも読めるよう、いったん別の入れ物に読んでから写す
        ItemStackHandler loaded = new ItemStackHandler();
        loaded.deserializeNBT(tag.getCompound("items"));
        for (int i = 0; i < items.getSlots(); i++) {
            items.setStackInSlot(i, i < loaded.getSlots() ? loaded.getStackInSlot(i) : ItemStack.EMPTY);
        }
        if (tag.contains("item_sides")) {
            itemSides.setPacked(tag.getInt("item_sides"));
            if (!tag.contains("eject_master")) {
                itemSides.setEjectEnabled(true);
            }
        }
        int[] savedTankSides = tag.getIntArray("tank_sides");
        if (savedTankSides.length == tankSides.length) {
            for (int i = 0; i < tankSides.length; i++) {
                tankSides[i].setPacked(savedTankSides[i]);
            }
        } else if (tag.contains("fluid_sides")) {
            // 古いデータ: 液体の設定が1つだけ。入力タンクは「入力」の面、出力タンクは「出力」の面と自動排出を引き継ぐ
            SideConfig old = new SideConfig();
            old.setPacked(tag.getInt("fluid_sides"));
            for (int i = 0; i < tankSides.length; i++) {
                boolean input = i < type.fluidInputs();
                for (SideConfig.Face f : SideConfig.Face.values()) {
                    boolean on = input ? old.canInsert(f) : old.canExtract(f);
                    tankSides[i].set(f, on ? (input ? SideConfig.INPUT : SideConfig.OUTPUT) : SideConfig.NONE,
                            !input && old.eject(f));
                }
                tankSides[i].setEjectEnabled(!tag.contains("eject_master") || old.ejectEnabled());
            }
        }
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        makeFree = !tag.contains("make_free") || tag.getBoolean("make_free");
        ListTag tankList = tag.getList("tanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < tanks.length && i < tankList.size(); i++) {
            tanks[i].readFromNBT(tankList.getCompound(i));
        }
        energy.setEnergy(tag.getInt("energy"));
        progress = tag.getInt("progress");
        mode = tag.getInt("mode");
        mass = tag.getDouble("mass");
        metalMass = tag.getDouble("metal_mass");
        resumeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        dirty = true;
    }

    // ------------------------------------------------------------------ 自動搬入出

    /** 入力スロットへは入れるだけ、出力スロットからは取り出すだけ。 */
    private final class AutomationItems implements IItemHandler {
        @Override
        public int getSlots() {
            return items.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < type.inputSlots() || slot == type.catalystSlot() ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= type.outputSlot() && slot < type.outputSlot() + type.outputSlots()
                    ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return (slot < type.inputSlots() || slot == type.catalystSlot()) && slotAccepts(slot, stack);
        }
    }

    /** 面の設定で入れるだけ・出すだけに絞ったアイテムの入れ物。 */
    private final class SidedItems implements IItemHandler {
        private final boolean in;
        private final boolean out;

        SidedItems(boolean in, boolean out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public int getSlots() {
            return automationItems.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return automationItems.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return in ? automationItems.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return out ? automationItems.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return automationItems.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return in && automationItems.isItemValid(slot, stack);
        }
    }

    /** 面の設定で絞った液体の入れ物。inTanks・outTanks は入れてよい・取り出してよいタンクのビット（-1 ならすべて）。 */
    private final class SidedFluids implements IFluidHandler {
        private final int inTanks;
        private final int outTanks;

        SidedFluids(int inTanks, int outTanks) {
            this.inTanks = inTanks;
            this.outTanks = outTanks;
        }

        @Override
        public int getTanks() {
            return automationFluids.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return automationFluids.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return automationFluids.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return (inTanks & (1 << tank)) != 0 && automationFluids.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fillInputs(resource, action, inTanks);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = type.fluidInputs(); i < tanks.length; i++) {
                if ((outTanks & (1 << i)) != 0 && tanks[i].getFluid().isFluidEqual(resource)) {
                    return tanks[i].drain(resource, action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = type.fluidInputs(); i < tanks.length; i++) {
                if ((outTanks & (1 << i)) != 0 && !tanks[i].isEmpty()) {
                    return tanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    /** 入力タンクに入れる。同じ液体の入ったタンクを先に、なければ空のタンクへ。mask は入れてよいタンクのビット。 */
    private int fillInputs(FluidStack resource, IFluidHandler.FluidAction action, int mask) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < type.fluidInputs(); i++) {
                if ((mask & (1 << i)) == 0) {
                    continue;
                }
                FluidTank tank = tanks[i];
                boolean candidate = pass == 0 ? tank.getFluid().isFluidEqual(resource)
                        : tank.isEmpty();
                if (candidate) {
                    return tank.fill(resource, action);
                }
            }
        }
        return 0;
    }

    /** 入力タンクへは入れるだけ、出力タンクからは取り出すだけ。 */
    private final class AutomationFluids implements IFluidHandler {
        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank < type.fluidInputs();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fillInputs(resource, action, -1);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = type.fluidInputs(); i < tanks.length; i++) {
                if (tanks[i].getFluid().isFluidEqual(resource)) {
                    return tanks[i].drain(resource, action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = type.fluidInputs(); i < tanks.length; i++) {
                if (!tanks[i].isEmpty()) {
                    return tanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
