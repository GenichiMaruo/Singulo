package io.github.genichimaruo.singulo.block;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two distinct glass slabs sharing a position. The original slab items are used for placement and drops.
 * <p>
 * This block ({@code mixed_glass_slab}) holds white panel glass and black reinforced glass. Its state ({@link #BLACK_ON_TOP})
 * is stored in existing worlds since 0.21.0, so it is kept as it is. Pairs that include collapsar reinforced glass (0.23.0)
 * use {@link Collapsar} ({@code mixed_collapsar_glass_slab}), which shares all behaviour through {@link #layer}.
 */
public class MixedGlassSlab extends GlassBlock {
    public static final BooleanProperty BLACK_ON_TOP = BooleanProperty.create("black_on_top");
    public static final int BOTTOM_HIT = 1, TOP_HIT = 2;

    /** 重ねられるガラス（コラプサーとの組で、もう一方を表すのに使う）。 */
    public enum Glass implements StringRepresentable {
        WHITE("white", "white_glass_panel"),
        BLACK("black", "black_reinforced_glass"),
        COLLAPSAR("collapsar", "collapsar_reinforced_glass");

        private final String name;
        /** 元のガラスの ID（ハーフブロックは <id>_slab）。 */
        public final String base;

        Glass(String name, String base) {
            this.name = name;
            this.base = base;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Nullable
        public static Glass of(Block base) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(base);
            for (Glass g : values()) {
                if (id.getNamespace().equals(io.github.genichimaruo.singulo.Singulo.MODID) && id.getPath().equals(g.base)) return g;
            }
            return null;
        }

        BlockState slab(boolean top) {
            return SinguloBlocks.VARIANTS.get(base + "_slab").get().defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        }
    }

    public interface ExplosionHits {
        int singulo$glassHits(BlockPos pos);
        void singulo$addLootDecay(LootParams.Builder loot);
    }

    public MixedGlassSlab(Properties properties) {
        super(properties);
        if (stateDefinition.getProperties().contains(BLACK_ON_TOP)) {
            registerDefaultState(stateDefinition.any().setValue(BLACK_ON_TOP, false));
        }
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BLACK_ON_TOP);
    }

    /** このブロックの上（top）か下のハーフブロック。 */
    protected BlockState layer(BlockState mixed, boolean top) {
        return (mixed.getValue(BLACK_ON_TOP) == top ? Glass.BLACK : Glass.WHITE).slab(top);
    }

    /** 重ねたガラス（白黒・コラプサーとの組のどちらでも）の、上（top）か下のハーフブロック。 */
    public static BlockState half(BlockState mixed, boolean top) {
        return ((MixedGlassSlab) mixed.getBlock()).layer(mixed, top);
    }

    /** 下と上のガラスから、重ねたブロックの状態（白と黒は元からのブロック、コラプサーとの組は {@link Collapsar}）。 */
    public static BlockState of(Glass lower, Glass upper) {
        if (lower != Glass.COLLAPSAR && upper != Glass.COLLAPSAR) {
            return SinguloBlocks.MIXED_GLASS_SLAB.get().defaultBlockState().setValue(BLACK_ON_TOP, upper == Glass.BLACK);
        }
        return SinguloBlocks.MIXED_COLLAPSAR_GLASS_SLAB.get().defaultBlockState()
                .setValue(Collapsar.COLLAPSAR_ON_TOP, upper == Glass.COLLAPSAR)
                .setValue(Collapsar.OTHER, upper == Glass.COLLAPSAR ? lower : upper);
    }

    public static boolean topAt(BlockPos pos, double y) { return y >= pos.getY() + 0.5; }

    private static BlockState selected(BlockState state, BlockPos pos, HitResult hit) {
        boolean top = !(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)
                || topAt(pos, hit.getLocation().y);
        return half(state, top);
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return new ItemStack(selected(state, pos, target).getBlock());
    }

    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return selected(state, pos, player.pick(player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()), 1, false)).getDestroyProgress(player, level, pos);
    }

    @Override public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState surface = entity instanceof Player player
                ? selected(state, pos, player.pick(player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()), 1, false)) : half(state, true);
        return surface.getSoundType(level, pos, entity);
    }


    /** 掘って落とすのは、道具が上下どちらのハーフブロックも掘れるときだけ（コラプサーの方はダイヤ以上のツルハシ）。 */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.hasCorrectToolForDrops(half(state, false)) && player.hasCorrectToolForDrops(half(state, true));
    }

    @Override public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return half(state, topAt(pos, explosion.getPosition().y)).getExplosionResistance(level, pos, explosion);
    }

    @Override public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return (direction == Direction.DOWN || hidesHalf(state, true, adjacent, direction))
                && (direction == Direction.UP || hidesHalf(state, false, adjacent, direction));
    }

    private static boolean hidesHalf(BlockState state, boolean top, BlockState adjacent, Direction direction) {
        BlockState slab = half(state, top);
        Block glass = ((GlassJoin.Shaped) slab.getBlock()).base();
        BlockState neighbour = GlassJoin.matchingPart(adjacent, glass);
        return neighbour != null && GlassJoin.covered(slab, neighbour, direction);
    }

    private static int hits(Explosion explosion, BlockPos pos) {
        return explosion instanceof ExplosionHits hit ? hit.singulo$glassHits(pos) : 0;
    }

    @Override public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        int hit = hits(explosion, pos);
        // Partial hits generate loot for the destroyed half, not the whole composite.
        return hit != TOP_HIT && hit != BOTTOM_HIT && super.canDropFromExplosion(state, level, pos, explosion);
    }

    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        int hit = hits(explosion, pos);
        if (hit != TOP_HIT && hit != BOTTOM_HIT) {
            super.onBlockExploded(state, level, pos, explosion);
            return;
        }
        BlockState broken = half(state, hit == TOP_HIT);
        if (level instanceof ServerLevel server && broken.canDropFromExplosion(level, pos, explosion)) {
            var loot = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
            if (explosion instanceof ExplosionHits data) data.singulo$addLootDecay(loot);
            broken.spawnAfterBreak(server, pos, ItemStack.EMPTY, explosion.getIndirectSourceEntity() instanceof Player);
            broken.getDrops(loot).forEach(stack -> Block.popResource(level, pos, stack));
        }
        level.setBlockAndUpdate(pos, half(state, hit == BOTTOM_HIT));
    }

    /**
     * コラプサー強化ガラスのハーフブロックと、白か黒のガラスのハーフブロックを重ねたもの（0.23.0。{@code mixed_collapsar_glass_slab}）。
     * ふるまいは白黒の重ねハーフブロックと同じ。コラプサーを含むので、ウィザーとエンダードラゴンには壊されない。
     */
    public static final class Collapsar extends MixedGlassSlab {
        public static final BooleanProperty COLLAPSAR_ON_TOP = BooleanProperty.create("collapsar_on_top");
        /** コラプサーと重ねた、もう一方のガラス（白か黒）。 */
        public static final EnumProperty<Glass> OTHER = EnumProperty.create("other", Glass.class, Glass.WHITE, Glass.BLACK);

        public Collapsar(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(COLLAPSAR_ON_TOP, true).setValue(OTHER, Glass.BLACK));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(COLLAPSAR_ON_TOP, OTHER);
        }

        @Override protected BlockState layer(BlockState mixed, boolean top) {
            return (mixed.getValue(COLLAPSAR_ON_TOP) == top ? Glass.COLLAPSAR : mixed.getValue(OTHER)).slab(top);
        }

        @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
            if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                    || entity instanceof net.minecraft.world.entity.projectile.WitherSkull
                    || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                return false;
            }
            return super.canEntityDestroy(state, level, pos, entity);
        }
    }
}
