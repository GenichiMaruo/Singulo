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
 * ワームホール生成器（段階5、3×3×3 のマルチブロック。コアは底の中央）。毎tick requiredPerTick()（設定 wormholeGeneratorPower、既定 100 MFE）を
 * GENERATE_TICKS（10秒）続けて受けると、一対の不安定な口を作る。途中で足りない tick があるとやり直し。
 * 口は出力に置かれ、空の手で右クリックするか搬出して受け取る。60秒以内に固定化しないと消える。
 */
public class WormholeGeneratorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener {
    /** 毎tick必要な電力の既定値（設定 wormholeGeneratorPower）。 */
    public static final int DEFAULT_REQUIRED_PER_TICK = 100_000_000;

    /** 毎tick必要な電力（設定 wormholeGeneratorPower、既定 100 MFE/t）。 */
    public static int requiredPerTick() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.WORMHOLE_GENERATOR_POWER.get() : DEFAULT_REQUIRED_PER_TICK;
    }
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
            int take = Math.max(0, Math.min(amount, requiredPerTick() - received));
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
            return requiredPerTick();
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
    /** 直前の tick に受け取った電力（画面に出す）。 */
    private int lastReceived;
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

    /** 入出力口（外殻の代わりに置いたもの）。形成済みのときだけ、この生成器につながる。 */
    private java.util.List<BlockPos> ioPorts = java.util.List.of();
    private boolean chunkUnloading;

    private static void linkPorts(Level level, java.util.List<BlockPos> ports, @javax.annotation.Nullable BlockPos controller) {
        for (BlockPos p : ports) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof io.github.genichimaruo.singulo.multiblock.PortBlockEntity port) {
                port.link(controller);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && !chunkUnloading) {
            linkPorts(level, ioPorts, null);
        }
        super.setRemoved();
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
            java.util.List<BlockPos> found = new java.util.ArrayList<>();
            formed = Structures.casingShapeWithPorts(level, pos, Structures.wormholeGeneratorLayout(pos), 2,
                    io.github.genichimaruo.singulo.multiblock.MultiblockPart.Role.WORMHOLE_IO, found);
            if (!found.equals(ioPorts)) {
                linkPorts(level, ioPorts, null);
                linkPorts(level, found, pos);
                ioPorts = found;
            }
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, pos, was, formed, firstCheck, 2, 0, 3);
            firstCheck = false;
        }
        // 出力の口は時間がたつと消える
        for (int i = 0; i < 2; i++) {
            if (UnstableMouthItem.expired(output.getStackInSlot(i), level)) {
                output.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (formed && outputEmpty() && received >= requiredPerTick()) {
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
        lastReceived = received;
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
        io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, worldPosition, "wormhole_generator_open", 2.0F, 1.0F);
    }

    @Override
    public void openMenu(ServerPlayer player) {
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.WORMHOLE_GENERATOR, output, i -> switch (i) {
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.PROGRESS -> progress;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.FORMED -> formed ? 1 : 0;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.RECEIVED -> lastReceived;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.REQUIRED -> requiredPerTick();
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.REMAIN_0, io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.REMAIN_1 -> (int) Math.max(0,
                    UnstableMouthItem.remaining(output.getStackInSlot(i - io.github.genichimaruo.singulo.machine.DeviceMenu.Gen.REMAIN_0), level));
            default -> 0;
        }, (p, id) -> false);
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
