package io.github.genichimaruo.singulo.test;

import com.mojang.serialization.JsonOps;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineDiagnosis;
import io.github.genichimaruo.singulo.machine.MachineRecipeIndex;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblyGameTests {
    private static final MachineType TYPE = MachineType.PRECISION_ASSEMBLER;

    private static MachineRecipe recipe(GameTestHelper h, String path) {
        return MachineRecipeIndex.station(h.getLevel(), TYPE).stream()
                .filter(r -> r.id().equals(Singulo.id("precision_assembler/" + path))).findFirst().orElseThrow().value();
    }

    private static MachineBlockEntity assembler(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(TYPE).get());
        return h.getBlockEntity(pos);
    }

    /** Read the diagram independently of the machine's cell-index conversion. */
    private static List<ItemStack> parts(GameTestHelper h, MachineRecipe recipe) {
        List<ItemStack> out = new ArrayList<>();
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            if ((y == 0 || y == 4) && (x == 0 || x == 4)) continue;
            char c = recipe.shape().get(y).charAt(x);
            if (c == ' ') out.add(ItemStack.EMPTY);
            else {
                var choices = recipe.ingredients().get(c - 'A').ingredient().getItems();
                h.assertTrue(choices.length > 0, "No material for " + recipe.result() + " at " + x + "," + y);
                out.add(choices[out.size() % choices.length].copyWithCount(1));
            }
        }
        out.add(recipe.ingredients().stream().filter(i -> i.uses() > 0)
                .map(i -> i.ingredient().getItems()[0].copyWithCount(1)).findFirst().orElse(ItemStack.EMPTY));
        return out;
    }

    private static void loadBoard(MachineBlockEntity machine, List<ItemStack> board) {
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++) machine.assemblyGrid().setStackInSlot(i, board.get(i).copy());
    }

    private static void tick(GameTestHelper h, MachineBlockEntity machine, int duration) {
        for (int i = 0; i < duration; i++)
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }

    private static void assertProduct(GameTestHelper h, MachineBlockEntity machine, MachineRecipe recipe) {
        h.assertTrue(ItemStack.matches(machine.items().getStackInSlot(TYPE.outputSlot()), recipe.result()),
                "Incorrect assembled product for " + recipe.result());
        for (int slot = 0; slot < MachineRecipe.ASSEMBLY_CELLS; slot++)
            h.assertTrue(machine.assemblyGrid().getStackInSlot(slot).isEmpty(), "Board retained a part at " + slot);
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void everyInstalledAssemblyDiagramMatchesExactlyOneProduct(GameTestHelper h) {
        var all = MachineRecipeIndex.station(h.getLevel(), TYPE).stream()
                .filter(r -> r.id().getNamespace().equals(Singulo.MODID)).toList();
        h.assertTrue(all.size() == (ModList.get().isLoaded("mekanism") ? 141 : 139), "Unexpected number of bundled diagrams");
        for (var holder : all) {
            var recipe = holder.value();
            h.assertTrue(recipe.hasAssemblyShape(), "Missing diagram: " + holder.id());
            var board = parts(h, recipe);
            var plan = recipe.assemblyPlan(board);
            h.assertTrue(plan != null, "The registered ingredients do not match " + holder.id());
            for (int slot = 0; slot < MachineRecipe.ASSEMBLY_CELLS; slot++)
                h.assertTrue(plan.consume()[slot] == (board.get(slot).isEmpty() ? 0 : 1), "Wrong consumption for " + holder.id());
            for (var other : all) if (other != holder) {
                h.assertTrue(other.value().assemblyPlan(board) == null, "Same board made both " + holder.id() + " and " + other.id());
                h.assertTrue(!canAssembleWhilePlacing(other.value(), recipe),
                        "Placing " + holder.id() + " can start " + other.id() + " before the board is complete");
            }
        }
        h.succeed();
    }

    /** A player can pause between any two placements, with any compatible catalyst already loaded. */
    private static boolean canAssembleWhilePlacing(MachineRecipe other, MachineRecipe intended) {
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
            var required = other.assemblyInput(cell);
            if (required == null) continue;
            var planned = intended.assemblyInput(cell);
            if (planned == null || Arrays.stream(planned.ingredient().getItems()).noneMatch(required.ingredient()::test)) return false;
        }
        return true;
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void assemblyShapeSurvivesNetworkAndLegacyJsonRemainsShapeless(GameTestHelper h) {
        var recipe = recipe(h, "android_tank_upgrade");
        var registry = h.getLevel().registryAccess();
        var board = parts(h, recipe);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry);
        try {
            MachineRecipe.STREAM_CODEC.encode(buf, recipe);
            var decoded = MachineRecipe.STREAM_CODEC.decode(buf);
            h.assertTrue(recipe.shape().equals(decoded.shape()) && decoded.assemblyPlan(board) != null, "Client lost the assembly shape");
        } finally {
            buf.release();
        }
        var ops = registry.createSerializationContext(JsonOps.INSTANCE);
        var codec = MachineRecipe.CODEC.codec();
        var json = codec.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        json.remove("shape");
        var legacy = codec.parse(ops, json).getOrThrow();
        h.assertTrue(!legacy.hasAssemblyShape() && legacy.assemblyPlan(board) != null, "Legacy recipe failed to load");
        int glass = 4, bucket = 9;
        java.util.Collections.swap(board, glass, bucket);
        h.assertTrue(recipe.assemblyPlan(board) == null && legacy.assemblyPlan(board) != null,
                "The shaped and shapeless recipes did not distinguish the swapped glass and bucket");
        var diagnosis = MachineDiagnosis.idle(h.getLevel(), TYPE, board, List.of(), -1);
        h.assertTrue(diagnosis.kind() == MachineDiagnosis.Kind.MISPLACED
                        && diagnosis.lines().contains(net.minecraft.network.chat.Component.translatable("gui.singulo.assembly.board_hint")),
                "Swapped parts did not point to the assembly diagram");
        var removed = board.set(glass, ItemStack.EMPTY);
        h.assertTrue(MachineDiagnosis.idle(h.getLevel(), TYPE, board, List.of(), -1).kind() == MachineDiagnosis.Kind.MISSING,
                "A missing bucket was reported as a placement mistake");
        board.set(glass, removed);
        board.set(0, new ItemStack(Items.DIRT));
        h.assertTrue(recipe.assemblyPlan(board) == null && legacy.assemblyPlan(board) == null, "Unused board material was ignored");
        board.set(0, ItemStack.EMPTY);
        java.util.Collections.swap(board, glass, bucket);
        board.set(glass, board.get(glass).copyWithCount(2));
        h.assertTrue(recipe.assemblyPlan(board) == null && legacy.assemblyPlan(board) == null, "Overfilled board cell was accepted");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void tankBoardSurvivesReloadAndRejectsExternalParts(GameTestHelper h) {
        var machine = assembler(h);
        var recipe = recipe(h, "android_tank_upgrade");
        var board = parts(h, recipe);
        loadBoard(machine, board);
        var glass = machine.assemblyGrid().extractItem(4, 1, false);
        h.assertTrue(!glass.isEmpty(), "Tank diagram has no glass in its upper left");
        var refused = machine.automationItems().insertItem(0, glass.copy(), false);
        h.assertTrue(ItemStack.matches(refused, glass), "Automation accepted a board part");
        machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
        int before = machine.energy().getEnergyStored();
        tick(h, machine, 3);
        h.assertTrue(machine.report().progress() == 0 && machine.energy().getEnergyStored() == before,
                "Incomplete board consumed energy or started to craft");
        machine.assemblyGrid().setStackInSlot(4, refused);
        tick(h, machine, 7);
        h.assertTrue(machine.report().progress() == 7 && machine.energy().getEnergyStored() < before, "Board did not spend FE");
        var registry = h.getLevel().registryAccess();
        var saved = machine.saveWithoutMetadata(registry);
        machine.assemblyGrid().extractItem(9, 1, false);
        machine.loadWithComponents(saved, registry);
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++)
            h.assertTrue(ItemStack.matches(machine.assemblyGrid().getStackInSlot(i), board.get(i)), "Lost manual cell on load: " + i);
        tick(h, machine, recipe.time() - 7);
        assertProduct(h, machine, recipe);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void poweredAssemblerConsumesAllTwentyOneWormholeCoreParts(GameTestHelper h) {
        var recipe = recipe(h, "wormhole_generator_core");
        var machine = assembler(h);
        var board = parts(h, recipe);
        h.assertTrue(board.stream().filter(s -> !s.isEmpty()).count() == 21, "Expected a full board and no catalyst");
        loadBoard(machine, board);
        tick(h, machine, 2);
        h.assertTrue(machine.report().progress() == 0 && machine.items().getStackInSlot(TYPE.outputSlot()).isEmpty(), "Unpowered assembly advanced");
        machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
        tick(h, machine, recipe.time());
        assertProduct(h, machine, recipe);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void completeBoardNamesItsProductWhenOnlyTheCatalystIsMissing(GameTestHelper h) {
        for (var id : List.of("degenerate_furnace_frame", "degenerate_compactor_frame", "casimir_cavity_mirror_plate_pair")) {
            var intended = recipe(h, id);
            var board = parts(h, intended);
            board.set(MachineRecipe.ASSEMBLY_CELLS, ItemStack.EMPTY);
            var diagnosis = MachineDiagnosis.idle(h.getLevel(), TYPE, board, List.of(), -1);
            h.assertTrue(diagnosis.kind() == MachineDiagnosis.Kind.MISSING && diagnosis.lines().getFirst().equals(
                    net.minecraft.network.chat.Component.translatable("gui.singulo.reason.making", intended.result().getHoverName())),
                    "Missing catalyst was attributed to the wrong diagram for " + id + ": " + diagnosis.lines());
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "precision_assembly")
    public static void cavityFrameChargesOnlyTheSeparateCatalyst(GameTestHelper h) {
        var recipe = recipe(h, "casimir_cavity_frame");
        var machine = assembler(h);
        var board = parts(h, recipe);
        loadBoard(machine, board);
        machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
        tick(h, machine, 2);
        h.assertTrue(machine.report().progress() == 0, "Assembly started without a catalyst");
        var catalyst = board.get(MachineRecipe.ASSEMBLY_CELLS);
        long before = UsesHelper.remaining(catalyst);
        int cost = recipe.assemblyPlan(board).uses()[MachineRecipe.ASSEMBLY_CELLS];
        machine.items().setStackInSlot(TYPE.catalystSlot(), catalyst.copy());
        tick(h, machine, recipe.time());
        assertProduct(h, machine, recipe);
        h.assertTrue(cost > 0 && UsesHelper.remaining(machine.magazine().loaded()) == before - cost,
                "The loaded catalyst did not pay exactly one batch");
        h.succeed();
    }
}
