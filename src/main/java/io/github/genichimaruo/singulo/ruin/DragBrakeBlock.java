package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 制動器。沈んだ炉心区画の環状回廊の各区画の終わりにあり、壊せない。触れて下ろす（engaged）と、同じ区画（segment）の
 * 回転流の床（{@link FrameDragFloorBlock}）が {@value #REACH} ブロック以内で止まる。一度下ろすと戻らない。
 */
public class DragBrakeBlock extends Block {
    public static final BooleanProperty ENGAGED = BooleanProperty.create("engaged");
    public static final IntegerProperty SEGMENT = FrameDragFloorBlock.SEGMENT;
    /** 止める床を探す広さ（水平のブロック数と、上下のブロック数）。 */
    public static final int REACH = 48, REACH_Y = 8;

    public DragBrakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ENGAGED, false).setValue(SEGMENT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENGAGED, SEGMENT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(ENGAGED)) {
            player.sendOverlayMessage(Component.translatable("gui.singulo.brake.already"));
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(ENGAGED, true), Block.UPDATE_ALL);
        int stopped = stop((ServerLevel) level, pos, state.getValue(SEGMENT));
        level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.6F, 0.5F);
        ((ServerLevel) level).sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
        player.sendOverlayMessage(Component.translatable(stopped > 0 ? "gui.singulo.brake.engaged" : "gui.singulo.brake.already"));
        return InteractionResult.CONSUME;
    }

    /** pos の周りの、区画 segment の回転流の床を止める。止めた数を返す。 */
    public static int stop(ServerLevel level, BlockPos pos, int segment) {
        Block floor = SinguloBlocks.FRAME_DRAG_FLOOR.get();
        BoundingBox box = new BoundingBox(pos.getX() - REACH, pos.getY() - REACH_Y, pos.getZ() - REACH,
                pos.getX() + REACH, pos.getY() + REACH_Y, pos.getZ() + REACH);
        int stopped = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(floor))) {
                        continue;
                    }
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState s = section.getBlockState(x, y, z);
                                if (!s.is(floor) || !s.getValue(FrameDragFloorBlock.ACTIVE) || s.getValue(FrameDragFloorBlock.SEGMENT) != segment) {
                                    continue;
                                }
                                p.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (box.isInside(p)) {
                                    level.setBlock(p, s.setValue(FrameDragFloorBlock.ACTIVE, false), Block.UPDATE_CLIENTS);
                                    stopped++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return stopped;
    }
}
