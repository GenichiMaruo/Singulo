package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.storage.HorizonVaultBlockEntity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercise the registered ItemStack tooltip path, including BlockItem's delegation. */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class BlockItemTooltipGameTests {
    @GameTest(template = "empty")
    public static void allPipeItemsShowTheirCapacity(GameTestHelper h) {
        var pipes = List.of(SinguloBlocks.FLUID_PIPE.get(), SinguloBlocks.VACUUM_JACKETED_PIPE.get(),
                SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get());
        long[] capacities = {2_000, 32_000, 512_000, Long.MAX_VALUE};
        for (int i = 0; i < pipes.size(); i++) {
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = tooltip(h, new ItemStack(pipes.get(i)), flag);
                if (capacities[i] == Long.MAX_VALUE) {
                    line(h, lines, "tooltip.singulo.fluid_pipe.unlimited");
                    absent(h, lines, "tooltip.singulo.fluid_pipe.capacity");
                } else {
                    h.assertTrue(line(h, lines, "tooltip.singulo.fluid_pipe.capacity").getArgs()[0]
                            .equals(String.format("%,d", capacities[i])), "Wrong pipe capacity");
                    absent(h, lines, "tooltip.singulo.fluid_pipe.unlimited");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyVaultItemShowsCapacity(GameTestHelper h) {
        var lines = tooltip(h, new ItemStack(SinguloBlocks.HORIZON_VAULT.get()), TooltipFlag.NORMAL);
        h.assertTrue(line(h, lines, "tooltip.singulo.horizon_vault.capacity").getArgs()[0]
                .equals(String.format("%,d", HorizonVaultBlockEntity.CAPACITY)), "Wrong empty vault capacity");
        absent(h, lines, "tooltip.singulo.horizon_vault.contents");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void filledVaultItemShowsStoredTypeAndLongCount(GameTestHelper h) {
        ItemStack stack = new ItemStack(SinguloBlocks.HORIZON_VAULT.get());
        ItemStack stored = new ItemStack(Items.DIAMOND);
        stored.setHoverName(Component.literal("Stored test diamonds"));
        SinguloComponents.set(stack, SinguloComponents.VAULT_ITEM.get(), stored);
        long count = 9_876_543_210L;
        SinguloComponents.set(stack, SinguloComponents.VAULT_COUNT.get(), count);
        var lines = tooltip(h, stack, TooltipFlag.NORMAL);
        Object[] args = line(h, lines, "tooltip.singulo.horizon_vault.contents").getArgs();
        h.assertTrue(args[0].equals(stored.getHoverName()) && args[1].equals(String.format("%,d", count)),
                "Stored item name or long count missing");
        absent(h, lines, "tooltip.singulo.horizon_vault.capacity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidPortItemShowsBothTanksAndSharedMarkers(GameTestHelper h) {
        ItemStack stack = new ItemStack(SinguloBlocks.FLUID_DRONE_PORT.get());
        var empty = tooltip(h, stack, TooltipFlag.NORMAL);
        absent(h, empty, "tooltip.singulo.fluid_drone_port.send");
        absent(h, empty, "tooltip.singulo.fluid_drone_port.receive");
        FluidStack send = new FluidStack(Fluids.WATER, 12_345);
        FluidStack receive = new FluidStack(Fluids.LAVA, 54_321);
        SinguloComponents.set(stack, SinguloComponents.PORT_SEND_FLUID.get(), send);
        SinguloComponents.set(stack, SinguloComponents.PORT_RECEIVE_FLUID.get(), receive);
        CompoundTag data = new CompoundTag();
        data.putString("id", "singulo:fluid_drone_port");
        stack.getOrCreateTag().put("BlockEntityTag", data);
        stack.getOrCreateTag().putBoolean("singulo:packed_device", true);
        SinguloComponents.set(stack, SinguloComponents.MIXED_SOURCE.get(), true);
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            var lines = tooltip(h, stack, flag);
            for (var entry : List.of(new Object[]{"send", send}, new Object[]{"receive", receive})) {
                FluidStack fluid = (FluidStack) entry[1];
                Object[] args = line(h, lines, "tooltip.singulo.fluid_drone_port." + entry[0]).getArgs();
                h.assertTrue(args[0].equals(fluid.getDisplayName()) && args[1].equals(String.format("%,d", fluid.getAmount())),
                        "Wrong fluid name or amount for " + entry[0]);
            }
            line(h, lines, "tooltip.singulo.packed_device");
            line(h, lines, "tooltip.singulo.mixed_source");
        }
        h.succeed();
    }

    private static List<Component> tooltip(GameTestHelper h, ItemStack stack, TooltipFlag flag) {
        ItemStack before = stack.copy();
        var lines = stack.getTooltipLines(null, flag);
        h.assertTrue(ItemStack.matches(before, stack), "Tooltip changed item data");
        line(h, lines, "tooltip.singulo.stage");
        return lines;
    }

    private static TranslatableContents line(GameTestHelper h, List<Component> lines, String key) {
        var matches = lines.stream().map(Component::getContents)
                .filter(c -> c instanceof TranslatableContents t && t.getKey().equals(key)).toList();
        h.assertTrue(matches.size() == 1, "Expected exactly one " + key + ", found " + matches.size());
        return (TranslatableContents) matches.get(0);
    }

    private static void absent(GameTestHelper h, List<Component> lines, String key) {
        h.assertFalse(lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)),
                "Unexpected " + key);
    }
}
