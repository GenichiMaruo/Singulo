package io.github.genichimaruo.singulo.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 使用回数（触媒は寿命）を持つアイテム。耐久値バーで残りを示す。 */
public class UsesItem extends SinguloItem {
    private final int defaultMax;
    private final boolean catalyst;

    public UsesItem(Properties properties, int stage, int defaultMax, boolean catalyst) {
        super(properties.stacksTo(1), stage, false);
        this.defaultMax = defaultMax;
        this.catalyst = catalyst;
    }

    public int defaultMax() {
        return defaultMax;
    }

    public boolean isCatalyst() {
        return catalyst;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return UsesHelper.get(stack).used() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        UsesData data = UsesHelper.get(stack);
        return data.max() <= 0 ? 0 : Math.round(13.0F * data.remaining() / data.max());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        UsesData data = UsesHelper.get(stack);
        float f = data.max() <= 0 ? 0 : (float) data.remaining() / data.max();
        // 残りが多いほど水色、少ないほど琥珀色（発光ラインの状態色に合わせる）
        return Mth.color(Mth.lerp(f, 1.0F, 0.47F), Mth.lerp(f, 0.7F, 0.82F), Mth.lerp(f, 0.2F, 0.94F));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        UsesData data = UsesHelper.get(stack);
        if (catalyst) {
            tooltip.add(Component.translatable("tooltip.singulo.lifetime",
                    formatTicks(data.remaining()), formatTicks(data.max())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.singulo.uses", data.remaining(), data.max())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.repairs() > 0) {
            tooltip.add(Component.translatable("tooltip.singulo.repairs", data.repairs(), UsesHelper.maxRepairs())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!catalyst && data.remaining() == 0) {
            tooltip.add(Component.translatable(UsesHelper.canRestore(stack)
                    ? "tooltip.singulo.worn_out" : "tooltip.singulo.beyond_repair").withStyle(ChatFormatting.GOLD));
        }
    }

    static String formatTicks(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
