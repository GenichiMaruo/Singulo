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
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 汎用加工装置。マルチブロックのコントローラはこれを継承し、形成状態・大きさ・速度を差し込む。
 */
public class MachineBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        io.github.genichimaruo.singulo.multiblock.MultiblockPortBlockEntity.Outputs, SideConfig.Holder, MachineReport.Source {
    public enum Status { IDLE, RUNNING, NO_POWER, OUTPUT_FULL, NOT_FORMED, OFF }

    /**
     * 同期する値の並び（SyncedInts の番号）。この後にタンクごとに 流体ID+1・量 が続く。
     * D_STRUCTURE はマルチブロックの大きさ（-1 = マルチブロックでない、0 = 未形成）。D_FLAGS は FLAG_* のビット。
     */
    public static final int D_PROGRESS = 0, D_MAX_PROGRESS = 1, D_ENERGY = 2, D_CAPACITY = 3, D_USAGE = 4,
            D_STATUS = 5, D_MODE = 6, D_MASS = 7, D_MASS_TARGET = 8, D_STRUCTURE = 9, D_FLAGS = 10, D_SELECTED = 11,
            D_LOADED_ITEM = 12, D_LOADED_FILL = 13, D_LOADED_USES = 14, D_LINING = 15, D_TANKS = 16;
    /** 動かす（電源スイッチ）・材料なしのレシピも作る・材料なしのレシピがある。 */
    public static final int FLAG_ENABLED = 1, FLAG_MAKE_FREE = 2, FLAG_HAS_FREE = 4;

    private final MachineType type;
    private final ItemStackHandler items;
    @Nullable private final AssemblyGrid assemblyGrid;
    private final java.util.BitSet assemblyReturns = new java.util.BitSet(0);
    /** Bulk inputs from older assemblers, returned to a player on opening the machine. */
    private final List<ItemStack> legacyAssemblyInputs = new ArrayList<>();
    private int assemblyPreview = -2;
    /** Exhausted recipe tools waiting for space in the normal output slots. */
    private final java.util.BitSet pendingReturns = new java.util.BitSet();
    /** 触媒の装填（触媒スロットのある装置だけ使う）。触媒スロットは予備の置き場で、使う1個は中に装填する。 */
    private final CatalystMagazine magazine = new CatalystMagazine(this::markDirty);
    private final FluidTank[] tanks;
    private final SinguloEnergyStorage energy;
    private final IItemHandler automationItems;
    private final IFluidHandler automationFluids;

    private int progress;
    private int maxProgress;
    /** Paid growth duration while the final product waits for a compatible output slot. */
    private int completedDuration;
    private int batchRemaining;
    private int usage;
    private Status status = Status.IDLE;
    /** 0 = 通常加工、1以上 = 質量レシピ（massRecipes の番号+1）。 */
    private int mode;
    private double mass;
    private double metalMass;
    @Nullable
    private RecipeHolder<MachineRecipe> current;
    /** 保存されていた処理途中のレシピ。読み込み後に同じレシピが選ばれたら進捗を引き継ぐ。 */
    @Nullable
    private ResourceLocation resumeRecipe;
    private boolean dirty = true;
    private int underpoweredTicks;
    /** 時間の場（TimeFields）で進む tick の端数。 */
    private double timeCarry;
    /** 質量バッファに入れた元ブロックの種類（混成ボーナス用）。 */
    private final java.util.Set<ResourceLocation> massSources = new java.util.HashSet<>();
    /** 電源スイッチ。切ると処理を止める（進み具合はそのまま残す）。 */
    private boolean enabled = true;
    /** 材料のいらないレシピ（電力だけで作るもの）も作るか。 */
    private boolean makeFree = true;
    /** この装置に材料のいらないレシピがあるか（画面にスイッチを出すか）。 */
    private boolean hasFree;
    /** 指定した作るもの（{@link MachineType#selectsRecipe()} の装置だけ）。null なら自動で選ぶ。 */
    @Nullable
    private ResourceLocation selectedRecipe;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.MACHINE.get(), pos, state);
    }

    protected MachineBlockEntity(BlockEntityType<?> beType, BlockPos pos, BlockState state) {
        super(beType, pos, state);
        this.type = ((MachineBlock) state.getBlock()).type();
        this.items = new ItemStackHandler(type.itemSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                pendingReturns.clear(slot);
                if (!crafting && slot < type.inputSlots() && level != null) {
                    inputsChangedAt = level.getGameTime();          // 人・ホッパー・アンドロイドが入れ替えた（装置が使った分は数えない）
                }
                markDirty();
                if (slot == type.capacitySlot() && energy != null) refreshEnergyCapacity();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slotAccepts(slot, stack);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot == type.assemblyUpgradeSlot() && assemblyGrid != null && assemblyGrid.locksUpgrade()) return ItemStack.EMPTY;
                return super.extractItem(slot, amount, simulate);
            }

            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                if (slot == type.assemblyUpgradeSlot()) return AssemblyGrid.upgradeLimit();
                if (slot < type.inputSlots() + type.outputSlots()) return MachineStock.limit(stack, slot < type.inputSlots());
                if (slot == type.catalystSlot()) {
                    return Math.min(CatalystMagazine.SUPPLY_LIMIT, super.getStackLimit(slot, stack));
                }
                return slot < type.inputSlots() && UsesHelper.hasUses(stack) ? 1 : super.getStackLimit(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                if (slot < type.inputSlots() + type.outputSlots()) return MachineStock.limit(64);
                if (slot == type.assemblyUpgradeSlot()) return AssemblyGrid.upgradeLimit();
                return slot == type.upgradeSlot() || slot == type.capacitySlot() ? 1
                        : slot == type.catalystSlot() ? CatalystMagazine.SUPPLY_LIMIT : 64;
            }
        };
        this.assemblyGrid = type == MachineType.PRECISION_ASSEMBLER ? new AssemblyGrid() {
            @Override
            public int getSlotLimit(int slot) {
                return AssemblyGrid.limit(64, items.getStackInSlot(type.assemblyUpgradeSlot()));
            }

            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return AssemblyGrid.limit(stack, items.getStackInSlot(type.assemblyUpgradeSlot()));
            }

            @Override
            protected void onContentsChanged(int slot) {
                assemblyReturns.clear(slot);
                markDirty();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !assemblyReturns.get(slot) && !isCatalyst(stack);
            }
        } : null;
        this.tanks = new FluidTank[type.tanks()];
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] = new FluidTank(MachineType.tankCapacity()) {
                @Override
                public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
                    return getSpace() <= 0 ? 0 : super.fill(resource, action);
                }

                @Override
                protected void onContentsChanged() {
                    markDirty();
                }
            };
        }
        int capacity = type.energyCapacity();
        this.energy = new SinguloEnergyStorage(capacity, type.energyReceiveLimit(), 0, this::setChanged);
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

    /** The physical board used by players and androids. No external item capability exposes its cells. */
    public ItemStackHandler assemblyGrid() {
        return java.util.Objects.requireNonNull(assemblyGrid, "Machine has no assembly board");
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    private void refreshEnergyCapacity() {
        energy.setReceiveLimit(type.energyReceiveLimit());
        energy.resizeCapacity(Math.max(type.energyCapacity(), CapacityUpgrades.capacity(
                CapacityUpgrades.tier(items.getStackInSlot(type.capacitySlot())))));
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

    /** 触媒の装填。 */
    public CatalystMagazine magazine() {
        return magazine;
    }

    /** いま使っている触媒（装填中のもの。まだなければ予備のいちばん上）。触媒スロットのない装置では空。 */
    public ItemStack catalyst() {
        return type.hasCatalystSlot() ? magazine.current(items, type.catalystSlot()) : ItemStack.EMPTY;
    }

    /** 画面から: 装填中の触媒をプレイヤーへ取り出す。 */
    public void ejectLoaded(net.minecraft.world.entity.player.Player player) {
        ItemStack out = magazine.eject();
        if (!out.isEmpty() && !player.getInventory().add(out)) {
            player.drop(out, false);
        }
    }

    /** そのスロットにそのアイテムを入れてよいか（触媒は専用スロットだけ、アップグレードはアップグレード枠だけ）。 */
    public boolean slotAccepts(int slot, ItemStack stack) {
        if (pendingReturns.get(slot)) return false;
        if (slot == type.spentCatalystSlot()) return UsesHelper.isSpentCatalyst(stack);
        if (slot == type.assemblyUpgradeSlot()) return AssemblyGrid.isUpgrade(stack);
        if (slot == type.capacitySlot()) return CapacityUpgrades.tier(stack) > 0;
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
                out[i].set(f, mode, i >= type.fluidInputs());
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
        SideConfig config = sides(channel);
        // Old settings often had every per-face flag off despite the master being on.
        // Turning the master on must be sufficient to start the configured outputs.
        if (on && java.util.Arrays.stream(SideConfig.Face.values()).noneMatch(f -> config.canExtract(f) && config.eject(f))) {
            for (SideConfig.Face face : SideConfig.Face.values()) {
                if (config.canExtract(face)) config.set(face, config.mode(face), true);
            }
        }
        config.setEjectEnabled(on);
        setChanged();
    }

    /** 面の設定を変える（GUIから）。channel は {@link #sideChannels()} の番号。 */
    public void setSide(int channel, SideConfig.Face face, int mode, boolean eject) {
        sides(channel).set(face, mode, eject);
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            // 隣の液体パイプにつなぎ直させる（出し入れしない面になった・なくなった）
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
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
            level.invalidateCapabilities(worldPosition);
        }
    }

    /** マルチブロックのコントローラは面の設定を持たない（入出力は搬入出ポートで行い、どの面からでも出し入れできる）。 */
    public boolean usesSideConfig() {
        return !type.isMultiblock();
    }

    /** 搬入出ポートが押し出してよい出力（完成品・使用済み部品・出力タンク）。 */
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
                IItemHandler dest = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        target, dir.getOpposite());
                if (dest != null) {
                    // Finished products first, then exhausted tools still waiting in their input slots.
                    for (int i = 0; i < type.outputSlots(); i++) {
                        ejectItem(dest, type.outputSlot() + i);
                    }
                    if (type.spentCatalystSlot() >= 0) ejectItem(dest, type.spentCatalystSlot());
                    for (int slot = pendingReturns.nextSetBit(0); slot >= 0; slot = pendingReturns.nextSetBit(slot + 1)) {
                        ejectItem(dest, slot);
                    }
                }
            }
            if (type.fluidOutputs() > 0) {
                IFluidHandler dest = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
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

    private void ejectItem(IItemHandler dest, int slot) {
        SideConfig.pushOut(items, slot, dest);
    }

    /** Keep a blocked return visible in its original slot; never discard or consume it again. */
    private void flushReturns() {
        flushReturns(items, pendingReturns);
        if (assemblyGrid != null) flushReturns(assemblyGrid, assemblyReturns);
    }

    private void flushReturns(ItemStackHandler inventory, java.util.BitSet returns) {
        for (int slot = returns.nextSetBit(0); slot >= 0; slot = returns.nextSetBit(slot + 1)) {
            ItemStack stack = inventory.getStackInSlot(slot);
            ItemStack rest = insertOutput(stack.copy(), false);
            if (rest.getCount() != stack.getCount() || stack.isEmpty()) {
                inventory.setStackInSlot(slot, rest);
                if (!rest.isEmpty()) returns.set(slot);
            }
        }
    }

    protected void markDirty() {
        dirty = true;
        assemblyPreview = -2;
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

    /** マルチブロックの外装板の位置に置いたコラプサー内張りの数（内張りを受け付けない装置は 0）。 */
    public int liningCount() {
        return 0;
    }

    /** 処理速度の倍率（マルチブロックの大きさなど）。 */
    protected double speedMultiplier() {
        return 1.0;
    }

    /**
     * このレシピの1回あたりの電力を、速さによらず一定にするか（速くなると、そのぶん毎tickの電力が増える）。
     * 極低温冷却塔の液体窒素: 詰めた電力を極低温タービンが取り戻すので、速くして安く作れると、取り戻す電力のほうが多くなる。
     */
    protected boolean fixedEnergyPerCraft(MachineRecipe recipe) {
        // 電解槽の水素と酸素は水素燃料電池で電力に戻せるので、単極子などで安く作れると電力が増えてしまう
        return type == MachineType.ELECTROLYZER;
    }

    /** レシピを1回完了した直後に呼ばれる。 */
    protected void onCrafted(Level level, MachineRecipe recipe) {}

    /**
     * このレシピは、電力が足りなくても、ある分だけ使って進めるか（時間結晶育成槽の触媒）。進めた場合、要求の80%を割った tick は
     * 「不純」として数え、完成品の adjustResult に純度（不純でなかった割合）を渡す。
     */
    protected boolean runsUnderpowered(MachineRecipe recipe) {
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

    protected void tick(Level level, BlockPos pos, BlockState state) {
        refreshRecipeSnapshot();
        flushReturns();
        if (type.hasCatalystSlot()) {
            // 失活触媒・外した使いかけは出力スロットへ。予備との合流・入れ替え・装填もここで
            magazine.tick(items, type.catalystSlot(), this::returnCatalyst);
        }
        if (level.getGameTime() % 10 == 0 && usesSideConfig()) {
            autoEject(level);
        }
        boolean active = false;
        if (!enabled) {
            usage = 0;
            status = Status.OFF;
        } else if (!canOperate(level)) {
            progress = 0;
            completedDuration = 0;
            usage = 0;
            current = null;
            dirty = true;
            status = Status.NOT_FORMED;
        } else {
            if (type.massMode() && mode > 0) {
                absorbMass(level);
            }
            if (dirty || waitingSettle) {
                dirty = false;
                refreshRecipe(level);
            }
            if (current == null) {
                progress = 0;
                usage = 0;
                status = Status.IDLE;
            } else {
                status = process(level, pos, current.value());
                active = status == Status.RUNNING;
            }
        }
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    private Status process(Level level, BlockPos pos, MachineRecipe recipe) {
        if (batchRemaining > 0) {
            finishBatch(level, recipe, 256);
            return Status.RUNNING;
        }
        if (progress < maxProgress) {
            if (!canOutput(recipe)) {
                timeCarry = 0;
                return Status.OUTPUT_FULL;
            }
            // 時間の場で変わる進行量のうち、蓄電容量で1回に払える分だけ進める。
            timeCarry += TimeFields.speed(level, pos);
            int steps = (int) timeCarry;
            if (steps == 0) return Status.RUNNING;
            int done = Math.min(maxProgress - progress, affordableSteps(steps));
            if (!energy.consume(usage * done) && !(runsUnderpowered(recipe) && drawPartial())) {
                timeCarry = 0;
                return Status.NO_POWER;
            }
            timeCarry -= steps;
            progress += done;
        }
        if (progress >= maxProgress) {
            if (!craft(level, recipe)) {
                // 完成時の純度が違って重ならない場合、進捗と材料を保ち、電力を再請求せずに待つ。
                completedDuration = maxProgress;
                timeCarry = 0;
                return Status.OUTPUT_FULL;
            }
            // Each additional operation pays a complete cycle from the buffer and goes through the
            // same item, fluid, catalyst, byproduct and output checks as the first operation.
            batchRemaining = ServerConfig.MACHINE_BATCH_LIMIT.get() - 1;
            if (batchRemaining > 0) finishBatch(level, recipe, 255);
            progress = 0;
            completedDuration = 0;
            dirty = true;
        }
        return Status.RUNNING;
    }

    private void finishBatch(Level level, MachineRecipe recipe, int tickBudget) {
        long cost = (long) usage * maxProgress;
        if (cost > 0) batchRemaining = (int) Math.min(batchRemaining, energy.getEnergyStored() / cost);
        if (batchRemaining == 0 || bulkItems(recipe, batchRemaining, cost)) {
            batchRemaining = 0;
        } else {
            int count = Math.min(batchRemaining, tickBudget);
            for (int i = 0; i < count; i++) {
                if (!craft(level, recipe)) {
                    batchRemaining = 0;
                    break;
                }
                energy.consume((int) cost);
                batchRemaining--;
            }
        }
        setChanged();
    }

    /** Complete an ordinary item recipe in slot-sized groups, independent of the number of items. */
    private boolean bulkItems(MachineRecipe r, int remaining, long cost) {
        if (getClass() != MachineBlockEntity.class || r.result().isEmpty() || type.outputSlots() != 1
                || r.result().getMaxStackSize() <= 1 || !r.byproduct().isEmpty() || r.restore().isPresent()
                || r.mass().isPresent() || !r.fluidIngredients().isEmpty() || !r.fluidResults().isEmpty()
                || r.ingredients().stream().anyMatch(in -> in.uses() != 0 || type.hasCatalystSlot() && r.assemblyCatalyst(in))) return false;
        var inventory = assemblyGrid == null ? items : assemblyGrid;
        crafting = true;
        try {
            while (remaining > 0) {
                var inputs = currentInputs(r);
                var plan = inputPlan(r, inputs, assemblyGrid != null);
                if (plan == null) break;
                if (type.hasCatalystSlot() && plan.consume()[inputs.size() - 1] != 0) break;
                ItemStack result = previewResult(r, inputs, plan);
                ItemStack stored = items.getStackInSlot(type.outputSlot());
                if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, result)) break;
                int copies = Math.min(remaining, (MachineStock.limit(result, false) - stored.getCount()) / result.getCount());
                for (int i = 0; i < inputs.size(); i++) {
                    if (plan.consume()[i] > 0) copies = Math.min(copies, inputs.get(i).getCount() / plan.consume()[i]);
                }
                if (copies <= 0) break;
                for (int i = 0; i < inputs.size(); i++) {
                    if (plan.consume()[i] == 0) continue;
                    int slot = assemblyGrid == null ? type.recipeSlot(i) : i;
                    ItemStack stack = inventory.getStackInSlot(slot);
                    inventory.setStackInSlot(slot, stack.copyWithCount(stack.getCount() - plan.consume()[i] * copies));
                }
                insertOutput(result.copyWithCount(result.getCount() * copies), false);
                energy.consume((int) (cost * copies));
                remaining -= copies;
            }
        } finally {
            crafting = false;
        }
        return true;
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
    private List<RecipeHolder<MachineRecipe>> stationRecipes(Level level) {
        return stationRecipes(level, type);
    }

    /** {@link #stationRecipes(Level)} の装置の種類ごとの版（画面で待機の理由を調べるときにも使う）。 */
    public static List<RecipeHolder<MachineRecipe>> stationRecipes(Level level, MachineType type) {
        return MachineRecipeIndex.station(level, type);
    }

    /** 質量モードで選べるレシピ（ID順）。 */
    public static List<RecipeHolder<MachineRecipe>> massRecipes(Level level, MachineType type) {
        return MachineRecipeIndex.mass(level, type);
    }

    @Nullable
    private RecipeHolder<MachineRecipe> selectedMassRecipe(Level level) {
        List<RecipeHolder<MachineRecipe>> list = massRecipes(level, type);
        return mode > 0 && mode <= list.size() ? list.get(mode - 1) : null;
    }

    private boolean sizeAllows(MachineRecipe r) {
        return r.minSize() <= 0 || structureSize() >= r.minSize();
    }

    private void refreshRecipe(Level level) {
        waitingSettle = false;
        ResourceLocation previous = current != null ? current.id() : resumeRecipe;
        RecipeHolder<MachineRecipe> found = null;
        boolean manual = assemblyGrid != null;
        List<ItemStack> inputs;
        if (mode > 0) {
            inputs = inputsExceptMassSlot();
            RecipeHolder<MachineRecipe> selected = selectedMassRecipe(level);
            if (selected != null && massAvailable(selected.value()) && selected.value().plan(inputs) != null
                    && fluidsAvailable(selected.value())) {
                found = selected;
            }
        } else {
            hasFree = false;
            inputs = manual ? assemblyInputs() : inputStacks();
            found = findRecipe(level, inputs, manual);
        }
        resumeRecipe = null;
        if (found == null || !found.id().equals(previous)) {
            batchRemaining = 0;
            progress = 0;
            completedDuration = 0;
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
                speed *= ServerConfig.OVERCLOCK_SPEED_MULTIPLIER.get();
            }
            // 上位の触媒で払うレシピは速く進む
            MachineRecipe.Plan plan = inputPlan(found.value(), inputs, manual);
            if (plan != null) {
                speed *= plan.speed();
            }
            maxProgress = completedDuration > 0 ? completedDuration : Math.clamp(Math.round(found.value().time() / speed), 1, Integer.MAX_VALUE);
            if (fixedEnergyPerCraft(found.value())) {
                // 1回あたりの電力（レシピの電力 × 時間）を、速さ・単極子・オーバークロックによらず保つ
                usage = (int) Math.ceil(found.value().energy() * ServerConfig.MACHINE_ENERGY_MULTIPLIER.get()
                        * found.value().time() / (double) maxProgress);
            } else {
                usage = (int) Math.ceil(found.value().energy() * ServerConfig.MACHINE_ENERGY_MULTIPLIER.get()
                        / (hasMonopole() ? 1.5 : 1.0) * (hasOverclock() ? ServerConfig.OVERCLOCK_POWER_MULTIPLIER.get() : 1.0));
            }
        }
    }

    @Nullable
    private RecipeHolder<MachineRecipe> findRecipe(Level level, List<ItemStack> inputs, boolean manual) {
        RecipeHolder<MachineRecipe> found = null, blocked = null;
        for (RecipeHolder<MachineRecipe> holder : stationRecipes(level)) {
            MachineRecipe r = holder.value();
            boolean free = isFree(r);
            hasFree |= free;
            if (found != null || r.isMassRecipe() || !sizeAllows(r) || free && !makeFree || !fluidsAvailable(r)) continue;
            MachineRecipe.Plan plan = inputPlan(r, inputs, manual);
            // The board specifies a complete recipe. The selected product and hopper settling delay are for bulk inputs.
            if (plan == null || !manual && !selectionAllows(level, holder, inputs, plan)) continue;
            if (canOutput(r, inputs, plan)) found = holder;
            else if (blocked == null) blocked = holder;
        }
        return found != null ? found : blocked;
    }

    @Nullable
    private static MachineRecipe.Plan inputPlan(MachineRecipe recipe, List<ItemStack> inputs, boolean manual) {
        return manual ? recipe.assemblyPlan(inputs) : recipe.plan(inputs);
    }

    /**
     * 作るものを選べる装置で、このレシピを作ってよいか（{@link RecipeSelection}）。指定があればそのレシピだけ、
     * なければ入っている物をすべて使い、ほかのレシピの途中になりえないものだけ。
     */
    private boolean selectionAllows(Level level, RecipeHolder<MachineRecipe> holder, List<ItemStack> inputs, MachineRecipe.Plan plan) {
        if (!type.selectsRecipe()) {
            return true;
        }
        if (selectedRecipe != null) {
            return holder.id().equals(selectedRecipe);
        }
        if (!RecipeSelection.usesEveryInput(type, inputs, plan)) {
            return false;
        }
        if (!RecipeSelection.ambiguous(level, type, holder)) {
            return true;
        }
        // ほかのレシピの途中と同じ組み合わせ: 入れるのが SETTLE_TICKS 止まったら、入れ終わったとみなして作る
        if (level.getGameTime() - inputsChangedAt >= RecipeSelection.SETTLE_TICKS) {
            return true;
        }
        waitingSettle = true;
        return false;
    }

    /** 人・ホッパー・アンドロイドが入力の欄を最後に変えた時刻（装置が材料を使った分は含めない）。 */
    private long inputsChangedAt;
    /** 装置が材料を使っている最中か（その変化は inputsChangedAt に数えない）。 */
    private boolean crafting;
    /** 入れ終わるのを待っているレシピがあるか（あれば毎 tick 選び直す）。 */
    private boolean waitingSettle;

    /**
     * アンドロイドが作りたい物 output（材料 inputs）に合うレシピを指定する（作るものを選べる装置だけ）。
     * 材料の一部が別のレシピと同じでも、アンドロイドの決めたレシピ通りの物ができる。
     */
    public void selectFor(net.minecraft.world.item.ItemStack output, List<net.minecraft.world.item.ItemStack> inputs) {
        if (level == null || output.isEmpty()) {
            return;
        }
        if (type.massMode()) {
            int target = 0;
            var mass = massRecipes(level, type);
            for (int i = 0; i < mass.size(); i++) {
                if (ItemStack.isSameItem(mass.get(i).value().result(), output)) {
                    target = i + 1;
                    break;
                }
            }
            if (target != mode) {
                mode = target;
                markDirty();
            }
        }
        if (!type.selectsRecipe()) {
            return;
        }
        RecipeHolder<MachineRecipe> best = null;
        int bestScore = -1;
        for (RecipeHolder<MachineRecipe> h : RecipeSelection.selectable(level, type)) {
            if (!net.minecraft.world.item.ItemStack.isSameItem(h.value().result(), output)) {
                continue;
            }
            int score = 0;
            for (MachineRecipe.ItemInput in : h.value().ingredients()) {
                if (inputs.stream().anyMatch(in.ingredient())) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = h;
                bestScore = score;
            }
        }
        if (best != null && !best.id().equals(selectedRecipe)) {
            selectedRecipe = best.id();
            selectedIndex = -2;
            markDirty();
        }
    }

    /** 作るものを指定する（GUIから）。index は {@link RecipeSelection#selectable} の番号、範囲外なら指定を外す。 */
    public void selectRecipe(int index) {
        if (!type.selectsRecipe() || level == null) {
            return;
        }
        RecipeHolder<MachineRecipe> holder = RecipeSelection.byIndex(level, type, index);
        selectedRecipe = holder == null ? null : holder.id();
        selectedIndex = -2;
        markDirty();
    }

    /** 指定したレシピの {@link RecipeSelection#selectable} での番号（-1 = 指定なし）。同期のたびに探さないよう覚えておく。 */
    private int selectedIndex = -2;
    @Nullable
    private Object recipeSnapshot;

    /** A data pack reload can change both automatic matching and the synchronized recipe order. */
    private void refreshRecipeSnapshot() {
        if (level == null) return;
        Object snapshot = MachineRecipeIndex.snapshot(level);
        if (recipeSnapshot != snapshot) {
            recipeSnapshot = snapshot;
            for (FluidTank tank : tanks) tank.setCapacity(MachineType.tankCapacity());
            refreshEnergyCapacity();
            selectedIndex = -2;
            markDirty();
        }
    }

    private int selectedIndex() {
        refreshRecipeSnapshot();
        if (selectedIndex == -2 && level != null) {
            selectedIndex = RecipeSelection.indexOf(level, type, selectedRecipe);
        }
        return Math.max(-1, selectedIndex);
    }

    /** 設定カードから: 指定を貼る（同じ種類の装置でなければ何もしない）。 */
    public void pasteSelectedRecipe(@Nullable ResourceLocation id) {
        if (type.selectsRecipe()) {
            selectedRecipe = id;
            selectedIndex = -2;
            markDirty();
        }
    }

    @Nullable
    public ResourceLocation selectedRecipe() {
        return selectedRecipe;
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

    @Override public int remainingTicks() {
        return current == null ? -1 : Math.max(1, maxProgress - progress);
    }

    /** 装置の様子の報告（0.21.0。画面のヘッダーと同じ状態と理由）。 */
    @Override
    public MachineReport report() {
        MachineReport.State state;
        Component label;
        List<Component> details = List.of();
        switch (status) {
            case RUNNING -> {
                state = MachineReport.State.RUN;
                label = Component.translatable("gui.singulo.status.running");
            }
            case IDLE -> {
                MachineDiagnosis.Result why = null;
                if (level != null && mode == 0) {
                    List<FluidStack> fluids = new ArrayList<>();
                    for (FluidTank tank : tanks) {
                        fluids.add(tank.getFluid());
                    }
                    boolean board = assemblyGrid != null;
                    why = MachineDiagnosis.idle(level, type, board ? assemblyInputs() : inputStacks(), fluids, structureSize(),
                            !board && type.selectsRecipe() ? RecipeSelection.byIndex(level, type, selectedIndex()) : null);
                }
                if (why == null || why.kind() == MachineDiagnosis.Kind.EMPTY) {
                    state = MachineReport.State.IDLE;
                    label = Component.translatable("gui.singulo.status.idle");
                } else {
                    state = MachineReport.State.WARN;
                    label = Component.translatable("gui.singulo.reason.label." + why.kind().name().toLowerCase());
                    details = why.lines();
                }
            }
            case NO_POWER -> {
                state = MachineReport.State.NO_POWER;
                label = Component.translatable("gui.singulo.status.no_power");
            }
            case OUTPUT_FULL -> {
                state = MachineReport.State.FULL;
                label = Component.translatable("gui.singulo.status.output_full");
            }
            case NOT_FORMED -> {
                state = MachineReport.State.BROKEN;
                label = Component.translatable("gui.singulo.status.not_formed");
            }
            default -> {
                state = MachineReport.State.OFF;
                label = Component.translatable("gui.singulo.status.off");
            }
        }
        ItemStack loaded = magazine.loaded();
        return new MachineReport(state, label, details, progress, maxProgress, energy.getEnergyStored(), energy.getMaxEnergyStored(),
                status == Status.RUNNING && current != null ? usage : 0, 0, loaded.copyWithCount(1), magazine.fill(), List.of(), 0);
    }

    /** レシピの照合に使うスロット（入力 → 触媒）の中身。 */
    public List<ItemStack> inputStacks() {
        List<ItemStack> list = new ArrayList<>(type.recipeSlots());
        for (int i = 0; i < type.recipeSlots(); i++) {
            int slot = type.recipeSlot(i);
            if (slot == type.catalystSlot()) {
                list.add(magazine.pooled(items, slot));              // 触媒は装填中のものと予備の同じ触媒を合わせた残りで照合する
            } else {
                list.add(pendingReturns.get(slot) ? ItemStack.EMPTY : items.getStackInSlot(slot));
            }
        }
        return list;
    }

    private boolean hasAssemblyInputs() {
        if (assemblyGrid == null) return false;
        for (int i = 0; i < assemblyGrid.getSlots(); i++) {
            if (!assemblyReturns.get(i) && !assemblyGrid.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    /** Read-only view of board cells followed by the loaded and reserve catalyst balance. */
    public List<ItemStack> assemblyInputs() {
        List<ItemStack> inputs = new ArrayList<>(assemblyGrid.getSlots() + 1);
        for (int i = 0; i < assemblyGrid.getSlots(); i++) {
            inputs.add(assemblyReturns.get(i) ? ItemStack.EMPTY : assemblyGrid.getStackInSlot(i));
        }
        inputs.add(magazine.pooled(items, type.catalystSlot()));
        return inputs;
    }

    private List<ItemStack> currentInputs(MachineRecipe recipe) {
        if (assemblyGrid != null) return assemblyInputs();
        return recipe.isMassRecipe() ? inputsExceptMassSlot() : inputStacks();
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

    /** Reserve the entire payment before draining anything. Input tanks precede product tanks. */
    private boolean payRecipeFluids(MachineRecipe r, IFluidHandler.FluidAction action) {
        if (r.fluidIngredients().isEmpty()) return true;
        int[] drains = new int[tanks.length];
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            int needed = in.amount();
            for (int i = 0; i < tanks.length && needed > 0; i++) {
                FluidStack stored = tanks[i].getFluid();
                if (stored.isEmpty() || !stored.getFluid().isSame(in.fluid())) continue;
                int take = Math.min(needed, stored.getAmount() - drains[i]);
                drains[i] += take;
                needed -= take;
            }
            if (needed > 0) return false;
        }
        if (action.execute()) {
            for (int i = 0; i < tanks.length; i++) if (drains[i] > 0) tanks[i].drain(drains[i], action);
        }
        return true;
    }

    private boolean fluidsAvailable(MachineRecipe r) {
        return payRecipeFluids(r, IFluidHandler.FluidAction.SIMULATE);
    }

    private void absorbMass(Level level) {
        RecipeHolder<MachineRecipe> selected = selectedMassRecipe(level);
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
                massSources.add(BuiltInRegistries.ITEM.getKey(taken.getItem()));
            }
            markDirty();
        }
    }

    private ItemStack previewResult(MachineRecipe r, List<ItemStack> inputs, MachineRecipe.Plan plan) {
        if (plan.restoreSlot() >= 0) {
            return UsesHelper.restore(inputs.get(plan.restoreSlot()), r.result());
        }
        ItemStack out = r.assemble(inputs, plan);
        markMixed(r, out);
        return out;
    }

    private boolean canOutput(MachineRecipe r) {
        if (r.result().isEmpty() && assemblyGrid == null) return assignFluidOutputs(r.fluidResults(), true);
        List<ItemStack> inputs = currentInputs(r);
        return canOutput(r, inputs, inputPlan(r, inputs, assemblyGrid != null));
    }

    private boolean canOutput(MachineRecipe r, List<ItemStack> inputs, @Nullable MachineRecipe.Plan plan) {
        if (plan == null || !canReturnSpent(plan)) return false;
        if (!r.result().isEmpty()) {
            if (!type.hasOutput()) {
                return false;
            }
            if (!fitsOutputs(previewResult(r, inputs, plan), r.byproduct(), runsUnderpowered(r))) {
                return false;
            }
        }
        return assignFluidOutputs(r.fluidResults(), true);
    }

    /** 成果物と副産物が、両方とも出力スロットに入り切るか（同じスロットを取り合う分も数える）。 */
    private boolean fitsOutputs(ItemStack result, ItemStack byproduct, boolean unknownPurity) {
        ItemStack[] slots = new ItemStack[type.outputSlots()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = items.getStackInSlot(type.outputSlot() + i).copy();
        }
        for (ItemStack want : new ItemStack[]{result, byproduct}) {
            int left = want.getCount();
            for (int i = 0; i < slots.length && left > 0 && !want.isEmpty(); i++) {
                if (slots[i].isEmpty()) {
                    int limit = MachineStock.limit(want, false);
                    slots[i] = want.copyWithCount(Math.min(left, limit));
                    left -= slots[i].getCount();
                } else if (ItemStack.isSameItemSameComponents(slots[i], want)
                        || unknownPurity && want == result && ItemStack.isSameItem(slots[i], result)) {
                    int move = Math.min(left, MachineStock.limit(slots[i], false) - slots[i].getCount());
                    if (move > 0) {
                        slots[i].grow(move);
                        left -= move;
                    }
                }
            }
            if (!want.isEmpty() && left > 0) {
                return false;
            }
        }
        return true;
    }

    /** 出力スロットへ順に入れ、入り切らなかった分を返す。 */
    protected ItemStack insertOutput(ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (int i = 0; i < type.outputSlots() && !rest.isEmpty(); i++) {
            rest = items.insertItem(type.outputSlot() + i, rest, simulate);
        }
        return rest;
    }

    private ItemStack returnCatalyst(ItemStack stack) {
        if (type.spentCatalystSlot() >= 0 && UsesHelper.isSpentCatalyst(stack))
            return items.insertItem(type.spentCatalystSlot(), stack, false);
        return insertOutput(stack, false);
    }

    private boolean canReturnSpent(MachineRecipe.Plan plan) {
        if (assemblyGrid == null) return true;
        ItemStack spent = magazine.spentBy(plan.uses()[MachineRecipe.ASSEMBLY_CELLS], items, type.catalystSlot());
        return spent.isEmpty() || items.insertItem(type.spentCatalystSlot(), spent, true).isEmpty();
    }

    /** 液体の成果物を出力タンクに入れる。同じ液体のタンクを優先し、なければ空のタンクへ。 */
    private boolean assignFluidOutputs(List<FluidStack> results, boolean simulate) {
        boolean[] used = new boolean[type.fluidOutputs()];
        for (FluidStack result : results) {
            int target = -1;
            for (int pass = 0; pass < 2 && target < 0; pass++) {
                for (int i = 0; i < type.fluidOutputs(); i++) {
                    FluidTank tank = tanks[type.fluidInputs() + i];
                    boolean candidate = pass == 0 ? FluidStack.isSameFluidSameComponents(tank.getFluid(), result)
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

    private boolean craft(Level level, MachineRecipe r) {
        crafting = true;
        try {
            return craftNow(level, r);
        } finally {
            crafting = false;
        }
    }

    private boolean craftNow(Level level, MachineRecipe r) {
        List<ItemStack> inputs = currentInputs(r);
        MachineRecipe.Plan plan = inputPlan(r, inputs, assemblyGrid != null);
        if (plan == null || !massAvailable(r)) {
            return false;
        }
        ItemStack out = previewResult(r, inputs, plan);
        if (!out.isEmpty()) {
            out = adjustResult(r, out, 1.0 - Math.min(1.0, underpoweredTicks / (double) Math.max(1, maxProgress)));
        }
        if (!fitsOutputs(out, r.byproduct(), false) || !canReturnSpent(plan) || !assignFluidOutputs(r.fluidResults(), true)
                || !payRecipeFluids(r, IFluidHandler.FluidAction.EXECUTE)) {
            return false;
        }
        underpoweredTicks = 0;
        if (r.isMassRecipe()) {
            massSources.clear();
        }
        ItemStackHandler materialSlots = assemblyGrid != null ? assemblyGrid : items;
        java.util.BitSet returns = assemblyGrid != null ? assemblyReturns : pendingReturns;
        for (int i = 0; i < inputs.size(); i++) {
            if (type.hasCatalystSlot() && i == inputs.size() - 1) {
                int slot = type.catalystSlot();
                if (plan.uses()[i] > 0) {
                    if (magazine.loaded().isEmpty()) {
                        magazine.tick(items, slot, this::returnCatalyst);
                    }
                    magazine.consume(plan.uses()[i], items, slot);
                    if (assemblyGrid != null) magazine.flush(this::returnCatalyst);
                }
                for (int k = 0; k < plan.consume()[i]; k++) {
                    // 触媒そのものを材料として使う: 装填中のものから（なければ予備から）
                    if (magazine.eject().isEmpty()) {
                        items.extractItem(slot, 1, false);
                    }
                }
                continue;
            }
            int slot = assemblyGrid != null ? i : type.recipeSlot(i);
            if (plan.uses()[i] > 0) {
                ItemStack used = UsesHelper.consume(materialSlots.getStackInSlot(slot), plan.uses()[i]);
                materialSlots.setStackInSlot(slot, used);
                if (!used.isEmpty() && UsesHelper.remaining(used) == 0) {
                    returns.set(slot);
                }
            }
            if (plan.consume()[i] > 0) {
                ItemStack stock = materialSlots.getStackInSlot(slot);
                materialSlots.setStackInSlot(slot, stock.copyWithCount(stock.getCount() - plan.consume()[i]));
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
        if (!r.byproduct().isEmpty()) {
            insertOutput(r.byproduct().copy(), false);
        }
        flushReturns();
        assignFluidOutputs(r.fluidResults(), false);
        onCrafted(level, r);
        return true;
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
                && BuiltInRegistries.ITEM.getKey(out.getItem()).equals(io.github.genichimaruo.singulo.Singulo.id("compressed_block_1"))) {
            out.set(SinguloComponents.MIXED_SOURCE.get(), true);
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
        return assemblyPreviewIndex(type) + (type.massMode() ? 4 : type == MachineType.PRECISION_ASSEMBLER ? 1 : 0);
    }

    /** Synced after the existing side settings. Zero is an empty board preview. */
    public static int assemblyPreviewIndex(MachineType type) {
        return sidesIndex(type) + 1 + type.tanks();
    }

    private int assemblyPreview() {
        if (assemblyPreview == -2 && level != null) {
            RecipeHolder<MachineRecipe> match = null;
            if (!dirty) match = current;
            else if (hasAssemblyInputs()) match = findRecipe(level, assemblyInputs(), true);
            assemblyPreview = match == null ? -1 : RecipeSelection.indexOf(level, type, match.id());
        }
        return Math.max(0, assemblyPreview + 1);
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
            case D_MASS -> Math.clamp(Math.round((massTargetIsMetal() ? metalMass : mass) * 100), 0, Integer.MAX_VALUE);
            case D_MASS_TARGET -> Math.clamp(Math.round(massTarget() * 100), 0, Integer.MAX_VALUE);
            case D_STRUCTURE -> structureSize();
            case D_LINING -> liningCount();
            case D_SELECTED -> selectedIndex() + 1;
            case D_LOADED_ITEM -> magazine.loadedItemId();
            case D_LOADED_FILL -> Math.round(magazine.fill() * 1000);
            // JEI の搬入の見積もりがサーバーと同じになるよう、装填中の触媒の残りの使用回数はそのまま送る
            case D_LOADED_USES -> magazine.loaded().isEmpty() ? 0 : io.github.genichimaruo.singulo.item.UsesHelper.remaining(magazine.loaded());
            case D_FLAGS -> (enabled ? FLAG_ENABLED : 0) | (makeFree ? FLAG_MAKE_FREE : 0) | (hasFree ? FLAG_HAS_FREE : 0);
            default -> {
                int tail = index - assemblyPreviewIndex(type);
                if (type.massMode() && tail >= 0) {
                    double value = tail < 2 ? (massTargetIsMetal() ? metalMass : mass) : massTarget();
                    yield SyncedInts.doubleWord(value, tail % 2);
                }
                if (index == assemblyPreviewIndex(type)) yield assemblyPreview();
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

    private int affordableSteps(int steps) {
        return affordableSteps(steps, usage, energy.getMaxEnergyStored());
    }

    /** 時間の場で進みたい steps のうち、蓄電容量で1回に払える分（1 tick 分も払えなければ 1。そのときは電力不足と容量の案内になる）。 */
    public static int affordableSteps(int steps, int usage, int capacity) {
        if (usage <= 0) {
            return steps;
        }
        return (int) Math.max(1, Math.min(steps, (long) capacity / usage));
    }

    private boolean massTargetIsMetal() {
        if (level == null) {
            return false;
        }
        RecipeHolder<MachineRecipe> r = selectedMassRecipe(level);
        return r != null && r.value().mass().map(MachineRecipe.MassInput::metalOnly).orElse(false);
    }

    private double massTarget() {
        if (level == null) {
            return 0;
        }
        RecipeHolder<MachineRecipe> r = selectedMassRecipe(level);
        return r == null ? 0 : r.value().mass().map(MachineRecipe.MassInput::amount).orElse(0.0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        recoverLegacyAssemblyInputs(player);
        return new MachineMenu(containerId, inventory, this);
    }

    public void recoverLegacyAssemblyInputs(Player player) {
        if (legacyAssemblyInputs.isEmpty() || player.level().isClientSide) return;
        var inventory = new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(player.getInventory());
        for (var it = legacyAssemblyInputs.listIterator(); it.hasNext();) {
            ItemStack stack = it.next();
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory, stack, false);
            if (rest.getCount() == stack.getCount()) continue;
            if (rest.isEmpty()) it.remove();
            else it.set(rest);
            setChanged();
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(type.ordinal());
        });
    }

    boolean packStockOnBreak() {
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.getCount() > stack.getMaxStackSize()) return true;
        }
        if (assemblyGrid == null) return false;
        for (int i = 0; i < assemblyGrid.getSlots(); i++) {
            ItemStack stack = assemblyGrid.getStackInSlot(i);
            if (stack.getCount() > 4 * stack.getMaxStackSize()) return true;
        }
        return false;
    }

    public void dropContents(Level level, BlockPos pos) {
        // One movable machine instead of potentially millions of loose item entities.
        if (packStockOnBreak()) {
            if (!level.isClientSide) {
                ItemStack packed = new ItemStack(getBlockState().getBlock());
                net.minecraft.world.item.BlockItem.setBlockEntityData(packed, getType(), saveCustomOnly(level.registryAccess()));
                packed.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
                Block.popResource(level, pos, packed);
            }
            return;
        }
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, pos, items.getStackInSlot(i));
        }
        if (assemblyGrid != null) {
            for (int i = 0; i < assemblyGrid.getSlots(); i++) {
                ItemStack parts = assemblyGrid.getStackInSlot(i).copy();
                while (!parts.isEmpty()) Block.popResource(level, pos, parts.split(parts.getMaxStackSize()));
            }
            for (ItemStack old : legacyAssemblyInputs) Block.popResource(level, pos, old);
        }
        for (ItemStack s : magazine.contents()) {
            Block.popResource(level, pos, s);
        }
        hydrogenLeak(level, pos);
    }

    /** テスト用: 最初の出力タンクの中身を決める。 */
    public void forceOutputTankForTest(net.neoforged.neoforge.fluids.FluidStack stack) {
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
        long hydrogen = 0;
        for (FluidTank tank : tanks) {
            if (tank.getFluid().is(io.github.genichimaruo.singulo.registry.SinguloFluids.get("hydrogen"))) {
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("inputs_changed_at", inputsChangedAt);
        tag.put("items", MachineStock.save(items, registries));
        tag.putIntArray("pending_returns", pendingReturns.stream().toArray());
        if (assemblyGrid != null) {
            tag.put("assembly_grid", assemblyGrid.serializeNBT(registries));
            tag.putIntArray("assembly_returns", assemblyReturns.stream().toArray());
            if (!legacyAssemblyInputs.isEmpty()) {
                ListTag old = new ListTag();
                for (ItemStack stack : legacyAssemblyInputs) old.add(stack.save(registries));
                tag.put("legacy_assembly_inputs", old);
            }
        }
        tag.put("magazine", magazine.save(registries));
        tag.putIntArray("slot_layout", new int[]{type.inputSlots(), type.outputSlots()});
        tag.putInt("item_sides", itemSides.packed());
        tag.putBoolean("eject_master", true);
        tag.putIntArray("tank_sides", tankSidesPacked());
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("make_free", makeFree);
        ListTag tankList = new ListTag();
        for (FluidTank tank : tanks) {
            tankList.add(tank.writeToNBT(registries, new CompoundTag()));
        }
        tag.put("tanks", tankList);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("progress", progress);
        if (batchRemaining > 0) tag.putInt("batch_remaining", batchRemaining);
        if (completedDuration > 0) tag.putInt("completed_duration", completedDuration);
        tag.putInt("underpowered_ticks", underpoweredTicks);
        tag.putInt("mode", mode);
        if (selectedRecipe != null) {
            tag.putString("selected_recipe", selectedRecipe.toString());
        }
        tag.putDouble("mass", mass);
        tag.putDouble("metal_mass", metalMass);
        if (!massSources.isEmpty()) {
            ListTag sources = new ListTag();
            massSources.stream().sorted().forEach(id -> sources.add(StringTag.valueOf(id.toString())));
            tag.put("mass_sources", sources);
        }
        ResourceLocation recipe = current == null ? resumeRecipe : current.id();
        if (recipe != null) {
            tag.putString("recipe", recipe.toString());
        }
    }

    /**
     * 今のスロット i に入れる、保存したデータのスロットの番号（入力 → 出力 → 触媒 → 単極子 の並びを、区切りごとに写す）。
     * 対応するものがなければ -1。
     */
    private int savedSlot(int i, int savedInputs, int savedOutputs) {
        if (i < type.inputSlots()) {
            return i < savedInputs ? i : -1;
        }
        if (i < type.outputSlot() + type.outputSlots()) {
            int k = i - type.outputSlot();
            return k < savedOutputs ? savedInputs + k : -1;
        }
        int tail = i - (type.inputSlots() + type.outputSlots());    // 触媒・単極子は出力の後ろの同じ位置
        return savedInputs + savedOutputs + tail;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        magazine.load(tag.getCompound("magazine"), registries);
        // スロット数が変わった古いデータでも読めるよう、いったん別の入れ物に読んでから写す
        ItemStackHandler loaded = MachineStock.read(tag.getCompound("items"), registries);
        int[] layout = tag.getIntArray("slot_layout");
        if (layout.length != 2) {
            layout = type.legacyLayout();
        }
        for (int i = 0; i < items.getSlots(); i++) {
            int from = savedSlot(i, layout[0], layout[1]);
            items.setStackInSlot(i, from >= 0 && from < loaded.getSlots() ? loaded.getStackInSlot(from) : ItemStack.EMPTY);
        }
        java.util.BitSet savedReturns = new java.util.BitSet();
        for (int slot : tag.getIntArray("pending_returns")) {
            if (slot >= 0 && slot < loaded.getSlots()) savedReturns.set(slot);
        }
        pendingReturns.clear();
        for (int i = 0; i < type.recipeSlots(); i++) {
            int slot = type.recipeSlot(i);
            int from = savedSlot(slot, layout[0], layout[1]);
            if (from >= 0 && savedReturns.get(from) && !items.getStackInSlot(slot).isEmpty()) {
                pendingReturns.set(slot);
            }
        }
        if (assemblyGrid != null) {
            legacyAssemblyInputs.clear();
            for (Tag entry : tag.getList("legacy_assembly_inputs", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) entry);
                if (!stack.isEmpty()) legacyAssemblyInputs.add(stack);
            }
            for (int i = 0; i < Math.min(loaded.getSlots(), layout[0]); i++) {
                ItemStack stack = loaded.getStackInSlot(i);
                if (!stack.isEmpty()) legacyAssemblyInputs.add(stack);
            }
            if (layout[0] > 0 && UsesHelper.isSpentCatalyst(items.getStackInSlot(type.outputSlot()))) {
                ItemStack rest = items.insertItem(type.spentCatalystSlot(), items.getStackInSlot(type.outputSlot()), false);
                items.setStackInSlot(type.outputSlot(), rest);
            }
            assemblyGrid.deserializeNBT(registries, tag.getCompound("assembly_grid"));
            assemblyReturns.clear();
            for (int slot : tag.getIntArray("assembly_returns")) {
                if (slot >= 0 && slot < assemblyGrid.getSlots() && !assemblyGrid.getStackInSlot(slot).isEmpty()) assemblyReturns.set(slot);
            }
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
            tanks[i].readFromNBT(registries, tankList.getCompound(i));
        }
        refreshEnergyCapacity();
        energy.restoreEnergy(tag.getInt("energy"));
        progress = tag.getInt("progress");
        batchRemaining = Math.max(0, tag.getInt("batch_remaining"));
        completedDuration = Math.clamp(tag.getInt("completed_duration"), 0, Math.max(0, progress));
        underpoweredTicks = Math.max(0, tag.getInt("underpowered_ticks"));
        mode = type.massMode() ? tag.getInt("mode") : 0;
        selectedRecipe = type.selectsRecipe() && tag.contains("selected_recipe") ? io.github.genichimaruo.singulo.registry.LegacyIds.recipe(ResourceLocation.tryParse(tag.getString("selected_recipe"))) : null;
        selectedIndex = -2;
        mass = tag.getDouble("mass");
        metalMass = tag.getDouble("metal_mass");
        massSources.clear();
        for (Tag entry : tag.getList("mass_sources", Tag.TAG_STRING)) {
            ResourceLocation source = ResourceLocation.tryParse(entry.getAsString());
            if (source != null) massSources.add(source);
        }
        current = null;
        resumeRecipe = tag.contains("recipe") ? io.github.genichimaruo.singulo.registry.LegacyIds.recipe(ResourceLocation.tryParse(tag.getString("recipe"))) : null;
        if (assemblyGrid != null && layout[0] > 0 && !tag.getBoolean("assembly_active")) {
            // A paid bulk cycle cannot be resumed against different materials in the manual board.
            resumeRecipe = null;
            progress = completedDuration = underpoweredTicks = 0;
        }
        // Loading usually happens before setLevel, so inventory callbacks cannot restore this clock.
        inputsChangedAt = tag.getLong("inputs_changed_at");
        dirty = true;
    }

    // ------------------------------------------------------------------ 自動搬入出

    /** 入力は搬入、完成品とこの装置で使い切った部品は搬出できる。 */
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
            if (assemblyGrid != null) return stack;
            if (slot == type.catalystSlot()) {
                // 予備の新品に重ならない使いかけの触媒は、装填中のものと合わせて受け取る
                ItemStack rest = items.insertItem(slot, stack, simulate);
                return !rest.isEmpty() && magazine.absorb(rest, items, slot, simulate) ? ItemStack.EMPTY : rest;
            }
            return slot < type.inputSlots() ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return (slot >= type.outputSlot() && slot < type.outputSlot() + type.outputSlots()) || slot == type.spentCatalystSlot() || pendingReturns.get(slot)
                    ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return assemblyGrid == null && (slot < type.inputSlots() || slot == type.catalystSlot()) && slotAccepts(slot, stack);
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
    private final class SidedFluids implements IFluidHandler, io.github.genichimaruo.singulo.pipe.PipeFluidFilter {
        private final int inTanks;
        private final int outTanks;

        SidedFluids(int inTanks, int outTanks) {
            this.inTanks = inTanks;
            this.outTanks = outTanks;
        }

        @Override
        public boolean pipeAccepts(FluidStack fluid) {
            return usesFluid(fluid);
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
                if ((outTanks & (1 << i)) != 0 && FluidStack.isSameFluidSameComponents(tanks[i].getFluid(), resource)) {
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
        // The tower also consumes nitrogen from its product tanks. Keep its single feed tank available for lava, water or helium.
        boolean coolant = towerCoolant(resource);
        int first = coolant ? type.fluidInputs() : 0;
        int end = coolant ? tanks.length : type.fluidInputs();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = first; i < end; i++) {
                if ((mask & (1 << i)) == 0) {
                    continue;
                }
                FluidTank tank = tanks[i];
                boolean candidate = pass == 0 ? FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)
                        : tank.isEmpty();
                if (candidate) {
                    return tank.fill(resource, action);
                }
            }
        }
        return 0;
    }

    private boolean towerCoolant(FluidStack resource) {
        return type == MachineType.CRYOGENIC_COOLING_TOWER
                && resource.getFluid() == io.github.genichimaruo.singulo.registry.SinguloFluids.get("liquid_nitrogen");
    }

    /** パイプから入れてよい液体か: すでに入力タンクに入っているか、この装置のレシピが使うもの。 */
    private boolean usesFluid(FluidStack fluid) {
        for (int i = 0; i < type.fluidInputs(); i++) {
            if (FluidStack.isSameFluidSameComponents(tanks[i].getFluid(), fluid)) {
                return true;
            }
        }
        return level != null && (usesFluid(stationRecipes(level), fluid) || usesFluid(massRecipes(level, type), fluid));
    }

    private static boolean usesFluid(List<RecipeHolder<MachineRecipe>> recipes, FluidStack fluid) {
        for (RecipeHolder<MachineRecipe> holder : recipes) {
            for (MachineRecipe.FluidInput in : holder.value().fluidIngredients()) {
                if (in.fluid() == fluid.getFluid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 入力タンクへは入れるだけ、出力タンクからは取り出すだけ。 */
    private final class AutomationFluids implements IFluidHandler, io.github.genichimaruo.singulo.pipe.PipeFluidFilter {
        @Override
        public boolean pipeAccepts(FluidStack fluid) {
            return usesFluid(fluid);
        }

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
            return towerCoolant(stack) ? tank >= type.fluidInputs() && tank < tanks.length : tank < type.fluidInputs();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fillInputs(resource, action, -1);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = type.fluidInputs(); i < tanks.length; i++) {
                if (FluidStack.isSameFluidSameComponents(tanks[i].getFluid(), resource)) {
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
