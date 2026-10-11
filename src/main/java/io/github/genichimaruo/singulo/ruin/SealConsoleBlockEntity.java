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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 封印コンソール。触れると守護機ホライズン・ウォーデンを起こす（近くに封鎖された保管庫があるときだけ）。
 * 守護機が倒されると保管庫の力場を消し、挑戦者がいなくなって守護機が封印に戻ると、また触れられる状態に戻る。
 * <p>
 * 攻略後の再戦: 覚醒の記録核を手に持って使うと、保管庫が開いていても覚醒した守護機を呼び出す（記録核を1個使う）。
 * <p>
 * 隠しボス: 終焉の記録を手に持って使うと、最後の記録者が目覚める（{@link #activateArchivist}）。
 */
public class SealConsoleBlockEntity extends BlockEntity {
    public static final int VAULT_SEARCH_RADIUS = 12;
    public static final int MESSAGE_RADIUS = 32;

    @Nullable
    private UUID warden;
    @Nullable
    private UUID archivist;

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
                    if (level.getBlockEntity(p) instanceof RuinCacheBlockEntity cache && cache.guarded() && "final_lab".equals(cache.ruin())) {
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
        if (activeWarden(level) != null || activeArchivist(level) != null) {
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
        w.startEmerging();
        level.addFreshEntity(w);
        warden = w.getUUID();
        setChanged();
        broadcast(level, "gui.singulo.console.awakened");
        return w;
    }

    /** 覚醒の記録核（覚醒した守護機を呼び出す）。 */
    public static net.minecraft.world.item.Item awakeningCore() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("awakening_record_core"));
    }

    /**
     * 覚醒の記録核を使ったとき。覚醒した守護機を起こせたらそれを返す（呼んだ側が記録核を1個減らす）。
     * 保管庫が封鎖されていてもいなくても呼べる（勝てば封鎖も解ける）。
     */
    @Nullable
    public HorizonWarden activateAwakened(ServerLevel level, @Nullable ServerPlayer player) {
        if (activeWarden(level) != null || activeArchivist(level) != null) {
            tell(player, "gui.singulo.console.active");
            return null;
        }
        RuinCacheBlockEntity vault = vault(level);
        if (vault == null) {
            tell(player, "gui.singulo.console.no_vault");
            return null;
        }
        // 通常戦と同じく、中身の再生期限が過ぎていればここで入れ直して封鎖を確定させる（勝てばその封鎖が解ける）。
        // これをしないと、勝ったあとに保管庫を開こうとした時点で入れ直しが起きて、また封鎖されてしまう
        vault.refillIfDue(level, level.getGameTime());
        HorizonWarden w = SinguloEntities.HORIZON_WARDEN.get().create(level);
        if (w == null) {
            return null;
        }
        BlockPos spawn = worldPosition.above();
        w.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        w.awaken(worldPosition, worldPosition, true);
        w.startEmerging();
        level.addFreshEntity(w);
        warden = w.getUUID();
        setChanged();
        broadcast(level, "gui.singulo.console.awakened_rematch");
        return w;
    }

    // ------------------------------------------------------------------ 隠しボス: 最後の記録者

    /** 終焉の記録（4つの断章から作る。最後の記録者を呼び出す）。 */
    public static net.minecraft.world.item.Item terminusRecord() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("terminus_record"));
    }

    @Nullable
    public LastArchivist activeArchivist(ServerLevel level) {
        if (archivist == null) {
            return null;
        }
        Entity e = level.getEntity(archivist);
        return e instanceof LastArchivist a && a.isAlive() ? a : null;
    }

    /**
     * 終焉の記録を使ったとき。最後の記録者を起こせたらそれを返す（呼んだ側が記録を1個減らす）。
     * 守護機と同時には呼べない。保管庫が開いていても呼べる（保管庫の封鎖には関わらない）。
     */
    @Nullable
    public LastArchivist activateArchivist(ServerLevel level, @Nullable ServerPlayer player) {
        if (activeWarden(level) != null || activeArchivist(level) != null) {
            tell(player, "gui.singulo.console.active");
            return null;
        }
        if (vault(level) == null) {
            tell(player, "gui.singulo.console.no_vault");
            return null;
        }
        LastArchivist a = SinguloEntities.LAST_ARCHIVIST.get().create(level);
        if (a == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 at = LastArchivist.spawnAt(worldPosition);
        a.moveTo(at.x, at.y, at.z, 180, 0);
        a.awaken(worldPosition);
        a.startEmerging();
        level.addFreshEntity(a);
        archivist = a.getUUID();
        setChanged();
        return a;
    }

    void onArchivistDefeated(ServerLevel level) {
        archivist = null;
        setChanged();
    }

    void onArchivistReset(ServerLevel level) {
        archivist = null;
        setChanged();
        broadcast(level, "gui.singulo.archivist.reset");
    }

    void onWardenDefeated(ServerLevel level) {
        warden = null;
        setChanged();
        RuinCacheBlockEntity vault = vault(level);
        if (vault != null) {
            vault.setSealed(false);
            level.playSound(null, vault.getBlockPos(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("vault.unseal"), net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 1.0F);
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
        if (archivist != null) {
            tag.putUUID("archivist", archivist);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        warden = tag.hasUUID("warden") ? tag.getUUID("warden") : null;
        archivist = tag.hasUUID("archivist") ? tag.getUUID("archivist") : null;
    }
}
