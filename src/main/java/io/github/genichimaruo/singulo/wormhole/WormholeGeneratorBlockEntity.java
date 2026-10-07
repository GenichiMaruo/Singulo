package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * ワームホール生成器（段階5、3×3×3 のマルチブロック。コアは底の中央）。毎tick REQUIRED_PER_TICK（1 GFE）を
 * GENERATE_TICKS（10秒）続けて受けると、一対の不安定な口を作る。途中で足りない tick があるとやり直し。
 * 口は出力に置かれ、空の手で右クリックするか搬出して受け取る。60秒以内に固定化しないと消える。
 */
public class WormholeGeneratorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener {
    public static final int REQUIRED_PER_TICK = 1_000_000_000;
    public static final int GENERATE_TICKS = 200;
    static final int CHECK_INTERVAL = 40;

    private final ItemStackHandler output = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final IItemHandler automation = new net.neoforged.neoforge.items.wrapper.RangedWrapper(output, 0, 2) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }
    };
    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (!formed || !outputEmpty()) {
                return 0;
            }
            int take = Math.max(0, Math.min(amount, REQUIRED_PER_TICK - received));
            if (!simulate) {
                received += take;
            }
            return take;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return received;
        }

        @Override
        public int getMaxEnergyStored() {
            return REQUIRED_PER_TICK;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return formed && outputEmpty();
        }
    };
    /** 前の tick の処理のあとに受け取った電力。 */
    private int received;
    private int progress;
    private boolean formed;
    private boolean firstCheck = true;
    private long nextCheck;

    public WormholeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORMHOLE_GENERATOR.get(), pos, state);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public IItemHandler automationItems() {
        return automation;
    }

    public ItemStackHandler output() {
        return output;
    }

    public int progress() {
        return progress;
    }

    public boolean formed() {
        return formed;
    }

    private boolean outputEmpty() {
        return output.getStackInSlot(0).isEmpty() && output.getStackInSlot(1).isEmpty();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WormholeGeneratorBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            boolean was = formed;
            formed = Structures.casingShape(level, pos, Structures.wormholeGeneratorLayout(pos), 2);
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, pos, was, formed, firstCheck, 2, 0, 3);
            firstCheck = false;
        }
        // 出力の口は時間がたつと消える
        for (int i = 0; i < 2; i++) {
            if (UnstableMouthItem.expired(output.getStackInSlot(i), level)) {
                output.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (formed && outputEmpty() && received >= REQUIRED_PER_TICK) {
            progress++;
            if (progress % 10 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.05);
            }
            if (progress >= GENERATE_TICKS) {
                generate(level);
            }
        } else {
            progress = 0;
        }
        received = 0;
        boolean lit = progress > 0;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** 一対の口を作る。 */
    public void generate(Level level) {
        ItemStack[] pair = UnstableMouthItem.createPair(level);
        output.setStackInSlot(0, pair[0]);
        output.setStackInSlot(1, pair[1]);
        progress = 0;
        level.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    @Override
    public void openMenu(ServerPlayer player) {
        boolean gave = false;
        for (int i = 0; i < 2; i++) {
            ItemStack s = output.extractItem(i, 1, false);
            if (!s.isEmpty()) {
                player.getInventory().placeItemBackInInventory(s);
                gave = true;
            }
        }
        if (gave) {
            player.displayClientMessage(Component.translatable("gui.singulo.wormhole.generated", UnstableMouthItem.LIFETIME / 20), true);
        } else if (!formed) {
            player.displayClientMessage(Component.translatable("gui.singulo.status.not_formed"), true);
        } else {
            player.displayClientMessage(Component.translatable("gui.singulo.wormhole.generator", progress / 20, GENERATE_TICKS / 20), true);
        }
    }

    @Override
    public void onBroken(Level level) {
        // 不安定な口は装置の外では保てないので、壊すと消える
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.deserializeNBT(registries, tag.getCompound("output"));
    }
}
