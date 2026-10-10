package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import io.github.genichimaruo.singulo.ruin.HorizonBolt;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlock;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ホライズン・ウォーデンと封印コンソール・封鎖された保管庫の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BossGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos VAULT = new BlockPos(1, 1, 1);
    private static final BlockPos CONSOLE = new BlockPos(4, 1, 4);
    private static final long DAY = RuinCacheBlockEntity.TICKS_PER_DAY;

    private BossGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** 最終実験施設の中央を再現する: 封鎖された保管庫と封印コンソール。 */
    private static SealConsoleBlockEntity arena(GameTestHelper helper) {
        helper.setBlock(VAULT, SinguloBlocks.RUIN_CACHE.get().defaultBlockState().setValue(RuinCacheBlock.SEALED, true));
        RuinCacheBlockEntity vault = helper.getBlockEntity(VAULT);
        vault.setRuin("final_lab");
        helper.setBlock(CONSOLE, SinguloBlocks.SEAL_CONSOLE.get());
        return helper.getBlockEntity(CONSOLE);
    }

    private static RuinCacheBlockEntity vault(GameTestHelper helper) {
        return helper.getBlockEntity(VAULT);
    }

    /** 起動すると出現の演出が始まり、そのあいだは動かず傷つかない。演出が終わると戦い始める。 */
    @GameTest(template = EMPTY, timeoutTicks = HorizonWarden.EMERGE_TICKS + 40)
    public static void wardenEmergesBeforeFighting(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        helper.assertTrue(warden != null && warden.emergeTicks() == HorizonWarden.EMERGE_TICKS && warden.isNoAi(), "出現の演出が始まらない");
        helper.assertFalse(warden.hurt(helper.getLevel().damageSources().generic(), 50), "出現の途中で傷ついた");
        helper.runAtTickTime(HorizonWarden.EMERGE_TICKS + 5, () -> {
            helper.assertTrue(warden.emergeTicks() == 0 && !warden.isNoAi(), "演出が終わっても動き出さない");
            helper.assertTrue(warden.getHealth() == warden.getMaxHealth(), "演出の間に体力が減った");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void consoleWakesWardenWhileVaultSealed(GameTestHelper helper) {
        SealConsoleBlockEntity console = arena(helper);
        HorizonWarden warden = console.activate(helper.getLevel(), null);
        warden.skipEmerging();
        helper.assertTrue(warden != null, "守護機が起動しない");
        helper.assertTrue(Math.abs(warden.effectiveMaxHealth() - (float) HorizonWarden.HEALTH) < 0.5F && warden.getHealth() == warden.getMaxHealth(),
                "HP1200 相当で起動しない: " + warden.getHealth() + " / " + warden.effectiveMaxHealth());
        helper.assertTrue(warden.phase() == 1 && warden.getAttributeValue(Attributes.ARMOR) == HorizonWarden.ARMOR_PHASE_1,
                "フェーズ1の外装がない");
        helper.assertTrue(vault(helper).isSealed(), "戦っている間に保管庫が開いている");
        helper.assertTrue(console.activate(helper.getLevel(), null) == null, "2体目が起動した");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wardenPhasesFollowHealth(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        warden.setHealth(500);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(warden.phase() == 2, "HP 2/3 未満でフェーズ2にならない");
            helper.assertTrue(warden.getAttributeValue(Attributes.ARMOR) == 0, "フェーズ2で外装が剥がれない");
            warden.setHealth(200);
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(warden.phase() == 3, "HP 1/3 未満でフェーズ3にならない");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void defeatOpensVaultWithSeed(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        warden.kill();
        helper.runAtTickTime(2, () -> {
            RuinCacheBlockEntity vault = vault(helper);
            helper.assertTrue(!vault.isSealed(), "倒しても力場が消えない");
            vault.refillIfDue(helper.getLevel(), helper.getLevel().getGameTime());
            boolean seed = false;
            for (int i = 0; i < vault.getContainerSize(); i++) {
                seed |= vault.getItem(i).is(item("dormant_singularity_seed"));
            }
            helper.assertTrue(seed, "初回の報酬に休眠した特異点の種がない");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void wardenReturnsToSealWithoutChallengers(GameTestHelper helper) {
        SealConsoleBlockEntity console = arena(helper);
        HorizonWarden warden = console.activate(helper.getLevel(), null);
        warden.skipEmerging();
        warden.setHealth(300);
        helper.succeedWhen(() -> {
            helper.assertTrue(warden.isRemoved(), "挑戦者がいないのに守護機が残っている");
            helper.assertTrue(console.activeWarden(helper.getLevel()) == null, "コンソールが起動中のまま");
            helper.assertTrue(vault(helper).isSealed(), "削り逃げで保管庫が開いた");
        });
    }

    @GameTest(template = EMPTY)
    public static void regeneratedVaultIsSealedAgain(GameTestHelper helper) {
        arena(helper);
        RuinCacheBlockEntity vault = vault(helper);
        vault.setSealed(false);
        long t = 1000;
        vault.refillIfDue(helper.getLevel(), t);     // 初回（倒したあと）
        helper.assertTrue(!vault.isSealed(), "初回の中身で封鎖された");
        vault.clearContent();
        vault.refillIfDue(helper.getLevel(), t);
        vault.refillIfDue(helper.getLevel(), t + 14 * DAY);
        helper.assertTrue(vault.isSealed(), "14日で再生した中身が封鎖されない（再挑戦が要らない）");
        helper.succeed();
    }

    /** ダイヤの防具一式を着たゾンビ（普通の攻撃なら大きく軽減される相手）。 */
    private static Zombie armoredZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest(template = EMPTY)
    public static void singularityZoneDealsFullDamageThroughArmor(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        Zombie zombie = armoredZombie(helper, new BlockPos(6, 1, 1));
        warden.deploySingularity(zombie.position());
        helper.runAtTickTime(22, () -> {
            helper.assertTrue(zombie.getArmorValue() >= 20, "防具が着られていない: " + zombie.getArmorValue());
            // 10 tick ごとに WardenSingularity.DAMAGE。防具（防御20）があっても減らない → 2回で 20 → 10
            float twoHits = 20 - 2 * io.github.genichimaruo.singulo.ruin.WardenSingularity.DAMAGE;
            helper.assertTrue(zombie.getHealth() <= twoHits + 0.01F && zombie.getHealth() >= twoHits - 4.01F,
                    "潮汐ダメージが防具で軽減されている: HP " + zombie.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void gravityAttackPullsThenLifts(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        Zombie zombie = armoredZombie(helper, new BlockPos(7, 1, 7));
        warden.gravityAttack(helper.getLevel(), zombie);
        net.minecraft.world.phys.Vec3 pull = zombie.getDeltaMovement();
        net.minecraft.world.phys.Vec3 toWarden = warden.position().subtract(zombie.position());
        helper.assertTrue(pull.x * toWarden.x + pull.z * toWarden.z > 0, "1回目で守護機の方へ引き寄せられない");
        warden.gravityAttack(helper.getLevel(), zombie);
        helper.assertTrue(zombie.getDeltaMovement().y >= 1.39, "2回目で浮かされない: " + zombie.getDeltaMovement().y);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wardenShootsBoltsAtDistance(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        Zombie near = armoredZombie(helper, CONSOLE.east());
        helper.assertTrue(!warden.shootBolt(near), "近すぎる相手に光弾を撃った");
        Zombie far = armoredZombie(helper, new BlockPos(0, 1, 7));
        helper.assertTrue(warden.shootBolt(far), "離れた相手に光弾を撃たない");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(HorizonBolt.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(CONSOLE)).inflate(8)).isEmpty(), "光弾が出ていない");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tidalDamageIgnoresArmorAndWardenIsBoss(GameTestHelper helper) {
        helper.assertTrue(SinguloDamageTypes.tidal(helper.getLevel(), null).is(DamageTypeTags.BYPASSES_ARMOR),
                "潮汐ダメージが防具を無視しない");
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        helper.assertTrue(warden.getType().is(Tags.EntityTypes.BOSSES), "ボス扱いになっていない");
        helper.assertTrue(!GravityGauntletItem.canAffect(warden), "ガントレットで操れてしまう");
        helper.succeed();
    }

    /** フェーズ2以降: 撃った矢を跳ね返し、撃った相手に当てる（ウォーデンは無傷）。 */
    @GameTest(template = "huge", timeoutTicks = 200)
    public static void phaseTwoReflectsProjectilesAtShooter(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        warden.setNoAi(false);
        warden.setHealth(500);
        net.minecraft.world.entity.monster.Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(4, 1, 13));
        skeleton.setNoAi(true);
        float[] wardenHealth = new float[1];
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(warden.phase() == 2, "フェーズ2にならない");
            wardenHealth[0] = warden.getHealth();
            net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                    EntityType.ARROW, helper.getLevel());
            arrow.setOwner(skeleton);
            arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1, skeleton.getZ());
            net.minecraft.world.phys.Vec3 d = warden.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            arrow.shoot(d.x, d.y, d.z, 1.6F, 0);
            helper.getLevel().addFreshEntity(arrow);
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(warden.getHealth() >= wardenHealth[0], "矢がウォーデンに当たった");
            helper.assertTrue(skeleton.getHealth() < skeleton.getMaxHealth(), "跳ね返した矢が撃った相手に当たらない");
            helper.succeed();
        });
    }

    /** 重力の手: 持ち上げたあと強く叩き落とし、着地で大きな落下ダメージ（つかんでいる間のダメージとは別に8以上）。 */
    @GameTest(template = "huge", timeoutTicks = 200)
    public static void gravityGripSlamsWithFallDamage(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 1, 9));
        warden.startGrip(zombie);
        float held = 2 * HorizonWarden.GRIP_DAMAGE;
        helper.runAtTickTime(HorizonWarden.GRIP_WARN + HorizonWarden.GRIP_HOLD + 30, () -> {
            float lost = 20 - (zombie.isAlive() ? zombie.getHealth() : 0);
            helper.assertTrue(lost >= held + 8, "叩き落としの落下ダメージが小さい: 減った体力 " + lost);
            zombie.discard();
            helper.succeed();
        });
    }

    /** 倒されると、演出のあいだは膝をついて残り、終わると消える。 */
    @GameTest(template = EMPTY, timeoutTicks = HorizonWarden.DEATH_TICKS + 30)
    public static void wardenDeathPlaysOut(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        warden.kill();
        helper.runAtTickTime(HorizonWarden.DEATH_TICKS / 2, () -> helper.assertFalse(warden.isRemoved(), "演出の途中で消えた"));
        helper.runAtTickTime(HorizonWarden.DEATH_TICKS + 10, () -> {
            helper.assertTrue(warden.isRemoved(), "演出が終わっても消えない");
            helper.succeed();
        });
    }

    /** フェーズ2からは、ビームが壁を抜ける（手加減なし）。 */
    @GameTest(template = EMPTY)
    public static void wardenPiercesFromPhaseTwo(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
        warden.skipEmerging();
        helper.assertFalse(warden.piercing(), "フェーズ1から貫通している");
        warden.setHealth(warden.getMaxHealth() * 0.5F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(warden.phase() == 2 && warden.piercing(), "フェーズ2で貫通しない: " + warden.phase());
            warden.discard();
            helper.succeed();
        });
    }
}
