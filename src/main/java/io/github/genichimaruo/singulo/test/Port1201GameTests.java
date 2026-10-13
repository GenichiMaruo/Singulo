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
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Port1201GameTests {
    private Port1201GameTests() {}

    @GameTest(template = "empty")
    public static void chargerEnergyPersistsAndExposesCapability(GameTestHelper helper) {
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

    @GameTest(template = "empty")
    public static void armorPlateAcceptsArmorEnchantments(GameTestHelper helper) {
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

    @GameTest(template = "empty")
    public static void afterglowWingsUseLegacyFlightHooks(GameTestHelper helper) {
        var wings = SinguloItems.AFTERGLOW_WINGS.get();
        ItemStack stack = new ItemStack(wings);
        var wearer = helper.makeMockPlayer();
        helper.assertTrue(wings.canElytraFly(stack, wearer), "Afterglow wings cannot start gliding");
        helper.assertTrue(wings.elytraFlightTick(stack, wearer, 100), "Wings stopped gliding");
        helper.assertFalse(stack.isDamageableItem(), "Wings became damageable during the backport");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newPacketsRoundTripOnLegacyChannel(GameTestHelper helper) {
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
            helper.assertTrue(buffer.readableBytes() == 0, "Packet decoder left extra bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
