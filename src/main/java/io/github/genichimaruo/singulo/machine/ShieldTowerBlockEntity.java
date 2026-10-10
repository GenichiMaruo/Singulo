package io.github.genichimaruo.singulo.machine;

import java.util.List;

import net.minecraft.world.entity.player.Player;

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
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.ExplosionEvent;

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
    /** 塔の軸の根元（基壇の上の中心）。形成したときに決まる。ドームと押し返しの中心。 */
    private BlockPos axis;
    /** 放射冠の位置（だれでも壊せ、壊すとシールドが止まる）。 */
    private BlockPos crown;
    /** 許可証のスロット。登録された人がいれば、守りの中で設置・破壊・取り出しができるのはその人たちだけ。 */
    private final net.minecraftforge.items.ItemStackHandler permit = new net.minecraftforge.items.ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return stack.getItem() instanceof io.github.genichimaruo.singulo.item.ShieldPermitItem;
        }

        @Override
        public int getSlotLimit(int s) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final io.github.genichimaruo.singulo.multiblock.PortLinks ports = new io.github.genichimaruo.singulo.multiblock.PortLinks();
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
            var found = io.github.genichimaruo.singulo.multiblock.Shapes.find(
                    io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.SHIELD_TOWER, world, worldPosition);
            formed = found != null;
            BlockPos newAxis = found == null ? null : found.pos(worldPosition, 2, 1, 2);
            crown = found == null ? null : found.pos(worldPosition, 2, 8, 2);
            ports.update(world, worldPosition, found == null ? java.util.List.of() : found.ports());
            if (!java.util.Objects.equals(newAxis, axis) || was != formed) {
                axis = newAxis;
                world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(world, worldPosition, was, formed, firstCheck, 4, 1, 9);
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
        Vec3 c = Vec3.atCenterOf(axis());
        for (Mob mob : world.getEntitiesOfClass(Mob.class, new AABB(axis()).inflate(r),
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

    /** 展開の段階（tick）: 充電 → 光の柱 → 球が降りる → 衝撃波。 */
    public static final int DEPLOY_BEAM = 30, DEPLOY_DOME = 45, DEPLOY_DOME_TICKS = 60, DEPLOY_WAVE = 105, DEPLOY_END = 130;

    /** 守りが始まってからの tick（クライアント。止まっていれば -1）。 */
    public float deployTicks(float partialTick) {
        if (shownProtection <= 0 || getLevel() == null) {
            return -1;
        }
        return getLevel().getGameTime() - activatedAt + partialTick;
    }

    /** 球が降りきった割合（0〜1）。 */
    public float domeProgress(float partialTick) {
        float t = deployTicks(partialTick);
        if (t < 0) {
            return 0;
        }
        return Math.min(1, Math.max(0, (t - DEPLOY_DOME) / DEPLOY_DOME_TICKS));
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("protection", protection);
        tag.putInt("radius", radius());
        tag.putLong("axis", axis().asLong());
        tag.putBoolean("formed", formed);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("permit")) {
            permit.deserializeNBT(tag.getCompound("permit"));
        }
        if (tag.contains("protection")) {
            int p = tag.getInt("protection");
            if (shownProtection == 0 && p > 0 && getLevel() != null) {
                activatedAt = getLevel().getGameTime();
            }
            shownProtection = p;
            shownRadius = tag.getInt("radius");
            axis = BlockPos.of(tag.getLong("axis"));
            formed = tag.getBoolean("formed");
        }
    }

    public boolean covers(Vec3 pos, int minLevel) {
        int r = radius();
        return protection >= minLevel && !isRemoved() && Vec3.atCenterOf(axis()).distanceToSqr(pos) <= (double) r * r;
    }

    @Override
    public net.minecraftforge.items.IItemHandler menuItems() {
        return new net.minecraftforge.items.wrapper.CombinedInvWrapper(slot, permit);
    }

    public net.minecraftforge.items.ItemStackHandler permit() {
        return permit;
    }

    /** 形ができているか（クライアントでは同期された値。描画は形ができてから）。 */
    public boolean shownFormed() {
        return formed;
    }

    /** 放射冠が壊されたら、すぐに形を確かめ直す（シールドが止まる）。 */
    void crownBroken() {
        nextCheck = 0;
    }

    /** 守りの中で、この人が設置・破壊・取り出しを止められるか（許可証に登録された人がいて、その人が入っていない）。 */
    private boolean locks(BlockPos pos, Player player) {
        ItemStack card = permit.getStackInSlot(0);
        if (protection <= 0 || card.isEmpty() || io.github.genichimaruo.singulo.item.ShieldPermitItem.members(card).isEmpty()) {
            return false;
        }
        int r = radius();
        if (Vec3.atCenterOf(axis()).distanceToSqr(Vec3.atCenterOf(pos)) > (double) r * r) {
            return false;
        }
        return !io.github.genichimaruo.singulo.item.ShieldPermitItem.allows(card, player);
    }

    /** pos での操作を、どれかの動いているシールドが許可証で止めるか。 */
    public static boolean locked(Level world, BlockPos pos, Player player) {
        Set<ShieldTowerBlockEntity> set = ACTIVE.get(world);
        if (set == null || player.isSpectator()) {
            return false;
        }
        for (ShieldTowerBlockEntity shield : set) {
            if (shield.locks(pos, player)) {
                return true;
            }
        }
        return false;
    }

    /** pos が動いているシールドの放射冠か。放射冠はだれでも壊せる（壊すとシールドが止まる）。 */
    public static boolean isActiveCrown(Level world, BlockPos pos) {
        Set<ShieldTowerBlockEntity> set = ACTIVE.get(world);
        if (set == null) {
            return false;
        }
        for (ShieldTowerBlockEntity shield : set) {
            if (pos.equals(shield.crown)) {
                return true;
            }
        }
        return false;
    }

    /** 放射冠が壊されたシールドに知らせる。 */
    public static void onCrownBroken(Level world, BlockPos pos) {
        Set<ShieldTowerBlockEntity> set = ACTIVE.get(world);
        if (set != null) {
            for (ShieldTowerBlockEntity shield : List.copyOf(set)) {
                if (pos.equals(shield.crown)) {
                    shield.crownBroken();
                }
            }
        }
    }

    /** 塔の軸の根元（基壇の上の中心）。形がわからないうちはコントローラの位置。 */
    public BlockPos axis() {
        return axis != null ? axis : worldPosition;
    }

    @Override
    public void onChunkUnloaded() {
        ports.onChunkUnloaded();
        super.onChunkUnloaded();
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
        ports.onRemoved(getLevel());
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
        if (shielded(event.getLevel(), event.getExplosion().getPosition(), 4)) {
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
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** シンギュラリティ・コアのシールドの中には敵対モブが湧かない（スポナー・スポーンエッグは除く）。 */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity() instanceof Enemy && event.getSpawner() == null
                && shielded(event.getEntity().level(), new Vec3(event.getX(), event.getY(), event.getZ()), 5)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("permit", permit.serializeNBT());
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        net.minecraft.world.level.block.Block.popResource(level, worldPosition, permit.getStackInSlot(0));
    }
}
