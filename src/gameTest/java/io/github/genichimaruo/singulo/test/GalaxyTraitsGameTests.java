package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.genesis.GalaxyTraits;
import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import io.github.genichimaruo.singulo.genesis.GenesisGalaxies;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 創世炉の銀河の性質（0.21.0）: 生まれる前に素材を核に使うと形・色・大きさが決まり、完成した炉に原初の種を使うと次の銀河を生める。
 * 性質のない以前の保存は、それまでと同じ銀河（性質 0）として読む。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class GalaxyTraitsGameTests {
    private static final BlockPos CORE = new BlockPos(4, 1, 4);

    @GameTest(template = "large", timeoutTicks = 120)
    public static void materialsShapeTheGalaxyAndSeedsMakeMore(GameTestHelper h) throws ReflectiveOperationException {
        // 素材と性質
        int red = GalaxyTraits.apply(0, new ItemStack(Items.RED_DYE));
        h.assertTrue(GalaxyTraits.dye(red) == DyeColor.RED && GalaxyTraits.form(red) == GalaxyTraits.SPIRAL, "Red dye does not color the arms");
        int barred = GalaxyTraits.apply(red, new ItemStack(Items.BLAZE_ROD));
        h.assertTrue(GalaxyTraits.form(barred) == GalaxyTraits.BARRED && GalaxyTraits.dye(barred) == DyeColor.RED, "A blaze rod lost the color");
        h.assertTrue(GalaxyTraits.grand(GalaxyTraits.apply(barred, new ItemStack(Items.NETHER_STAR))), "A nether star is not grand");
        h.assertTrue(GalaxyTraits.apply(0, new ItemStack(Items.DIRT)) == -1, "Dirt changes the galaxy");
        h.assertTrue(GalaxyTraits.form(0) == GalaxyTraits.SPIRAL && GalaxyTraits.dye(0) == null && !GalaxyTraits.grand(0),
                "Traits 0 is not the old spiral");

        // 以前の保存（種だけ）は性質 0 として読む
        CompoundTag legacy = new CompoundTag();
        legacy.put("seeds", new LongArrayTag(new long[] {11L, 22L}));
        var load = GenesisGalaxies.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        GenesisGalaxies old = (GenesisGalaxies) load.invoke(null, legacy, h.getLevel().registryAccess());
        h.assertTrue(old.seeds().equals(java.util.List.of(11L, 22L)) && old.traits().equals(java.util.List.of(0, 0)),
                "Old galaxies do not load as plain spirals: " + old.traits());
        CompoundTag saved = old.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(saved.getIntArray("traits").length == 2, "Traits are not saved");

        // 炉を組む
        h.setBlock(CORE, SinguloBlocks.GENESIS_CORE.get());
        for (BlockPos p : GenesisCoreBlockEntity.FRAMES) {
            h.setBlock(CORE.offset(p), SinguloBlocks.SIMPLE.get("genesis_frame").get());
        }
        for (BlockPos p : GenesisCoreBlockEntity.PYLONS) {
            h.setBlock(CORE.offset(p), SinguloBlocks.SIMPLE.get("genesis_pylon").get());
        }
        GenesisCoreBlockEntity core = h.getBlockEntity(CORE);
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // 仮のプレイヤーはクリエイティブで、素材が減らない
        h.runAfterDelay(60, () -> {
            h.assertTrue(core.formed() && core.stage() >= GenesisCoreBlockEntity.CRADLE, "Not formed");
            // 揺籃: 染料と形の素材を使うと性質が変わり、1個ずつ減る。同じ素材をもう一度使っても減らない
            ItemStack dye = new ItemStack(Items.BLUE_DYE, 2);
            h.assertTrue(core.useItem(player, dye) && dye.getCount() == 1, "The dye was not used");
            ItemStack rod = new ItemStack(Items.BLAZE_ROD, 2);
            h.assertTrue(core.useItem(player, rod) && rod.getCount() == 1, "The blaze rod was not used");
            h.assertTrue(core.useItem(player, rod) && rod.getCount() == 1, "The same material was used up again");
            h.assertTrue(GalaxyTraits.dye(core.galaxyTraits()) == DyeColor.BLUE && GalaxyTraits.form(core.galaxyTraits()) == GalaxyTraits.BARRED,
                    "The core did not take the traits: " + core.galaxyTraits());
            // 種火に入っても性質は残る
            h.assertTrue(core.useItem(player, new ItemStack(GenesisCoreBlockEntity.primordialSeed())), "The seed was refused");
            h.assertTrue(core.stage() == GenesisCoreBlockEntity.KINDLING && GalaxyTraits.form(core.galaxyTraits()) == GalaxyTraits.BARRED,
                    "Traits were lost when kindling");
            // 完成した炉に原初の種: 種火からやり直し、性質は新しく選ぶ
            try {
                var stage = GenesisCoreBlockEntity.class.getDeclaredField("stage");
                stage.setAccessible(true);
                stage.setInt(core, GenesisCoreBlockEntity.COMPLETE);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            ItemStack seed = new ItemStack(GenesisCoreBlockEntity.primordialSeed(), 2);
            h.assertTrue(core.useItem(player, seed) && seed.getCount() == 1, "A finished engine refuses another seed");
            h.assertTrue(core.stage() == GenesisCoreBlockEntity.KINDLING && core.galaxyTraits() == 0, "The next galaxy did not restart");
            CompoundTag tag = core.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(tag.getBoolean("reborn"), "The engine does not remember that it was reborn");
            h.succeed();
        });
    }
}
