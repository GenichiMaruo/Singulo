package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Development only: exercise real client caches and verify unreachable render probes can be collected. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class RuntimeReviewHarness {
    private static int phase, ticks;
    private static List<WeakReference<BlockEntity>> probes;
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Class<?> owner, String name) throws Exception {
        var f = owner.getDeclaredField(name); f.setAccessible(true);
        return (Map<Object, Object>) f.get(null);
    }
    private static Object field(Class<?> owner, String name) throws Exception {
        var f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    private static void require(boolean okay, String message) { if (!okay) throw new AssertionError(message); }

    private static List<WeakReference<BlockEntity>> seed(Minecraft mc) throws Exception {
        var result = new ArrayList<WeakReference<BlockEntity>>();
        var blocks = List.of(SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get(),
                SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER.get(), SinguloBlocks.CORE_CONTROLLER.get());
        var owners = List.of(DegenerateCompactorRenderer.class, DegenerateFurnaceRenderer.class, PenroseReactorRenderer.class);
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            BlockEntity be = ((EntityBlock) block).newBlockEntity(new BlockPos(100, 80, 100), block.defaultBlockState());
            be.setLevel(mc.level);
            map(owners.get(i), i == 2 ? "LAST_STATE" : "LAST_SLAM").put(be, i == 2 ? PenroseReactorBlockEntity.State.DORMANT : 1L);
            if (i == 2) map(owners.get(i), "FORMED_AT").put(be, 1L);
            result.add(new WeakReference<>(be));
        }
        return result;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent event) throws Exception {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || phase == 2) return;
        if (phase == 0) {
            // No world edits: invalid controller positions exercise the bounded shape cache.
            for (int i = 0; i < 4200; i++) ShapeCache.get(mc.level, new BlockPos(i, 300, 0), Blueprints.Kind.DEGENERATE_COMPACTOR);
            require(map(ShapeCache.class, "CACHE").size() <= 4096, "Shape cache grows without a bound");
            require(field(ShapeCache.class, "lastLevel") instanceof WeakReference<?>, "Shape cache retains ClientLevel strongly");
            NeutrinoOverlay.show(List.of(BlockPos.ZERO), List.of(BlockPos.ZERO));
            HologramRenderer.toggle(BlockPos.ZERO, Blueprints.Kind.DEGENERATE_COMPACTOR, Direction.SOUTH, 5, false);
            var records = List.copyOf(ClientRecords.decoded());
            SinguloClient.clearWorldState();
            require(map(ShapeCache.class, "CACHE").isEmpty(), "World change kept shape cache");
            require(((List<?>) field(NeutrinoOverlay.class, "ores")).isEmpty(), "World change kept scanner outlines");
            require(field(HologramRenderer.class, "layout") == null, "World change kept projection");
            require(ClientRecords.decoded().equals(records), "Dimension change lost server record knowledge");
            probes = seed(mc);
            phase = 1;
            return;
        }
        if (++ticks % 5 == 0) System.gc();
        if (probes.stream().allMatch(p -> p.get() == null)) {
            Path output = Path.of(System.getProperty("singulo.v011Output")); Files.createDirectories(output);
            Files.writeString(output.resolve("runtime-check.txt"), "shape cache bounded and cleared\nscanner/projection cleared\nrecord knowledge preserved across dimensions\n3 render probes collected while client level remains open\n");
            Singulo.LOGGER.info("SINGULO_RUNTIME_CACHE_COMPLETE collected=3");
            phase = 2;
        } else if (ticks > 120) throw new AssertionError("Client render cache retains unreachable block entities");
    }
}
