package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidFabricatorBlockEntity;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidLaser;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
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

    /** 遺構のアンドロイドが1体出撃中の印刷台を置く（出撃数2・印刷済み2）。 */
    private static AndroidFabricatorBlockEntity fabricatorWith(GameTestHelper helper, BlockPos at, RuinAndroid a) {
        helper.setBlock(at, SinguloBlocks.RUIN_ANDROID_FABRICATOR.get());
        AndroidFabricatorBlockEntity f = helper.getBlockEntity(at);
        CompoundTag tag = f.saveWithFullMetadata(helper.getLevel().registryAccess());
        tag.putString("kind", AndroidKind.BLADE.id);
        tag.putBoolean("wave", true);
        tag.putInt("wave_size", 2);
        tag.putInt("printed", 2);
        ListTag ids = new ListTag();
        ids.add(NbtUtils.createUUID(a.getUUID()));
        tag.put("androids", ids);
        f.loadWithComponents(tag, helper.getLevel().registryAccess());
        return f;
    }

    private static BladeAndroid ruinAndroid(GameTestHelper helper, BlockPos fabricator) {
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.setNoAi(true);
        a.setHome(helper.absolutePos(fabricator));
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 5))));
        helper.getLevel().addFreshEntity(a);
        return a;
    }

    private static int memories(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(4),
                e -> e.getItem().is(item(AndroidKind.BLADE.memoryId()))).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** 読み込みが外れただけ（見つからないだけ）の生きているアンドロイドは、倒した扱いにならない（設計メモリは出ない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void fabricatorKeepsUnloadedAndroid(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        BladeAndroid a = ruinAndroid(helper, at);
        AndroidFabricatorBlockEntity f = fabricatorWith(helper, at, a);
        a.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(memories(helper, at) == 0, "読み込まれていないだけのアンドロイドで設計メモリが出た");
            helper.assertTrue(f.alive() == 1, "読み込まれていないアンドロイドを出撃から外した");
            helper.succeed();
        });
    }

    /** 出撃したアンドロイドが全部倒されたら、設計メモリが1つだけ出る。 */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void fabricatorDropsMemoryWhenAllDefeated(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        BladeAndroid a = ruinAndroid(helper, at);
        AndroidFabricatorBlockEntity f = fabricatorWith(helper, at, a);
        a.kill();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(f.alive() == 0, "倒したアンドロイドが出撃に残っている");
            helper.assertTrue(memories(helper, at) == 1, "設計メモリが1つ出ない: " + memories(helper, at));
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(4))) {
                e.discard();
            }
            helper.succeed();
        });
    }

    /** 護衛の技はボス・中立の動物・村人・プレイヤーに当たらず、敵対モブには当たる。遺構の技は仲間以外に当たる。 */
    @GameTest(template = EMPTY)
    public static void guardAttacksSpareBossesAndNeutrals(GameTestHelper helper) {
        var level = helper.getLevel();
        BladeAndroid guard = SinguloEntities.BLADE_ANDROID.get().create(level);
        guard.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        BladeAndroid ruin = SinguloEntities.BLADE_ANDROID.get().create(level);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        List<LivingEntity> spared = List.of(SinguloEntities.GRAVITY_REMNANT.get().create(level), EntityType.COW.create(level),
                EntityType.VILLAGER.create(level), player);
        for (LivingEntity e : spared) {
            helper.assertFalse(RuinAndroid.canHit(true, guard, e), "護衛の技が当たる: " + e.getType());
            helper.assertFalse(RuinAndroid.canHit(true, null, e), "出した護衛がいない技が当たる: " + e.getType());
        }
        var zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(RuinAndroid.canHit(true, guard, zombie), "護衛の技が敵対モブに当たらない");
        helper.assertTrue(RuinAndroid.canHit(false, ruin, EntityType.VILLAGER.create(level)), "遺構の技が村人に当たらない");
        helper.assertFalse(RuinAndroid.canHit(false, ruin, SinguloEntities.WHIP_ANDROID.get().create(level)), "遺構の技が遺構の仲間に当たる");
        helper.succeed();
    }

    /** 出した護衛がいなくなっても、光柱は村人を傷つけず、敵対モブだけを傷つける。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void orphanGuardLaserSparesVillagers(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.STONE);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
        BladeAndroid guard = SinguloEntities.BLADE_ANDROID.get().create(level);
        guard.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        guard.setNoAi(true);
        guard.moveTo(at.add(3, 0, 0));
        level.addFreshEntity(guard);
        var villager = EntityType.VILLAGER.create(level);
        var zombie = EntityType.ZOMBIE.create(level);
        for (var e : List.<net.minecraft.world.entity.Mob>of(villager, zombie)) {
            e.setNoAi(true);
            e.moveTo(at);
            level.addFreshEntity(e);
        }
        AndroidLaser.spawn(level, guard, at);
        AndroidLaser laser = level.getEntitiesOfClass(AndroidLaser.class, new AABB(at, at).inflate(3)).getFirst();
        guard.remove(Entity.RemovalReason.KILLED);
        laser.tickCount = AndroidLaser.WARN;
        laser.tick();
        helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "出した護衛がいない光柱が村人を傷つけた");
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "出した護衛がいない光柱が敵対モブを傷つけない");
        laser.discard();
        villager.discard();
        zombie.discard();
        helper.succeed();
    }

    /** 同じ場所にステーションを置き直しても、前の護衛は身寄りのないアンドロイドになり、新しいステーションでリンクできる。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void replacedStationRelinksOldGuard(GameTestHelper helper) {
        station(helper, 1);
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid old = androids(helper).get(0);
            old.moveTo(old.position().add(3, 0, 0));
            helper.setBlock(STATION, net.minecraft.world.level.block.Blocks.AIR);
            AndroidStationBlockEntity s = station(helper, 0);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(old.orphan(), "置き直したステーションの前の護衛が身寄りのないアンドロイドにならない");
                s.press(AndroidStationBlockEntity.BUTTON_LINK);
                s.press(AndroidStationBlockEntity.BUTTON_MODE_FOLLOW);
                helper.assertTrue(!old.orphan() && helper.absolutePos(STATION).equals(old.home()), "新しいステーションにリンクできない");
                helper.assertTrue(old.mode() == RuinAndroid.MODE_FOLLOW && s.manages(old.getUUID()), "新しいステーションで操作できない");
                old.discard();
                helper.succeed();
            });
        });
    }

    /**
     * 許可証: 持ち主と登録した人だけが操作でき、追従する人を登録した人から選べる。
     * 追従中「ついていくだけ」なら敵を狙わず、「護衛する」なら狙う。
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void permitAndFollowSettings(GameTestHelper helper) {
        AndroidStationBlockEntity s = station(helper, 1);
        var owner = helper.makeMockServerPlayerInLevel();
        var friend = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        s.setOwner(owner.getUUID());
        ItemStack permit = new ItemStack(item("shield_permit"));
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, owner);
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, friend);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PERMIT, permit);
        helper.assertTrue(s.mayOperate(owner) && s.mayOperate(friend), "持ち主か登録した人がステーションを使えない");
        helper.assertFalse(s.mayOperate(stranger), "登録していない人がステーションを使える");
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            s.press(AndroidStationBlockEntity.BUTTON_MODE_FOLLOW);
            s.press(AndroidStationBlockEntity.BUTTON_FOLLOW_ONLY);
            helper.assertTrue(a.ownerPlayer() == owner, "はじめは持ち主に追従しない");
            s.press(AndroidStationBlockEntity.BUTTON_LEADER);
            helper.assertTrue(a.ownerPlayer() == friend, "選んだ登録者に追従しない");
            s.press(AndroidStationBlockEntity.BUTTON_LEADER);
            helper.assertTrue(a.ownerPlayer() == owner, "切り替えが持ち主に戻らない");
            helper.assertTrue(a.mayOperate(friend) && !a.mayOperate(stranger), "アンドロイドの画面を開ける人が許可証と合わない");
            var zombie = EntityType.ZOMBIE.create(helper.getLevel());
            zombie.setNoAi(true);
            zombie.moveTo(a.position().add(3, 0, 0));
            helper.getLevel().addFreshEntity(zombie);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(a.getTarget() == null, "ついていくだけなのに敵を狙う");
                s.press(AndroidStationBlockEntity.BUTTON_ESCORT);
                helper.succeedWhen(() -> {
                    helper.assertTrue(a.getTarget() == zombie || !zombie.isAlive(), "護衛するのに敵を狙わない");
                    zombie.discard();
                    a.discard();
                });
            });
        });
    }

    /** アンドロイドの画面で「追従のみ／追従・護衛」を切り替えると、ステーションの設定も変わり、上書きで戻らない。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void androidScreenSwitchesEscort(GameTestHelper helper) {
        AndroidStationBlockEntity s = station(helper, 1);
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            s.press(AndroidStationBlockEntity.BUTTON_MODE_FOLLOW);
            a.chooseEscort(false);
            helper.assertFalse(s.escort(), "アンドロイドの画面の切り替えがステーションに伝わらない");
            helper.runAfterDelay(45, () -> {
                helper.assertFalse(a.escort(), "ステーションの設定で切り替えが戻った");
                a.chooseEscort(true);
                helper.assertTrue(s.escort() && a.escort(), "護衛に戻せない");
                a.discard();
                helper.succeed();
            });
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
