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
    /** 一波を全滅させたとき、ドローン用クリスタルメモリが出る確率。 */
    public static final float MEMORY_CHANCE = 0.4F;
    private float memoryChance = MEMORY_CHANCE;

    private int tier = 1;
    private final List<UUID> drones = new ArrayList<>();
    private long nextWave;
    /** 今の波でプレイヤーに倒されたドローンと、出撃させた数（全部倒されたときだけメモリが出る）。 */
    private final java.util.Set<UUID> killed = new java.util.HashSet<>();
    private int launched;

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

    /** ドック1個の出撃数。段階1は2機、段階2〜3は3機。 */
    public int waveSize() {
        return Math.min(3, tier + 1);
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
            cleared(level);
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
        killed.clear();
        launched = 0;
        for (int i = 0; i < waveSize(); i++) {
            SecurityDrone drone = SinguloEntities.SECURITY_DRONE.get().create(level);
            if (drone == null) {
                continue;
            }
            double angle = Math.PI * 2 * i / waveSize();
            drone.moveTo(worldPosition.getX() + 0.5 + Math.cos(angle), worldPosition.getY() + 1.5,
                    worldPosition.getZ() + 0.5 + Math.sin(angle), level.random.nextFloat() * 360, 0);
            drone.setTier(tier);
            drone.markFacilitySpawned();                           // 倒すと部品を少し落とす（ドックの個体だけ）
            drone.restrictTo(worldPosition, HOME_RADIUS);
            level.addFreshEntity(drone);
            drones.add(drone.getUUID());
            launched++;
        }
        setChanged();
    }

    /** テスト用: メモリが出る確率を決め打ちにする（1 で必ず、0 で出ない）。 */
    /** ドックのドローンがプレイヤーに倒された。 */
    public void recordKill(UUID id) {
        if (drones.contains(id) && killed.add(id)) {
            setChanged();
        }
    }

    public void setMemoryChanceForTest(float chance) {
        memoryChance = chance;
    }

    /** 一波を全滅させたとき: MEMORY_CHANCE の確率で、ドローン用クリスタルメモリがドックの上に出る。 */
    private void cleared(ServerLevel level) {
        boolean all = launched > 0 && killed.size() >= launched;
        killed.clear();
        launched = 0;
        if (!all || level.random.nextFloat() >= memoryChance) {
            return;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("drone_memory"));
        var top = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition.above());
        var drop = new net.minecraft.world.entity.item.ItemEntity(level, top.x, top.y + 0.2, top.z,
                new net.minecraft.world.item.ItemStack(item), 0, 0.2, 0);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.6F);
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
        ListTag dead = new ListTag();
        for (UUID id : killed) {
            dead.add(NbtUtils.createUUID(id));
        }
        tag.put("killed", dead);
        tag.putInt("launched", launched);
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
        killed.clear();
        for (Tag t : tag.getList("killed", Tag.TAG_INT_ARRAY)) {
            killed.add(NbtUtils.loadUUID(t));
        }
        launched = tag.getInt("launched");
    }
}
