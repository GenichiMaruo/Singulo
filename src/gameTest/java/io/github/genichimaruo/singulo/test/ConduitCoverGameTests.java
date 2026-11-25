package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.ConduitCover;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ConduitCoverGameTests {
    private static final BlockPos AT = new BlockPos(2, 1, 2);

    private static ConduitCover.State cover(GameTestHelper h) {
        return ConduitCover.of(h.getBlockState(AT));
    }

    /** 面 face を右クリックしたように使い、はめ込みを次の tick を待たずに終える（settle）。 */
    private static void use(GameTestHelper h, Player player, ItemStack stack, Direction face) {
        click(h, player, stack, face);
        ConduitCover.settle(h.getBlockState(AT), h.getLevel(), h.absolutePos(AT));
    }

    /** 面 face を右クリックしたように使う（はめ込みは次の tick にブロック入りになる）。 */
    private static void click(GameTestHelper h, Player player, ItemStack stack, Direction face) {
        BlockPos pos = h.absolutePos(AT);
        BlockHitResult hit = new BlockHitResult(pos.getCenter().relative(face, 0.5), face, pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ConduitCover.use(stack, h.getBlockState(AT), h.getLevel(), pos, player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * 0.23.0: 掘るのに道具の要るブロック（黒曜石）を入れたカバーは、それを掘れる道具でないと外れない。素手でも移設レンチでも外れず、
     * ダイヤモンドのツルハシなら外れる。道具の要らないブロック（木材）は、これまでどおり素手で外れる。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void toolBoundInlayNeedsTheRightTool(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.COPPER_WIRE.get());
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(AT);
        var player = h.makeMockPlayer();
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        use(h, player, new ItemStack(Items.OBSIDIAN), Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.BLOCK, "Obsidian was not set in");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(ConduitCover.breakCover(h.getBlockState(AT), level, pos, player) && cover(h) == ConduitCover.State.BLOCK,
                "Obsidian came out by hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SinguloItems.RELOCATION_WRENCH.get()));
        h.assertTrue(ConduitCover.locked(level, pos, player), "Obsidian could come out with the wrench");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertFalse(ConduitCover.locked(level, pos, player), "Diamond pickaxe could not take out the obsidian");
        h.assertTrue(ConduitCover.breakCover(h.getBlockState(AT), level, pos, player) && cover(h) == ConduitCover.State.FRAME,
                "Obsidian did not come out with a diamond pickaxe");
        use(h, player, new ItemStack(Items.OAK_PLANKS), Direction.UP);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(ConduitCover.breakCover(h.getBlockState(AT), level, pos, player) && cover(h) == ConduitCover.State.FRAME,
                "Planks did not come out by hand");
        h.succeed();
    }

    /**
     * 0.23.0: ブロック・ガラス入りのカバーは、はめ込んだブロックのウィザー・エンダードラゴンへの耐性を持つ。コラプサー強化パネル（ブロック入り）
     * とコラプサー強化ガラス（ガラス入り）は、ウィザー・その頭蓋骨・ドラゴンに壊されず、青い頭蓋骨の爆発でも強さが下がらない。
     * 石を入れたカバーと、枠だけのカバーは、これまでどおり壊される。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inlayLendsItsWitherProofing(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(AT);
        var player = h.makeMockPlayer();
        var wither = net.minecraft.world.entity.EntityType.WITHER.create(level);
        var skull = net.minecraft.world.entity.EntityType.WITHER_SKULL.create(level);
        var dragon = net.minecraft.world.entity.EntityType.ENDER_DRAGON.create(level);
        skull.setDangerous(true);
        var blast = new net.minecraft.world.level.Explosion(level, skull, pos.getX(), pos.getY(), pos.getZ(), 1, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
        h.setBlock(AT, SinguloBlocks.COPPER_WIRE.get());
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        h.assertTrue(h.getBlockState(AT).canEntityDestroy(level, pos, wither), "Empty frame resisted the Wither");
        for (var inlay : new net.minecraft.world.level.block.Block[] {SinguloBlocks.COLLAPSAR_REINFORCED_PANEL.get(),
                SinguloBlocks.COLLAPSAR_REINFORCED_GLASS.get(), Blocks.STONE}) {
            use(h, player, new ItemStack(inlay), Direction.UP);
            var state = h.getBlockState(AT);
            h.assertTrue(ConduitCover.inlaid(state), inlay + " was not set in");
            boolean proof = inlay != Blocks.STONE;
            for (var entity : new net.minecraft.world.entity.Entity[] {wither, skull, dragon}) {
                h.assertTrue(state.canEntityDestroy(level, pos, entity) != proof,
                        "Cover with " + inlay + (proof ? " was breakable by " : " resisted ") + entity.getType());
            }
            float resistance = state.getExplosionResistance(level, pos, blast);
            float lowered = skull.getBlockExplosionResistance(blast, level, pos, state, state.getFluidState(), resistance);
            h.assertTrue((lowered == resistance) == proof, "Blue skull lowered the resistance of " + inlay + " to " + lowered);
            ConduitCover.takeOne(level, pos);
        }
        h.succeed();
    }

    /**
     * 0.23.0: 光るブロック（グロウストーン）をはめ込むと、カバーがそのブロックと同じ明るさで光る。はめ込み中（枠だけの間）はまだ光らず、
     * 外すと消える。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glowingInlayLightsTheCover(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(AT);
        var player = h.makeMockPlayer();
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        h.assertTrue(level.getLightEmission(pos) == 0, "Empty frame glowed");
        click(h, player, new ItemStack(Items.GLOWSTONE), Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.FRAME && level.getLightEmission(pos) == 0, "Cover glowed before the inlay settled");
        ConduitCover.settle(h.getBlockState(AT), level, pos);
        h.assertTrue(cover(h) == ConduitCover.State.BLOCK && level.getLightEmission(pos) == 15,
                "Glowstone cover gave light " + level.getLightEmission(pos) + " instead of 15");
        ItemStack off = ConduitCover.takeOne(level, pos);
        h.assertTrue(off.is(Items.GLOWSTONE) && level.getLightEmission(pos) == 0, "Light stayed after the glowstone came off");
        h.succeed();
    }

    /**
     * 0.23.0: 枠にガラスもはめ込める（バニラのガラス・色付きガラス・この作品のガラス）。ガラス入りは光を通し、隣を隠さず、
     * 当たり判定は枠だけのときと同じ中空の箱（外からは1ブロックいっぱい）。外すとガラス → 枠の順に戻る。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void coverTakesGlassAndStaysSeeThrough(GameTestHelper h) {
        for (var ok : new net.minecraft.world.level.block.Block[] {Blocks.GLASS, Blocks.RED_STAINED_GLASS, Blocks.TINTED_GLASS,
                SinguloBlocks.WHITE_GLASS_PANEL.get(), SinguloBlocks.COLLAPSAR_STAR_GLASS.get()}) {
            h.assertTrue(ConduitCover.accepts(ok.defaultBlockState()) && ConduitCover.stateFor(ok.defaultBlockState()) == ConduitCover.State.GLASS,
                    ok + " was not taken as glass");
        }
        h.assertTrue(ConduitCover.stateFor(Blocks.STONE.defaultBlockState()) == ConduitCover.State.BLOCK, "Stone was taken as glass");
        h.setBlock(AT, SinguloBlocks.COPPER_WIRE.get());
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(AT);
        var player = h.makeMockPlayer();
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        ItemStack glass = new ItemStack(Items.GLASS, 2);
        use(h, player, glass, Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.GLASS && glass.getCount() == 1, "Glass was not set in: " + cover(h));
        var glazed = h.getBlockState(AT);
        h.assertTrue(!glazed.isSolidRender(level, pos) && glazed.getLightBlock(level, pos) == 0 && glazed.propagatesSkylightDown(level, pos)
                && !glazed.isSuffocating(level, pos), "Glass cover did not let light through");
        h.assertTrue(glazed.getCollisionShape(level, pos).bounds().equals(new AABB(0, 0, 0, 1, 1, 1)) && !glazed.isCollisionShapeFullBlock(level, pos),
                "Glass cover collision was not the hollow full-size box");
        h.assertTrue(glazed.getOcclusionShape(level, pos).isEmpty(), "Glass cover hid its neighbours' faces");
        ItemStack first = ConduitCover.takeOne(level, pos);
        h.assertTrue(first.is(Items.GLASS) && cover(h) == ConduitCover.State.FRAME, "First removal did not return the glass: " + first);
        h.succeed();
    }

    /**
     * カバーの枠をケーブルに取り付け（枠を1つ使う）、枠に丸太をはめ込む（1つ使う。向きは右クリックした面の軸）。
     * すき間のある・透ける・入れ物のブロック（ガラス・階段・葉・チェスト・かまど）は断り、向きのある丸太は受け付ける。
     * 枠だけの間は当たり判定が1ブロックいっぱいで、光を通し隣を隠さない。ブロック入りの間だけ隣を隠し、光をさえぎる。
     * 保存し直しても向きを覚え、外すと丸太 → 枠の順に戻り、もとのケーブルに戻る。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void coverFitsInlaysAndComesBack(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.COPPER_WIRE.get());
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(AT);
        var player = h.makeMockPlayer();
        h.assertTrue(ConduitCover.allowed(Blocks.STONE.defaultBlockState()) && ConduitCover.allowed(Blocks.OAK_LOG.defaultBlockState())
                && ConduitCover.allowed(Blocks.CARVED_PUMPKIN.defaultBlockState()), "Solid blocks (with or without a facing) were refused");
        for (var bad : new net.minecraft.world.level.block.Block[] {Blocks.GLASS_PANE, Blocks.OAK_STAIRS, Blocks.CHEST,
                Blocks.OAK_LEAVES, Blocks.FURNACE, SinguloBlocks.COPPER_WIRE.get()}) {
            h.assertFalse(ConduitCover.accepts(bad.defaultBlockState()), bad + " was allowed in a cover");
        }
        var bare = h.getBlockState(AT);
        h.assertFalse(bare.isCollisionShapeFullBlock(level, pos), "Bare cable collided as a full block");

        ItemStack frames = new ItemStack(SinguloItems.COVER_FRAME.get(), 2);
        use(h, player, frames, Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.FRAME && frames.getCount() == 1, "Frame was not fitted: " + cover(h));
        var framed = h.getBlockState(AT);
        // 選ぶ形は1ブロックいっぱい。当たり判定は外側いっぱいの中空の箱（中へ入れない。いっぱいの箱にすると内側の面が隣の明るさで黒く描かれる）
        h.assertTrue(net.minecraft.world.level.block.Block.isShapeFullBlock(framed.getShape(level, pos)), "Frame outline did not fill the block");
        var collision = framed.getCollisionShape(level, pos);
        h.assertTrue(collision.bounds().equals(new AABB(0, 0, 0, 1, 1, 1)) && !framed.isCollisionShapeFullBlock(level, pos)
                && !collision.isEmpty(), "Empty frame collision was not a hollow full-size box: " + collision.bounds());
        h.assertTrue(!framed.isSolidRender(level, pos) && framed.getLightBlock(level, pos) == 0 && framed.propagatesSkylightDown(level, pos)
                && !framed.isSuffocating(level, pos), "Empty frame did not let light through like glass");

        ItemStack panes = new ItemStack(Items.GLASS_PANE, 3);
        use(h, player, panes, Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.FRAME && panes.getCount() == 3, "Glass pane was set in");

        ItemStack logs = new ItemStack(Items.OAK_LOG, 3);
        use(h, player, logs, Direction.EAST);
        h.assertTrue(cover(h) == ConduitCover.State.BLOCK && logs.getCount() == 2, "Log was not set in");
        var holder = (ConduitCover.Holder) level.getBlockEntity(pos);
        h.assertTrue(holder.coverState() != null && holder.coverState().is(Blocks.OAK_LOG)
                && holder.coverState().getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "Log did not keep the clicked axis: " + holder.coverState());
        var covered = h.getBlockState(AT);
        h.assertTrue(covered.isSolidRender(level, pos) && covered.getLightBlock(level, pos) > 0 && covered.isCollisionShapeFullBlock(level, pos),
                "Covered cable did not act as a solid block");
        // 爆発への強さは、はめ込んだ丸太と同じ（ケーブルだけより強い）
        var blast = new net.minecraft.world.level.Explosion(level, null, pos.getX(), pos.getY(), pos.getZ(), 4, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
        float coveredResistance = covered.getExplosionResistance(level, pos, blast);
        float logResistance = Blocks.OAK_LOG.defaultBlockState().getExplosionResistance(level, pos, blast);
        h.assertTrue(coveredResistance == logResistance && logResistance > bare.getBlock().getExplosionResistance(),
                "Covered cable resisted explosions like " + coveredResistance + " instead of the log's " + logResistance);
        h.assertTrue(covered.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH)
                == bare.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH), "Cover changed the connections");

        // 保存し直しても、向きを含めて覚えている
        var be = level.getBlockEntity(pos);
        var tag = be.saveWithFullMetadata();
        holder.setCoverState(null);
        be.load(tag);
        h.assertTrue(holder.coverState() != null && holder.coverState().getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X,
                "Reload lost the log or its axis");

        ItemStack first = ConduitCover.takeOne(level, pos);
        h.assertTrue(first.is(Items.OAK_LOG) && cover(h) == ConduitCover.State.FRAME, "First removal did not return the log: " + first);
        ItemStack second = ConduitCover.takeOne(level, pos);
        h.assertTrue(second.is(SinguloItems.COVER_FRAME.get()) && cover(h) == ConduitCover.State.NONE,
                "Second removal did not return the frame: " + second);
        h.assertFalse(h.getBlockState(AT).isCollisionShapeFullBlock(level, pos), "Cable stayed full after removal");
        h.succeed();
    }

    /**
     * 枠はケーブルのないところにも置け（光を通し、当たり判定は中空の箱）、あとからケーブルで右クリックすると、その場所につながりを決めて
     * 枠付きのケーブルが入る（ケーブルを1つ使う）。ほかのブロックでは入らない。撤去モードのように枠だけ残すと、枠のブロックに戻り、枠は落とさない。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void frameFirstThenCable(GameTestHelper h) {
        var level = h.getLevel();
        var player = h.makeMockPlayer();
        BlockPos pos = h.absolutePos(AT);
        h.setBlock(AT, SinguloBlocks.COVER_FRAME.get());
        h.setBlock(AT.east(), SinguloBlocks.COPPER_WIRE.get());
        var frame = h.getBlockState(AT);
        h.assertTrue(frame.getLightBlock(level, pos) == 0 && !frame.isCollisionShapeFullBlock(level, pos)
                && frame.getCollisionShape(level, pos).bounds().equals(new AABB(0, 0, 0, 1, 1, 1)), "Placed frame was not a light-passing hollow box");

        BlockHitResult hit = new BlockHitResult(pos.getCenter().relative(Direction.UP, 0.5), Direction.UP, pos, false);
        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        h.getBlockState(AT).use(level, player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(h.getBlockState(AT).is(SinguloBlocks.COVER_FRAME.get()) && stone.getCount() == 1, "Stone went into an empty frame");

        ItemStack wire = new ItemStack(SinguloBlocks.COPPER_WIRE.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, wire);
        h.getBlockState(AT).use(level, player, InteractionHand.MAIN_HAND, hit);
        var placed = h.getBlockState(AT);
        h.assertTrue(placed.is(SinguloBlocks.COPPER_WIRE.get()) && cover(h) == ConduitCover.State.FRAME && wire.getCount() == 2,
                "Cable did not go into the frame: " + placed);
        h.assertTrue(placed.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST)
                && h.getBlockState(AT.east()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST),
                "Cable in the frame did not connect to its neighbour");

        h.assertTrue(ConduitCover.leaveFrame(level, pos) && h.getBlockState(AT).is(SinguloBlocks.COVER_FRAME.get()),
                "Removing the cable did not leave the frame");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        boolean frameDropped = drops.stream().anyMatch(e -> e.getItem().is(SinguloItems.COVER_FRAME.get()));
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        h.assertFalse(frameDropped, "Leaving the frame also dropped a frame");
        var water = (net.minecraft.world.level.block.SimpleWaterloggedBlock) h.getBlockState(AT).getBlock();
        h.assertTrue(water.placeLiquid(level, pos, h.getBlockState(AT), net.minecraft.world.level.material.Fluids.WATER.getSource(false)),
                "Empty frame could not be waterlogged");
        ItemStack pipe = new ItemStack(SinguloBlocks.FLUID_PIPE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pipe);
        h.getBlockState(AT).use(level, player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(h.getBlockState(AT).is(SinguloBlocks.FLUID_PIPE.get()) && h.getBlockState(AT).getFluidState().isSource(),
                "Fitting a pipe took water from the clicked face instead of the frame");
        h.assertTrue(ConduitCover.leaveFrame(level, pos) && h.getBlockState(AT).is(SinguloBlocks.COVER_FRAME.get())
                && h.getBlockState(AT).getFluidState().isSource(), "Removing the pipe drained the frame");
        h.succeed();
    }

    /**
     * プレイヤーがカバーを付けたパイプを壊すと、パイプは残してカバーを1つずつ外して落とす。ブロックとして壊れたとき（爆発など）は、
     * 枠とはめ込んだブロックも落とす。撤去の前に外す（strip）と、壊しても二重に落とさない。
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void coveredConduitsReturnTheirCover(GameTestHelper h) {
        var level = h.getLevel();
        var player = h.makeMockPlayer();
        BlockPos pos = h.absolutePos(AT);
        h.setBlock(AT, SinguloBlocks.FLUID_PIPE.get());
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        use(h, player, new ItemStack(Items.OAK_PLANKS), Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.BLOCK, "Pipe cover was not set");
        // プレイヤーが壊すと、パイプは残して木材 → 枠の順に外れて落ちる
        var fluid = level.getFluidState(pos);
        h.assertFalse(h.getBlockState(AT).onDestroyedByPlayer(level, pos, player, true, fluid), "Breaking removed the pipe with its cover");
        h.assertTrue(cover(h) == ConduitCover.State.FRAME, "First break did not take off the planks");
        h.assertFalse(h.getBlockState(AT).onDestroyedByPlayer(level, pos, player, true, fluid), "Breaking removed the framed pipe");
        h.assertTrue(cover(h) == ConduitCover.State.NONE && h.getBlockState(AT).is(SinguloBlocks.FLUID_PIPE.get()),
                "Second break did not take off the frame and keep the pipe");
        var broken = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        h.assertTrue(broken.stream().anyMatch(e -> e.getItem().is(Items.OAK_PLANKS))
                && broken.stream().anyMatch(e -> e.getItem().is(SinguloItems.COVER_FRAME.get())), "Broken cover pieces were not dropped");
        broken.forEach(net.minecraft.world.entity.Entity::discard);
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        use(h, player, new ItemStack(Items.OAK_PLANKS), Direction.UP);
        level.destroyBlock(pos, false);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        boolean planks = drops.stream().anyMatch(e -> e.getItem().is(Items.OAK_PLANKS));
        int frameCount = drops.stream().filter(e -> e.getItem().is(SinguloItems.COVER_FRAME.get())).mapToInt(e -> e.getItem().getCount()).sum();
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        h.assertTrue(planks && frameCount == 1, "Broken pipe did not drop its cover once");

        h.setBlock(AT, SinguloBlocks.HORIZON_BUS.get());
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        var stripped = ConduitCover.strip(level, pos);
        h.assertTrue(stripped.size() == 1 && stripped.get(0).is(SinguloItems.COVER_FRAME.get()) && cover(h) == ConduitCover.State.NONE,
                "Strip did not return the frame");
        level.destroyBlock(pos, false);
        var again = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        boolean dup = again.stream().anyMatch(e -> e.getItem().is(SinguloItems.COVER_FRAME.get()));
        again.forEach(net.minecraft.world.entity.Entity::discard);
        h.assertFalse(dup, "Stripped cable dropped its frame again");

        // はめ込みは、まずブロックを覚えて（状態は枠のまま）、次の tick にブロック入りになる。その間に別のブロックでは上書きしない
        h.setBlock(AT, SinguloBlocks.COPPER_WIRE.get());
        use(h, player, new ItemStack(SinguloItems.COVER_FRAME.get()), Direction.UP);
        click(h, player, new ItemStack(Items.STONE), Direction.UP);
        ItemStack other = new ItemStack(Items.OAK_PLANKS, 2);
        click(h, player, other, Direction.UP);
        var holder = (ConduitCover.Holder) level.getBlockEntity(pos);
        h.assertTrue(cover(h) == ConduitCover.State.FRAME && holder.coverState() != null && holder.coverState().is(Blocks.STONE)
                && other.getCount() == 2, "Inlay was not remembered first (or was overwritten)");
        h.runAfterDelay(3, () -> {
            h.assertTrue(cover(h) == ConduitCover.State.BLOCK, "Inlay did not settle on the next tick: " + cover(h));
            h.succeed();
        });
    }
}
