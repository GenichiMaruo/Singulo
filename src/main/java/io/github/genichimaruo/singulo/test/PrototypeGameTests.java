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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 地下アンドロイド研究所の試作機（斧型「アルマ」・ビット型「ノクス」）と、その設計メモリ。 */
@GameTestHolder(Singulo.MODID)
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
        return z;
    }

    /** 試作機の型は遺構の印刷台では印刷しない（試作機の型だけが研究所の型）。 */
    @GameTest(template = "empty")
    public static void prototypesAreLabOnly(GameTestHelper helper) {
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
        var player = helper.makeMockServerPlayerInLevel();
        Set<AndroidKind> got = new HashSet<>();
        got.add(PrototypeMemories.next(player, player.getRandom()));
        got.add(PrototypeMemories.next(player, player.getRandom()));
        helper.assertTrue(got.size() == 2, "2回で両方の型がそろわない: " + got);
        AndroidKind third = PrototypeMemories.next(player, player.getRandom());
        helper.assertTrue(third == AndroidKind.AXE || third == AndroidKind.BIT, "3回目に試作機の型が出ない: " + third);
        helper.assertTrue(player.getData(PrototypeMemories.RECEIVED).size() == 2, "受け取った型の記録が2つでない");
        helper.assertTrue(PrototypeMemories.stack(AndroidKind.AXE).getItem() != net.minecraft.world.item.Items.AIR, "斧型の設計メモリのアイテムがない");
        player.discard();
        helper.succeed();
    }

    /** 斧型の叩きつけ: 体の前へまっすぐ走る衝撃波が、線の上の相手にだけ当たる。 */
    @GameTest(template = HUGE, batch = BATCH + "_1", timeoutTicks = 40)
    public static void axeShockwaveRunsStraight(GameTestHelper helper) {
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
}
