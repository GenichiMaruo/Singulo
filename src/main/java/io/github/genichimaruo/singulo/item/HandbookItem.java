package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Singulo ハンドブック。右クリックで手引き書の画面を開く。初めてワールドに入ったときに1冊もらえる。 */
public class HandbookItem extends SinguloItem {
    private static final String GIVEN = "singulo_handbook_given";

    public HandbookItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            io.github.genichimaruo.singulo.client.HandbookScreen.open();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** 初めて入ったプレイヤーにハンドブックを渡す。 */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.getInventory().placeItemBackInInventory(new ItemStack(SinguloItems.HANDBOOK.get()));
        player.displayClientMessage(Component.translatable("message.singulo.welcome"), false);
    }
}
