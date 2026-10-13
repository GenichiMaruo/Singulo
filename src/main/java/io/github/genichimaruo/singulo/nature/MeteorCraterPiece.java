package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * 隕石クレーターの本体。型紙ではなく、その場の地面の高さを1列ずつ読んで描き込むので、斜面でも四角い切り口が出ない。
 * <ul>
 * <li>すり鉢: 中心は、生まれた場所の地面から3ブロックほど下。縁に近づくほど、その列の地面の高さへなめらかに寄せる
 *     （斜面では上り側が少し深く、下り側は開く）。底は焼けた岩、木の生えない砂利と凝灰岩。</li>
 * <li>縁: その列の地面の上に、外へ向かって低くなる盛り土。表面はその土地の地面（草・砂・テラコッタ・石）に合わせる。</li>
 * <li>飛び散った土: 縁の外に、放射状の筋になって、遠いほどまばらに。ときどき岩や隕石のかけらが転がる。</li>
 * <li>隕石: 中心で傾いて半分埋まり、芯に隕石の核が1つ。殻の表面には星屑の結晶。</li>
 * </ul>
 * 乱数はすべて場所と種から決まるので、チャンクの境目をまたいでも形がつながる。
 */
public class MeteorCraterPiece extends StructurePiece {
    /** 中心から描き込む水平の広さ（すり鉢の半径の最大 ＋ 縁 ＋ 飛び散る範囲）。 */
    static final int REACH = 15;
    static final double RIM = 2.8;
    private static final int SET = Block.UPDATE_CLIENTS;

    private final BlockPos center;
    private final long seed;
    private final double baseRadius;
    private final double meteorSize;
    private final double[] phase = new double[6];
    /** 隕石の中心（ブロックの座標、小数）と傾き。 */
    private final double mx;
    private final double my;
    private final double mz;
    private final double tilt;

    public MeteorCraterPiece(BlockPos center, long seed) {
        super(SinguloWorldgen.METEOR_CRATER_PIECE.get(), 0, box(center));
        this.center = center;
        this.seed = seed;
        RandomSource r = RandomSource.create(seed);
        baseRadius = 4.8 + r.nextDouble() * 1.1;
        meteorSize = 1.9 + r.nextDouble() * 0.35;
        for (int i = 0; i < phase.length; i++) {
            phase[i] = r.nextDouble() * Math.PI * 2;
        }
        mx = center.getX() + 0.5 + (r.nextDouble() - 0.5) * 1.2;
        mz = center.getZ() + 0.5 + (r.nextDouble() - 0.5) * 1.2;
        my = center.getY() - 3 + 0.7;
        tilt = (r.nextDouble() - 0.5) * 0.7;
    }

    public MeteorCraterPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(new BlockPos(tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz")), tag.getLong("seed"));
    }

    private static BoundingBox box(BlockPos c) {
        return new BoundingBox(c.getX() - REACH, c.getY() - 10, c.getZ() - REACH, c.getX() + REACH, c.getY() + 28, c.getZ() + REACH);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("cx", center.getX());
        tag.putInt("cy", center.getY());
        tag.putInt("cz", center.getZ());
        tag.putLong("seed", seed);
    }

    /** 隕石の核の場所（隕石コンパスが指す）。 */
    public BlockPos corePos() {
        return BlockPos.containing(mx, my, mz);
    }

    /** 角度ごとに揺らいだ、すり鉢の半径。 */
    public double radius(double theta) {
        return baseRadius + 0.7 * Math.sin(3 * theta + phase[0]) + 0.4 * Math.sin(5 * theta + phase[1])
                + 0.25 * Math.sin(7 * theta + phase[2]);
    }

    /** 飛び散った土の筋（0〜1、角度で変わる）。 */
    private double rays(double theta) {
        return Mth.clamp(0.5 + 0.35 * Math.cos(7 * theta + phase[3]) + 0.25 * Math.cos(11 * theta + phase[4]), 0, 1);
    }

    /** 場所ごとに決まった乱数。 */
    private RandomSource random(int x, int y, int z, int salt) {
        return RandomSource.create(Mth.getSeed(x, y, z) ^ seed ^ (salt * 0x9E3779B97F4A7C15L));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource unused,
            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int cx = center.getX();
        int cz = center.getZ();
        // 描いたすり鉢の底の高さ（列ごと）。掘ったあとに高さの地図を読み直すと、出来上がったチャンクでは
        // 生成用の高さ（WG）が掘る前のまま残っていて、底の上に浮いたものを置いてしまうので、自分で覚えておく。
        // 部品は同時に別のチャンクで描かれることがあるので、この呼び出しの中だけで持つ
        java.util.Map<Long, Integer> floors = new java.util.HashMap<>();
        for (int x = Math.max(chunkBox.minX(), cx - REACH); x <= Math.min(chunkBox.maxX(), cx + REACH); x++) {
            for (int z = Math.max(chunkBox.minZ(), cz - REACH); z <= Math.min(chunkBox.maxZ(), cz + REACH); z++) {
                int floor = column(level, x, z);
                if (floor != Integer.MIN_VALUE) {
                    floors.put(BlockPos.asLong(x, 0, z), floor);
                }
            }
        }
        meteor(level, chunkBox, floors);
    }

    // ------------------------------------------------------------------ 地面

    /** 土地の地面の種類（縁と飛び散った土の表面を合わせる）。 */
    private enum Soil { DIRT, SAND, RED_SAND, TERRACOTTA, STONE }

    private static Soil soil(BlockState top) {
        if (top.is(Blocks.RED_SAND)) {
            return Soil.RED_SAND;
        }
        if (top.is(BlockTags.SAND) || top.is(Blocks.SANDSTONE)) {
            return Soil.SAND;
        }
        if (top.is(BlockTags.TERRACOTTA)) {
            return Soil.TERRACOTTA;
        }
        if (top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(Blocks.GRAVEL) || top.is(Blocks.CALCITE) || top.is(Blocks.SNOW_BLOCK)
                || top.is(Blocks.PACKED_ICE) || top.is(Blocks.POWDER_SNOW)) {
            return Soil.STONE;
        }
        return Soil.DIRT;
    }

    /** 地面の上に乗っているだけのもの（草花・木・雪・つる）。 */
    private static boolean soft(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.BAMBOO) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)
                || s.is(Blocks.BEE_NEST) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.SNOW);
    }

    /** その列の、地面のいちばん上のブロックの高さ（草花や木は飛ばす）。上が水なら Integer.MIN_VALUE。 */
    private static int ground(WorldGenLevel level, int x, int z) {
        // 生成中は WG の高さ、出来上がったチャンク（/place など）では今の高さが正しい。どちらかが古くなっていても、
        // 大きいほうから下へ、地面の上に乗っているだけのもの（空気を含む）を飛ばして探すので、本当の地面に行き着く
        int y = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) - 1;
        int bottom = level.getMinBuildHeight();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        while (y > bottom) {
            BlockState s = level.getBlockState(p.setY(y));
            if (!s.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (!soft(s)) {
                return y;
            }
            y--;
        }
        return Integer.MIN_VALUE;
    }

    /** 1列を描く。すり鉢の中なら底の高さ、それ以外は Integer.MIN_VALUE。 */
    private int column(WorldGenLevel level, int x, int z) {
        double dx = x + 0.5 - (center.getX() + 0.5);
        double dz = z + 0.5 - (center.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        double theta = Math.atan2(dz, dx);
        double r = radius(theta);
        double ray = rays(theta);
        double reach = r + RIM + 2.5 + 3.0 * ray;
        if (d >= reach) {
            return Integer.MIN_VALUE;
        }
        int g = ground(level, x, z);
        if (g == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        RandomSource rnd = random(x, 0, z, 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, g, z);
        BlockState top = level.getBlockState(p);
        Soil soil = soil(top);
        if (d < r) {
            return bowl(level, x, z, g, d / r, rnd);
        } else if (d < r + RIM) {
            rim(level, x, z, g, (d - r) / RIM, theta, top, soil, rnd);
        } else {
            ejecta(level, x, z, g, (d - r - RIM) / (reach - r - RIM), ray, top, soil, rnd);
        }
        return Integer.MIN_VALUE;
    }

    /** すり鉢の底（k は縁までの割合 0〜1）。底の高さを返す。 */
    private int bowl(WorldGenLevel level, int x, int z, int g, double k, RandomSource rnd) {
        double depth = 3.0 * (1 - k * k) + 0.4 + (rnd.nextDouble() - 0.5) * 0.5;
        // 縁に近いほど、その列の地面の高さへ寄せる（斜面でも切り立った壁にしない）
        double w = Mth.clamp((k - 0.5) / 0.5, 0, 1);
        w = w * w * (3 - 2 * w);
        double ref = (1 - w) * center.getY() + w * g;
        int fy = Math.min(g, (int) Math.round(ref - depth));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, fy, z);
        // 底の上の地面と、その上の草木を取り除く
        for (int y = fy + 1; y <= g; y++) {
            level.setBlock(p.setY(y), Blocks.AIR.defaultBlockState(), SET);
        }
        clearAbove(level, x, Math.max(fy, g), z, 24);
        level.setBlock(p.setY(fy), floorBlock(k, rnd), SET);
        // 底の下を岩でふさぐ（洞窟とつながって抜けないように）
        for (int y = fy - 1; y >= fy - 2; y--) {
            BlockState below = level.getBlockState(p.setY(y));
            if (below.isAir() || !below.getFluidState().isEmpty() || soft(below)) {
                level.setBlock(p, rnd.nextInt(3) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState(), SET);
            }
        }
        return fy;
    }

    /** 底の材料: 中心ほど焼けて溶けた岩、外側は砂利と凝灰岩（土ではないので、木は生えない）。 */
    private static BlockState floorBlock(double k, RandomSource rnd) {
        int n = rnd.nextInt(10);
        if (k < 0.35) {
            return (n < 5 ? Blocks.SMOOTH_BASALT : n < 8 ? Blocks.BLACKSTONE : n < 9 ? Blocks.BASALT : Blocks.TUFF).defaultBlockState();
        }
        if (k < 0.7) {
            return (n < 4 ? Blocks.TUFF : n < 8 ? Blocks.GRAVEL : Blocks.BLACKSTONE).defaultBlockState();
        }
        return (n < 5 ? Blocks.GRAVEL : n < 8 ? Blocks.TUFF : Blocks.COARSE_DIRT).defaultBlockState();
    }

    /** 縁の盛り土（t は縁の内側 0 から外側 1）。 */
    private void rim(WorldGenLevel level, int x, int z, int g, double t, double theta, BlockState top, Soil soil, RandomSource rnd) {
        double h = 1.9 * Math.pow(1 - t, 1.2) * (0.8 + 0.4 * Math.sin(2 * theta + phase[5])) + (rnd.nextDouble() - 0.5) * 0.6;
        int n = (int) Math.round(h);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, g + 1, z);
        // 木の根元は盛らない（幹が埋まって浮いた木にならないように）
        if (n <= 0 || level.getBlockState(p).is(BlockTags.LOGS)) {
            scatter(level, x, z, g, 0.6, top, soil, rnd);
            return;
        }
        // 盛り土で埋まる草花と、その上に残る背の高い草の上半分を取り除く
        for (int y = g + 1; y <= g + n + 2; y++) {
            BlockState s = level.getBlockState(p.setY(y));
            if (!s.isAir() && soft(s) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), SET);
            }
        }
        level.setBlock(p.setY(g), sub(soil, top, rnd), SET);
        for (int i = 1; i <= n; i++) {
            level.setBlock(p.setY(g + i), i == n ? surface(soil, top, rnd) : sub(soil, top, rnd), SET);
        }
    }

    /** 飛び散った土（t は縁の外 0 から届く先 1）。 */
    private void ejecta(WorldGenLevel level, int x, int z, int g, double t, double ray, BlockState top, Soil soil, RandomSource rnd) {
        double chance = 0.65 * Math.pow(1 - t, 1.5) * (0.35 + 0.65 * ray);
        scatter(level, x, z, g, chance, top, soil, rnd);
    }

    private void scatter(WorldGenLevel level, int x, int z, int g, double chance, BlockState top, Soil soil, RandomSource rnd) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, g, z);
        if (rnd.nextDouble() < chance) {
            level.setBlock(p, debris(soil, rnd), SET);
        }
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos(x, g + 1, z);
        BlockState up = level.getBlockState(above);
        boolean free = up.isAir() || up.canBeReplaced() && !up.is(BlockTags.LOGS);
        if (!free) {
            return;
        }
        double roll = rnd.nextDouble();
        if (roll < chance * 0.08) {
            // 転がった岩
            clearPlant(level, above);
            int n = rnd.nextInt(5);
            level.setBlock(above, (n < 2 ? Blocks.TUFF : n < 4 ? Blocks.COBBLED_DEEPSLATE : Blocks.BLACKSTONE).defaultBlockState(), SET);
        } else if (roll < chance * 0.11) {
            // 隕石のかけら（クレーターの外のかけらには、結晶は育たない）
            clearPlant(level, above);
            level.setBlock(above, rnd.nextBoolean() ? SinguloBlocks.METEORITE_CRUST.get().defaultBlockState()
                    : SinguloBlocks.METEORIC_IRON_CHUNK.get().defaultBlockState(), SET);
        }
    }

    private static void clearPlant(WorldGenLevel level, BlockPos.MutableBlockPos at) {
        // 背の高い草の上半分も
        BlockPos up = at.above();
        if (level.getBlockState(up).canBeReplaced() && !level.getBlockState(up).isAir()) {
            level.setBlock(up, Blocks.AIR.defaultBlockState(), SET);
        }
    }

    /** 上に乗った草木と雪を取り除く（地面の上、空気に出るまで）。 */
    private static void clearAbove(WorldGenLevel level, int x, int from, int z, int limit) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, from + 1, z);
        int air = 0;
        for (int i = 0; i < limit && air < 2; i++) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                air++;
            } else if (soft(s) && s.getFluidState().isEmpty()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), SET);
                air = 0;
            } else {
                break;
            }
            p.move(Direction.UP);
        }
    }

    private static BlockState pickSand(Soil soil) {
        return (soil == Soil.RED_SAND ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
    }

    /** 盛り土の中身。 */
    private static BlockState sub(Soil soil, BlockState top, RandomSource rnd) {
        int n = rnd.nextInt(8);
        return switch (soil) {
            case SAND, RED_SAND -> n < 6 ? pickSand(soil) : (n < 7 ? Blocks.GRAVEL : Blocks.TUFF).defaultBlockState();
            case TERRACOTTA -> n < 4 ? top : (n < 6 ? Blocks.RED_SAND : n < 7 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState();
            case STONE -> (n < 3 ? Blocks.STONE : n < 5 ? Blocks.GRAVEL : n < 7 ? Blocks.COBBLESTONE : Blocks.TUFF).defaultBlockState();
            case DIRT -> (n < 3 ? Blocks.DIRT : n < 6 ? Blocks.COARSE_DIRT : n < 7 ? Blocks.ROOTED_DIRT : Blocks.GRAVEL).defaultBlockState();
        };
    }

    /** 盛り土の表面: その土地の地面に、掘り返した土が混じる。 */
    private static BlockState surface(Soil soil, BlockState top, RandomSource rnd) {
        int n = rnd.nextInt(10);
        return switch (soil) {
            case SAND, RED_SAND -> n < 8 ? pickSand(soil) : Blocks.GRAVEL.defaultBlockState();
            case TERRACOTTA -> n < 5 ? top : (n < 8 ? Blocks.RED_SAND : Blocks.COARSE_DIRT).defaultBlockState();
            case STONE -> n < 4 && !top.is(Blocks.SNOW_BLOCK) && !top.is(Blocks.POWDER_SNOW) ? top
                    : (n < 7 ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState();
            case DIRT -> n < 5 && top.is(BlockTags.DIRT) ? top : (n < 8 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
        };
    }

    /** 飛び散った土（地面のいちばん上を替える）。 */
    private static BlockState debris(Soil soil, RandomSource rnd) {
        int n = rnd.nextInt(10);
        return switch (soil) {
            case SAND, RED_SAND -> (n < 5 ? Blocks.GRAVEL : n < 8 ? Blocks.COARSE_DIRT : Blocks.TUFF).defaultBlockState();
            case TERRACOTTA -> (n < 5 ? Blocks.RED_SAND : n < 8 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState();
            case STONE -> (n < 5 ? Blocks.GRAVEL : n < 8 ? Blocks.TUFF : Blocks.COBBLESTONE).defaultBlockState();
            case DIRT -> (n < 5 ? Blocks.COARSE_DIRT : n < 8 ? Blocks.GRAVEL : n < 9 ? Blocks.ROOTED_DIRT : Blocks.TUFF).defaultBlockState();
        };
    }

    // ------------------------------------------------------------------ 隕石

    /** その場所が隕石の形の中か（1 以下なら中）。 */
    public double insideMeteor(BlockPos p) {
        return inside(p.getX(), p.getY(), p.getZ());
    }

    /** 隕石の形の中か（1 以下なら中。0.45 より内側は芯）。 */
    private double inside(int x, int y, int z) {
        double rx = meteorSize * 1.1;
        double ry = meteorSize * 0.85;
        double rz = meteorSize * 0.95;
        double px = x + 0.5 - mx;
        double py = y + 0.5 - my;
        double pz = z + 0.5 - mz;
        double qx = px * Math.cos(tilt) - py * Math.sin(tilt);
        double qy = px * Math.sin(tilt) + py * Math.cos(tilt);
        double bump = 0.18 * Math.sin(3 * qx + phase[0]) * Math.cos(2 * pz + phase[1]);
        return (qx / rx) * (qx / rx) + (qy / ry) * (qy / ry) + (pz / rz) * (pz / rz) - bump;
    }

    private void meteor(WorldGenLevel level, BoundingBox chunkBox, java.util.Map<Long, Integer> floors) {
        int span = (int) Math.ceil(meteorSize * 1.1) + 3;
        BlockPos core = corePos();
        BlockState crust = SinguloBlocks.METEORITE_CRUST.get().defaultBlockState().setValue(MeteoriteCrustBlock.NATURAL, true);
        BlockState iron = SinguloBlocks.METEORIC_IRON_CHUNK.get().defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(mx) - span; x <= (int) Math.floor(mx) + span; x++) {
            for (int z = (int) Math.floor(mz) - span; z <= (int) Math.floor(mz) + span; z++) {
                if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                    continue;
                }
                for (int y = (int) Math.floor(my) - span; y <= (int) Math.floor(my) + span; y++) {
                    double e = inside(x, y, z);
                    p.set(x, y, z);
                    if (e <= 1.0) {
                        RandomSource rnd = random(x, y, z, 2);
                        BlockState b;
                        if (p.equals(core)) {
                            b = SinguloBlocks.METEORITE_CORE.get().defaultBlockState();
                        } else if (e > 0.45) {
                            b = rnd.nextFloat() < 0.22F ? iron : crust;
                        } else {
                            b = rnd.nextFloat() < 0.6F ? iron : crust;
                        }
                        level.setBlock(p, b, SET);
                    } else if (e <= 1.9 && level.getBlockState(p).isAir()) {
                        cluster(level, p);
                    }
                }
            }
        }
        // まだ熱い所と、飛び散った小さなかけら（隕石のすぐ脇の底）
        for (int x = (int) Math.floor(mx) - 4; x <= (int) Math.floor(mx) + 4; x++) {
            for (int z = (int) Math.floor(mz) - 4; z <= (int) Math.floor(mz) + 4; z++) {
                if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                    continue;
                }
                double dd = Math.hypot(x + 0.5 - mx, z + 0.5 - mz);
                Integer floorY = floors.get(BlockPos.asLong(x, 0, z));
                if (dd < 1.5 || dd > 3.6 || floorY == null) {
                    continue;
                }
                p.set(x, floorY, z);
                BlockState floor = level.getBlockState(p);
                if (floor.is(SinguloBlocks.METEORITE_CRUST.get()) || floor.is(SinguloBlocks.METEORIC_IRON_CHUNK.get())
                        || floor.is(SinguloBlocks.STARDUST_CLUSTER.get()) || floor.is(SinguloBlocks.METEORITE_CORE.get())) {
                    continue;
                }
                RandomSource rnd = random(x, 0, z, 3);
                float roll = rnd.nextFloat();
                if (roll < 0.07F) {
                    level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), SET);
                } else if (roll < 0.14F && level.getBlockState(p.above()).isAir()) {
                    level.setBlock(p.move(Direction.UP), rnd.nextBoolean() ? iron : SinguloBlocks.METEORITE_CRUST.get().defaultBlockState(), SET);
                }
            }
        }
    }

    /** 殻のすぐ外の空いた所に、星屑の結晶が育っている（上向きと横向き。殻に接している所だけ）。 */
    private void cluster(WorldGenLevel level, BlockPos.MutableBlockPos p) {
        RandomSource rnd = random(p.getX(), p.getY(), p.getZ(), 4);
        if (rnd.nextFloat() >= 0.16F) {
            return;
        }
        for (Direction facing : new Direction[] {Direction.UP, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
            BlockPos on = p.relative(facing.getOpposite());
            double e = inside(on.getX(), on.getY(), on.getZ());
            if (e <= 1.0 && e > 0.45 && !on.equals(corePos())) {
                level.setBlock(p, SinguloBlocks.STARDUST_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, facing)
                        .setValue(AmethystClusterBlock.WATERLOGGED, false), SET);
                return;
            }
        }
    }
}
