package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 警備機ドック。近くにプレイヤー（クリエイティブ・観戦を除く）が来ると、警備ドローンを出撃させる。
 * 出撃したドローンが全滅してから1日（24000 tick）経つと、次の出撃ができる。ドローンはドックから20ブロック以内を守る。
 */
public class GuardDockBlockEntity extends BlockEntity {
    public static final int DETECT_RANGE = 16;
    public static final int HOME_RADIUS = 20;
    public static final long COOLDOWN = 24_000;
    static final int CHECK_INTERVAL = 20;

    private int tier = 1;
    private final List<UUID> drones = new ArrayList<>();
    private long nextWave;

    public GuardDockBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.GUARD_DOCK.get(), pos, state);
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Mth.clamp(tier, 1, 3);
        setChanged();
    }

    /** 1回の出撃の数。 */
    public int waveSize() {
        return tier + 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuardDockBlockEntity be) {
        if (level.getGameTime() % CHECK_INTERVAL != 0 || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        boolean hadDrones = !drones.isEmpty();
        drones.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (hadDrones && drones.isEmpty()) {
            nextWave = level.getGameTime() + COOLDOWN;
            setChanged();
        }
        if (!drones.isEmpty() || level.getGameTime() < nextWave) {
            return;
        }
        Player player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, DETECT_RANGE, p -> !p.isSpectator() && !((Player) p).isCreative());
        if (player != null) {
            launch(level);
        }
    }

    /** ドローンを出撃させる。 */
    public void launch(ServerLevel level) {
        for (int i = 0; i < waveSize(); i++) {
            SecurityDrone drone = SinguloEntities.SECURITY_DRONE.get().create(level);
            if (drone == null) {
                continue;
            }
            double angle = Math.PI * 2 * i / waveSize();
            drone.moveTo(worldPosition.getX() + 0.5 + Math.cos(angle), worldPosition.getY() + 1.5,
                    worldPosition.getZ() + 0.5 + Math.sin(angle), level.random.nextFloat() * 360, 0);
            drone.setTier(tier);
            drone.restrictTo(worldPosition, HOME_RADIUS);
            level.addFreshEntity(drone);
            drones.add(drone.getUUID());
        }
        setChanged();
    }

    public int aliveDrones() {
        return drones.size();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("tier", tier);
        tag.putLong("next_wave", nextWave);
        ListTag list = new ListTag();
        for (UUID id : drones) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("drones", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tier = tag.contains("tier") ? Mth.clamp(tag.getInt("tier"), 1, 3) : 1;
        nextWave = tag.getLong("next_wave");
        drones.clear();
        for (Tag t : tag.getList("drones", Tag.TAG_INT_ARRAY)) {
            drones.add(NbtUtils.loadUUID(t));
        }
    }
}
