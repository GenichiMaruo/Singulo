package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.CatalystDeviceMenu;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorMenu;
import io.github.genichimaruo.singulo.reactor.PenroseReactorMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;

public final class SinguloMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, Singulo.MODID);

    public static final Supplier<MenuType<MachineMenu>> MACHINE = REGISTER.register("machine",
            () -> IForgeMenuType.create(MachineMenu::client));

    public static final Supplier<MenuType<ThermoelectricGeneratorMenu>> THERMOELECTRIC_GENERATOR = REGISTER.register(
            "thermoelectric_generator", () -> IForgeMenuType.create(ThermoelectricGeneratorMenu::client));

    public static final Supplier<MenuType<CatalystDeviceMenu>> CATALYST_DEVICE = REGISTER.register(
            "catalyst_device", () -> IForgeMenuType.create(CatalystDeviceMenu::client));

    public static final Supplier<MenuType<PenroseReactorMenu>> PENROSE_REACTOR = REGISTER.register(
            "penrose_reactor", () -> IForgeMenuType.create(PenroseReactorMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.machine.SmesMenu>> SMES = REGISTER.register(
            "smes", () -> IForgeMenuType.create(io.github.genichimaruo.singulo.machine.SmesMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu>> WORMHOLE_STABILIZER =
            REGISTER.register("wormhole_stabilizer",
                    () -> IForgeMenuType.create(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.machine.DeviceMenu>> DEVICE = REGISTER.register("device",
            () -> IForgeMenuType.create(io.github.genichimaruo.singulo.machine.DeviceMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.android.AndroidMenu>> ANDROID = REGISTER.register(
            "android", () -> IForgeMenuType.create(io.github.genichimaruo.singulo.android.AndroidMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.android.work.WorkConsoleMenu>> ANDROID_WORK_CONSOLE = REGISTER.register(
            "android_work_console", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.genichimaruo.singulo.android.work.WorkConsoleMenu::client));
    public static final Supplier<MenuType<io.github.genichimaruo.singulo.android.work.DeliveryPortMenu>> ANDROID_DELIVERY_PORT = REGISTER.register(
            "android_delivery_port", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.genichimaruo.singulo.android.work.DeliveryPortMenu::client));
    public static final Supplier<MenuType<io.github.genichimaruo.singulo.android.work.RecipeRackMenu>> ANDROID_RECIPE_RACK = REGISTER.register(
            "android_recipe_rack", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.genichimaruo.singulo.android.work.RecipeRackMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.android.AndroidStationMenu>> ANDROID_STATION = REGISTER.register(
            "android_station", () -> IForgeMenuType.create(io.github.genichimaruo.singulo.android.AndroidStationMenu::client));

    public static final Supplier<MenuType<io.github.genichimaruo.singulo.genesis.GenesisCoreMenu>> GENESIS_CORE = REGISTER.register(
            "genesis_core", () -> IForgeMenuType.create(io.github.genichimaruo.singulo.genesis.GenesisCoreMenu::client));

    private SinguloMenus() {}
}
