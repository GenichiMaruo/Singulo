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
        for (var bad : new net.minecraft.world.level.block.Block[] {Blocks.GLASS, Blocks.OAK_STAIRS, Blocks.CHEST,
                Blocks.OAK_LEAVES, Blocks.FURNACE, SinguloBlocks.COPPER_WIRE.get()}) {
            h.assertFalse(ConduitCover.allowed(bad.defaultBlockState()), bad + " was allowed in a cover");
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

        ItemStack glass = new ItemStack(Items.GLASS, 3);
        use(h, player, glass, Direction.UP);
        h.assertTrue(cover(h) == ConduitCover.State.FRAME && glass.getCount() == 3, "Glass was set in");

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
