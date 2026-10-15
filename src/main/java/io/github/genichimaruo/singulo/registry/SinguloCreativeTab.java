package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineType;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Singulo.MODID);

    public static final Supplier<CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.singulo"))
            .icon(() -> new ItemStack(SinguloBlocks.MACHINES.get(MachineType.KILN).get()))
            .displayItems((params, output) -> SinguloItems.TAB_ORDER.forEach(item -> {
                // Mekanism がないときは、Mekanism の化学物質のためのアップグレードを出さない（使えないので）
                String path = item.getId().getPath();
                if (io.github.genichimaruo.singulo.android.work.Chemicals.present()
                        || !path.equals("android_gas_tank_upgrade") && !path.equals("android_dual_tank_upgrade")) {
                    output.accept(item.get());
                }
            }))
            .build());

    private SinguloCreativeTab() {}
}
