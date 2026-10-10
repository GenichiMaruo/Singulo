package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * その場所に置かないと何も集まらない収集装置の共通部分（深海圧力収集器・虚空捕集器）。電力は要らない。
 * 場所のよさ（0〜1）に比例して進み、最大のとき ticksAtMax() tick に1個できる。
 * 右クリックで、たまったものを受け取り、今の集まり具合を知らせる。搬出（ホッパーやパイプ）でも取り出せる。
 */
public abstract class EnvironmentCollectorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    static final int CHECK_INTERVAL = 20;

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = new net.minecraftforge.items.wrapper.RangedWrapper(output, 0, 1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }
    };
    private double progress;
    private double rate;

    protected EnvironmentCollectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 場所のよさ（0 なら何も集まらない、1 で最大）。 */
    protected abstract double rateAt(Level level, BlockPos pos);

    /** できるもの（singulo の ID）。 */
    protected abstract String product();

    /** 1つできたときの音（SinguloSounds の名前）。 */
    protected abstract String collectSound();

    /** 最大のときに1個できるまでの tick。 */
    protected abstract int ticksAtMax();

    /** 集まらないときの理由と、集まっているときの説明（翻訳キーの前半）。 */
    protected abstract String messageKey();

    public IItemHandler automationItems() {
        return automation;
    }

    public ItemStackHandler output() {
        return output;
    }

    public double rate() {
        return rate;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnvironmentCollectorBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % CHECK_INTERVAL != 0) {
            return;
        }
        be.rate = be.rateAt(level, pos);
        be.progress += be.rate * CHECK_INTERVAL / be.ticksAtMax();
        while (be.progress >= 1) {
            ItemStack made = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(be.product())));
            if (!be.output.insertItem(0, made, false).isEmpty()) {
                be.progress = 1;
                break;
            }
            be.progress -= 1;
            level.playSound(null, pos, io.github.genichimaruo.singulo.registry.SinguloSounds.get(be.collectSound()),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        be.setChanged();
        boolean active = be.rate > 0;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        ItemStack stored = output.extractItem(0, 64, false);
        if (!stored.isEmpty() && !player.getInventory().add(stored)) {
            player.drop(stored, false);
        }
        double r = level == null ? 0 : rateAt(level, worldPosition);
        if (r <= 0) {
            player.displayClientMessage(Component.translatable(messageKey() + ".idle"), true);
        } else {
            int seconds = (int) Math.round(ticksAtMax() / r / 20);
            player.displayClientMessage(Component.translatable(messageKey() + ".rate", Math.round(r * 100), seconds), true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("output", output.serializeNBT());
        tag.putDouble("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        output.deserializeNBT(tag.getCompound("output"));
        progress = tag.getDouble("progress");
    }
}
