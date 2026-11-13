package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * フードプリンター。タンパク質・炭水化物・甘味それぞれの食材の枠の食材をペーストにして、同じ種類のフードカートリッジへ入れる
 * （{@link FoodPaste}）。入れる順は、選んだ食品に足りない種類（足りない分の多い順）→ ほかは少ない種類から。食材を多めに
 * 入れておけば、足りないペーストから勝手に補われる。入りきらない食材は枠で待つ。
 * <p>
 * 選んだ食品（{@link PrintedFood}。自動なら、ペーストが足りる食品を出口にある物から優先して）を、
 * ペーストが足りて出口に入る間、続けて印刷する。印刷の始めにペーストを払い、電力（毎 tick {@link PrintedFood#ENERGY_PER_TICK}）が
 * ある間だけ進む。電源を切ると印刷を止めて待つ（食材をペーストにするのは続ける）。
 * <p>
 * 中身はクライアントにも送り、印刷の動き（{@code FoodPrinterRenderer}）と背面の窓のペーストの量に使う。
 */
public class FoodPrinterBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener {
    /** 枠: タンパク質・炭水化物・甘味の食材、同じ順のカートリッジ、印刷した食品。 */
    public static final int INGREDIENT = 0, CARTRIDGE = 3, OUTPUT = 6, SLOTS = 7;
    public static final int ENERGY_CAPACITY = 20_000, MAX_RECEIVE = 200;
    /** 選ぶ食品の「自動」。 */
    public static final int AUTO = -1;
    /** 食材をペーストにする間隔（tick）。 */
    static final int FEED_INTERVAL = 4;
    /** 印刷中に、進み具合をクライアントへ合わせ直す間隔（tick）。 */
    static final int SYNC_INTERVAL = 20;
    /** 画面に送る値の番号。 */
    public static final int D_ENERGY = 0, D_SELECTED = 1, D_PRINTING = 2, D_PROGRESS = 3, D_ENABLED = 4, D_STATE = 5, DATA = 6;
    /** 状態（画面のヘッダーの表示）。待機: 自動で印刷できる食品がない。ペースト不足: 選んだ食品のペーストが足りない。 */
    public static final int S_IDLE = 0, S_RUNNING = 1, S_NO_POWER = 2, S_FULL = 3, S_OFF = 4, S_SHORT = 5;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            return isCartridge(slot) ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!loading) {
                sync();
            }
        }
    };
    /** ホッパーなど: 食材とカートリッジを入れ、印刷した食品だけを取り出す。 */
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
            return slot == OUTPUT ? stack : items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT && accepts(slot, stack);
        }
    };
    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    /** 選んでいる食品（{@link #AUTO} なら自動）、いま印刷している食品（-1 なら印刷していない）と、その進み（tick）。 */
    private int selected = AUTO, printing = -1, progress;
    private boolean enabled = true;
    private int state = S_IDLE;
    private int feedTimer;
    /** 読み込み中（枠を移すときに、クライアントへ送らない）。 */
    private boolean loading;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case D_ENERGY -> energy.getEnergyStored();
                case D_SELECTED -> selected;
                case D_PRINTING -> printing;
                case D_PROGRESS -> progress;
                case D_ENABLED -> enabled ? 1 : 0;
                case D_STATE -> state;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case D_ENERGY -> energy.setEnergy(value);
                case D_SELECTED -> selected = value;
                case D_PRINTING -> printing = value;
                case D_PROGRESS -> progress = value;
                case D_ENABLED -> enabled = value != 0;
                case D_STATE -> state = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public FoodPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FOOD_PRINTER.get(), pos, state);
    }

    public static boolean isIngredient(int slot) {
        return slot >= INGREDIENT && slot < INGREDIENT + FoodPaste.KINDS;
    }

    public static boolean isCartridge(int slot) {
        return slot >= CARTRIDGE && slot < CARTRIDGE + FoodPaste.KINDS;
    }

    /** 枠に入れられる物か（食材の枠はその種類の食材、カートリッジの枠は空か同じ種類のカートリッジ。出口には入れられない）。 */
    public static boolean accepts(int slot, ItemStack stack) {
        if (isIngredient(slot)) {
            return FoodPaste.kindOf(stack) == slot - INGREDIENT;
        }
        if (isCartridge(slot)) {
            return FoodPaste.fitsSlot(stack, slot - CARTRIDGE);
        }
        return false;
    }

    public ItemStackHandler items() {
        return items;
    }

    public IItemHandler automation() {
        return automation;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public ContainerData data() {
        return data;
    }

    public int selected() {
        return selected;
    }

    public int printing() {
        return printing;
    }

    public int progress() {
        return progress;
    }

    public boolean enabled() {
        return enabled;
    }

    public int state() {
        return state;
    }

    /** 種類 kind のカートリッジのペースト（カートリッジがなければ -1）。 */
    public int paste(int kind) {
        ItemStack c = items.getStackInSlot(CARTRIDGE + kind);
        return c.isEmpty() ? -1 : FoodPaste.amount(c);
    }

    /** 印刷する食品を選ぶ（食品の番号でなければ自動）。 */
    public void select(int food) {
        selected = PrintedFood.byIndex(food) == null ? AUTO : food;
        sync();
    }

    public void togglePower() {
        enabled = !enabled;
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FoodPrinterBlockEntity be) {
        if (++be.feedTimer >= FEED_INTERVAL) {
            be.feedTimer = 0;
            be.feed();
        }
        if (be.enabled && be.printing < 0) {
            be.tryStart();
        }
        boolean working = false;
        PrintedFood food = PrintedFood.byIndex(be.printing);
        if (be.enabled && food != null && be.energy.consume(PrintedFood.ENERGY_PER_TICK)) {
            working = true;
            be.progress++;
            if (be.progress >= food.ticks()) {
                be.finish(food);
            } else if (be.progress % SYNC_INTERVAL == 0) {
                be.sync();
            }
        }
        be.state = be.computeState(working);
        if (state.getValue(AbstractMachineBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, working), Block.UPDATE_CLIENTS);
        }
    }

    /** クライアント: 電力がある間（LIT）、サーバーの合わせ直しを待たずに印刷を進めて見せる。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, FoodPrinterBlockEntity be) {
        PrintedFood food = PrintedFood.byIndex(be.printing);
        if (food != null && state.getValue(AbstractMachineBlock.LIT) && be.progress < food.ticks() - 1) {
            be.progress++;
        }
    }

    private int computeState(boolean working) {
        if (!enabled) {
            return S_OFF;
        }
        if (printing >= 0) {
            return working ? S_RUNNING : S_NO_POWER;
        }
        PrintedFood food = PrintedFood.byIndex(selected);
        if (food != null) {
            return hasPaste(food) ? S_FULL : S_SHORT;
        }
        for (PrintedFood any : PrintedFood.values()) {
            if (hasPaste(any)) {
                return S_FULL;
            }
        }
        return S_IDLE;
    }

    /**
     * 食材の枠の食材を1個、ペーストにして同じ種類のカートリッジへ入れる。入れる種類の順は、選んだ（印刷している）食品に足りない種類
     * （足りない分の多い順）→ ほかは少ない種類から。その種類の食材がない・入りきらないときは次の種類へ。
     */
    void feed() {
        PrintedFood goal = goal();
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            if (FoodPaste.fitsSlot(items.getStackInSlot(CARTRIDGE + k), k) && FoodPaste.amount(items.getStackInSlot(CARTRIDGE + k)) < FoodPaste.CAPACITY) {
                order.add(k);
            }
        }
        order.sort(Comparator.comparingInt((Integer k) -> -shortfall(goal, k)).thenComparingInt(k -> FoodPaste.amount(items.getStackInSlot(CARTRIDGE + k))));
        for (int k : order) {
            ItemStack cartridge = items.getStackInSlot(CARTRIDGE + k);
            int room = FoodPaste.CAPACITY - FoodPaste.amount(cartridge);
            ItemStack stack = items.getStackInSlot(INGREDIENT + k);
            if (FoodPaste.kindOf(stack) == k && FoodPaste.unitsOf(stack) <= room) {
                ItemStack filled = cartridge.copy();
                FoodPaste.set(filled, k, FoodPaste.amount(cartridge) + FoodPaste.unitsOf(stack));
                items.setStackInSlot(CARTRIDGE + k, filled);
                items.setStackInSlot(INGREDIENT + k, stack.copyWithCount(stack.getCount() - 1));
                if (level != null) {
                    level.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.5F, 1.2F);
                }
                return;
            }
        }
    }

    /** ペーストを補う目当ての食品: 選んだ食品、自動なら印刷している食品か出口にある食品（なければ null）。 */
    private PrintedFood goal() {
        PrintedFood food = PrintedFood.byIndex(selected);
        if (food == null) {
            food = PrintedFood.byIndex(printing);
        }
        if (food == null && items.getStackInSlot(OUTPUT).getItem() instanceof PrintedFoodItem p) {
            food = p.printedFood();
        }
        return food;
    }

    /** 食品 goal の1個に、種類 kind のペーストがいくつ足りないか（goal がなければ 0）。 */
    private int shortfall(PrintedFood goal, int kind) {
        return goal == null ? 0 : Math.max(0, goal.cost(kind) - Math.max(0, paste(kind)));
    }

    /** 次に印刷する食品（選んだ食品か、自動なら出口にある食品 → 並びの順で、ペーストが足りて出口に入るもの）。なければ null。 */
    PrintedFood next() {
        PrintedFood fixed = PrintedFood.byIndex(selected);
        if (fixed != null) {
            return canPrint(fixed) ? fixed : null;
        }
        if (items.getStackInSlot(OUTPUT).getItem() instanceof PrintedFoodItem p && canPrint(p.printedFood())) {
            return p.printedFood();
        }
        for (PrintedFood food : PrintedFood.values()) {
            if (canPrint(food)) {
                return food;
            }
        }
        return null;
    }

    /** 次の食品の印刷を始める（ペーストはここで払う）。 */
    void tryStart() {
        PrintedFood food = next();
        if (food == null) {
            return;
        }
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            if (food.cost(k) > 0) {
                ItemStack c = items.getStackInSlot(CARTRIDGE + k).copy();
                FoodPaste.set(c, k, FoodPaste.amount(c) - food.cost(k));
                items.setStackInSlot(CARTRIDGE + k, c);
            }
        }
        printing = food.ordinal();
        progress = 0;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.25F, 1.8F);
        }
        sync();
    }

    /** ペーストが足りるか。 */
    public boolean hasPaste(PrintedFood food) {
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            if (food.cost(k) > 0) {
                ItemStack c = items.getStackInSlot(CARTRIDGE + k);
                if (c.isEmpty() || FoodPaste.kind(c) != k || FoodPaste.amount(c) < food.cost(k)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** ペーストが足りて、出口に入るか。 */
    public boolean canPrint(PrintedFood food) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        return hasPaste(food) && (out.isEmpty() || out.is(food.item()) && out.getCount() < out.getMaxStackSize());
    }

    private void finish(PrintedFood food) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        items.setStackInSlot(OUTPUT, out.isEmpty() ? new ItemStack(food.item()) : out.copyWithCount(out.getCount() + 1));
        printing = -1;
        progress = 0;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        sync();
    }

    /** 保存し、見た目（印刷の進み・カートリッジ・出口の食品）をクライアントへ送る。 */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FoodPrinterMenu(id, inv, worldPosition, items, data),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) < 8 * 8;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("selected", selected);
        tag.putInt("printing", printing);
        tag.putInt("progress", progress);
        tag.putBoolean("enabled", enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            ItemStackHandler loaded = new ItemStackHandler();
            loaded.deserializeNBT(registries, tag.getCompound("items"));
            loading = true;
            if (loaded.getSlots() == SLOTS) {
                for (int i = 0; i < SLOTS; i++) {
                    items.setStackInSlot(i, loaded.getStackInSlot(i));
                }
            } else {
                migrate(loaded);
            }
            loading = false;
        }
        energy.setEnergy(tag.getInt("energy"));
        selected = tag.contains("selected") ? tag.getInt("selected") : AUTO;
        printing = tag.contains("printing") ? tag.getInt("printing") : -1;
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        if (PrintedFood.byIndex(selected) == null) {
            selected = AUTO;
        }
        if (PrintedFood.byIndex(printing) == null) {
            printing = -1;
        }
        progress = Math.max(0, tag.getInt("progress"));
    }

    /**
     * 開発中の古い並び（食材1枠・カートリッジ3・出口の5枠と、食材庫9枠・カートリッジ3・出口の13枠）の中身を、いまの枠へ移す。
     * 食材は種類の枠へ重ねる（どちらも公開前の並びなので、1枠に重ならない分は移さない）。
     */
    private void migrate(ItemStackHandler loaded) {
        int n = loaded.getSlots();
        int cartridges = n == 5 ? 1 : n == 13 ? 9 : -1;
        if (cartridges < 0) {
            return;
        }
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            items.setStackInSlot(CARTRIDGE + k, loaded.getStackInSlot(cartridges + k));
        }
        items.setStackInSlot(OUTPUT, loaded.getStackInSlot(cartridges + FoodPaste.KINDS));
        for (int i = 0; i < cartridges; i++) {
            ItemStack stack = loaded.getStackInSlot(i);
            int kind = FoodPaste.kindOf(stack);
            if (kind >= 0) {
                items.insertItem(INGREDIENT + kind, stack, false);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
