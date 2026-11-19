package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.Chemicals;
import io.github.genichimaruo.singulo.android.work.FluidSamples;
import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkJob;
import io.github.genichimaruo.singulo.api.android.AndroidChemicalStorage;
import io.github.genichimaruo.singulo.api.android.AndroidRecipe;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

    @GameTest(template = EMPTY)
    public static void pipeLeavesPassiveMekanismTankUntouched(GameTestHelper h) {
        var tankPos = new BlockPos(3, 3, 3);
        var tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank"));
        h.setBlock(tankPos, tank);
        h.getBlockEntity(tankPos).applyComponentsFromItemStack(new ItemStack(tank));
        var outlet = tankPos.below();
        h.setBlock(outlet, SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(outlet.east(), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.runAfterDelay(5, () -> {
            var top = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(tankPos), Direction.UP);
            h.assertTrue(top != null && top.fill(new FluidStack(Fluids.LAVA, 8000), FluidAction.EXECUTE) == 8000,
                    "Mekanism tank did not accept the test lava");
            h.runAfterDelay(20, () -> {
                h.assertTrue(top.getFluidInTank(0).getAmount() == 8000, "Tank was already ejecting with a receiver directly below");
                h.setBlock(outlet, SinguloBlocks.FLUID_PIPE.get());
                var receiver = (io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity) h.getBlockEntity(outlet.east());
                h.runAfterDelay(20, () -> {
                    h.assertTrue(top.getFluidInTank(0).getAmount() == 8000 && receiver.sendTank().isEmpty(),
                            "A pipe extracted lava from a passive Mekanism tank");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY)
    public static void poweredPumpDrainsPassiveMekanismTank(GameTestHelper h) {
        var tankAt = new BlockPos(3, 3, 3);
        var tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank"));
        h.setBlock(tankAt, tank);
        h.getBlockEntity(tankAt).applyComponentsFromItemStack(new ItemStack(tank));
        h.runAfterDelay(5, () -> {
            var absolute = h.absolutePos(tankAt);
            var above = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absolute, Direction.UP);
            h.assertTrue(above != null && above.fill(new FluidStack(Fluids.LAVA, 8000), FluidAction.EXECUTE) == 8000, "Tank did not accept lava");
            h.setBlock(tankAt.below(), SinguloBlocks.FLUID_PUMP.get().defaultBlockState()
                    .setValue(io.github.genichimaruo.singulo.pipe.FluidPumpBlock.INTAKE, Direction.UP));
            var pump = (io.github.genichimaruo.singulo.pipe.FluidPumpBlockEntity) h.getBlockEntity(tankAt.below());
            h.setBlock(tankAt.below().east(), SinguloBlocks.FLUID_DRONE_PORT.get());
            var target = (io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity) h.getBlockEntity(tankAt.below().east());
            h.assertTrue(pump.energy().receiveEnergy(125, false) == 125, "Wrong pump energy debit");
            h.succeedWhen(() -> h.assertTrue(target.sendTank().getFluidAmount() == 5251 && above.getFluidInTank(0).getAmount() == 2749
                    && pump.energy().getEnergyStored() == 0, "Passive Mekanism tank did not deliver the powered volume"));
        });
    }

    @GameTest(template = EMPTY)
    public static void externalFluidTankSuppliesAndroid(GameTestHelper h) {
        BlockPos tankPos = new BlockPos(2, 2, 2);
        BlockPos absolute = h.absolutePos(tankPos);
        var tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank"));
        h.assertTrue(tank != Blocks.AIR, "Mekanism fluid tank missing");
        h.setBlock(tankPos, tank);
        h.getLevel().getBlockEntity(absolute).applyComponentsFromItemStack(new ItemStack(tank));
        h.runAfterDelay(5, () -> {
            var lava = new FluidStack(Fluids.LAVA, 2000);
            int inserted = 0;
            for (Direction face : Direction.values()) {
                var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absolute, face);
                if (handler != null) inserted += handler.fill(lava.copyWithAmount(2000 - inserted), FluidAction.EXECUTE);
                if (inserted == 2000) break;
            }
            h.assertTrue(inserted == 2000, "Mekanism did not accept the initial lava");
            for (Direction face : Direction.values()) {
                var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absolute, face);
                if (handler == null || handler.drain(lava, FluidAction.SIMULATE).getAmount() != 2000) continue;
                BlockPos consolePos = absolute.relative(face);
                h.getLevel().setBlockAndUpdate(consolePos, SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState());
                var console = (WorkConsoleBlockEntity) h.getLevel().getBlockEntity(consolePos);
                var sample = FluidSamples.of(lava.copyWithAmount(1));
                h.assertTrue(ItemCount.get(console.stock(), sample) == 2000, "Console missed adjacent Mekanism fluid storage");
                var android = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
                android.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade"));
                var pickup = new WorkConsoleBlockEntity.Pickup(consolePos, java.util.List.of(new ItemCount(sample, 1000)));
                h.assertTrue(console.fetchAt(android, WorkJob.simple(WorkJob.Type.CRAFT, 1), pickup), "Fluid pickup failed");
                h.assertTrue(android.liquidAmount(sample) == 1000 && ItemCount.get(console.stock(), sample) == 1000
                                && handler.drain(lava, FluidAction.SIMULATE).getAmount() == 1000,
                        "1000 mB pickup did not conserve lava");
                h.setBlock(tankPos, Blocks.AIR);
                h.assertTrue(ItemCount.get(console.stock(), sample) == 0, "Removed tank is still visible");
                h.succeed();
                return;
            }
            h.fail("No drainable face on Mekanism fluid tank");
        });
    }

    /**
     * 作業指令台の隣のケミカルタンクの水素を素材置き場の在庫に数え、ガスタンクアップグレードを積んだアンドロイドのタンクに入れて出せる。
     * ガス・液体アップグレードなら、液体も化学物質も 16,000 mB ずつ。
     */
    @GameTest(template = EMPTY)
    public static void chemicalTanks(GameTestHelper helper) {
        helper.assertTrue(Chemicals.present(), "Mekanism が入っていない（-PwithMekanism で走らせる）");
        BlockPos consolePos = new BlockPos(1, 1, 1), tankPos = consolePos.above();
        helper.setBlock(consolePos, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        var tankBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_chemical_tank"));
        helper.setBlock(tankPos, tankBlock);
        // Mekanism の装置は、面ごとの設定の既定値をアイテムが持っている（手で置いたときと同じにする）
        helper.getLevel().getBlockEntity(helper.absolutePos(tankPos)).applyComponentsFromItemStack(new ItemStack(tankBlock));
        // Mekanism の装置は、置いた次の tick から面ごとの入れ口ができる
        helper.runAfterDelay(5, () -> {
            var store = Chemicals.store(helper.getLevel(), helper.absolutePos(tankPos), net.minecraft.core.Direction.UP);
            helper.assertTrue(store != null, "ケミカルタンクの化学物質の入れ口が見つからない");
            helper.assertTrue(Chemicals.exists("mekanism:hydrogen"), "水素が見つからない");
            helper.assertTrue(store.insert("mekanism:hydrogen", 5000, true) == 5000 && store.amount("mekanism:hydrogen") == 0,
                    "化学物質の挿入シミュレーションがタンクを変えた");
            long put = store.insert("mekanism:hydrogen", 5000, false);
            helper.assertTrue(put == 5000, "ケミカルタンクに水素が入らない: " + put);
            WorkConsoleBlockEntity console = helper.getBlockEntity(consolePos);
            ItemStack hydrogen = AndroidRecipe.chemicalSample("mekanism:hydrogen", 1);
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
            // A second console can only reach this same tank through the addon's chemical endpoint.
            BlockPos remote = new BlockPos(4, 1, 3), port = remote.north(2);
            helper.setBlock(remote, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
            WorkConsoleBlockEntity other = helper.getBlockEntity(remote);
            other.setOwner(java.util.UUID.randomUUID());
            String id = AndroidRecipe.chemicalId(hydrogen);
            var output = java.util.Arrays.stream(Direction.values()).map(face -> Chemicals.store(helper.getLevel(),
                    helper.absolutePos(tankPos), face)).filter(s -> s != null && s.extract(id, 1, true) == 1).findFirst().orElseThrow();
            helper.setBlock(port, Blocks.CHEST);
            class ChemicalPort extends net.minecraft.world.level.block.entity.ChestBlockEntity implements AndroidWorkEndpoint {
                final java.util.UUID identity = java.util.UUID.randomUUID();
                ChemicalPort() { super(helper.absolutePos(port), Blocks.CHEST.defaultBlockState()); }
                public java.util.UUID endpointId() { return identity; }
                public java.util.UUID endpointOwner() { return other.owner(); }
                public net.neoforged.neoforge.items.IItemHandler supplyItems() { return null; }
                public net.neoforged.neoforge.items.IItemHandler deliveryItems() { return null; }
                public AndroidChemicalStorage supplyChemicals() { return output; }
                public AndroidChemicalStorage deliveryChemicals() { return store; }
            }
            var endpoint = new ChemicalPort();
            helper.getLevel().setBlockEntity(endpoint);
            helper.assertTrue(AndroidWorkEndpoints.link(helper.getLevel(), other.getBlockPos(), other.owner(), endpoint.getBlockPos())
                    == AndroidWorkEndpoints.Result.LINKED && ItemCount.get(other.stock(), hydrogen) == 6000,
                    "A chemical-only endpoint could not expose the Mekanism tank");
            var pickup = new WorkConsoleBlockEntity.Pickup(endpoint.getBlockPos(), java.util.List.of(new ItemCount(hydrogen, 1000)));
            helper.assertTrue(other.fetchAt(a, WorkJob.simple(WorkJob.Type.CRAFT, 1), pickup) && a.liquidAmount(hydrogen) == 1000
                    && stores.amount(hydrogen) == 5000, "Chemical endpoint pickup did not conserve hydrogen");
            a.discard();
            helper.succeed();
        });
    }
}
