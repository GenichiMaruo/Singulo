package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AxeAndroid;
import io.github.genichimaruo.singulo.android.BitAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.PrototypeMemories;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 地下アンドロイド研究所の試作機（斧型「アルマ」・ビット型「ノクス」）と、その設計メモリ。 */
@GameTestHolder("singulo_prototype")
@PrefixGameTestTemplate(false)
public final class PrototypeGameTests {
    private static final String HUGE = "huge";
    private static final String BATCH = "prototype";

    private PrototypeGameTests() {}

    /** 動かない的（倒れにくいハスク）。 */
    private static Zombie dummy(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        return z;
    }

    /** 試作機の型は遺構の印刷台では印刷しない（試作機の型だけが研究所の型）。 */
    @GameTest(template = "empty")
    public static void prototypesAreLabOnly(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        for (AndroidKind k : AndroidKind.ruinKinds()) {
            helper.assertTrue(k != AndroidKind.AXE && k != AndroidKind.BIT, "印刷台の型に試作機が入っている: " + k);
        }
        helper.assertTrue(Set.of(AndroidKind.prototypes()).equals(Set.of(AndroidKind.AXE, AndroidKind.BIT)), "試作機の型が斧型とビット型でない");
        helper.succeed();
    }

    /** 統括AIの設計メモリは、まだ受け取っていない型から先に渡す（2回で両方そろう）。そのあともどちらかを渡す。 */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void prototypeMemoriesMissingFirst(GameTestHelper helper) {
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        Set<AndroidKind> got = new HashSet<>();
        got.add(PrototypeMemories.next(player, player.getRandom()));
        got.add(PrototypeMemories.next(player, player.getRandom()));
        helper.assertTrue(got.size() == 2, "2回で両方の型がそろわない: " + got);
        AndroidKind third = PrototypeMemories.next(player, player.getRandom());
        helper.assertTrue(third == AndroidKind.AXE || third == AndroidKind.BIT, "3回目に試作機の型が出ない: " + third);
        helper.assertTrue(io.github.genichimaruo.singulo.compat.PlayerData.get(player, PrototypeMemories.RECEIVED, com.mojang.serialization.Codec.STRING.listOf(), java.util.List.<String>of()).size() == 2, "受け取った型の記録が2つでない");
        helper.assertTrue(PrototypeMemories.stack(AndroidKind.AXE).getItem() != net.minecraft.world.item.Items.AIR, "斧型の設計メモリのアイテムがない");
        player.discard();
        helper.succeed();
    }

    /** 斧型の叩きつけ: 体の前へまっすぐ走る衝撃波が、線の上の相手にだけ当たる。 */
    @GameTest(template = HUGE, batch = BATCH + "_1", timeoutTicks = 40)
    public static void axeShockwaveRunsStraight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AxeAndroid axe = SinguloEntities.AXE_ANDROID.get().create(helper.getLevel());
        axe.setNoAi(true);
        axe.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 2))));
        helper.getLevel().addFreshEntity(axe);
        Zombie ahead = dummy(helper, new BlockPos(7, 1, 6));
        Zombie aside = dummy(helper, new BlockPos(11, 1, 6));
        helper.runAtTickTime(10, () -> {
            axe.setYRot(0);
            axe.yBodyRot = 0;                       // 南（+Z）を向く
            ahead.setOnGround(true);                // AI のない的は、床の上でも接地の印が立たない
            aside.setOnGround(true);
            int hit = axe.shockwave(helper.getLevel());
            helper.assertTrue(hit == 1, "衝撃波の当たった数が1でない: " + hit);
            helper.assertTrue(ahead.getHealth() < 200, "前の相手に衝撃波が当たらない");
            helper.assertTrue(aside.getHealth() == 200, "線から外れた相手に衝撃波が当たった");
            axe.discard();
            ahead.discard();
            aside.discard();
            helper.succeed();
        });
    }

    /** ビット型のビット: 見通しがあれば光線で撃ち、壁があれば撃たない。 */
    @GameTest(template = HUGE, batch = BATCH + "_2", timeoutTicks = 40)
    public static void bitBeamNeedsLineOfSight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = SinguloEntities.BIT_ANDROID.get().create(helper.getLevel());
        bit.setNoAi(true);
        bit.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 2))));
        helper.getLevel().addFreshEntity(bit);
        Zombie target = dummy(helper, new BlockPos(7, 1, 11));
        helper.runAtTickTime(5, () -> {
            bit.setYRot(0);
            bit.yBodyRot = 0;
            bit.setDeployed(BitAndroid.BITS_OUT);
            helper.assertTrue(bit.deployed() == BitAndroid.BITS_OUT, "ビットが浮かばない");
            helper.assertTrue(bit.fire(helper.getLevel(), 0, target), "見通しがあるのに撃たない");
            helper.assertTrue(target.getHealth() < 200 && bit.shotFlash() > 0, "光線が当たらない");
            for (int x = 1; x <= 13; x++) {
                for (int y = 1; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
                }
            }
            float h = target.getHealth();
            for (int i = 0; i < BitAndroid.BITS_OUT; i++) {
                helper.assertFalse(bit.fire(helper.getLevel(), i, target), "壁ごしに撃った（ビット " + i + "）");
            }
            helper.assertTrue(target.getHealth() == h, "壁ごしに光線が当たった");
            bit.discard();
            target.discard();
            helper.succeed();
        });
    }
    private static BitAndroid bit(GameTestHelper helper) {
        BitAndroid bit = SinguloEntities.BIT_ANDROID.get().create(helper.getLevel());
        bit.setNoAi(true);
        bit.setNoGravity(true);
        bit.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 3))));
        bit.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 1, 3)), 0, 0);
        helper.getLevel().addFreshEntity(bit);
        bit.setDeployed(BitAndroid.BITS_OUT);
        return bit;
    }

    /** 突進は予告後に一度だけ当たり、帰還では再度当てない。別エンティティも増やさない。 */
    @GameTest(template = HUGE, batch = BATCH + "_slash", timeoutTicks = 60)
    public static void bitSlashTravelsAndReturns(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(bit.slash(helper.getLevel(), 0, target), "突進を開始しない");
            helper.assertTrue(target.getHealth() == 200, "予告中に即時命中した");
            helper.assertTrue(bit.bladeOut(0, 0), "先端に刃が出ない");
            helper.assertFalse(bit.fire(helper.getLevel(), 0, target), "突進中の同じビットが射撃した");
        });
        helper.runAtTickTime(18, () -> {
            helper.assertTrue(target.getHealth() == 200 - BitAndroid.SLASH_DAMAGE, "突進が一度だけ命中していない: " + target.getHealth());
            helper.assertFalse(bit.bladeOut(0, 0), "帰還中も刃が出ている");
        });
        helper.runAtTickTime(33, () -> {
            helper.assertTrue(bit.slashBit() == -1, "ビットが帰還しない");
            helper.assertTrue(target.getHealth() == 200 - BitAndroid.SLASH_DAMAGE, "帰還中に再命中した");
            bit.discard(); target.discard(); helper.succeed();
        });
    }

    /** 発射後の壁の追加にも止まり、護衛の味方には当てない。 */
    @GameTest(template = HUGE, batch = BATCH + "_wall", timeoutTicks = 50)
    public static void bitSlashStopsAtNewWall(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(bit.slash(helper.getLevel(), 0, target), "突進を開始しない");
            for (int x = 1; x <= 13; x++) for (int y = 1; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        });
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(target.getHealth() == 200, "新しい壁を通り抜けて命中した");
            helper.assertFalse(bit.slash(helper.getLevel(), 1, target), "壁ごしに突進を開始した");
            helper.assertFalse(bit.slash(helper.getLevel(), -1, target), "不正なビット番号を受け付けた");
            bit.setDeployed(0);
            helper.assertFalse(bit.slash(helper.getLevel(), 0, target), "格納中に突進した");
            bit.discard(); target.discard(); helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = BATCH + "_ally", timeoutTicks = 50)
    public static void bitSlashProtectsAlliesAndTracksSeparately(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        BitAndroid ally = bit(helper);
        ally.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 7))));
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(bit.slash(helper.getLevel(), 0, ally), "味方に突進した");
            Vec3 before = bit.bitPosition(1, 1);
            bit.setPos(bit.getX() + 3, bit.getY(), bit.getZ());
            Vec3 after = bit.bitPosition(1, 1);
            helper.assertTrue(before.distanceTo(after) < 0.01, "移動と同時にビットが貼り付いて動いた");
            bit.setPos(bit.getX() - 3, bit.getY(), bit.getZ());
            helper.assertTrue(bit.slash(helper.getLevel(), 0, target), "突進を開始しない");
        });
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(ally.getHealth() == ally.getMaxHealth(), "射線上の味方に命中した");
            helper.assertTrue(target.getHealth() < 200, "味方の後ろの敵に命中しない");
            bit.discard(); ally.discard(); target.discard(); helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = BATCH + "_cancel", timeoutTicks = 30)
    public static void bitSlashCancelsWhenStowed(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(bit.slash(helper.getLevel(), 0, target), "突進を開始しない");
            bit.setDeployed(0);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(bit.slashBit() == -1 && target.getHealth() == 200, "格納後も攻撃が続いた");
            bit.discard(); target.discard(); helper.succeed();
        });
    }

    /** 半減後の速い射撃でも、突進直後の光線を相手の無敵時間で無駄にしない。実際の戦闘Goalで検査する。 */
    @GameTest(template = HUGE, batch = BATCH + "_cadence", timeoutTicks = 160)
    public static void enragedBitLungeLeavesRoomForNextBeam(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        bit.setHealth(bit.getMaxHealth() / 2);
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        // Keep the timing target inside the cleared arena after beam knockback.
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        bit.setTarget(target);
        bit.setNoAi(false);
        float[] previous = {target.getHealth()};
        boolean[] slashHit = {false}, checked = {false};
        helper.onEachTick(() -> {
            float lost = previous[0] - target.getHealth();
            if (Math.abs(lost - BitAndroid.SLASH_DAMAGE) < 0.01F) slashHit[0] = true;
            if (slashHit[0] && bit.shotFlash() == BitAndroid.FLASH - 1) {
                helper.assertTrue(Math.abs(lost - BitAndroid.BIT_DAMAGE) < 0.01F,
                        "突進後の光線が無敵時間に消えた: lost=" + lost + " slashTime=" + bit.slashTime(0));
                checked[0] = true;
            }
            previous[0] = target.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(checked[0], "突進命中後の光線を検査できない: slashHit=" + slashHit[0] + " target=" + target.position() + " health=" + target.getHealth() + " bit=" + bit.position() + " charge=" + bit.charge() + " tick=" + bit.tickCount + " state=" + bit.state() + " targetMatches=" + (bit.getTarget() == target) + " sight=" + bit.hasLineOfSight(target) + " shot=" + bit.shotBit());
            bit.discard(); target.discard();
        });
    }

    /** GameTestの遠い座標でも、同期した狙いの小数部分を丸めて失わない。 */
    @GameTest(template = HUGE, batch = BATCH + "_precision", timeoutTicks = 40)
    public static void bitAimKeepsFractionalCoordinates(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BitAndroid bit = bit(helper);
        Zombie target = dummy(helper, new BlockPos(7, 2, 10));
        target.setNoGravity(true);
        target.setPos(target.position().add(0.375, 0.125, 0.125));
        helper.runAtTickTime(5, () -> {
            Vec3 center = target.getBoundingBox().getCenter();
            helper.assertTrue(bit.fire(helper.getLevel(), 0, target), "光線を撃たない");
            helper.assertTrue(bit.shotAt().distanceTo(center) < 0.0001, "光線の狙いの小数を失った");
            helper.assertTrue(bit.slash(helper.getLevel(), 1, target), "突進を開始しない");
            helper.assertTrue(bit.slashAim().distanceTo(center) < 0.0001, "突進の狙いの小数を失った");
        });
        helper.runAtTickTime(30, () -> {
            bit.discard(); target.discard(); helper.succeed();
        });
    }

}
