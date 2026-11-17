package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.LegacyIds;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 0.20.0 で改名した登録 ID: 古いワールドに残った古い ID が、新しい ID のブロック・アイテム・ブロックエンティティ・レシピになる。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class LegacyIdGameTests {
    private static <T> void resolves(GameTestHelper helper, Registry<T> registry, Map<String, String> renames) {
        renames.forEach((from, to) -> {
            T current = registry.getOptional(Singulo.id(to)).orElse(null);
            helper.assertTrue(current != null, "New id is not registered: " + to);
            helper.assertTrue(registry.getOptional(Singulo.id(from)).orElse(null) == current, "Old id does not resolve: " + from + " -> " + to);
            helper.assertTrue(Singulo.id(to).equals(registry.getKey(current)), "Renamed entry keeps an old key: " + to);
        });
    }

    @GameTest(template = "empty")
    public static void oldRegistryIdsLoadAsTheirNewIds(GameTestHelper helper) {
        resolves(helper, BuiltInRegistries.BLOCK, LegacyIds.BLOCKS);
        resolves(helper, BuiltInRegistries.ITEM, LegacyIds.BLOCKS);
        resolves(helper, BuiltInRegistries.ITEM, LegacyIds.ITEMS);
        resolves(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE, LegacyIds.BLOCK_ENTITIES);

        // 持ち物・箱の中身（保存された ItemStack）
        var registries = helper.getLevel().registryAccess();
        CompoundTag stackTag = new CompoundTag();
        stackTag.putString("id", "singulo:record_fragment");
        stackTag.putByte("Count", (byte) 3);
        ItemStack stack = ItemStack.of(stackTag);
        helper.assertTrue(stack.getCount() == 3 && Singulo.id("crystal_memory").equals(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                "Saved stack with an old id was not migrated: " + stack);

        // チャンクに残ったブロックエンティティ（古い種類の ID）
        var controller = BuiltInRegistries.BLOCK.get(Singulo.id("reactor_core_controller")).defaultBlockState();
        CompoundTag beTag = new CompoundTag();
        beTag.putString("id", "singulo:penrose_reactor");
        BlockEntity be = BlockEntity.loadStatic(helper.absolutePos(new BlockPos(1, 1, 1)), controller, beTag);
        helper.assertTrue(be != null && be.getType() == BuiltInRegistries.BLOCK_ENTITY_TYPE.get(Singulo.id("reactor_core_controller")),
                "Saved block entity with an old type id was not loaded");

        // 保存されていた選択中のレシピ
        var recipes = helper.getLevel().getRecipeManager();
        LegacyIds.RECIPES.forEach((from, to) -> {
            helper.assertTrue(recipes.byKey(Singulo.id(to)).isPresent(), "Renamed recipe is missing: " + to);
            helper.assertTrue(recipes.byKey(Singulo.id(from)).isEmpty(), "Old recipe id still exists: " + from);
            helper.assertTrue(Singulo.id(to).equals(LegacyIds.recipe(Singulo.id(from))), "Old recipe id is not mapped: " + from);
        });
        helper.assertTrue(Singulo.id("crafting/kiln").equals(LegacyIds.recipe(Singulo.id("crafting/kiln"))), "Unrenamed recipe changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldWorldRegistrySnapshotsRemapWithoutLosingNumericIds(GameTestHelper helper) throws ReflectiveOperationException {
        snapshot(helper, net.minecraftforge.registries.ForgeRegistries.BLOCKS, LegacyIds.BLOCKS);
        var items = new java.util.HashMap<>(LegacyIds.BLOCKS);
        items.putAll(LegacyIds.ITEMS);
        snapshot(helper, net.minecraftforge.registries.ForgeRegistries.ITEMS, items);
        snapshot(helper, net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES, LegacyIds.BLOCK_ENTITIES);
        helper.succeed();
    }

    private static <T> void snapshot(GameTestHelper helper, net.minecraftforge.registries.IForgeRegistry<T> current,
            Map<String, String> renames) throws ReflectiveOperationException {
        var active = (net.minecraftforge.registries.ForgeRegistry<T>) current;
        var snapshot = active.makeSnapshot();
        // Reconstruct the pre-0.20 registry names in a saved snapshot, retaining numeric IDs.
        renames.forEach((from, to) -> {
            int id = snapshot.ids.remove(Singulo.id(to));
            snapshot.ids.put(Singulo.id(from), id);
        });
        snapshot.aliases.clear();
        var stagingManager = new net.minecraftforge.registries.RegistryManager("STAGING");
        // Item registry creation shares the block registry's block-to-item slave map.
        stagingManager.getRegistry(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getRegistryName(),
                net.minecraftforge.registries.RegistryManager.ACTIVE);
        net.minecraftforge.registries.ForgeRegistry<T> staging = stagingManager.getRegistry(active.getRegistryName(),
                net.minecraftforge.registries.RegistryManager.ACTIVE);
        var missing = new it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap<net.minecraft.resources.ResourceLocation>();
        var remaps = new java.util.HashMap<net.minecraft.resources.ResourceLocation, net.minecraftforge.registries.IdMappingEvent.IdRemapping>();
        staging.loadIds(snapshot.ids, snapshot.overrides, missing, remaps, active, active.getRegistryName());
        helper.assertTrue(missing.size() == renames.size(), "Old snapshot did not exercise the missing-mapping path");
        var event = staging.getMissingEvent(active.getRegistryName(), missing);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        var defaulted = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var failed = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var process = java.util.Arrays.stream(net.minecraftforge.registries.ForgeRegistry.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("processMissingEvent") && method.getParameterCount() == 8)
                .findFirst().orElseThrow();
        process.setAccessible(true);
        process.invoke(staging, active.getRegistryName(), active, event.getAllMappings(active.getRegistryKey()),
                missing, remaps, defaulted, failed, false);
        helper.assertTrue(missing.isEmpty() && defaulted.isEmpty() && failed.isEmpty(), "Old world entries were lost or defaulted");
        renames.forEach((from, to) -> {
            T target = active.getValue(Singulo.id(to));
            helper.assertTrue(staging.getValue(Singulo.id(from)) == target, "Old snapshot key does not resolve: " + from);
            helper.assertTrue(staging.getID(target) == snapshot.ids.get(Singulo.id(from)), "Saved numeric ID changed: " + from);
        });
    }
}
