package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * アンドロイド作業台。アンドロイドが作業台のレシピを作る場所（バニラの作業台より速く作る）。
 * プレイヤーには画面がなく、右クリックで使い方を知らせる。
 * <p>作った物がアンドロイドの持ち物に入りきらないときは、足元に落とさずに、ここの預かり箱（{@link #BUFFER_SLOTS} 枠）へ入れる。
 * 預かった物は、その注文の素材置き場として扱い（作業指令台の受け渡し場所の1つ）、あとの工程や納品で取りに来る。
 * 取りに来られなくなった物（注文を取り消したなど）は、右クリックで受け取れる。壊すと中身を落とす。
 */
public class AndroidWorkbenchBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener {
    /** ここで作ると、1回あたりの時間がこの割合になる（バニラの作業台 2秒 → 0.5秒）。 */
    public static final int CRAFT_TICKS = 10;
    /** 預かり箱の枠の数。 */
    public static final int BUFFER_SLOTS = 27;

    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public AndroidWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_WORKBENCH.get(), pos, state);
    }

    /** 作った物の預かり箱（作業指令台とアンドロイドが使う）。 */
    public ItemStackHandler buffer() {
        return buffer;
    }

    public boolean bufferEmpty() {
        for (int i = 0; i < buffer.getSlots(); i++) {
            if (!buffer.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (bufferEmpty()) {
            player.displayClientMessage(Component.translatable("message.singulo.android_workbench.hint"), true);
            return;
        }
        // 預かっている物を渡す（取りに来られなくなった物を取り出すため）
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.extractItem(i, Integer.MAX_VALUE, false);
            if (!s.isEmpty() && !player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        player.displayClientMessage(Component.translatable("message.singulo.android_workbench.buffer_taken"), true);
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < buffer.getSlots(); i++) {
            Block.popResource(level, worldPosition, buffer.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("buffer", buffer.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("buffer")) {
            buffer.deserializeNBT(registries, tag.getCompound("buffer"));
        }
    }
}
