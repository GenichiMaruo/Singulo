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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** アンドロイド（充電・護衛・組立ステーション・転送アップグレード・アンドロイド用のガラス）の確認。 */
@GameTestHolder("singulo_automation")
@PrefixGameTestTemplate(false)
public final class AndroidGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos STATION = p(2, 1, 2);

    private AndroidGameTests() {}

    // Preserve the original layout, with four walkable blocks on every side.
    // Devices on the old 8x8 edge let pathfinding leave the fixture and become unreachable.
    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x + 4, y, z + 4);
    }

    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper) {
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        // Keep the player within interaction range, away from machine approach squares.
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 1, 2)));
        player.teleportTo(helper.getLevel(), at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** 電力・設計メモリ・キットのあるステーションを置く。 */
    private static AndroidStationBlockEntity station(GameTestHelper helper, int kits) {
        // A printed guard needs walkable ground beside the station to clear its top.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        }
        helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity s = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, STATION);
        s.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, new ItemStack(item(AndroidKind.BLADE.memoryId())));
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_KIT, new ItemStack(item("android_kit"), kits));
        return s;
    }

    /** ふつうの拠点と同じく、ステーション・階段の下の段まで床を張る（上の面へ1段で上がれるように）。 */
    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 7, 0, 7)) {
            helper.setBlock(p, net.minecraft.world.level.block.Blocks.STONE);
        }
    }

    /** このテストのステーションが印刷したアンドロイド（並んで動くほかのテストのものは数えない。少し離れていても数える）。 */
    private static List<RuinAndroid> androids(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(STATION);
        return helper.getLevel().getEntitiesOfClass(RuinAndroid.class, new AABB(station).inflate(32), a -> station.equals(a.home()));
    }

    /** ステーションは、メモリとキットと電力で護衛を1体だけ印刷し、キットと電力を使う。印刷中は傷つかない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 100, batch = "automation_stationPrintsOneGuard")
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

    /** 工場式印刷ユニットを積むと、印刷の電力が半分になり、半分の時間で印刷し終える。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_print_unit", timeoutTicks = 120)
    public static void printUnitHalvesPrinting(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PRINT_UNIT, new ItemStack(item("android_factory_print_unit")));
        helper.assertTrue(s.hasPrintUnit(), "工場式印刷ユニットを積めない");
        long[] seen = {-1, -1};
        int[] energy = {-1};
        helper.onEachTick(() -> {
            List<RuinAndroid> list = androids(helper);
            if (!list.isEmpty() && seen[0] < 0) {
                seen[0] = helper.getTick();
                energy[0] = s.energy().getEnergyStored();
            }
            if (!list.isEmpty() && list.get(0).printed() && seen[1] < 0) {
                seen[1] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            List<RuinAndroid> now = androids(helper);
            helper.assertTrue(seen[1] >= 0, "印刷し終わらない: " + now.size() + " " + (now.isEmpty() ? "" : now.get(0).printProgress(0)
                    + " state=" + now.get(0).state() + " alive=" + now.get(0).isAlive()) + " seen=" + seen[0]);
            helper.assertTrue(energy[0] == AndroidStationBlockEntity.CAPACITY - AndroidStationBlockEntity.PRINT_FE / 2,
                    "印刷の電力が半分にならない: " + (AndroidStationBlockEntity.CAPACITY - energy[0]));
            helper.assertTrue(seen[1] - seen[0] <= RuinAndroid.PRINT_TICKS / 2 + 2, "印刷が半分の時間で終わらない: " + (seen[1] - seen[0]));
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /** 護衛はプレイヤーの攻撃で傷つかず、ふつうのダメージでは傷つく。体力はコンパレーターに出る。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_guard_hp", timeoutTicks = 160)
    public static void guardIgnoresPlayersAndReportsHealth(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var player = player(helper);
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            float before = a.getHealth();
            a.hurt(helper.getLevel().damageSources().playerAttack(player), 10);
            helper.assertTrue(a.getHealth() == before, "プレイヤーの攻撃で傷ついた");
            a.invulnerableTime = 0;
            a.hurt(helper.getLevel().damageSources().magic(), 15);
            helper.assertTrue(a.getHealth() < before, "ふつうのダメージで傷つかない: printed=" + a.printed() + " state=" + a.state()
                    + " hp=" + a.getHealth() + "/" + before);
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
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_transfer_inventory", timeoutTicks = 400)
    public static void transferUpgradePassesInventory(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 2);
        // A placed station belongs to its player; unrelated orphan fixtures must not block this reprint.
        s.setOwner(java.util.UUID.randomUUID());
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_UPGRADE, new ItemStack(item("android_transfer_upgrade")));
        boolean[] transferred = {false};
        helper.onEachTick(() -> {
            List<RuinAndroid> current = androids(helper);
            // 持ち物の引継ぎだけを調べる。足場のない空テンプレートから歩いて落ちるのを避ける。
            current.forEach(a -> { a.setNoAi(true); a.setNoGravity(true); });
            if (transferred[0] || helper.getTick() < RuinAndroid.PRINT_TICKS + 30
                    || current.isEmpty() || !current.get(0).printed()) {
                return;
            }
            RuinAndroid first = current.get(0);
            transferred[0] = true;
            first.inventory().setItem(RuinAndroid.SLOT_REPAIR, new ItemStack(item("android_repair_kit"), 5));
            first.kill();
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(STATION)).inflate(8), e -> e.getItem().is(item("android_repair_kit"))).isEmpty(),
                    "転送アップグレードがあるのに持ち物が落ちた");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(transferred[0], "最初の護衛が印刷されない");
            // 同じ組で並んで動くほかのテストのアンドロイドは数えない（このステーションのものだけ）
            List<RuinAndroid> list = androids(helper).stream()
                    .filter(a -> a.isAlive() && helper.absolutePos(STATION).equals(a.home())).toList();
            helper.assertTrue(list.size() == 1, "印刷し直されない: " + list.size());
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
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(5, 1, 5))));
        helper.getLevel().addFreshEntity(a);
        return a;
    }

    private static int memories(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(4),
                e -> e.getItem().is(item(AndroidKind.BLADE.memoryId()))).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** 読み込みが外れただけ（見つからないだけ）の生きているアンドロイドは、倒した扱いにならない（設計メモリは出ない）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 100, batch = "automation_fabricatorKeepsUnloadedAndroid")
    public static void fabricatorKeepsUnloadedAndroid(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos at = p(2, 1, 2);
        BladeAndroid a = ruinAndroid(helper, at);
        AndroidFabricatorBlockEntity f = fabricatorWith(helper, at, a);
        a.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(memories(helper, at) == 0, "読み込まれていないだけのアンドロイドで設計メモリが出た");
            helper.assertTrue(f.alive() == 1, "読み込まれていないアンドロイドを出撃から外した");
            helper.succeed();
        });
    }

    /** 出撃したアンドロイドが全部倒されたら、設計メモリが（出る回なら）1つだけ出る。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 160, batch = "automation_fabricatorDropsMemoryWhenAllDefeated")
    public static void fabricatorDropsMemoryWhenAllDefeated(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos at = p(2, 1, 2);
        BladeAndroid a = ruinAndroid(helper, at);
        AndroidFabricatorBlockEntity f = fabricatorWith(helper, at, a);
        f.setMemoryChanceForTest(1);
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

    /** 設計メモリが出ない回: 全部倒しても何も出ず、台は1日休む（確率は半分）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 160, batch = "automation_fabricatorSometimesDropsNoMemory")
    public static void fabricatorSometimesDropsNoMemory(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.assertTrue(AndroidFabricatorBlockEntity.MEMORY_CHANCE == 0.5F, "設計メモリの確率が半分でない: " + AndroidFabricatorBlockEntity.MEMORY_CHANCE);
        BlockPos at = p(2, 1, 2);
        BladeAndroid a = ruinAndroid(helper, at);
        AndroidFabricatorBlockEntity f = fabricatorWith(helper, at, a);
        f.setMemoryChanceForTest(0);
        a.kill();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(f.alive() == 0, "倒したアンドロイドが出撃に残っている");
            helper.assertTrue(memories(helper, at) == 0, "出ない回なのに設計メモリが出た: " + memories(helper, at));
            helper.succeed();
        });
    }

    /** 護衛の技はボス・中立の動物・村人・プレイヤーに当たらず、敵対モブには当たる。遺構の技は仲間以外に当たる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_guardAttacksSpareBossesAndNeutrals")
    public static void guardAttacksSpareBossesAndNeutrals(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        BladeAndroid guard = SinguloEntities.BLADE_ANDROID.get().create(level);
        guard.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        BladeAndroid ruin = SinguloEntities.BLADE_ANDROID.get().create(level);
        var player = player(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60, batch = "automation_orphanGuardLaserSparesVillagers")
    public static void orphanGuardLaserSparesVillagers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        helper.setBlock(p(3, 1, 3), net.minecraft.world.level.block.Blocks.STONE);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(p(3, 2, 3)));
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 300, batch = "automation_replacedStationRelinksOldGuard")
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
     * 許可証にだれかが登録されていれば、護衛は許可証に載っていない人を敵とみなす（狙う・技が当たる・味方ではない・ダメージを受ける）。
     * 持ち主・登録者・クリエイティブの人は対象外。許可証がなければ、だれも敵とみなさない。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400, batch = "automation_guardsAttackPlayersOffThePermit")
    public static void guardsAttackPlayersOffThePermit(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        // makeMockServerPlayerInLevel の人はいつもクリエイティブ扱いなので、サバイバルの人を作る
        var owner = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var friend = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var stranger = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var builder = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        builder.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        friend.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        stranger.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        s.setOwner(owner.getUUID());
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 30, () -> {
            RuinAndroid a = androids(helper).get(0);
            a.setNoAi(true);
            helper.assertFalse(a.intruder(stranger), "許可証がないのに、登録していない人を敵とみなした");
            helper.assertTrue(a.isAlliedTo(stranger), "許可証がないのに、登録していない人が味方でない");
            ItemStack permit = new ItemStack(item("shield_permit"));
            io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, owner);
            io.github.genichimaruo.singulo.item.ShieldPermitItem.add(permit, friend);
            s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PERMIT, permit);
            s.press(AndroidStationBlockEntity.BUTTON_MODE_FOLLOW);       // 設定を押すと、許可証もアンドロイドへ渡る
            s.press(AndroidStationBlockEntity.BUTTON_MODE_GUARD);
            helper.assertTrue(a.intruder(stranger), "許可証に載っていない人を敵とみなさない");
            helper.assertFalse(a.intruder(owner) || a.intruder(friend), "持ち主か登録した人を敵とみなした");
            helper.assertFalse(a.intruder(builder), "クリエイティブの人を敵とみなした");
            helper.assertTrue(RuinAndroid.canHit(true, a, stranger), "許可証に載っていない人に護衛の技が当たらない");
            helper.assertFalse(RuinAndroid.canHit(true, a, friend), "登録した人に護衛の技が当たる");
            helper.assertFalse(a.isAlliedTo(stranger), "許可証に載っていない人が味方のまま");
            helper.assertTrue(a.isAlliedTo(friend), "登録した人が味方でない");
            stranger.moveTo(a.position().add(2, 0, 0));
            helper.assertTrue(a.guardTargetsForTest(stranger), "ステーションの近くにいる、許可証に載っていない人を狙わない");
            float before = a.getHealth();
            a.invulnerableTime = 0;
            a.hurt(helper.getLevel().damageSources().playerAttack(friend), 2);
            helper.assertTrue(a.getHealth() == before, "登録した人の攻撃でダメージを受けた");
            a.invulnerableTime = 0;
            a.hurt(helper.getLevel().damageSources().playerAttack(stranger), 2);
            helper.assertTrue(a.getHealth() < before, "許可証に載っていない人の攻撃でダメージを受けない");
            helper.succeed();
        });
    }

    /**
     * 許可証: 持ち主と登録した人だけが操作でき、追従する人を登録した人から選べる。
     * 追従中「ついていくだけ」なら敵を狙わず、「護衛する」なら狙う。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400, batch = "automation_permitAndFollowSettings")
    public static void permitAndFollowSettings(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var owner = player(helper);
        var friend = player(helper);
        var stranger = player(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200, batch = "automation_removedMemberLosesAndroidScreen")
    public static void removedMemberLosesAndroidScreen(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var owner = player(helper);
        var friend = player(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200, batch = "automation_androidScreenSwitchesEscort")
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

    /** 巡回路の欄に作業位置カード（床のブロック）を置く。 */
    private static void routeCard(GameTestHelper helper, AndroidStationBlockEntity s, int index, BlockPos floor) {
        ItemStack card = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(card, helper.getLevel(), helper.absolutePos(floor),
                helper.getLevel().getBlockState(helper.absolutePos(floor)).getBlock());
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_ROUTE + index, card);
    }

    /** 巡回: 巡回路の2つの地点を、順に回る（どちらの上にも立つ）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_patrol", timeoutTicks = 1200)
    public static void patrolWalksRoute(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        BlockPos[] points = {p(1, 0, 6), p(6, 0, 6)};
        for (int i = 0; i < points.length; i++) {
            routeCard(helper, s, i, points[i]);
        }
        s.press(AndroidStationBlockEntity.BUTTON_MODE_PATROL);
        for (int i = 0; i < 3; i++) {
            s.press(AndroidStationBlockEntity.BUTTON_DWELL);     // 立ち止まる時間: 5秒 → 10 → 20 → 0秒
        }
        boolean[] visited = new boolean[points.length];
        helper.onEachTick(() -> {
            for (RuinAndroid a : androids(helper)) {
                for (int i = 0; i < points.length; i++) {
                    Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(points[i].above()));
                    visited[i] |= a.printed() && a.position().subtract(stand).horizontalDistance() < 1.7;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(androids(helper).stream().allMatch(a -> a.mode() == RuinAndroid.MODE_PATROL), "動き方が巡回にならない");
            helper.assertTrue(visited[0] && visited[1], "巡回路の地点を回らない: " + visited[0] + ", " + visited[1]);
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /**
     * 経路の決まり: 巡回の地点（東の帯の中）へ向かうとき、帯（対象範囲）に入るので経由地点（帯の南の端）を通り、
     * そのあいだ進入禁止ゾーン（ステーションと帯のあいだの列。南の端だけ空いている）に入らない。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_patrol", timeoutTicks = 1200)
    public static void patrolFollowsRouteRules(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        var level = helper.getLevel();
        BlockPos point = p(6, 0, 1), via = p(6, 0, 7);
        routeCard(helper, s, 0, point);
        // 1行目: 経由地点・対象範囲（x 5〜7 の帯）・進入禁止ゾーン（x 4 の列、z 0〜6）
        ItemStack viaCard = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(viaCard, level, helper.absolutePos(via),
                level.getBlockState(helper.absolutePos(via)).getBlock());
        ItemStack scope = new ItemStack(item("area_card"));
        io.github.genichimaruo.singulo.android.work.AreaCardItem.record(scope, level, helper.absolutePos(p(5, 0, 0)),
                helper.absolutePos(p(7, 0, 7)));
        ItemStack zone = new ItemStack(item("area_card"));
        io.github.genichimaruo.singulo.android.work.AreaCardItem.record(zone, level, helper.absolutePos(p(4, 0, 0)),
                helper.absolutePos(p(4, 0, 6)));
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_RULES, viaCard);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_RULES + 1, scope);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_RULES + 2, zone);
        s.press(AndroidStationBlockEntity.BUTTON_MODE_PATROL);
        boolean[] passedVia = {false}, reached = {false}, trespassed = {false};
        helper.onEachTick(() -> {
            for (RuinAndroid a : androids(helper)) {
                if (!a.printed()) {
                    continue;
                }
                Vec3 rel = a.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(p(0, 0, 0))));
                passedVia[0] |= rel.subtract(6.5, 1, 7.5).horizontalDistance() < 1.6;
                reached[0] |= passedVia[0] && rel.subtract(6.5, 1, 1.5).horizontalDistance() < 1.6;
                trespassed[0] |= !passedVia[0] && rel.x >= 4 && rel.x < 5 && rel.z >= 0 && rel.z < 7;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(trespassed[0], "経由地点へ向かうあいだに、進入禁止ゾーンに入った");
            helper.assertTrue(passedVia[0], "対象範囲に入るのに、経由地点を通らない");
            helper.assertTrue(reached[0], "経由地点を通ったあと、巡回の地点へ行かない: " + androids(helper).stream()
                    .map(a -> a.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))).toString()).toList());
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /** タンクの画面: 水入りバケツでクリックするとタンクに入り（空のバケツが残る）、空のバケツで汲み、捨てるボタンで空になる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_tankTakesBuckets")
    public static void tankTakesBuckets(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        a.inventory().setItem(RuinAndroid.SLOT_TANK, new ItemStack(item("android_tank_upgrade")));
        var player = player(helper);
        player.containerMenu.setCarried(new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        helper.assertTrue(a.useTankContainer(player, 0), "水入りバケツでタンクに入れられない");
        var water = new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1);
        helper.assertTrue(a.tankAmount(water) == 1000 && player.containerMenu.getCarried().is(net.minecraft.world.item.Items.BUCKET),
                "タンクに 1000 mB 入って、空のバケツが残らない: " + a.tankAmount(water));
        helper.assertTrue(a.useTankContainer(player, 0) && player.containerMenu.getCarried().is(net.minecraft.world.item.Items.WATER_BUCKET)
                && a.tankAmount(water) == 0, "空のバケツでタンクから汲めない");
        player.containerMenu.setCarried(new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        a.useTankContainer(player, 1);
        helper.assertTrue(a.clearTank(1) && a.tanksEmpty(), "捨てるボタンでタンクが空にならない");
        a.discard();
        helper.succeed();
    }

    /** 拠点の暮らし: 昼、手の空いた護衛は、ステーションのそばの階段に座る（座れる物がなければ地面に。ほかに掃く仕草などもする）。
     * 迎撃モードは暮らしをしないので、巡回路のない巡回モード（ステーションのそばで待つ）で確かめる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_life", timeoutTicks = 2400)
    public static void idleGuardSitsOnStairs(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.getLevel().setDayTime(1000);
        floor(helper);
        station(helper, 1).press(AndroidStationBlockEntity.BUTTON_MODE_PATROL);
        BlockPos stair = p(5, 1, 2);
        helper.setBlock(stair, net.minecraft.world.level.block.Blocks.OAK_STAIRS);
        // 作業台（名前に "bench" が入る）は椅子ではない。階段より近くに置く
        BlockPos bench = p(3, 1, 3);
        helper.setBlock(bench, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORKBENCH.get());
        boolean[] sat = {false}, onBench = {false};
        helper.onEachTick(() -> {
            for (RuinAndroid a : androids(helper)) {
                if (a.printed() && a.tickCount % 60 == 0 && a.state() == RuinAndroid.STATE_IDLE) {
                    a.lifeNow();
                }
                sat[0] |= a.state() == RuinAndroid.STATE_SIT
                        && a.position().subtract(Vec3.atBottomCenterOf(helper.absolutePos(stair))).horizontalDistance() < 0.45;
                onBench[0] |= a.state() == RuinAndroid.STATE_SIT
                        && a.position().subtract(Vec3.atBottomCenterOf(helper.absolutePos(bench))).horizontalDistance() < 0.6;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!onBench[0], "作業台に座った（作業台の中に埋まる）");
            helper.assertTrue(sat[0], "手の空いた護衛が、そばの階段に座らない: " + androids(helper).stream().map(a -> "pos=" + a.position() + " state=" + a.state() + " life=" + a.lifeState() + " health=" + a.getHealth() + " target=" + a.getTarget()).toList());
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /** 拠点の暮らし: しばらく落ちたままの物を拾って、そばのチェストへしまう。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_life_tidy", timeoutTicks = 1200)
    public static void idleGuardTidiesIntoChest(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.getLevel().setDayTime(1000);
        floor(helper);
        station(helper, 1).press(AndroidStationBlockEntity.BUTTON_MODE_PATROL);
        BlockPos chest = p(6, 1, 6);
        helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
        net.minecraft.world.entity.item.ItemEntity drop = helper.spawnItem(Items.APPLE, 6.5F, 1, 10.5F);
        drop.setPickUpDelay(0);
        // Test tidying an already-old item. Otherwise the guard can begin a random
        // 20-40 second sit before this item ages, consuming almost the whole timeout.
        CompoundTag droppedItem = drop.saveWithoutId(new CompoundTag());
        droppedItem.putShort("Age", (short) (20 * 11));
        drop.load(droppedItem);
        helper.assertTrue(drop.getAge() >= 20 * 11, "片付け対象の落下物が十分に古くない");
        helper.onEachTick(() -> {
            for (RuinAndroid a : androids(helper)) {
                if (a.printed() && a.tickCount % 40 == 0 && a.state() == RuinAndroid.STATE_IDLE) {
                    a.lifeNow();
                }
            }
        });
        helper.succeedWhen(() -> {
            var be = (net.minecraft.world.level.block.entity.ChestBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, chest);
            String why = " (落ちた物: " + (drop.isAlive() ? "残っている age=" + drop.getAge() + " at=" + drop.position() : "消えた")
                    + androids(helper).stream().map(a -> " / " + a.state() + " hand=" + a.getMainHandItem() + " pos=" + a.blockPosition()
                    + " exact=" + a.position() + " home=" + a.home() + " life=" + a.lifeState() + " guard=" + a.isGuard() + " active=" + a.active()
                    + " target=" + a.getTarget()).reduce("", String::concat) + ")";
            helper.assertTrue(!be.isEmpty() && be.getItem(0).is(Items.APPLE), "落ちていた物を、そばのチェストへしまわない" + why);
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /** 迎撃モード: 手の空いた護衛は暮らし（座るなど）をせず、1体はステーションの上、もう1体はすぐ横で待つ。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_guard_dock", timeoutTicks = 1200)
    public static void interceptGuardsWaitOnStation(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.getLevel().setDayTime(1000);
        floor(helper);
        // ステーションが2体印刷する（ステーションが受け持たない護衛は、はぐれになって持ち場を持たない）
        AndroidStationBlockEntity station = station(helper, 2);
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PRODUCTION, new ItemStack(item("android_production_upgrade")));
        helper.setBlock(p(5, 1, 2), net.minecraft.world.level.block.Blocks.OAK_STAIRS);
        java.util.function.Supplier<List<RuinAndroid>> guardsNow = () -> androids(helper).stream().filter(RuinAndroid::isAlive).toList();
        Vec3 dock = Vec3.atBottomCenterOf(helper.absolutePos(STATION).above());
        boolean[] lived = {false}, placed = {false};
        String[] where = {""};
        helper.onEachTick(() -> {
            for (RuinAndroid a : guardsNow.get()) {
                if (a.tickCount % 40 == 0) {
                    a.lifeNow();
                }
                lived[0] |= RuinAndroid.lifeState(a.state());
            }
            long on = guardsNow.get().stream().filter(a -> a.position().subtract(dock).horizontalDistance() < 0.6
                    && Math.abs(a.getY() - dock.y) < 0.5).count();
            long beside = guardsNow.get().stream().filter(a -> a.position().subtract(dock).horizontalDistance() < 1.6
                    && Math.abs(a.getY() - (dock.y - 1)) < 0.5).count();
            where[0] = "上=" + on + " 横=" + beside + guardsNow.get().stream().map(a -> " / " + a.position() + " s=" + a.state())
                    .reduce("", String::concat);
            placed[0] |= guardsNow.get().size() == 2 && on == 1 && beside == 1;
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(lived[0], "迎撃モードなのに、暮らし（座る・手入れなど）を始めた");
            helper.assertTrue(placed[0], "1体が上、1体がすぐ横で待たない: " + where[0]);
            guardsNow.get().forEach(RuinAndroid::discard);
        });
    }

    /** 範囲カード: 2つの角を記録すると、その範囲になる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_areaCardRecordsTwoCorners")
    public static void areaCardRecordsTwoCorners(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        ItemStack card = new ItemStack(item("area_card"));
        helper.assertTrue(io.github.genichimaruo.singulo.android.work.AreaCardItem.box(card) == null, "未記録なのに範囲がある");
        io.github.genichimaruo.singulo.android.work.AreaCardItem.recordFirst(card, helper.getLevel(), p(3, 1, 9));
        helper.assertTrue(io.github.genichimaruo.singulo.android.work.AreaCardItem.box(card) == null, "1つ目の角だけで範囲になった");
        io.github.genichimaruo.singulo.android.work.AreaCardItem.recordSecond(card, p(1, 4, 5));
        var box = io.github.genichimaruo.singulo.android.work.AreaCardItem.box(card);
        helper.assertTrue(box != null && box.getXSpan() == 3 && box.getYSpan() == 4 && box.getZSpan() == 5, "範囲の大きさが違う: " + box);
        helper.assertTrue(card.getMaxStackSize() == 1, "記録したカードが重なる");
        helper.succeed();
    }

    /** 巡回中に敵の群れ（5体）を見つけたら、ステーションがレッドストーン信号を出し、記録に残す。倒した相手も記録に残る。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_patrol", timeoutTicks = 1200)
    public static void patrolRaisesAlarm(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        floor(helper); // Keep the real combat path walkable on either loader.
        AndroidStationBlockEntity s = station(helper, 1);
        routeCard(helper, s, 0, p(2, 0, 5));
        s.press(AndroidStationBlockEntity.BUTTON_MODE_PATROL);
        boolean[] alarmed = {false};
        helper.runAtTickTime(RuinAndroid.PRINT_TICKS + 40, () -> {
            for (int i = 0; i < 5; i++) {
                // Sunlight must not kill the victim before the guard can earn a kill log.
                var zombie = EntityType.HUSK.create(helper.getLevel());
                zombie.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(1 + i, 1, 6))));
                zombie.setNoAi(true);
                zombie.setPersistenceRequired();
                helper.getLevel().addFreshEntity(zombie);
            }
        });
        boolean[] powered = {false};
        helper.onEachTick(() -> {
            alarmed[0] |= s.alarming();
            BlockPos at = helper.absolutePos(STATION);
            powered[0] |= helper.getLevel().getBlockState(at).getSignal(helper.getLevel(), at, net.minecraft.core.Direction.EAST) == 15;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(alarmed[0], "敵の群れを見つけても、警報を出さない");
            helper.assertTrue(powered[0], "警報なのに、ステーションがレッドストーン信号を出さない");
            RuinAndroid a = androids(helper).get(0);
            var log = a.patrolLog();
            helper.assertTrue(log.stream().anyMatch(e -> e.kind() == io.github.genichimaruo.singulo.android.AndroidPatrol.KIND_ALARM),
                    "警報が記録に残らない");
            helper.assertTrue(log.stream().anyMatch(e -> e.kind() == io.github.genichimaruo.singulo.android.AndroidPatrol.KIND_KILL),
                    "倒した相手が記録に残らない: state=" + a.state() + " pos=" + a.position()
                            + " target=" + a.getTarget() + " day=" + helper.getLevel().getDayTime());
            a.discard();
        });
    }

    /** バッテリーアップグレード1個につき充電の満タンが50%増え、外すと満タンに合わせて減る。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_batteryUpgradeRaisesCharge")
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

    /**
     * バックパックのティア: 12・18・24 の枠が作業で使える。ティアより後ろの枠（旧版の24枠で入れた物）は消さず、作業には使わない。
     * 中身が入ったままでも、中身が収まるバックパックへは付け替えられ、収まらないものへは付け替えられない。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_backpackTiersSetCapacity")
    public static void backpackTiersSetCapacity(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        String[] tiers = {"android_backpack_upgrade", "android_reinforced_backpack_upgrade", "android_large_backpack_upgrade"};
        int[] slots = {12, 18, 24};
        helper.assertTrue(a.carryCapacity() == RuinAndroid.CARRY_SLOTS, "バックパックなしで持ち物が5でない: " + a.carryCapacity());
        for (int i = 0; i < tiers.length; i++) {
            a.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(item(tiers[i])));
            helper.assertTrue(a.carryCapacity() == RuinAndroid.CARRY_SLOTS + slots[i], tiers[i] + " の運べる欄が違う: " + a.carryCapacity());
        }
        // 旧版の24枠のバックパックで、20枠目に入れた物
        a.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(item(tiers[0])));
        a.inventory().setItem(RuinAndroid.PACK_START + 20, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 5));
        for (int slot : a.carrySlots()) {
            helper.assertTrue(slot != RuinAndroid.PACK_START + 20, "ティアより後ろの枠を作業に使う");
        }
        helper.assertTrue(a.inventory().getItem(RuinAndroid.PACK_START + 20).getCount() == 5, "ティアより後ろの枠の物が消えた");
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        var menu = new io.github.genichimaruo.singulo.android.AndroidMenu(1, player.getInventory(), a);
        net.minecraft.world.inventory.Slot backpack = menu.slots.stream()
                .filter(s -> s.container == a.inventory() && s.getContainerSlot() == RuinAndroid.SLOT_BACKPACK).findFirst().orElseThrow();
        net.minecraft.world.inventory.Slot packSlot = menu.slots.stream()
                .filter(s -> s.container == a.inventory() && s.getContainerSlot() == RuinAndroid.PACK_START + 14).findFirst().orElseThrow();
        helper.assertFalse(packSlot.mayPlace(new ItemStack(net.minecraft.world.item.Items.DIRT)), "12枠のバックパックで15枠目に入れられる");
        player.containerMenu = menu;
        menu.setCarried(new ItemStack(item(tiers[1])));
        helper.assertFalse(backpack.mayPickup(player), "中身（21枠目）が入らない強化バックパックへ付け替えられる");
        menu.setCarried(new ItemStack(item(tiers[2])));
        helper.assertTrue(backpack.mayPickup(player), "中身が収まる大型バックパックへ付け替えられない");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertFalse(backpack.mayPickup(player), "中身があるのにバックパックを外せる");
        a.discard();
        helper.succeed();
    }

    /** 持ち物は位置ごと保存する（空きスロットがあってもずれない）。0.6.0 の保存も、装甲板を装甲板の場所へ戻す。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_androidInventoryKeepsSlots")
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
    private static final BlockPos CONSOLE = p(0, 1, 0), SUPPLY = p(1, 1, 0), CHEST = p(1, 1, 1),
            DELIVERY = p(3, 1, 0), RACK = p(4, 1, 0), TABLE = p(4, 1, 3);

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
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
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
        var player = player(helper);
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

    /** オークのフェンス50個（原木 → 板材 → 棒 → フェンスの3工程）を作らせても、持ち物からあふれた物を地面に落とさない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 6000, batch = "automation_fiftyFencesNeverDrop")
    public static void fiftyFencesNeverDrop(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        fiftyFences(helper, false, false);
    }

    /** 同じ（JEI から記録したように、棒はどの板材でも作れるレシピ）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 6000, batch = "automation_fiftyFencesWithVariantsNeverDrop")
    public static void fiftyFencesWithVariantsNeverDrop(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        fiftyFences(helper, true, false);
    }

    /** 同じ（どの板材でも作れるレシピ、バックパックあり）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 6000, batch = "automation_fiftyFencesWithBackpackNeverDrop")
    public static void fiftyFencesWithBackpackNeverDrop(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        fiftyFences(helper, true, true);
    }

    private static void fiftyFences(GameTestHelper helper, boolean variants, boolean backpack) {
        var console = workshop(helper, 22);
        if (variants) {
            // 棒のレシピを、どの板材でも作れるもの（JEI の「+」で記録したとき）に置き換える
            java.util.List<ItemStack> sticks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            sticks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
            sticks.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
            java.util.List<String> alts = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ""));
            alts.set(0, "{\"tag\":\"minecraft:planks\"}");
            alts.set(3, "{\"tag\":\"minecraft:planks\"}");
            ItemStack card = new ItemStack(item("recipe_card"));
            io.github.genichimaruo.singulo.android.work.RecipeData.crafting(sticks, new ItemStack(net.minecraft.world.item.Items.STICK, 4), alts)
                    .write(card, helper.getLevel().registryAccess());
            io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity r = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
            r.items().setStackInSlot(2, card);
        }
        var level = helper.getLevel();
        java.util.List<ItemStack> fence = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int i : new int[]{0, 2, 3, 5}) {
            fence.set(i, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        }
        fence.set(1, new ItemStack(net.minecraft.world.item.Items.STICK));
        fence.set(4, new ItemStack(net.minecraft.world.item.Items.STICK));
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(3, recipeCard(helper, fence, new ItemStack(net.minecraft.world.item.Items.OAK_FENCE, 3)));
        BladeAndroid a = worker(helper, false);
        if (backpack) {
            a.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(item("android_backpack_upgrade")));
        }
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.OAK_FENCE), 50,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        java.util.Map<String, Integer> dropped = new java.util.TreeMap<>();
        helper.onEachTick(() -> {
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 8, 16))))) {
                dropped.merge(e.getItem().getHoverName().getString(), e.getItem().getCount(), Integer::sum);
                e.discard();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped.isEmpty(), "地面に落とした: " + dropped);
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.OAK_FENCE) >= 50,
                    "フェンスが50個届かない: " + delivered(helper, net.minecraft.world.item.Items.OAK_FENCE));
            a.discard();
        });
    }

    /**
     * アンドロイド作業台の預かり箱: 作った物が持ち物に入りきらなければ足元に落とさず預かり箱へ入れ、あとの工程（棒）は預かり箱の
     * 板材を持ち物へ戻して作り、残った完成品は取りに来て届ける（作り始めに、空いている持ち物を思いがけない物でふさぐ）。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 3600, batch = "automation_workbenchBufferCatchesOverflow")
    public static void workbenchBufferCatchesOverflow(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        var level = helper.getLevel();
        helper.setBlock(TABLE, SinguloBlocks.ANDROID_WORKBENCH.get());
        ItemStack bench = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(bench, level, helper.absolutePos(TABLE),
                SinguloBlocks.ANDROID_WORKBENCH.get());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(0, bench);
        BladeAndroid a = worker(helper, false);
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 8,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity wb = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, TABLE);
        boolean[] stuffed = {false}, used = {false};
        java.util.Map<String, Integer> dropped = new java.util.TreeMap<>();
        helper.onEachTick(() -> {
            if (!stuffed[0] && a.state() == RuinAndroid.STATE_CRAFTING) {
                stuffed[0] = true;
                for (int slot : a.carrySlots()) {
                    if (a.inventory().getItem(slot).isEmpty()) {
                        a.inventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
                    }
                }
            }
            used[0] |= !wb.bufferEmpty();
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 8, 16))))) {
                dropped.merge(e.getItem().getHoverName().getString(), e.getItem().getCount(), Integer::sum);
                e.discard();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stuffed[0] && used[0], "預かり箱を使っていない");
            helper.assertTrue(dropped.isEmpty(), "地面に落とした: " + dropped);
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 8,
                    "棒が8本届かない: " + delivered(helper, net.minecraft.world.item.Items.STICK));
            helper.assertTrue(wb.bufferEmpty(), "預かり箱に物が残った");
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            a.discard();
        });
    }

    /** 預かり箱の物は、右クリックで受け取れ（取りに来られなくなったとき）、壊すと落ちる。保存しても消えない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workbenchBufferIsKept")
    public static void workbenchBufferIsKept(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(TABLE, SinguloBlocks.ANDROID_WORKBENCH.get());
        io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity wb = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, TABLE);
        wb.buffer().insertItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_FENCE, 10), false);
        CompoundTag tag = wb.saveWithoutMetadata();
        var copy = new io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity(helper.absolutePos(TABLE),
                helper.getBlockState(TABLE));
        copy.load(tag);
        helper.assertTrue(copy.buffer().getStackInSlot(0).getCount() == 10, "保存すると預かり箱の物が消える");
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        wb.openMenu(player);
        helper.assertTrue(wb.bufferEmpty() && player.getInventory().countItem(net.minecraft.world.item.Items.OAK_FENCE) == 10,
                "右クリックで預かり箱の物を受け取れない");
        wb.buffer().insertItem(0, new ItemStack(net.minecraft.world.item.Items.STICK, 5), false);
        helper.getLevel().destroyBlock(helper.absolutePos(TABLE), false);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(TABLE)).inflate(2), e -> e.getItem().is(net.minecraft.world.item.Items.STICK)).size() == 1,
                    "壊しても預かり箱の物が落ちない");
            helper.succeed();
        });
    }

    /** 作業の持ち物: 同じ物は1つのスタックにまとまる（半端なスタックに分かれたままにしない）。入れるときも、使ったあとも。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_carryStacksSameItems")
    public static void carryStacksSameItems(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = worker(helper, false);
        int[] slots = a.carrySlots();                                  // 作業していないときは、持ち物の5つ全部
        java.util.function.Supplier<Integer> stacks = () -> {
            int n = 0;
            for (int slot : a.carrySlots()) {
                n += a.inventory().getItem(slot).isEmpty() ? 0 : 1;
            }
            return n;
        };
        // 素材を使ったあとのように、板材が3つのスロットに少しずつ残っている
        a.inventory().setItem(slots[0], new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 20));
        a.inventory().setItem(slots[2], new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 20));
        a.inventory().setItem(slots[4], new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 20));
        ItemStack rest = a.addToCarry(new ItemStack(net.minecraft.world.item.Items.OAK_FENCE, 3));
        helper.assertTrue(rest.isEmpty() && stacks.get() == 2, "入れるときに同じ物がまとまらない: " + stacks.get() + " スロット");
        a.addToCarry(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 50));           // 板材 110 → 2スタック
        helper.assertTrue(stacks.get() == 3, "板材が2スタックにまとまらない: " + stacks.get() + " スロット");
        a.takeFromCarry(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), 60);        // 板材 50 → 1スタック
        helper.assertTrue(stacks.get() == 2, "使ったあとに半端なスタックが残る: " + stacks.get() + " スロット");
        a.discard();
        helper.succeed();
    }

    /**
     * 作っている途中で充電が切れたら、作業を中断してステーションへ充電しに行き、満タンになったら続きをして注文を終える
     * （充電切れと起動を繰り返して止まったままにならない）。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 4800, batch = "automation_craftingPausesToRecharge")
    public static void craftingPausesToRecharge(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        floor(helper);
        var console = workshop(helper, 4);
        AndroidStationBlockEntity s = station(helper, 1);
        int[] lowPower = {0};
        int[] last = {-1};
        boolean[] ordered = {false}, charged = {false};
        StringBuilder trail = new StringBuilder();
        helper.onEachTick(() -> {
            List<RuinAndroid> list = androids(helper);
            if (list.isEmpty() || !list.get(0).printed()) {
                return;
            }
            RuinAndroid a = list.get(0);
            if (!ordered[0] && a.state() == RuinAndroid.STATE_IDLE) {
                ordered[0] = true;
                CompoundTag saved = a.saveWithoutId(new CompoundTag());
                saved.putInt("charge", 25);                        // 2回作ると切れる
                a.load(saved);
                var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 16,
                        List.of(a.getUUID()), helper.absolutePos(DELIVERY));
                helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
            }
            int st = a.state();
            if (st == RuinAndroid.STATE_LOW_POWER && last[0] != RuinAndroid.STATE_LOW_POWER) {
                lowPower[0]++;
            }
            charged[0] |= st == RuinAndroid.STATE_CHARGING;
            if (st != last[0] && trail.length() < 1200) {
                trail.append(helper.getTick()).append(':').append(st).append('(').append(a.charge()).append(") ");
            }
            last[0] = st;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ordered[0], "印刷されない");
            helper.assertTrue(lowPower[0] >= 1 && lowPower[0] <= 2, "充電切れを繰り返している: " + lowPower[0] + " 回 " + trail);
            helper.assertTrue(charged[0], "ステーションで充電していない: " + trail);
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 16,
                    "棒が16本届かない: " + delivered(helper, net.minecraft.world.item.Items.STICK));
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            androids(helper).forEach(RuinAndroid::discard);
        });
    }

    /** 手が空いていてステーションの近くにいれば、充電が減っているときは自分で戻って満タンにする。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 1600, batch = "automation_idleGuardTopsUpCharge")
    public static void idleGuardTopsUpCharge(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        floor(helper);
        station(helper, 1);
        boolean[] drained = {false}, docked = {false};
        helper.onEachTick(() -> {
            List<RuinAndroid> list = androids(helper);
            if (list.isEmpty() || !list.get(0).printed() || drained[0] || list.get(0).state() != RuinAndroid.STATE_IDLE) {
                if (!list.isEmpty() && drained[0]) {
                    docked[0] |= list.get(0).state() == RuinAndroid.STATE_CHARGING;
                }
                return;
            }
            RuinAndroid a = list.get(0);
            drained[0] = true;
            CompoundTag saved = a.saveWithoutId(new CompoundTag());
            saved.putInt("charge", a.maxCharge() / 2);                     // 半分（充電切れではない）
            a.load(saved);
            a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(6, 1, 6))));
        });
        helper.succeedWhen(() -> {
            List<RuinAndroid> list = androids(helper);
            helper.assertTrue(drained[0] && docked[0], "充電が減っても、ステーションへ充電しに戻らない");
            RuinAndroid a = list.get(0);
            helper.assertTrue(a.charge() == a.maxCharge(), "満タンにならない: " + a.charge());
            list.forEach(RuinAndroid::discard);
        });
    }

    /** 素材が足りない注文は、計算で止まる（原木1本では棒16本は作れない）。在庫で足りれば作れる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workPlanNeedsMaterials")
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

    /** 「新規」の注文: 在庫にある完成品は使わず、注文した数を新しく作る（在庫を使う注文なら、在庫だけで足りる）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workOrderFreshIgnoresFinishedStock")
    public static void workOrderFreshIgnoresFinishedStock(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 1);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.STICK, 4));
        ItemStack stick = new ItemStack(net.minecraft.world.item.Items.STICK);
        var fromStock = console.plan(stick, 4);
        helper.assertTrue(fromStock.ok() && fromStock.steps.isEmpty(), "在庫の棒4本で足りるのに、作る工程がある: " + fromStock.steps.size());
        var fresh = console.plan(stick, 4, true);
        helper.assertTrue(fresh.ok() && fresh.steps.size() == 2, "新規なのに、在庫の棒を使った（工程の数: " + fresh.steps.size() + "）");
        helper.succeed();
    }

    /** 持ち物とバックパックの中の並べ替え: 同じ物はまとめ、物の ID の順に前から詰める。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_androidSortsCarriedItems")
    public static void androidSortsCarriedItems(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.setNoAi(true);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        var inv = a.inventory();
        inv.setItem(RuinAndroid.CARRY_START, new ItemStack(net.minecraft.world.item.Items.STICK, 10));
        inv.setItem(RuinAndroid.CARRY_START + 1, new ItemStack(net.minecraft.world.item.Items.DIRT, 5));
        inv.setItem(RuinAndroid.CARRY_START + 3, new ItemStack(net.minecraft.world.item.Items.STICK, 20));
        helper.assertTrue(a.sortCarry(), "並べ替えられない");
        helper.assertTrue(inv.getItem(RuinAndroid.CARRY_START).is(net.minecraft.world.item.Items.DIRT) && inv.getItem(RuinAndroid.CARRY_START).getCount() == 5,
                "1つめが土5個でない: " + inv.getItem(RuinAndroid.CARRY_START));
        helper.assertTrue(inv.getItem(RuinAndroid.CARRY_START + 1).is(net.minecraft.world.item.Items.STICK) && inv.getItem(RuinAndroid.CARRY_START + 1).getCount() == 30,
                "棒がまとまって2つめに来ない: " + inv.getItem(RuinAndroid.CARRY_START + 1));
        helper.assertTrue(inv.getItem(RuinAndroid.CARRY_START + 2).isEmpty() && inv.getItem(RuinAndroid.CARRY_START + 3).isEmpty(), "後ろが空かない");
        a.discard();
        helper.succeed();
    }

    /** 「預ける」: 近くのチェストまで行って、持ち物を入れる。近くに箱がなければ行かない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400, batch = "automation_androidStashesIntoNearbyChest")
    public static void androidStashesIntoNearbyChest(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(p(1, 1, 1)), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(net.minecraft.world.item.Items.STICK, 12));
        helper.assertTrue(a.requestStash() != null, "近くに箱がないのに、預けに行くことになった");
        helper.setBlock(p(5, 1, 2), net.minecraft.world.level.block.Blocks.CHEST);
        helper.assertTrue(a.requestStash() == null, "近くのチェストへ預けに行かない");
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, p(5, 1, 2));
            helper.assertTrue(chest.getItem(0).is(net.minecraft.world.item.Items.STICK) && chest.getItem(0).getCount() == 12, "チェストに棒が入らない");
            helper.assertTrue(a.inventory().getItem(RuinAndroid.CARRY_START).isEmpty(), "持ち物に棒が残っている");
            a.discard();
        });
    }

    /** アンドロイドが資材口から原木を取り、作業台で板材と棒を作って、納品口へ棒を届ける。作業中は持ち物を取り出せない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidCraftsAndDelivers")
    public static void androidCraftsAndDelivers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(p(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        var player = player(helper);
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.assertTrue(a.isWorking(), "アンドロイドが作業中にならない");
        long making = io.github.genichimaruo.singulo.android.work.WorkConsoleView.making(console, new ItemStack(net.minecraft.world.item.Items.STICK));
        helper.assertTrue(making == 8, "作っている最中の数（画面の枠）が注文の数でない: " + making);
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

    /** アンドロイドを選ばずに注文すると、作業量に見合う人数だけ招集する（作業台1つの小さな注文に、3体のうち1体だけ）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workConsoleRecruitsOnlyNeeded")
    public static void workConsoleRecruitsOnlyNeeded(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        List<BladeAndroid> crew = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
            a.makeGuard(null, helper.absolutePos(p(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
            a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(1 + i, 1, 2))));
            helper.getLevel().addFreshEntity(a);
            crew.add(a);
        }
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        // Other concurrently running fixtures may also have unowned guards in range.
        var candidates = console.candidates(player);
        helper.assertTrue(candidates.containsAll(crew), "用意した3体が選べる候補にならない");
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        var order = console.orders().get(0);
        long working = order.workers.stream().map(id -> helper.getLevel().getEntity(id))
                .filter(e -> e instanceof RuinAndroid a && a.isWorking()).count();
        helper.assertTrue(order.workers.size() == 1 && working == 1, "必要以上に招集した: 受け持ち " + order.workers.size() + " / 作業中 " + working);
        // 画面で「自動」（だれも選ばない）にすると、選べる候補をみな送ってくる。そのときも必要な数だけ
        console.cancel(order.id, null);
        var error2 = console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8,
                crew.stream().map(RuinAndroid::getUUID).toList(), true, helper.absolutePos(DELIVERY));
        helper.assertTrue(error2 == null, "自動で注文できない: " + (error2 == null ? "" : error2.getString()));
        var auto = console.orders().get(console.orders().size() - 1);
        helper.assertTrue(auto.workers.size() == 1, "自動の注文で、候補を全員招集した: " + auto.workers.size());
        crew.forEach(RuinAndroid::discard);
        helper.succeed();
    }

    /** Automatic recruitment must retain the distant backpack worker needed by a six-stack cake recipe. */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_workConsoleAutoKeepsCapableWorker")
    public static void workConsoleAutoKeepsCapableWorker(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        for (int i = 0; i < 3; i++) chest.setItem(i, new ItemStack(Items.MILK_BUCKET));
        chest.setItem(3, new ItemStack(Items.SUGAR, 2));
        chest.setItem(4, new ItemStack(Items.EGG));
        chest.setItem(5, new ItemStack(Items.WHEAT, 3));
        var grid = List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR),
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT));
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(3, recipeCard(helper, grid, new ItemStack(Items.CAKE)));
        BladeAndroid small = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        BladeAndroid capable = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        for (BladeAndroid a : List.of(small, capable)) {
            a.makeGuard(null, helper.absolutePos(p(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
            a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(a == small ? p(1, 1, 0) : p(6, 1, 6))));
            helper.getLevel().addFreshEntity(a);
        }
        capable.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(item("android_backpack_upgrade")));
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        for (boolean gui : List.of(false, true)) {
            var ids = gui ? List.of(small.getUUID(), capable.getUUID()) : List.<java.util.UUID>of();
            var error = console.start(player, new ItemStack(Items.CAKE), 1, ids, gui, helper.absolutePos(DELIVERY));
            helper.assertTrue(error == null, "Cake order rejected before recruitment: " + error);
            var order = console.orders().get(console.orders().size() - 1);
            if (gui) {
                // Explicit GUI candidates isolate the closest-small / distant-capable regression.
                helper.assertTrue(order.workers.contains(capable.getUUID()), "Automatic recruitment discarded the only capable worker");
            } else {
                // Empty IDs deliberately discover all nearby guards, including other fixtures.
                helper.assertTrue(order.workers.stream().allMatch(id ->
                                helper.getLevel().getEntity(id) instanceof RuinAndroid a && a.carryCapacity() >= 6),
                        "Automatic recruitment selected a worker unable to carry the six cake input stacks");
            }
            helper.assertTrue(order.workers.size() == 1, "Cake requires only its capable worker, not extra idle workers");
            console.cancel(order.id, null);
        }
        small.discard();
        capable.discard();
        helper.succeed();
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
        a.makeGuard(null, helper.absolutePos(p(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
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
        BlockPos furnacePos = p(0, 1, 3);
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
        var player = player(helper);
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2, List.of(a.getUUID()),
                helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 2, "鉄インゴットが2つ届かない" + " pos=" + a.position() + " ticks=" + a.tickCount + " health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " guard=" + a.isGuard() + " printed=" + a.printed() + " target=" + a.getTarget() + " work=" + a.saveWorkState() + " console=" + console.saveWithoutMetadata() + " furnace=" + furnace.saveWithoutMetadata());
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            a.discard();
        });
    }

    /** 燃料の「満タン」: 燃料が切れたら、1個ずつではなく、装置に入るだけ（在庫の石炭5個を全部）持ってくる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidFillsFuel")
    public static void androidFillsFuel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = p(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2));
        chest.setItem(2, new ItemStack(net.minecraft.world.item.Items.COAL, 5));
        ItemStack loc = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(loc, level, helper.absolutePos(furnacePos),
                net.minecraft.world.level.block.Blocks.FURNACE);
        java.util.List<ItemStack> in = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        in.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        ItemStack card = new ItemStack(item("recipe_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.processing(in, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of(),
                new ItemStack(net.minecraft.world.item.Items.COAL), io.github.genichimaruo.singulo.android.work.RecipeData.DEFAULT_FACES
                        | io.github.genichimaruo.singulo.android.work.RecipeData.FUEL_FILL).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS + 1, card);
        BladeAndroid a = worker(helper, false);
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 2, "鉄インゴットが2つ届かない");
            int coal = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                coal += chest.getItem(i).is(net.minecraft.world.item.Items.COAL) ? chest.getItem(i).getCount() : 0;
            }
            helper.assertTrue(coal == 0, "満タンなのに石炭を全部持ってこない（残り " + coal + "）");
            a.discard();
        });
    }

    /**
     * 燃料が素材置き場になく、作れるとき: 燃料（樫の板材）を作る注文を自動で出し、原木から板材を作って、かまどに足して焼き上げる。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_multitask", timeoutTicks = 3000)
    public static void androidOrdersMissingFuel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);                  // 原木2本と、板材・棒のレシピ
        var level = helper.getLevel();
        BlockPos furnacePos = p(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2));
        ItemStack loc = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(loc, level, helper.absolutePos(furnacePos),
                net.minecraft.world.level.block.Blocks.FURNACE);
        java.util.List<ItemStack> in = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        in.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        ItemStack card = new ItemStack(item("recipe_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.processing(in, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of(),
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 2)).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS + 1, card);
        BladeAndroid a = worker(helper, false);
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        boolean[] fuelOrdered = {false};
        helper.onEachTick(() -> fuelOrdered[0] |= console.orders().stream().anyMatch(o -> o.target.is(net.minecraft.world.item.Items.OAK_PLANKS)));
        helper.succeedWhen(() -> {
            helper.assertTrue(fuelOrdered[0], "燃料（板材）を作る注文を出さない");
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 2, "燃料を作って、鉄インゴットを2つ届けない");
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            a.discard();
        });
    }

    /** 入れる面を間違えたレシピ（かまどの下の面から原鉄）: 立ち尽くさず、注文の時点で理由を出して断る。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_wrongInputFaceIsRefused")
    public static void wrongInputFaceIsRefused(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = p(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, furnacePos);
        furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 4));   // 下の面にも見える燃料の欄が埋まっていても見分ける
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2));
        ItemStack loc = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(loc, level, helper.absolutePos(furnacePos),
                net.minecraft.world.level.block.Blocks.FURNACE);
        java.util.List<ItemStack> in = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        in.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        int down = net.minecraft.core.Direction.DOWN.get3DDataValue();
        ItemStack card = new ItemStack(item("recipe_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.processing(in, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of(),
                ItemStack.EMPTY, io.github.genichimaruo.singulo.android.work.RecipeData.faces(down, down,
                        io.github.genichimaruo.singulo.android.work.RecipeData.FACE_AUTO)).write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS + 1, card);
        BladeAndroid a = worker(helper, false);
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error != null && error.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("gui.singulo.work_console.error.wrong_face"), "入れる面の間違いを断らない: " + error);
        a.discard();
        helper.succeed();
    }

    /**
     * 装置の加工を待つあいだに、ほかの作業をする: かまどに原鉄を入れたら、焼けるのを待たずに、もう1つの注文（棒）を作って届け、
     * 焼けたら取りに戻って、鉄インゴットも届ける。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "android_multitask", timeoutTicks = 3000)
    public static void androidWorksWhileFurnaceSmelts(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 2);
        var level = helper.getLevel();
        BlockPos furnacePos = p(0, 1, 3);
        helper.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE);
        net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, furnacePos);
        furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CHEST);
        chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 4));
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
        var player = player(helper);
        var error = console.start(player, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 4, List.of(a.getUUID()),
                helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "鉄インゴットを注文できない: " + (error == null ? "" : error.getString()));
        error = console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "作業中のアンドロイドに、2つ目の注文を受け持たせられない: " + (error == null ? "" : error.getString()));
        int[] ingotsWhenSticksDone = {-1};
        helper.onEachTick(() -> {
            if (ingotsWhenSticksDone[0] < 0 && delivered(helper, net.minecraft.world.item.Items.STICK) >= 8) {
                ingotsWhenSticksDone[0] = delivered(helper, net.minecraft.world.item.Items.IRON_INGOT);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.IRON_INGOT) == 4, "鉄インゴットが4つ届かない");
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.STICK) == 8, "棒が8本届かない");
            helper.assertTrue(console.orders().isEmpty(), "注文が終わらない");
            helper.assertTrue(ingotsWhenSticksDone[0] >= 0 && ingotsWhenSticksDone[0] < 4,
                    "かまどを待っているあいだに棒を作らなかった（棒が届いたときの鉄インゴット: " + ingotsWhenSticksDone[0] + "）");
            helper.assertFalse(a.isWorking(), "注文が終わっても作業中のまま");
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
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 4,
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
        BlockPos furnacePos = p(0, 1, 3);
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
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 2,
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
        BlockPos from = p(0, 1, 5), to = p(1, 1, 5);
        // Natural gravel above the cleared template must not refill a mined cell.
        helper.setBlock(from.above(6), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(to.above(6), net.minecraft.world.level.block.Blocks.STONE);
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
        var player = player(helper);
        ItemStack cobble = new ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
        helper.assertTrue(console.start(player, cobble, 2, List.of(plain.getUUID()), helper.absolutePos(DELIVERY)) != null,
                "採掘アップグレードなしで採掘の注文ができた");
        plain.discard();
        BladeAndroid a = worker(helper, true);
        var error = console.start(player, cobble, 2, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.COBBLESTONE) == 2, "丸石が2つ届かない" + " pos=" + a.position() + " ticks=" + a.tickCount + " health=" + a.getHealth() + " charge=" + a.charge() + " removed=" + a.isRemoved() + " guard=" + a.isGuard() + " printed=" + a.printed() + " target=" + a.getTarget() + " work=" + a.saveWorkState() + " console=" + console.saveWithoutMetadata() + " message=" + console.message() + " worker=" + a.saveWithoutId(new net.minecraft.nbt.CompoundTag()) + " delivery=" + io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, DELIVERY).saveWithoutMetadata());
            helper.assertTrue(level.getBlockState(helper.absolutePos(from)).isAir() && level.getBlockState(helper.absolutePos(to)).isAir(),
                    "範囲の石が掘られていない: from=" + helper.absolutePos(from) + "=" + level.getBlockState(helper.absolutePos(from))
                            + ", to=" + helper.absolutePos(to) + "=" + level.getBlockState(helper.absolutePos(to))
                            + ", delivery=" + delivered(helper, net.minecraft.world.item.Items.COBBLESTONE) + ", worker=" + a.saveWithoutId(new net.minecraft.nbt.CompoundTag())
                            + ", console=" + console.saveWithoutMetadata());
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
        var player = player(helper);
        var error = console.register(player, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), 4,
                io.github.genichimaruo.singulo.android.work.StandingOrder.KEEP, null, List.of(a.getUUID()));   // 並んで動くほかのテストのアンドロイドを巻き込まない
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
        var player = player(helper);
        console.register(player, new ItemStack(net.minecraft.world.item.Items.STICK), 4,
                io.github.genichimaruo.singulo.android.work.StandingOrder.PULSE, helper.absolutePos(DELIVERY), List.of(a.getUUID()));   // 並んで動くほかのテストのアンドロイドを巻き込まない
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
        var player = player(helper);
        console.start(player, new ItemStack(net.minecraft.world.item.Items.STICK), 8, List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        net.minecraft.world.entity.monster.Zombie[] attacker = {null};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.tickCount >= 5 && a.active(), "個体のAIが動き始めるのを待つ"))
                .thenExecute(() -> {
                    var zombie = EntityType.ZOMBIE.create(helper.getLevel());
                    zombie.moveTo(a.position().add(1.5, 0, 0));
                    zombie.setNoAi(true);
                    zombie.setPersistenceRequired();       // プレイヤーから遠いと消える（遠くで動くほかのテストのプレイヤーが一番近いとき）
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
        // Keep the farm level with the walking surface; raised crops create a
        // pathfinding ledge beside the machines. Light propagation is asynchronous:
        // wait for irrigation and lighting before planting crops.
        helper.setBlock(p(3, 3, 5), net.minecraft.world.level.block.Blocks.GLOWSTONE);
        helper.setBlock(p(2, 0, 5), net.minecraft.world.level.block.Blocks.WATER);
        for (int x = 0; x < 2; x++) {
            helper.setBlock(p(x, 0, 5), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int x = 0; x < 2; x++) {
                helper.assertTrue(helper.getLevel().getRawBrightness(helper.absolutePos(p(x, 1, 5)), 0) >= 9,
                        "畑の照明更新が終わっていない");
            }
        }).thenExecute(() -> {
            for (int x = 0; x < 2; x++) {
                helper.setBlock(p(x, 1, 5), ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT)
                        .getStateForAge(7));
            }
            procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                    io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, helper.absolutePos(p(0, 1, 5)),
                    helper.absolutePos(p(1, 1, 5)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.WHEAT, 2));
            BladeAndroid a = worker(helper, false);
            var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.WHEAT), 2, List.of(a.getUUID()),
                    helper.absolutePos(DELIVERY));
            helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
            helper.succeedWhen(() -> {
                helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.WHEAT) == 2, "小麦が2つ届かない: count=" + delivered(helper, net.minecraft.world.item.Items.WHEAT) + " position=" + a.position() + " target=" + a.getTarget() + " health=" + a.getHealth() + " work=" + a.saveWorkState() + " crop0=" + helper.getBlockState(p(0, 1, 5)) + " crop1=" + helper.getBlockState(p(1, 1, 5)) + " soil0=" + helper.getBlockState(p(0, 0, 5)) + " light=" + helper.getLevel().getRawBrightness(helper.absolutePos(p(0, 1, 5)), 0) + " message=" + console.message());
                helper.assertTrue(helper.getBlockState(p(0, 1, 5)).is(net.minecraft.world.level.block.Blocks.WHEAT)
                        && helper.getBlockState(p(1, 1, 5)).is(net.minecraft.world.level.block.Blocks.WHEAT), "植え直していない");
                a.discard();
            });
        });
    }

    /** 収穫: 芽生えたアメジストの上の、育ちきった塊だけを取る（芽生えたアメジストは壊さない）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidHarvestsAmethyst")
    public static void androidHarvestsAmethyst(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        helper.setBlock(p(1, 1, 6), net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST);
        helper.setBlock(p(1, 2, 6), net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, helper.absolutePos(p(0, 1, 5)),
                helper.absolutePos(p(2, 3, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 4));
        BladeAndroid a = worker(helper, false);
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD), 4,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.AMETHYST_SHARD) == 4, "アメジストの欠片が4つ届かない");
            helper.assertTrue(helper.getBlockState(p(1, 1, 6)).is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST),
                    "芽生えたアメジストを壊した");
            a.discard();
        });
    }

    /** 伐採: 伐採アップグレードを積んだアンドロイドが、木を丸ごと切って原木を届ける。積んでいなければ注文できない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidFellsTree")
    public static void androidFellsTree(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        helper.setBlock(p(1, 0, 6), net.minecraft.world.level.block.Blocks.DIRT);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(p(1, y, 6), net.minecraft.world.level.block.Blocks.OAK_LOG);
        }
        helper.setBlock(p(1, 4, 6), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL, helper.absolutePos(p(0, 1, 5)),
                helper.absolutePos(p(2, 1, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 3));
        var player = player(helper);
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
            helper.assertFalse(helper.getBlockState(p(1, 3, 6)).is(net.minecraft.world.level.block.Blocks.OAK_LOG), "木の上が残った");
            a.discard();
        });
    }

    /** 毛刈り: 範囲の羊の毛を刈り、落ちた羊毛を拾って届ける。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 2400, batch = "automation_androidShearsSheep")
    public static void androidShearsSheep(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var sheep = EntityType.SHEEP.create(helper.getLevel());
        sheep.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(1, 1, 6))));
        sheep.setNoAi(true);
        helper.getLevel().addFreshEntity(sheep);
        procedureCard(helper, List.of(new io.github.genichimaruo.singulo.android.work.ProcedureStep(
                io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR, helper.absolutePos(p(0, 1, 5)),
                helper.absolutePos(p(2, 1, 7)), ItemStack.EMPTY)), new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL));
        BladeAndroid a = worker(helper, false);
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL), 1,
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
        var player = player(helper);
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
        var error = console.start(player(helper), new ItemStack(planks), 3, List.of(a.getUUID()), null);
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

    /** 止まっている理由: 電力のないステーションで充電しようとすると、「ステーションの電力が足りない」を出す。電力が戻れば消える。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 1200, batch = "automation_stallShowsStationOutOfPower")
    public static void stallShowsStationOutOfPower(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        floor(helper);
        AndroidStationBlockEntity s = station(helper, 1);
        boolean[] drained = {false}, seen = {false};
        helper.onEachTick(() -> {
            List<RuinAndroid> list = androids(helper);
            if (list.isEmpty() || !list.get(0).printed()) {
                return;
            }
            RuinAndroid a = list.get(0);
            if (!drained[0]) {
                // 印刷できたら、ステーションの電力とアンドロイドの充電を空にする
                drained[0] = true;
                s.energy().setEnergy(0);
                CompoundTag saved = a.saveWithoutId(new CompoundTag());
                saved.putInt("charge", 0);
                a.load(saved);
            } else if (!seen[0] && a.stall() == RuinAndroid.STALL_NO_POWER) {
                seen[0] = true;
                s.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);     // 電力を戻す
            }
        });
        helper.succeedWhen(() -> {
            List<RuinAndroid> list = androids(helper);
            helper.assertTrue(seen[0], "電力のないステーションで、止まっている理由が出ない: "
                    + list.stream().map(a -> "state=" + a.state() + " stall=" + a.stall() + " charge=" + a.charge()).toList());
            RuinAndroid a = list.get(0);
            helper.assertTrue(a.charge() > 0 && a.stall() == RuinAndroid.STALL_NONE, "電力が戻っても理由が消えない: " + a.stall()
                    + " charge=" + a.charge() + " state=" + a.state());
            list.forEach(RuinAndroid::discard);
        });
    }

    /**
     * 計画の持ち物の見積もり: 副産物も記録した数ずつ数え、1回ずつ作るあいだ一度も枠を超えない一番多い回数を選ぶ（決まった数の枠を
     * 空けておかない。1回多くすると、途中のどこかであふれる）。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_planCountsByproductsExactly")
    public static void planCountsByproductsExactly(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        var recipe = new io.github.genichimaruo.singulo.android.work.RecipeData(
                io.github.genichimaruo.singulo.android.work.RecipeData.MODE_PROCESSING, grid,
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), List.of(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3)));
        var max = io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.class.getDeclaredMethod("maxCrafts",
                io.github.genichimaruo.singulo.android.work.RecipeData.class, int.class, long.class);
        max.setAccessible(true);
        int slots = 5;
        long k = (long) max.invoke(null, recipe, slots, 10_000L);
        java.util.function.LongUnaryOperator peak = n -> {
            long worst = 0;
            for (long d = 0; d <= n; d++) {
                worst = Math.max(worst, (n - d + 63) / 64 + (d + 63) / 64 + (3 * d + 63) / 64);   // 原鉄の残り・インゴット・丸石
            }
            return worst;
        };
        helper.assertTrue(k > 0 && peak.applyAsLong(k) <= slots, "選んだ回数で途中にあふれる: " + k + " 回で " + peak.applyAsLong(k));
        helper.assertTrue(peak.applyAsLong(k + 1) > slots, "枠を使い切っていない（あと1回作れる）: " + k);
        helper.succeed();
    }

    /**
     * 採掘で一緒に出る物（前もって分からない）で持ち物がいっぱいになっても、足元に落とさない: 掘る前に収まるかを確かめ、
     * 収まらなければ置きに戻ってから続ける。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 3600, batch = "automation_miningNeverDropsWhenFull")
    public static void miningNeverDropsWhenFull(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        // 上の段は花崗岩（目当てではない物が出る）、下の段は石（丸石が出る）
        for (int x = 0; x <= 1; x++) {
            helper.setBlock(p(x, 2, 5), net.minecraft.world.level.block.Blocks.GRANITE);
            helper.setBlock(p(x, 1, 5), net.minecraft.world.level.block.Blocks.STONE);
        }
        var step = new io.github.genichimaruo.singulo.android.work.ProcedureStep(io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE,
                helper.absolutePos(p(0, 1, 5)), helper.absolutePos(p(1, 2, 5)), ItemStack.EMPTY);
        ItemStack card = new ItemStack(item("procedure_card"));
        io.github.genichimaruo.singulo.android.work.RecipeData.procedure(List.of(step), new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))
                .write(card, level.registryAccess());
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RACK);
        rack.items().setStackInSlot(io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity.COLS * 2 + 1, card);
        BladeAndroid a = worker(helper, true);
        // 持ち物5つのうち4つは私物でふさがっている（作業に使えるのは1つ）
        for (int i = 0; i < 4; i++) {
            a.inventory().setItem(RuinAndroid.CARRY_START + i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
        }
        var error = console.start(io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper), new ItemStack(net.minecraft.world.item.Items.COBBLESTONE), 2,
                List.of(a.getUUID()), helper.absolutePos(DELIVERY));
        helper.assertTrue(error == null, "注文できない: " + (error == null ? "" : error.getString()));
        boolean[] dropped = {false};
        helper.onEachTick(() -> dropped[0] |= !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10)).isEmpty());
        helper.succeedWhen(() -> {
            helper.assertFalse(dropped[0], "持ち物からあふれた物が足元に落ちた");
            helper.assertTrue(delivered(helper, net.minecraft.world.item.Items.COBBLESTONE) == 2, "丸石が2つ届かない");
            a.discard();
        });
    }

    /** 加工のバッチ投入では、充電10で原鉄を1個だけ投入し、その場で止まる。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_processingStopsWhenChargeRunsOut")
    public static void processingStopsWhenChargeRunsOut(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var console = workshop(helper, 0);
        var level = helper.getLevel();
        BlockPos furnacePos = p(0, 1, 3);
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
        var error = console.start(player(helper), new ItemStack(net.minecraft.world.item.Items.STICK), 8,
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
        var insert = goal.getClass().getDeclaredMethod("insertOnce", java.util.function.Function.class, java.util.function.Function.class,
                io.github.genichimaruo.singulo.android.work.RecipeData.class);
        java.util.function.Function<ItemStack, Object> toHandler = item -> handler, noFluid = item -> null;
        insert.setAccessible(true);
        int initialCharge = a.charge();
        helper.assertFalse((Boolean) insert.invoke(goal, toHandler, noFluid, recipe), "2種類とも入ったことになった");
        helper.assertTrue(handler.getStackInSlot(0).is(net.minecraft.world.item.Items.OAK_LOG), "最初の材料が入らない");
        helper.assertTrue(a.inventory().getItem(RuinAndroid.CARRY_START + 1).getCount() == 1, "未投入の材料を失った");
        handler.extractItem(0, 64, false);       // 装置が最初の材料を消費した
        CompoundTag checkpoint = a.saveWorkState();
        a.restoreWorkState(checkpoint);
        helper.assertTrue((Boolean) insert.invoke(goal, toHandler, noFluid, recipe), "未投入の材料から再開できない");
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
        var owner = player(helper);
        var friend = player(helper);
        var stranger = player(helper);
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
        BlockPos other = p(5, 1, 5);
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
        BlockPos at = helper.absolutePos(p(1, 1, 1));
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
    /** A full nearby chest must not mask another storage that can accept the cargo. */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_stashSkipsFullStorage")
    public static void stashSkipsFullStorage(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(2, 1, 2))));
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(Items.STICK, 12));
        BlockPos full = p(3, 1, 2), empty = p(6, 1, 2);
        helper.setBlock(full, net.minecraft.world.level.block.Blocks.CHEST);
        helper.setBlock(empty, net.minecraft.world.level.block.Blocks.CHEST);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, full);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.DIRT, 64));
        var accepts = RuinAndroid.class.getDeclaredMethod("stashable", BlockPos.class);
        accepts.setAccessible(true);
        helper.assertFalse((boolean) accepts.invoke(a, helper.absolutePos(full)), "Full chest hides usable storage");
        helper.assertTrue((boolean) accepts.invoke(a, helper.absolutePos(empty)), "Empty chest rejects cargo");
        helper.assertTrue(a.requestStash() == null, "Usable storage was not found");
        var target = RuinAndroid.class.getDeclaredField("stashTarget");
        target.setAccessible(true);
        helper.assertFalse(helper.absolutePos(full).equals(target.get(a)), "Selected full chest");
        a.discard();
        helper.succeed();
    }

    /** Loot functions may return one stack whose count exceeds its item stack limit. */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "automation_miningCapacitySplitsOversizedLoot")
    public static void miningCapacitySplitsOversizedLoot(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        var goalField = RuinAndroid.class.getDeclaredField("workGoal");
        goalField.setAccessible(true);
        Object goal = goalField.get(a);
        var fits = goal.getClass().getDeclaredMethod("fitsInCarry", List.class);
        fits.setAccessible(true);
        for (int i = 0; i < 4; i++) a.inventory().setItem(RuinAndroid.CARRY_START + i, new ItemStack(Items.DIRT, 64));
        helper.assertTrue((boolean) fits.invoke(goal, List.of(new ItemStack(Items.COBBLESTONE, 64))), "One stack should fit");
        helper.assertFalse((boolean) fits.invoke(goal, List.of(new ItemStack(Items.COBBLESTONE, 128))), "Oversized loot was counted as one slot");
        a.inventory().setItem(RuinAndroid.CARRY_START + 3, ItemStack.EMPTY);
        helper.assertTrue((boolean) fits.invoke(goal, List.of(new ItemStack(Items.COBBLESTONE, 128))), "Two stacks should fit in two slots");
        a.discard();
        helper.succeed();
    }

    /** Supply ports route cargo to adjacent storage instead of exposing their own inventory. */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200, batch = "automation_androidStashesViaSupplyPort")
    public static void androidStashesViaSupplyPort(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        a.makeGuard(null, helper.absolutePos(STATION), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(p(4, 1, 2))));
        helper.getLevel().addFreshEntity(a);
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(Items.STICK, 12));
        BlockPos port = p(5, 1, 2), box = p(6, 1, 2);
        helper.setBlock(port, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        helper.setBlock(box, net.minecraft.world.level.block.Blocks.CHEST);
        var accepts = RuinAndroid.class.getDeclaredMethod("stashable", BlockPos.class);
        accepts.setAccessible(true);
        helper.assertTrue((boolean) accepts.invoke(a, helper.absolutePos(port)), "Supply port does not expose adjacent storage");
        helper.assertTrue(a.requestStash() == null, "Supply storage was not found");
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, box);
            helper.assertTrue(chest.getItem(0).is(Items.STICK) && chest.getItem(0).getCount() == 12, "Cargo did not arrive via supply port");
            helper.assertTrue(a.inventory().getItem(RuinAndroid.CARRY_START).isEmpty(), "Stashed cargo remained in inventory");
            a.discard();
        });
    }

}
