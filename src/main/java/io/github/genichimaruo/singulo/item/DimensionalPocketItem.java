package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 次元ポケット（封印コンテナの特異点錠から）。右クリックで、どこからでも自分だけの収納（27枠）を開く。
 * 中身はアイテムではなくプレイヤーに付いている（ポケットを失くしても、死んでも消えない。別のポケットでも同じ中身）。
 */
public class DimensionalPocketItem extends SinguloItem {
    public static final int SIZE = 27;
    public static final String CONTENTS = "singulo:dimensional_pocket";

    public DimensionalPocketItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Pocket pocket = new Pocket(sp);
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, pocket),
                    Component.translatable("container.singulo.dimensional_pocket")));
            level.playSound(null, player.blockPosition(), SinguloSounds.get("dimensional_pocket.open"), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** プレイヤーに付いた中身を写した入れ物。変わるたびに書き戻す。 */
    public static final class Pocket extends SimpleContainer {
        private final ServerPlayer player;
        private boolean loading;

        public Pocket(ServerPlayer player) {
            super(SIZE);
            this.player = player;
            loading = true;
            List<ItemStack> saved = io.github.genichimaruo.singulo.compat.PlayerData.get(player, CONTENTS, ItemStack.CODEC.listOf(), new ArrayList<>());
            for (int i = 0; i < SIZE && i < saved.size(); i++) {
                setItem(i, saved.get(i).copy());
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) {
                List<ItemStack> out = new ArrayList<>(SIZE);
                for (int i = 0; i < SIZE; i++) {
                    out.add(getItem(i).copy());
                }
                io.github.genichimaruo.singulo.compat.PlayerData.set(player, CONTENTS, ItemStack.CODEC.listOf(), out);
            }
        }

        @Override
        public void stopOpen(Player p) {
            super.stopOpen(p);
            setChanged();
            p.level().playSound(null, p.blockPosition(), SinguloSounds.get("dimensional_pocket.close"), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }
}
