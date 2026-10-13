package io.github.genichimaruo.singulo.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * ホログラムの床。最後の記録者が現れたとき、ボス部屋の床の欠けた所を埋める、赤い半透明の投影（{@link LastArchivist}）。
 * 上に立てるが、壊せず、何も落とさず、アイテムにもならない（拾えない・選べない）。戦いが終わると消える。
 */
public class ArenaHologramBlock extends HalfTransparentBlock {
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.25F), 0.8F);

    public ArenaHologramBlock(Properties properties) {
        super(properties);
    }

    /** 隣り合うホログラムどうしの面は描かない（ガラスと同じ）。 */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** 中ボタンで選んでも何も手に入らない。 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }

    /** 投影の赤い粒が、ときどき表面から立ちのぼる。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(RED, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
