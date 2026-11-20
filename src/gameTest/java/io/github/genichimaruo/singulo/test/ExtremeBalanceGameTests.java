package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.FluidSamples;
import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.FluidCapsuleItem;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.GeneratorSlotsMenu;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.MuonFusionReactorBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipePatches;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class ExtremeBalanceGameTests {
    @GameTest(template = "empty", batch = "extreme_balance")
    public static void externalRecipeUsesItsOwnStageAndKeepsJsonValues(GameTestHelper h) {
        var time = ServerConfig.STAGE2_TIME_MULTIPLIER.get();
        var power = ServerConfig.STAGE2_ENERGY_MULTIPLIER.get();
        var id = ResourceLocation.fromNamespaceAndPath("example_pack", "steel");
        var raw = new MachineRecipe("kiln", 2, 100, 120, List.of(), List.of(), Optional.empty(), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), List.of(), 0, 0, ItemStack.EMPTY);
        try {
            ServerConfig.STAGE2_TIME_MULTIPLIER.set(0.25);
            ServerConfig.STAGE2_ENERGY_MULTIPLIER.set(3.5);
            RecipeTweaks.invalidate();
            var r = RecipeTweaks.adjust(id, raw);
            h.assertTrue(r.time() == 25 && r.energy() == 420 && raw.time() == 100, "Stage settings skipped or changed source recipe");
            ServerConfig.STAGE2_TIME_MULTIPLIER.set(1e9);
            ServerConfig.STAGE2_ENERGY_MULTIPLIER.set(0.0);
            RecipeTweaks.invalidate();
            r = RecipeTweaks.adjust(id, raw);
            h.assertTrue(r.time() == Integer.MAX_VALUE && r.energy() == 0, "Time overflowed or free recipe acquired an energy cost");
            h.succeed();
        } finally {
            ServerConfig.STAGE2_TIME_MULTIPLIER.set(time);
            ServerConfig.STAGE2_ENERGY_MULTIPLIER.set(power);
            RecipeTweaks.invalidate();
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void transportLimitsRemainLongAndIndependent(GameTestHelper h) {
        long rate = ServerConfig.COPPER_CABLE_RATE.get(), pipe = ServerConfig.VACUUM_PIPE_RATE.get();
        double loss = ServerConfig.COPPER_CABLE_LOSS.get();
        try {
            ServerConfig.COPPER_CABLE_RATE.set(7_000_000_000L);
            ServerConfig.COPPER_CABLE_LOSS.set(0.125);
            ServerConfig.VACUUM_PIPE_RATE.set(Long.MAX_VALUE);
            h.assertTrue(SinguloBlocks.COPPER_WIRE.get().capacity() == 7_000_000_000L
                    && SinguloBlocks.COPPER_WIRE.get().lossPerBlock() == 0.125
                    && SinguloBlocks.VACUUM_JACKETED_PIPE.get().capacity() == Long.MAX_VALUE
                    && SinguloBlocks.FLUID_PIPE.get().capacity() == ServerConfig.COPPER_PIPE_RATE.get(), "Conduit limits narrowed or crossed tiers");
            h.succeed();
        } finally {
            ServerConfig.COPPER_CABLE_RATE.set(rate);
            ServerConfig.COPPER_CABLE_LOSS.set(loss);
            ServerConfig.VACUUM_PIPE_RATE.set(pipe);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void androidOversizedTanksAndChargeCanBeUsedAfterDowngrade(GameTestHelper h) {
        int capacity = ServerConfig.ANDROID_TANK_CAPACITY.get(), battery = ServerConfig.ANDROID_BATTERY_CAPACITY.get();
        try {
            ServerConfig.ANDROID_TANK_CAPACITY.set(Integer.MAX_VALUE);
            ServerConfig.ANDROID_BATTERY_CAPACITY.set(Integer.MAX_VALUE);
            var android = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
            android.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade", 1));
            var sample = FluidSamples.of(new FluidStack(Fluids.WATER, 1));
            long both = 2L * Integer.MAX_VALUE;
            h.assertTrue(android.maxCharge() == Integer.MAX_VALUE && android.charge() == Integer.MAX_VALUE, "Battery overflow");
            h.assertTrue(android.fillLiquid(sample, both, true) == both && android.liquidAmount(sample) == 0
                    && android.fillLiquid(sample, both, false) == both && android.liquidAmount(sample) == both, "Two full tanks lost volume");
            var save = android.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            ServerConfig.ANDROID_TANK_CAPACITY.set(1);
            ServerConfig.ANDROID_BATTERY_CAPACITY.set(1);
            android.load(save);
            h.assertTrue(android.charge() == Integer.MAX_VALUE && android.maxCharge() == 1 && android.liquidSpace(sample) == 0
                    && android.drainLiquid(sample, both - 1, false) == both - 1 && android.liquidAmount(sample) == 1, "Downgrade lost stock or charge");
            h.succeed();
        } finally {
            ServerConfig.ANDROID_TANK_CAPACITY.set(capacity);
            ServerConfig.ANDROID_BATTERY_CAPACITY.set(battery);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void droneCapsuleSurvivesSmallerPortAndShippingCostCannotWrap(GameTestHelper h) {
        int cap = ServerConfig.FLUID_CAPSULE_CAPACITY.get(), base = ServerConfig.SHIPMENT_BASE_FE.get();
        int rate = ServerConfig.SHIPMENT_FE_PER_BLOCK.get(), battery = ServerConfig.CRATE_TIER1_CAPACITY.get();
        int slots = ServerConfig.CARGO_STACKS_PER_FLIGHT.get();
        try {
            ServerConfig.CRATE_TIER1_CAPACITY.set(Integer.MAX_VALUE);
            ServerConfig.SHIPMENT_BASE_FE.set(1);
            ServerConfig.SHIPMENT_FE_PER_BLOCK.set(Integer.MAX_VALUE);
            h.assertTrue(!DroneCrateItem.canReach(1, 1) && DroneCrateItem.cost(2) == Integer.MAX_VALUE, "Unpayable flight accepted");
            ServerConfig.SHIPMENT_FE_PER_BLOCK.set(0);
            h.assertTrue(DroneCrateItem.canReach(1, 10_000_000) && DroneCrateItem.cost(10_000_000) == 1, "Flat-rate flight blocked");
            ServerConfig.CARGO_STACKS_PER_FLIGHT.set(1);
            ServerConfig.SHIPMENT_BASE_FE.set(Integer.MAX_VALUE);
            var at = new BlockPos(1, 1, 1);
            h.setBlock(at, SinguloBlocks.DRONE_PORT.get());
            DronePortBlockEntity sender = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, at);
            sender.energy().restoreEnergy(Integer.MAX_VALUE);
            sender.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, item("packing_crate", 1));
            int cargo = DronePortBlockEntity.SLOT_CARGO;
            sender.items().setStackInSlot(cargo + 2, new ItemStack(Items.IRON_INGOT, 7));
            sender.items().setStackInSlot(cargo + 7, new ItemStack(Items.GOLD_INGOT, 11));
            sender.stageForTest(sender.getBlockPos().east(4), 0);
            h.assertTrue(DroneCrateItem.contents(sender.staged()).get(0).getCount() == 7
                    && sender.items().getStackInSlot(cargo + 7).getCount() == 11, "Shipment exceeded configured occupied slots");
            h.assertTrue(sender.energy().getEnergyStored() == 0 && sender.energy().receiveEnergy(1, false) == 0
                    && sender.cancel() && sender.energy().getEnergyStored() == Integer.MAX_VALUE, "Full-price refund overflowed");
            ServerConfig.FLUID_CAPSULE_CAPACITY.set(Integer.MAX_VALUE);
            var water = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
            ItemStack capsule = FluidCapsuleItem.pack(item("fluid_capsule", 1), water);
            h.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.FLUID_DRONE_PORT.get());
            FluidDronePortBlockEntity port = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(2, 1, 2));
            h.assertTrue(port.receiveTank().fill(water, IFluidHandler.FluidAction.EXECUTE) == Integer.MAX_VALUE, "Port capacity wrapped");
            ServerConfig.FLUID_CAPSULE_CAPACITY.set(25);
            FluidCapsuleItem.setFluid(capsule, io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(water, Integer.MAX_VALUE - 1));
            h.assertTrue(FluidCapsuleItem.fluid(capsule).getAmount() == Integer.MAX_VALUE - 1
                    && port.receiveTank().fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                    && port.receiveTank().getFluidAmount() == Integer.MAX_VALUE && FluidDronePortBlockEntity.autoThreshold(1) == 25,
                    "Shrinking a capsule or port destroyed remaining cargo");
            h.succeed();
        } finally {
            ServerConfig.FLUID_CAPSULE_CAPACITY.set(cap);
            ServerConfig.SHIPMENT_BASE_FE.set(base);
            ServerConfig.SHIPMENT_FE_PER_BLOCK.set(rate);
            ServerConfig.CRATE_TIER1_CAPACITY.set(battery);
            ServerConfig.CARGO_STACKS_PER_FLIGHT.set(slots);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void forgeKeepsSavedPhaseScaleAndDedicatedTankSurplus(GameTestHelper h) {
        double scale = ServerConfig.FORGE_PHASE_TIME_MULTIPLIER.get(), tank = ServerConfig.DEDICATED_TANK_MULTIPLIER.get();
        try {
            ServerConfig.FORGE_PHASE_TIME_MULTIPLIER.set(0.5);
            ServerConfig.DEDICATED_TANK_MULTIPLIER.set(2.0);
            var pos = new BlockPos(2, 1, 2);
            h.setBlock(pos, SinguloBlocks.JET_FORGE_CORE.get());
            JetForgeBlockEntity forge = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            var helium = new FluidStack(io.github.genichimaruo.singulo.registry.SinguloFluids.get("liquid_helium"), 32000);
            h.assertTrue(forge.duration(JetForgeBlockEntity.PRIME) == 30 && JetForgeBlockEntity.recipeTicks(50) == 235
                    && forge.coolant().fill(helium, IFluidHandler.FluidAction.EXECUTE) == 32000, "Configured forge preview or tank differs");
            forge.forcePhase(JetForgeBlockEntity.FOCUS, 17);
            var saved = forge.saveWithoutMetadata();
            saved.putDouble("phase_time_multiplier", 0.5);
            ServerConfig.DEDICATED_TANK_MULTIPLIER.set(0.01);
            ServerConfig.FORGE_PHASE_TIME_MULTIPLIER.set(1e9);
            forge.load(saved);
            h.assertTrue(forge.duration(JetForgeBlockEntity.FOCUS) == 45 && forge.phaseTicks() == 17
                    && forge.coolant().getTankCapacity(0) == 160 && forge.tank().getFluidAmount() == 32000
                    && forge.coolant().fill(helium, IFluidHandler.FluidAction.EXECUTE) == 0, "Active forge retimed or coolant disappeared");
            saved.remove("phase_time_multiplier");
            forge.load(saved);
            h.assertTrue(forge.duration(JetForgeBlockEntity.FOCUS) == 90, "Legacy job inherited a new phase duration");
            h.succeed();
        } finally {
            ServerConfig.FORGE_PHASE_TIME_MULTIPLIER.set(scale);
            ServerConfig.DEDICATED_TANK_MULTIPLIER.set(tank);
        }
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), count);
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void extremeStockSurvivesDowngradeAndDropsOnePackedMachine(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);
        var extra = ServerConfig.EXTRA_STACKS_PER_UPGRADE.get();
        var limit = ServerConfig.STOCK_UPGRADE_LIMIT.get();
        try {
            ServerConfig.EXTRA_STACKS_PER_UPGRADE.set(Integer.MAX_VALUE);
            ServerConfig.STOCK_UPGRADE_LIMIT.set(Integer.MAX_VALUE);
            h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
            MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            int slot = machine.type().assemblyUpgradeSlot();
            h.assertTrue(machine.items().insertItem(slot, item("assembly_stock_upgrade", Integer.MAX_VALUE), false).isEmpty(), "Upgrade count limit not applied");
            var grid = machine.assemblyGrid();
            h.assertTrue(grid.insertItem(0, new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE), false).isEmpty()
                    && grid.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1, "Extreme cell capacity overflowed");
            var save = machine.saveWithoutMetadata();
            ServerConfig.EXTRA_STACKS_PER_UPGRADE.set(0);
            ServerConfig.STOCK_UPGRADE_LIMIT.set(1);
            machine.load(save);
            h.assertTrue(grid.getStackInSlot(0).getCount() == Integer.MAX_VALUE && machine.items().extractItem(slot, 1, false).isEmpty(),
                    "Lowering the limit altered saved stock or unlocked the upgrade");
            var worldPos = machine.getBlockPos();
            h.getLevel().destroyBlock(worldPos, true);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(worldPos).inflate(1));
            h.assertTrue(drops.size() == 1 && (drops.get(0).getItem().getTagElement("BlockEntityTag") != null), "Huge board did not drop exactly one packed machine");
            h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
            MachineBlockEntity restored = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            restored.load(drops.get(0).getItem().getTagElement("BlockEntityTag").copy());
            h.assertTrue(restored.assemblyGrid().getStackInSlot(0).getCount() == Integer.MAX_VALUE
                    && restored.items().getStackInSlot(slot).getCount() == Integer.MAX_VALUE, "Packed machine lost items");
            h.succeed();
        } finally {
            ServerConfig.EXTRA_STACKS_PER_UPGRADE.set(extra);
            ServerConfig.STOCK_UPGRADE_LIMIT.set(limit);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void overclockBatchChargesEachOperationAndHandlesBothSpeedExtremes(GameTestHelper h) {
        var speed = ServerConfig.OVERCLOCK_SPEED_MULTIPLIER.get();
        var power = ServerConfig.OVERCLOCK_POWER_MULTIPLIER.get();
        var batch = ServerConfig.MACHINE_BATCH_LIMIT.get();
        var capacity = ServerConfig.MACHINE_BUFFER_MULTIPLIER.get();
        try {
            ServerConfig.OVERCLOCK_SPEED_MULTIPLIER.set(1e9);
            ServerConfig.OVERCLOCK_POWER_MULTIPLIER.set(7.5);
            ServerConfig.MACHINE_BATCH_LIMIT.set(256);
            ServerConfig.MACHINE_BUFFER_MULTIPLIER.set(1e9);
            var pos = new BlockPos(1, 1, 1);
            h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
            MachineBlockEntity m = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            m.items().setStackInSlot(0, item("zinc_dust", 32));
            m.items().setStackInSlot(m.type().upgradeSlot(), item("overclock_chip", 1));
            m.selectFor(item("zinc_ingot", 1), List.of(item("zinc_dust", 1)));
            h.assertTrue(m.energy().getMaxEnergyStored() == Integer.MAX_VALUE, "Scaled buffer wrapped");
            m.energy().setEnergy(1000);
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(m.items().getStackInSlot(m.type().outputSlot()).getCount() == 3 && m.energy().getEnergyStored() == 100
                    && m.items().getStackInSlot(0).getCount() == 29, "Batch did not charge 300 FE and one input for each result");
            ServerConfig.OVERCLOCK_SPEED_MULTIPLIER.set(0.000001);
            ServerConfig.OVERCLOCK_POWER_MULTIPLIER.set(1e9);
            m.energy().setEnergy(Integer.MAX_VALUE);
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(m.syncData().getInt(MachineBlockEntity.D_MAX_PROGRESS) == 100_000_000 && m.energy().getEnergyStored() == 0
                    && m.syncData().getInt(MachineBlockEntity.D_PROGRESS) == 1, "Very slow overclock or saturated FE/t did not work");
            h.succeed();
        } finally {
            ServerConfig.OVERCLOCK_SPEED_MULTIPLIER.set(speed);
            ServerConfig.OVERCLOCK_POWER_MULTIPLIER.set(power);
            ServerConfig.MACHINE_BATCH_LIMIT.set(batch);
            ServerConfig.MACHINE_BUFFER_MULTIPLIER.set(capacity);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void bulkRecipePaysRealItemAndFluidQuantities(GameTestHelper h) throws IOException {
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        int slots = ServerConfig.MACHINE_SLOT_STACKS.get(), tank = ServerConfig.MACHINE_TANK_CAPACITY.get();
        int batch = ServerConfig.MACHINE_BATCH_LIMIT.get();
        try {
            ServerConfig.MACHINE_SLOT_STACKS.set(Integer.MAX_VALUE);
            ServerConfig.MACHINE_TANK_CAPACITY.set(Integer.MAX_VALUE);
            ServerConfig.MACHINE_BATCH_LIMIT.set(Integer.MAX_VALUE);
            var raw = RecipePatches.read(h.getLevel().getServer().getResourceManager(), Singulo.id("kiln/zinc_ingot"));
            raw.addProperty("energy", 0);
            raw.addProperty("time", 1);
            raw.getAsJsonArray("ingredients").get(0).getAsJsonObject().addProperty("count", 1_000_000);
            raw.getAsJsonObject("result").addProperty("count", Integer.MAX_VALUE);
            raw.add("fluid_ingredients", JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":2000000000}]"));
            var holder = new MachineRecipe.Serializer().fromJson(Singulo.id("extreme_kiln"), raw);
            manager.replaceRecipes(List.of(holder));
            var pos = new BlockPos(2, 1, 2);
            h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
            MachineBlockEntity m = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            var io = m.automationItems();
            int out = m.type().outputSlot();
            h.assertTrue(io.insertItem(0, item("zinc_dust", 1_000_007), false).isEmpty(), "Bulk input rejected");
            h.assertTrue(m.automationFluids().fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE) == Integer.MAX_VALUE, "Tank capacity truncated");
            m.pasteSelectedRecipe(holder.getId());
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(io.getStackInSlot(0).getCount() == 7 && io.getStackInSlot(out).getCount() == Integer.MAX_VALUE
                    && m.automationFluids().getFluidInTank(0).getAmount() == 147_483_647, "Bulk craft lost or created material");
            var saved = m.saveWithoutMetadata();
            ServerConfig.MACHINE_SLOT_STACKS.set(1);
            m.load(saved);
            h.assertTrue(io.extractItem(out, Integer.MAX_VALUE, false).getCount() == 64
                    && io.getStackInSlot(out).getCount() == Integer.MAX_VALUE - 64, "Stock lost across save/downgrade or oversized player stack produced");
            ServerConfig.MACHINE_SLOT_STACKS.set(Integer.MAX_VALUE);
            raw.remove("fluid_ingredients");
            raw.getAsJsonArray("ingredients").get(0).getAsJsonObject().addProperty("count", 1);
            raw.getAsJsonObject("result").addProperty("count", 1);
            manager.replaceRecipes(List.of(new MachineRecipe.Serializer().fromJson(holder.getId(), raw)));
            m.items().setStackInSlot(out, ItemStack.EMPTY);
            m.items().setStackInSlot(0, item("zinc_dust", Integer.MAX_VALUE));
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(io.getStackInSlot(out).getCount() == Integer.MAX_VALUE && io.getStackInSlot(0).isEmpty(), "Max-count batch did not finish in one tick");
            raw.addProperty("time", 2);
            raw.add("fluid_ingredients", JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":1}]"));
            manager.replaceRecipes(List.of(new MachineRecipe.Serializer().fromJson(holder.getId(), raw)));
            m.items().setStackInSlot(out, ItemStack.EMPTY);
            m.items().setStackInSlot(0, item("zinc_dust", 600));
            for (int i = 0; i < 2; i++) MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(io.getStackInSlot(out).getCount() == 256, "Stateful batch exceeded tick budget");
            m.load(m.saveWithoutMetadata());
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(io.getStackInSlot(out).getCount() == 512, "Stateful batch failed to resume after save");
            MachineBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            h.assertTrue(io.getStackInSlot(out).getCount() == 600 && io.getStackInSlot(0).isEmpty(), "Last batch slice lost materials");
            h.succeed();
        } finally {
            ServerConfig.MACHINE_SLOT_STACKS.set(slots);
            ServerConfig.MACHINE_TANK_CAPACITY.set(tank);
            ServerConfig.MACHINE_BATCH_LIMIT.set(batch);
            manager.replaceRecipes(original);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void largeGeneratorRateAndRecipeNumbersRoundTrip(GameTestHelper h) throws IOException {
        double old = ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get();
        try {
            ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.set(1e9);
            var pos = new BlockPos(1, 1, 1);
            h.setBlock(pos, SinguloBlocks.MUON_FUSION_REACTOR.get());
            MuonFusionReactorBlockEntity reactor = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            reactor.items().setStackInSlot(0, item("muon_catalyst", 1));
            reactor.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            MuonFusionReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
            h.assertTrue(reactor.energy().getEnergyStored() == Integer.MAX_VALUE && reactor.slotsData().getInt(GeneratorSlotsMenu.D_RATE_WHOLE) == Integer.MAX_VALUE,
                    "Generator output and menu disagree at the FE limit");
            ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.set(0.25 / ServerConfig.FUSION_REACTOR_OUTPUT.get());
            reactor.energy().setEnergy(0);
            MuonFusionReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
            h.assertTrue(reactor.slotsData().getDouble(GeneratorSlotsMenu.D_RATE) == 0.25 && reactor.energy().getEnergyStored() == 0,
                    "Fractional rate rounded prematurely");
            reactor.load(reactor.saveWithoutMetadata());
            for (int i = 0; i < 3; i++) MuonFusionReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
            h.assertTrue(reactor.energy().getEnergyStored() == 1, "Fractional generation lost on save");
            JsonObject raw = RecipePatches.read(h.getLevel().getServer().getResourceManager(), Singulo.id("kiln/zinc_ingot"));
            raw.addProperty("time", Integer.MAX_VALUE);
            raw.addProperty("energy", Integer.MAX_VALUE);
            h.assertTrue(new MachineRecipe.Serializer().fromJson(Singulo.id("extreme_number"), raw).time() == Integer.MAX_VALUE, "Large recipe did not decode");
            raw.add("energy", JsonParser.parseString("4294967297"));
            try {
                new MachineRecipe.Serializer().fromJson(Singulo.id("extreme_number"), raw);
                h.fail("Out-of-range recipe cost wrapped to 1");
            } catch (com.google.gson.JsonSyntaxException expected) {
                // The legacy JSON serializer must reject overflow before converting to int.
            }
            h.assertTrue(EnergyText.shortForm(Long.MAX_VALUE).equals("9.22E") && EnergyText.shortForm(Integer.MAX_VALUE).equals("2.15G")
                    && EnergyText.shortForm(0.000001).length() < 10, "Numeric labels grew beyond their display width");
            h.succeed();
        } finally {
            ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.set(old);
        }
    }

    @GameTest(template = "empty", batch = "extreme_balance")
    public static void hugeBossHealthAccumulatesSubPixelHitsAndScalesAttacks(GameTestHelper h) {
        double hp = ServerConfig.BOSS_HEALTH_MULTIPLIER.get(), damage = ServerConfig.BOSS_DAMAGE_MULTIPLIER.get();
        try {
            ServerConfig.BOSS_HEALTH_MULTIPLIER.set(1e7);
            ServerConfig.BOSS_DAMAGE_MULTIPLIER.set(0.0);
            var boss = SinguloEntities.ECHO_SENTINEL.get().create(h.getLevel());
            h.assertTrue(boss.effectiveMaxHealth() > 1e9 && boss.getHealth() == boss.getMaxHealth(), "Extreme effective HP was truncated");
            float start = boss.getHealth();
            for (int i = 0; i < 64; i++) {
                boss.invulnerableTime = 0;
                boss.hurt(boss.damageSources().generic(), 16);
            }
            h.assertTrue(boss.getHealth() < start && boss.getHealth() > start - 1, "Tiny hits were lost or exaggerated");
            var target = net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
            float before = target.getHealth();
            target.hurt(boss.damageSources().mobAttack(boss), 2);
            h.assertTrue(target.getHealth() == before, "Zero boss damage was ignored");
            ServerConfig.BOSS_DAMAGE_MULTIPLIER.set(1e9);
            target.invulnerableTime = 0;
            target.hurt(boss.damageSources().mobAttack(boss), 2);
            h.assertTrue(target.isDeadOrDying(), "Extreme boss damage wrapped");
            h.succeed();
        } finally {
            ServerConfig.BOSS_HEALTH_MULTIPLIER.set(hp);
            ServerConfig.BOSS_DAMAGE_MULTIPLIER.set(damage);
        }
    }
}
