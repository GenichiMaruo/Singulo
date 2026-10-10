package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 野良ブラックホール。ペンローズ・リアクターの炉が、モブなどを投げ込まれて耐えきれずに崩壊したあと、その場に残る。
 * まわりのものを引き寄せ、地平線に触れたものを消して質量にする。壊せないが、同じ場所にリアクターを組み直すと
 * リアクターが取り込んで、その質量のまま稼働を始める（炉心の中心がこのブロックになるように組む）。
 */
public class RogueBlackHoleBlockEntity extends BlockEntity {
    private double mass = PenroseReactorBlockEntity.MAX_MASS;
    private double spin;

    public RogueBlackHoleBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ROGUE_BLACK_HOLE.get(), pos, state);
    }

    public double mass() {
        return mass;
    }

    public double spin() {
        return spin;
    }

    public void setCore(double mass, double spin) {
        this.mass = mass;
        this.spin = spin;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public Vec3 center() {
        return Vec3.atCenterOf(worldPosition);
    }

    /** 事象の地平線の半径（リアクターの炉心と同じ式。崩壊の前後で大きさが変わらない）。 */
    public double horizonRadius() {
        return PenroseReactorBlockEntity.horizonFor(mass);
    }

    /** 引き寄せの強さの倍率（質量5000で稼働中のリアクターと同じ、質量が増えるほど強い）。 */
    public double pullScale() {
        double m = mass / PenroseReactorBlockEntity.MAX_MASS;
        return Math.max(0.4, Math.min(2.5, 0.4 + 0.6 * Math.min(1, m) + 1.0 * Math.max(0, m - 1)));
    }

    /** 逃げられないほど強く引く内側の半径。 */
    public double innerRadius() {
        return PenroseReactorBlockEntity.INNER_RADIUS * (0.6 + 0.4 * pullScale());
    }

    /**
     * ホーキング放射で質量が減る速さ（毎秒）。dm/dt = −K/m² なので寿命は m³/(3K)。
     * 質量5000でおよそ6時間（ゲームが進んでいる時間）で消える。小さいほど速く減り、最後は一気に蒸発する。
     */
    public static final double EVAPORATION_K = Math.pow(PenroseReactorBlockEntity.MAX_MASS, 3) / (3 * 6 * 3600.0);
    /** これを割ると蒸発しきって消える。 */
    public static final double VANISH_MASS = 30;
    private long lastTick = -1;

    public static void serverTick(Level level, BlockPos pos, BlockState state, RogueBlackHoleBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        // ホーキング放射（読み込まれていなかった間の分もまとめて減らす）
        long now = level.getGameTime();
        long dt = lastTick < 0 ? 1 : Math.max(0, Math.min(now - lastTick, 20L * 3600 * 24));
        lastTick = now;
        for (long left = dt; left > 0 && mass > VANISH_MASS; ) {
            long step = Math.min(left, 200);                           // 大きく飛ぶときは少しずつ（最後の加速を飛ばさない）
            mass -= EVAPORATION_K / (mass * mass) * step / 20.0;
            left -= step;
        }
        if (mass <= VANISH_MASS) {
            vanish(level);
            return;
        }
        Vec3 c = center();
        double horizon = horizonRadius();
        boolean kill = !ServerConfig.SPEC.isLoaded() || ServerConfig.EVENT_HORIZON_KILL.get();
        int radius = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_RADIUS.get() : 24;
        double before = mass;
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(worldPosition).inflate(radius), EventHorizonTargets::affected)) {
            if (kill && PenroseReactorBlockEntity.touches(e.getBoundingBox(), c, horizon)) {
                mass += PenroseReactorBlockEntity.foreignMass(e);
                PenroseReactorBlockEntity.returnRecord(level, e, c, horizon);
                EventHorizon.consume(level, e);
                continue;
            }
            if (e instanceof Player) {
                continue;                                              // プレイヤーはクライアントで引く（なめらかに）
            }
            Vec3 v = PenroseReactorBlockEntity.pulledVelocity(e.getDeltaMovement(), e.getBoundingBox().getCenter(), c,
                    innerRadius(), pullScale());
            if (v != null) {
                e.setDeltaMovement(v);
                e.hurtMarked = true;
            }
        }
        if (Math.abs(mass - before) > 0.5 || level.getGameTime() % 40 == 0) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 蒸発しきって消える: まぶしい光と衝撃（ブロックは壊さない）。 */
    private void vanish(ServerLevel level) {
        Vec3 c = center();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.4);
        level.explode(null, c.x, c.y, c.z, 3.0F, Level.ExplosionInteraction.NONE);
        level.removeBlock(worldPosition, false);
    }

    // ------------------------------------------------------------------ クライアント: 自分のプレイヤーへの引力

    private static final Set<RogueBlackHoleBlockEntity> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT_LOADED.remove(this);
        super.setRemoved();
    }

    public static List<RogueBlackHoleBlockEntity> clientLoaded(Level level) {
        List<RogueBlackHoleBlockEntity> out = new java.util.ArrayList<>();
        for (RogueBlackHoleBlockEntity r : CLIENT_LOADED) {
            if (!r.isRemoved() && r.level == level) {
                out.add(r);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 保存と同期

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("mass", mass);
        tag.putDouble("spin", spin);
        tag.putLong("last_tick", lastTick);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("mass")) {
            mass = tag.getDouble("mass");
            spin = tag.getDouble("spin");
        }
        if (tag.contains("last_tick")) {
            lastTick = tag.getLong("last_tick");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("mass", mass);
        tag.putDouble("spin", spin);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
