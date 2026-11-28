package io.github.genichimaruo.singulo.android.program;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * プログラムカード（0.24.0）。持って使うと、プログラムを書く画面が開く。アンドロイド組立ステーションのプログラムの欄に入れ、
 * 動き方を「プログラム」にすると、受け持つアンドロイドがその通りに動く。最初の行がコメント（{@code # …}）なら、それを説明として出す。
 */
public class ProgramCardItem extends Item {
    public ProgramCardItem(Properties properties) {
        super(properties);
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof ProgramCardItem;
    }

    /** 書いてある文（なければ空）。 */
    public static String source(ItemStack stack) {
        return stack.getOrDefault(SinguloComponents.PROGRAM.get(), "");
    }

    public static void setSource(ItemStack stack, String source) {
        if (source.isBlank()) {
            stack.remove(SinguloComponents.PROGRAM.get());
        } else {
            stack.set(SinguloComponents.PROGRAM.get(), source);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            io.github.genichimaruo.singulo.client.ProgramEditorScreen.open(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String source = source(stack);
        if (source.isBlank()) {
            tooltip.add(Component.translatable("tooltip.singulo.program_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        String title = title(source);
        if (!title.isEmpty()) {
            tooltip.add(Component.literal(title).withStyle(ChatFormatting.AQUA));
        }
        long lines = source.lines().filter(l -> !l.isBlank()).count();
        tooltip.add(Component.translatable("tooltip.singulo.program_card.lines", lines).withStyle(ChatFormatting.GRAY));
        ProgramError e = checked(source);
        if (e != null) {
            tooltip.add(e.describe().copy().withStyle(ChatFormatting.RED));
        }
    }

    /** 説明は毎フレーム作り直すので、最後に確かめた文の結果を覚えておく。 */
    private static String lastChecked = "";
    @javax.annotation.Nullable
    private static ProgramError lastError;

    @javax.annotation.Nullable
    private static synchronized ProgramError checked(String source) {
        if (!source.equals(lastChecked)) {
            lastChecked = source;
            lastError = Program.check(source);
        }
        return lastError;
    }

    /** 最初の行のコメント（{@code # } を除いたもの。なければ空）。 */
    public static String title(String source) {
        for (String line : source.lines().toList()) {
            String s = line.strip();
            if (s.isEmpty()) {
                continue;
            }
            return s.startsWith("#") ? s.substring(1).strip() : "";
        }
        return "";
    }
}
