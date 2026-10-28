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

    /** 触媒の重ねられる数（新品どうしだけ重なる。使いかけは残りが違うので重ならない）。装置の触媒スロットに積める予備の数と同じ。 */
    public static final int CATALYST_STACK = 16;

    public UsesItem(Properties properties, int stage, int defaultMax, boolean catalyst) {
        super(properties.stacksTo(catalyst ? CATALYST_STACK : 64), stage, false);
        this.defaultMax = defaultMax;
        this.catalyst = catalyst;
    }

    public int defaultMax() {
        return defaultMax;
    }

    public boolean isCatalyst() {
        return catalyst;
    }

    /** 新品（と復元品）は重なる（触媒は {@link #CATALYST_STACK} 個まで）。使いかけはそれぞれ残りが違うので1個ずつ。 */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        UsesData data = UsesHelper.get(stack);
        return data.used() > 0 || data.remaining() == 0 ? 1 : super.getMaxStackSize(stack);
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
                    lifetime(data.remaining()), lifetime(data.max())).withStyle(ChatFormatting.GRAY));
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

    /** 触媒の寿命（tick）を「45分」「12分30秒」「40秒」の形にする。触媒の使用回数はどこでもこの形で見せる。 */
    public static Component lifetime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        int minutes = seconds / 60;
        if (minutes == 0) {
            return Component.translatable("tooltip.singulo.seconds", seconds);
        }
        return seconds % 60 == 0 ? Component.translatable("tooltip.singulo.minutes", minutes)
                : Component.translatable("tooltip.singulo.min_sec", minutes, seconds % 60);
    }

    /** 触媒（寿命で数える物）か。 */
    public static boolean isCatalyst(ItemStack stack) {
        return stack.getItem() instanceof UsesItem u && u.isCatalyst();
    }
}
