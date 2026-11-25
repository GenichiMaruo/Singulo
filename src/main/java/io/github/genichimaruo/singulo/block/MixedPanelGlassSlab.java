package io.github.genichimaruo.singulo.block;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ガラスのハーフブロック（白・黒・コラプサー）と、パネルのハーフブロック（白・黒・コラプサー）を、同じマスに上下で重ねたもの（0.23.0）。
 * 置くときも落とすときも元のハーフブロックのアイテムを使う（壊すと両方が落ちる）。見た目・掘る速さ・音・爆発への強さは、
 * 狙った方（爆発は中心の高さの方）のハーフブロックに合わせる。コラプサーのハーフブロックを含むと、ウィザーとエンダードラゴンには壊されない。
 * <p>
 * 半分が透けるので、隣を隠さない（{@code noOcclusion}）。自分の面は、上下の半分ごとに、隣に隠れるかを見る（{@link #halfHidden}）:
 * ガラスの半分は、この作品のガラスか不透明な物に覆われると隠れ、パネルの半分は不透明な物に覆われると隠れる。
 * 片方の半分だけが隠れる面は、模型の包み（client の MixedPanelGlassModel）がその半分の面だけを描かない。
 */
public final class MixedPanelGlassSlab extends Block {
    public static final EnumProperty<MixedGlassSlab.Glass> GLASS = EnumProperty.create("glass", MixedGlassSlab.Glass.class);
    public static final EnumProperty<MixedPanelSlab.Panel> PANEL = EnumProperty.create("panel", MixedPanelSlab.Panel.class);
    public static final BooleanProperty GLASS_ON_TOP = BooleanProperty.create("glass_on_top");

    /** ガラスとパネルのハーフブロック（このブロックに重ねられる物）。 */
    public interface Stackable {
        Block base();

        /** 持っているこのハーフブロックを、existing のハーフブロックのマスへ重ねて置けるか（バニラのハーフブロックの決まり）。 */
        boolean fits(BlockState existing, BlockPlaceContext context);
    }

    public MixedPanelGlassSlab(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GLASS, MixedGlassSlab.Glass.WHITE)
                .setValue(PANEL, MixedPanelSlab.Panel.WHITE).setValue(GLASS_ON_TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLASS, PANEL, GLASS_ON_TOP);
    }

    public static BlockState of(MixedGlassSlab.Glass glass, MixedPanelSlab.Panel panel, boolean glassOnTop) {
        return SinguloBlocks.MIXED_PANEL_GLASS_SLAB.get().defaultBlockState().setValue(GLASS, glass).setValue(PANEL, panel)
                .setValue(GLASS_ON_TOP, glassOnTop);
    }

    /**
     * 持っている held のハーフブロックを、existing のハーフブロックのマスへ置いたときの重ねた状態。ガラスとパネルの組でなければ null。
     */
    @Nullable
    public static BlockState stack(BlockState existing, Block held) {
        if (!(existing.getBlock() instanceof Stackable theirs) || !(held instanceof Stackable mine)
                || !existing.hasProperty(SlabBlock.TYPE) || existing.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return null;
        }
        boolean theirsOnTop = existing.getValue(SlabBlock.TYPE) == SlabType.TOP;
        MixedGlassSlab.Glass glass = MixedGlassSlab.Glass.of(mine.base());
        MixedPanelSlab.Panel panel = MixedPanelSlab.Panel.of(theirs.base());
        if (glass != null && panel != null) {
            return of(glass, panel, !theirsOnTop);                                // 持っているのがガラス
        }
        glass = MixedGlassSlab.Glass.of(theirs.base());
        panel = MixedPanelSlab.Panel.of(mine.base());
        return glass != null && panel != null ? of(glass, panel, theirsOnTop) : null;
    }

    /** 上（top）か下のハーフブロック。 */
    public static BlockState half(BlockState mixed, boolean top) {
        boolean glass = mixed.getValue(GLASS_ON_TOP) == top;
        String base = glass ? mixed.getValue(GLASS).base : mixed.getValue(PANEL).base;
        return SinguloBlocks.VARIANTS.get(base + "_slab").get().defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    /** 不透明な所（パネルの半分）の形。 */
    private static VoxelShape panelShape(BlockState mixed) {
        return half(mixed, !mixed.getValue(GLASS_ON_TOP)).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** 隣 adjacent のうち、向こうが見えない（不透明な）所の形。 */
    private static VoxelShape opaque(BlockState adjacent) {
        if (adjacent.getBlock() instanceof MixedPanelGlassSlab) {
            return panelShape(adjacent);
        }
        return adjacent.canOcclude() ? adjacent.getOcclusionShape() : Shapes.empty();
    }

    /**
     * 上（top）か下の半分の direction 側の面が、隣 adjacent に隠れるか。ガラスの半分は、この作品のガラス（{@link GlassJoin#matchingPart}）
     * か不透明な物に覆われると隠れ、パネルの半分は不透明な物に覆われると隠れる。その面がない半分（上の半分の下の面など）は隠れる扱い。
     */
    public static boolean halfHidden(BlockState state, boolean top, BlockState adjacent, Direction direction) {
        if (direction == (top ? Direction.DOWN : Direction.UP)) {
            return true;
        }
        BlockState slab = half(state, top);
        VoxelShape shape = slab.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (Shapes.blockOccludes(shape, opaque(adjacent), direction)) {
            return true;
        }
        if (state.getValue(GLASS_ON_TOP) != top) {
            return false;
        }
        BlockState part = GlassJoin.matchingPart(adjacent, ((Stackable) slab.getBlock()).base());
        return part != null && GlassJoin.covered(slab, part, direction);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return halfHidden(state, false, adjacent, direction) && halfHidden(state, true, adjacent, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;                                           // 半分は透けるので、ガラスと同じく周りを暗くしない
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    private static BlockState selected(BlockState state, BlockPos pos, HitResult hit) {
        boolean top = !(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)
                || MixedGlassSlab.topAt(pos, hit.getLocation().y);
        return half(state, top);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        HitResult target = player.pick(player.blockInteractionRange(), 1, false);
        return new ItemStack(selected(state, pos, target).getBlock());
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return selected(state, pos, player.pick(player.blockInteractionRange(), 1, false)).getDestroyProgress(player, level, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState surface = entity instanceof Player player
                ? selected(state, pos, player.pick(player.blockInteractionRange(), 1, false)) : half(state, true);
        return surface.getSoundType(level, pos, entity);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return half(state, MixedGlassSlab.topAt(pos, explosion.center().y)).getExplosionResistance(level, pos, explosion);
    }

    /** 掘って落とすのは、道具が上下どちらのハーフブロックも掘れるときだけ（コラプサーの方はダイヤ以上のツルハシ）。 */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.hasCorrectToolForDrops(half(state, false), player.level(), pos) && player.hasCorrectToolForDrops(half(state, true), player.level(), pos);
    }

    /** コラプサーのハーフブロックを含むと、ウィザー（体当たり・頭蓋骨）とエンダードラゴンには壊されない。 */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        boolean collapsar = state.getValue(GLASS) == MixedGlassSlab.Glass.COLLAPSAR || state.getValue(PANEL) == MixedPanelSlab.Panel.COLLAPSAR;
        if (collapsar && (entity instanceof WitherBoss || entity instanceof WitherSkull || entity instanceof EnderDragon)) {
            return false;
        }
        return super.canEntityDestroy(state, level, pos, entity);
    }
}
