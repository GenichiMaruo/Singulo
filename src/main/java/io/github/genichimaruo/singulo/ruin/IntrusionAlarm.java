package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 前室の中継器がそろう前に、壁を越えてボス部屋へ入り込まれたときの警報（最終実験施設のドーム・沈んだ炉心区画の球）。
 * <ol>
 *   <li>閉じた隔壁の内側に挑戦者がいると警報が鳴る。部屋の灯がすべて赤い警告灯に変わり、報酬を持たない体力
 *       {@value #HEALTH_MULTIPLIER} 倍の番人（排除モード）が現れ、隔壁が開いて外へ出るよう告げる</li>
 *   <li>挑戦者が全員外へ出ると隔壁が閉じ、灯が戻り、排除モードの番人は封印へ戻る。壊されていた耐圧殻と耐圧ガラスは、
 *       遺構の型紙のとおりに作り直す（ほかのブロックは戻さない）</li>
 * </ol>
 * 排除モードの番人は倒しても何も落とさず、保管庫の封鎖も解けず、撃破の記録にもならない。中継器がそろって隔壁が開いて
 * いれば、警報は鳴らない（警報の途中でそろったら、隔壁は開いたままにする）。
 */
public final class IntrusionAlarm {
    /** 排除モードの番人の体力の倍率（通常の番人の体力に対して）。 */
    public static final float HEALTH_MULTIPLIER = 20;
    /** 調べる間（tick）と、警報を繰り返す間（tick）。 */
    static final int CHECK_INTERVAL = 10, REPEAT_INTERVAL = 40;
    static final int MESSAGE_RADIUS = 48;

    /** 警報を鳴らす側（封印コンソール・封印核）。 */
    public interface Host {
        /** 排除モードの番人を出す（出せなければ null）。 */
        @Nullable
        net.minecraft.world.entity.LivingEntity spawnIntruderGuard(ServerLevel level);

        /** 排除モードの番人を封印へ戻す（倒されていれば覚えているのを消すだけ）。 */
        void dismissIntruderGuard(ServerLevel level);

        /** 保存する状態が変わった。 */
        void alarmChanged();
    }

    private final boolean dome;
    private boolean active;
    private long startedAt;

    IntrusionAlarm(boolean dome) {
        this.dome = dome;
    }

    public boolean active() {
        return active;
    }

    /** 封印コンソール・封印核の tick から毎回呼ぶ（{@value #CHECK_INTERVAL} tick ごとに調べる）。 */
    void tick(ServerLevel level, BlockPos core, Host host) {
        if (level.getGameTime() % CHECK_INTERVAL == 0) {
            check(level, core, host);
        }
    }

    /** 侵入を調べて、警報を始める・続ける・終える（テストからも呼ぶ）。 */
    public void check(ServerLevel level, BlockPos core, Host host) {
        if (!PressureRoomSeal.pressureRoom(level, core, dome)) return;
        BlockPos gate = PressureRoomSeal.gate(level, core, dome);
        if (gate == null) return;
        List<ServerPlayer> inside = intruders(level, core);
        if (!active) {
            if (!inside.isEmpty() && PressureRoomSeal.closed(level, gate) && RuinRelayBlockEntity.facilityLock(level, core) != null) {
                start(level, core, gate, host, inside);
            }
            return;
        }
        boolean relaysDone = RuinRelayBlockEntity.relaysComplete(level, core);
        if (relaysDone || inside.isEmpty() && !inDoorway(level, gate)) {
            finish(level, core, gate, host, !relaysDone);
            return;
        }
        if ((level.getGameTime() - startedAt) % REPEAT_INTERVAL == 0) {
            level.playSound(null, gate, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 0.6F);
            level.playSound(null, core.above(4), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 0.5F);
            for (ServerPlayer p : inside) {
                p.displayClientMessage(Component.translatable("gui.singulo.intrusion.warning"), true);
            }
        }
    }

    private void start(ServerLevel level, BlockPos core, BlockPos gate, Host host, List<ServerPlayer> inside) {
        active = true;
        startedAt = level.getGameTime();
        host.alarmChanged();
        setLights(level, core, true);
        if (PressureRoomSeal.setGate(level, gate, true) > 0) {
            level.playSound(null, gate, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
        host.spawnIntruderGuard(level);
        level.playSound(null, core.above(4), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0F, 0.6F);
        for (ServerPlayer p : inside) {
            p.displayClientMessage(Component.translatable("gui.singulo.intrusion.alarm"), false);
            p.displayClientMessage(Component.translatable("gui.singulo.intrusion.warning"), true);
        }
    }

    private void finish(ServerLevel level, BlockPos core, BlockPos gate, Host host, boolean close) {
        active = false;
        host.alarmChanged();
        setLights(level, core, false);
        host.dismissIntruderGuard(level);
        if (close) {
            restoreHull(level, core, gate, dome);
        }
        if (close && PressureRoomSeal.setGate(level, gate, false) > 0) {
            level.playSound(null, gate, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.5F);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(gate).inflate(MESSAGE_RADIUS))) {
                p.displayClientMessage(Component.translatable("gui.singulo.intrusion.cleared"), false);
            }
        }
    }

    /** 遺構ごとの、型紙の中の耐圧殻・耐圧ガラス（封印コンソール・封印核からの位置と状態。型紙の向きのまま）。 */
    private static final java.util.Map<String, List<java.util.Map.Entry<BlockPos, BlockState>>> HULLS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ボス部屋の耐圧殻と耐圧ガラスを、遺構の型紙のとおりに作り直す（違っている所だけ）。型紙では隔壁が封印コンソール・
     * 封印核の南にあるので、実際の隔壁の向きから回し方を決める。作り直した数を返す。
     */
    static int restoreHull(ServerLevel level, BlockPos core, BlockPos gate, boolean dome) {
        String ruin = dome ? "final_lab" : "sunken_reactor";
        List<java.util.Map.Entry<BlockPos, BlockState>> hull = HULLS.computeIfAbsent(ruin, id -> hullOf(level, id, dome));
        net.minecraft.core.Direction toGate = net.minecraft.core.Direction.getNearest(gate.getX() - core.getX(), 0, gate.getZ() - core.getZ());
        net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.NONE;
        for (net.minecraft.world.level.block.Rotation r : net.minecraft.world.level.block.Rotation.values()) {
            if (r.rotate(net.minecraft.core.Direction.SOUTH) == toGate) {
                rotation = r;
            }
        }
        int restored = 0;
        for (var entry : hull) {
            BlockPos at = core.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                    entry.getKey(), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO));
            BlockState state = entry.getValue().rotate(level, at, rotation);
            if (level.isLoaded(at) && !level.getBlockState(at).equals(state)) {
                level.setBlock(at, state, Block.UPDATE_ALL);
                restored++;
            }
        }
        return restored;
    }

    private static List<java.util.Map.Entry<BlockPos, BlockState>> hullOf(ServerLevel level, String ruin, boolean dome) {
        var template = level.getStructureManager().get(io.github.genichimaruo.singulo.Singulo.id("ruins/" + ruin));
        if (template.isEmpty()) {
            return List.of();
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        Block coreBlock = dome ? SinguloBlocks.SEAL_CONSOLE.get() : SinguloBlocks.GUARDIAN_CORE.get();
        var cores = template.get().filterBlocks(BlockPos.ZERO, settings, coreBlock);
        if (cores.isEmpty()) {
            return List.of();
        }
        BlockPos origin = cores.get(0).pos();
        List<java.util.Map.Entry<BlockPos, BlockState>> out = new java.util.ArrayList<>();
        for (Block block : new Block[] {SinguloBlocks.PRESSURE_HULL.get(), SinguloBlocks.PRESSURE_GLASS.get()}) {
            for (var info : template.get().filterBlocks(BlockPos.ZERO, settings, block)) {
                out.add(java.util.Map.entry(info.pos().subtract(origin), info.state()));
            }
        }
        return List.copyOf(out);
    }

    /** 閉じた隔壁の内側にいる挑戦者（生きていて、創造・観戦モードでない）。 */
    List<ServerPlayer> intruders(ServerLevel level, BlockPos core) {
        int r = PressureRoomSeal.reach(dome) + 2;
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(core).inflate(r, 30, r),
                p -> p.isAlive() && !ArenaShield.exempt(p) && PressureRoomSeal.inside(core, dome, p.position()));
    }

    /** 隔壁のマスに誰か立っているか（閉じると挟まるので待つ）。 */
    private static boolean inDoorway(ServerLevel level, BlockPos gate) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(gate.offset(-1, 0, -1).getCenter(), gate.offset(1, 3, 1).getCenter()).inflate(0.5)).isEmpty();
    }

    /** 部屋の灯（遺構のランプ）を赤い警告灯に替える・戻す。 */
    static void setLights(ServerLevel level, BlockPos core, boolean alarm) {
        boolean dome = level.getBlockState(core).is(SinguloBlocks.SEAL_CONSOLE.get());
        Block from = alarm ? SinguloBlocks.RUIN_LAMP.get() : SinguloBlocks.RUIN_LAMP_CRIMSON.get();
        BlockState to = (alarm ? SinguloBlocks.RUIN_LAMP_CRIMSON.get() : SinguloBlocks.RUIN_LAMP.get()).defaultBlockState();
        int r = dome ? 18 : 23;
        int floor = PressureRoomSeal.floor(core, dome);
        for (BlockPos p : BlockPos.betweenClosed(core.getX() - r, floor - 1, core.getZ() - r, core.getX() + r, floor + (dome ? 20 : 28),
                core.getZ() + r)) {
            if (level.getBlockState(p).is(from) && PressureRoomSeal.shell(core, dome, p)) {
                level.setBlock(p, to, Block.UPDATE_ALL);
            }
        }
    }

    void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("intrusion_alarm", active);
        tag.putLong("intrusion_started", startedAt);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        active = tag.getBoolean("intrusion_alarm");
        startedAt = tag.getLong("intrusion_started");
    }
}
