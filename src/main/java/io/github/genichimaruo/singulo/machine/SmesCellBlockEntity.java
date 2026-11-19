package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;

/**
 * SMESセル（超伝導磁気エネルギー貯蔵）。10 MFE を蓄える。正面から出し、ほかの5面から受け取る。
 * 右クリックで画面を開き、蓄電量と1 tick あたりの出し入れを見られる。
 * 画面の充電の枠: 「充電する物を入れる枠」の物を蓄えた電力で充電し（出し入れの速さまで。正面から出す分と合わせる）、
 * 満タンになったら「充電済みの物が出てくる枠」へ移す。ホッパーなどからも、入れる枠へ入れ、出てくる枠から取り出せる。
 * 壊すと枠の物を落とす。
 */
public class SmesCellBlockEntity extends io.github.genichimaruo.singulo.compat.LegacyBlockEntity implements AbstractMachineBlock.MenuOpener,
        net.minecraft.world.MenuProvider, AbstractMachineBlock.BreakListener {
    /** 充電の枠: 0 は入れる枠、1 は充電済みの物が出てくる枠。 */
    public static final int CHARGE_IN = 0, CHARGE_OUT = 1;
    public static final int CAPACITY = 10_000_000;
    public static final int RATE = 100_000;

    private final SinguloEnergyStorage energy;
    private final int rate;
    private final EnergyHandler input = new Side(true);
    private final EnergyHandler output = new Side(false);
    /** 入出力の上限は、隣の装置の処理順によらずワールドの同じ tick 全体で共有する。 */
    private long transferTick = Long.MIN_VALUE;
    private int inThisTick, outThisTick;
    private int lastIn, lastOut;
    private final io.github.genichimaruo.singulo.compat.TransactionalItemStackHandler charge = new io.github.genichimaruo.singulo.compat.TransactionalItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return slot == CHARGE_IN && io.github.genichimaruo.singulo.energy.ItemCharging.chargeable(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** ホッパーなど用: 入れる枠へ入れるだけ、出てくる枠から取り出すだけ。 */
    private final net.neoforged.neoforge.items.IItemHandler chargeAutomation = new net.neoforged.neoforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return charge.getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return slot == CHARGE_IN ? charge.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == CHARGE_OUT ? charge.extractItem(slot, amount, simulate) : net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return charge.isItemValid(slot, stack);
        }
    };

    public SmesCellBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.SMES_CELL.get(), pos, state, CAPACITY, RATE);
    }

    protected SmesCellBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                  BlockState state, int capacity, int rate) {
        super(type, pos, state);
        this.rate = rate;
        this.energy = new SinguloEnergyStorage(capacity, rate, rate, this::setChanged) {
            @Override public int insert(int amount, TransactionContext tx) {
                if (amount < 0) throw new IllegalArgumentException("Negative energy insertion");
                updateTransferTick();
                int got = super.insert(Math.min(amount, Math.max(0, rate - inThisTick)), tx);
                if (got > 0) { flowJournal.updateSnapshots(tx); inThisTick += got; }
                return got;
            }

            @Override public int extract(int amount, TransactionContext tx) {
                if (amount < 0) throw new IllegalArgumentException("Negative energy extraction");
                updateTransferTick();
                int got = super.extract(Math.min(amount, Math.max(0, rate - outThisTick)), tx);
                if (got > 0) { flowJournal.updateSnapshots(tx); outThisTick += got; }
                return got;
            }
        };
    }

    private void updateTransferTick() {
        long now = level == null ? 0 : level.getGameTime();
        if (transferTick == now) return;
        lastIn = transferTick == now - 1 ? inThisTick : 0;
        lastOut = transferTick == now - 1 ? outThisTick : 0;
        transferTick = now;
        inThisTick = 0;
        outThisTick = 0;
    }

    /** SMESモジュール（段階4）。2 GFE を蓄え、100 MFE/t で出し入れする。 */
    public static class Module extends SmesCellBlockEntity {
        public static final int MODULE_CAPACITY = 2_000_000_000;
        public static final int MODULE_RATE = 100_000_000;

        public Module(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.SMES_MODULE.get(), pos, state, MODULE_CAPACITY, MODULE_RATE);
        }
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    /** 充電の枠（画面用）。 */
    public net.neoforged.neoforge.items.ItemStackHandler chargeSlots() {
        return charge;
    }

    /** 充電の枠（ホッパーなど用。入れる枠へ入れ、出てくる枠から取り出す）。 */
    public net.neoforged.neoforge.items.IItemHandler chargeAutomation() {
        return chargeAutomation;
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < charge.getSlots(); i++) {
            Block.popResource(level, worldPosition, charge.getStackInSlot(i));
        }
    }

    @Nullable
    public EnergyHandler energyFor(@Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return side == getBlockState().getValue(AbstractMachineBlock.FACING) ? output : input;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmesCellBlockEntity be) {
        Direction front = state.getValue(AbstractMachineBlock.FACING);
        be.updateTransferTick();
        int available = Math.min(be.energy.getEnergyStored(), be.rate - be.outThisTick);
        BlockPos neighbor = pos.relative(front);
        if (available > 0 && level.isLoaded(neighbor)) {
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, neighbor, front.getOpposite());
            if (target != null) {
                try (var tx = Transaction.openRoot()) {
                    int accepted = target.insert(available, tx);
                    if (accepted >= 0 && accepted <= available && be.energy.extract(accepted, tx) == accepted) tx.commit();
                }
            }
        }
        // 充電の枠の物を充電する（出し入れの速さのうち、正面から出した残りまで）
        long used = io.github.genichimaruo.singulo.energy.ItemCharging.tick(be.charge, CHARGE_IN, CHARGE_OUT,
                Math.min(be.energy.getEnergyStored(), be.rate - be.outThisTick));
        if (used > 0) {
            be.energy.consume((int) used);
            be.outThisTick += (int) used;
        }
        boolean active = be.lastOut > 0 || be.outThisTick > 0;
        if (state.getValue(AbstractMachineBlock.LIT) != active && level.getGameTime() % 10 == 0) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
                                                                          net.minecraft.world.entity.player.Player player) {
        return new SmesMenu(id, inv, worldPosition, charge, SyncedInts.server(SmesMenu.COUNT, i -> switch (i) {
            case SmesMenu.D_STORED -> energy.getEnergyStored();
            case SmesMenu.D_CAPACITY -> energy.getMaxEnergyStored();
            case SmesMenu.D_IN -> lastIn;
            case SmesMenu.D_OUT -> lastOut;
            case SmesMenu.D_RATE -> rate;
            default -> 0;
        }));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("charge", io.github.genichimaruo.singulo.compat.NbtCompat.serialize(charge, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getIntOr("energy", 0));
        if (tag.contains("charge")) {
            io.github.genichimaruo.singulo.compat.NbtCompat.deserialize(charge, registries, tag.getCompoundOrEmpty("charge"));
        }
    }

    /** 受け取り専用・出力専用の口。 */
    private final SnapshotJournal<int[]> flowJournal = new SnapshotJournal<>() {
        @Override protected int[] createSnapshot() { return new int[]{inThisTick, outThisTick}; }
        @Override protected void revertToSnapshot(int[] state) {
            inThisTick = state[0]; outThisTick = state[1];
        }
    };
    private final class Side implements IEnergyStorage, EnergyHandler {
        private final boolean receive;
        Side(boolean receive) { this.receive = receive; }
        @Override public int insert(int amount, TransactionContext tx) {
            if (amount < 0) throw new IllegalArgumentException("Negative energy insertion");
            if (!receive) return 0;
            return energy.insert(amount, tx);
        }
        @Override public int extract(int amount, TransactionContext tx) {
            if (amount < 0) throw new IllegalArgumentException("Negative energy extraction");
            if (receive) return 0;
            return energy.extract(amount, tx);
        }
        @Override public long getAmountAsLong() { return energy.getAmountAsLong(); }
        @Override public long getCapacityAsLong() { return energy.getCapacityAsLong(); }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0) return 0;
            try (var tx = Transaction.openRoot()) {
                int got = insert(amount, tx); if (!simulate) tx.commit(); return got;
            }
        }
        @Override public int extractEnergy(int amount, boolean simulate) {
            if (amount <= 0) return 0;
            try (var tx = Transaction.openRoot()) {
                int got = extract(amount, tx); if (!simulate) tx.commit(); return got;
            }
        }
        @Override public int getEnergyStored() { return energy.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return !receive; }
        @Override public boolean canReceive() { return receive; }
    }

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> transferItems() {
        return new io.github.genichimaruo.singulo.compat.ItemTransferView(charge, (s,r) -> s == CHARGE_IN, (s,r) -> s == CHARGE_OUT);
    }
}