package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * 形成済みのマルチブロックは、どの部品を右クリックしてもコントローラーの画面を開く。
 * スニーク中は普通どおり（部品にブロックを置くなど）。
 */
public final class MultiblockInteraction {
    private MultiblockInteraction() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isSecondaryUseActive()) {
            return;
        }
        var level = event.getLevel();
        BlockPos controller = Blueprints.controllerOf(level, event.getPos());
        if (controller == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (event.getEntity() instanceof ServerPlayer player
                && level.getBlockEntity(controller) instanceof AbstractMachineBlock.MenuOpener opener) {
            opener.openMenu(player);
        }
    }
}
