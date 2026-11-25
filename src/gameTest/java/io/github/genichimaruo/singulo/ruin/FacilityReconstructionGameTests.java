package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class FacilityReconstructionGameTests {
    private FacilityReconstructionGameTests() {}

    private static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "rebuild-tester"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return TestScope.ownPlayer(helper, player);
    }

    @GameTest(template = "empty", batch = "facility_reconstruction", timeoutTicks = 400)
    public static void oldRotatedFacilityRequiresWarningAndRebuildsOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var manager = level.getStructureManager();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var ruin = registry.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, Singulo.id("final_lab"))).value();
        var oldTemplate = manager.get(Singulo.id("ruins/final_lab")).orElseThrow();
        var current = manager.get(Singulo.id("ruins/final_lab_antechamber")).orElseThrow();
        var settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90).setIgnoreEntities(true);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(120);
        BlockPos anchor = oldTemplate.filterBlocks(origin, settings, SinguloBlocks.SEAL_CONSOLE.get()).getFirst().pos();
        BlockPos vault = oldTemplate.filterBlocks(origin, settings, SinguloBlocks.RUIN_CACHE.get()).getFirst().pos();
        BlockPos newOrigin = anchor.subtract(current.filterBlocks(BlockPos.ZERO, settings, SinguloBlocks.SEAL_CONSOLE.get()).getFirst().pos());
        var bounds = current.getBoundingBox(settings, newOrigin);
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
        var oldElement = StructurePoolElement.single(Singulo.id("ruins/final_lab").toString()).apply(StructureTemplatePool.Projection.RIGID);
        var oldPiece = new PoolElementStructurePiece(manager, oldElement, origin, 0, settings.getRotation(),
                oldTemplate.getBoundingBox(settings, origin), LiquidSettings.APPLY_WATERLOGGING);
        ChunkPos startChunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        var start = new StructureStart(ruin, startChunk, 0, new PiecesContainer(List.of(oldPiece)));
        var serialization = net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
        var reloaded = StructureStart.loadStaticStart(serialization, start.createTag(serialization, startChunk), 0);
        helper.assertTrue(reloaded != null && reloaded.isValid(), "Old structure start could not be reloaded");
        level.getChunk(startChunk.x(), startChunk.z()).setStartForStructure(ruin, reloaded);
        level.getChunkAt(anchor).addReferenceForStructure(ruin, startChunk.pack());

        level.setBlock(anchor, SinguloBlocks.SEAL_CONSOLE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(vault, SinguloBlocks.RUIN_CACHE.get().defaultBlockState().setValue(RuinCacheBlock.SEALED, true), Block.UPDATE_CLIENTS);
        var cache = (RuinCacheBlockEntity) level.getBlockEntity(vault);
        cache.setRuin("final_lab");
        helper.assertTrue(RuinReconstruction.plan(level, anchor) == null, "Uncleared site eligible");
        cache.setSealed(false);
        cache.setItem(0, new ItemStack(Items.DIAMOND, 3));
        var oldCacheNbt = cache.saveWithoutMetadata(level.registryAccess());
        var currentCacheNbt = oldCacheNbt.copy();
        currentCacheNbt.putInt("reconstruction_revision", RuinReconstruction.REVISION);
        io.github.genichimaruo.singulo.test.harness.TestNbt.load(cache, currentCacheNbt, level.registryAccess());
        helper.assertTrue(RuinReconstruction.plan(level, anchor) == null, "Current generation eligible");
        io.github.genichimaruo.singulo.test.harness.TestNbt.load(cache, oldCacheNbt, level.registryAccess());
        helper.assertTrue(RuinReconstruction.plan(level, anchor) != null, "Eligible rotated old site not located");

        BlockPos newVault = current.filterBlocks(newOrigin, settings, SinguloBlocks.RUIN_CACHE.get()).getFirst().pos();
        var glass = current.filterBlocks(newOrigin, settings, SinguloBlocks.PRESSURE_GLASS.get()).getFirst().pos();
        level.setBlock(glass, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
        var player = player(helper);
        var item = SinguloItems.FACILITY_RECONSTRUCTOR.get();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var held = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
        var action = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(true);
        var mode = ServerConfig.MULTIPLAYER_ACCESS;
        var recipeEnabled = ServerConfig.RECIPE_ENABLED;
        var oldMode = mode.get();
        var oldRecipe = recipeEnabled.get();
        var list = level.getServer().getPlayerList();
        try {
            mode.set("admins_only");
            item.useOn(action);
            helper.assertTrue(level.getBlockState(glass).is(Blocks.OBSIDIAN) && held.getCount() == 1,
                    "Unprivileged player overwrote or consumed an item");
            mode.set("everyone");
            item.useOn(action);
            helper.assertTrue(level.getBlockState(glass).is(Blocks.OBSIDIAN) && held.getCount() == 1,
                    "Unconfirmed warning click overwrote world or consumed item");

            list.op(new net.minecraft.server.players.NameAndId(player.getGameProfile()), java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), java.util.Optional.empty());
            TestScope.of(helper).defer(() -> list.deop(new net.minecraft.server.players.NameAndId(player.getGameProfile())));
            helper.assertTrue(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && player.isSecondaryUseActive(), "Fixture did not grant operator permission or sneak");
            mode.set("disabled");
            item.useOn(action);
            helper.assertTrue(level.getBlockState(glass).is(Blocks.OBSIDIAN) && held.getCount() == 1,
                    "Disabled mode did not invalidate pending confirmation even for an administrator");

            // Already-owned items still work if the recipe is off.
            recipeEnabled.set(false);
            mode.set("admins_only");
            item.useOn(action);
            helper.assertTrue(level.getBlockState(glass).is(Blocks.OBSIDIAN),
                    "Denied click left a stale confirmation");
            item.useOn(action);
        } finally {
            recipeEnabled.set(oldRecipe);
            mode.set(oldMode);
        }
        helper.assertTrue(held.isEmpty() && level.getBlockState(glass).is(SinguloBlocks.PRESSURE_GLASS.get())
                        && level.getBlockState(anchor).is(SinguloBlocks.SEAL_CONSOLE.get()),
                "Confirmed rebuild failed: item=" + held.getCount() + ", glass=" + level.getBlockState(glass) + ", anchor=" + level.getBlockState(anchor));
        helper.assertTrue(level.getBlockEntity(newVault) instanceof RuinCacheBlockEntity moved &&
                !moved.isSealed() && moved.reconstructionRevision() == RuinReconstruction.REVISION
                && moved.getItem(0).is(Items.DIAMOND) && moved.getItem(0).getCount() == 3, "Vault contents or clear/revision state lost");
        helper.assertTrue(level.structureManager().getStructureWithPieceAt(glass, ruin).isValid(),
                "New outer shell cannot be found with saved structure references");
        helper.assertTrue(RuinReconstruction.plan(level, anchor) == null, "Reconstructed site can be used a second time");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "facility_reconstruction_android", timeoutTicks = 400)
    public static void oldAndroidBossRoomReusesTheSavedRotationAndAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var manager = level.getStructureManager();
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, Singulo.id("android_lab"))).value();
        var min = helper.absolutePos(BlockPos.ZERO).above(170);
        var rotation = Rotation.COUNTERCLOCKWISE_90;
        var template = manager.get(Singulo.id("lab/boss_hall")).orElseThrow();
        var settings = new StructurePlaceSettings().setRotation(rotation);
        var offsets = template.getBoundingBox(settings, BlockPos.ZERO);
        var origin = min.offset(-offsets.minX(), -offsets.minY(), -offsets.minZ());
        var box = template.getBoundingBox(settings, origin);
        var room = new io.github.genichimaruo.singulo.ruin.lab.LabPieces.Room(manager, "boss_hall", min, rotation, box);
        var anchor = template.filterBlocks(origin, settings, SinguloBlocks.GUARDIAN_CORE.get()).getFirst().pos();
        helper.assertTrue(room.isBossAnchor(level, anchor), "No deterministic Android boss core marker");
        var vault = room.bossCache(level);
        var chunk = new ChunkPos(anchor.getX() >> 4, anchor.getZ() >> 4);
        var start = new StructureStart(structure, chunk, 0, new PiecesContainer(List.of(room)));
        var serialization = net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
        var reloaded = StructureStart.loadStaticStart(serialization, start.createTag(serialization, chunk), 0);
        helper.assertTrue(reloaded != null && reloaded.isValid(), "Saved Android boss room could not be reloaded");
        var bounds = reloaded.getBoundingBox();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
        level.getChunk(chunk.x(), chunk.z()).setStartForStructure(structure, reloaded);
        level.getChunkAt(anchor).addReferenceForStructure(structure, chunk.pack());

        level.setBlock(anchor, SinguloBlocks.GUARDIAN_CORE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(vault, SinguloBlocks.RUIN_CACHE.get().defaultBlockState().setValue(RuinCacheBlock.SEALED, true),
                Block.UPDATE_CLIENTS);
        var cache = (RuinCacheBlockEntity) level.getBlockEntity(vault);
        cache.setRuin("android_lab");
        cache.setItem(0, new ItemStack(Items.EMERALD, 2));
        var plan = RuinReconstruction.plan(level, anchor, true);
        helper.assertTrue(plan != null && plan.template() == null && plan.sealed()
                && RuinReconstruction.plan(level, anchor) == null,
                "Undefeated Android boss room not restricted to Creative");

        var player = player(helper);
        var item = SinguloItems.FACILITY_RECONSTRUCTOR.get();
        var held = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
        var action = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        var policy = ServerConfig.MULTIPLAYER_ACCESS;
        var oldPolicy = policy.get();
        try {
            policy.set("everyone");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            helper.assertTrue(!player.isCreative(), "Survival policy test fixture is still Creative");
            item.useOn(action);
            helper.assertTrue(RuinReconstruction.plan(level, anchor, true) != null, "Survival applied to an undefeated facility");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.setShiftKeyDown(true);
            helper.assertTrue(player.isCreative() && !net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()), "Creative policy test fixture is not a non-op");
            item.useOn(action);
            helper.assertTrue(RuinReconstruction.plan(level, anchor, true) != null, "Creative skipped the warning");
            item.useOn(action);
        } finally {
            policy.set(oldPolicy);
        }
        helper.assertTrue(level.getBlockState(anchor).is(SinguloBlocks.GUARDIAN_CORE.get())
                        && level.getBlockEntity(vault) instanceof RuinCacheBlockEntity updated
                        && updated.getItem(0).is(Items.EMERALD) && updated.getItem(0).getCount() == 2
                        && updated.reconstructionRevision() == RuinReconstruction.REVISION && updated.isSealed(),
                "Creative rebuild did not preserve original anchor and locked vault");
        helper.assertTrue(held.getCount() == 1 && RuinReconstruction.plan(level, anchor, true) == null,
                "Creative item was consumed or Android lab can rebuild twice");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "facility_reconstruction_recipe", timeoutTicks = 100)
    public static void recipeTogglesAtCraftingEntry(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var actuator = new ItemStack(registry.getValue(Singulo.id("precision_actuator")));
        var memory = new ItemStack(registry.getValue(Singulo.id("crystal_memory")));
        var copper = new ItemStack(Items.COPPER_BLOCK);
        var diamond = new ItemStack(Items.DIAMOND);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
                List.of(actuator, copper, actuator, diamond, memory, diamond, actuator, copper, actuator));
        var type = net.minecraft.world.item.crafting.RecipeType.CRAFTING;
        var config = ServerConfig.RECIPE_ENABLED;
        var original = config.get();
        try {
            config.set(true);
            var recipe = level.getServer().getRecipeManager().getRecipeFor(type, input, level).orElseThrow();
            helper.assertTrue(recipe.id().identifier().equals(Singulo.id("facility_reconstructor"))
                    && recipe.value().assemble(input).is(SinguloItems.FACILITY_RECONSTRUCTOR.get()),
                    "Configured recipe did not craft the expected device");
            config.set(false);
            helper.assertTrue(level.getServer().getRecipeManager().getRecipeFor(type, input, level).isEmpty()
                    && recipe.value().assemble(input).isEmpty(),
                    "Disabled recipe survived fresh lookup or cached direct assembly");
            config.set(true);
            helper.assertTrue(level.getServer().getRecipeManager().getRecipeFor(type, input, level).isPresent(),
                    "Re-enabled recipe did not return");
        } finally {
            config.set(original);
        }
        helper.succeed();
    }

}
