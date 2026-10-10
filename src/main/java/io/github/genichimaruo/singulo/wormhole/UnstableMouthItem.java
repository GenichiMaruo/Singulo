package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.item.SinguloItem;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 不安定なワームホールの口。ワームホール生成器が一対で作る。生まれてから LIFETIME tick 以内に固定化装置へ入れないと消える。
 */
public class UnstableMouthItem extends SinguloItem {
    public static final int LIFETIME = 1200;

    public UnstableMouthItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    /** 新しい対の口を2個作る。 */
    public static ItemStack[] createPair(Level level) {
        long pair = level.random.nextLong();
        ItemStack[] out = new ItemStack[2];
        for (int i = 0; i < 2; i++) {
            out[i] = new ItemStack(SinguloItems.UNSTABLE_WORMHOLE_MOUTH.get());
            SinguloComponents.set(out[i], SinguloComponents.WORMHOLE.get(), new WormholeData(pair, level.getGameTime()));
        }
        return out;
    }

    /** 残りの tick（すでに消えるべきなら 0 以下）。 */
    public static long remaining(ItemStack stack, @Nullable Level level) {
        WormholeData data = SinguloComponents.get(stack, SinguloComponents.WORMHOLE.get());
        if (data == null || level == null) {
            return 0;
        }
        return data.created() + LIFETIME - level.getGameTime();
    }

    public static boolean expired(ItemStack stack, Level level) {
        return stack.getItem() instanceof UnstableMouthItem && remaining(stack, level) <= 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && expired(stack, level)) {
            stack.setCount(0);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("gui.singulo.wormhole.mouth_collapsed"), true);
            }
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && expired(stack, entity.level())) {
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        long left = remaining(stack, context);
        tooltip.add(Component.translatable("tooltip.singulo.wormhole.unstable", Math.max(0, left / 20))
                .withStyle(ChatFormatting.RED));
    }
}
