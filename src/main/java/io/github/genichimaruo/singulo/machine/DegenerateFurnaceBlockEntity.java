package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * 縮退熱炉のコントローラ（ティア4の発電機、7×7×9 のマルチブロック）。上下のピストンで圧縮ブロックLv2 を押しつぶし、
 * 縮退圧の熱で degenerateFurnaceOutput（既定 20 MFE/t）を出す。燃料は Lv2 1個で BURN_TICKS 燃え、時間結晶触媒を消費する。
 * 出力は触媒の速度倍率に比例する。
 */
public class DegenerateFurnaceBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 4;
    public static final int BURN_TICKS = 200;
    public static final int PUSH_PER_TICK = 100_000_000;
    static final int CHECK_INTERVAL = 40;

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final IItemHandler automation = new CombinedInvWrapper(slot, fuel);
    private int burn;
    private int output;
    private int formed;
    private boolean firstCheck = true;
    private long nextCheck;

    public DegenerateFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.DEGENERATE_FURNACE.get(), pos, state, 200_000_000, 0, PUSH_PER_TICK);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(BuiltInRegistries.ITEM.get(Singulo.id("compressed_block_2")));
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public int structureSize() {
        return formed;
    }

    @Override
    public Kind kind() {
        return Kind.DEGENERATE_FURNACE;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return 0;
    }

    @Override
    protected Status readiness(ServerLevel level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            int was = formed;
            formed = Structures.findFurnace(level, worldPosition, getBlockState().getBlock());
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, worldPosition, was > 0, formed > 0, firstCheck, 7, 8, 8);
            firstCheck = false;
        }
        if (formed == 0) {
            return Status.NOT_FORMED;
        }
        if (burn <= 0 && fuel.getStackInSlot(0).isEmpty()) {
            return Status.NO_FUEL;
        }
        return null;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        output = 0;
        if (active) {
            if (burn <= 0) {
                fuel.extractItem(0, 1, false);
                burn = BURN_TICKS;
            }
            burn--;
            output = (int) Math.min(Integer.MAX_VALUE, Math.round(ServerConfig.DEGENERATE_FURNACE_OUTPUT.get()
                    * effect.speed() * ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get()));
            energy.generate(output);
        }
        pushEnergy(level, PUSH_PER_TICK);
    }

    @Override
    protected int displayValue() {
        return output;
    }

    @Override
    protected int extraValue() {
        return fuel.getStackInSlot(0).getCount();
    }

    @Override
    public IItemHandler automationItems() {
        return automation;
    }

    /** 燃料（圧縮ブロックLv2）と触媒は手に持って右クリックでも入れられる。 */
    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (isFuel(stack)) {
            ItemStack rest = fuel.insertItem(0, stack.copy(), false);
            stack.setCount(rest.getCount());
            return true;
        }
        return false;
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        Block.popResource(level, worldPosition, fuel.getStackInSlot(0));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.putInt("burn", burn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        burn = tag.getInt("burn");
    }
}
