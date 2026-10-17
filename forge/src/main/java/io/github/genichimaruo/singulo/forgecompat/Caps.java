package io.github.genichimaruo.singulo.forgecompat;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloCapabilities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * Forge 版: NeoForge の level.getCapability(鍵, 位置, 向き) と level.invalidateCapabilities(位置) の代わり。
 * 機能は {@link SinguloCapabilities#register} の登録内容を、Forge の AttachCapabilitiesEvent で付ける。
 * 中身は聞かれるたびに登録した関数から取り直す（NeoForge と同じ）。中身が前と変わったら、前に渡した LazyOptional を無効にする。
 */
@Mod.EventBusSubscriber(modid = Singulo.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Caps {
    private Caps() {}

    private static RegisterCapabilitiesEvent registered;
    // ブロックエンティティごとの、付けた機能（getCapability の書き足しから先に聞く）
    private static final Map<BlockEntity, Provider<BlockEntity>> PROVIDERS = new java.util.WeakHashMap<>();

    private static synchronized RegisterCapabilitiesEvent registered() {
        if (registered == null) {
            RegisterCapabilitiesEvent event = new RegisterCapabilitiesEvent();
            SinguloCapabilities.register(event);
            registered = event;
        }
        return registered;
    }

    @Nullable
    public static <T, C> T find(Level level, BlockCapability<T, C> cap, BlockPos pos, @Nullable C context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        return be.getCapability(cap.forge(), (Direction) context).resolve().orElse(null);
    }

    @Nullable
    public static <T, C> T find(ItemStack stack, ItemCapability<T, C> cap) {
        return stack.isEmpty() ? null : stack.getCapability(cap.forge()).resolve().orElse(null);
    }

    /**
     * ブロックエンティティの getCapability の書き足しから呼ぶ。登録した機能があればそれを返す（null なら Forge の既定に任せる）。
     * 入れ物のブロックエンティティは Forge が自分でアイテムの機能を出すので、登録した方（中身を見せない等）を先にする。
     */
    @Nullable
    public static <T> LazyOptional<T> override(BlockEntity be, Capability<T> cap, @Nullable Direction side) {
        Provider<BlockEntity> provider;
        synchronized (PROVIDERS) {
            provider = PROVIDERS.get(be);
        }
        return provider != null && provider.handles(cap) ? provider.getCapability(cap, side) : null;
    }

    public static void invalidate(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            be.invalidateCaps();
            be.reviveCaps();
        }
    }

    @SubscribeEvent
    public static void attachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity be = event.getObject();
        var entries = registered().blocks.get(be.getType());
        if (entries != null) {
            Provider<BlockEntity> provider = attach(event, be, entries);
            synchronized (PROVIDERS) {
                PROVIDERS.put(be, provider);
            }
        }
    }

    @SubscribeEvent
    public static void attachItem(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        // Forge 1.21.1 はバケツに液体の機能を付けない（NeoForge は付ける）。タンクや装置へバケツで出し入れできるよう、ここで付ける
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem || stack.getItem() == net.minecraft.world.item.Items.MILK_BUCKET) {
            event.addCapability(Singulo.id("bucket"), new net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper(stack));
        }
        var entries = registered().items.get(stack.getItem());
        if (entries != null) {
            attach(event, stack, entries);
        }
    }

    private static <O> Provider<O> attach(AttachCapabilitiesEvent<?> event, O owner, List<RegisterCapabilitiesEvent.Entry<O>> entries) {
        Provider<O> provider = new Provider<>(owner, entries);
        event.addCapability(Singulo.id("capabilities"), provider);
        event.addListener(provider::invalidate);
        return provider;
    }

    private static final class Provider<O> implements ICapabilityProvider {
        private record Issued(Object value, LazyOptional<?> optional) {}

        private final O owner;
        private final List<RegisterCapabilitiesEvent.Entry<O>> entries;
        // 登録の番号と向きごとに、最後に渡した中身（同じ中身なら同じ LazyOptional を返す）
        private final Map<Integer, Issued> issued = new HashMap<>();

        Provider(O owner, List<RegisterCapabilitiesEvent.Entry<O>> entries) {
            this.owner = owner;
            this.entries = entries;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                if (entry.forgeCap() != cap) {
                    continue;
                }
                Object value = entry.provider().getCapability(owner, side);
                if (value == null) {
                    continue;
                }
                int key = i * 7 + (side == null ? 6 : side.ordinal());
                synchronized (issued) {
                    Issued last = issued.get(key);
                    if (last != null && last.value() == value && last.optional().isPresent()) {
                        return last.optional().cast();
                    }
                    if (last != null) {
                        last.optional().invalidate();
                    }
                    LazyOptional<Object> result = LazyOptional.of(() -> value);
                    issued.put(key, new Issued(value, result));
                    return result.cast();
                }
            }
            return LazyOptional.empty();
        }

        boolean handles(Capability<?> cap) {
            for (var entry : entries) {
                if (entry.forgeCap() == cap) {
                    return true;
                }
            }
            return false;
        }

        void invalidate() {
            synchronized (issued) {
                issued.values().forEach(last -> last.optional().invalidate());
                issued.clear();
            }
        }
    }
}
