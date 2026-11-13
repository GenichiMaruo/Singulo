package io.github.genichimaruo.singulo.compat.mekanism;

import io.github.genichimaruo.singulo.android.work.QioStores;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.inventory.qio.IQIOComponent;
import mekanism.api.inventory.qio.IQIOFrequency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.items.IItemHandler;

/**
 * QIO ダッシュボードの裏につないだ作業のブロックへ、その周波数の中身を入れ物として見せる（Mekanism の API だけを使う）。
 * <ul>
 *   <li>ダッシュボードの向き（facing）は画面の向き。裏はその反対で、作業のブロックからダッシュボードへの向きが facing と同じなら裏が接している。</li>
 *   <li>周波数が決まっていない（リンクしていない）ダッシュボードは使わない。</li>
 *   <li>同じ周波数のダッシュボードは、何枚つないでも同じ入れ物を返す（数えるのは1回）。</li>
 *   <li>中身は、種類ごとに1枠（数は QIO の量。int の上限まで）と、入れるための空き枠1つ。枠の並びは同じ tick の間は変えない
 *       （出し入れした分は数だけ直し、新しく入った種類は次の tick から並ぶ）。</li>
 * </ul>
 */
public final class MekanismQio implements QioStores.Access {
    private static final ResourceLocation DASHBOARD = new ResourceLocation("mekanism", "qio_dashboard");
    /**
     * 周波数ごとの入れ物（同じ周波数のダッシュボードを何枚つないでも、同じ入れ物を返す。集める側は同じ入れ物を1回だけ数える）。
     * 周波数が消えたら忘れる。
     */
    private static final java.util.Map<IQIOFrequency, java.lang.ref.WeakReference<Handler>> HANDLERS = new java.util.WeakHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public int id(IItemHandler handler) {
        return handler instanceof Handler h ? h.id : 0;
    }

    @Nullable
    @Override
    public IItemHandler dashboard(Level level, BlockPos pos, Direction toDashboard) {
        BlockState state = level.getBlockState(pos);
        if (!DASHBOARD.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())) || facing(state) != toDashboard) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof IQIOComponent component)) {
            return null;
        }
        // No player identity is carried by the automation API. Respect effective block
        // security (including owner overrides), and fail closed if capability is absent.
        if (!publicAccess(level, pos)) {
            return null;
        }
        IQIOFrequency frequency = component.getQIOFrequency();
        if (frequency == null) {
            return null;
        }
        return handler(level, frequency);
    }

    private static final net.minecraftforge.common.capabilities.Capability<mekanism.api.security.ISecurityObject> SECURITY =
            net.minecraftforge.common.capabilities.CapabilityManager.get(new net.minecraftforge.common.capabilities.CapabilityToken<>() {});

    static mekanism.api.security.ISecurityObject securityCapability(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.getCapability(SECURITY).orElse(null);
    }

    static boolean publicAccess(Level level, BlockPos pos) {
        var security = securityCapability(level, pos);
        return security != null && mekanism.api.security.ISecurityUtils.INSTANCE
                .getEffectiveSecurityMode(security, level.isClientSide) == mekanism.api.security.SecurityMode.PUBLIC;
    }

    // Weak values are essential: a Handler itself holds its frequency and world.
    static IItemHandler handler(Level level, IQIOFrequency frequency) {
        var reference = HANDLERS.get(frequency);
        Handler handler = reference == null ? null : reference.get();
        if (handler == null) {
            handler = new Handler(level, frequency);
            HANDLERS.put(frequency, new java.lang.ref.WeakReference<>(handler));
        }
        handler.level = level;
        return handler;
    }

    @Nullable
    private static Direction facing(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof DirectionProperty d && "facing".equals(d.getName())) {
                return state.getValue(d);
            }
        }
        return null;
    }

    /** QIO の周波数を、種類ごとの枠の入れ物として見せる。 */
    private static final class Handler implements IItemHandler {
        private final int id = IDS.incrementAndGet();
        private Level level;
        private final IQIOFrequency frequency;
        private List<ItemStack> types;
        private long builtAt = Long.MIN_VALUE;

        Handler(Level level, IQIOFrequency frequency) {
            this.level = level;
            this.frequency = frequency;
        }

        private List<ItemStack> types() {
            long now = level.getGameTime();
            if (types == null || builtAt != now) {
                List<ItemStack> list = new ArrayList<>();
                frequency.forAllStored((stack, count) -> {
                    if (!stack.isEmpty() && count > 0) {
                        list.add(stack.copyWithCount((int) Math.min(Integer.MAX_VALUE, count)));
                    }
                });
                types = list;
                builtAt = now;
            }
            return types;
        }

        @Override
        public int getSlots() {
            return types().size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemStack> t = types();
            return slot >= 0 && slot < t.size() ? t.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // Capture before mutation, otherwise the first insertion of a tick is counted twice.
            List<ItemStack> t = types();
            long in = frequency.massInsert(stack, stack.getCount(), simulate ? Action.SIMULATE : Action.EXECUTE);
            if (in <= 0) {
                return stack;
            }
            if (!simulate) {
                for (int i = 0; i < t.size(); i++) {
                    if (ItemStack.isSameItemSameTags(t.get(i), stack)) {
                        t.set(i, t.get(i).copyWithCount((int) Math.min(Integer.MAX_VALUE, t.get(i).getCount() + in)));
                        break;
                    }
                }
            }
            return in >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - in));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            List<ItemStack> t = types();
            if (amount <= 0 || slot < 0 || slot >= t.size() || t.get(slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack type = t.get(slot);
            long out = frequency.massExtract(type, Math.min(amount, type.getMaxStackSize()), simulate ? Action.SIMULATE : Action.EXECUTE);
            if (out <= 0) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                int left = (int) Math.max(0, type.getCount() - out);
                t.set(slot, left > 0 ? type.copyWithCount(left) : ItemStack.EMPTY);
            }
            return type.copyWithCount((int) out);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
