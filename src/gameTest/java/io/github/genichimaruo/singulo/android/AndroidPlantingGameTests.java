package io.github.genichimaruo.singulo.android;
import io.github.genichimaruo.singulo.android.work.*;
import io.github.genichimaruo.singulo.registry.*;
import io.github.genichimaruo.singulo.Singulo;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidPlantingGameTests {
    private static void plant(AndroidWorkGoal goal, ServerLevel level, WorkConsoleBlockEntity console,
            BlockPos target, ItemStack item) throws ReflectiveOperationException {
        for (var entry : java.util.Map.<String, Object>of("mineTarget", target, "mineTimer", 100, "mineSettled", true).entrySet()) {
            var field = AndroidWorkGoal.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(goal, entry.getValue());
        }
        var method = AndroidWorkGoal.class.getDeclaredMethod("plant", ServerLevel.class, WorkConsoleBlockEntity.class,
                WorkJob.class, ProcedureStep.class, long.class);
        method.setAccessible(true);
        method.invoke(goal, level, console, null, new ProcedureStep(ProcedureStep.PLANT, target, null, item), 1L);
    }
    @GameTest(template = "empty")
    public static void androidPlantingHonorsGroundAndCropProtection(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(2, 1, 2)), target = ground.above();
        level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        h.setBlock(new BlockPos(4, 1, 4), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = (WorkConsoleBlockEntity) h.getBlockEntity(new BlockPos(4, 1, 4));
        var android = SinguloEntities.BLADE_ANDROID.get().create(level);
        android.makeGuard(null, ground, RuinAndroid.MODE_GUARD, 30);
        android.setCharge(100);
        android.inventory().setItem(RuinAndroid.SLOT_MINING,
                new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_farming_upgrade"))));
        android.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(Items.WHEAT_SEEDS));
        int charge = android.charge();
        BlockPos[] protectedPos = {ground};
        boolean[] observed = {false};
        Consumer<BlockEvent.BreakEvent> protect = event -> {
            if (event.getLevel() == level && event.getPos().equals(protectedPos[0])) {
                observed[0] = true;
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(protect);
        try {
            plant(new AndroidWorkGoal(android), level, console, target, new ItemStack(Items.WHEAT_SEEDS));
            h.assertTrue(observed[0], "The ground was not checked before tilling");
            h.assertTrue(level.getBlockState(ground).is(Blocks.DIRT) && level.getBlockState(target).isAir(), "Protected ground was changed");
            h.assertTrue(android.inventory().getItem(RuinAndroid.CARRY_START).getCount() == 1 && android.charge() == charge, "Canceled tilling consumed seeds or charge");
            protectedPos[0] = target;
            observed[0] = false;
            plant(new AndroidWorkGoal(android), level, console, target, new ItemStack(Items.WHEAT_SEEDS));
            h.assertTrue(observed[0] && level.getBlockState(ground).is(Blocks.DIRT) && level.getBlockState(target).isAir(), "Protected crop position was changed");
            h.assertTrue(android.inventory().getItem(RuinAndroid.CARRY_START).getCount() == 1 && android.charge() == charge, "Canceled planting consumed seeds or charge");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protect);
            android.discard();
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void androidPlantingRejectsContainersAndMultiblockItems(GameTestHelper h) throws ReflectiveOperationException {
        var accepts = AndroidWorkGoal.class.getDeclaredMethod("plantBlock", net.minecraft.world.level.block.Block.class);
        accepts.setAccessible(true);
        for (var block : java.util.List.of(Blocks.SHULKER_BOX, Blocks.CHEST, Blocks.OAK_DOOR, Blocks.SUNFLOWER, Blocks.STONE)) {
            h.assertFalse((boolean) accepts.invoke(null, block), "Unsafe plant block was accepted: " + block);
        }
        for (var block : java.util.List.of(Blocks.WHEAT, Blocks.PUMPKIN_STEM, Blocks.OAK_SAPLING, Blocks.NETHER_WART,
                Blocks.SWEET_BERRY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.COCOA, Blocks.BAMBOO_SAPLING)) {
            h.assertTrue((boolean) accepts.invoke(null, block), "Supported crop was rejected: " + block);
        }
        h.succeed();
    }
}
