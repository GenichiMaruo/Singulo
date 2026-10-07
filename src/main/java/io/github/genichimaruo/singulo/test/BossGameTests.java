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

    @GameTest(template = EMPTY)
    public static void consoleWakesWardenWhileVaultSealed(GameTestHelper helper) {
        SealConsoleBlockEntity console = arena(helper);
        HorizonWarden warden = console.activate(helper.getLevel(), null);
        helper.assertTrue(warden != null, "守護機が起動しない");
        helper.assertTrue(warden.getMaxHealth() == 800 && warden.getHealth() == 800, "HP800 で起動しない: " + warden.getHealth());
        helper.assertTrue(warden.phase() == 1 && warden.getAttributeValue(Attributes.ARMOR) == HorizonWarden.ARMOR_PHASE_1,
                "フェーズ1の外装がない");
        helper.assertTrue(vault(helper).isSealed(), "戦っている間に保管庫が開いている");
        helper.assertTrue(console.activate(helper.getLevel(), null) == null, "2体目が起動した");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wardenPhasesFollowHealth(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
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
        Zombie zombie = armoredZombie(helper, new BlockPos(6, 1, 1));
        warden.deploySingularity(zombie.position());
        helper.runAtTickTime(22, () -> {
            helper.assertTrue(zombie.getArmorValue() >= 20, "防具が着られていない: " + zombie.getArmorValue());
            // 10 tick ごとに4ダメージ。防具（防御20）があっても減らない → 2回で 20 → 12
            helper.assertTrue(zombie.getHealth() <= 12.01F && zombie.getHealth() >= 7.99F,
                    "潮汐ダメージが防具で軽減されている: HP " + zombie.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void gravityAttackPullsThenLifts(GameTestHelper helper) {
        HorizonWarden warden = arena(helper).activate(helper.getLevel(), null);
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
        helper.assertTrue(warden.getType().is(Tags.EntityTypes.BOSSES), "ボス扱いになっていない");
        helper.assertTrue(!GravityGauntletItem.canAffect(warden), "ガントレットで操れてしまう");
        helper.succeed();
    }
}
