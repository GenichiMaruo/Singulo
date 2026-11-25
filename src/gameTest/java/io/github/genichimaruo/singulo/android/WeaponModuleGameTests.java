package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 武装モジュール: 枠と装備、出力増幅器の倍率と剣のエンチャント、特化モジュールの効き目。掌底放電（7）を物差しにする。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponModuleGameTests {
    private static final float PALM = SparkAndroid.PALM_DAMAGE;
    private static final float EPS = 0.01F;

    private WeaponModuleGameTests() {}

    /** 南を向いた、動かない護衛の放電型（前の1ブロックに的を置く）と、武装モジュール。 */
    private static SparkAndroid guard(GameTestHelper helper, ItemStack module) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 7, 0, 7)) helper.setBlock(p, Blocks.STONE);
        SparkAndroid s = SinguloEntities.SPARK_ANDROID.get().create(helper.getLevel());
        s.setNoAi(true);
        s.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 2))));
        s.yBodyRot = 0;
        helper.getLevel().addFreshEntity(s);
        helper.setBlock(new BlockPos(6, 0, 6), SinguloBlocks.ANDROID_STATION.get());
        s.makeGuard(UUID.randomUUID(), helper.absolutePos(new BlockPos(6, 0, 6)), RuinAndroid.MODE_FOLLOW, 0);
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, module);
        return s;
    }

    private static Zombie dummy(GameTestHelper helper, double armor) {
        Zombie z = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 3));
        z.setNoAi(true);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        z.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        return z;
    }

    private static float palm(GameTestHelper helper, SparkAndroid s, Zombie z) {
        float before = z.getHealth();
        helper.assertTrue(s.palm(helper.getLevel()) == 1, "掌底放電が的に当たらない");
        return before - z.getHealth();
    }

    /** 枠に積むと利き手の装備に写り（ドロップ増加はふつうの仕組みで効く）、外すと消える。 */
    @GameTest(template = "empty")
    public static void moduleSlotMirrorsMainHand(GameTestHelper helper) {
        ItemStack amp = new ItemStack(SinguloItems.ANDROID_WEAPON_MODULE.get());
        var looting = Enchantments.MOB_LOOTING;
        var book = net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                new net.minecraft.world.item.enchantment.EnchantmentInstance(looting, 3));
        helper.assertTrue(amp.canApplyAtEnchantingTable(looting) && amp.isBookEnchantable(book),
                "Legacy amplifiers must accept sword enchantments at the table and from a book");
        helper.assertTrue(!new ItemStack(SinguloItems.DRAIN_MODULE.get()).canApplyAtEnchantingTable(looting),
                "Specialized modules must not accept amplifier enchantments");
        var table = EnchantmentHelper.selectEnchantment(net.minecraft.util.RandomSource.create(42), amp, 30, false);
        helper.assertTrue(!table.isEmpty() && table.stream().allMatch(e -> e.enchantment.category
                        == net.minecraft.world.item.enchantment.EnchantmentCategory.WEAPON),
                "Legacy enchanting table must offer weapon enchantments for the amplifier");
        amp.enchant(looting, 3);
        SparkAndroid s = guard(helper, amp);
        helper.assertTrue(s.getItemBySlot(EquipmentSlot.MAINHAND).is(amp.getItem()), "武装モジュールが利き手の装備に写らない");
        helper.assertTrue(s.weaponModule() == AndroidWeaponModuleItem.Kind.AMP_1, "積んだモジュールの種類が分からない");
        helper.assertTrue(EnchantmentHelper.getEnchantmentLevel(looting, s) == 3, "ドロップ増加がアンドロイドに効かない");
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, ItemStack.EMPTY);
        helper.assertTrue(s.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && s.weaponModule() == null, "外したモジュールが残る");
        helper.succeed();
    }

    /** 出力増幅器: Mk2 は 1.6 倍。ダメージ増加のエンチャントの分も足される。 */
    @GameTest(template = "empty")
    public static void amplifierMultipliesAndAddsSharpness(GameTestHelper helper) {
        SparkAndroid s = guard(helper, new ItemStack(SinguloItems.ANDROID_WEAPON_MODULE_2.get()));
        Zombie z = dummy(helper, 0);
        float plain = palm(helper, s, z);
        helper.assertTrue(Math.abs(plain - PALM * 1.6F) < EPS, "Mk2 の倍率が 1.6 でない: " + plain);
        ItemStack sharp = new ItemStack(SinguloItems.ANDROID_WEAPON_MODULE_2.get());
        sharp.enchant(Enchantments.SHARPNESS, 5);
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, sharp);
        float sharpened = palm(helper, s, z);
        helper.assertTrue(sharpened > plain + 2.9F, "ダメージ増加のエンチャントが効かない: " + sharpened);
        helper.succeed();
    }

    /** 重撃: 大技は 2.2 倍、ほかの技はそのまま。 */
    @GameTest(template = "empty")
    public static void heavyFavoursFinishingMoves(GameTestHelper helper) {
        SparkAndroid s = guard(helper, new ItemStack(SinguloItems.HEAVY_STRIKE_MODULE.get()));
        Zombie z = dummy(helper, 0);
        helper.assertTrue(Math.abs(palm(helper, s, z) - PALM) < EPS, "ふつうの技の威力が変わった");
        float before = z.getHealth();
        s.setState(SparkAndroid.STATE_TENRAI_STRIKE);
        s.strike(helper.getLevel());
        helper.assertTrue(Math.abs(before - z.getHealth() - SparkAndroid.TENRAI_DAMAGE * 2.2F) < EPS, "大技が 2.2 倍でない: " + (before - z.getHealth()));
        helper.succeed();
    }

    /** 吸収・凍結・貫通。 */
    @GameTest(template = "empty")
    public static void specialisedModulesApplyTheirEffects(GameTestHelper helper) {
        SparkAndroid s = guard(helper, new ItemStack(SinguloItems.DRAIN_MODULE.get()));
        Zombie z = dummy(helper, 0);
        s.setHealth(10);
        float dealt = palm(helper, s, z);
        helper.assertTrue(Math.abs(s.getHealth() - (10 + dealt * 0.1F)) < EPS, "与えたダメージの1割が戻らない: " + s.getHealth());
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, new ItemStack(SinguloItems.FROST_MODULE.get()));
        palm(helper, s, z);
        helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "凍結で鈍らない");
        z.discard();
        Zombie armored = dummy(helper, 20);
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, ItemStack.EMPTY);
        float blocked = palm(helper, s, armored);
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, new ItemStack(SinguloItems.PIERCING_MODULE.get()));
        float pierced = palm(helper, s, armored);
        helper.assertTrue(pierced > blocked + 0.5F, "貫通で防具の守りが減らない: " + blocked + " → " + pierced);
        helper.succeed();
    }

    /** 対番人はボスにだけ 1.8 倍。連撃は 3 tick に 1 回、待ち時間を余分に減らす。 */
    @GameTest(template = "empty")
    public static void wardenAndRapidModifiers(GameTestHelper helper) {
        var boss = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        var husk = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(AndroidWeaponModules.modifier(AndroidWeaponModuleItem.Kind.WARDEN, RuinAndroid.STATE_IDLE, boss) == 1.8F,
                "対番人がボスに 1.8 倍でない");
        helper.assertTrue(AndroidWeaponModules.modifier(AndroidWeaponModuleItem.Kind.WARDEN, RuinAndroid.STATE_IDLE, husk) == 1,
                "対番人がボス以外にも効く");
        SparkAndroid s = guard(helper, new ItemStack(SinguloItems.RAPID_STRIKE_MODULE.get()));
        int extra = 0;
        for (int i = 0; i < 30; i++) {
            s.tickCount = i;
            if (s.rapidCooldown()) extra++;
        }
        helper.assertTrue(extra == 10, "連撃の余分な待ち時間の減りが 3 tick に 1 回でない: " + extra);
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, ItemStack.EMPTY);
        helper.assertFalse(s.rapidCooldown(), "連撃を外しても待ち時間が速く減る");
        helper.succeed();
    }

    /**
     * 対番人モジュールを積んだ護衛だけがボスと戦う: ボスを狙い、技が当たり、ボスの攻撃も受ける。積んでいない護衛はボスと互いに敵対しない。
     * ボスはどちらの護衛へも狙いを移さない。
     */
    @GameTest(template = "empty")
    public static void wardenModuleLetsOnlyItsGuardFightBosses(GameTestHelper helper) {
        SparkAndroid s = guard(helper, ItemStack.EMPTY);
        var boss = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        boss.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 5))));
        helper.assertFalse(s.bossHunter() || s.hostile(boss) || RuinAndroid.canHit(true, s, boss), "積んでいない護衛がボスと戦う");
        float hp = s.getHealth();
        s.hurt(s.damageSources().mobAttack(boss), 5);
        helper.assertTrue(s.getHealth() == hp, "積んでいない護衛がボスの攻撃を受けた");
        s.inventory().setItem(RuinAndroid.SLOT_WEAPON, new ItemStack(SinguloItems.WARDEN_BREAKER_MODULE.get()));
        helper.assertTrue(s.bossHunter() && s.hostile(boss) && RuinAndroid.canHit(true, s, boss), "対番人モジュールを積んでもボスと戦わない");
        s.invulnerableTime = 0;
        s.hurt(s.damageSources().mobAttack(boss), 5);
        helper.assertTrue(s.getHealth() < hp, "対番人モジュールを積んだ護衛がボスの攻撃を受けない");
        boss.setTarget(s);
        helper.assertTrue(boss.getTarget() != s, "ボスが護衛のアンドロイドへ狙いを移した");
        helper.succeed();
    }

    /** 組立ステーションの武装モジュールの枠: 印刷したアンドロイドのうち、まだ積んでいないものにだけ積む。 */
    @GameTest(template = "empty")
    public static void stationFitsWeaponModuleOnPrint(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = (AndroidStationBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
        ItemStack amp = new ItemStack(SinguloItems.ANDROID_WEAPON_MODULE.get());
        helper.assertTrue(station.items().isItemValid(AndroidStationBlockEntity.SLOT_WEAPON, amp), "ステーションの枠に武装モジュールが入らない");
        helper.assertFalse(station.items().isItemValid(AndroidStationBlockEntity.SLOT_WEAPON, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD)),
                "ステーションの武装モジュールの枠に別の物が入る");
        station.items().insertItem(AndroidStationBlockEntity.SLOT_WEAPON, amp, false);
        RuinAndroid armed = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        armed.inventory().setItem(RuinAndroid.SLOT_WEAPON, new ItemStack(SinguloItems.FROST_MODULE.get()));
        helper.assertFalse(station.equipWeapon(armed), "もう積んでいるアンドロイドに積み替えた");
        RuinAndroid bare = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        helper.assertTrue(station.equipWeapon(bare) && bare.weaponModule() == AndroidWeaponModuleItem.Kind.AMP_1
                && station.items().getStackInSlot(AndroidStationBlockEntity.SLOT_WEAPON).isEmpty(), "印刷したアンドロイドに積まない");
        helper.succeed();
    }

    /** 携帯型印刷機: 組ごとの武装モジュールの枠から積む。Mk1 → Mk2 で、Mk1 の枠のモジュールは Mk2 の1組目の枠へ移る。 */
    @GameTest(template = "empty")
    public static void portablePrinterFitsWeaponModulePerUnit(GameTestHelper helper) {
        ItemStack mk2 = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER_2.get());
        var items = new java.util.ArrayList<>(PortableAndroidPrinterItem.contents(mk2));
        helper.assertTrue(items.size() == 15, "Mk2 の中身の枠が 3組×5 でない: " + items.size());
        items.set(PortableAndroidPrinterItem.weaponSlot(3, 1), new ItemStack(SinguloItems.PIERCING_MODULE.get()));
        PortableAndroidPrinterItem.setContents(mk2, items);
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        helper.assertFalse(PortableAndroidPrinterItem.equipWeapon(mk2, a, 0), "別の組のモジュールを積んだ");
        helper.assertTrue(PortableAndroidPrinterItem.equipWeapon(mk2, a, 1) && a.weaponModule() == AndroidWeaponModuleItem.Kind.PIERCE
                && PortableAndroidPrinterItem.contents(mk2).get(PortableAndroidPrinterItem.weaponSlot(3, 1)).isEmpty(),
                "組の武装モジュールを積まない");
        // Mk1 の中身（メモリと武装モジュール）を Mk2 へ写して、武装モジュールを移す
        ItemStack mk1 = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        var one = new java.util.ArrayList<>(PortableAndroidPrinterItem.contents(mk1));
        one.set(PortableAndroidPrinterItem.slot(0, PortableAndroidPrinterItem.SLOT_MEMORY),
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("android_memory_blade"))));
        one.set(PortableAndroidPrinterItem.weaponSlot(1, 0), new ItemStack(SinguloItems.DRAIN_MODULE.get()));
        PortableAndroidPrinterItem.setContents(mk1, one);
        ItemStack upgraded = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER_2.get());
        if (mk1.hasTag()) upgraded.setTag(mk1.getTag().copy());
        PortableAndroidPrinterItem.moveWeaponsAfterUpgrade(upgraded, 1);
        var moved = PortableAndroidPrinterItem.contents(upgraded);
        helper.assertTrue(moved.get(PortableAndroidPrinterItem.slot(1, PortableAndroidPrinterItem.SLOT_MEMORY)).isEmpty()
                && moved.get(PortableAndroidPrinterItem.weaponSlot(3, 0)).is(SinguloItems.DRAIN_MODULE.get())
                && !moved.get(PortableAndroidPrinterItem.slot(0, PortableAndroidPrinterItem.SLOT_MEMORY)).isEmpty(),
                "Mk1 の武装モジュールが Mk2 の1組目の枠へ移らない");
        helper.succeed();
    }
}
