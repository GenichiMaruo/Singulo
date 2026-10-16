package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.FactoryConveyorBlock;
import io.github.genichimaruo.singulo.ruin.FactoryLineBlock;
import io.github.genichimaruo.singulo.ruin.FoundryOverseer;
import io.github.genichimaruo.singulo.ruin.GuardianCoreBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinBoss;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlock;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 廃アンドロイド工場のボス（製造統括AI「ファウンドリ」）と、工場のブロック・工場式印刷ユニット。
 * 封印核は近くの製造ライン・コンベアを探すので、ほかのテストとは1つずつ別の組で動かす。
 */
@GameTestHolder("singulo_lab")
@PrefixGameTestTemplate(false)
public final class FactoryGameTests {
    private static final String HUGE = "huge";
    private static final String BATCH = "factory";
    private static final BlockPos VAULT = new BlockPos(1, 1, 1);
    private static final BlockPos CORE = new BlockPos(7, 1, 7);
    /** 4つの製造ライン（前を核へ向ける）。 */
    private static final List<BlockPos> LINES = List.of(new BlockPos(1, 1, 7), new BlockPos(13, 1, 7), new BlockPos(7, 1, 1),
            new BlockPos(7, 1, 13));
    private static final List<Direction> FACING = List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
    /** 床のコンベア（東へ流れる。テストの場所の床の1段上に並べる）。 */
    private static final int CONVEYOR_Z = 11, CONVEYOR_Y = 1;

    private FactoryGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** 中央管制ホールを小さく再現する: 封鎖された保管庫・封印核・4つの製造ライン・床のコンベアの列。 */
    private static GuardianCoreBlockEntity hall(GameTestHelper helper) {
        helper.setBlock(VAULT, SinguloBlocks.RUIN_CACHE.get().defaultBlockState().setValue(RuinCacheBlock.SEALED, true));
        ((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).setRuin("android_lab");
        for (int i = 0; i < LINES.size(); i++) {
            helper.setBlock(LINES.get(i), SinguloBlocks.FACTORY_LINE.get().defaultBlockState().setValue(FactoryLineBlock.FACING, FACING.get(i)));
        }
        for (int x = 3; x <= 11; x++) {
            helper.setBlock(new BlockPos(x, CONVEYOR_Y, CONVEYOR_Z), SinguloBlocks.FACTORY_CONVEYOR.get().defaultBlockState()
                    .setValue(FactoryConveyorBlock.FACING, Direction.EAST));
        }
        helper.setBlock(CORE, SinguloBlocks.GUARDIAN_CORE.get());
        GuardianCoreBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, CORE);
        be.scan(helper.getLevel());
        // ほかの組のテストが残した仕掛けを数えないように、このテストの場所の中のものだけにする
        AABB bounds = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(64, 24, 64)));
        be.lines().removeIf(p -> !bounds.contains(Vec3.atCenterOf(p)));
        be.conveyors().removeIf(p -> !bounds.contains(Vec3.atCenterOf(p)));
        return be;
    }

    private static FoundryOverseer foundry(GameTestHelper helper, GuardianCoreBlockEntity core) {
        RuinBoss boss = core.awaken(helper.getLevel());
        helper.assertTrue(boss instanceof FoundryOverseer, "廃アンドロイド工場の部屋でファウンドリが現れない: " + boss);
        boss.skipEmerging();
        return (FoundryOverseer) boss;
    }

    /** 動かない的（倒れにくいハスク）。 */
    private static Zombie dummy(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawn(EntityType.HUSK, at);                 // 日に焼けない
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        return z;
    }

    private static int dropped(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 封印核と製造ライン

    /** 封印核はファウンドリを起こし、4つの製造ラインを数える。ラインが動いている間は受けるダメージが小さく、壊すと大きくなる。 */
    @GameTest(template = HUGE, batch = BATCH + "_1", timeoutTicks = 80)
    public static void linesShieldTheFoundry(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        helper.assertTrue(f.lineCount() == 4, "製造ラインを4つ数えない: " + f.lineCount());
        helper.assertTrue(core.awaken(helper.getLevel()) == null, "2体目が現れた");
        float before = f.getHealth();
        f.hurt(helper.getLevel().damageSources().generic(), 10);
        float shielded = before - f.getHealth();
        helper.assertTrue(Math.abs(shielded - 2) < 0.01, "4つのラインで受けるダメージが2割にならない: " + shielded);
        for (BlockPos p : LINES) {
            helper.setBlock(p, Blocks.AIR);
        }
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(f.lineCount() == 0, "ラインを壊しても数が減らない: " + f.lineCount());
            float h = f.getHealth();
            f.hurt(helper.getLevel().damageSources().generic(), 10);
            helper.assertTrue(Math.abs(h - f.getHealth() - 10) < 0.01, "ラインがないのにダメージが減る: " + (h - f.getHealth()));
            core.restoreProps(helper.getLevel());
            helper.assertTrue(helper.getBlockState(LINES.get(0)).is(SinguloBlocks.FACTORY_LINE.get())
                    && helper.getBlockState(LINES.get(0)).getValue(FactoryLineBlock.FACING) == Direction.EAST, "封印核がラインを元の向きで直さない");
            f.discard();
            helper.succeed();
        });
    }

    /** 倒すと保管庫の封鎖が解け、精密アクチュエーターを落とし、コンベアは止まる。 */
    @GameTest(template = HUGE, batch = BATCH + "_2", timeoutTicks = 120)
    public static void foundryDefeatUnsealsVault(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        f.setConveyors(true);
        helper.assertTrue(helper.getBlockState(new BlockPos(5, CONVEYOR_Y, CONVEYOR_Z)).getValue(FactoryConveyorBlock.RUNNING), "コンベアが動き出さない");
        // 研究所の範囲を覚えた封印核: 倒すと、範囲の中の耐圧殻の封じが解ける
        BlockPos hull = new BlockPos(13, 1, 13);
        helper.setBlock(hull, SinguloBlocks.PRESSURE_HULL.get().defaultBlockState()
                .setValue(io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock.SEALED, true));
        core.setFacility(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(14, 14, 14))));
        f.kill();
        helper.runAtTickTime(FoundryOverseer.DEATH_TICKS + 10, () -> {
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒しても保管庫が開かない");
            int n = dropped(helper, item("precision_actuator"));
            helper.assertTrue(n >= 3 && n <= 4, "精密アクチュエーターを3〜4個落とさない: " + n);
            // 部屋に挑戦者がいないときは、試作機の設計メモリをどちらか1つ
            int memories = dropped(helper, item("android_memory_axe")) + dropped(helper, item("android_memory_bit"));
            helper.assertTrue(memories == 1, "試作機の設計メモリを1つ落とさない: " + memories);
            helper.assertFalse(helper.getBlockState(new BlockPos(5, CONVEYOR_Y, CONVEYOR_Z)).getValue(FactoryConveyorBlock.RUNNING), "倒してもコンベアが止まらない");
            helper.assertFalse(helper.getBlockState(hull).getValue(io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock.SEALED),
                    "倒しても耐圧殻の封じが解けない");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ コンベア

    /** 動いているコンベアは上に乗ったものを流し、決まった間ごとに（その前に警告して）向きを変える。 */
    @GameTest(template = HUGE, batch = BATCH + "_3", timeoutTicks = FoundryOverseer.CONVEYOR_PERIOD + 60)
    public static void conveyorsCarryAndReverse(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        f.setConveyors(true);
        // 動きを持たない（AI を止めた）モブは流れないので、落ちている物で確かめる
        ItemEntity box = helper.spawnItem(net.minecraft.world.item.Items.STONE, 4.5F, CONVEYOR_Y + 1.1F, CONVEYOR_Z + 0.5F);
        box.setPickUpDelay(400);
        double startX = box.getX();
        BlockPos probe = new BlockPos(5, CONVEYOR_Y, CONVEYOR_Z);
        boolean[] warned = {false};
        helper.onEachTick(() -> warned[0] |= helper.getBlockState(probe).getValue(FactoryConveyorBlock.WARN));
        helper.runAtTickTime(30, () -> helper.assertTrue(box.getX() > startX + 1.0, "コンベアが上に乗ったものを流さない: " + (box.getX() - startX)));
        helper.runAtTickTime(FoundryOverseer.CONVEYOR_PERIOD + 20, () -> {
            BlockState s = helper.getBlockState(probe);
            helper.assertTrue(warned[0], "向きを変える前に警告しない");
            helper.assertTrue(s.getValue(FactoryConveyorBlock.FACING) == Direction.WEST, "コンベアが向きを変えない: " + s);
            box.discard();
            f.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 技

    /** 溶接レーザーは床すれすれをなぞる: 床に立つ相手には当たり、高い所の相手には当たらない。 */
    @GameTest(template = HUGE, batch = BATCH + "_4", timeoutTicks = FoundryOverseer.LASER_AIM + FoundryOverseer.LASER_SWEEP + 30)
    public static void laserSkimsTheFloor(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        Zombie low = dummy(helper, new BlockPos(11, 1, 7));
        helper.setBlock(new BlockPos(11, 1, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(11, 2, 6), Blocks.STONE);
        Zombie high = dummy(helper, new BlockPos(11, 3, 6));
        f.startLaser(low);
        helper.runAtTickTime(FoundryOverseer.LASER_AIM + FoundryOverseer.LASER_SWEEP + 10, () -> {
            helper.assertTrue(low.getHealth() < low.getMaxHealth(), "床に立つ相手にレーザーが当たらない");
            helper.assertTrue(high.getHealth() >= high.getMaxHealth(), "高い所の相手にレーザーが当たった");
            low.discard();
            high.discard();
            f.discard();
            helper.succeed();
        });
    }

    /** プレスは影の丸の中の相手を叩き、動きを鈍らせる。外にいれば当たらない。 */
    @GameTest(template = HUGE, batch = BATCH + "_5", timeoutTicks = FoundryOverseer.PRESS_WINDUP + 40)
    public static void pressSlamsAndSlows(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        Zombie in = dummy(helper, new BlockPos(3, 1, 3));
        Zombie out = dummy(helper, new BlockPos(11, 1, 3));
        f.startPress(in);
        helper.runAtTickTime(FoundryOverseer.PRESS_WINDUP + 5, () -> {
            helper.assertTrue(in.getHealth() < in.getMaxHealth() && in.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "プレスが叩かない・鈍らせない");
            helper.assertTrue(out.getHealth() >= out.getMaxHealth(), "丸の外の相手にプレスが当たった");
            in.discard();
            out.discard();
            f.discard();
            helper.succeed();
        });
    }

    /** ロボットアームは、狙いが止まったあと降りてきて、印の中の相手をつかんで投げる。 */
    @GameTest(template = HUGE, batch = BATCH + "_6", timeoutTicks = FoundryOverseer.ARM_AIM + 40)
    public static void armGrabsAndThrows(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        Zombie z = dummy(helper, new BlockPos(4, 1, 10));
        f.startArm(0, z);
        double[] rise = {0};
        helper.onEachTick(() -> rise[0] = Math.max(rise[0], z.getDeltaMovement().y));
        helper.runAtTickTime(FoundryOverseer.ARM_AIM + 4, () -> {
            helper.assertTrue(z.getHealth() < z.getMaxHealth(), "アームがつかんだ相手が傷つかない");
            helper.assertTrue(rise[0] > 0.5, "アームが相手を投げない: " + rise[0]);
            z.discard();
            f.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 量産型と試作機

    /** ラインは量産型（体力半分・何も落とさない）を、同時に4体まで印刷する。本気になると試作機を2体呼ぶ。 */
    @GameTest(template = HUGE, batch = BATCH + "_7", timeoutTicks = 60)
    public static void linesPrintMassAndPrototypes(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        FoundryOverseer f = foundry(helper, core);
        for (int i = 0; i < 6; i++) {
            f.printMass(helper.getLevel(), null);
        }
        List<RuinAndroid> mass = f.factoryMade(helper.getLevel());
        helper.assertTrue(mass.size() == FoundryOverseer.MAX_MASS, "量産型が4体にならない: " + mass.size());
        RuinAndroid one = mass.get(0);
        helper.assertTrue(!one.prototype() && one.getMaxHealth() < RuinAndroid.createAttributes().build()
                .getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), "量産型の体力が半分でない");
        Zombie z = dummy(helper, new BlockPos(4, 1, 4));
        f.setTarget(z);
        f.setHealth(f.getMaxHealth() * 0.4F);
        helper.runAtTickTime(10, () -> {
            long prototypes = f.factoryMade(helper.getLevel()).stream().filter(RuinAndroid::prototype).count();
            helper.assertTrue(prototypes == 2, "本気になっても試作機を2体呼ばない: " + prototypes);
            helper.assertTrue(f.factoryMade(helper.getLevel()).stream().filter(RuinAndroid::prototype)
                    .allMatch(a -> a instanceof io.github.genichimaruo.singulo.android.AxeAndroid
                            || a instanceof io.github.genichimaruo.singulo.android.BitAndroid), "試作機が斧型・ビット型でない");
            helper.assertTrue(f.factoryMade(helper.getLevel()).stream().filter(a -> !a.prototype())
                    .noneMatch(a -> a instanceof io.github.genichimaruo.singulo.android.AxeAndroid
                            || a instanceof io.github.genichimaruo.singulo.android.BitAndroid), "量産型に試作機の型が混じった");
            f.factoryMade(helper.getLevel()).forEach(a -> a.discard());
            z.discard();
            f.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 覚醒

    /** 覚醒の製造核で、保管庫が開いていても覚醒したファウンドリ（体力×2.5）が現れる。ほかの覚醒核では現れない。 */
    @GameTest(template = HUGE, batch = BATCH + "_8", timeoutTicks = 40)
    public static void foundryCoreSummonsAwakened(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GuardianCoreBlockEntity core = hall(helper);
        RuinCacheBlockEntity vault = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT);
        vault.refillIfDue(helper.getLevel(), helper.getLevel().getGameTime());
        vault.setSealed(false);
        helper.assertTrue(core.awaken(helper.getLevel()) == null, "保管庫が開いているのに普通の番人が現れた");
        helper.assertTrue(core.awakenAwakened(helper.getLevel(), null, new ItemStack(item("awakening_gravity_core"))) == null,
                "工場で覚醒の重力核が使えた");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, new ItemStack(item("awakening_foundry_core")));
        helper.assertTrue(boss instanceof FoundryOverseer && boss.awakened(), "覚醒の製造核で覚醒したファウンドリが現れない: " + boss);
        boss.skipEmerging();
        double expected = FoundryOverseer.health() * RuinBoss.awakenedHealthMultiplier();
        helper.assertTrue(Math.abs(boss.effectiveMaxHealth() - expected) < 1.0, "体力が " + expected + " 相当でない: " + boss.effectiveMaxHealth());
        boss.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ 探索コンパス

    /** 研究棟で調整したコンパス（封鎖培養施設まで探せる）は、分かれ道の廃アンドロイド工場も探せる。その前は探せない。 */
    @GameTest(template = "empty")
    public static void compassFindsFactoryAfterResearchBuilding(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        ItemStack compass = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get());
        io.github.genichimaruo.singulo.item.ExplorerCompassItem.upgrade(compass);
        helper.assertFalse(io.github.genichimaruo.singulo.item.ExplorerCompassItem.targets(compass).contains("android_lab"),
                "研究棟を攻略する前に工場を探せる");
        io.github.genichimaruo.singulo.item.ExplorerCompassItem.upgrade(compass);
        List<String> targets = io.github.genichimaruo.singulo.item.ExplorerCompassItem.targets(compass);
        helper.assertTrue(targets.contains("culture_facility") && targets.contains("android_lab"),
                "研究棟で調整しても工場を探せない: " + targets);
        helper.assertTrue(io.github.genichimaruo.singulo.item.ExplorerCompassItem.targetId(compass).equals("culture_facility"),
                "調整したら封鎖培養施設を探す向きにならない");
        helper.succeed();
    }
}
