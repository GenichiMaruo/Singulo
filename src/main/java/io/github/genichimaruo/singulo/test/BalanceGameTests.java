package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.QuantumHeatEngineBlockEntity;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** 0.3.1 の調整（時間結晶モジュール・満充電の待機・アンカーの上限・設定の当てはめ）の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BalanceGameTests {
    private static final String EMPTY = "empty";

    private BalanceGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static MachineBlockEntity assembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity m = helper.getBlockEntity(pos);
        helper.onEachTick(() -> m.energy().setEnergy(m.energy().getMaxEnergyStored()));
        return m;
    }

    /** 搬入（ホッパーやパイプと同じ口）から入れる。入り切らなければ失敗。 */
    private static void insert(GameTestHelper helper, IItemHandler handler, ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < handler.getSlots() && !rest.isEmpty(); i++) {
            rest = handler.insertItem(i, rest, false);
        }
        helper.assertTrue(rest.isEmpty(), "搬入で入らない: " + stack);
    }

    // ------------------------------------------------------------------ 時間結晶モジュール（複数の時間結晶を要る設備）

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void coreControllerFromModulesByNormalInsertion(GameTestHelper helper) {
        MachineBlockEntity m = assembler(helper, new BlockPos(2, 1, 2));
        IItemHandler auto = m.automationItems();
        insert(helper, auto, new ItemStack(item("nihonium_frame")));
        insert(helper, auto, new ItemStack(item("quantum_computing_module"), 2));
        insert(helper, auto, new ItemStack(item("time_crystal_module"), 4));
        insert(helper, auto, new ItemStack(item("gravity_field_stabilizer"), 2));
        helper.succeedWhen(() -> helper.assertTrue(m.items().getStackInSlot(m.type().outputSlot()).is(item("core_controller")),
                "時間結晶モジュール4個を普通に入れて炉心制御装置ができない"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void timeCrystalModuleNeedsThirtyMinutesOfLife(GameTestHelper helper) {
        MachineBlockEntity fresh = assembler(helper, new BlockPos(1, 1, 1));
        MachineBlockEntity weak = assembler(helper, new BlockPos(5, 1, 5));
        ItemStack good = new ItemStack(item("time_crystal_catalyst"));
        int max = UsesHelper.get(good).max();
        ItemStack worn = new ItemStack(item("time_crystal_catalyst"));
        worn.set(SinguloComponents.USES.get(), new UsesData(max / 2, max, 0));      // 残り半分（22.5分）
        for (MachineBlockEntity m : List.of(fresh, weak)) {
            m.items().insertItem(0, new ItemStack(item("optical_lattice_substrate")), false);
            m.items().insertItem(1, new ItemStack(item("steel_plate"), 2), false);
        }
        helper.assertTrue(fresh.items().insertItem(fresh.type().catalystSlot(), good, false).isEmpty(), "触媒枠に入らない");
        helper.assertTrue(weak.items().insertItem(weak.type().catalystSlot(), worn, false).isEmpty(), "触媒枠に入らない");
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(fresh.items().getStackInSlot(fresh.type().outputSlot()).is(item("time_crystal_module")),
                    "新品の時間結晶からモジュールができない");
            ItemStack left = fresh.catalyst();
            helper.assertTrue(left.is(item("time_crystal_catalyst")) && UsesHelper.remaining(left) == max - 36_000,
                    "切り出したあとに触媒が15分ぶん残っていない: " + UsesHelper.remaining(left));
            helper.assertTrue(weak.items().getStackInSlot(weak.type().outputSlot()).isEmpty(),
                    "寿命が30分に満たない時間結晶からモジュールができた");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 満充電の待機

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void fullGeneratorWaitsWithoutWearingCatalyst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.QUANTUM_HEAT_ENGINE.get());
        QuantumHeatEngineBlockEntity engine = helper.getBlockEntity(pos);
        engine.energy().setEnergy(engine.energy().getMaxEnergyStored());
        engine.catalystSlot().insertItem(0, new ItemStack(item("bose_condensate_catalyst")), false);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(engine.status() == CatalystDeviceBlockEntity.Status.STANDBY, "満充電なのに待機しない: " + engine.status());
            helper.assertTrue(UsesHelper.get(engine.catalyst()).used() == 0, "待機中に触媒が減った");
            // 蓄電が減ると再開する
            engine.energy().setEnergy(0);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(engine.status() == CatalystDeviceBlockEntity.Status.RUNNING, "蓄電が減っても再開しない: " + engine.status());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ アンカーの上限

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void anchorsPerPlayerAreLimited(GameTestHelper helper) {
        int limit = WorldlineAnchorBlockEntity.anchorLimit();
        if (limit <= 0 || limit > 8) {
            helper.succeed();                                 // 既定（8）以外の設定では確かめない
            return;
        }
        UUID owner = UUID.randomUUID();
        List<WorldlineAnchorBlockEntity> anchors = new ArrayList<>();
        for (int i = 0; i < limit + 1; i++) {
            BlockPos pos = new BlockPos(1 + (i % 3) * 2, 1 + (i / 9) * 2, 1 + (i / 3 % 3) * 2);
            helper.setBlock(pos, SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
            WorldlineAnchorBlockEntity a = helper.getBlockEntity(pos);
            a.setOwner(owner);
            a.catalystSlot().insertItem(0, new ItemStack(item("muon_catalyst")), false);
            anchors.add(a);
        }
        helper.onEachTick(() -> anchors.forEach(a -> a.energy().setEnergy(a.energy().getMaxEnergyStored())));
        helper.runAtTickTime(10, () -> {
            long running = anchors.stream().filter(a -> a.status() == CatalystDeviceBlockEntity.Status.RUNNING).count();
            long limited = anchors.stream().filter(a -> a.status() == CatalystDeviceBlockEntity.Status.LIMITED).count();
            helper.assertTrue(running == limit && limited == 1, "上限 " + limit + " で動いた数 " + running + "、止められた数 " + limited);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 設定の当てはめ

    /** 設定が既定値のままなら、データパックのレシピをそのまま使う（JSONと同じもの）。 */
    @GameTest(template = EMPTY)
    public static void defaultSettingsKeepDatapackRecipes(GameTestHelper helper) {
        for (RecipeHolder<MachineRecipe> h : helper.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            String path = h.id().getPath();
            if (path.equals("degenerate_compactor/reactor_shell") || path.equals("degenerate_compactor/degenerate_matter_shell")
                    || path.equals("casimir_cavity/exotic_matter")) {
                helper.assertTrue(RecipeTweaks.adjust(h.id(), h.value()) == h.value(), "既定の設定なのにレシピが変わった: " + path);
            }
            if (path.equals("degenerate_compactor/reactor_shell")) {
                helper.assertTrue(h.value().result().getCount() == 24, "殻1個から炉殻24枚にならない");
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ マージ後レビュー（SNG-021〜025）の再現ケース

    private static MachineRecipe adjusted(GameTestHelper helper, String path) {
        RecipeTweaks.invalidate();
        for (RecipeHolder<MachineRecipe> h : helper.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            if (h.id().equals(Singulo.id(path))) {
                return RecipeTweaks.adjust(h.id(), h.value());
            }
        }
        throw new IllegalStateException("レシピがない: " + path);
    }

    private static int nitrogen(MachineRecipe r) {
        return r.fluidIngredients().stream().mapToInt(MachineRecipe.FluidInput::amount).sum();
    }

    /** SNG-021: expert でも通常の殻の冷却材がタンク1つ（8,000 mB）に収まる。 */
    @GameTest(template = EMPTY, batch = "balance_expert")
    public static void expertShellFitsTank(GameTestHelper helper) {
        String old = ServerConfig.PRESET.get();
        try {
            ServerConfig.PRESET.set("expert");
            MachineRecipe r = adjusted(helper, "degenerate_compactor/degenerate_matter_shell");
            helper.assertTrue(nitrogen(r) > 0 && nitrogen(r) <= MachineType.TANK_CAPACITY,
                    "expert の殻の窒素がタンクに収まらない: " + nitrogen(r));
            MachineRecipe strange = adjusted(helper, "degenerate_compactor/degenerate_matter_shell_from_strange_matter");
            helper.assertTrue(nitrogen(strange) <= MachineType.TANK_CAPACITY, "expert のストレンジ物質版がタンクに収まらない");
        } finally {
            ServerConfig.PRESET.set(old);
            RecipeTweaks.invalidate();
        }
        helper.succeed();
    }

    /** SNG-022: 寿命を短くする設定でも、新品の時間結晶からモジュールを作れる（低純度品は作れないまま）。 */
    @GameTest(template = EMPTY, batch = "balance_lifetime")
    public static void supportedLifetimeCanProduceModule(GameTestHelper helper) {
        int old = ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.get();
        try {
            ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(20);
            MachineRecipe r = adjusted(helper, "precision_assembler/time_crystal_module");
            ItemStack fresh = new ItemStack(item("time_crystal_catalyst"));
            helper.assertTrue(r.plan(List.of(fresh, new ItemStack(item("optical_lattice_substrate")), new ItemStack(item("steel_plate"), 2))) != null,
                    "寿命20分の新品からモジュールを作れない: 新品 " + UsesHelper.remaining(fresh));
            ItemStack worn = new ItemStack(item("time_crystal_catalyst"));
            int max = UsesHelper.get(worn).max();
            worn.set(SinguloComponents.USES.get(), new UsesData(max / 2, max, 0));
            helper.assertTrue(r.plan(List.of(worn, new ItemStack(item("optical_lattice_substrate")), new ItemStack(item("steel_plate"), 2))) == null,
                    "残り半分の結晶からモジュールができてしまう");
        } finally {
            ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(old);
            RecipeTweaks.invalidate();
        }
        helper.succeed();
    }

    /** SNG-025: shellNitrogenMb はストレンジ物質版にも（半分として）効く。0 なら窒素は要らない。 */
    @GameTest(template = EMPTY, batch = "balance_strange")
    public static void zeroShellCoolantAppliesToAlternative(GameTestHelper helper) {
        int old = ServerConfig.SHELL_NITROGEN_MB.get();
        try {
            ServerConfig.SHELL_NITROGEN_MB.set(0);
            helper.assertTrue(adjusted(helper, "degenerate_compactor/degenerate_matter_shell_from_strange_matter").fluidIngredients().isEmpty(),
                    "shellNitrogenMb=0 でもストレンジ物質版に窒素が要る");
            ServerConfig.SHELL_NITROGEN_MB.set(4000);
            helper.assertTrue(nitrogen(adjusted(helper, "degenerate_compactor/degenerate_matter_shell_from_strange_matter")) == 2000,
                    "shellNitrogenMb=4000 でストレンジ物質版が半分の2000 mBにならない");
        } finally {
            ServerConfig.SHELL_NITROGEN_MB.set(old);
            RecipeTweaks.invalidate();
        }
        helper.succeed();
    }

    /** SNG-023: 再生期限が過ぎた保管庫でも、覚醒戦に勝てば開けられる。 */
    @GameTest(template = EMPTY, batch = "balance_vault")
    public static void awakenedVictoryOpensDueVault(GameTestHelper helper) {
        BlockPos vaultPos = new BlockPos(1, 1, 1);
        BlockPos consolePos = new BlockPos(4, 1, 4);
        helper.setBlock(vaultPos, SinguloBlocks.RUIN_CACHE.get());
        RuinCacheBlockEntity vault = helper.getBlockEntity(vaultPos);
        vault.setRuin("final_lab");
        var data = (net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData();
        long oldTime = helper.getLevel().getGameTime();
        boolean sealed;
        try {
            vault.refillIfDue(helper.getLevel(), oldTime);
            vault.clearContent();
            vault.refillIfDue(helper.getLevel(), oldTime);
            data.setGameTime(oldTime + vault.regenTicks() + 1000);
            helper.setBlock(consolePos, SinguloBlocks.SEAL_CONSOLE.get());
            SealConsoleBlockEntity console = helper.getBlockEntity(consolePos);
            HorizonWarden warden = console.activateAwakened(helper.getLevel(), null);
            helper.assertTrue(warden != null, "覚醒した守護機が起きない");
            warden.skipEmerging();
            warden.setHealth(0);
            warden.die(helper.getLevel().damageSources().generic());
            vault.refillIfDue(helper.getLevel(), helper.getLevel().getGameTime());
            sealed = vault.isSealed();
        } finally {
            data.setGameTime(oldTime);
        }
        helper.assertFalse(sealed, "覚醒戦に勝った直後に保管庫が封鎖し直された");
        helper.succeed();
    }

    /** SNG-024: 上限を下げると、動いているアンカーも後から動き出した順に止まる。 */
    @GameTest(template = EMPTY, batch = "balance_anchor", timeoutTicks = 40)
    public static void loweredAnchorLimitIsEnforced(GameTestHelper helper) {
        int old = ServerConfig.MAX_ANCHORS_PER_PLAYER.get();
        ServerConfig.MAX_ANCHORS_PER_PLAYER.set(3);
        UUID owner = UUID.randomUUID();
        List<WorldlineAnchorBlockEntity> anchors = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlockPos p = new BlockPos(i * 2 + 1, 1, 2);
            helper.setBlock(p, SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
            WorldlineAnchorBlockEntity a = helper.getBlockEntity(p);
            a.setOwner(owner);
            a.catalystSlot().insertItem(0, new ItemStack(item("muon_catalyst")), false);
            anchors.add(a);
        }
        helper.onEachTick(() -> anchors.forEach(a -> a.energy().setEnergy(a.energy().getMaxEnergyStored())));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(anchors.stream().filter(a -> a.loadedRadius() >= 0).count() == 3, "3台が動き出さない");
            ServerConfig.MAX_ANCHORS_PER_PLAYER.set(1);
        });
        helper.runAtTickTime(15, () -> {
            try {
                long active = anchors.stream().filter(a -> a.loadedRadius() >= 0).count();
                helper.assertTrue(active == 1, "上限1に下げても動いている数が " + active);
                helper.succeed();
            } finally {
                ServerConfig.MAX_ANCHORS_PER_PLAYER.set(old);
                anchors.forEach(a -> a.onBroken(helper.getLevel()));
            }
        });
    }
}
