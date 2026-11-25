package io.github.genichimaruo.singulo.block;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 白いパネル・黒色強化パネル・コラプサー強化パネルのハーフブロックのうち、違う2つを同じマスに上下で重ねたもの（0.23.0）。
 * ガラスの {@link MixedGlassSlab} と同じく、置くときも落とすときも元のハーフブロックのアイテムを使う（壊すと両方が落ちる）。
 * 見た目・掘る速さ・音・爆発への強さは、狙った方（爆発は中心の高さの方）のハーフブロックに合わせる。
 * コラプサーのハーフブロックを含むと、ウィザーとエンダードラゴンには壊されない。
 */
public final class MixedPanelSlab extends Block {
    /** 重ねられるパネル。 */
    public enum Panel implements StringRepresentable {
        WHITE("white", "white_panel"),
        BLACK("black", "black_reinforced_panel"),
        COLLAPSAR("collapsar", "collapsar_reinforced_panel");

        private final String name;
        /** 元の建材の ID（ハーフブロックは <id>_slab）。 */
        public final String base;

        Panel(String name, String base) {
            this.name = name;
            this.base = base;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Nullable
        public static Panel of(Block base) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(base);
            for (Panel p : values()) {
                if (id.getNamespace().equals(io.github.genichimaruo.singulo.Singulo.MODID) && id.getPath().equals(p.base)) {
                    return p;
                }
            }
            return null;
        }
    }

    public static final EnumProperty<Panel> LOWER = EnumProperty.create("lower", Panel.class);
    public static final EnumProperty<Panel> UPPER = EnumProperty.create("upper", Panel.class);

    public MixedPanelSlab(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOWER, Panel.WHITE).setValue(UPPER, Panel.BLACK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOWER, UPPER);
    }

    public static BlockState of(Panel lower, Panel upper) {
        return SinguloBlocks.MIXED_PANEL_SLAB.get().defaultBlockState().setValue(LOWER, lower).setValue(UPPER, upper);
    }

    /** 上（top）か下のハーフブロック。 */
    public static BlockState half(BlockState mixed, boolean top) {
        Panel panel = mixed.getValue(top ? UPPER : LOWER);
        return SinguloBlocks.VARIANTS.get(panel.base + "_slab").get().defaultBlockState()
                .setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
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
        boolean collapsar = state.getValue(LOWER) == Panel.COLLAPSAR || state.getValue(UPPER) == Panel.COLLAPSAR;
        if (collapsar && (entity instanceof WitherBoss || entity instanceof WitherSkull || entity instanceof EnderDragon)) {
            return false;
        }
        return super.canEntityDestroy(state, level, pos, entity);
    }

    /**
     * 3つのパネルのハーフブロック。バニラのハーフブロックの置き方のまま、別のパネルのハーフブロックを重ねると
     * {@link MixedPanelSlab} になる。
     */
    public static final class PairSlab extends SlabBlock implements MixedPanelGlassSlab.Stackable {
        private final Supplier<Block> base;

        public PairSlab(Supplier<Block> base, Properties properties) {
            super(properties);
            this.base = base;
        }

        public Block base() {
            return base.get();
        }

        @Override
        public boolean fits(BlockState existing, BlockPlaceContext context) {
            return super.canBeReplaced(existing, context);
        }

        @Override
        protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            // バニラは持っているアイテムが「このブロック」かを見るので、別のパネルのハーフブロックでも重ねられるようにする
            // （0.23.0: ガラスのハーフブロックも重ねられる。{@link MixedPanelGlassSlab}）
            return Block.byItem(context.getItemInHand().getItem()) instanceof MixedPanelGlassSlab.Stackable held && held.fits(state, context);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
            BlockState withGlass = MixedPanelGlassSlab.stack(existing, this);
            if (withGlass != null) {
                return withGlass;
            }
            if (existing.getBlock() instanceof PairSlab other && other != this && existing.getValue(TYPE) != SlabType.DOUBLE) {
                Panel mine = Panel.of(base()), theirs = Panel.of(other.base());
                if (mine != null && theirs != null) {
                    boolean theirsOnTop = existing.getValue(TYPE) == SlabType.TOP;
                    return of(theirsOnTop ? mine : theirs, theirsOnTop ? theirs : mine);
                }
            }
            return super.getStateForPlacement(context);
        }
    }
}
