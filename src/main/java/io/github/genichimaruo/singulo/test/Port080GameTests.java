package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.AreaCardItem;
import io.github.genichimaruo.singulo.android.work.ChemicalSamples;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Save/reload boundaries whose NBT/API representation differs between 1.21.1 and 1.20.1. */
@GameTestHolder("singulo_progression")
@PrefixGameTestTemplate(false)
public final class Port080GameTests {
    @GameTest(template = "empty", batch = "port_080_saves")
    public static void patrolAndFoundryPositionsSurviveReload(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        var level = h.getLevel();
        var a = SinguloEntities.BIT_ANDROID.get().create(level);
        a.makeGuard(null, h.absolutePos(new BlockPos(1, 1, 1)), RuinAndroid.MODE_PATROL, 30);
        var route = List.of(h.absolutePos(new BlockPos(2, 1, 2)), h.absolutePos(new BlockPos(5, 1, 5)));
        a.setPatrol(route, true, true, 60);
        var loaded = SinguloEntities.BIT_ANDROID.get().create(level);
        loaded.load(a.saveWithoutId(new CompoundTag()));
        var saved = loaded.saveWithoutId(new CompoundTag());
        var locations = saved.getCompound("patrol").getList("route", Tag.TAG_COMPOUND);
        h.assertTrue(locations.size() == 2, "Patrol route disappeared across old-format BlockPos NBT");
        h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockPos(locations.getCompound(1)).equals(route.get(1)), "Patrol route coordinates changed");
        BlockPos line = new BlockPos(3, 1, 3), belt = new BlockPos(4, 1, 3);
        h.setBlock(line, SinguloBlocks.FACTORY_LINE.get());
        h.setBlock(belt, SinguloBlocks.FACTORY_CONVEYOR.get());
        var boss = SinguloEntities.FOUNDRY_OVERSEER.get().create(level);
        boss.setLines(List.of(h.absolutePos(line)));
        boss.setConveyors(List.of(h.absolutePos(belt)));
        var restored = SinguloEntities.FOUNDRY_OVERSEER.get().create(level);
        restored.load(boss.saveWithoutId(new CompoundTag()));
        h.assertTrue(restored.intactLines().equals(List.of(h.absolutePos(line))), "Foundry line positions disappeared on reload");
        restored.setConveyors(true);
        h.assertTrue(level.getBlockState(h.absolutePos(belt)).getValue(io.github.genichimaruo.singulo.ruin.FactoryConveyorBlock.RUNNING),
                "Foundry conveyor positions disappeared on reload");
        restored.setConveyors(false);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "port_080_cards")
    public static void areaAndChemicalCardsKeepNbtAndStackRules(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        var card = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("area_card")));
        h.assertTrue(card.getMaxStackSize() == 64, "Blank area cards do not stack");
        AreaCardItem.record(card, h.getLevel(), new BlockPos(1, 2, 3), new BlockPos(5, 6, 7));
        card = ItemStack.of(card.save(new CompoundTag()));
        h.assertTrue(card.getMaxStackSize() == 1 && AreaCardItem.second(card).equals(new BlockPos(5, 6, 7)),
                "Recorded area cards lost their NBT or stack limit");
        AreaCardItem.clear(card);
        h.assertTrue(card.getMaxStackSize() == 64 && ItemStack.isSameItemSameTags(card,
                new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("area_card")))), "Cleared area card does not match a blank card");
        var chemical = ChemicalSamples.of("infusion|mekanism:carbon", 64000);
        var reloaded = ItemStack.of(chemical.save(new CompoundTag()));
        h.assertTrue(ChemicalSamples.id(reloaded).equals("infusion|mekanism:carbon") && ChemicalSamples.amount(reloaded) == 64000,
                "Chemical family or amount disappeared from the sample NBT");
        h.succeed();
    }
}
