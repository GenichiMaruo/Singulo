package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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

    /** 場の持ち主（装置の位置）→ 場。 */
    private static final Map<Level, Map<BlockPos, Field>> FIELDS = new WeakHashMap<>();

    /** 場を張る（radius が 0 以下なら外す）。owner は装置の位置、center は場の中心。 */
    public static void set(Level level, BlockPos owner, BlockPos center, int radius, boolean accelerate) {
        if (radius <= 0 || !accelerate && !dilationEnabled()) {
            remove(level, owner);
            return;
        }
        FIELDS.computeIfAbsent(level, l -> new HashMap<>()).put(owner.immutable(),
                new Field(owner.immutable(), center.immutable(), radius, accelerate));
    }

    public static void remove(Level level, BlockPos owner) {
        Map<BlockPos, Field> map = FIELDS.get(level);
        if (map != null) {
            map.remove(owner);
        }
    }

    /** その場所の状態。0 は普通、1 は加速、-1 は膨張。 */
    public static int state(Level level, BlockPos pos) {
        Map<BlockPos, Field> map = FIELDS.get(level);
        if (map == null || map.isEmpty()) {
            return 0;
        }
        boolean accel = false;
        boolean dilate = false;
        for (Field f : map.values()) {
            if (f.covers(pos)) {
                if (f.accelerate) {
                    accel = true;
                } else {
                    dilate = true;
                }
            }
        }
        return accel == dilate ? 0 : accel ? 1 : -1;
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
