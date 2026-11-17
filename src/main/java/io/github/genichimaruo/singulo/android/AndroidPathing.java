package io.github.genichimaruo.singulo.android;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * アンドロイドの道探しから見たアンドロイド用のガラス（0.20.0）。
 *
 * <p>バニラの道の種類の一時記憶（PathTypeCache）は生き物を問わず {@code getBlockPathType(..., null)} で求めるので、
 * ガラスはいつも「アンドロイド以外」の見え方になる（透過ガラスはふさがった所・遮断ガラスは空いた所）。それはほかの生き物には正しいが、
 * アンドロイドには逆なので、アンドロイドの道探しだけはここでガラスを見直す:
 * 透過ガラス（と階段）は空いた所で、上に立てない。遮断ガラス（と階段）はふさがった所で、上に立てる。
 */
final class AndroidPathing {
    private AndroidPathing() {
    }

    /** アンドロイドの目でガラスを見る道探しの文脈（NodeEvaluator.prepare の後に差し替える）。 */
    static PathfindingContext context(PathNavigationRegion level, Mob mob) {
        return new PathfindingContext(level, mob) {
            private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

            @Override
            public PathType getPathTypeFromState(int x, int y, int z) {
                PathType glass = glass(getBlockState(at.set(x, y, z)));
                return glass != null ? glass : super.getPathTypeFromState(x, y, z);
            }
        };
    }

    /** アンドロイドにとってのガラスの道の種類（ガラスでなければ null）。 */
    @Nullable
    static PathType glass(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AndroidGlassBlock glass) {
            return glass.gate() ? PathType.OPEN : PathType.BLOCKED;
        }
        if (block instanceof AndroidGlassStairs stairs) {
            return stairs.gate() ? PathType.OPEN : PathType.BLOCKED;
        }
        return null;
    }

    /** 足もとがガラスなら、アンドロイドとしてのぶつかり方で床の高さを求める（透過ガラスは床にならない）。ほかは NaN。 */
    static double floorLevel(BlockGetter level, BlockPos pos, Mob mob) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (glass(state) == null) {
            return Double.NaN;
        }
        VoxelShape shape = state.getCollisionShape(level, below, CollisionContext.of(mob));
        return below.getY() + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y));
    }

    /**
     * 地上の歩き方。バニラは足もとのブロックのぶつかる形を生き物を問わずに見て、その中にいればジャンプする。
     * 透過ガラス（と階段）の中を通るときも、ガラスの中にいると思って跳ねるので、足もとがガラスのときだけ、アンドロイドとしてのぶつかり方で決める。
     */
    static final class Move extends MoveControl {
        Move(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            BlockPos at = mob.blockPosition();
            BlockState feet = mob.level().getBlockState(at);
            if (operation != Operation.MOVE_TO || glass(feet) == null) {
                super.tick();
                return;
            }
            // MoveControl.tick の MOVE_TO と同じ。ジャンプの判定だけ、ぶつかる形をこの生き物で求める
            operation = Operation.WAIT;
            double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ(), dy = wantedY - mob.getY();
            if (dx * dx + dy * dy + dz * dz < 2.5000003E-7F) {
                mob.setZza(0.0F);
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0F));
            mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            VoxelShape shape = feet.getCollisionShape(mob.level(), at, CollisionContext.of(mob));
            if (dy > mob.maxUpStep() && dx * dx + dz * dz < Math.max(1.0F, mob.getBbWidth())
                    || !shape.isEmpty() && mob.getY() < shape.max(Direction.Axis.Y) + at.getY()
                    && !feet.is(BlockTags.DOORS) && !feet.is(BlockTags.FENCES)) {
                mob.getJumpControl().jump();
                operation = Operation.JUMPING;
            }
        }
    }
}
