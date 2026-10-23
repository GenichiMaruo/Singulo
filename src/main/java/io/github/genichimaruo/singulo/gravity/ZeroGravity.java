package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.Singulo;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 無重力ブロックの効き目。働いている無重力ブロックの上の、高さ（既定 {@value #DEFAULT_HEIGHT} ブロック。コントロールボックスで変える）の空間にいる
 * 生き物は重力が0になる（落ちず、跳ぶと漂う。落下の勢いもなくなる）。外へ出ると元に戻る。
 * <p>
 * どこが無重力かは、受電器が働かせた無重力ブロックを、真上の列（x, z）ごとの一覧にして覚えておく（ワールドごと。サーバーだけ）。
 * 生き物は自分の列の一覧だけを見るので、無重力ブロックがいくつあっても、高さがどれだけでも重くならない。
 * 重力の変化は属性（重力）の修飾で付けるので、プレイヤーの手元にもそのまま伝わる。
 */
public final class ZeroGravity {
    public static final int DEFAULT_HEIGHT = 5;
    /** 高さ {@value #DEFAULT_HEIGHT} のときの、無重力ブロック1つの電力（FE/t）と、高さ1ブロックごとの倍率。 */
    public static final long BASE_COST = 30;
    public static final double GROWTH = 1.4;
    static final ResourceLocation ID = Singulo.id("zero_gravity");

    /** ワールドごとの一覧: 列（x, z） → その列の無重力ブロック（y と高さを1つの long に）。 */
    private static final Map<Level, Long2ObjectOpenHashMap<LongArrayList>> ZONES = Collections.synchronizedMap(new WeakHashMap<>());

    private ZeroGravity() {}

    /** 高さ height の無重力ブロック1つが毎tick使う電力。高さ1ブロックごとに ×{@value #GROWTH}（上限なし。long に収まらなければ最大値）。 */
    public static long costPerBlock(int height) {
        double c = BASE_COST * Math.pow(GROWTH, height - DEFAULT_HEIGHT);
        return c >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1, Math.round(c));
    }

    public static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static long entry(int y, int height) {
        return ((long) y << 32) | (height & 0xFFFFFFFFL);
    }

    /** 働いている無重力ブロックを一覧に入れる（同じ位置なら高さを書き換える）。 */
    static void put(Level level, BlockPos pos, int height) {
        if (level.isClientSide) {
            return;
        }
        Long2ObjectOpenHashMap<LongArrayList> zones = ZONES.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        synchronized (zones) {
            LongArrayList list = zones.computeIfAbsent(column(pos.getX(), pos.getZ()), k -> new LongArrayList(1));
            for (int i = 0; i < list.size(); i++) {
                if ((int) (list.getLong(i) >> 32) == pos.getY()) {
                    list.set(i, entry(pos.getY(), height));
                    return;
                }
            }
            list.add(entry(pos.getY(), height));
        }
    }

    static void remove(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<LongArrayList> zones = ZONES.get(level);
        if (zones == null) {
            return;
        }
        synchronized (zones) {
            long key = column(pos.getX(), pos.getZ());
            LongArrayList list = zones.get(key);
            if (list == null) {
                return;
            }
            list.removeIf(e -> (int) (e >> 32) == pos.getY());
            if (list.isEmpty()) {
                zones.remove(key);
            }
        }
    }

    /** (x, y, z) の足もとが、働いている無重力ブロックの上の無重力の空間か。 */
    public static boolean inZone(Level level, double x, double y, double z) {
        Long2ObjectOpenHashMap<LongArrayList> zones = ZONES.get(level);
        if (zones == null || zones.isEmpty()) {
            return false;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        synchronized (zones) {
            LongArrayList list = zones.get(column(bx, bz));
            if (list == null) {
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                long e = list.getLong(i);
                int top = (int) (e >> 32) + 1;                       // ブロックの上の面
                int height = (int) e;
                if (y >= top - 0.01 && y < (double) top + height) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 生き物ごとに、無重力の空間にいるあいだ重力を0にする（サーバーだけ。属性はプレイヤーの手元にも伝わる）。 */
    static void update(LivingEntity living) {
        if (living.level().isClientSide) {
            return;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        // アンドロイドは足裏の磁気で床をとらえて、無重力の中でも自分で歩いて動ける（重力を変えない）
        boolean in = !GravityEffects.selfPropelled(living) && inZone(living.level(), living.getX(), living.getY(), living.getZ());
        boolean has = gravity.hasModifier(ID);
        if (in && !has) {
            gravity.addTransientModifier(new AttributeModifier(ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!in && has) {
            gravity.removeModifier(ID);
        }
        if (in) {
            living.resetFallDistance();
        }
    }

    /** A fast fall can enter and land within one tick, before the post-tick distance reset. */
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide && inZone(living.level(), living.getX(), living.getY(), living.getZ())) {
            event.setCanceled(true);
            living.resetFallDistance();
        }
    }

    public static void forgetLevel(Level level) {
        ZONES.remove(level);
    }

    /** Only 256 indexed column lookups, independent of the size of the world or zone height. */
    public static void forgetChunk(Level level, net.minecraft.world.level.ChunkPos chunk) {
        Long2ObjectOpenHashMap<LongArrayList> zones = ZONES.get(level);
        if (zones == null) return;
        synchronized (zones) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    zones.remove(column(x, z));
                }
            }
        }
    }

    /** いま無重力か（属性の修飾を見る。サーバーでもプレイヤーの手元でも使える）。 */
    public static boolean weightless(LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(ID);
    }
}
