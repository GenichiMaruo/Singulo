package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 遺構の印刷台。1つの型（光刃型など。置かれたときに決まる）のアンドロイド専用。
 * 近くにプレイヤー（クリエイティブ・観戦を除く）が来ると、2〜3体を1体ずつ台の上に印刷する。
 * アンドロイドは充電が切れるとこの台へ戻って充電する。全部倒すと、1日（24000 tick）休む。
 */
public class AndroidFabricatorBlockEntity extends BlockEntity {
    public static final int DETECT_RANGE = 16;
    public static final long COOLDOWN = 24_000;
    /** 1体の印刷が終わってから、次の印刷を始めるまで（台の上が空いていれば、すぐ）。 */
    static final int NEXT_PRINT_DELAY = 40;

    @Nullable
    private AndroidKind kind;
    private final List<UUID> androids = new ArrayList<>();
    private boolean waveActive;
    private int waveSize;
    private int printed;
    private long lastPrint;
    private long nextWave;

    public AndroidFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_FABRICATOR.get(), pos, state);
    }

    @Nullable
    public AndroidKind kind() {
        return kind;
    }

    public void setKind(@Nullable AndroidKind kind) {
        this.kind = kind;
        setChanged();
    }

    /** 今の出撃で出ているアンドロイド（生きているもの）の数。 */
    public int alive() {
        return androids.size();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AndroidFabricatorBlockEntity be) {
        if (level.getGameTime() % 5 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (kind == null) {
            AndroidKind[] all = AndroidKind.values();
            setKind(all[level.random.nextInt(all.length)]);
        }
        androids.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        long now = level.getGameTime();
        if (waveActive) {
            if (printed < waveSize) {
                if (now - lastPrint >= RuinAndroid.PRINT_TICKS + NEXT_PRINT_DELAY || topClear(level) && now - lastPrint >= RuinAndroid.PRINT_TICKS) {
                    print(level);
                }
            } else if (androids.isEmpty()) {
                waveActive = false;
                nextWave = now + COOLDOWN;
                cleared(level);
                setChanged();
            }
            return;
        }
        if (now < nextWave) {
            return;
        }
        Player player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5,
                DETECT_RANGE, p -> !p.isSpectator() && !((Player) p).isCreative());
        if (player != null) {
            waveActive = true;
            waveSize = 2 + level.random.nextInt(2);
            printed = 0;
            lastPrint = now - RuinAndroid.PRINT_TICKS - NEXT_PRINT_DELAY;
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.5F);
            setChanged();
        }
    }

    /** 台の上に、まだ立っているアンドロイドがいないか。 */
    private boolean topClear(ServerLevel level) {
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        return level.getEntitiesOfClass(RuinAndroid.class, new net.minecraft.world.phys.AABB(top, top).inflate(0.6, 1, 0.6)).isEmpty();
    }

    /** 1体を印刷し始める。 */
    private void print(ServerLevel level) {
        if (kind == null) {
            return;
        }
        RuinAndroid a = kind.type().create(level);
        if (a == null) {
            return;
        }
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        a.moveTo(top.x, top.y, top.z, level.random.nextFloat() * 360, 0);
        a.setHome(worldPosition);
        a.startPrinting();
        level.addFreshEntity(a);
        androids.add(a.getUUID());
        printed++;
        lastPrint = level.getGameTime();
        setChanged();
    }

    /** 全部倒されたとき: この型の設計メモリ（小型クリスタルメモリ）が台の上に出る。 */
    private void cleared(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        if (kind == null) {
            return;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(kind.memoryId()));
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level, top.x, top.y + 0.2, top.z,
                new net.minecraft.world.item.ItemStack(item), 0, 0.2, 0);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (kind != null) {
            tag.putString("kind", kind.id);
        }
        ListTag list = new ListTag();
        for (UUID id : androids) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("androids", list);
        tag.putBoolean("wave", waveActive);
        tag.putInt("wave_size", waveSize);
        tag.putInt("printed", printed);
        tag.putLong("next_wave", nextWave);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = tag.contains("kind") ? AndroidKind.byId(tag.getString("kind")) : null;
        androids.clear();
        for (Tag t : tag.getList("androids", Tag.TAG_INT_ARRAY)) {
            androids.add(NbtUtils.loadUUID(t));
        }
        waveActive = tag.getBoolean("wave");
        waveSize = tag.getInt("wave_size");
        printed = tag.getInt("printed");
        nextWave = tag.getLong("next_wave");
    }
}
