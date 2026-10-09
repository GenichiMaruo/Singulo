package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 決まった形のマルチブロックの設計図と判定。形は箱の中の座標（x: 横 0〜width−1、y: 高さ 0〜height−1、
 * z: 奥行き 0〜depth−1）ごとに「何を置くか」で決める。コントローラは (cx, cy, cz) で、z が小さい側が手前（コントローラの正面側）。
 * <p>
 * 判定はワールドの4つの向きを試す。設計図（ホロ投影機・組み立てコマンド・JEI）も同じ定義から作るので、判定とずれない。
 * 外装板（PANEL）の位置にはマルチブロック搬入出ポートを置いてもよい。
 */
public final class ShapeSpec {
    public enum Kind {
        /** 決まった部品（枠・意味のある部品）。ポートは置けない */
        PART,
        /** 外装板。マルチブロック搬入出ポートと入れ替えられる */
        PANEL,
        /** 空気でなければならない（中の部屋） */
        AIR,
        CONTROLLER,
        /** 形の外（何があってもよい） */
        IGNORE
    }

    public record Slot(Kind kind, @Nullable Supplier<? extends Block> block) {
        public static final Slot AIR = new Slot(Kind.AIR, null);
        public static final Slot CONTROLLER = new Slot(Kind.CONTROLLER, null);
        public static final Slot IGNORE = new Slot(Kind.IGNORE, null);

        public static Slot part(Supplier<? extends Block> block) {
            return new Slot(Kind.PART, block);
        }

        public static Slot panel(Supplier<? extends Block> block) {
            return new Slot(Kind.PANEL, block);
        }
    }

    public interface Layout {
        Slot at(int x, int y, int z);
    }

    private final int width;
    private final int height;
    private final int depth;
    private final int cx;
    private final int cy;
    private final int cz;
    private final Layout layout;

    public ShapeSpec(int width, int height, int depth, int cx, int cy, int cz, Layout layout) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.layout = layout;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public Slot slot(int x, int y, int z) {
        return layout.at(x, y, z);
    }

    /** 箱の辺の上にあるか（x・y・z のうち、端にある軸の数）。2以上なら辺、3なら角。 */
    public static int edges(int x, int y, int z, int w, int h, int d) {
        return (x == 0 || x == w - 1 ? 1 : 0) + (y == 0 || y == h - 1 ? 1 : 0) + (z == 0 || z == d - 1 ? 1 : 0);
    }

    /** コントローラの位置と奥の向き（back）から、(x, y, z) のワールドの位置。 */
    public BlockPos pos(BlockPos controller, Direction back, int x, int y, int z) {
        return controller.relative(back.getClockWise(), x - cx).above(y - cy).relative(back, z - cz);
    }

    /** 箱の中心（ワールド座標）。 */
    public Vec3 center(BlockPos controller, Direction back) {
        BlockPos a = pos(controller, back, 0, 0, 0);
        BlockPos b = pos(controller, back, width - 1, height - 1, depth - 1);
        return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, (a.getY() + b.getY()) / 2.0 + 0.5, (a.getZ() + b.getZ()) / 2.0 + 0.5);
    }

    /** 形成できている向き。ports に搬入出ポートの位置を入れる。できていなければ null。 */
    @Nullable
    public Direction find(Level level, BlockPos controller, Block controllerBlock, @Nullable List<BlockPos> ports) {
        for (Direction back : Direction.Plane.HORIZONTAL) {
            List<BlockPos> found = new ArrayList<>();
            if (matches(level, controller, back, controllerBlock, found)) {
                if (ports != null) {
                    ports.addAll(found);
                }
                return back;
            }
        }
        return null;
    }

    public boolean matches(Level level, BlockPos controller, Direction back, Block controllerBlock, List<BlockPos> ports) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    Slot slot = layout.at(x, y, z);
                    if (slot.kind() == Kind.IGNORE) {
                        continue;
                    }
                    BlockPos p = pos(controller, back, x, y, z);
                    BlockState s = level.getBlockState(p);
                    boolean ok = switch (slot.kind()) {
                        case PART -> s.is(slot.block().get());
                        case PANEL -> {
                            if (s.is(SinguloBlocks.MULTIBLOCK_PORT.get())) {
                                ports.add(p.immutable());
                                yield true;
                            }
                            yield s.is(slot.block().get());
                        }
                        case AIR -> s.isAir();
                        case CONTROLLER -> s.is(controllerBlock);
                        case IGNORE -> true;
                    };
                    if (!ok) {
                        ports.clear();
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 設計図: 部品・外装板・空気の位置と、置くもの（コントローラと形の外は含めない）。 */
    public void place(BlockPos controller, Direction back, BiConsumer<BlockPos, BlockState> out) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    Slot slot = layout.at(x, y, z);
                    switch (slot.kind()) {
                        case PART, PANEL -> out.accept(pos(controller, back, x, y, z), slot.block().get().defaultBlockState());
                        case AIR -> out.accept(pos(controller, back, x, y, z), Blocks.AIR.defaultBlockState());
                        default -> {
                        }
                    }
                }
            }
        }
    }
}
