package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 作業指令台につなぐブロック（資材口・納品口・レシピ棚）の共通部分。
 * 作業指令台を記録した作業位置カードで右クリックすると、その指令台につながる（同じ指令台なら外れる）。
 * 何も持たずにシフト右クリックすると、どの指令台につながっているかをアクションバーに出す。
 */
public abstract class WorkLinkedBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        AndroidEyes.Holder {
    /** 正面の顔（アンドロイドが使っている間は目を開ける）。 */
    protected final AndroidEyes eyes = new AndroidEyes();

    @Override
    public AndroidEyes eyes() {
        return eyes;
    }

    /** つながっている作業指令台（なければ null）。 */
    @Nullable
    private BlockPos console;

    protected WorkLinkedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos console() {
        return console;
    }

    /** つながっている作業指令台（読み込まれていなければ null）。 */
    @Nullable
    public WorkConsoleBlockEntity consoleEntity() {
        return console != null && level != null && level.isLoaded(console)
                && level.getBlockEntity(console) instanceof WorkConsoleBlockEntity c ? c : null;
    }

    /** 開いた後の許可証変更と、指令台未読込の間も権限を維持する。 */
    public boolean mayOperate(net.minecraft.world.entity.player.Player player) {
        if (player.level().isClientSide || console == null) {
            return true;
        }
        if (level != null && level.isLoaded(console) && !(level.getBlockEntity(console) instanceof WorkConsoleBlockEntity)) {
            setConsole(null);                   // 指令台が壊された場合は、永続的な締め出しにしない
            return true;
        }
        WorkConsoleBlockEntity linked = consoleEntity();
        return linked != null && linked.mayOperate(player);
    }

    void setConsole(@Nullable BlockPos console) {
        this.console = console;
        setChanged();
    }

    /** 種類（作業指令台の画面の一覧に出す）。 */
    public abstract WorkConsoleBlockEntity.LinkKind linkKind();

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!WorkLocationCardItem.isCard(stack)) {
            return false;
        }
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return true;
        }
        BlockPos target = WorkLocationCardItem.pos(stack);
        if (target == null || !WorkLocationCardItem.sameDimension(stack, player.level())
                || !(player.level().getBlockEntity(target) instanceof WorkConsoleBlockEntity c)) {
            player.displayClientMessage(Component.translatable("message.singulo.work_link.not_console"), true);
            return true;
        }
        if (!c.mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return true;
        }
        if (target.equals(console)) {
            c.removeLink(worldPosition);
            setConsole(null);
            player.displayClientMessage(Component.translatable("message.singulo.work_link.removed"), true);
            return true;
        }
        if (!target.closerThan(worldPosition, WorkConsoleBlockEntity.LINK_RANGE)) {
            player.displayClientMessage(Component.translatable("message.singulo.work_link.too_far", (int) WorkConsoleBlockEntity.LINK_RANGE), true);
            return true;
        }
        WorkConsoleBlockEntity old = consoleEntity();
        if (old != null) {
            old.removeLink(worldPosition);
        }
        setConsole(target);
        c.addLink(worldPosition);
        player.displayClientMessage(Component.translatable("message.singulo.work_link.added", target.getX(), target.getY(), target.getZ()), true);
        return true;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (player.isShiftKeyDown() || !openScreen(player)) {
            showLink(player);
        }
    }

    /** 画面を開く（画面がなければ false）。 */
    protected boolean openScreen(ServerPlayer player) {
        return false;
    }

    protected void showLink(ServerPlayer player) {
        player.displayClientMessage(console == null ? Component.translatable("message.singulo.work_link.none")
                : Component.translatable("message.singulo.work_link.info", console.getX(), console.getY(), console.getZ()), true);
    }

    /** 壊されたら、指令台の一覧から外れる。 */
    @Override
    public void onBroken(Level level) {
        WorkConsoleBlockEntity c = consoleEntity();
        if (c != null) {
            c.removeLink(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (console != null) {
            tag.put("console", NbtUtils.writeBlockPos(console));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        console = NbtUtils.readBlockPos(tag, "console").orElse(null);
    }
}
