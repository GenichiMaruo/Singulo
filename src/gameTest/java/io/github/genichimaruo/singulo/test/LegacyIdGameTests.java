package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.LegacyIds;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

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
        // The block-state palette decoder used by old chunks must resolve old block IDs too.
        CompoundTag paletteEntry = new CompoundTag();
        paletteEntry.putString("Name", "singulo:core_controller");
        var oldState = net.minecraft.nbt.NbtUtils.readBlockState(
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), paletteEntry);
        helper.assertTrue(oldState.is(io.github.genichimaruo.singulo.registry.SinguloBlocks.CORE_CONTROLLER.get()),
                "Old chunk palette block ID was not migrated");
        CompoundTag stackTag = new CompoundTag();
        stackTag.putString("id", "singulo:record_fragment");
        stackTag.putInt("count", 3);
        ItemStack stack = ItemStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stackTag).getOrThrow();
        helper.assertTrue(stack.getCount() == 3 && Singulo.id("crystal_memory").equals(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                "Saved stack with an old id was not migrated: " + stack);

        // チャンクに残ったブロックエンティティ（古い種類の ID）
        var controller = BuiltInRegistries.BLOCK.getValue(Singulo.id("reactor_core_controller")).defaultBlockState();
        CompoundTag beTag = new CompoundTag();
        beTag.putString("id", "singulo:penrose_reactor");
        BlockEntity be = BlockEntity.loadStatic(helper.absolutePos(new BlockPos(1, 1, 1)), controller, beTag, registries);
        helper.assertTrue(be != null && be.getType() == BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Singulo.id("reactor_core_controller")),
                "Saved block entity with an old type id was not loaded");

        // 保存されていた選択中のレシピ
        var recipes = helper.getLevel().getServer().getRecipeManager();
        LegacyIds.RECIPES.forEach((from, to) -> {
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Singulo.id(to))).isPresent(), "Renamed recipe is missing: " + to);
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Singulo.id(from))).isEmpty(), "Old recipe id still exists: " + from);
            helper.assertTrue(Singulo.id(to).equals(LegacyIds.recipe(Singulo.id(from))), "Old recipe id is not mapped: " + from);
        });
        helper.assertTrue(Singulo.id("crafting/kiln").equals(LegacyIds.recipe(Singulo.id("crafting/kiln"))), "Unrenamed recipe changed");
        helper.succeed();
    }
}
