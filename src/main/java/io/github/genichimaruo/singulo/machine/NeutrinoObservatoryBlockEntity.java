package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.network.ScanPayload;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ニュートリノ観測所（設置型のニュートリノ・スキャナー）。電力を使い、半径 RADIUS の中の鉱石と遺構のブロックを
 * 1 tick に1層ずつ調べ続ける。右クリックで表示を入れると、近くのプレイヤー全員に、見つけたものの輪郭が浮かび上がり続ける。
 * 映る鉱石はスキャナーと同じく感度の段階で決まり、ニュートリノ感度モジュールを使って上げる。
 */
public class NeutrinoObservatoryBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    public static final int RADIUS = 48;
    public static final int SHOW_RANGE = 48;
    public static final int FE_PER_TICK = 1_000;
    public static final int MAX_MARKS = 1_500;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(400_000, 20_000, 0, this::setChanged);
    private boolean display = true;
    /** 感度の段階（1〜3）。 */
    private int tier = 1;
    private int layer;
    private final List<BlockPos> ores = new ArrayList<>();
    private final List<BlockPos> ruins = new ArrayList<>();
    private int lastOres;
    private int lastRuins;

    public NeutrinoObservatoryBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.NEUTRINO_OBSERVATORY.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public int tier() {
        return tier;
    }

    /** 感度を上げる（ニュートリノ感度モジュールから）。上がったら true。 */
    public boolean raiseTier(int to) {
        if (to <= tier) {
            return false;
        }
        tier = Math.min(io.github.genichimaruo.singulo.item.NeutrinoScannerItem.MAX_TIER, to);
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NeutrinoObservatoryBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean powered = energy.getEnergyStored() >= FE_PER_TICK;
        if (powered) {
            energy.consume(FE_PER_TICK);
            scanLayer(level, pos);
        }
        boolean lit = powered && display;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** 1層ぶん調べる。最後の層まで終わったら、見つけたものを近くのプレイヤーに送り、最初からやり直す。 */
    private void scanLayer(ServerLevel level, BlockPos center) {
        int dy = layer - RADIUS;
        int r2 = RADIUS * RADIUS - dy * dy;
        if (r2 >= 0) {
            int span = (int) Math.sqrt(r2);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (dx * dx + dz * dz > r2 || ores.size() + ruins.size() >= MAX_MARKS) {
                        continue;
                    }
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.is(Tags.Blocks.ORES)) {
                        if (io.github.genichimaruo.singulo.item.NeutrinoScannerItem.oreTier(s) <= tier) {
                            ores.add(p.immutable());
                        }
                    } else if (s.is(SinguloTags.RUIN_BLOCKS)) {
                        ruins.add(p.immutable());
                    }
                }
            }
        }
        if (++layer > 2 * RADIUS) {
            layer = 0;
            lastOres = ores.size();
            lastRuins = ruins.size();
            if (display) {
                ScanPayload payload = new ScanPayload(List.copyOf(ores), List.copyOf(ruins));
                for (ServerPlayer player : level.players()) {
                    if (player.blockPosition().distSqr(center) <= SHOW_RANGE * SHOW_RANGE && player.connection.hasChannel(ScanPayload.TYPE)) {
                        PacketDistributor.sendToPlayer(player, payload);
                    }
                }
            }
            ores.clear();
            ruins.clear();
            setChanged();
        }
    }

    /** 右クリック: 表示を入れたり切ったりする。 */
    @Override
    public void openMenu(ServerPlayer player) {
        display = !display;
        setChanged();
        player.displayClientMessage(Component.translatable(display ? "gui.singulo.observatory.on" : "gui.singulo.observatory.off",
                lastOres, lastRuins).append(Component.translatable("gui.singulo.observatory.tier", tier)), true);
        player.level().playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, display ? 0.9F : 0.6F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putBoolean("display", display);
        tag.putInt("tier", tier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
        if (tag.contains("display")) {
            display = tag.getBoolean("display");
        }
        tier = Math.max(1, tag.getInt("tier"));
    }
}
