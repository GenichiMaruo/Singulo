package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidChargerItem;
import io.github.genichimaruo.singulo.network.EndingPayload;
import io.github.genichimaruo.singulo.network.GalaxyPayload;
import io.github.genichimaruo.singulo.network.RenameAndroidPayload;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Regression checks for APIs that differ from the 1.21.1 implementation. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = Singulo.MODID)
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Port1201GameTests {
    private Port1201GameTests() {}

    /** ConfigValue.set() auto-saves; a delayed file watcher reload can undo another test's temporary setting. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stabilizeGameTestConfig(net.minecraftforge.event.server.ServerStartedEvent event) throws java.io.IOException {
        if (!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestServer()) return;
        for (var config : net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets()
                .get(net.minecraftforge.fml.config.ModConfig.Type.SERVER)) {
            if (config.getModId().equals(Singulo.MODID)) {
                com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(config.getFullPath());
                Singulo.LOGGER.info("GameTests: disabled asynchronous config watching for {}", config.getFileName());
            }
        }
    }


    @GameTest(setupTicks = 20, template = "empty")
    public static void stepHeightMatchesModernOneBlockHeight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var warden = io.github.genichimaruo.singulo.registry.SinguloEntities.HORIZON_WARDEN.get().create(helper.getLevel());
        var android = io.github.genichimaruo.singulo.registry.SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        helper.assertTrue(Math.abs(warden.getStepHeight() - 1.0F) < 0.001F, "Warden step height changed: " + warden.getStepHeight());
        helper.assertTrue(Math.abs(android.getStepHeight() - 1.0F) < 0.001F, "Android step height changed: " + android.getStepHeight());
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void chargerEnergyPersistsAndExposesCapability(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        ItemStack charger = new ItemStack(SinguloItems.ANDROID_CHARGER.get());
        var energy = charger.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        helper.assertTrue(energy != null, "Wireless charger has no Forge energy capability");
        helper.assertTrue(energy.receiveEnergy(12345, false) == 12345, "Charger rejected energy");
        ItemStack restored = ItemStack.of(charger.save(new CompoundTag()));
        var restoredEnergy = restored.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        helper.assertTrue(restoredEnergy != null && restoredEnergy.getEnergyStored() == 12345,
                "Charger lost energy across NBT round-trip");
        helper.assertTrue(AndroidChargerItem.energy(restored).getMaxEnergyStored() == 300000, "Charger capacity changed");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void armorPlateAcceptsArmorEnchantments(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        ItemStack plate = new ItemStack(SinguloItems.ANDROID_ARMOR_PLATE.get());
        var item = plate.getItem();
        helper.assertTrue(item.canApplyAtEnchantingTable(plate, Enchantments.ALL_DAMAGE_PROTECTION),
                "Armor plate cannot receive protection on 1.20.1");
        helper.assertTrue(item.canApplyAtEnchantingTable(plate, Enchantments.THORNS), "Armor plate cannot receive thorns");
        helper.assertFalse(item.canApplyAtEnchantingTable(plate, Enchantments.SHARPNESS), "Armor plate accepts weapon enchantments");
        plate.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        ItemStack restored = ItemStack.of(plate.save(new CompoundTag()));
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.ALL_DAMAGE_PROTECTION, restored) == 3, "Armor enchantment did not survive NBT saving");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void afterglowWingsUseLegacyFlightHooks(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var wings = SinguloItems.AFTERGLOW_WINGS.get();
        ItemStack stack = new ItemStack(wings);
        var wearer = helper.makeMockPlayer();
        helper.assertTrue(wings.canElytraFly(stack, wearer), "Afterglow wings cannot start gliding");
        helper.assertTrue(wings.elytraFlightTick(stack, wearer, 100), "Wings stopped gliding");
        helper.assertFalse(stack.isDamageableItem(), "Wings became damageable during the backport");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void automationLegacyItemsRoundTrip(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        for (var upgrade : List.of(SinguloItems.ANDROID_MINING_UPGRADE.get(), SinguloItems.ANDROID_FELLING_UPGRADE.get())) {
            ItemStack stack = new ItemStack(upgrade);
            helper.assertTrue(upgrade.canApplyAtEnchantingTable(stack, Enchantments.BLOCK_EFFICIENCY), "Upgrade rejects efficiency");
            helper.assertTrue(upgrade.canApplyAtEnchantingTable(stack, Enchantments.BLOCK_FORTUNE), "Upgrade rejects fortune");
            helper.assertTrue(upgrade.canApplyAtEnchantingTable(stack, Enchantments.SILK_TOUCH), "Upgrade rejects silk touch");
            helper.assertFalse(upgrade.canApplyAtEnchantingTable(stack, Enchantments.UNBREAKING), "Upgrade accepts durability enchantment");
            stack.enchant(Enchantments.BLOCK_FORTUNE, 3);
            ItemStack saved = ItemStack.of(stack.save(new CompoundTag()));
            helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                    Enchantments.BLOCK_FORTUNE, saved) == 3, "Upgrade enchantment lost during saving");
        }
        var fluid = new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1500);
        ItemStack sample = io.github.genichimaruo.singulo.android.work.FluidSamples.of(fluid);
        var restored = io.github.genichimaruo.singulo.android.work.FluidSamples.fluid(ItemStack.of(sample.save(new CompoundTag())));
        helper.assertTrue(restored.isFluidEqual(fluid) && restored.getAmount() == 1500, "Fluid sample lost its identity or millibuckets");
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        helper.assertTrue(location.getMaxStackSize() == 64, "Blank location cards do not stack");
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(location, helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        helper.assertTrue(location.getMaxStackSize() == 1, "Recorded location cards still stack");
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.clear(location);
        helper.assertTrue(location.getMaxStackSize() == 64 && location.getTag() == null, "Cleared card differs from a blank card");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void newPacketsRoundTripOnLegacyChannel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GalaxyPayload galaxies = new GalaxyPayload(List.of(0L, Long.MIN_VALUE, Long.MAX_VALUE), true);
            GalaxyPayload.encode(galaxies, buffer);
            helper.assertTrue(galaxies.equals(GalaxyPayload.decode(buffer)), "Galaxy packet changed seeds or fresh flag");
            EndingPayload ending = new EndingPayload(true);
            EndingPayload.encode(ending, buffer);
            helper.assertTrue(ending.equals(EndingPayload.decode(buffer)), "Ending packet lost the archivist flag");
            RenameAndroidPayload rename = new RenameAndroidPayload(42, "護衛🌈 Android");
            RenameAndroidPayload.encode(rename, buffer);
            helper.assertTrue(rename.equals(RenameAndroidPayload.decode(buffer)), "Rename packet lost Unicode or container id");
            var data = new CompoundTag();
            data.putString("unicode", "指令台🌈");
            data.putLong("amount", 1500);
            var action = new io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload(43, data);
            io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload.encode(action, buffer);
            helper.assertTrue(action.equals(io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload.decode(buffer)), "Work action packet changed NBT");
            var view = new io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload(44, data);
            io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload.encode(view, buffer);
            helper.assertTrue(view.equals(io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload.decode(buffer)), "Work view packet changed NBT");
            helper.assertTrue(buffer.readableBytes() == 0, "Packet decoder left extra bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
    @GameTest(setupTicks = 20, template = "empty")
    public static void workConsoleLinksSurviveLegacyNbt(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var pos = new net.minecraft.core.BlockPos(2, 1, 2);
        helper.setBlock(pos, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        var link = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 2));
        var relativeLink = new net.minecraft.core.BlockPos(3, 1, 2);
        helper.setBlock(relativeLink, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_DELIVERY_PORT.get());
        io.github.genichimaruo.singulo.android.work.DeliveryPortBlockEntity port = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, relativeLink);
        var card = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(card, helper.getLevel(), helper.absolutePos(pos),
                io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(helper);
        helper.assertTrue(port.useItem(player, card, net.minecraft.world.InteractionHand.MAIN_HAND), "Cannot link port fixture");
        helper.assertTrue(console.links().equals(List.of(link)), "Port fixture did not register its link");
        var saved = console.saveWithFullMetadata();
        console.load(saved);
        helper.assertTrue(console.links().equals(List.of(link)), "Work console lost links across 1.20.1 compound-tag round-trip");
        helper.succeed();
    }
}
