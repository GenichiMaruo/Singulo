package io.github.genichimaruo.singulo.ruin.lab;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * 中央動力炉の制御盤。起動すると {@value #STARTUP} tick の起動の演出（警報・蒸気・炉心の唸り）のあと、施設の電力を戻す
 * （{@link LabPower#restore}）。そのとき、眠っていた統括AIが目を覚ます（施設の中の挑戦者へ放送が流れる）。
 * 施設の範囲は、研究所を生成したときに LabStructure が書き込む。範囲がなければ（自分で置いたときなど）、まわり {@value #FALLBACK} ブロック。
 */
public class ReactorConsoleBlockEntity extends BlockEntity {
    public static final int STARTUP = 100;
    static final int FALLBACK = 16;

    @Nullable
    private BoundingBox facility;
    private boolean powered;
    private int startup;

    public ReactorConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.REACTOR_CONSOLE.get(), pos, state);
    }

    /** 施設の範囲を覚える（生成のとき）。 */
    public void setFacility(BoundingBox box) {
        facility = box;
        setChanged();
    }

    @Nullable
    public BoundingBox facility() {
        return facility;
    }

    public boolean powered() {
        return powered;
    }

    public boolean starting() {
        return startup > 0;
    }

    /** 起動を始める（もう動いている・起動中なら、そう知らせるだけ）。 */
    public void start(ServerLevel level, @Nullable ServerPlayer player) {
        if (powered || startup > 0) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(powered ? "lab.singulo.reactor.online" : "lab.singulo.reactor.starting"), true);
            }
            return;
        }
        startup = STARTUP;
        setChanged();
        broadcast(level, Component.translatable("lab.singulo.reactor.start").withStyle(ChatFormatting.YELLOW));
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 0.6F);
    }

    /** 起動の演出を飛ばして、すぐ電力を戻す（テスト用）。 */
    public void finishNow(ServerLevel level) {
        startup = 0;
        powerUp(level);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorConsoleBlockEntity be) {
        if (be.startup <= 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        int t = STARTUP - be.startup;
        be.startup--;
        if (t % 20 == 0) {
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2F, 0.5F + t / (float) STARTUP);
        }
        if (t % 4 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.1);
        }
        if (t == STARTUP / 2) {
            // 炉に火が入る: 先に炉心と導管が光り、炉が唸る（施設の明かりは、起動が終わってから）
            server.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 2.0F, 0.6F);
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0F, 0.5F);
            LabPower.ignite(server, be.area());
        }
        if (be.startup == 0) {
            be.powerUp(server);
        }
    }

    private BoundingBox area() {
        return facility != null ? facility : new BoundingBox(worldPosition).inflatedBy(FALLBACK);
    }

    private void powerUp(ServerLevel level) {
        powered = true;
        setChanged();
        int n = LabPower.restore(level, area());
        for (ServerPlayer player : players(level)) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
                io.github.genichimaruo.singulo.registry.SinguloTriggers.milestone(player, "lab_power");
            }
        }
        BlockState s = level.getBlockState(worldPosition);
        if (s.getBlock() instanceof ReactorConsoleBlock && !s.getValue(ReactorConsoleBlock.POWERED)) {
            level.setBlock(worldPosition, s.setValue(ReactorConsoleBlock.POWERED, true), Block.UPDATE_CLIENTS);
        }
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.7F);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 2.0F, 0.8F);
        broadcast(level, Component.translatable("lab.singulo.reactor.restored").withStyle(ChatFormatting.AQUA));
        broadcast(level, Component.translatable("lab.singulo.reactor.ai").withStyle(ChatFormatting.RED));
        io.github.genichimaruo.singulo.Singulo.LOGGER.debug("Lab power restored at {}: {} blocks", worldPosition, n);
    }

    private java.util.List<ServerPlayer> players(ServerLevel level) {
        BoundingBox b = area();
        AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1).inflate(8);
        return level.getEntitiesOfClass(ServerPlayer.class, box);
    }

    /** 施設の中のプレイヤーへ知らせる（施設内放送）。 */
    private void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer p : players(level)) {
            p.sendSystemMessage(msg);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (facility != null) {
            tag.putIntArray("facility", LabPower.write(facility));
        }
        tag.putBoolean("powered", powered);
        tag.putInt("startup", startup);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] a = tag.getIntArray("facility");
        facility = a.length == 6 ? LabPower.read(a) : null;
        powered = tag.getBoolean("powered");
        startup = tag.getInt("startup");
    }
}
