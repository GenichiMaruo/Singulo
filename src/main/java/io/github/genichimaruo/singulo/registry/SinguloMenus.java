package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.CatalystDeviceMenu;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorMenu;
import io.github.genichimaruo.singulo.reactor.PenroseReactorMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, Singulo.MODID);

    public static final Supplier<MenuType<MachineMenu>> MACHINE = REGISTER.register("machine",
            () -> IMenuTypeExtension.create(MachineMenu::client));

    public static final Supplier<MenuType<ThermoelectricGeneratorMenu>> THERMOELECTRIC_GENERATOR = REGISTER.register(
            "thermoelectric_generator", () -> IMenuTypeExtension.create(ThermoelectricGeneratorMenu::client));

    public static final Supplier<MenuType<CatalystDeviceMenu>> CATALYST_DEVICE = REGISTER.register(
            "catalyst_device", () -> IMenuTypeExtension.create(CatalystDeviceMenu::client));

    public static final Supplier<MenuType<PenroseReactorMenu>> PENROSE_REACTOR = REGISTER.register(
            "penrose_reactor", () -> IMenuTypeExtension.create(PenroseReactorMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.machine.SmesMenu>> SMES = REGISTER.register(
            "smes", () -> IMenuTypeExtension.create(io.github.genichimaruo.singulo.machine.SmesMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu>> WORMHOLE_STABILIZER =
            REGISTER.register("wormhole_stabilizer",
                    () -> IMenuTypeExtension.create(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.machine.DeviceMenu>> DEVICE = REGISTER.register("device",
            () -> IMenuTypeExtension.create(io.github.genichimaruo.singulo.machine.DeviceMenu::client));

    private SinguloMenus() {}
}
