package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 宇宙線ミュオン収集器。宇宙線が大気にぶつかってできるミュオンは高い所ほど多い（実在）。
 * 空が見える場所でだけ動き、Y=100以下ではゼロ、Y=200以上で最大（1分に1束）。電力は要らない。
 * 右クリックで溜まったミュオン束を受け取り、今の収集速度を表示する。
 */
public class CosmicMuonCollectorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    static final int TICKS_PER_BUNDLE_AT_MAX = 1200;
    static final int CHECK_INTERVAL = 20;

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = new net.neoforged.neoforge.items.wrapper.RangedWrapper(output, 0, 1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }
    };
    private double progress;
    private double rate;

    public CosmicMuonCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.COSMIC_MUON_COLLECTOR.get(), pos, state);
    }

    public IItemHandler automationItems() {
        return automation;
    }

    public ItemStackHandler output() {
        return output;
    }

    /** 高さと空の見え方から、最大に対する収集の割合（0〜1）。 */
    public static double rateAt(Level level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        return Mth.clamp((pos.getY() - 100) / 100.0, 0, 1);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CosmicMuonCollectorBlockEntity be) {
        if (level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        be.rate = rateAt(level, pos);
        be.progress += be.rate * CHECK_INTERVAL / TICKS_PER_BUNDLE_AT_MAX;
        while (be.progress >= 1) {
            ItemStack bundle = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_bundle")));
            if (!be.output.insertItem(0, bundle, false).isEmpty()) {
                be.progress = 1;
                break;
            }
            be.progress -= 1;
        }
        be.setChanged();
        boolean active = be.rate > 0;
        if (state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.MUON_COLLECTOR, output, i -> switch (i) {
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Muon.RATE -> (int) Math.round(rateAt(level, worldPosition) * 100);
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Muon.Y -> worldPosition.getY();
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Muon.SKY -> level != null && level.canSeeSky(worldPosition.above()) ? 1 : 0;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Muon.PROGRESS -> (int) Math.round(Math.min(1, progress) * 100);
            default -> 0;
        }, (p, id) -> false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
        tag.putDouble("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.deserializeNBT(registries, tag.getCompound("output"));
        progress = tag.getDouble("progress");
    }
}
