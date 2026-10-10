package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ケラウノス放電塔。ためた電力で人工の雷を起こし、RANGE ブロック以内のいちばん近い避雷針へ撃つ。
 * 避雷針が砂の上に立っていれば、その砂が雷ガラス塊になる（雷雨を待たずに雷ガラスを作れる）。
 * 撃ち方は3つ（スニークして右クリックで切り替える）: 手動（右クリック）・赤石（信号が入った瞬間）・自動（電力がたまったら勝手に撃つ）。
 * どのモードでも右クリックで撃てる。CHARGE_TICKS のあいだ放電球に力をためてから（描画: KeraunosTowerRenderer）、
 * 先端から避雷針へ雷が飛ぶ。1回に COST FE を使い、撃ったあと COOLDOWN tick は撃てない。
 */
public class KeraunosTowerBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    /** 撃ち方。 */
    public enum Mode {
        MANUAL, REDSTONE, AUTO
    }

    public static final int RANGE = 16;
    public static final int COST = 1_000_000;
    public static final int CHARGE_TICKS = 40;
    public static final int BOLT_TICKS = 12;
    public static final int COOLDOWN = 60;
    /** 放電球の中心（ブロックの底からの高さ）。 */
    public static final double ORB_HEIGHT = 1.35;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(4_000_000, 200_000, 0, this::setChanged);
    /** ため始めた時刻（-1 はためていない）と、雷を撃った時刻。 */
    private long chargeStart = -1;
    private long boltAt = -1000;
    @Nullable
    private BlockPos target;
    private boolean powered;
    private Mode mode = Mode.MANUAL;

    public KeraunosTowerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.KERAUNOS_TOWER.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setChanged();
    }

    public long chargeStart() {
        return chargeStart;
    }

    public long boltAt() {
        return boltAt;
    }

    @Nullable
    public BlockPos target() {
        return target;
    }

    /** いちばん近い避雷針（なければ null）。 */
    @Nullable
    public BlockPos findRod() {
        if (level == null) {
            return null;
        }
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (level.getBlockState(p).is(Blocks.LIGHTNING_ROD)) {
                // 重ねた避雷針は、いちばん上を狙う
                if (level.getBlockState(p.above()).is(Blocks.LIGHTNING_ROD)) {
                    continue;
                }
                double d = p.distSqr(worldPosition);
                if (d < bestSq) {
                    bestSq = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /** 撃つ（ため始める）。始めたら true。player は知らせる相手（赤石なら null）。 */
    public boolean fire(@Nullable ServerPlayer player) {
        if (level == null || chargeStart >= 0 || level.getGameTime() - boltAt < COOLDOWN) {
            tell(player, "busy");
            return false;
        }
        BlockPos rod = findRod();
        if (rod == null) {
            tell(player, "no_rod");
            return false;
        }
        if (!energy.consume(COST)) {
            tell(player, "no_power");
            return false;
        }
        target = rod;
        chargeStart = level.getGameTime();
        level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("keraunos_tower.charge"), SoundSource.BLOCKS, 1.5F, 1.0F);
        sync();
        return true;
    }

    private static void tell(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.singulo.keraunos." + key), true);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, KeraunosTowerBlockEntity be) {
        boolean signal = level.hasNeighborSignal(pos);
        if (be.mode == Mode.REDSTONE && signal && !be.powered) {
            be.fire(null);
        }
        be.powered = signal;
        // 自動: 電力がたまり、撃てる状態なら撃つ（避雷針を探すのは1秒に1回）
        if (be.mode == Mode.AUTO && be.chargeStart < 0 && level.getGameTime() - be.boltAt >= COOLDOWN
                && be.energy.getEnergyStored() >= COST && (level.getGameTime() + pos.asLong()) % 20 == 0) {
            be.fire(null);
        }
        if (be.chargeStart >= 0 && level.getGameTime() - be.chargeStart >= CHARGE_TICKS) {
            be.discharge((ServerLevel) level);
        }
        boolean lit = be.chargeStart >= 0 || level.getGameTime() - be.boltAt < BOLT_TICKS;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void discharge(ServerLevel level) {
        chargeStart = -1;
        if (target == null || !level.getBlockState(target).is(Blocks.LIGHTNING_ROD)) {
            target = null;
            sync();
            return;
        }
        boltAt = level.getGameTime();
        Fulgurite.strike(level, target.above());
        // 避雷針の先に散る火花
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, target.getX() + 0.5, target.getY() + 0.9,
                target.getZ() + 0.5, 30, 0.25, 0.4, 0.25, 0.3);
        Vec3 orb = Vec3.atBottomCenterOf(worldPosition).add(0, ORB_HEIGHT, 0);
        level.playSound(null, orb.x, orb.y, orb.z, io.github.genichimaruo.singulo.registry.SinguloSounds.get("keraunos_tower.discharge"), SoundSource.BLOCKS, 2.0F, 1.0F);
        sync();
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            setMode(Mode.values()[(mode.ordinal() + 1) % Mode.values().length]);
            level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("keraunos_tower.mode"), SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.singulo.keraunos.mode."
                    + mode.name().toLowerCase(java.util.Locale.ROOT)), true);
            return;
        }
        if (fire(player)) {
            player.displayClientMessage(Component.translatable("message.singulo.keraunos.fire",
                    energy.getEnergyStored(), energy.getMaxEnergyStored()), true);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("charge", chargeStart);
        tag.putLong("bolt", boltAt);
        if (target != null) {
            tag.putLong("target", target.asLong());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putLong("charge", chargeStart);
        tag.putString("mode", mode.name());
        tag.putLong("bolt", boltAt);
        if (target != null) {
            tag.putLong("target", target.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energy.setEnergy(tag.getInt("energy"));
        }
        chargeStart = tag.contains("charge") ? tag.getLong("charge") : -1;
        boltAt = tag.contains("bolt") ? tag.getLong("bolt") : -1000;
        target = tag.contains("target") ? BlockPos.of(tag.getLong("target")) : null;
        try {
            mode = tag.contains("mode") ? Mode.valueOf(tag.getString("mode")) : Mode.MANUAL;
        } catch (IllegalArgumentException e) {
            mode = Mode.MANUAL;
        }
    }
}
