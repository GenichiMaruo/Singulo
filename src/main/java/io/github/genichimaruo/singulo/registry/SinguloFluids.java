package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.generated.GeneratedContent.FluidDef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 液体・ガス。どれも設置できるブロックやバケツは持たず、装置のタンクと配管の中だけに存在する。
 * ガスは密度を負にして、他modのタンク表示で上に溜まるようにしている。
 */
public final class SinguloFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Singulo.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Singulo.MODID);

    public record Entry(FluidDef def, RegistryObject<FluidType> type,
                        RegistryObject<ForgeFlowingFluid.Source> source,
                        RegistryObject<ForgeFlowingFluid.Flowing> flowing) {}

    public static final Map<String, Entry> ALL = new LinkedHashMap<>();

    static {
        for (FluidDef def : GeneratedContent.FLUIDS) {
            ALL.put(def.id(), create(def));
        }
    }

    private static Entry create(FluidDef def) {
        RegistryObject<FluidType> type = FLUID_TYPES.register(def.id(), () -> new FluidType(
                FluidType.Properties.create()
                        .descriptionId("fluid_type.singulo." + def.id())
                        .density(def.gas() ? -1000 : 1000)
                        .viscosity(def.gas() ? 200 : 1000)
                        .temperature(temperatureOf(def.id()))
                        .canSwim(false).canDrown(false).canPushEntity(false)) {
            @Override
            public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                consumer.accept(io.github.genichimaruo.singulo.client.SinguloClient.fluidExtensions(def.color()));
            }
        });
        @SuppressWarnings("unchecked")
        Supplier<ForgeFlowingFluid.Properties>[] props = new Supplier[1];
        RegistryObject<ForgeFlowingFluid.Source> source =
                FLUIDS.register(def.id(), () -> new ForgeFlowingFluid.Source(props[0].get()));
        RegistryObject<ForgeFlowingFluid.Flowing> flowing =
                FLUIDS.register("flowing_" + def.id(), () -> new ForgeFlowingFluid.Flowing(props[0].get()));
        ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(type, source, flowing);
        props[0] = () -> properties;
        return new Entry(def, type, source, flowing);
    }

    /** 実在の沸点付近の温度（K）。 */
    private static int temperatureOf(String id) {
        return switch (id) {
            case "liquid_nitrogen" -> 77;
            case "liquid_helium" -> 4;
            case "axion_condensate" -> 2;
            default -> 300;
        };
    }

    public static Fluid get(String id) {
        return ALL.get(id).source().get();
    }

    private SinguloFluids() {}
}
