package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.nature.MeteorCores;
import io.github.genichimaruo.singulo.nature.MeteorCraterPiece;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * 隕石コンパス。右クリックすると、まだ掘り出されていない隕石の核のうち、いちばん近いものを探し、針がそちらを向く（距離も出る）。
 * 指している核が掘り出されると、針は回り出す（もう一度右クリックすると次を探す）。
 */
public class MeteorCompassItem extends SinguloItem {
    /** 探す広さ（クレーターの配置の区画の数。1区画は 28 チャンク四方）。 */
    public static final int SEARCH_CELLS = 4;

    public MeteorCompassItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        ServerLevel server = (ServerLevel) level;
        player.getCooldowns().addCooldown(this, 60);
        BlockPos core = nearestCore(server, player.blockPosition());
        if (core == null) {
            stack.getOrCreateTag().remove("LodestonePos"); stack.getOrCreateTag().remove("LodestoneDimension");
            player.displayClientMessage(Component.translatable("compass.singulo.meteor.not_found"), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.getOrCreateTag().put("LodestonePos", net.minecraft.nbt.NbtUtils.writeBlockPos(core));
        stack.getOrCreateTag().put("LodestoneDimension", Level.RESOURCE_KEY_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, level.dimension()).result().orElseThrow());
        stack.getOrCreateTag().putBoolean("LodestoneTracked", false);
        int dist = (int) Math.sqrt(player.blockPosition().distSqr(new BlockPos(core.getX(), player.getBlockY(), core.getZ())));
        player.displayClientMessage(Component.translatable("compass.singulo.meteor.found", dist), true);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
        return InteractionResultHolder.success(stack);
    }

    /** 指している核が掘り出されたら、針を外す。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || entity.tickCount % 20 != 0) {
            return;
        }
        GlobalPos target = ExplorerCompassItem.pointing(stack);
        if (target == null || !target.dimension().equals(level.dimension())) {
            return;
        }
        if (MeteorCores.mined(server, target.pos())) {
            stack.getOrCreateTag().remove("LodestonePos"); stack.getOrCreateTag().remove("LodestoneDimension");
            if (entity instanceof Player player && selected) {
                player.displayClientMessage(Component.translatable("compass.singulo.meteor.mined"), true);
            }
        }
    }

    /**
     * いちばん近い、まだ掘り出されていない隕石の核。クレーターの配置の区画を、近い輪から順に調べる
     * （見つかった輪の中でいちばん近いもの）。古い版で作られたクレーター（核がない）は数えない。
     */
    public static BlockPos nearestCore(ServerLevel level, BlockPos from) {
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, Singulo.id("meteor_crater")));
        if (holder.isEmpty()) {
            return null;
        }
        Structure crater = holder.get().value();
        var state = level.getChunkSource().getGeneratorState();
        BlockPos best = null;
        for (StructurePlacement placement : state.getPlacementsForStructure(holder.get())) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            BlockPos found = nearestInCells(from, spread.spacing(), SEARCH_CELLS, (cx, cz) -> {
                ChunkPos cp = spread.getPotentialStructureChunk(state.getLevelSeed(), cx * spread.spacing(), cz * spread.spacing());
                BlockPos core = core(level, cp, crater, spread);
                return core == null || MeteorCores.mined(level, core) ? null : core;
            });
            if (found != null && (best == null || horizontal(from, found) < horizontal(from, best))) {
                best = found;
            }
        }
        return best;
    }

    /** 区画 (cx, cz) にある候補（なければ null）。 */
    @FunctionalInterface
    public interface CellLookup {
        BlockPos at(int cx, int cz);
    }

    /**
     * 配置の区画（一辺 spacing チャンク）を近い輪から調べ、水平距離がいちばん近い候補を返す。区画の輪の順は距離の順と同じではない
     * （区画の端にいると、隣の区画の候補のほうが近いことがある）ので、見つかったあとも、その距離より近い所を含みうる輪は調べる。
     * 輪 r の区画は、どこも今いる区画から (r - 1) 区画ぶん以上離れている。
     */
    public static BlockPos nearestInCells(BlockPos from, int spacing, int maxRing, CellLookup lookup) {
        int cellX = Math.floorDiv(from.getX() >> 4, spacing);
        int cellZ = Math.floorDiv(from.getZ() >> 4, spacing);
        double cell = spacing * 16.0;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxRing; ring++) {
            // 核は区画の中のチャンクから1ブロックほどはみ出しうるので、少し余裕を見る
            if (best != null && (ring - 1) * cell - 2 >= bestDist) {
                break;
            }
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    BlockPos found = lookup.at(cellX + i, cellZ + j);
                    if (found == null) {
                        continue;
                    }
                    double dist = horizontal(from, found);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = found;
                    }
                }
            }
        }
        return best;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** そのチャンクから始まるクレーターの核の場所（なければ null）。 */
    private static BlockPos core(ServerLevel level, ChunkPos cp, Structure crater, StructurePlacement placement) {
        var presence = level.structureManager().checkStructurePresence(cp, crater, false);
        if (presence == net.minecraft.world.level.levelgen.structure.StructureCheckResult.START_NOT_PRESENT) {
            return null;
        }
        StructureStart start = level.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(crater);
        if (start == null || !start.isValid()) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof MeteorCraterPiece m) {
                return m.corePos();
            }
        }
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (ExplorerCompassItem.pointing(stack) == null) {
            tooltip.add(Component.translatable("compass.singulo.meteor.idle").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
