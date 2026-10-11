package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.recipe.*;
import io.github.genichimaruo.singulo.registry.*;
import io.github.genichimaruo.singulo.ruin.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Review-only acceptance tests; external source directory, no production edits. */
@GameTestHolder("singulo_review")
@PrefixGameTestTemplate(false)
public final class MergedFixReviewGameTests {
    private static ItemStack stack(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), count);
    }
    private static MachineRecipe recipe(GameTestHelper h, String path) {
        var holder = h.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()).stream()
                .filter(r -> r.id().equals(Singulo.id(path))).findFirst().orElseThrow();
        return RecipeTweaks.adjust(holder.id(), holder.value());
    }
    // Configuration cases run in separate batches. Invalidate only the recipe adjustment cache
    // so these instantaneous checks do not depend on its one-second wall-clock throttle.
    private static void invalidate() {
        try {
            var cache = RecipeTweaks.class.getDeclaredField("CACHE"); cache.setAccessible(true);
            ((Map<?, ?>) cache.get(null)).clear();
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    @GameTest(template = "empty", batch = "review_expert")
    public static void expertShellFitsTank(GameTestHelper h) {
        String old = ServerConfig.PRESET.get();
        try {
            ServerConfig.PRESET.set("expert"); invalidate();
            MachineRecipe r = recipe(h, "degenerate_compactor/degenerate_matter_shell");
            h.assertTrue(r.fluidIngredients().getFirst().amount() <= MachineType.TANK_CAPACITY,
                    "expert nitrogen=" + r.fluidIngredients().getFirst().amount() + ", tank=" + MachineType.TANK_CAPACITY);
            h.succeed();
        } finally { ServerConfig.PRESET.set(old); invalidate(); }
    }
    @GameTest(template = "empty", batch = "review_lifetime")
    public static void supportedLifetimeCanProduceModule(GameTestHelper h) {
        int old = ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.get();
        try {
            ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(20); invalidate();
            var crystal = stack("time_crystal_catalyst", 1);
            var r = recipe(h, "precision_assembler/time_crystal_module");
            h.assertTrue(r.plan(List.of(crystal, stack("optical_lattice_substrate", 1), stack("steel_plate", 2))) != null,
                    "fresh crystal=" + UsesHelper.remaining(crystal) + " ticks, module requires=" + r.ingredients().getFirst().uses());
            h.succeed();
        } finally { ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(old); invalidate(); }
    }
    @GameTest(template = "empty", batch = "review_strange")
    public static void zeroShellCoolantAppliesToAlternative(GameTestHelper h) {
        int old = ServerConfig.SHELL_COOLANT_MB.get();
        try {
            ServerConfig.SHELL_COOLANT_MB.set(0); invalidate();
            var r = recipe(h, "degenerate_compactor/degenerate_matter_shell_from_strange_matter");
            h.assertTrue(r.fluidIngredients().isEmpty(), "alternative ignores shellCoolantMb=0, nitrogen=" + r.fluidIngredients().stream().mapToInt(MachineRecipe.FluidInput::amount).sum());
            h.succeed();
        } finally { ServerConfig.SHELL_COOLANT_MB.set(old); invalidate(); }
    }
    @GameTest(template = "empty", batch = "review_vault")
    public static void awakenedVictoryOpensDueVault(GameTestHelper h) {
        var vp = new BlockPos(1, 1, 1); var cp = new BlockPos(4, 1, 4);
        h.setBlock(vp, SinguloBlocks.RUIN_CACHE.get());
        RuinCacheBlockEntity v = h.getBlockEntity(vp); v.setRuin("final_lab");
        var data = (net.minecraft.world.level.storage.ServerLevelData) h.getLevel().getLevelData();
        long oldTime = h.getLevel().getGameTime();
        boolean sealed;
        try {
            v.refillIfDue(h.getLevel(), oldTime); v.clearContent(); v.refillIfDue(h.getLevel(), oldTime);
            data.setGameTime(oldTime + v.regenTicks() + 1000);
            h.setBlock(cp, SinguloBlocks.SEAL_CONSOLE.get());
            SealConsoleBlockEntity c = h.getBlockEntity(cp);
            HorizonWarden w = c.activateAwakened(h.getLevel(), null);
            h.assertTrue(w != null, "warden missing"); w.skipEmerging();
            w.setHealth(0); w.die(h.getLevel().damageSources().generic());
            v.refillIfDue(h.getLevel(), h.getLevel().getGameTime());
            sealed = v.isSealed();
        } finally { data.setGameTime(oldTime); }
        h.assertFalse(sealed, "vault resealed immediately after awakened victory");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "review_anchor", timeoutTicks = 40)
    public static void loweredAnchorLimitIsEnforced(GameTestHelper h) {
        int old = ServerConfig.MAX_ANCHORS_PER_PLAYER.get();
        ServerConfig.MAX_ANCHORS_PER_PLAYER.set(3);
        var owner = UUID.randomUUID(); var anchors = new ArrayList<WorldlineAnchorBlockEntity>();
        for (int i = 0; i < 3; i++) {
            var p = new BlockPos(i * 2 + 1, 1, 2);
            h.setBlock(p, SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
            WorldlineAnchorBlockEntity a = h.getBlockEntity(p); a.setOwner(owner);
            a.catalystSlot().insertItem(0, stack("muon_catalyst", 1), false); anchors.add(a);
        }
        h.onEachTick(() -> anchors.forEach(a -> a.energy().setEnergy(a.energy().getMaxEnergyStored())));
        h.runAtTickTime(5, () -> {
            h.assertTrue(anchors.stream().filter(a -> a.loadedRadius() >= 0).count() == 3, "fixture did not start 3 anchors");
            ServerConfig.MAX_ANCHORS_PER_PLAYER.set(1);
        });
        h.runAtTickTime(15, () -> {
            try {
                long active = anchors.stream().filter(a -> a.loadedRadius() >= 0).count();
                h.assertTrue(active <= 1, "lowered limit=1, still active=" + active);
                h.succeed();
            } finally { ServerConfig.MAX_ANCHORS_PER_PLAYER.set(old); anchors.forEach(a -> a.onBroken(h.getLevel())); }
        });
    }
}
