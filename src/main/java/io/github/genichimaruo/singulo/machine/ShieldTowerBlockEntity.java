package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * イベントホライズン・シールド発生塔（ティア5、3×3×9 のマルチブロック。コアは底の中央）。
 * 半径 shieldRadius（既定32）の球の中を守る。電力は半径の二乗に比例する。守れるものは触媒のティアで増える。
 * <ul>
 *   <li>時間結晶触媒（ティア4）: 範囲内で起きた爆発を打ち消し、外からの爆発でも範囲内のブロックとエンティティは壊れない。
 *       範囲内のモブはブロックを壊したり持ち去ったりできない</li>
 *   <li>シンギュラリティ・コア（ティア5）: さらに敵対モブが湧かず、外から入ってきた敵対モブを押し返す（ボスは除く）</li>
 * </ul>
 * 慣性スタビライザー（ティア3、防爆のみ）の完全版。
 */
public class ShieldTowerBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 5;
    /** 電力 = FE_PER_RADIUS_SQ × 半径²（半径32で 16,384 FE/t）。 */
    public static final int FE_PER_RADIUS_SQ = 16;
    static final int CHECK_INTERVAL = 40;
    static final int REPEL_INTERVAL = 5;

    private static final Map<Level, Set<ShieldTowerBlockEntity>> ACTIVE = new WeakHashMap<>();

    /** 0 は停止中、4 は防爆・防荒らし、5 はさらに湧き止めと押し返し。 */
    private int protection;
    private boolean formed;
    private boolean firstCheck = true;
    private long nextCheck;

    public ShieldTowerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.SHIELD_TOWER.get(), pos, state, 2_000_000, 200_000, 0);
    }

    public static int radius() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.SHIELD_RADIUS.get() : 32;
    }

    @Override
    public Kind kind() {
        return Kind.SHIELD_TOWER;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        int r = radius();
        return FE_PER_RADIUS_SQ * r * r;
    }

    public boolean formed() {
        return formed;
    }

    /** 守りの段階（0 は停止中）。 */
    public int protectionLevel() {
        return protection;
    }

    @Override
    protected Status readiness(ServerLevel world) {
        if (world.getGameTime() >= nextCheck) {
            nextCheck = world.getGameTime() + CHECK_INTERVAL;
            boolean was = formed;
            formed = Structures.casingShape(world, worldPosition, Structures.shieldTowerLayout(worldPosition), 0);
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(world, worldPosition, was, formed, firstCheck, 2, 0, 10);
            firstCheck = false;
        }
        return formed ? null : Status.NOT_FORMED;
    }

    @Override
    protected void apply(ServerLevel world, CatalystHelper.Effect effect, boolean active) {
        int now = active ? Math.min(5, CatalystHelper.tierOf(slot.getStackInSlot(0))) : 0;
        if (now != protection) {
            protection = now;
            world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            Set<ShieldTowerBlockEntity> set = ACTIVE.computeIfAbsent(world, l -> Collections.newSetFromMap(new IdentityHashMap<>()));
            if (protection > 0) {
                set.add(this);
            } else {
                set.remove(this);
            }
        }
        if (protection >= 5 && world.getGameTime() % REPEL_INTERVAL == 0) {
            repel(world);
        }
    }

    /** 範囲内の敵対モブを外へ押し出す。 */
    private void repel(ServerLevel world) {
        int r = radius();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        for (Mob mob : world.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(r),
                m -> m instanceof Enemy && !m.getType().is(Tags.EntityTypes.BOSSES) && m.position().distanceToSqr(c) <= (double) r * r)) {
            Vec3 out = mob.position().subtract(c).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
            mob.setDeltaMovement(out.scale(0.8).add(0, 0.2, 0));
            mob.hurtMarked = true;
        }
    }

    @Override
    protected int displayValue() {
        return radius();
    }

    @Override
    protected int extraValue() {
        return protection;
    }

    // ------------------------------------------------------------------ 見た目（クライアント）

    /** クライアントに見えている守りの段階と、ドームが降り始めた時刻。 */
    private int shownProtection;
    private int shownRadius = 32;
    private long activatedAt;

    public int shownProtection() {
        return shownProtection;
    }

    public int shownRadius() {
        return shownRadius;
    }

    /** ドームが降りきった割合（0〜1）。 */
    public float domeProgress(float partialTick) {
        if (shownProtection <= 0 || getLevel() == null) {
            return 0;
        }
        return Math.min(1, (getLevel().getGameTime() - activatedAt + partialTick) / 40F);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("protection", protection);
        tag.putInt("radius", radius());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("protection")) {
            int p = tag.getInt("protection");
            if (shownProtection == 0 && p > 0 && getLevel() != null) {
                activatedAt = getLevel().getGameTime();
            }
            shownProtection = p;
            shownRadius = tag.getInt("radius");
        }
    }

    public boolean covers(Vec3 pos, int minLevel) {
        int r = radius();
        return protection >= minLevel && !isRemoved() && Vec3.atCenterOf(worldPosition).distanceToSqr(pos) <= (double) r * r;
    }

    @Override
    public void setRemoved() {
        if (getLevel() != null) {
            Set<ShieldTowerBlockEntity> set = ACTIVE.get(getLevel());
            if (set != null) {
                set.remove(this);
            }
        }
        protection = 0;
        super.setRemoved();
    }

    /** その位置を、指定の段階以上のシールドが守っているか。 */
    public static boolean shielded(Level world, Vec3 pos, int minLevel) {
        Set<ShieldTowerBlockEntity> set = ACTIVE.get(world);
        if (set == null || set.isEmpty()) {
            return false;
        }
        for (ShieldTowerBlockEntity s : set) {
            if (s.covers(pos, minLevel)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ イベント

    /** 範囲内で起きた爆発は始まる前に打ち消す。 */
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (shielded(event.getLevel(), event.getExplosion().center(), 4)) {
            event.setCanceled(true);
        }
    }

    /** 外からの爆発でも、範囲内のブロックとエンティティは巻き込まない。 */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level world = event.getLevel();
        if (!ACTIVE.containsKey(world) || ACTIVE.get(world).isEmpty()) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> shielded(world, Vec3.atCenterOf(pos), 4));
        event.getAffectedEntities().removeIf(e -> shielded(world, e.position(), 4));
    }

    /** 範囲内のモブはブロックを荒らせない。 */
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() != null && shielded(event.getEntity().level(), event.getEntity().position(), 4)) {
            event.setCanGrief(false);
        }
    }

    /** シンギュラリティ・コアのシールドの中には敵対モブが湧かない（スポナー・スポーンエッグは除く）。 */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity() instanceof Enemy && event.getSpawner() == null
                && shielded(event.getEntity().level(), new Vec3(event.getX(), event.getY(), event.getZ()), 5)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
