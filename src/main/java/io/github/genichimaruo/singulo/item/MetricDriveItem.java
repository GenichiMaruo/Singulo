package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * メトリック・ドライブ（段階5）。持ち物に入れておくと、周りの時空の計量を変えて動きを変える。右クリックでモードを切り替える。
 * <ul>
 *   <li>低重力: 重力 ×0.25、落下ダメージ ×0.25（高く跳び、ゆっくり落ちる）</li>
 *   <li>無重力: 飛行できる。落下ダメージなし</li>
 *   <li>高重力: 重力 ×1.5、ノックバック無効</li>
 * </ul>
 * 動いている間はエキゾチック物質を CHARGE_PER_MATTER tick ごとに1個使う。持ち物に複数あれば最初の1個だけ効く。
 * 設計書では Curios 枠の装着装置だが、依存を増やさないため持ち物に入れるだけで効くようにした。
 */
public class MetricDriveItem extends SinguloItem {
    public enum Mode { OFF, LOW_GRAVITY, ZERO_G, HIGH_GRAVITY }

    public static final int CHARGE_PER_MATTER = 12_000;

    private static final ResourceLocation MODIFIER = Singulo.id("metric_drive");
    private record Effect(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {}

    public MetricDriveItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    public static Mode mode(ItemStack stack) {
        Integer m = stack.get(SinguloComponents.GRAVITY_MODE.get());
        return Mode.values()[m == null ? 0 : Mth.clamp(m, 0, Mode.values().length - 1)];
    }

    private static List<Effect> effects(Mode mode) {
        return switch (mode) {
            case OFF -> List.of();
            case LOW_GRAVITY -> List.of(
                    new Effect(Attributes.GRAVITY, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Effect(Attributes.FALL_DAMAGE_MULTIPLIER, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            case ZERO_G -> List.of(
                    new Effect(NeoForgeMod.CREATIVE_FLIGHT, 1, AttributeModifier.Operation.ADD_VALUE),
                    new Effect(Attributes.FALL_DAMAGE_MULTIPLIER, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            case HIGH_GRAVITY -> List.of(
                    new Effect(Attributes.GRAVITY, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Effect(Attributes.KNOCKBACK_RESISTANCE, 1, AttributeModifier.Operation.ADD_VALUE),
                    new Effect(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1, AttributeModifier.Operation.ADD_VALUE));
        };
    }

    private static final List<Holder<Attribute>> ALL = List.of(Attributes.GRAVITY, Attributes.FALL_DAMAGE_MULTIPLIER,
            NeoForgeMod.CREATIVE_FLIGHT, Attributes.KNOCKBACK_RESISTANCE, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            stack.set(SinguloComponents.GRAVITY_MODE.get(), next.ordinal());
            player.displayClientMessage(Component.translatable("tooltip.singulo.gauntlet.mode", modeName(next)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    static Component modeName(Mode mode) {
        return Component.translatable("metric_drive.singulo.mode." + mode.name().toLowerCase());
    }

    /** 持ち物の中で最初に動いているドライブ。 */
    public static ItemStack activeDrive(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof MetricDriveItem && mode(s) != Mode.OFF) {
                return s;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof MetricDriveItem && mode(off) != Mode.OFF ? off : ItemStack.EMPTY;
    }

    /** サーバー側で毎tick、ドライブの効果をかける（または外す）。 */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** 1 tick ぶん、ドライブの効果をかける（または外す）。サーバー側だけで呼ぶ。 */
    public static void tick(Player player) {
        ItemStack drive = activeDrive(player);
        Mode mode = Mode.OFF;
        if (!drive.isEmpty()) {
            if (ExoticCharge.draw(player, drive, CHARGE_PER_MATTER)) {
                mode = mode(drive);
            } else if (player.tickCount % 100 == 0) {
                player.displayClientMessage(Component.translatable("metric_drive.singulo.no_exotic"), true);
            }
        }
        apply(player, mode);
    }

    static void apply(Player player, Mode mode) {
        List<Effect> wanted = effects(mode);
        for (Holder<Attribute> attribute : ALL) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            Effect effect = wanted.stream().filter(e -> e.attribute.equals(attribute)).findFirst().orElse(null);
            if (effect == null) {
                if (instance.hasModifier(MODIFIER)) {
                    instance.removeModifier(MODIFIER);
                }
            } else {
                AttributeModifier current = instance.getModifier(MODIFIER);
                if (current == null || current.amount() != effect.amount || current.operation() != effect.op) {
                    instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, effect.amount, effect.op));
                }
            }
        }
        if (mode == Mode.ZERO_G || mode == Mode.LOW_GRAVITY) {
            player.fallDistance = Math.min(player.fallDistance, 3);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.gauntlet.mode", modeName(mode(stack))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.exotic_charge", ExoticCharge.get(stack) / 20)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.metric_drive.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
