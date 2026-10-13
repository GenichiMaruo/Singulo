package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** アンドロイド（充電・護衛・組立ステーション・転送アップグレード・アンドロイド用のガラス）の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos STATION = new BlockPos(2, 1, 2);

    private AndroidGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** 電力・設計メモリ・キットのあるステーションを置く。 */
    private static AndroidStationBlockEntity station(GameTestHelper helper, int kits) {
        helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity s = helper.getBlockEntity(STATION);
        s.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, new ItemStack(item(AndroidKind.BLADE.memoryId())));
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_KIT, new ItemStack(item("android_kit"), kits));
        return s;
    }

    private static List<RuinAndroid> androids(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(RuinAndroid.class, new AABB(helper.absolutePos(STATION)).inflate(8));
    }

    /** ステーションは、メモリとキットと電力で護衛を1体だけ印刷し、キットと電力を使う。印刷中は傷つかない。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void stationPrintsOneGuard(GameTestHelper helper) {
        AndroidStationBlockEntity s = station(helper, 3);
        helper.runAtTickTime(40, () -> {
            List<RuinAndroid> list = androids(helper);
            helper.assertTrue(list.size() == 1, "1体だけ印刷されない: " + list.size());
            RuinAndroid a = list.get(0);
            helper.assertTrue(a instanceof BladeAndroid && a.isGuard() && !a.orphan(), "光刃型の護衛にならない");
            helper.assertTrue(s.items().getStackInSlot(AndroidStationBlockEntity.SLOT_KIT).getCount() == 2, "キットを1つ使っていない");
            helper.assertTrue(s.energy().getEnergyStored() == AndroidStationBlockEntity.CAPACITY - AndroidStationBlockEntity.PRINT_FE,
                    "印刷の電力を使っていない");
            helper.assertTrue(!a.printed(), "印刷がもう終わっている");
            helper.assertFalse(a.hurt(helper.getLevel().damageSources().magic(), 5), "印刷中に傷つく");
            a.discard();
            helper.succeed();
        });
    }

    /** 護衛はプレイヤーの攻撃で傷つかず、ふつうのダメージでは傷つく。体力はコンパレーターに出る。 */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void guardIgnoresPlayersAndReportsHealth(GameTestHelper helper) {
        AndroidStationBlockEntity s = station(helper, 1);
        var player = helper.makeMockServerPlayerInLevel();
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            float before = a.getHealth();
            a.hurt(helper.getLevel().damageSources().playerAttack(player), 10);
            helper.assertTrue(a.getHealth() == before, "プレイヤーの攻撃で傷ついた");
            a.invulnerableTime = 0;
            a.hurt(helper.getLevel().damageSources().magic(), 15);
            helper.assertTrue(a.getHealth() < before, "ふつうのダメージで傷つかない");
            // 体力を知らせる（毎秒）のを待って、コンパレーターの値を見る
            helper.runAfterDelay(25, () -> {
                int signal = s.signal();
                int want = (int) Math.ceil(15 * a.getHealth() / a.getMaxHealth());
                helper.assertTrue(signal == want, "コンパレーターの値が体力と合わない: " + signal + " / " + want);
                a.discard();
                helper.succeed();
            });
        });
    }

    /** 転送アップグレードがあれば、倒れた護衛の持ち物はステーションへ送られ、次に印刷した護衛が持つ。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void transferUpgradePassesInventory(GameTestHelper helper) {
        AndroidStationBlockEntity s = station(helper, 2);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_UPGRADE, new ItemStack(item("android_transfer_upgrade")));
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid first = androids(helper).get(0);
            first.inventory().setItem(RuinAndroid.SLOT_REPAIR, new ItemStack(item("android_repair_kit"), 5));
            first.kill();
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(STATION)).inflate(8), e -> e.getItem().is(item("android_repair_kit"))).isEmpty(),
                    "転送アップグレードがあるのに持ち物が落ちた");
        });
        helper.succeedWhen(() -> {
            List<RuinAndroid> list = androids(helper).stream().filter(RuinAndroid::isAlive).toList();
            helper.assertTrue(list.size() == 1, "印刷し直されない");
            ItemStack kits = list.get(0).inventory().getItem(RuinAndroid.SLOT_REPAIR);
            helper.assertTrue(kits.getCount() == 5, "持ち物が次の護衛に渡らない: " + kits);
            list.get(0).discard();
        });
    }

    /** 遮断ガラスはアンドロイドだけを止め、透過ガラスはアンドロイドだけを通す。 */
    @GameTest(template = EMPTY)
    public static void androidGlassFiltersAndroids(GameTestHelper helper) {
        BladeAndroid android = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        var pig = EntityType.PIG.create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        var barrier = SinguloBlocks.ANDROID_BARRIER_GLASS.get().defaultBlockState();
        var gate = SinguloBlocks.ANDROID_GATE_GLASS_PATTERNED.get().defaultBlockState();
        var level = helper.getLevel();
        helper.assertFalse(barrier.getCollisionShape(level, at, CollisionContext.of(android)).isEmpty(), "遮断ガラスをアンドロイドが通れる");
        helper.assertTrue(barrier.getCollisionShape(level, at, CollisionContext.of(pig)).isEmpty(), "遮断ガラスをほかの生き物が通れない");
        helper.assertTrue(gate.getCollisionShape(level, at, CollisionContext.of(android)).isEmpty(), "透過ガラスをアンドロイドが通れない");
        helper.assertFalse(gate.getCollisionShape(level, at, CollisionContext.of(pig)).isEmpty(), "透過ガラスをほかの生き物が通れる");
        android.discard();
        pig.discard();
        helper.succeed();
    }
}
