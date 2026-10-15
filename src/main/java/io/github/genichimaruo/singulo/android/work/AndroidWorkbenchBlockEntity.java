package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * アンドロイド作業台。アンドロイドが作業台のレシピを作る場所（バニラの作業台より速く作る）。
 * プレイヤーには画面がなく、右クリックで使い方を知らせる。
 */
public class AndroidWorkbenchBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    /** ここで作ると、1回あたりの時間がこの割合になる（バニラの作業台 2秒 → 0.5秒）。 */
    public static final int CRAFT_TICKS = 10;

    public AndroidWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_WORKBENCH.get(), pos, state);
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.singulo.android_workbench.hint"), true);
    }
}
