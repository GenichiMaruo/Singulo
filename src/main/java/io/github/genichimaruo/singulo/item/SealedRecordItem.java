package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.ruin.AncientRecords;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 封印記録（封印コンテナの特異点錠から）。右クリックで読むと、封印された記録が1つハンドブックに加わる。 */
public class SealedRecordItem extends SinguloItem {
    public SealedRecordItem(Properties properties, int stage) {
        super(properties, stage, false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            String id = AncientRecords.learnNextHidden(sp);
            if (id == null) {
                sp.sendOverlayMessage(Component.translatable("message.singulo.hidden_records_complete"));
                return io.github.genichimaruo.singulo.compat.Interactions.fail(stack);
            }
            sp.sendSystemMessage(Component.translatable("message.singulo.record_read",
                    Component.translatable("record.singulo." + id + ".title")).withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
            if (!sp.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return io.github.genichimaruo.singulo.compat.Interactions.sidedSuccess(stack, level.isClientSide());
    }
}
