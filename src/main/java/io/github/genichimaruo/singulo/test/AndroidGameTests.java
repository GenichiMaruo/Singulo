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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** アンドロイド（充電・護衛・組立ステーション・転送アップグレード・アンドロイド用のガラス）の確認。 */
@GameTestHolder("singulo_automation")
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
        // A printed guard needs walkable ground beside the station to clear its top.
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        }
        helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity s = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, STATION);
        s.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, new ItemStack(item(AndroidKind.BLADE.memoryId())));
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_KIT, new ItemStack(item("android_kit"), kits));
        return s;
    }

    private static List<RuinAndroid> androids(GameTestHelper helper) {
        // Adjacent 1.20.1 test fixtures can be within eight blocks; only count this station's guards.
        return helper.getLevel().getEntitiesOfClass(RuinAndroid.class, new AABB(helper.absolutePos(STATION)).inflate(8),
                a -> helper.absolutePos(STATION).equals(a.home()));
    }

    /** ステーションは、メモリとキットと電力で護衛を1体だけ印刷し、キットと電力を使う。印刷中は傷つかない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 100)
    public static void stationPrintsOneGuard(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 160)
    public static void guardIgnoresPlayersAndReportsHealth(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400)
    public static void transferUpgradePassesInventory(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 2);
        // A placed station belongs to its player; unrelated orphan fixtures must not block this reprint.
        s.setOwner(java.util.UUID.randomUUID());
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
            helper.assertTrue(list.size() == 1, "印刷し直されない: count=" + list.size() + ", station=" + s.saveWithoutMetadata());
            ItemStack kits = list.get(0).inventory().getItem(RuinAndroid.SLOT_REPAIR);
            helper.assertTrue(kits.getCount() == 5, "持ち物が次の護衛に渡らない: " + kits);
            list.get(0).discard();
        });
    }

    /** 遺構のアンドロイドが1体出撃中の印刷台を置く（出撃数2・印刷済み2）。 */
    private static AndroidFabricatorBlockEntity fabricatorWith(GameTestHelper helper, BlockPos at, RuinAndroid a) {
        helper.setBlock(at, SinguloBlocks.RUIN_ANDROID_FABRICATOR.get());
        AndroidFabricatorBlockEntity f = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, at);
        CompoundTag tag = f.saveWithFullMetadata();
        tag.putString("kind", AndroidKind.BLADE.id);
        tag.putBoolean("wave", true);
        tag.putInt("wave_size", 2);
        tag.putInt("printed", 2);
        ListTag ids = new ListTag();
        ids.add(NbtUtils.createUUID(a.getUUID()));
        tag.put("androids", ids);
        f.load(tag);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 100)
    public static void fabricatorKeepsUnloadedAndroid(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 160)
    public static void fabricatorDropsMemoryWhenAllDefeated(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void guardAttacksSpareBossesAndNeutrals(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        BladeAndroid guard = SinguloEntities.BLADE_ANDROID.get().create(level);
        guard.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        BladeAndroid ruin = SinguloEntities.BLADE_ANDROID.get().create(level);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60)
    public static void orphanGuardLaserSparesVillagers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
        AndroidLaser laser = level.getEntitiesOfClass(AndroidLaser.class, new AABB(at, at).inflate(3)).get(0);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 300)
    public static void replacedStationRelinksOldGuard(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400)
    public static void permitAndFollowSettings(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var owner = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var friend = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var stranger = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
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
            // 索敵の AI のタイミングによらないよう、狙いを直接付けて、毎 tick の見直しで外されるかを見る
            a.setNoAi(true);
            owner.moveTo(a.position());
            var zombie = EntityType.ZOMBIE.create(helper.getLevel());
            zombie.setNoAi(true);
            zombie.moveTo(a.position().add(3, 0, 0));
            helper.getLevel().addFreshEntity(zombie);
            a.setTarget(zombie);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(a.getTarget() == null, "ついていくだけなのに敵を狙う");
                s.press(AndroidStationBlockEntity.BUTTON_ESCORT);
                a.setTarget(zombie);
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(a.getTarget() == zombie, "護衛するのに敵を狙わない");
                    zombie.discard();
                    a.discard();
                    helper.succeed();
                });
            });
        });
    }

    /** 許可証から外された人は、開いていたアンドロイドの画面でも、名前の変更・解体・持ち物の出し入れができない。持ち主はできる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void removedMemberLosesAndroidScreen(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var owner = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var friend = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        s.setOwner(owner.getUUID());
        ItemStack permit = new ItemStack(item("shield_permit"));
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, owner);
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, friend);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PERMIT, permit);
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            owner.moveTo(a.position());
            friend.moveTo(a.position());
            var friendMenu = new io.github.genichimaruo.singulo.android.AndroidMenu(1, friend.getInventory(), a);
            var ownerMenu = new io.github.genichimaruo.singulo.android.AndroidMenu(2, owner.getInventory(), a);
            helper.assertTrue(friendMenu.stillValid(friend), "登録した人が画面を使えない");
            // 許可証を持ち主だけのものに替えて、アンドロイドへ伝える
            ItemStack ownerOnly = new ItemStack(item("shield_permit"));
            io.github.genichimaruo.singulo.item.ShieldPermitItem.add(ownerOnly, owner);
            s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PERMIT, ownerOnly);
            s.press(AndroidStationBlockEntity.BUTTON_MODE_GUARD);
            helper.assertFalse(friendMenu.stillValid(friend), "外された人の画面が開いたまま使える");
            friendMenu.rename(friend, "Intruder");
            helper.assertFalse(a.hasCustomName(), "外された人が名前を変えられる");
            helper.assertFalse(friendMenu.clickMenuButton(friend, io.github.genichimaruo.singulo.android.AndroidMenu.BUTTON_DISMANTLE),
                    "外された人が解体できる");
            helper.assertTrue(ownerMenu.stillValid(owner), "持ち主の画面が使えなくなった");
            ownerMenu.rename(owner, "Mine");
            helper.assertTrue(a.hasCustomName(), "持ち主が名前を変えられない");
            a.discard();
            helper.succeed();
        });
    }

    /** アンドロイドの画面で「追従のみ／追従・護衛」を切り替えると、ステーションの設定も変わり、上書きで戻らない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void androidScreenSwitchesEscort(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    /** 生産アップグレードがあれば2体まで印刷する（1体目がステーションの上からどいてから、2体目を印刷する）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 600, batch = "android_station_production")
    public static void productionUpgradePrintsTwo(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 3);
        s.setOwner(java.util.UUID.randomUUID());
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PRODUCTION, new ItemStack(item("android_production_upgrade")));
        helper.succeedWhen(() -> {
            List<RuinAndroid> list = androids(helper).stream().filter(RuinAndroid::isAlive).toList();
            helper.assertTrue(list.size() == 2, "2体印刷されない: " + list.size() + " station=" + s.saveWithoutMetadata());
            helper.assertTrue(list.stream().allMatch(a -> s.manages(a.getUUID())), "ステーションが2体とも受け持っていない");
            helper.assertTrue(s.items().getStackInSlot(AndroidStationBlockEntity.SLOT_KIT).getCount() == 1, "キットを2つ使っていない");
            list.forEach(RuinAndroid::discard);
        });
    }

    /** バッテリーアップグレード1個につき充電の満タンが50%増え、外すと満タンに合わせて減る。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void batteryUpgradeRaisesCharge(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        helper.assertTrue(a.maxCharge() == RuinAndroid.CHARGE_MAX, "アップグレードなしの満タンが違う");
        a.inventory().setItem(RuinAndroid.SLOT_BATTERY, new ItemStack(item("android_battery_upgrade"), 2));
        helper.assertTrue(a.batteries() == 2 && a.maxCharge() == RuinAndroid.CHARGE_MAX * 2, "2個で満タンが2倍にならない");
        CompoundTag tag = a.saveWithoutId(new CompoundTag());
        tag.putInt("charge", RuinAndroid.CHARGE_MAX * 2);
        a.load(tag);
        helper.assertTrue(a.charge() == RuinAndroid.CHARGE_MAX * 2, "読み込みで充電が満タンを超えた分を失った: " + a.charge());
        a.inventory().setItem(RuinAndroid.SLOT_BATTERY, new ItemStack(item("android_battery_upgrade"), 1));
        helper.assertTrue(a.maxCharge() == RuinAndroid.CHARGE_MAX * 3 / 2 && a.charge() == a.maxCharge(), "外したときに充電が満タンに合わない");
        a.discard();
        helper.succeed();
    }

    /** 持ち物は位置ごと保存する（空きスロットがあってもずれない）。0.6.0 の保存も、装甲板を装甲板の場所へ戻す。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void androidInventoryKeepsSlots(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        a.inventory().setItem(RuinAndroid.SLOT_PLATE, new ItemStack(item("android_armor_plate")));
        a.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(item("android_backpack_upgrade")));
        a.inventory().setItem(RuinAndroid.PACK_START + 7, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 12));
        CompoundTag tag = a.saveWithoutId(new CompoundTag());
        BladeAndroid b = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        b.load(tag);
        helper.assertTrue(RuinAndroid.isArmorPlate(b.inventory().getItem(RuinAndroid.SLOT_PLATE)), "装甲板の場所がずれた");
        helper.assertTrue(b.inventory().getItem(RuinAndroid.SLOT_REPAIR).isEmpty(), "修理キットの場所に物が入った");
        helper.assertTrue(b.inventory().getItem(RuinAndroid.PACK_START + 7).getCount() == 12 && b.hasBackpack(), "バックパックの中身の場所がずれた");
        // 0.6.0 までの保存（位置なしの並び）
        CompoundTag legacy = a.saveWithoutId(new CompoundTag());
        legacy.remove("items");
        ListTag list = new ListTag();
        list.add(new ItemStack(item("android_armor_plate")).save(new net.minecraft.nbt.CompoundTag()));
        legacy.put("inventory", list);
        BladeAndroid c = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        c.load(legacy);
        helper.assertTrue(RuinAndroid.isArmorPlate(c.inventory().getItem(RuinAndroid.SLOT_PLATE)), "古い保存の装甲板が装甲板の場所に戻らない");
        a.discard();
        b.discard();
        c.discard();
        helper.succeed();
    }

    /** 作業の場所: 指令台・資材口（隣にチェスト）・納品口・レシピ棚・作業台。 */
    private static final BlockPos CONSOLE = new BlockPos(0, 1, 0), SUPPLY = new BlockPos(1, 1, 0), CHEST = new BlockPos(1, 1, 1),
            DELIVERY = new BlockPos(3, 1, 0), RACK = new BlockPos(4, 1, 0), TABLE = new BlockPos(4, 1, 3);

    private static ItemStack recipeCard(GameTestHelper helper, java.util.List<ItemStack> grid, ItemStack output) {
        ItemStack card = new ItemStack(item("recipe_card"));
        new io.github.genichimaruo.singulo.android.work.RecipeData(grid, output).write(card, helper.getLevel().registryAccess());
        return card;
    }

    /**
     * 作業の工場を組む: 指令台に資材口・納品口・レシピ棚を作業位置カードでつなぎ、棚に「原木→板材」「板材→棒」のレシピを並べる。
     * チェストには原木 logs 本。
     */
    private static io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity workshop(GameTestHelper helper, int logs) {
        var level = helper.getLevel();
        // The empty structure contains air, including y=0. Give the factory a real
        // floor so irrigation and farmland do not form a floating, two-block ledge.
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        }
        helper.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        helper.setBlock(SUPPLY, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        helper.setBlock(CHEST, net.minecraft.world.level.block.Blocks.CHEST);
        helper.setBlock(DELIVERY, SinguloBlocks.ANDROID_DELIVERY_PORT.get());
        helper.setBlock(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        helper.setBlock(TABLE, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, logs));
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        ItemStack link = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(link, level, helper.absolutePos(CONSOLE),
                SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        for (BlockPos p : List.of(SUPPLY, DELIVERY, RACK)) {
            io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity b = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, p);
            b.useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        ItemStack table = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(table, level, helper.absolutePos(TABLE),
                net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        java.util.List<ItemStack> planks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        planks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        java.util.List<ItemStack> sticks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        sticks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        sticks.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        rack.items().setStackInSlot(0, table);
        rack.items().setStackInSlot(1, recipeCard(helper, planks, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4)));
        rack.items().setStackInSlot(2, recipeCard(helper, sticks, new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
        return io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CONSOLE);
    }

    /** 素材が足りない注文は、計算で止まる（原木1本では棒16本は作れない）。在庫で足りれば作れる。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void workPlanNeedsMaterials(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 1);
        ItemStack stick = new ItemStack(net.minecraft.world.item.Items.STICK);
        var short_ = console.plan(stick, 16);
        helper.assertFalse(short_.ok(), "素材が足りないのに作れることになった");
        var ok = console.plan(stick, 8);
        helper.assertTrue(ok.ok() && ok.steps.size() == 2, "原木1本から棒8本の工程が2つにならない: " + ok.steps.size());
        helper.succeed();
    }

    /** アンドロイドが資材口から原木を取り、作業台で板材と棒を作って、納品口へ棒を届ける。作業中は持ち物を取り出せない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidCraftsAndDelivers")
    public static void androidCraftsAndDelivers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(new BlockPos(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.assertTrue(a.isWorking(), "アンドロイドが作業中にならない");
        io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity port = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, DELIVERY);
        helper.succeedWhen(() -> {
            int sticks = 0;
            for (int i = 0; i < port.handler().getSlots(); i++) {
                if (port.handler().getStackInSlot(i).is(net.minecraft.world.item.Items.STICK)) {
                    sticks += port.handler().getStackInSlot(i).getCount();
                }
            }
            helper.assertTrue(sticks == 8, "棒が8本届かない: " + sticks + " health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " work=" + a.saveWorkState() + " message=" + console.message());
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            helper.assertFalse(a.isWorking(), "注文が終わっても作業中のまま");
            // 作った板材は、資材置き場へ戻さずに、そのまま棒に使う
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
            for (int i = 0; i < chest.getContainerSize(); i++) {
                helper.assertFalse(chest.getItem(i).is(net.minecraft.world.item.Items.OAK_PLANKS), "作った板材を資材置き場へ戻した");
            }
            a.discard();
        });
    }

    /** 納品口に届いた item の数。 */
    private static int delivered(GameTestHelper helper, net.minecraft.world.item.Item item) {
        io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity port = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, DELIVERY);
        int n = 0;
        for (int i = 0; i < port.handler().getSlots(); i++) {
            if (port.handler().getStackInSlot(i).is(item)) {
                n += port.handler().getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    /** 作業を始めたアンドロイド（採掘アップグレードつきにもできる）。 */
    private static BladeAndroid worker(GameTestHelper helper, boolean miner) {
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(new BlockPos(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
        if (miner) {
            a.inventory().setItem(RuinAndroid.SLOT_MINING, new ItemStack(item("android_mining_upgrade")));
        }
        helper.getLevel().addFreshEntity(a);
        return a;
    }

    /** 加工モード: かまどに原鉄を上から入れ、焼けた鉄インゴットを下から取り出して、納品口へ届ける。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidSmeltsInFurnace")
    public static void androidSmeltsInFurnace(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = new BlockPos(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, furnacePos);
        furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2));
        ItemStack loc = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(loc, level, helper.absolutePos(furnacePos),
                net.minecraft.world.level.block.Blocks.FURNACE);
        java.util.List<ItemStack> in = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        in.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        ItemStack card = new ItemStack(item("recipe_card"));
        new io.github.genichimaruo.singulo.android.work.RecipeData(io.github.genichimaruo.singulo.android.work.RecipeData.MODE_PROCESSING, in,
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of()).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS + 1, card);
        BladeAndroid a = worker(helper, false);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2, List.of(a.getUUID()),
                helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 2, "鉄インゴットが2つ届かない" + " pos=" + a.position() + " ticks=" + a.tickCount + " health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " guard=" + a.isGuard() + " printed=" + a.printed() + " target=" + a.getTarget() + " work=" + a.saveWorkState() + " console=" + console.saveWithoutMetadata() + " furnace=" + furnace.saveWithoutMetadata());
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            a.discard();
        });
    }

    /** 同じ種類の別の物も使うレシピ: 見本は樫の板材でも、素材置き場の白樺の板材から棒を作る。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidCraftsWithVariants")
    public static void androidCraftsWithVariants(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.BIRCH_PLANKS, 2));
        // 棒のレシピを、同じ種類も使うものに置き換える（板材の欄は、どの木の板材でもよい）
        java.util.List<ItemStack> sticks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        sticks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        sticks.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        String planks = io.github.genichimaruo.singulo.android.work.ItemCount.key(
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.PLANKS));
        java.util.List<String> alts = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ""));
        alts.set(0, planks);
        alts.set(3, planks);
        ItemStack card = new ItemStack(item("recipe_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.crafting(sticks, new ItemStack(net.minecraft.world.item.Items.STICK, 4), alts)
                .write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(2, card);
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 4,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "白樺の板材で棒を注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 4, "棒が4本届かない: health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " work=" + a.saveWorkState() + " message=" + console.message());
            a.discard();
        });
    }

    /** 加工モードの燃料: かまどの燃料が切れていれば、素材置き場から記録した燃料（石炭）を取ってきて入れる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidFetchesFuel")
    public static void androidFetchesFuel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = new BlockPos(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2));
        chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
        ItemStack loc = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(loc, level, helper.absolutePos(furnacePos),
                net.minecraft.world.level.block.Blocks.FURNACE);
        java.util.List<ItemStack> in = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        in.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        ItemStack card = new ItemStack(item("recipe_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.processing(in, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of(),
                new ItemStack(net.minecraft.world.item.Items.COAL)).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS + 1, card);
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 2, "燃料を取ってきて鉄インゴットを2つ届けない");
            a.discard();
        });
    }

    /** 手順カードの採掘: 採掘アップグレードを積んだアンドロイドが、範囲の石を掘って丸石を集め、納品口へ届ける。積んでいなければ注文できない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidMinesWithProcedure")
    public static void androidMinesWithProcedure(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos from = new BlockPos(0, 1, 5), to = new BlockPos(1, 1, 5);
        helper.setBlock(from, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(to, net.minecraft.world.level.block.Blocks.STONE);
        var step = new io.github.genichimaruo.singulo.android.work.ProcedureStep(io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE,
                helper.absolutePos(from), helper.absolutePos(to), ItemStack.EMPTY);
        ItemStack card = new ItemStack(item("procedure_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.procedure(List.of(step), new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))
                .write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS * 2 + 1, card);   // 場所のカードがない行
        BladeAndroid plain = worker(helper, false);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        ItemStack cobble = new ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
        helper.assertTrue(console.start(player, cobble, 2, List.of(plain.getUUID()), helper.absolutePos(DELIVERY)) != null,
                "採掘アップグレードなしで採掘の注文ができた");
        plain.discard();
        BladeAndroid a = worker(helper, true);
        var error = console.start(player, cobble, 2, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.COBBLESTONE) == 2, "丸石が2つ届かない" + " pos=" + a.position() + " ticks=" + a.tickCount + " health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " guard=" + a.isGuard() + " printed=" + a.printed() + " target=" + a.getTarget() + " work=" + a.saveWorkState() + " console=" + console.saveWithoutMetadata());
            helper.assertTrue(level.getBlockState(helper.absolutePos(from)).isAir() && level.getBlockState(helper.absolutePos(to)).isAir(),
                    "範囲の石が掘られていない");
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            a.discard();
        });
    }

    /**
     * 登録した注文（在庫を保つ）: 素材置き場の板材（1枚）が4枚を下回っているので、指令台が自分で注文し、原木から作って素材置き場へ入れる
     * （今ある板材を予約するだけで終わらせない）。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_standingOrderKeepsStock")
    public static void standingOrderKeepsStock(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        net.minecraft.world.level.block.entity.ChestBlockEntity seed = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        seed.setItem(5, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        BladeAndroid a = worker(helper, false);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var error = console.register(player, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), 4,
                io.github.genichimaruo.singulo.android.work.StandingOrder.KEEP, null, List.of());
        helper.assertTrue(error == null, "登録できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
            int planks = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(net.minecraft.world.item.Items.OAK_PLANKS)) {
                    planks += chest.getItem(i).getCount();
                }
            }
            helper.assertTrue(planks >= 4, "板材が素材置き場に4枚そろわない: " + planks);
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            helper.assertFalse(a.isWorking(), "注文が終わっても作業中のまま");
            a.discard();
        });
    }

    /** 登録した注文（信号で1回）: 信号が入るまでは注文せず、入ったら1回だけ注文して、納品口へ届ける。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_standingOrderOnPulse")
    public static void standingOrderOnPulse(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        BladeAndroid a = worker(helper, false);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        console.register(player, new ItemStack(net.minecraft.world.item.Items.STICK), 4,
                io.github.genichimaruo.singulo.android.work.StandingOrder.PULSE, helper.absolutePos(DELIVERY), List.of());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(console.orders().isEmpty(), "信号がないのに注文した");
            console.onSignal(true);
            console.onSignal(false);
            helper.succeedWhen(() -> {
                helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 4, "棒が4本届かない: health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " work=" + a.saveWorkState() + " message=" + console.message());
                helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
                a.discard();
            });
        });
    }

    /** 作業中に襲われたら、襲ってきた相手を迎え撃ち、片付けたら作業に戻って注文を終える。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_defense", timeoutTicks = 2400)
    public static void workingAndroidFightsBack(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        BladeAndroid a = worker(helper, false);
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        net.minecraft.world.entity.monster.Zombie[] attacker = {null};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.tickCount >= 5 && a.active(), "個体のAIが動き始めるのを待つ"))
                .thenExecute(() -> {
                    var zombie = EntityType.ZOMBIE.create(helper.getLevel());
                    zombie.moveTo(a.position().add(1.5, 0, 0));
                    zombie.setNoAi(true);
                    zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                    zombie.setHealth(200);
                    helper.getLevel().addFreshEntity(zombie);
                    attacker[0] = zombie;
                    a.invulnerableTime = 0;
                    helper.assertTrue(a.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1), "応戦確認用の攻撃が入らなかった");
                    Singulo.LOGGER.info("Android defense fixture: entityTick={}, hurtTick={}, lastAttackerMatches={}, active={}, working={}",
                            a.tickCount, a.getLastHurtByMobTimestamp(), a.getLastHurtByMob() == zombie, a.active(), a.isWorking());
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(a.getTarget() == attacker[0], "作業中に襲ってきた相手を狙わない: tick=" + a.tickCount
                            + " active=" + a.active() + " lastAttacker=" + (a.getLastHurtByMob() == attacker[0]));
                    helper.assertTrue(a.isWorking(), "戦っている間に作業を取り上げられた");
                })
                .thenExecute(() -> attacker[0].discard())
                .thenWaitUntil(() -> helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 8,
                        "片付けたあと作業に戻らない"))
                .thenExecute(a::discard)
                .thenSucceed();
    }

    /** 手順カード1枚（steps、できる物 output）を、場所のカードのない行に並べる。 */
    private static void procedureCard(GameTestHelper helper, List<io.github.genichimaruo.singulo.android.work.ProcedureStep> steps, ItemStack output) {
        ItemStack card = new ItemStack(item("procedure_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.procedure(steps, output).write(card, helper.getLevel().registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS * 3 + 1, card);
    }

    /** 収穫: 育ちきった小麦を取って植え直し、小麦を納品口へ届ける。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidHarvestsAndReplants")
    public static void androidHarvestsAndReplants(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        // Light propagation on 1.20.1 is asynchronous. Plant only after the lit,
        // irrigated fixture is ready, rather than losing crops to survival updates.
        helper.setBlock(new BlockPos(3, 3, 5), net.minecraft.world.level.block.Blocks.GLOWSTONE);
        helper.setBlock(new BlockPos(2, 1, 5), net.minecraft.world.level.block.Blocks.WATER);
        for (int x = 0; x < 2; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int x = 0; x < 2; x++) {
                helper.assertTrue(helper.getLevel().getRawBrightness(helper.absolutePos(new BlockPos(x, 2, 5)), 0) >= 9,
                        "畑の照明更新が終わっていない");
            }
        }).thenExecute(() -> {
            for (int x = 0; x < 2; x++) {
                helper.setBlock(new BlockPos(x, 2, 5), ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT)
                        .getStateForAge(7));
            }
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, helper.absolutePos(new BlockPos(0, 2, 5)),
                helper.absolutePos(new BlockPos(1, 2, 5)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.WHEAT, 2));
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.WHEAT), 2, List.of(a.getUUID()),
                helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.WHEAT) == 2, "小麦が2つ届かない: count=" + delivered(helper, net.minecraft.world.item.Items.WHEAT) + " target=" + a.getTarget() + " health=" + a.getHealth() + " work=" + a.saveWorkState() + " crop0=" + helper.getBlockState(new BlockPos(0, 2, 5)) + " crop1=" + helper.getBlockState(new BlockPos(1, 2, 5)) + " soil0=" + helper.getBlockState(new BlockPos(0, 1, 5)) + " light=" + helper.getLevel().getRawBrightness(helper.absolutePos(new BlockPos(0, 2, 5)), 0) + " message=" + console.message());
            helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 5)).is(net.minecraft.world.level.block.Blocks.WHEAT)
                    && helper.getBlockState(new BlockPos(1, 2, 5)).is(net.minecraft.world.level.block.Blocks.WHEAT), "植え直していない");
            a.discard();
        });
        });
    }

    /** 収穫: 芽生えたアメジストの上の、育ちきった塊だけを取る（芽生えたアメジストは壊さない）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidHarvestsAmethyst")
    public static void androidHarvestsAmethyst(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        helper.setBlock(new BlockPos(1, 1, 6), net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST);
        helper.setBlock(new BlockPos(1, 2, 6), net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, helper.absolutePos(new BlockPos(0, 1, 5)),
                helper.absolutePos(new BlockPos(2, 3, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 4));
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD), 4,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.AMETHYST_SHARD) == 4, "アメジストの欠片が4つ届かない");
            helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 6)).is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST),
                    "芽生えたアメジストを壊した");
            a.discard();
        });
    }

    /** 伐採: 伐採アップグレードを積んだアンドロイドが、木を丸ごと切って原木を届ける。積んでいなければ注文できない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidFellsTree")
    public static void androidFellsTree(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        helper.setBlock(new BlockPos(1, 0, 6), net.minecraft.world.level.block.Blocks.DIRT);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 6), net.minecraft.world.level.block.Blocks.OAK_LOG);
        }
        helper.setBlock(new BlockPos(1, 4, 6), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL, helper.absolutePos(new BlockPos(0, 1, 5)),
                helper.absolutePos(new BlockPos(2, 1, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 3));
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        BladeAndroid plain = worker(helper, false);
        helper.assertTrue(console.start(player, new ItemStack(net.minecraft.world.item.Items.OAK_LOG), 3, List.of(plain.getUUID()),
                helper.absolutePos(DELIVERY)) != null, "伐採アップグレードなしで伐採の注文ができた");
        plain.discard();
        BladeAndroid a = worker(helper, false);
        a.inventory().setItem(RuinAndroid.SLOT_FELLING, new ItemStack(item("android_felling_upgrade")));
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.OAK_LOG), 3, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.OAK_LOG) == 3, "原木が3つ届かない");
            helper.assertFalse(helper.getBlockState(new BlockPos(1, 3, 6)).is(net.minecraft.world.level.block.Blocks.OAK_LOG), "木の上が残った");
            a.discard();
        });
    }

    /** 毛刈り: 範囲の羊の毛を刈り、落ちた羊毛を拾って届ける。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidShearsSheep")
    public static void androidShearsSheep(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var sheep = EntityType.SHEEP.create(helper.getLevel());
        sheep.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 6))));
        sheep.setNoAi(true);
        helper.getLevel().addFreshEntity(sheep);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR, helper.absolutePos(new BlockPos(0, 1, 5)),
                helper.absolutePos(new BlockPos(2, 1, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL));
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL), 1,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.WHITE_WOOL) >= 1, "羊毛が届かない");
            helper.assertTrue(sheep.isSheared(), "毛を刈っていない");
            sheep.discard();
            a.discard();
        });
    }

    /** 装飾: 着けた装飾はクライアントにも伝わり（描くため）、染めた色が使われる。枠に合わない物は入らない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_androidWearsAccessories")
    public static void androidWearsAccessories(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        ItemStack ribbon = new ItemStack(item("android_ribbon"));
        ((net.minecraft.world.item.DyeableLeatherItem) ribbon.getItem()).setColor(ribbon, 0x123456);
        a.inventory().setItem(RuinAndroid.SLOT_DECOR, ribbon);
        ItemStack worn = a.decor(io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD);
        helper.assertTrue(worn.is(item("android_ribbon")), "頭の装飾が伝わらない");
        helper.assertTrue(((io.github.genichimaruo.singulo.android.AndroidAccessoryItem) worn.getItem()).color(worn) == 0x123456, "染めた色が使われない");
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var menu = new io.github.genichimaruo.singulo.android.AndroidMenu(1, player.getInventory(), a);
        helper.assertFalse(menu.getSlot(RuinAndroid.SLOT_DECOR + io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND)
                .mayPlace(new ItemStack(item("android_cape"))), "手の枠にマントが入る");
        helper.assertTrue(menu.getSlot(RuinAndroid.SLOT_DECOR + io.github.genichimaruo.singulo.android.AndroidAccessoryItem.BACK)
                .mayPlace(new ItemStack(item("android_cape"))), "背中の枠にマントが入らない");
        a.discard();
        helper.succeed();
    }


    /** 満杯の納品箱へ少しずつ届けても、製作の余りを注文数に足さない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_partialDeliveryKeepsSurplus")
    public static void partialDeliveryKeepsSurplus(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var planks = net.minecraft.world.item.Items.OAK_PLANKS;
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(0, new ItemStack(planks, 3));
        BladeAndroid a = worker(helper, false);
        a.setNoAi(true);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(planks), 3, List.of(a.getUUID()), null);
        helper.assertTrue(error == null, "在庫の納品を注文できない");
        var job = console.requestJob(a);
        var pickups = console.planPickup(job, a.blockPosition());
        helper.assertTrue(pickups != null, "受け取りを計画できない");
        for (var pickup : pickups) {
            helper.assertTrue(console.fetchAt(a, job, pickup), "予約した板材を取れない");
        }
        a.addToCarry(new ItemStack(planks)); // 同じ種類の製作の余り
        for (int i = 0; i < console.outbox().getContainerSize(); i++) {
            console.outbox().setItem(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
        }
        console.outbox().setItem(0, new ItemStack(planks, 63));
        helper.assertFalse(console.deliver(a, job), "1個だけで納品完了になった");
        CompoundTag controller = console.saveWithFullMetadata();
        console.load(controller);
        helper.assertTrue(console.orders().get(0).deliverInProgress == 2, "再読込で未納品数を失った");
        console.outbox().setItem(0, new ItemStack(planks, 63));
        helper.assertFalse(console.deliver(a, job), "2個だけで納品完了になった");
        console.outbox().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(console.deliver(a, job), "残り1個の納品が終わらない");
        helper.assertTrue(console.outbox().getItem(0).getCount() == 1, "最後に残数を超えて納品した");
        helper.assertTrue(console.orders().get(0).delivered == 3, "納品数が注文と違う");
        int surplus = 0;
        for (int slot : a.carrySlots()) {
            if (a.inventory().getItem(slot).is(planks)) {
                surplus += a.inventory().getItem(slot).getCount();
            }
        }
        helper.assertTrue(surplus == 1, "製作の余りが持ち物に残らない");
        console.complete(job);
        helper.assertTrue(console.orders().isEmpty(), "納品後に注文が終わらない");
        a.discard();
        helper.succeed();
    }

    /** 流体の計算結果は、見本の個数ではなくmBで製作数と余りを表示する。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_fluidPlanUsesMillibuckets")
    public static void fluidPlanUsesMillibuckets(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        ItemStack water = io.github.genichimaruo.singulo.android.work.FluidSamples.of(
                new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
        ItemStack card = new ItemStack(item("recipe_card"));
        var step = new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.EXTRACT, helper.absolutePos(TABLE), null, water);
        io.github.genichimaruo.singulo.android.work.RecipeData.procedure(List.of(step), water).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS * 3 + 1, card);
        var plan = console.plan(io.github.genichimaruo.singulo.android.work.FluidSamples.key(water), 1500);
        helper.assertTrue(plan.ok(), "流体の計画を作れない");
        var view = io.github.genichimaruo.singulo.android.work.WorkConsoleView.plan(console, plan, level.registryAccess());
        var made = view.getList("crafted", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        helper.assertTrue(made.getLong("count") == 2000 && made.getLong("surplus") == 500, "mBで表示していない");
        helper.succeed();
    }

    /** 加工のバッチ投入では、充電10で原鉄を1個だけ投入し、その場で止まる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_processingStopsWhenChargeRunsOut")
    public static void processingStopsWhenChargeRunsOut(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = new BlockPos(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        BladeAndroid a = worker(helper, false);
        a.setNoAi(true);
        CompoundTag saved = a.saveWithoutId(new CompoundTag());
        saved.putInt("charge", 10);
        a.load(saved);
        a.addToCarry(new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 3));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        var recipe = new io.github.genichimaruo.singulo.android.work.RecipeData(
                io.github.genichimaruo.singulo.android.work.RecipeData.MODE_PROCESSING, grid,
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of());
        var batch = new io.github.genichimaruo.singulo.android.work.WorkJob.Batch(0, 3, helper.absolutePos(furnacePos), recipe);
        var job = new io.github.genichimaruo.singulo.android.work.WorkJob(
                io.github.genichimaruo.singulo.android.work.WorkJob.Type.CRAFT, 0, 1, 0, List.of(), List.of(batch), true);
        Class<?> goalType = Class.forName("io.github.genichimaruo.singulo.android.AndroidWorkGoal");
        var constructor = goalType.getDeclaredConstructor(RuinAndroid.class);
        constructor.setAccessible(true);
        Object goal = constructor.newInstance(a);
        var process = goalType.getDeclaredMethod("process", net.minecraft.server.level.ServerLevel.class,
                io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.class,
                io.github.genichimaruo.singulo.android.work.WorkJob.class, io.github.genichimaruo.singulo.android.work.WorkJob.Batch.class);
        process.setAccessible(true);
        for (int i = 0; i < 10; i++) {
            process.invoke(goal, level, console, job, batch);
        }
        net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, furnacePos);
        helper.assertTrue(furnace.getItem(0).getCount() == 1, "充電切れのあとも投入を続けた");
        helper.assertTrue(a.charge() == 0 && !a.active(), "充電切れで止まらない");
        int raw = 0;
        for (int slot : a.carrySlots()) {
            if (a.inventory().getItem(slot).is(net.minecraft.world.item.Items.RAW_IRON)) {
                raw += a.inventory().getItem(slot).getCount();
            }
        }
        helper.assertTrue(raw == 2, "未投入の原鉄が残らない");
        a.discard();
        helper.succeed();
    }


    /** 一部製作済みで指令台と個体を再読込しても、最初の素材を二重に要求しない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidResumesCraftAfterReload")
    public static void androidResumesCraftAfterReload(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 1);
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 8,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.saveWorkState().getLong("crafted") > 0, "一部製作済みになるのを待つ"))
                .thenExecute(() -> {
                    CompoundTag saved = a.saveWithoutId(new CompoundTag());
                    CompoundTag controller = console.saveWithFullMetadata();
                    console.load(controller);
                    a.load(saved);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 8, "再読込後に棒8本が届かない");
                    helper.assertTrue(console.orders().isEmpty(), "再読込後に注文が終わらない");
                })
                .thenExecute(a::discard)
                .thenSucceed();
    }

    /** 転送後も、持ち主の物を資材として戻さず、作業用の物とタンクを引き継ぐ。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_transferredWorkKeepsPersonalSlotsAndFluids")
    public static void transferredWorkKeepsPersonalSlotsAndFluids(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(level);
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(net.minecraft.world.item.Items.EMERALD));
        a.inventory().setItem(RuinAndroid.SLOT_TANK, new ItemStack(item("android_tank_upgrade")));
        a.assignWork(helper.absolutePos(CONSOLE), 1);
        a.addToCarry(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));
        var water = new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        a.fillTanks(water, false);
        CompoundTag work = a.saveWorkState();
        BladeAndroid b = SinguloEntities.BLADE_ANDROID.get().create(level);
        for (int i = 0; i < RuinAndroid.INVENTORY_SIZE; i++) {
            b.inventory().setItem(i, a.inventory().getItem(i).copy());
        }
        b.assignWork(helper.absolutePos(CONSOLE), 1);
        b.restoreWorkState(work);
        boolean personalExcluded = true, cargoIncluded = false;
        for (int slot : b.carrySlots()) {
            personalExcluded &= slot != RuinAndroid.CARRY_START;
            cargoIncluded |= b.inventory().getItem(slot).is(net.minecraft.world.item.Items.OAK_PLANKS);
        }
        helper.assertTrue(personalExcluded && cargoIncluded, "転送後に持ち主の物と作業用の物が混ざった");
        helper.assertTrue(b.tankAmount(water) == 1000, "転送後に流体を失った");
        a.discard();
        b.discard();
        helper.succeed();
    }


    /** 1枠に2種類を投入できたと誤認せず、途中の再読込後は未投入の材料だけを入れる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_processingRemembersPartialInputs")
    public static void processingRemembersPartialInputs(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        a.inventory().setItem(RuinAndroid.CARRY_START + 1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        grid.set(1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        var recipe = new io.github.genichimaruo.singulo.android.work.RecipeData(
                io.github.genichimaruo.singulo.android.work.RecipeData.MODE_PROCESSING, grid,
                new ItemStack(net.minecraft.world.item.Items.STICK), List.of());
        var handler = new net.minecraftforge.items.ItemStackHandler(1);
        var goalField = RuinAndroid.class.getDeclaredField("workGoal");
        goalField.setAccessible(true);
        Object goal = goalField.get(a);
        var insert = goal.getClass().getDeclaredMethod("insertOnce", net.minecraftforge.items.IItemHandler.class,
                net.minecraftforge.fluids.capability.IFluidHandler.class,
                io.github.genichimaruo.singulo.android.work.RecipeData.class);
        insert.setAccessible(true);
        int initialCharge = a.charge();
        helper.assertFalse((Boolean) insert.invoke(goal, handler, null, recipe), "2種類とも入ったことになった");
        helper.assertTrue(handler.getStackInSlot(0).is(net.minecraft.world.item.Items.OAK_LOG), "最初の材料が入らない");
        helper.assertTrue(a.inventory().getItem(RuinAndroid.CARRY_START + 1).getCount() == 1, "未投入の材料を失った");
        handler.extractItem(0, 64, false);       // 装置が最初の材料を消費した
        CompoundTag checkpoint = a.saveWorkState();
        a.restoreWorkState(checkpoint);
        helper.assertTrue((Boolean) insert.invoke(goal, handler, null, recipe), "未投入の材料から再開できない");
        helper.assertTrue(handler.getStackInSlot(0).is(net.minecraft.world.item.Items.COBBLESTONE), "最初の材料を再投入した");
        helper.assertTrue(a.carryEmpty(), "材料が持ち物に残った");
        helper.assertTrue(a.charge() == initialCharge - 10, "1回分の加工で充電を二重に使った");
        a.discard();
        helper.succeed();
    }


    /** 許可証から外された人の棚・納品口を閉じ、他人の指令台への付け替えも拒否する。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workLinksEnforcePermissionChanges")
    public static void workLinksEnforcePermissionChanges(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        var owner = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var friend = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var stranger = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        friend.moveTo(Vec3.atCenterOf(helper.absolutePos(SUPPLY)));
        console.setOwner(owner.getUUID());
        ItemStack permit = new ItemStack(item("shield_permit"));
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, owner);
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, friend);
        console.permit().setStackInSlot(0, permit);
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity port = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, DELIVERY);
        var rackMenu = new io.github.genichimaruo.singulo.android.work.RecipeRackMenu(1, friend.getInventory(), helper.absolutePos(RACK), rack.items());
        var portMenu = new io.github.genichimaruo.singulo.android.work.DeliveryPortMenu(2, friend.getInventory(), helper.absolutePos(DELIVERY),
                new net.minecraft.world.SimpleContainer(io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity.SLOTS));
        helper.assertTrue(rackMenu.stillValid(friend) && portMenu.stillValid(friend), "登録者が棚・納品口を使えない");
        BlockPos other = new BlockPos(5, 1, 5);
        helper.setBlock(other, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity otherConsole = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, other);
        otherConsole.setOwner(stranger.getUUID());
        ItemStack link = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(link, level, helper.absolutePos(other),
                SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        rack.useItem(stranger, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(helper.absolutePos(CONSOLE).equals(rack.console()), "他人の棚を自分の指令台につなぎ替えた");
        ItemStack ownerOnly = new ItemStack(item("shield_permit"));
        io.github.genichimaruo.singulo.item.ShieldPermitItem.add(ownerOnly, owner);
        console.permit().setStackInSlot(0, ownerOnly);
        helper.assertFalse(rackMenu.stillValid(friend), "権限を外された後も棚が使える");
        helper.assertFalse(portMenu.stillValid(friend), "権限を外された後も納品口が使える");
        portMenu.rename(friend, "not allowed");
        helper.assertTrue(port.name().isEmpty(), "権限を外された後も納品口の名前を変えられる");
        CompoundTag linkedSave = rack.saveWithFullMetadata();
        helper.setBlock(CONSOLE, net.minecraft.world.level.block.Blocks.AIR);
        rack.load(linkedSave); // 棚が未読込の間に指令台が壊された場合
        helper.assertTrue(rack.mayOperate(stranger) && rack.console() == null, "壊れた指令台へのリンクで締め出された");
        helper.succeed();
    }

    /** 遮断ガラスはアンドロイドだけを止め、透過ガラスはアンドロイドだけを通す。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_androidGlassFiltersAndroids")
    public static void androidGlassFiltersAndroids(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    /** 副産物で満杯でも目的の品を持ち、私物・後続材料は守る。満杯の装置出力は抜き捨てない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workOutputSurvivesFullCargo")
    public static void workOutputSurvivesFullCargo(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(level);
        a.moveTo(Vec3.atCenterOf(helper.absolutePos(STATION)));
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(net.minecraft.world.item.Items.EMERALD));
        a.assignWork(helper.absolutePos(CONSOLE), 1);
        var target = new ItemStack(net.minecraft.world.item.Items.DIAMOND);
        var input = new ItemStack(net.minecraft.world.item.Items.OAK_LOG);
        var job = new io.github.genichimaruo.singulo.android.work.WorkJob(
                io.github.genichimaruo.singulo.android.work.WorkJob.Type.CRAFT, 1, 1, 0,
                List.of(new io.github.genichimaruo.singulo.android.work.ItemCount(input, 1)), List.of(), true);
        var jobField = RuinAndroid.class.getDeclaredField("workJob");
        jobField.setAccessible(true);
        jobField.set(a, job);
        int[] slots = a.carrySlots();
        a.inventory().setItem(slots[0], input.copy());
        for (int i = 1; i < slots.length; i++) {
            a.inventory().setItem(slots[i], new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
        }
        var goalField = RuinAndroid.class.getDeclaredField("workGoal");
        goalField.setAccessible(true);
        Object goal = goalField.get(a);
        var collect = goal.getClass().getDeclaredMethod("collect", List.class, ItemStack.class);
        collect.setAccessible(true);
        collect.invoke(goal, List.of(target.copy()), target);
        helper.assertTrue(a.inventory().getItem(RuinAndroid.CARRY_START).is(net.minecraft.world.item.Items.EMERALD), "私物を捨てた");
        helper.assertTrue(a.inventory().getItem(slots[0]).is(net.minecraft.world.item.Items.OAK_LOG), "後続材料を捨てた");
        boolean kept = false;
        for (int slot : slots) {
            kept |= a.inventory().getItem(slot).is(net.minecraft.world.item.Items.DIAMOND);
        }
        helper.assertTrue(kept && a.saveWorkState().getLong("progress") == 1, "目的の品を持たずに進捗を数えた");
        // 全枠が必要な材料で埋まるときは、装置に残して待つ。
        for (int slot : slots) {
            a.inventory().setItem(slot, input.copyWithCount(64));
        }
        var handler = new net.minecraftforge.items.ItemStackHandler(1);
        handler.setStackInSlot(0, target.copy());
        var extract = goal.getClass().getDeclaredMethod("extractToCarry",
                net.minecraftforge.items.IItemHandler.class, ItemStack.class, long.class);
        extract.setAccessible(true);
        long moved = (Long) extract.invoke(goal, handler, target, 1L);
        helper.assertTrue(moved == 0 && handler.getStackInSlot(0).getCount() == 1, "持てない完成品を装置から抜き捨てた");
        collect.invoke(goal, List.of(target.copy()), target);
        helper.assertTrue(a.saveWorkState().getLong("progress") == 1, "足元に落ちた品を取得済みと数えた");
        a.discard();
        helper.succeed();
    }
}
