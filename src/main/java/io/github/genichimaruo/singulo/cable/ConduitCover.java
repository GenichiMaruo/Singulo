package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.pipe.FluidPipeBlock;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelProperty;

/**
 * ケーブル・パイプのカバー。カバーの枠をケーブルに取り付け、枠に本物のブロックをはめ込むと、その場所がそのブロックの見た目になる
 * （見た目は、はめ込んだブロックそのもの）。つながりと送る量は変わらない。
 * <ul>
 *   <li>状態 {@link #PROPERTY}: なし・枠だけ・ブロック入り。枠がある間は当たり判定が1ブロックいっぱい。枠だけの間は、光を通し、
 *       隣に陰を落とさない（ガラスのように）。ブロック入りのときだけ、ふつうのブロックと同じように隣の面を隠し、光をさえぎる。</li>
 *   <li>はめ込んだブロックは、向きも含めた状態で、ブロックエンティティ（{@link Holder}）が覚える。向きは、その場所にふつうに置いたときと
 *       同じ（丸太は右クリックした面の向き、カボチャは顔をこちらへ）。</li>
 *   <li>移設レンチ（装置の移設モード）でスニークして右クリックすると、ブロック → 枠の順に1つずつ外す。壊すと両方を落とす。</li>
 * </ul>
 * はめ込めるのは、すき間のない不透明な1ブロック（向きがあってもよい）で、ブロックエンティティのない物だけ。
 */
public final class ConduitCover {
    public enum State implements StringRepresentable {
        NONE("none"), FRAME("frame"), BLOCK("block");

        private final String name;

        State(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<State> PROPERTY = EnumProperty.create("cover", State.class);
    /** 描画に渡す、はめ込んだブロック（向きを含む状態）。 */
    public static final ModelProperty<BlockState> MODEL_STATE = new ModelProperty<>();
    private static final String TAG = "cover";

    /** はめ込んだブロックを覚えるブロックエンティティ（電線・パイプ）。 */
    public interface Holder {
        @Nullable
        BlockState coverState();

        void setCoverState(@Nullable BlockState state);
    }

    private ConduitCover() {}

    public static boolean isConduit(Block block) {
        return block instanceof CableBlock || block instanceof FluidPipeBlock;
    }

    public static State of(BlockState state) {
        return state.hasProperty(PROPERTY) ? state.getValue(PROPERTY) : State.NONE;
    }

    /** 枠かブロックがあれば1ブロックいっぱい（なければ shape のまま）。 */
    public static VoxelShape shape(BlockState state, VoxelShape shape) {
        return of(state) == State.NONE ? shape : Shapes.block();
    }

    /** 枠だけのときの当たり判定: 厚さ1ドットの壁の中空の箱（外からは1ブロックいっぱいと同じに当たり、中へは入れない）。 */
    public static final VoxelShape HOLLOW =Shapes.join(Shapes.block(), Block.box(1, 1, 1, 15, 15, 15), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

    /**
     * 当たり判定: 枠だけなら中空の箱、ブロック入りなら1ブロックいっぱい、なければ shape のまま。
     * 枠だけのときに1ブロックいっぱいにしないのは、描画が「当たり判定が1ブロックいっぱいのブロック」の内側の面を、外の面と同じに
     * 隣のブロックの明るさで描くため（隣が詰まったブロックや地面だと、枠の内側の面が真っ黒になる）。
     */
    public static VoxelShape collision(BlockState state, VoxelShape shape) {
        return switch (of(state)) {
            case FRAME -> HOLLOW;
            case BLOCK -> Shapes.block();
            case NONE -> shape;
        };
    }

    /** 隣の面を隠す形: ブロック入りのときだけ1ブロックいっぱい。 */
    public static VoxelShape occlusion(BlockState state) {
        return of(state) == State.BLOCK ? Shapes.block() : Shapes.empty();
    }

    /** 光を通すか: ブロック入りのときだけ通さない（枠だけ・カバーなしは、ガラスのように通す）。 */
    public static boolean passesLight(BlockState state) {
        return of(state) != State.BLOCK;
    }

    /** はめ込めるブロックの状態か（すき間のない不透明な1ブロック。向きはあってよい。ブロックエンティティ・ケーブル・水に浸かった物は除く）。 */
    public static boolean allowed(@Nullable BlockState s) {
        if (s == null || s.isAir() || isConduit(s.getBlock()) || s.hasBlockEntity()
                || s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        var level = EmptyBlockGetter.INSTANCE;
        return s.getRenderShape() == RenderShape.MODEL && s.canOcclude()
                && s.isSolidRender(level, BlockPos.ZERO) && s.isCollisionShapeFullBlock(level, BlockPos.ZERO);
    }

    /** ブロックエンティティに保存する。 */
    public static void save(CompoundTag tag, @Nullable BlockState state) {
        if (state != null) {
            tag.put(TAG, NbtUtils.writeBlockState(state));
        }
    }

    /** 保存から読む（はめ込めなくなったブロックでも、外すときに返せるよう、そのまま読む）。 */
    @Nullable
    public static BlockState load(CompoundTag tag) {
        if (!tag.contains(TAG, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        BlockState state = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(TAG));
        return state.isAir() ? null : state;
    }

    /**
     * 手に持った物で、ケーブルを右クリックしたとき。カバーの枠ならケーブルに取り付け、枠だけのところにブロックならはめ込む
     * （向きは、右クリックした面とプレイヤーの向きで、ふつうに置いたときと同じに決める）。
     * それ以外はふつうの右クリック（ブロック入りのカバーにブロックを向けたときは、ふつうに隣へ置ける）。
     */
    public static InteractionResult use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                            BlockHitResult hit) {
        State cover = of(state);
        if (cover == State.NONE && stack.is(SinguloItems.COVER_FRAME.get())) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(PROPERTY, State.FRAME), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (cover == State.FRAME && stack.getItem() instanceof BlockItem item) {
            if (level.getBlockEntity(pos) instanceof Holder pending && pending.coverState() != null) {
                return InteractionResult.sidedSuccess(level.isClientSide);       // はめ込み中（次の tick にブロック入りになる）
            }
            BlockState inlay = item.getBlock().getStateForPlacement(new BlockPlaceContext(player, hand, stack, hit));
            if (!allowed(inlay)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.singulo.cover.not_allowed", stack.getHoverName()), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Holder holder) {
                // はめ込んだブロックを先にクライアントへ送り、ブロック入りの状態にするのは次の tick（settle）。同じ tick で状態を変えると、
                // クライアントは隣（地面など）の面を隠すのに、まだはめ込んだブロックを知らずに枠だけを描き、一瞬向こうが透けて見える
                holder.setCoverState(inlay);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, state.getBlock(), 1);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                var sound = inlay.getSoundType();
                level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** はめ込んだブロックを、持ち物に戻す形にする。 */
    private static ItemStack itemOf(@Nullable BlockState inlay) {
        return inlay == null ? ItemStack.EMPTY : new ItemStack(inlay.getBlock());
    }

    /** はめ込み（use）の次の tick: はめ込んだブロックがあれば、ブロック入りの状態にする。 */
    public static void settle(BlockState state, Level level, BlockPos pos) {
        if (of(state) == State.FRAME && level.getBlockEntity(pos) instanceof Holder holder && holder.coverState() != null) {
            level.setBlock(pos, state.setValue(PROPERTY, State.BLOCK), Block.UPDATE_ALL);
        }
    }

    /** 1つ外す（ブロックがあればブロック（はめ込み中でも）、枠だけなら枠）。外した物（なければ空）。 */
    public static ItemStack takeOne(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        State cover = of(state);
        if (cover != State.NONE && level.getBlockEntity(pos) instanceof Holder holder && holder.coverState() != null) {
            BlockState inlay = holder.coverState();
            holder.setCoverState(null);
            if (cover == State.BLOCK) {
                level.setBlock(pos, state.setValue(PROPERTY, State.FRAME), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
            return itemOf(inlay);
        }
        if (cover == State.FRAME) {
            level.setBlock(pos, state.setValue(PROPERTY, State.NONE), Block.UPDATE_ALL);
            return new ItemStack(SinguloItems.COVER_FRAME.get());
        }
        return ItemStack.EMPTY;
    }

    /** 移設レンチの撤去モードを持っているか（そのときは、カバーごとケーブルを外す）。 */
    private static boolean removesWholeConduit(@Nullable Player player) {
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        return held.getItem() instanceof io.github.genichimaruo.singulo.item.RelocationWrenchItem
                && io.github.genichimaruo.singulo.item.RelocationWrenchItem.mode(held) == io.github.genichimaruo.singulo.item.RelocationWrenchItem.MODE_CABLE;
    }

    /**
     * 壊す速さ: ブロック入りなら、はめ込んだブロックを掘る速さ（石ならツルハシで速い）。枠だけ・カバーなし・撤去モードのレンチは base のまま。
     */
    public static float destroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos, float base) {
        if (of(state) == State.BLOCK && !removesWholeConduit(player) && level.getBlockEntity(pos) instanceof Holder holder
                && holder.coverState() != null) {
            return holder.coverState().getDestroyProgress(player, level, pos);
        }
        return base;
    }

    /** 爆発への強さ: ブロック入りなら、はめ込んだブロックと同じ（黒曜石なら黒曜石と同じに耐える）。ほかは base のまま。 */
    public static float explosionResistance(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                            net.minecraft.world.level.Explosion explosion, float base) {
        if (of(state) == State.BLOCK && level.getBlockEntity(pos) instanceof Holder holder && holder.coverState() != null) {
            return holder.coverState().getExplosionResistance(level, pos, explosion);
        }
        return base;
    }

    /**
     * プレイヤーが壊したとき:カバーがあれば、ケーブルは残して、カバーを1つ外して落とす（ブロック入りならブロック、枠だけなら枠。
     * 創造モードでは落とさない）。外したら true（ケーブルは壊さない）。撤去モードのレンチでは外さず、ケーブルごと壊す（カバーも戻る）。
     */
    public static boolean breakCover(BlockState state, Level level, BlockPos pos, Player player) {
        if (of(state) == State.NONE || removesWholeConduit(player)) {
            return false;
        }
        if (!level.isClientSide) {
            ItemStack off = takeOne(level, pos);
            if (!off.isEmpty() && !player.getAbilities().instabuild) {
                Block.popResource(level, pos, off);
            }
        }
        return true;
    }

    /**
     * ケーブルだけを撤去して、枠を置いたまま残す（枠だけのカバーのとき。ブロック入り・はめ込み中・カバーなしは何もしない）。残したら true。
     * 枠を置いてからケーブルを入れたのと逆の手順（撤去モードのレンチ）。
     */
    public static boolean leaveFrame(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (of(state) != State.FRAME || level.getBlockEntity(pos) instanceof Holder holder && holder.coverState() != null) {
            return false;
        }
        level.setBlock(pos, state.setValue(PROPERTY, State.NONE), Block.UPDATE_CLIENTS);      // 枠を落とさないよう、先に外す
        level.setBlock(pos, io.github.genichimaruo.singulo.registry.SinguloBlocks.COVER_FRAME.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** カバーを全部外して返す（ケーブルはカバーなしに戻す。レンチで撤去する直前に使い、壊れたときに二重に落とさない）。 */
    public static List<ItemStack> strip(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> out = takeAll(level, pos, state);
        if (of(state) != State.NONE) {
            level.setBlock(pos, state.setValue(PROPERTY, State.NONE), Block.UPDATE_CLIENTS);
        }
        return out;
    }

    /** カバーの中身を全部取り出す（ブロックエンティティは空にする。状態はそのまま）。 */
    private static List<ItemStack> takeAll(Level level, BlockPos pos, BlockState state) {
        List<ItemStack> out = new ArrayList<>();
        State cover = of(state);
        if (cover == State.NONE) {
            return out;
        }
        out.add(new ItemStack(SinguloItems.COVER_FRAME.get()));
        if (level.getBlockEntity(pos) instanceof Holder holder) {
            BlockState inlay = holder.coverState();
            holder.setCoverState(null);
            if (inlay != null) {                                                 // はめ込み中（枠の状態）でも返す
                out.add(itemOf(inlay));
            }
        }
        return out;
    }

    /** ケーブルが別のブロックに変わる（壊された）とき、カバーの枠とはめ込んだブロックを落とす。 */
    public static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (level.isClientSide || state.is(newState.getBlock())) {
            return;
        }
        for (ItemStack s : takeAll(level, pos, state)) {
            Block.popResource(level, pos, s);
        }
    }
}
