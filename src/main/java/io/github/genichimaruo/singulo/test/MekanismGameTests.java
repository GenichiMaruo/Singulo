package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.ChemicalSamples;
import io.github.genichimaruo.singulo.android.work.Chemicals;
import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Mekanism との連携（化学物質）の確認。ふだんの GameTest（名前空間 singulo）とは分けてあり、Mekanism を入れて
 * {@code ./gradlew runGameTestServer -PwithMekanism -PtestNamespace=singulo_mekanism} で走らせる。
 * Mekanism はプレイヤーに独自の通信を送るので、ここでは偽のプレイヤーを作らない（作ると、テストのサーバーが止まる）。
 */
@GameTestHolder("singulo_mekanism")
@PrefixGameTestTemplate(false)
public final class MekanismGameTests {
    private static final String EMPTY = "empty";

    private MekanismGameTests() {}

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    /**
     * 作業指令台の隣のケミカルタンクの水素を素材置き場の在庫に数え、ガスタンクアップグレードを積んだアンドロイドのタンクに入れて出せる。
     * ガス・液体アップグレードなら、液体も化学物質も 16,000 mB ずつ。
     */
    @GameTest(template = EMPTY)
    public static void chemicalTanks(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.assertTrue(Chemicals.present(), "Mekanism が入っていない（-PwithMekanism で走らせる）");
        BlockPos consolePos = new BlockPos(1, 1, 1), tankPos = consolePos.above();
        helper.setBlock(consolePos, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        var tankBlock = BuiltInRegistries.BLOCK.get(new ResourceLocation("mekanism:basic_chemical_tank"));
        helper.setBlock(tankPos, tankBlock);
        // Mekanism の装置は、面ごとの設定の既定値をアイテムが持っている（手で置いたときと同じにする）
        tankBlock.setPlacedBy(helper.getLevel(), helper.absolutePos(tankPos), helper.getLevel().getBlockState(helper.absolutePos(tankPos)), null, new ItemStack(tankBlock));
        // Mekanism の装置は、置いた次の tick から面ごとの入れ口ができる
        helper.runAfterDelay(5, () -> {
            var store = Chemicals.store(helper.getLevel(), helper.absolutePos(tankPos), net.minecraft.core.Direction.UP);
            helper.assertTrue(store != null, "ケミカルタンクの化学物質の入れ口が見つからない");
            helper.assertTrue(Chemicals.exists("mekanism:hydrogen"), "水素が見つからない");
            helper.assertTrue(store.insert("mekanism:hydrogen", 5000, true) == 5000 && store.amount("mekanism:hydrogen") == 0,
                    "化学物質の挿入シミュレーションがタンクを変えた");
            long put = store.insert("mekanism:hydrogen", 5000, false);
            helper.assertTrue(put == 5000, "ケミカルタンクに水素が入らない: " + put);
            WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, consolePos);
            ItemStack hydrogen = ChemicalSamples.of("mekanism:hydrogen", 1);
            helper.assertTrue(ItemCount.get(console.stock(), hydrogen) == 5000, "素材置き場の在庫に水素が数えられない");
            // 指令台はタンクの下（入れるだけの面）にあるが、出せる面（前）から取り出せる
            var stores = io.github.genichimaruo.singulo.android.work.LiquidStores.at(helper.getLevel(), helper.absolutePos(consolePos));
            helper.assertTrue(stores.drain(hydrogen, 1000, true) == 1000 && stores.amount(hydrogen) == 5000,
                    "素材置き場の抽出シミュレーションが在庫を変えた");
            long drained = io.github.genichimaruo.singulo.android.work.LiquidStores.at(helper.getLevel(), helper.absolutePos(consolePos))
                    .drain(hydrogen, 1000, false);
            helper.assertTrue(drained == 1000 && ItemCount.get(console.stock(), hydrogen) == 4000, "素材置き場のケミカルタンクから取り出せない: " + drained);

            BladeAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
            a.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))));
            helper.getLevel().addFreshEntity(a);
            a.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_gas_tank_upgrade"));
            helper.assertTrue(a.hasChemTank() && !a.hasTank(), "ガスタンクアップグレードで化学物質のタンクにならない");
            helper.assertTrue(a.fillLiquid(hydrogen, 70000, false) == 64000 && a.liquidAmount(hydrogen) == 64000,
                    "化学物質のタンク2つに 64,000 mB 入らない: " + a.liquidAmount(hydrogen));
            helper.assertTrue(a.drainLiquid(hydrogen, 2000, false) == 2000 && a.liquidAmount(hydrogen) == 62000, "化学物質のタンクから出せない");
            var reloaded = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
            reloaded.load(a.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(reloaded.hasChemTank() && reloaded.liquidAmount(hydrogen) == 62000,
                    "再読み込みで化学物質の種類・量かタンク装備を失った");
            helper.assertTrue(reloaded.drainLiquid(hydrogen, 2000, true) == 2000 && reloaded.liquidAmount(hydrogen) == 62000,
                    "運搬タンクの抽出シミュレーションが内容を変えた");
            helper.assertTrue(stores.fill(hydrogen, reloaded.drainLiquid(hydrogen, 2000, false), false) == 2000
                            && stores.amount(hydrogen) == 6000 && reloaded.liquidAmount(hydrogen) == 60000,
                    "運搬タンクから素材置き場への返却で化学物質を失うか増殖した");
            reloaded.discard();
            a.drainLiquid(hydrogen, 62000, false);
            a.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_dual_tank_upgrade"));
            helper.assertTrue(a.chemCapacity() == 16000 && a.fluidCapacity() == 16000, "ガス・液体アップグレードで、各 16,000 mB にならない");
            a.discard();
            helper.succeed();
        });
    }
    /** All four 10.4 chemical families keep separate IDs and conserve their amounts. */
    @GameTest(template = EMPTY, batch = "chemical_families")
    public static void allChemicalFamiliesUseTypedKeys(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var tankItem = BuiltInRegistries.ITEM.get(new ResourceLocation("mekanism", "basic_chemical_tank"));
        for (String id : new String[]{"mekanism:hydrogen", "infusion|mekanism:carbon", "pigment|mekanism:red", "slurry|mekanism:dirty_iron"}) {
            helper.assertTrue(Chemicals.exists(id), "Missing chemical family: " + id);
            var tank = new ItemStack(tankItem);
            var store = Chemicals.itemStore(tank);
            helper.assertTrue(store != null, "Chemical tank item has no capabilities");
            long before = store.amount(id);
            long simulated = store.insert(id, 1234, true);
            helper.assertTrue(simulated > 0 && simulated <= 1234 && before == 0 && store.amount(id) == before, "Simulation for " + id + ": accepted=" + simulated + ", before=" + before + ", after=" + store.amount(id) + ", contents=" + store.contents());
            long inserted = store.insert(id, 1234, false);
            helper.assertTrue(inserted == simulated && store.amount(id) == inserted, "Insertion disagrees with simulation: " + id);
            // The Basic tier limits each item-capability transfer to 1,000 mB; honor partial transfers.
            if (inserted < 1234) inserted += store.insert(id, 1234 - inserted, false);
            helper.assertTrue(inserted == 1234 && store.amount(id) == 1234, "Cannot finish partial insertion: " + id);
            helper.assertTrue(store.contents().getOrDefault(id, 0L) == 1234, "Chemical family was conflated: " + store.contents());
            var saved = ItemStack.of(tank.save(new net.minecraft.nbt.CompoundTag()));
            var restored = Chemicals.itemStore(saved);
            helper.assertTrue(restored != null && restored.amount(id) == 1234, "Item reload lost " + id);
            long canExtract = restored.extract(id, 1234, true);
            helper.assertTrue(canExtract > 0 && canExtract <= 1234 && restored.amount(id) == 1234, "Extraction simulation changed " + id);
            long extracted = restored.extract(id, 1234, false);
            helper.assertTrue(extracted == canExtract, "Extraction disagrees with simulation: " + id);
            if (extracted < 1234) extracted += restored.extract(id, 1234 - extracted, false);
            helper.assertTrue(extracted == 1234 && restored.contents().isEmpty(), "Extraction lost or duplicated " + id);
        }
        helper.succeed();
    }

}
