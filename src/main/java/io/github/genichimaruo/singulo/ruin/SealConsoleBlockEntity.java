package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 封印コンソール。触れると守護機ホライズン・ウォーデンを起こす（近くに封鎖された保管庫があるときだけ）。
 * 守護機が倒されると保管庫の力場を消し、挑戦者がいなくなって守護機が封印に戻ると、また触れられる状態に戻る。
 */
public class SealConsoleBlockEntity extends BlockEntity {
    public static final int VAULT_SEARCH_RADIUS = 12;
    public static final int MESSAGE_RADIUS = 32;

    @Nullable
    private UUID warden;

    public SealConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.SEAL_CONSOLE.get(), pos, state);
    }

    /** 近くの、守護機に守られた保管庫。 */
    @Nullable
    public RuinCacheBlockEntity vault(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = VAULT_SEARCH_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (level.getBlockEntity(p) instanceof RuinCacheBlockEntity cache && cache.guardedByWarden()) {
                        return cache;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public HorizonWarden activeWarden(ServerLevel level) {
        if (warden == null) {
            return null;
        }
        Entity e = level.getEntity(warden);
        return e instanceof HorizonWarden w && w.isAlive() ? w : null;
    }

    /**
     * 触れたとき。守護機を起こせたらそれを返す。player は知らせる相手（null でもよい）。
     */
    @Nullable
    public HorizonWarden activate(ServerLevel level, @Nullable ServerPlayer player) {
        if (activeWarden(level) != null) {
            tell(player, "gui.singulo.console.active");
            return null;
        }
        RuinCacheBlockEntity vault = vault(level);
        if (vault == null) {
            tell(player, "gui.singulo.console.no_vault");
            return null;
        }
        // 中身の再生が済んでいれば、ここで入れ直して封鎖する
        vault.refillIfDue(level, level.getGameTime());
        if (!vault.isSealed()) {
            tell(player, "gui.singulo.console.open");
            return null;
        }
        HorizonWarden w = SinguloEntities.HORIZON_WARDEN.get().create(level);
        if (w == null) {
            return null;
        }
        BlockPos spawn = worldPosition.above();
        w.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        w.awaken(worldPosition, worldPosition);
        level.addFreshEntity(w);
        warden = w.getUUID();
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 0.7F);
        broadcast(level, "gui.singulo.console.awakened");
        return w;
    }

    void onWardenDefeated(ServerLevel level) {
        warden = null;
        setChanged();
        RuinCacheBlockEntity vault = vault(level);
        if (vault != null) {
            vault.setSealed(false);
        }
        broadcast(level, "gui.singulo.console.defeated");
    }

    void onWardenReset(ServerLevel level) {
        warden = null;
        setChanged();
        broadcast(level, "gui.singulo.console.reset");
    }

    private void broadcast(ServerLevel level, String key) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(MESSAGE_RADIUS))) {
            p.displayClientMessage(Component.translatable(key), false);
        }
    }

    private static void tell(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (warden != null) {
            tag.putUUID("warden", warden);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        warden = tag.hasUUID("warden") ? tag.getUUID("warden") : null;
    }
}
