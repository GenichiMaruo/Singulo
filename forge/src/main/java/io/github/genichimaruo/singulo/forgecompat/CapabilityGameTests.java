package io.github.genichimaruo.singulo.forgecompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.gametest.GameTestDontPrefix;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(value = "singulo", namespace = "singulo")
@GameTestDontPrefix
public final class CapabilityGameTests {
    private static ICapabilityProvider provider(AtomicReference<EnergyStorage> value) throws Exception {
        Class<?> type = Class.forName(Caps.class.getName() + "$Provider");
        var constructor = type.getDeclaredConstructor(Object.class, List.class);
        constructor.setAccessible(true);
        var entry = new RegisterCapabilitiesEvent.Entry<Object>(ForgeCapabilities.ENERGY, (owner, side) -> value.get());
        return (ICapabilityProvider) constructor.newInstance(new Object(), List.of(entry));
    }

    @GameTest(template = "empty")
    public static void removedHandlerInvalidatesCachedOptional(GameTestHelper helper) throws Exception {
        var value = new AtomicReference<>(new EnergyStorage(100));
        var provider = provider(value);
        var old = provider.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
        helper.assertTrue(old.isPresent(), "Initial capability missing");
        value.set(null);
        helper.assertFalse(provider.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(), "Removed handler still exposed");
        helper.assertFalse(old.isPresent(), "External cached handler remains valid after removal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementHandlerInvalidatesCachedOptional(GameTestHelper helper) throws Exception {
        var value = new AtomicReference<>(new EnergyStorage(100));
        var provider = provider(value);
        var old = provider.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
        var replacement = new EnergyStorage(200);
        value.set(replacement);
        var current = provider.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
        helper.assertFalse(old.isPresent(), "Old handler retained after replacement");
        helper.assertTrue(current.resolve().orElse(null) == replacement, "Replacement handler not exposed");
        helper.succeed();
    }
}
