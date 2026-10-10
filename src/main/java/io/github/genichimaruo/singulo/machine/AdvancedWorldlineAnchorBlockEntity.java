package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ワールドライン・アンカー（上位、ティア4）。半径はティア − 1 チャンク（ボース凝縮触媒で2、時間結晶触媒で3、
 * シンギュラリティ・コアで4）。
 * <p>
 * シンギュラリティ・コアを手に持って右クリックを2回（5秒以内）すると、コアを埋め込む。埋め込んだアンカーは触媒も電力も
 * 使わずに最大半径（5）を保ち続ける。コアは二度と取り出せず、アンカーを壊しても戻らない（一度きりの選択）。
 */
public class AdvancedWorldlineAnchorBlockEntity extends WorldlineAnchorBlockEntity {
    public static final int MACHINE_TIER = 4;
    public static final int MAX_RADIUS = 4;
    public static final int EMBEDDED_RADIUS = 5;
    static final int CONFIRM_TICKS = 100;

    private boolean embedded;
    private long confirmUntil;

    public AdvancedWorldlineAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORLDLINE_ANCHOR_ADVANCED.get(), pos, state);
    }

    public boolean embedded() {
        return embedded;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int maxRadius() {
        return MAX_RADIUS;
    }

    @Nullable
    @Override
    protected CatalystHelper.Effect permanentEffect() {
        return embedded ? new CatalystHelper.Effect(true, 1, 0) : null;
    }

    @Override
    protected int activeRadius() {
        return embedded ? cappedRadius(EMBEDDED_RADIUS) : super.activeRadius();
    }

    @Override
    protected int extraValue() {
        return embedded ? 1 : 0;
    }

    public static boolean isCore(ItemStack stack) {
        return CatalystHelper.tierOf(stack) == 5;
    }

    /** コアを埋め込む。すでに埋め込み済みなら何もしない。 */
    public boolean embed(Level level) {
        if (embedded) {
            return false;
        }
        embedded = true;
        // 入っていた触媒は外に出す（埋め込み後は使わない）
        net.minecraft.world.level.block.Block.popResource(level, worldPosition, slot.getStackInSlot(0));
        slot.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();
        return true;
    }

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!isCore(stack)) {
            return false;
        }
        if (embedded) {
            player.displayClientMessage(Component.translatable("gui.singulo.anchor.already_embedded"), true);
            return true;
        }
        long now = player.level().getGameTime();
        if (now > confirmUntil) {
            confirmUntil = now + CONFIRM_TICKS;
            player.displayClientMessage(Component.translatable("gui.singulo.anchor.confirm_embed"), true);
            return true;
        }
        confirmUntil = 0;
        embed(player.level());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(player.level(), worldPosition, "advanced_worldline_anchor_embed", 1.5F, 1.0F);
        player.displayClientMessage(Component.translatable("gui.singulo.anchor.embedded"), true);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("embedded", embedded);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        embedded = tag.getBoolean("embedded");
    }
}
