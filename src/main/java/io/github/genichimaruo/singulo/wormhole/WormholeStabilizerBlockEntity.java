package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ワームホール固定化装置（段階5）。不安定な口を入れると、エキゾチック物質 MATTER_PER_MOUTH 個で喉を支え、
 * 口の筐体 CASING_PER_MOUTH 個に収めて、STABILIZE_TICKS で持ち運べる「ワームホールの口」（設置できる）にする。
 * 口は2つまで同時に扱える。固定化を始める前の口は時間切れで消える（始まれば装置が支える）。
 * 口・エキゾチック物質・筐体は手に持って右クリックか搬入で入れ、できた口は空の手で右クリックか搬出で受け取る。
 */
public class WormholeStabilizerBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener, net.minecraft.world.MenuProvider {
    public static final int STABILIZE_TICKS = 100;
    public static final int MATTER_PER_MOUTH = 2;
    public static final int FE_PER_TICK = 10_000;
    public static final int CASING_PER_MOUTH = 1;
    public static final int SLOT_FUEL = 2;
    public static final int SLOT_CASING = 3;
    public static final int SLOTS = 4;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return s == SLOT_FUEL ? ExoticCharge.isExoticMatter(stack) : s == SLOT_CASING ? isCasing(stack)
                    : stack.getItem() instanceof UnstableMouthItem;
        }

        @Override
        public int getSlotLimit(int s) {
            return s >= SLOT_FUEL ? 64 : 1;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    /** 搬入は不安定な口・燃料・筐体だけ、搬出はできた口だけ。 */
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
            if (slot < SLOT_FUEL && (level == null || UnstableMouthItem.expired(stack, level))) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < SLOT_FUEL && isSealed(items.getStackInSlot(slot)) ? items.extractItem(slot, amount, simulate)
                    : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    };
    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(500_000, 50_000, 0, this::setChanged);
    private final int[] progress = new int[2];

    public WormholeStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORMHOLE_STABILIZER.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public IItemHandler automationItems() {
        return automation;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public static boolean isSealed(ItemStack stack) {
        return stack.is(SinguloBlocks.WORMHOLE_MOUTH.get().asItem());
    }

    /** 口の筐体（固定化した喉を収める入れ物）。 */
    public static boolean isCasing(ItemStack stack) {
        return !stack.isEmpty() && stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                io.github.genichimaruo.singulo.Singulo.id("wormhole_mouth_casing")));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WormholeStabilizerBlockEntity be) {
        boolean active = false;
        for (int i = 0; i < 2; i++) {
            active |= be.tickSlot(level, i);
        }
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    private boolean tickSlot(Level level, int i) {
        ItemStack mouth = items.getStackInSlot(i);
        if (!(mouth.getItem() instanceof UnstableMouthItem)) {
            progress[i] = 0;
            return false;
        }
        if (progress[i] == 0) {
            if (UnstableMouthItem.expired(mouth, level)) {
                items.setStackInSlot(i, ItemStack.EMPTY);
                return false;
            }
            if (items.getStackInSlot(SLOT_FUEL).getCount() < MATTER_PER_MOUTH
                    || items.getStackInSlot(SLOT_CASING).getCount() < CASING_PER_MOUTH || !energy.consume(FE_PER_TICK)) {
                return false;
            }
            items.extractItem(SLOT_FUEL, MATTER_PER_MOUTH, false);
            items.extractItem(SLOT_CASING, CASING_PER_MOUTH, false);
            progress[i] = 1;
            return true;
        }
        if (!energy.consume(FE_PER_TICK)) {
            return false;
        }
        if (++progress[i] >= STABILIZE_TICKS) {
            ItemStack sealed = new ItemStack(SinguloBlocks.WORMHOLE_MOUTH.get());
            SinguloComponents.set(sealed, SinguloComponents.WORMHOLE.get(), SinguloComponents.get(mouth, SinguloComponents.WORMHOLE.get()));
            items.setStackInSlot(i, sealed);
            progress[i] = 0;
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.6F);
        }
        setChanged();
        return true;
    }

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (stack.getItem() instanceof UnstableMouthItem || ExoticCharge.isExoticMatter(stack) || isCasing(stack)) {
            ItemStack rest = stack.copy();
            for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
                rest = automation.insertItem(i, rest, false);
            }
            stack.setCount(rest.getCount());
            return true;
        }
        return false;
    }

    /** 空の手で右クリックすると画面を開く（口や燃料を持っていれば、そのまま入れる＝useItem）。 */
    @Override
    public void openMenu(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
                                                                          net.minecraft.world.entity.player.Player player) {
        return new WormholeStabilizerMenu(id, inv, worldPosition, items,
                io.github.genichimaruo.singulo.machine.SyncedInts.server(WormholeStabilizerMenu.COUNT, i -> switch (i) {
                    case WormholeStabilizerMenu.D_PROGRESS_0 -> progress[0];
                    case WormholeStabilizerMenu.D_PROGRESS_1 -> progress[1];
                    case WormholeStabilizerMenu.D_ENERGY -> energy.getEnergyStored();
                    case WormholeStabilizerMenu.D_CAPACITY -> energy.getMaxEnergyStored();
                    default -> 0;
                }), this);
    }

    @Override
    public void onBroken(Level level) {
        // できた口・燃料・筐体は落とす（不安定な口は装置の外では保てない）
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (i >= SLOT_FUEL || isSealed(s)) {
                Block.popResource(level, worldPosition, s);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("energy", energy.getEnergyStored());
        tag.putIntArray("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 筐体の枠がなかった頃の保存（3枠）も読めるように、一度別の入れ物に読んでから移す
        ItemStackHandler saved = new ItemStackHandler(SLOTS);
        saved.deserializeNBT(tag.getCompound("items"));
        for (int i = 0; i < SLOTS; i++) {
            items.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        }
        energy.setEnergy(tag.getInt("energy"));
        int[] p = tag.getIntArray("progress");
        for (int i = 0; i < Math.min(2, p.length); i++) {
            progress[i] = p[i];
        }
    }
}
