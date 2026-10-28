package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/**
 * 時間の流れを変える場（ワールドごと）。
 * <ul>
 *   <li>重力時間膨張ゾーン（ペンローズ・リアクターの引力帯）: 処理速度 ×0.8、触媒の減り ×0.5</li>
 *   <li>時間加速（ティプラー・シリンダーの範囲）: 処理速度 ×2、触媒の減り ×2（設定 tiplerSpeedMultiplier）</li>
 * </ul>
 * 両方の範囲に入っている場所では打ち消し合って ×1 になる。場を張る装置自身（owner）には効かない。
 * 触媒の減りの倍率は、時間で減る触媒装置にだけ掛ける（加工装置がレシピで使う触媒は、処理速度に比例して減る）。
 */
public final class TimeFields {
    public static final double DILATION_SPEED = 0.8;
    public static final double DILATION_WEAR = 0.5;
    public static final double ACCELERATION_SPEED = 2.0;

    private record Field(BlockPos owner, BlockPos center, int radius, boolean accelerate) {
        boolean covers(BlockPos pos) {
            return !pos.equals(owner) && center.distSqr(pos) <= (double) radius * radius;
        }
    }

    /** ワールドごとの場の索引（値はワールドを持たないので、使い終えたワールドは消える）。 */
    private static final Map<Level, Index> FIELDS = new WeakHashMap<>();
    /** これより多くのチャンクにまたがる大きな場は、チャンクごとに登録せず別に持つ。 */
    private static final int MAX_INDEXED_CHUNKS = 1024;

    /** 場の索引: 持ち主ごとと、場がかかるチャンクごと（その場所にかかる場だけを調べるため）。 */
    private static final class Index {
        final Map<BlockPos, Field> owners = new HashMap<>();
        final Map<Long, Map<BlockPos, Field>> chunks = new HashMap<>();
        /** チャンクごとに登録しない大きな場（ふつうの設定の半径なら、かかるチャンクは81まで）。 */
        final Map<BlockPos, Field> wide = new HashMap<>();

        void index(Field f, boolean add) {
            int minX = (int) (((long) f.center.getX() - f.radius) >> 4);
            int maxX = (int) (((long) f.center.getX() + f.radius) >> 4);
            int minZ = (int) (((long) f.center.getZ() - f.radius) >> 4);
            int maxZ = (int) (((long) f.center.getZ() + f.radius) >> 4);
            if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_INDEXED_CHUNKS) {
                if (add) {
                    wide.put(f.owner, f);
                } else {
                    wide.remove(f.owner);
                }
                return;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = ChunkPos.asLong(x, z);
                    if (add) {
                        chunks.computeIfAbsent(key, k -> new HashMap<>()).put(f.owner, f);
                        continue;
                    }
                    Map<BlockPos, Field> bucket = chunks.get(key);
                    if (bucket != null) {
                        bucket.remove(f.owner);
                        if (bucket.isEmpty()) {
                            chunks.remove(key);
                        }
                    }
                }
            }
        }
    }

    /** ワールドを閉じるとき、そのワールドの場を忘れる。 */
    public static void forgetLevel(Level level) {
        FIELDS.remove(level);
    }

    /** 場を張る（radius が 0 以下なら外す）。owner は装置の位置、center は場の中心。 */
    public static void set(Level level, BlockPos owner, BlockPos center, int radius, boolean accelerate) {
        if (radius <= 0 || !accelerate && !dilationEnabled()) {
            remove(level, owner);
            return;
        }
        Index index = FIELDS.computeIfAbsent(level, l -> new Index());
        Field previous = index.owners.get(owner);
        // 動いている装置は毎 tick 場を張り直す。変わっていなければ索引を作り直さない
        if (previous != null && previous.center.equals(center) && previous.radius == radius
                && previous.accelerate == accelerate) {
            return;
        }
        Field field = new Field(owner.immutable(), center.immutable(), radius, accelerate);
        if (previous != null) {
            index.index(previous, false);
        }
        index.owners.put(field.owner, field);
        index.index(field, true);
    }

    public static void remove(Level level, BlockPos owner) {
        Index index = FIELDS.get(level);
        if (index != null) {
            Field removed = index.owners.remove(owner);
            if (removed != null) {
                index.index(removed, false);
            }
            if (index.owners.isEmpty()) {
                FIELDS.remove(level);
            }
        }
    }

    /** その場所の状態。0 は普通、1 は加速、-1 は膨張。 */
    public static int state(Level level, BlockPos pos) {
        Index index = FIELDS.get(level);
        if (index == null) {
            return 0;
        }
        Map<BlockPos, Field> nearby = index.chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        int found = covering(nearby, pos, 0);
        if (found != BOTH) {
            found = covering(index.wide, pos, found);
        }
        // 加速と膨張が重なると打ち消し合う
        return found == ACCELERATE ? 1 : found == DILATE ? -1 : 0;
    }

    /** その場所にかかっている場の種類（ビット）。 */
    private static final int ACCELERATE = 1, DILATE = 2, BOTH = ACCELERATE | DILATE;

    /** fields のうち pos にかかる場の種類を found に足す（両方そろえばそこでやめる）。 */
    private static int covering(Map<BlockPos, Field> fields, BlockPos pos, int found) {
        if (fields == null) {
            return found;
        }
        for (Field f : fields.values()) {
            if (f.covers(pos)) {
                found |= f.accelerate ? ACCELERATE : DILATE;
                if (found == BOTH) {
                    break;
                }
            }
        }
        return found;
    }

    /** 処理速度の倍率。 */
    public static double speed(Level level, BlockPos pos) {
        return switch (state(level, pos)) {
            case 1 -> accelerationSpeed();
            case -1 -> DILATION_SPEED;
            default -> 1.0;
        };
    }

    /** 触媒の減りの倍率。加速の倍率は処理速度と同じ。 */
    public static double wear(Level level, BlockPos pos) {
        return switch (state(level, pos)) {
            case 1 -> accelerationSpeed();
            case -1 -> DILATION_WEAR;
            default -> 1.0;
        };
    }

    public static double accelerationSpeed() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.TIPLER_SPEED_MULTIPLIER.get() : ACCELERATION_SPEED;
    }

    public static boolean dilationEnabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.TIME_DILATION_ENABLED.get();
    }

    private TimeFields() {}
}
