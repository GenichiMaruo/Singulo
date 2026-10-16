package io.github.genichimaruo.singulo.compat;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

public final class Legacy {
    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key, 10) ? Optional.of(net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound(key))) : Optional.empty();
    }
    public static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy(); copy.setAmount(amount); return copy;
    }
    @SuppressWarnings("unchecked")
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> T blockEntity(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos pos) {
        return (T)helper.getBlockEntity(pos);
    }
    public static java.util.UUID uuid(net.minecraft.resources.ResourceLocation id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean sameFluid(net.minecraftforge.fluids.FluidStack a, net.minecraftforge.fluids.FluidStack b) { return a.isFluidEqual(b); }
    private static final java.util.List<FixtureScope> FIXTURES = new java.util.ArrayList<>();
    private static boolean fixtureTracking;
    private static final class FixtureScope {
        final net.minecraft.server.level.ServerLevel level;
        final net.minecraft.world.phys.AABB bounds;
        final net.minecraft.gametest.framework.GameTestInfo info;
        final java.util.Set<net.minecraft.world.entity.Entity> entities =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        FixtureScope(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.AABB bounds,
                     net.minecraft.gametest.framework.GameTestInfo info) {
            this.level = level; this.bounds = bounds; this.info = info;
        }
    }
    private static void trackFixtureEntity(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        var entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        for (var fixture : FIXTURES) {
            if (event.getLevel() == fixture.level && (fixture.bounds.contains(entity.position())
                    || entity instanceof net.minecraft.world.entity.projectile.Projectile projectile
                    && fixture.entities.contains(projectile.getOwner()))) {
                fixture.entities.add(entity);
                return;
            }
        }
        // Single-test batches can deliberately spawn helpers outside the structure.
        // There is no other live fixture in this disposable GameTest level to own them.
        FixtureScope sole = null;
        for (var fixture : FIXTURES) if (event.getLevel() == fixture.level) {
            if (sole != null) return;
            sole = fixture;
        }
        if (sole != null) sole.entities.add(entity);
    }
    /** The batch runner's listener starts the next batch synchronously: cleanup must run first. */
    @SuppressWarnings("unchecked")
    private static void addCleanupListener(net.minecraft.gametest.framework.GameTestInfo info,
                                           net.minecraft.gametest.framework.GameTestListener listener)
            throws ReflectiveOperationException {
        var field = java.util.Arrays.stream(net.minecraft.gametest.framework.GameTestInfo.class.getDeclaredFields())
                .filter(f -> f.getGenericType() instanceof java.lang.reflect.ParameterizedType type
                        && java.util.Arrays.asList(type.getActualTypeArguments()).contains(net.minecraft.gametest.framework.GameTestListener.class))
                .findFirst().orElseThrow();
        field.setAccessible(true);
        ((java.util.List<net.minecraft.gametest.framework.GameTestListener>) field.get(info)).add(0, listener);
    }
    /** Completed machines and hostiles must not affect later fixtures in their search radius. */
    public static void isolateFixture(net.minecraft.gametest.framework.GameTestHelper helper) {
        isolateFixture(helper, 0);
    }
    /** Include a test arena extending beyond its structure in chunk readiness and cleanup. */
    public static void isolateFixture(net.minecraft.gametest.framework.GameTestHelper helper, int horizontalMargin) {
        try {
            var field = java.util.Arrays.stream(net.minecraft.gametest.framework.GameTestHelper.class.getDeclaredFields())
                    .filter(f -> f.getType() == net.minecraft.gametest.framework.GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            var info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
            // Vanilla returns the last block coordinate, while AABB.contains uses
            // an exclusive upper bound. Include the final row/ceiling of blocks.
            var bounds = info.getStructureBounds().expandTowards(1, 1, 1).inflate(horizontalMargin, 0, horizontalMargin);
            // GameTestServer advances ticks without a real-time delay. A fixed setupTicks
            // does not guarantee that asynchronously promoted chunks can tick entities yet.
            // Pump chunk tasks before the fixture starts, without advancing its game clock.
            var level = helper.getLevel();
            int minX = net.minecraft.util.Mth.floor(bounds.minX) >> 4;
            int maxX = net.minecraft.util.Mth.floor(bounds.maxX - 1) >> 4;
            int minZ = net.minecraft.util.Mth.floor(bounds.minZ) >> 4;
            int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ - 1) >> 4;
            java.util.List<BlockPos> chunks = new java.util.ArrayList<>();
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
                chunks.add(new BlockPos(x << 4, 0, z << 4));
            }
            java.util.function.BooleanSupplier ready = () -> chunks.stream().allMatch(level::isPositionEntityTicking);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            level.getServer().managedBlock(() -> {
                // Ticket promotion is owned by the chunk event loop, not just the
                // server queue. Pump it while waiting without advancing world ticks.
                level.getChunkSource().pollTask();
                return ready.getAsBoolean() || System.nanoTime() >= deadline;
            });
            helper.assertTrue(ready.getAsBoolean(), "Fixture chunks did not become entity-ticking before setup");
            if (!fixtureTracking) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Legacy::trackFixtureEntity);
                fixtureTracking = true;
            }
            var scope = new FixtureScope(level, bounds, info);
            FIXTURES.add(scope);
            addCleanupListener(info, new net.minecraft.gametest.framework.GameTestListener() {
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                private void cleanup() {
                    var level = helper.getLevel();
                    FIXTURES.remove(scope);
                    // Discard owned entities even after they left their original structure.
                    for (var entity : scope.entities) entity.discard();
                    // Keep forced chunks intact: another simultaneously running fixture may share one.
                    int minX = net.minecraft.util.Mth.floor(bounds.minX) >> 4;
                    int maxX = net.minecraft.util.Mth.floor(bounds.maxX - 1) >> 4;
                    int minZ = net.minecraft.util.Mth.floor(bounds.minZ) >> 4;
                    int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ - 1) >> 4;
                    for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                        var chunk = level.getChunkSource().getChunkNow(x, z);
                        if (chunk == null) continue;
                        for (var blockEntity : new java.util.ArrayList<>(chunk.getBlockEntities().values())) {
                            if (bounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos()))) {
                                // Remove the inventory before the block's onRemove hook can
                                // drop test cargo into a later reactor's gravity field.
                                level.removeBlockEntity(blockEntity.getBlockPos());
                                level.removeBlock(blockEntity.getBlockPos(), false);
                            }
                        }
                    }
                    for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, bounds)) {
                        if (!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
                    }
                }
            });
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot isolate GameTest fixture", ex);
        }
    }

    private Legacy() {}
    /** Forge 1.20.1's vanilla GameTest mock lacks a channel before player login hooks run. */
    public static net.minecraft.server.level.ServerPlayer mockServerPlayer(net.minecraft.gametest.framework.GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var pos = helper.absolutePos(BlockPos.ZERO);
        player.teleportTo(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        // Keep a completed fixture's player out of later boss participant counts and inventories.
        try {
            var field = java.util.Arrays.stream(net.minecraft.gametest.framework.GameTestHelper.class.getDeclaredFields())
                    .filter(f -> f.getType() == net.minecraft.gametest.framework.GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            var info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
            // Workshop floors can replace the structure block. Use the snapshot taken
            // by isolateFixture before any fixture blocks were placed.
            var fixtureBounds = FIXTURES.stream().filter(f -> f.info == info).map(f -> f.bounds)
                    .findFirst().orElseGet(info::getStructureBounds);
            java.util.Objects.requireNonNull(fixtureBounds, "GameTest fixture bounds");
            addCleanupListener(info, new net.minecraft.gametest.framework.GameTestListener() {
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                private void cleanup() {
                    // Automatic orders search nearby workers. Stop this completed fixture before
                    // the next batch places another workshop inside that search radius.
                    var level = helper.getLevel();
                    var bounds = fixtureBounds;
                    var origin = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
                    var end = BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1);
                    for (var android : level.getEntitiesOfClass(io.github.genichimaruo.singulo.android.RuinAndroid.class, bounds)) {
                        android.discard();
                    }
                    for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin, end)) {
                        if (level.getBlockEntity(pos) instanceof io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity console) {
                            console.orders().clear();
                            console.standing().clear();
                        }
                    }
                    server.getPlayerList().remove(player);
                    if (connection.channel() instanceof io.netty.channel.embedded.EmbeddedChannel channel) channel.finishAndReleaseAll();
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register GameTest mock player cleanup", e);
        }
        return player;
    }
}
