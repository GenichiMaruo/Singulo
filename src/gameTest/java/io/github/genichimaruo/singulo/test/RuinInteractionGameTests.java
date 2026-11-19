package io.github.genichimaruo.singulo.test;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.machine.ProbeStationBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.GuardianCoreBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RuinInteractionGameTests {
    private static final BlockPos DEVICE = new BlockPos(1, 1, 1);
    private static final BlockPos VAULT = new BlockPos(2, 1, 1);

    private static ServerPlayer survivor(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        TestScope.ownPlayer(helper, player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    private static void use(GameTestHelper helper, BlockPos pos, ServerPlayer player, ItemStack held) {
        BlockPos abs = helper.absolutePos(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.getBlockState(pos).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static RuinCacheBlockEntity vault(GameTestHelper helper, String ruin) {
        helper.setBlock(VAULT, SinguloBlocks.RUIN_CACHE.get());
        var cache = (RuinCacheBlockEntity) helper.getBlockEntity(VAULT);
        cache.setRuin(ruin);
        return cache;
    }

    @GameTest(template = "empty")
    public static void anchorRequiresConfirmationFromTheSamePlayer(GameTestHelper helper) {
        helper.setBlock(DEVICE, SinguloBlocks.WORLDLINE_ANCHOR_ADVANCED.get());
        TestScope.of(helper).defer(() -> helper.setBlock(DEVICE, Blocks.AIR));
        var anchor = (AdvancedWorldlineAnchorBlockEntity) helper.getBlockEntity(DEVICE);
        var first = survivor(helper, "AnchorFirst");
        var second = survivor(helper, "AnchorSecond");
        ItemStack firstCore = stack("singularity_core");
        ItemStack secondCore = stack("singularity_core");
        use(helper, DEVICE, first, firstCore);
        use(helper, DEVICE, second, secondCore);
        helper.assertTrue(!anchor.embedded() && firstCore.getCount() == 1 && secondCore.getCount() == 1,
                "One player's confirmation consumed another player's core");
        use(helper, DEVICE, first, firstCore);
        helper.assertTrue(anchor.embedded() && firstCore.isEmpty() && secondCore.getCount() == 1,
                "The confirming player must pay for permanent embedding");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "peaceful_ruin_bosses")
    public static void peacefulRuinsKeepSummoningItems(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = survivor(helper, "PeacefulVault");
        var previous = level.getDifficulty();
        try {
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            var cache = vault(helper, "research_building");
            cache.setSealed(true);
            helper.setBlock(DEVICE, SinguloBlocks.GUARDIAN_CORE.get());
            var core = (GuardianCoreBlockEntity) helper.getBlockEntity(DEVICE);
            helper.assertTrue(core.awaken(level) == null, "The automatic guardian spawned on Peaceful");
            ItemStack awakening = stack("awakening_echo_core");
            use(helper, DEVICE, player, awakening);
            helper.assertTrue(awakening.getCount() == 1 && core.activeBoss(level) == null,
                    "Guardian summoning spent an awakening core on Peaceful");

            cache.setRuin("final_lab");
            helper.setBlock(DEVICE, SinguloBlocks.SEAL_CONSOLE.get());
            var console = (SealConsoleBlockEntity) helper.getBlockEntity(DEVICE);
            helper.assertTrue(console.activate(level, player) == null, "The warden spawned on Peaceful");
            for (var item : new ItemStack[]{stack("awakening_record_core"), stack("terminus_record")}) {
                use(helper, DEVICE, player, item);
                helper.assertTrue(item.getCount() == 1 && console.activeWarden(level) == null
                                && console.activeArchivist(level) == null,
                        "The seal console consumed a summoning item on Peaceful");
            }
        } finally {
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "probe_loot_multiplier")
    public static void probeYieldUsesTheConfiguredExpeditionLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        var cache = vault(helper, "observation_post");
        helper.setBlock(DEVICE, SinguloBlocks.PROBE_STATION.get());
        var probe = (ProbeStationBlockEntity) helper.getBlockEntity(DEVICE);
        var player = TestScope.player(helper);
        RuinDiscovery.record(player, cache.ruin(), cache.getBlockPos());
        probe.registerFrom(player);
        double oldLoot = ServerConfig.LOOT_COUNT_MULTIPLIER.get();
        double oldYield = ServerConfig.PROBE_YIELD_MULTIPLIER.get();
        try {
            ServerConfig.LOOT_COUNT_MULTIPLIER.set(2.0);
            ServerConfig.PROBE_YIELD_MULTIPLIER.set(1.0);
            cache.refillIfDue(level, level.getGameTime());
            probe.completeMission(level, level.getGameTime());
            var unit = stack("degraded_control_unit").getItem();
            int manual = cache.countItem(unit);
            int automated = 0;
            for (int i = 0; i < probe.output().getSlots(); i++) {
                var stack = probe.output().getStackInSlot(i);
                if (stack.is(unit)) automated += stack.getCount();
            }
            // The base pool rolls 6–10 units; doubling it makes these ranges disjoint.
            helper.assertTrue(manual >= 12 && manual <= 20 && automated >= 12 && automated <= 20,
                    "Expected 12–20 units from both expeditions, got " + manual + " and " + automated);
        } finally {
            ServerConfig.LOOT_COUNT_MULTIPLIER.set(oldLoot);
            ServerConfig.PROBE_YIELD_MULTIPLIER.set(oldYield);
        }
        helper.succeed();
    }
}
