package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * アンドロイド納品口。注文の完成品を、アンドロイドがここへ届ける（作業指令台の注文で、納品先に選ぶ）。
 * 中は27スロットの箱で、右クリックで開ける。画面で名前を付けられる（納品先を選ぶときに出る）。ホッパーなどで取り出せる。
 */
public class DeliveryPortBlockEntity extends WorkLinkedBlockEntity {
    public static final int SLOTS = 27;

    private final SimpleContainer contents = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            DeliveryPortBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return level != null && level.getBlockEntity(worldPosition) == DeliveryPortBlockEntity.this
                    && player.distanceToSqr(worldPosition.getCenter()) < 8 * 8;
        }
    };
    private final IItemHandler handler = new InvWrapper(contents);
    /** 名前（空なら、場所で呼ぶ）。 */
    private String name = "";

    public DeliveryPortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_DELIVERY_PORT.get(), pos, state);
    }

    @Override
    public WorkConsoleBlockEntity.LinkKind linkKind() {
        return WorkConsoleBlockEntity.LinkKind.DELIVERY;
    }

    /** 中身を隣の箱へ出す（0.5秒ごと）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DeliveryPortBlockEntity be) {
        be.eyes.tick(level, pos, state);
        if ((level.getGameTime() + 5) % 10 == 0) {
            SupplyPortBlockEntity.pushOut(level, pos, be.contents);
        }
    }

    public IItemHandler handler() {
        return handler;
    }

    /** 完成品を入れる。入りきらなかった分を返す。 */
    public ItemStack insert(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack, false);
    }

    public String name() {
        return name;
    }

    /** 名前を付ける（空なら場所で呼ぶ）。クライアントにも送る。 */
    public void setName(String name) {
        String n = name == null ? "" : name.strip();
        this.name = n.length() > 32 ? n.substring(0, 32) : n;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected boolean openScreen(ServerPlayer player) {
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return true;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DeliveryPortMenu(id, inv, worldPosition, contents),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
        return true;
    }

    // 名前はクライアントにも送る（画面に出すため）
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("name", name);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, worldPosition, contents.getItem(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) {
            list.set(i, contents.getItem(i));
        }
        tag.put("items", ContainerHelper.saveAllItems(new CompoundTag(), list, registries));
        tag.putString("name", name);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("items"), list, registries);
        for (int i = 0; i < SLOTS; i++) {
            contents.setItem(i, list.get(i));
        }
        name = tag.getString("name");
    }
}
