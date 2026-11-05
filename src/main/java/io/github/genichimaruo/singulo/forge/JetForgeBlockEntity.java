package io.github.genichimaruo.singulo.forge;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.multiblock.FormationEffect;
import io.github.genichimaruo.singulo.multiblock.MultiblockPortBlockEntity;
import io.github.genichimaruo.singulo.multiblock.PortLinks;
import io.github.genichimaruo.singulo.multiblock.ShapeSpec;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 降着ジェット鍛造炉の制御核（形は {@link JetForgeLayout}）。炉心に加工用のブラックホールを作り、
 * レシピ（type: singulo:machine、station: {@value #STATION}）の素材を、両極のジェットを絞ったビームで鍛える。
 * <p>
 * ブラックホールはPリアクターと同じ手順で作る: 特異点の種（シンギュラリティ・コアでもよい）を入れて画面の「点火」を押し、
 * 点火の時間（ignitionWindowSeconds）のうちに {@link #ignitionEnergy()}（既定 25 GFE）を注ぐと、点火の演出のあとに重力崩壊してできる
 * （種は使い切る）。点火の演出と音はPリアクターと同じ長さで、炉心安定化コイルから種へ光が流れ込む。できたブラックホールは
 * まわりを引き寄せ、地平線に触れたものを飲み込む。制御核を壊すと、拘束されたまま野良ブラックホールとして炉心の中心に残り、
 * 同じ場所に組み直すと取り込む。
 * <ol>
 *   <li>予告（{@value #PRIME_TICKS} tick）: 素材を全部取り出してから（完成品の入る所があり、電力が1 tick ぶん溜まっているときだけ）、
 *       警告灯が回り、素材が照射室の中央へ浮かぶ</li>
 *   <li>点火（{@value #IGNITE_TICKS} tick）: 拘束コイルが動き、降着円盤が明るくなって両極からジェットが出る（レシピの電力の1/4）</li>
 *   <li>収束（{@value #FOCUS_TICKS} tick）: 3つの収束リングが順に入り、太いプラズマが白く細いビームになる（1/2）</li>
 *   <li>照射（レシピの時間）: 素材に当て続ける（レシピの電力そのまま）</li>
 *   <li>仕上げ（{@value #FINISH_TICKS} tick）と冷却（{@value #COOL_TICKS} tick）: 閃光と衝撃波のあと、冷えた完成品を出力へ</li>
 * </ol>
 * 電力が足りない tick・形が崩れている間・読み込まれていない部分がある間・ブラックホールがない間は、進まずに待つ（素材も進み具合も失わない）。
 * 完成品の入る所がなくなっていれば、冷却の終わりで待つ。形の判定は毎 tick {@value #VERIFY_PER_TICK} か所ずつ確かめる
 * （全体を一度に読まない）。保存するのは段階・進み具合・素材・完成品・電力で、読み込み直すと続きから進む。
 */
public class JetForgeBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        MultiblockPortBlockEntity.Outputs, DeviceMenu.Host {
    public static final String STATION = "accretion_jet_forge";
    public static final int IDLE = 0, PRIME = 1, IGNITE = 2, FOCUS = 3, IRRADIATE = 4, FINISH = 5, COOL = 6;
    public static final int PRIME_TICKS = 60, IGNITE_TICKS = 80, FOCUS_TICKS = 90, FINISH_TICKS = 40, COOL_TICKS = 100;
    /** 待っている理由（画面と描画）。 */
    public static final int OK = 0, NO_POWER = 1, BROKEN = 2, OUTPUT_FULL = 3, NO_CORE = 4, UNLOADED = 5, HELD = 6, IGNITING = 7;
    /** 炉心のブラックホール: ない・点火中・ある。 */
    public static final int BH_NONE = 0, BH_IGNITING = 1, BH_ACTIVE = 2;
    /** 点火の演出の長さ（Pリアクターと同じ。点火の音が鳴り終わる瞬間にできる）。 */
    public static final int IGNITION_SEQUENCE_TICKS = io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.IGNITION_SEQUENCE_TICKS;
    /** 炉心のブラックホールの質量（Pリアクターの点火直後と同じ）。野良として残っても、この質量で引く。 */
    public static final double CORE_MASS = io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.START_MASS;
    /** 事象の地平線の半径（ブロック。描画の影の半径 0.85 の 1/2.598）。 */
    public static final double HORIZON = 0.33;
    /** 逃げられないほど強く引く内側の半径（拘束コイルの内側）と、引く強さの倍率。 */
    public static final double PULL_INNER = 4.5, PULL_SCALE = 0.6;
    /** 引力の届く半径（質量500の小さなブラックホールなので、リアクターの引力帯 blackHolePullRadius より狭い。施設のまわりだけ）。 */
    public static final double PULL_RADIUS = 10;
    /** ビームのダメージ（10 tick ごと、防具を無視）。照射中と、点火・収束のあいだ。 */
    public static final float BEAM_DAMAGE = 40, JET_DAMAGE = 16;
    /** 種・材料3つ・完成品・空になった液体の容器（材料の枠に入れたカートリッジやキャニスターを注いだあと）。 */
    public static final int SLOT_CORE = 0, SLOT_IN = 1, INPUTS = 3, SLOT_OUT = SLOT_IN + INPUTS, SLOT_EMPTY = SLOT_OUT + 1,
            SLOTS = SLOT_EMPTY + 1;
    /** 材料の枠の液体の容器を、冷媒のタンクへ注ぐ間隔（tick）。 */
    static final int DRAIN_INTERVAL = 5;
    public static final int TANK_CAPACITY = 16_000;
    /** 形の判定で1 tick に確かめる位置の数（全体は約1,900か所なので、8 tick で一巡する）。 */
    static final int VERIFY_PER_TICK = 256;
    /** 未形成のときに、形ができたかを確かめる間隔。 */
    static final int FIND_INTERVAL = 40;
    /** 待機中に、始められるレシピを探す間隔。 */
    static final int START_INTERVAL = 10;
    static final int SYNC_INTERVAL = 20;
    /** 既定の1 tick の受電の上限（設定 jetForgeMaxInput）。 */
    public static final long DEFAULT_MAX_INPUT = 1_000_000_000L;

    public static long maxInput() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.JET_FORGE_MAX_INPUT.get() : DEFAULT_MAX_INPUT;
    }

    /** 点火に注ぐ電力（設定 jetForgeIgnitionEnergy、既定 25 GFE）。 */
    public static long ignitionEnergy() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.JET_FORGE_IGNITION_ENERGY.get() : 25_000_000_000L;
    }

    static int ignitionWindowTicks() {
        return (ServerConfig.SPEC.isLoaded() ? ServerConfig.IGNITION_WINDOW_SECONDS.get() : 10) * 20;
    }

    // ------------------------------------------------------------------ 状態

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_CORE) {
                return isCore(stack);
            }
            return slot >= SLOT_IN && slot < SLOT_OUT && (isIngredient(stack) || holdsCoolant(stack));
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_CORE ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == SLOT_CORE) {
                markSync();
            }
        }
    };
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, s -> isCoolant(s)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final Energy energy = new Energy();
    /** 受けた電力の蓄え（FE）。 */
    private long buffer;
    private long receivedThisTick;
    private long receivedTick = -1;
    /** 直近の受電の速さ（FE/t、ならした値。画面に出す）。 */
    private double intake;
    /** これまでに使った電力の合計（FE）。 */
    private long consumedTotal;

    private int phase = IDLE;
    private int phaseTicks;
    private int hold = OK;
    /** 加工中の素材（見せるための1個）と、できあがる物。 */
    private ItemStack working = ItemStack.EMPTY;
    private ItemStack result = ItemStack.EMPTY;
    /** 取り出した素材（仕上げの前に制御核が壊されたら返す）。 */
    private final List<ItemStack> charged = new ArrayList<>();
    private int energyPerTick;
    private int irradiateTicks = 1200;
    /** Locked when a run begins; upgrade changes cannot alter reserved output. */
    private int batchSize = 1;
    private boolean parallelUpgrade;

    public int batchSize() { return batchSize; }
    public boolean parallelUpgrade() { return parallelUpgrade; }
    @Nullable
    private ResourceLocation recipeId;

    // 炉心のブラックホール
    private int bh = BH_NONE;
    private double bhMass = CORE_MASS;
    private long ignitionStored;
    private int ignitionLeft;
    private int ignitionElapsed;
    private boolean ignitionCharged;

    // 形
    private boolean formed;
    @Nullable
    private Direction back;
    private final PortLinks ports = new PortLinks();
    private int verifyCursor;
    private final List<BlockPos> sweepPorts = new ArrayList<>();
    private boolean unloaded;
    private long nextFind;
    private long nextStart;
    private boolean firstCheck;
    private boolean dirtySync;
    private int syncTimer;

    // クライアント
    private int clientTicks;

    public JetForgeBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.JET_FORGE.get(), pos, state);
    }

    // ------------------------------------------------------------------ 読み取り（描画・画面・テスト）

    public int phase() {
        return phase;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int hold() {
        return hold;
    }

    public boolean formed() {
        return formed;
    }

    @Nullable
    public Direction back() {
        return back;
    }

    /** 炉心にブラックホールがあるか（加工できるか）。 */
    public boolean hasCore() {
        return bh == BH_ACTIVE;
    }

    /** 点火に使うシンギュラリティ・コアが入っているか。 */
    public boolean hasSeed() {
        return isCore(items.getStackInSlot(SLOT_CORE));
    }

    public int blackHole() {
        return bh;
    }

    public double blackHoleMass() {
        return bhMass;
    }

    public int ignitionElapsed() {
        return ignitionElapsed;
    }

    public boolean ignitionCharged() {
        return ignitionCharged;
    }

    public long ignitionStored() {
        return ignitionStored;
    }

    /** 点火の演出の進み（0〜1）。 */
    public float ignitionProgress() {
        return Math.min(1F, ignitionElapsed / (float) IGNITION_SEQUENCE_TICKS);
    }

    public ItemStack working() {
        return working;
    }

    public ItemStack result() {
        return result;
    }

    public int irradiateTicks() {
        return irradiateTicks;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public long buffer() {
        return buffer;
    }

    public long consumedTotal() {
        return consumedTotal;
    }

    public double intake() {
        return intake;
    }

    public List<ItemStack> charged() {
        return charged;
    }

    public ItemStackHandler items() {
        return items;
    }

    public FluidTank tank() {
        return tank;
    }

    public int clientTicks() {
        return clientTicks;
    }

    /** 段階の長さ（tick）。 */
    public int duration(int p) {
        return switch (p) {
            case PRIME -> PRIME_TICKS;
            case IGNITE -> IGNITE_TICKS;
            case FOCUS -> FOCUS_TICKS;
            case IRRADIATE -> irradiateTicks;
            case FINISH -> FINISH_TICKS;
            case COOL -> COOL_TICKS;
            default -> 0;
        };
    }

    /** 1回の加工の長さ（tick）。 */
    public int totalTicks() {
        return PRIME_TICKS + IGNITE_TICKS + FOCUS_TICKS + irradiateTicks + FINISH_TICKS + COOL_TICKS;
    }

    /** 始めてからの経過（tick）。 */
    public int elapsed() {
        int t = phaseTicks;
        for (int p = PRIME; p < phase; p++) {
            t += duration(p);
        }
        return phase == IDLE ? 0 : t;
    }

    /** 段階 p の1 tick に使う電力（FE）。 */
    public long demand(int p) {
        return switch (p) {
            case IGNITE -> energyPerTick / 4;
            case FOCUS -> energyPerTick / 2;
            case IRRADIATE -> energyPerTick;
            default -> 0;
        };
    }

    /** 1回の加工に使う電力の合計（FE）。 */
    public long energyPerRun() {
        return demand(IGNITE) * IGNITE_TICKS + demand(FOCUS) * FOCUS_TICKS + demand(IRRADIATE) * (long) irradiateTicks;
    }

    @Nullable
    public Vec3 core() {
        return back == null ? null : JetForgeLayout.core(worldPosition, back);
    }

    public LongEnergyStorage energy() {
        return energy;
    }

    public IFluidHandler coolant() {
        return coolantHandler;
    }

    public IItemHandler automationItems() {
        return automation;
    }

    @Override
    public IItemHandler ejectItems() {
        return output;
    }

    // ------------------------------------------------------------------ 素材の判定

    /** 点火に使う種（Pリアクターと同じく、特異点の種かシンギュラリティ・コア）。 */
    public static boolean isCore(ItemStack stack) {
        return stack.is(BuiltInRegistries.ITEM.get(Singulo.id("singularity_seed"))) || stack.is(BuiltInRegistries.ITEM.get(Singulo.id("singularity_core")));
    }

    private List<RecipeHolder<MachineRecipe>> recipes() {
        if (level == null) {
            return List.of();
        }
        List<RecipeHolder<MachineRecipe>> out = new ArrayList<>();
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            if (STATION.equals(h.value().station())) {
                out.add(h);
            }
        }
        return out;
    }

    /** どれかの鍛造のレシピの材料か。 */
    boolean isIngredient(ItemStack stack) {
        for (RecipeHolder<MachineRecipe> h : recipes()) {
            for (MachineRecipe.ItemInput in : h.value().ingredients()) {
                if (in.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 冷媒（液体ヘリウムなど）の入った液体の容器か（液体ヘリウム・カートリッジ・液体キャニスター・他MODの容器）。 */
    boolean holdsCoolant(ItemStack stack) {
        var handler = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack.copyWithCount(1)).orElse(null);
        if (handler == null) {
            return false;
        }
        for (int t = 0; t < handler.getTanks(); t++) {
            FluidStack f = handler.getFluidInTank(t);
            if (!f.isEmpty() && isCoolant(f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 材料の枠の容器を1個ずつ冷媒のタンクへ注ぎ、空になった容器を空の容器の枠へ移す。中身が全部タンクに入り、空の容器が枠に
     * 収まるときだけ注ぐ（半端に注いで中身や容器を失わない）。
     */
    private void drainContainers() {
        for (int i = SLOT_IN; i < SLOT_OUT; i++) {
            ItemStack in = items.getStackInSlot(i);
            if (in.isEmpty() || isIngredient(in) || !holdsCoolant(in)) {
                continue;
            }
            var handler = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(in.copyWithCount(1)).orElse(null);
            if (handler == null) {
                continue;
            }
            FluidStack inside = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (inside.isEmpty() || tank.fill(inside, IFluidHandler.FluidAction.SIMULATE) < inside.getAmount()) {
                continue;
            }
            FluidStack taken = handler.drain(inside.getAmount(), IFluidHandler.FluidAction.EXECUTE);
            ItemStack emptied = handler.getContainer();
            if (taken.getAmount() != inside.getAmount()
                    || !emptied.isEmpty() && !fitsEmpty(emptied)) {
                continue;                                    // 空の容器の置き場がない（試しただけなので、何も変わっていない）
            }
            tank.fill(taken, IFluidHandler.FluidAction.EXECUTE);
            items.extractItem(i, 1, false);
            if (!emptied.isEmpty()) {
                ItemStack held = items.getStackInSlot(SLOT_EMPTY);
                if (held.isEmpty()) {
                    items.setStackInSlot(SLOT_EMPTY, emptied.copy());
                } else {
                    held.grow(emptied.getCount());
                    items.setStackInSlot(SLOT_EMPTY, held);
                }
            }
            return;                                          // 1回に1個
        }
    }

    /** 空の容器の枠に stack が入るか。 */
    private boolean fitsEmpty(ItemStack stack) {
        ItemStack held = items.getStackInSlot(SLOT_EMPTY);
        return held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack)
                && held.getCount() + stack.getCount() <= Math.min(held.getMaxStackSize(), items.getSlotLimit(SLOT_EMPTY));
    }

    /** どれかの鍛造のレシピの冷媒か。 */
    boolean isCoolant(FluidStack stack) {
        for (RecipeHolder<MachineRecipe> h : recipes()) {
            for (MachineRecipe.FluidInput in : h.value().fluidIngredients()) {
                if (stack.getFluid().isSame(in.fluid())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, JetForgeBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    /** 描き方の時計と、同期を待たない段階の進み（サーバーと同じく、待っている間は進めない）。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, JetForgeBlockEntity be) {
        be.clientTicks++;
        if (be.bh == BH_IGNITING && be.hold == IGNITING) {
            be.ignitionElapsed++;
        }
        if (be.phase != IDLE && be.hold == OK && be.phaseTicks < be.duration(be.phase)) {
            be.phaseTicks++;
        }
    }

    private void tick(ServerLevel level) {
        updateShape(level);
        if ((level.getGameTime() + worldPosition.asLong()) % DRAIN_INTERVAL == 0) {
            drainContainers();
        }
        intake = intake * 0.9 + (receivedTick == level.getGameTime() - 1 ? receivedThisTick : 0) * 0.1;
        int before = hold;
        if (!formed) {
            hold = unloaded ? UNLOADED : BROKEN;
        } else if (unloaded) {
            hold = UNLOADED;
        } else if (bh == BH_IGNITING) {
            hold = IGNITING;
            tickIgnition(level);
        } else if (!hasCore()) {
            hold = NO_CORE;
        } else if (phase == IDLE) {
            hold = level.hasNeighborSignal(worldPosition) ? HELD : OK;
            if (hold == OK && level.getGameTime() >= nextStart) {
                nextStart = level.getGameTime() + START_INTERVAL;
                tryStart(level);
            }
        } else {
            advance(level);
        }
        if (hold != before) {
            markSync();
        }
        if (phase != IDLE && level.getGameTime() % 10 == 0) {
            hazards(level);
        }
        if (bh == BH_ACTIVE) {
            gravity(level);
        }
        if (bh == BH_IGNITING && !formed) {
            abortIgnition(level, "gui.singulo.jet_forge.ignition_broken");
        }
        if (phase != IDLE || bh == BH_IGNITING) {
            setChanged(); // Progress must survive a save/unload between synchronization packets.
        }
        if (dirtySync || (phase != IDLE || bh == BH_IGNITING) && ++syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            dirtySync = false;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        boolean lit = phase != IDLE;
        BlockState state = getBlockState();
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    private void markSync() {
        dirtySync = true;
    }

    // ------------------------------------------------------------------ 形（何回かに分けて確かめる）

    private void updateShape(ServerLevel level) {
        ShapeSpec spec = JetForgeLayout.SPEC;
        Block block = getBlockState().getBlock();
        if (!formed) {
            if (level.getGameTime() < nextFind) {
                return;
            }
            nextFind = level.getGameTime() + FIND_INTERVAL;
            boolean anyUnloaded = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                // A live black hole stays at its saved location when the frame is rebuilt.
                if (bh == BH_ACTIVE && back != null && d != back) {
                    continue;
                }
                List<BlockPos> found = new ArrayList<>();
                Boolean ok = spec.matchesCells(level, worldPosition, d, block, 0, spec.cellCount(), found);
                if (ok == Boolean.TRUE && !centerClear(level, d)) {
                    ok = false;
                }
                if (ok == null) {
                    anyUnloaded = true;
                } else if (ok) {
                    formed = true;
                    back = d;
                    unloaded = false;
                    verifyCursor = 0;
                    sweepPorts.clear();
                    ports.update(level, worldPosition, found);
                    absorbRemnant(level);
                    FormationEffect.onChange(level, worldPosition, false, true, firstCheck, 24, 1, 16);
                    if (!firstCheck) {
                        SinguloSounds.playAt(level, worldPosition, "jet_forge.formed", 2.0F, 1.0F);
                    }
                    firstCheck = false;
                    markSync();
                    return;
                }
            }
            unloaded = anyUnloaded;
            firstCheck = false;
            return;
        }
        // Test every intersecting chunk each tick, independently of the cell sweep.
        // Otherwise an unloaded cell late in the sweep permits several ticks of processing
        // each time the cursor restarts at zero. This never requests chunk loading.
        if (!shapeChunksLoaded(level)) {
            unloaded = true;
            sweepPorts.clear();
            verifyCursor = 0;
            return;
        }
        int to = verifyCursor + VERIFY_PER_TICK;
        Boolean ok = spec.matchesCells(level, worldPosition, back, block, verifyCursor, to, sweepPorts);
        if (ok == null) {
            unloaded = true;
            sweepPorts.clear();
            verifyCursor = 0;
            return;
        }
        unloaded = false;
        if (ok && verifyCursor == 0 && !centerClear(level, back)) {
            ok = false;
        }
        if (!ok) {
            formed = false;
            sweepPorts.clear();
            verifyCursor = 0;
            ports.update(level, worldPosition, List.of());
            nextFind = level.getGameTime() + FIND_INTERVAL;
            markSync();
            return;
        }
        verifyCursor = to;
        if (verifyCursor >= spec.cellCount()) {
            verifyCursor = 0;
            // 基礎とポートの入れ替え（どちらでも形は保たれる）をつなぎ直す
            ports.update(level, worldPosition, List.copyOf(sweepPorts));
            sweepPorts.clear();
        }
    }

    /** Chunk availability is independent of the incremental block-validation cursor. */
    protected boolean shapeChunksLoaded(ServerLevel level) {
        return JetForgeLayout.SPEC.chunksLoaded(level, worldPosition, back);
    }

    /** 炉心の中心が空気か野良ブラックホールか（ほかのブロックが詰まっていたら形ができていない）。 */
    private boolean centerClear(Level level, Direction d) {
        BlockPos center = BlockPos.containing(JetForgeLayout.core(worldPosition, d));
        if (!level.isLoaded(center)) {
            return false;
        }
        BlockState s = level.getBlockState(center);
        return s.isAir() || s.is(io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get());
    }

    /** 炉心の中心に残っていた野良ブラックホールを取り込む（制御核を壊したあとに組み直したとき）。 */
    private void absorbRemnant(ServerLevel level) {
        BlockPos c = BlockPos.containing(JetForgeLayout.core(worldPosition, back));
        if (!(level.getBlockEntity(c) instanceof io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity rogue)) {
            return;
        }
        double m = rogue.mass();
        level.removeBlock(c, false);
        if (bh == BH_IGNITING) {
            bh = BH_NONE;
        }
        bhMass = bh == BH_ACTIVE ? bhMass + m : m;
        bh = BH_ACTIVE;
        level.playSound(null, c, SinguloSounds.get("black_hole.open"), net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
        tellNear(level, "gui.singulo.jet_forge.absorbed");
        markSync();
        setChanged();
    }

    /** テスト用: 次の tick で形をすぐ確かめ直す。 */
    public void recheckNow() {
        nextFind = 0;
        if (formed) {
            verifyCursor = 0;
            sweepPorts.clear();
        }
    }

    // ------------------------------------------------------------------ 点火（Pリアクターと同じ手順）

    /** 画面の「点火」。形ができていて、ブラックホールがなく、コアが入っていれば始める。 */
    public boolean ignite() {
        if (!formed || bh != BH_NONE || !hasSeed() || level == null || level.isClientSide) {
            return false;
        }
        bh = BH_IGNITING;
        ignitionStored = 0;
        ignitionElapsed = 0;
        ignitionCharged = false;
        ignitionLeft = ignitionWindowTicks();
        markSync();
        setChanged();
        return true;
    }

    private void tickIgnition(ServerLevel level) {
        ignitionElapsed++;
        if (ignitionElapsed == 1) {
            Vec3 c = core();
            // 点火の音（Pリアクターと同じ16秒。鳴り終わる瞬間にブラックホールができる。高さを変えると長さがずれるので 1.0）
            level.playSound(null, c.x, c.y, c.z, SinguloSounds.get("penrose_reactor_ignition"), net.minecraft.sounds.SoundSource.BLOCKS,
                    3.0F, 1.0F);
        }
        if (!ignitionCharged && ignitionStored >= ignitionEnergy()) {
            ignitionCharged = true;
            markSync();
        }
        if (ignitionCharged && ignitionElapsed >= IGNITION_SEQUENCE_TICKS) {
            items.extractItem(SLOT_CORE, 1, false);
            bh = BH_ACTIVE;
            bhMass = CORE_MASS;
            ignitionStored = 0;
            Vec3 c = core();
            level.playSound(null, c.x, c.y, c.z, SinguloSounds.get("black_hole.open"), net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, worldPosition, 64, "jet_forge_ignite");
            tellNear(level, "gui.singulo.jet_forge.ignited");
            markSync();
            setChanged();
            return;
        }
        if (!ignitionCharged && --ignitionLeft <= 0) {
            abortIgnition(level, "gui.singulo.jet_forge.ignition_failed");
        }
    }

    /** 点火をやめる（時間切れ・形が崩れた・制御核が壊された）。注いだ電力は失われ、コアは残る。鳴り始めた音も止める。 */
    private void abortIgnition(ServerLevel level, @Nullable String message) {
        bh = BH_NONE;
        ignitionStored = 0;
        ignitionElapsed = 0;
        ignitionCharged = false;
        var id = Singulo.id("penrose_reactor_ignition");
        Vec3 c = back == null ? Vec3.atCenterOf(worldPosition) : core();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 96 * 96) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(id, net.minecraft.sounds.SoundSource.BLOCKS));
            }
        }
        if (message != null) {
            tellNear(level, message);
        }
        markSync();
        setChanged();
    }

    private void tellNear(ServerLevel level, String key) {
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(key)
                .withStyle(net.minecraft.ChatFormatting.GOLD);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(48))) {
            p.displayClientMessage(msg, false);
        }
    }

    // ------------------------------------------------------------------ 引力と事象の地平線

    /**
     * 炉心のブラックホールの引力（Pリアクターの野良ブラックホールと同じ式。プレイヤーはクライアントで引く）。
     * 地平線に触れたものは飲み込む（どんな守りも効かない）。形が崩れていても、ブラックホールがある限り引く。
     */
    private void gravity(ServerLevel level) {
        if (back == null) {
            return;
        }
        Vec3 c = core();
        boolean kill = !ServerConfig.SPEC.isLoaded() || ServerConfig.EVENT_HORIZON_KILL.get();
        for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new AABB(c, c).inflate(PULL_RADIUS), io.github.genichimaruo.singulo.reactor.EventHorizonTargets::affected)) {
            if (e.getBoundingBox().getCenter().distanceTo(c) > PULL_RADIUS) {
                continue;
            }
            if (kill && e.getBoundingBox().inflate(HORIZON).contains(c)) {
                io.github.genichimaruo.singulo.reactor.EventHorizon.consume(level, e);
                continue;
            }
            if (e instanceof net.minecraft.world.entity.player.Player) {
                continue;
            }
            Vec3 v = io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.pulledVelocity(e.getDeltaMovement(),
                    e.getBoundingBox().getCenter(), c, PULL_INNER, PULL_SCALE);
            if (v != null) {
                e.setDeltaMovement(v);
                e.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ 加工

    /** 材料をそろえられるか確かめ、そろえば全部取り出して予告を始める（完成品の入る所と、1 tick ぶんの電力があるときだけ）。 */
    private void tryStart(ServerLevel level) {
        for (RecipeHolder<MachineRecipe> h : recipes()) {
            MachineRecipe r = h.value();
            int batch = parallelUpgrade ? 4 : 1;
            int[] take = null;
            ItemStack product = ItemStack.EMPTY;
            for (; batch >= 1; batch--) {
                long demand = (long) r.energy() * batch;
                long count = (long) r.result().getCount() * batch;
                if (demand < 0 || demand > Integer.MAX_VALUE || count < 1 || count > r.result().getMaxStackSize()) continue;
                take = plan(r, batch);
                product = r.result().copyWithCount((int) count);
                if (take != null && fluidsReady(r, batch) && fits(product) && buffer >= demand) break;
            }
            if (batch < 1) {
                continue;
            }
            charged.clear();
            for (int i = 0; i < INPUTS; i++) {
                if (take[i] > 0) {
                    charged.add(items.extractItem(SLOT_IN + i, take[i], false));
                }
            }
            for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
                tank.drain(Math.multiplyExact(in.amount(), batch), IFluidHandler.FluidAction.EXECUTE);
            }
            ItemStack first = ItemStack.EMPTY;
            if (!r.ingredients().isEmpty()) {
                for (ItemStack s : charged) {
                    if (r.ingredients().get(0).ingredient().test(s)) {
                        first = s.copyWithCount(1);
                        break;
                    }
                }
            }
            working = first;
            result = product;
            batchSize = batch;
            energyPerTick = Math.multiplyExact(r.energy(), batch);
            irradiateTicks = Math.max(1, r.time());
            recipeId = h.id();
            phase = PRIME;
            phaseTicks = 0;
            hold = OK;
            SinguloSounds.playAt(level, worldPosition, "jet_forge.warning", 2.5F, 1.0F);
            markSync();
            return;
        }
    }

    /** 入力の枠から、レシピの材料を取る個数（枠ごと）。そろわなければ null。 */
    @Nullable
    private int[] plan(MachineRecipe r, int batch) {
        int[] left = new int[INPUTS];
        int[] take = new int[INPUTS];
        for (int i = 0; i < INPUTS; i++) {
            left[i] = items.getStackInSlot(SLOT_IN + i).getCount();
        }
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            long wanted = (long) in.count() * batch;
            if (wanted < 0 || wanted > INPUTS * 64) return null;
            int need = (int) wanted;
            for (int i = 0; i < INPUTS && need > 0; i++) {
                ItemStack s = items.getStackInSlot(SLOT_IN + i);
                if (left[i] > 0 && in.ingredient().test(s)) {
                    int n = Math.min(need, left[i]);
                    left[i] -= n;
                    take[i] += n;
                    need -= n;
                }
            }
            if (need > 0) {
                return null;
            }
        }
        return take;
    }

    private boolean fluidsReady(MachineRecipe r, int batch) {
        long total = 0;
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            if (!in.matches(tank.getFluid())) {
                return false;
            }
            total += (long) in.amount() * batch;
        }
        return total <= tank.getFluidAmount();
    }

    /** 完成品が出力の枠に入るか。 */
    private boolean fits(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack out = items.getStackInSlot(SLOT_OUT);
        return stack.getCount() <= Math.min(stack.getMaxStackSize(), items.getSlotLimit(SLOT_OUT))
                && (out.isEmpty() || ItemStack.isSameItemSameComponents(out, stack)
                && out.getCount() + stack.getCount() <= Math.min(out.getMaxStackSize(), items.getSlotLimit(SLOT_OUT)));
    }

    private void advance(ServerLevel level) {
        int length = duration(phase);
        if (phaseTicks >= length) {
            // 冷却の終わり: 完成品を出す（入らなければ待つ）
            if (phase == COOL) {
                if (!fits(result)) {
                    hold = OUTPUT_FULL;
                    return;
                }
                complete(level);
            } else {
                enter(level, phase + 1);
            }
            return;
        }
        long need = demand(phase);
        if (need > 0) {
            if (buffer < need) {
                hold = NO_POWER;
                return;
            }
            buffer -= need;
            consumedTotal += need;
        }
        hold = OK;
        phaseTicks++;
        events(level);
        if (phaseTicks >= length && phase != COOL) {
            enter(level, phase + 1);
        } else if (phaseTicks >= length) {
            if (fits(result)) {
                complete(level);
            } else {
                hold = OUTPUT_FULL;
            }
        }
    }

    private void enter(ServerLevel level, int next) {
        phase = next;
        phaseTicks = 0;
        switch (next) {
            case IGNITE -> SinguloSounds.playAt(level, worldPosition, "jet_forge.spool", 3.0F, 1.0F);
            case IRRADIATE -> SinguloSounds.playAt(level, worldPosition, "jet_forge.beam_start", 3.5F, 1.0F);
            case FINISH -> {
                SinguloSounds.playAt(level, worldPosition, "jet_forge.finish", 6.0F, 1.0F);
                shockwave(level);
            }
            case COOL -> SinguloSounds.playAt(level, worldPosition, "jet_forge.cool", 2.0F, 1.0F);
            default -> { }
        }
        markSync();
    }

    /** 段階の途中の合図の音。 */
    private void events(ServerLevel level) {
        if (phase == PRIME && phaseTicks % 20 == 0 && phaseTicks < PRIME_TICKS) {
            SinguloSounds.playAt(level, worldPosition, "jet_forge.warning", 2.5F, 1.0F);
        }
        if (phase == FOCUS && (phaseTicks == 1 || phaseTicks == FOCUS_TICKS / 3 || phaseTicks == FOCUS_TICKS * 2 / 3)) {
            SinguloSounds.playAt(level, worldPosition, "jet_forge.focus", 2.5F, 0.85F + 0.15F * (phaseTicks * 3 / FOCUS_TICKS));
        }
        if (phase == IRRADIATE && phaseTicks % 40 == 0) {
            SinguloSounds.playAt(level, worldPosition, "jet_forge.pulse", 2.0F, 0.9F + 0.2F * phaseTicks / irradiateTicks);
        }
    }

    private void complete(ServerLevel level) {
        if (!result.isEmpty()) {
            ItemStack out = items.getStackInSlot(SLOT_OUT);
            if (out.isEmpty()) {
                items.setStackInSlot(SLOT_OUT, result.copy());
            } else {
                out.grow(result.getCount());
                items.setStackInSlot(SLOT_OUT, out);
            }
        }
        SinguloSounds.playAt(level, worldPosition, "jet_forge.collect", 1.5F, 1.0F);
        working = ItemStack.EMPTY;
        result = ItemStack.EMPTY;
        charged.clear();
        recipeId = null;
        phase = IDLE;
        phaseTicks = 0;
        hold = OK;
        nextStart = level.getGameTime() + START_INTERVAL;
        markSync();
        setChanged();
    }

    // ------------------------------------------------------------------ 危険（ビームの通り道・仕上げの衝撃波）

    /** ビームの通り道の太さ（軸からの距離、ブロック）。 */
    static final double BEAM_RADIUS = 0.8;

    private void hazards(ServerLevel level) {
        if (back == null || hold != OK || phase < IGNITE || phase > IRRADIATE) {
            return;
        }
        Vec3 from = JetForgeLayout.at(worldPosition, back, 1, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        Vec3 to = JetForgeLayout.at(worldPosition, back, JetForgeLayout.STOP_X, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        Vec3 dir = axis.scale(1 / len);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5), LivingEntity::isAlive)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double s = c.subtract(from).dot(dir);
            if (s < 0 || s > len) {
                continue;
            }
            Vec3 off = c.subtract(from.add(dir.scale(s)));
            if (off.length() > BEAM_RADIUS + e.getBbWidth() / 2) {
                continue;
            }
            // 通り道の外へ押し出し、照射中は焼く（素材の位置は照射室の中なので、人がそこへ入らなければ当たらない）
            Vec3 push = off.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : off.normalize();
            e.push(push.x * 0.6, 0.25, push.z * 0.6);
            e.hurtMarked = true;
            // 通り道で浴びると大けが（防具を無視）。照射中のビームは致命的
            e.hurt(io.github.genichimaruo.singulo.registry.SinguloDamageTypes.jetBeam(level), phase == IRRADIATE ? BEAM_DAMAGE : JET_DAMAGE);
            e.igniteForSeconds(8);
        }
    }

    /** 仕上げの衝撃波: 照射室のまわりの生き物を外へ押す（壊さない・傷つけない）。 */
    private void shockwave(ServerLevel level) {
        if (back == null) {
            return;
        }
        Vec3 c = JetForgeLayout.target(worldPosition, back);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(10), LivingEntity::isAlive)) {
            Vec3 push = e.position().subtract(c);
            double d = push.length();
            if (d < 1e-3 || d > 10) {
                continue;
            }
            double k = 1.2 * (1 - d / 10);
            e.push(push.x / d * k, 0.3 * k, push.z / d * k);
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ 画面

    @Override
    public void openMenu(ServerPlayer player) {
        DeviceMenu.open(player, this, DeviceMenu.Kind.JET_FORGE, menuItems, i -> switch (i) {
            case DeviceMenu.Forge.PHASE -> phase;
            case DeviceMenu.Forge.ELAPSED -> elapsed();
            case DeviceMenu.Forge.TOTAL -> totalTicks();
            case DeviceMenu.Forge.HOLD -> hold;
            case DeviceMenu.Forge.FORMED -> formed ? 1 : 0;
            case DeviceMenu.Forge.CORE -> hasCore() ? 1 : 0;
            case DeviceMenu.Forge.COOLANT -> tank.getFluidAmount();
            case DeviceMenu.Forge.BUFFER_MFE -> (int) Math.min(Integer.MAX_VALUE, buffer / 1_000_000L);
            case DeviceMenu.Forge.CAPACITY_MFE -> (int) Math.min(Integer.MAX_VALUE, energy.capacity() / 1_000_000L);
            case DeviceMenu.Forge.INTAKE_MFE -> (int) Math.min(Integer.MAX_VALUE, Math.round(intake / 1_000_000.0));
            case DeviceMenu.Forge.DEMAND_MFE -> (int) Math.min(Integer.MAX_VALUE, demand(phase == IDLE ? IRRADIATE : phase) / 1_000_000L);
            case DeviceMenu.Forge.BH -> bh;
            case DeviceMenu.Forge.SEED -> hasSeed() ? 1 : 0;
            case DeviceMenu.Forge.IGNITION_PCT -> ignitionCharged ? 100 : (int) Math.min(100, 100.0 * ignitionStored / Math.max(1, ignitionEnergy()));
            case DeviceMenu.Forge.IGNITION_SECS -> bh != BH_IGNITING ? 0 : ignitionCharged
                    ? Math.max(0, IGNITION_SEQUENCE_TICKS - ignitionElapsed) / 20 : Math.max(0, ignitionLeft) / 20;
            case DeviceMenu.Forge.IGNITION_GFE -> (int) Math.min(Integer.MAX_VALUE, ignitionEnergy() / 1_000_000_000L);
            case DeviceMenu.Forge.BATCH -> phase == IDLE ? (parallelUpgrade ? 4 : 1) : batchSize;
            default -> 0;
        }, this);
    }

    @Override
    public boolean onButton(ServerPlayer player, int id) {
        if (id == DeviceMenu.Forge.BUTTON_IGNITE && ignite()) {
            SinguloSounds.playAt(level, worldPosition, "jet_forge.warning", 2.0F, 0.8F);
            return true;
        }
        return false;
    }

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, net.minecraft.world.InteractionHand hand) {
        if (!parallelUpgrade && stack.is(item("collapsar_parallel_cassette"))) {
            parallelUpgrade = true;
            if (!player.isCreative()) stack.shrink(1);
            setChanged();
            markSync();
            return true;
        }
        return false;
    }

    /** 画面のスロット: コアは加工中は取り出せない。出力は取り出すだけ。 */
    private final net.neoforged.neoforge.items.IItemHandlerModifiable menuItems = new net.neoforged.neoforge.items.IItemHandlerModifiable() {
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            // 画面での入れ替え（つかんだ物を置く・取る）。加工中のコアは動かさない
            if (slot == SLOT_CORE && bh == BH_IGNITING) {
                return;
            }
            items.setStackInSlot(slot, stack);
        }


        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= SLOT_OUT ? stack : items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == SLOT_CORE && bh == BH_IGNITING) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < SLOT_OUT && items.isItemValid(slot, stack);
        }
    };

    /** 点火中はコアを外せない（画面のスロットの判定）。 */
    public boolean coreLocked() {
        return bh == BH_IGNITING;
    }

    // ------------------------------------------------------------------ 搬入出

    /** 自動化の口: 入れるのは材料（とコア）、出すのは完成品だけ。 */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= SLOT_OUT ? stack : items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= SLOT_OUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < SLOT_OUT && items.isItemValid(slot, stack);
        }
    };

    /** 押し出す口（完成品の枠と、空の容器の枠）。 */
    private final IItemHandler output = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 || slot == 1 ? items.getStackInSlot(SLOT_OUT + slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 || slot == 1 ? items.extractItem(SLOT_OUT + slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    /** 冷媒の口（入れるだけ）。 */
    private final IFluidHandler coolantHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return isCoolant(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    /** 電力の口。形ができてコアが入っている間だけ、1 tick に jetForgeMaxInput まで受け取る（蓄えはその4倍まで）。 */
    private final class Energy implements LongEnergyStorage {
        long capacity() {
            long max = maxInput();
            return max > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : max * 4;
        }

        private boolean accepting() {
            return formed && bh != BH_NONE;
        }

        @Override
        public long receiveLong(long amount, boolean simulate) {
            if (!accepting() || amount <= 0 || level == null) {
                return 0;
            }
            long now = level.getGameTime();
            if (now != receivedTick) {
                receivedTick = now;
                receivedThisTick = 0;
            }
            boolean igniting = bh == BH_IGNITING;
            long room = Math.min(maxInput() - receivedThisTick,
                    igniting ? (ignitionCharged ? 0 : ignitionEnergy() - ignitionStored) : capacity() - buffer);
            long accepted = Math.max(0, Math.min(amount, room));
            if (!simulate && accepted > 0 && igniting) {
                ignitionStored += accepted;
                receivedThisTick += accepted;
                setChanged();
            } else if (!simulate && accepted > 0) {
                buffer += accepted;
                receivedThisTick += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, buffer);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return accepting();
        }
    }

    // ------------------------------------------------------------------ 壊す・読み込み解除

    @Override
    public void onBroken(Level level) {
        if (parallelUpgrade && !level.isClientSide) {
            parallelUpgrade = false;
            Block.popResource(level, worldPosition, new ItemStack(item("collapsar_parallel_cassette")));
        }
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            drops.add(items.getStackInSlot(i).copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        if (phase != IDLE) {
            // 仕上げより前なら取り出した素材を返し、仕上げのあと（鍛え終わっている）なら完成品を出す
            if (phase >= FINISH) {
                drops.add(result.copy());
            } else {
                for (ItemStack s : charged) {
                    drops.add(s.copy());
                }
            }
        }
        working = ItemStack.EMPTY;
        result = ItemStack.EMPTY;
        charged.clear();
        phase = IDLE;
        if (level instanceof ServerLevel server) {
            if (bh == BH_IGNITING) {
                abortIgnition(server, null);
            } else if (bh == BH_ACTIVE && back != null) {
                leaveRemnant(server);
            }
        }
        for (ItemStack s : drops) {
            if (!s.isEmpty()) {
                Block.popResource(level, worldPosition, s);
            }
        }
    }

    /** 炉心のブラックホールを、拘束されたまま炉心の中心に野良ブラックホールとして残す（中心のブロックは置き換える）。 */
    private void leaveRemnant(ServerLevel level) {
        BlockPos c = BlockPos.containing(JetForgeLayout.core(worldPosition, back));
        level.setBlock(c, io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(c) instanceof io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity rogue) {
            rogue.setCore(bhMass, 0);
            rogue.setStable(true);
        }
        bh = BH_NONE;
        tellNear(level, "gui.singulo.jet_forge.remnant");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            JetForgeClientIndex.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        ports.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        ports.onRemoved(level);
        if (level != null && level.isClientSide) {
            JetForgeClientIndex.remove(this);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ 保存と同期

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putLong("buffer", buffer);
        tag.putLong("consumed", consumedTotal);
        tag.putLong("ignition_stored", ignitionStored);
        tag.putInt("ignition_left", ignitionLeft);
        tag.putDouble("bh_mass", bhMass);
        writeProcess(tag, registries);
        ListTag list = new ListTag();
        for (ItemStack s : charged) {
            if (!s.isEmpty()) {
                list.add(s.save(registries));
            }
        }
        tag.put("charged", list);
        if (recipeId != null) {
            tag.putString("recipe", recipeId.toString());
        }
    }

    /** 段階と、見せる物（同期にも使う）。 */
    private void writeProcess(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("parallel_upgrade", parallelUpgrade);
        tag.putInt("batch_size", batchSize);
        tag.putInt("phase", phase);
        tag.putInt("phase_ticks", phaseTicks);
        tag.putInt("hold", hold);
        tag.putInt("energy_per_tick", energyPerTick);
        tag.putInt("irradiate_ticks", irradiateTicks);
        tag.putInt("bh", bh);
        tag.putInt("ignition_elapsed", ignitionElapsed);
        tag.putBoolean("ignition_charged", ignitionCharged);
        tag.putInt("back", back == null ? -1 : back.get3DDataValue());
        if (!working.isEmpty()) {
            tag.put("working", working.save(registries));
        }
        if (!result.isEmpty()) {
            tag.put("result", result.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            ItemStackHandler saved = new ItemStackHandler();
            saved.deserializeNBT(registries, tag.getCompound("items"));
            for (int i = 0; i < SLOTS; i++) {
                items.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
            }
            tank.readFromNBT(registries, tag.getCompound("tank"));
            buffer = tag.getLong("buffer");
            consumedTotal = tag.getLong("consumed");
            charged.clear();
            for (Tag t : tag.getList("charged", Tag.TAG_COMPOUND)) {
                ItemStack s = ItemStack.parseOptional(registries, (CompoundTag) t);
                if (!s.isEmpty()) {
                    charged.add(s);
                }
            }
            recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
            ignitionStored = tag.getLong("ignition_stored");
            ignitionLeft = tag.getInt("ignition_left");
            bhMass = tag.contains("bh_mass") ? tag.getDouble("bh_mass") : CORE_MASS;
            firstCheck = true;
        }
        phase = tag.getInt("phase");
        parallelUpgrade = tag.getBoolean("parallel_upgrade");
        batchSize = tag.contains("batch_size") ? Math.max(1, Math.min(4, tag.getInt("batch_size"))) : 1;
        phaseTicks = tag.getInt("phase_ticks");
        hold = tag.getInt("hold");
        energyPerTick = tag.getInt("energy_per_tick");
        irradiateTicks = tag.contains("irradiate_ticks") ? Math.max(1, tag.getInt("irradiate_ticks")) : 1200;
        working = tag.contains("working") ? ItemStack.parseOptional(registries, tag.getCompound("working")) : ItemStack.EMPTY;
        result = tag.contains("result") ? ItemStack.parseOptional(registries, tag.getCompound("result")) : ItemStack.EMPTY;
        int was = bh;
        bh = tag.getInt("bh");
        if (level != null && level.isClientSide && clientSynchronized && was != BH_ACTIVE && bh == BH_ACTIVE) {
            clientFormedAt = clientTicks;                 // できた瞬間の閃光（描画）
        }
        ignitionElapsed = tag.getInt("ignition_elapsed");
        ignitionCharged = tag.getBoolean("ignition_charged");
        // 向きは保存する（形が崩れていても、ブラックホールの位置と引力のため）
        int b = tag.contains("back") ? tag.getInt("back") : -1;
        back = b >= 2 && b < 6 ? Direction.from3DDataValue(b) : null;
        if (tag.contains("sync")) {
            clientSynchronized = true;
            // クライアントへの同期（形・コアの有無）
            formed = tag.getBoolean("formed");
            clientCore = tag.getBoolean("core");
        }
    }

    private boolean clientCore;
    private boolean clientSynchronized;
    private int clientFormedAt = Integer.MIN_VALUE / 2;

    /** クライアントで、ブラックホールができてからの tick（できた瞬間の閃光に使う）。 */
    public int formedAge() {
        return clientTicks - clientFormedAt;
    }

    /** クライアントで、炉心にブラックホールがあるか（更新のときに受け取る）。 */
    public boolean shownCore() {
        return level != null && level.isClientSide ? clientCore : hasCore();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("sync", true);
        tag.putBoolean("formed", formed);
        tag.putBoolean("core", hasCore());
        writeProcess(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** テスト用: 点火を飛ばして、炉心にブラックホールを置く。 */
    public void forceBlackHole() {
        bh = BH_ACTIVE;
        bhMass = CORE_MASS;
        markSync();
    }

    /** テスト用: 1回の加工の途中の状態を作る。 */
    public void forcePhase(int phase, int ticks) {
        this.phase = phase;
        this.phaseTicks = ticks;
        markSync();
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }
}
