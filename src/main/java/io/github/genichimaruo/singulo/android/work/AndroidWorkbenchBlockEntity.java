package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
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
 * 預かっている間は、台の上にその物が浮かんで回る（クライアントへは、見せる1つだけ送る）。
 * <p>段階（{@link #tier}）が上の台ほど速く作り、預かり箱も大きく、アンドロイドの充電の減りも少ない: 段階2 は4倍速・27枠、
 * 段階3（改良型）は8倍速・54枠・減り6割、段階4（量子）は20倍速・81枠・減り3割。上の段階の台は、前の段階の台を素材にして作る。
 */
public class AndroidWorkbenchBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        AndroidEyes.Holder {
    /** 正面の顔（アンドロイドが使っている間は目を開ける）。 */
    protected final AndroidEyes eyes = new AndroidEyes();

    @Override
    public AndroidEyes eyes() {
        return eyes;
    }

    /** 段階2の台で作る1回の時間（バニラの作業台 2秒 → 0.5秒）。 */
    public static final int CRAFT_TICKS = 10;
    /** 段階2の台の預かり箱の枠の数。 */
    public static final int BUFFER_SLOTS = 27;

    /** アンドロイド作業台の段階（2〜4。アンドロイド作業台でなければ 0）。 */
    public static int tier(Block block) {
        if (block == SinguloBlocks.QUANTUM_ANDROID_WORKBENCH.get()) {
            return 4;
        }
        if (block == SinguloBlocks.ADVANCED_ANDROID_WORKBENCH.get()) {
            return 3;
        }
        return block == SinguloBlocks.ANDROID_WORKBENCH.get() ? 2 : 0;
    }

    /** その台で作る1回の時間（段階2 は 0.5秒、段階3 は 0.25秒、段階4 は 0.1秒）。アンドロイド作業台でなければ -1。 */
    public static int craftTicks(Block block) {
        return switch (tier(block)) {
            case 4 -> 2;
            case 3 -> 5;
            case 2 -> CRAFT_TICKS;
            default -> -1;
        };
    }

    /**
     * その台で作るときの、音と火花の間隔（段階2 は 0.5秒、段階3 は 0.4秒、段階4 は 0.3秒。上の段階ほど少しずつ速い）。
     * 作る速さは {@link #craftTicks} のまま: 1回の音で、そのあいだに作れる回数をまとめて作る（段階3 は約1.6回、段階4 は3回）。
     */
    public static int beatTicks(Block block) {
        return switch (tier(block)) {
            case 4 -> 6;
            case 3 -> 8;
            case 2 -> CRAFT_TICKS;
            default -> -1;
        };
    }

    /** その台で1回作るときの充電の減り（段階2 は 10、段階3 は 6、段階4 は 3。上の段階ほど少ない）。アンドロイド作業台でなければ -1。 */
    public static int craftCost(Block block) {
        return switch (tier(block)) {
            case 4 -> 3;
            case 3 -> 6;
            case 2 -> 10;
            default -> -1;
        };
    }

    /** その台の預かり箱の枠の数（段階2 は 27、段階3 は 54、段階4 は 81）。 */
    public static int bufferSlots(Block block) {
        return BUFFER_SLOTS * Math.max(1, tier(block) - 1);
    }

    private final ItemStackHandler buffer;
    /** 台の上に見せる物（預かり箱の最初の物。空なら預かっていない）。クライアントでも分かる。 */
    private ItemStack shown = ItemStack.EMPTY;

    /** 台の上に浮かべて見せる物（預かっていなければ空）。 */
    public ItemStack shown() {
        return shown;
    }

    private ItemStack firstStored() {
        for (int i = 0; i < buffer.getSlots(); i++) {
            if (!buffer.getStackInSlot(i).isEmpty()) {
                return buffer.getStackInSlot(i);
            }
        }
        return ItemStack.EMPTY;
    }

    // 見せる物はクライアントにも送る
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!firstStored().isEmpty()) {
            tag.put("shown", firstStored().copyWithCount(1).save(registries));
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public AndroidWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_WORKBENCH.get(), pos, state);
        buffer = new ItemStackHandler(bufferSlots(state.getBlock())) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                ItemStack now = firstStored();
                if (level != null && !level.isClientSide && !ItemStack.isSameItemSameComponents(now, shown)) {
                    shown = now.copyWithCount(1);
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        };
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
        shown = tag.contains("shown") ? ItemStack.parseOptional(registries, tag.getCompound("shown")) : firstStored().copyWithCount(1);
    }
}
