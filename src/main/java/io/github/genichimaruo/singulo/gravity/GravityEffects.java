package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.Singulo;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

/**
 * 重力パネルの効き目。電力の届いた重力パネルの上に立つと重力が変わり、地面を離れてもそのまま、
 * 次に重力パネル以外へ着地したときに元へ戻る（別の種類のパネルへ着地したら、そちらに切り替わる）。
 * <ul>
 *   <li>低重力: 重力 ×{@value #LOW_GRAVITY}（高く跳べ、ゆっくり落ちる）。落下ダメージ ×{@value #LOW_FALL_DAMAGE}</li>
 *   <li>高重力: 重力 ×{@value #HIGH_GRAVITY}（跳ねにくく、すばやく落ちる）</li>
 * </ul>
 * サーバーとクライアントの両方で同じ判定をする（プレイヤーの動きはクライアントで計算されるため）。
 */
public final class GravityEffects {
    public static final double LOW_GRAVITY = 0.2;
    public static final double HIGH_GRAVITY = 2.5;
    public static final double LOW_FALL_DAMAGE = 0.25;
    static final java.util.UUID LOW_ID = io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("low_gravity_panel"));
    static final java.util.UUID HIGH_ID = io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("high_gravity_panel"));
    static final java.util.UUID FALL_ID = io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("low_gravity_panel_fall"));

    private GravityEffects() {}

    public static void onEntityTick(LivingTickEvent event) {
        { LivingEntity living = event.getEntity();
            update(living);
        }
    }

    static void update(LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return;
        }
        if (!living.onGround()) {
            return;                                    // 宙にいる間は、乗ったときの重力のまま
        }
        BlockState below = living.level().getBlockState(living.getOnPos());
        GravityPanelBlock.Kind kind = below.getBlock() instanceof GravityPanelBlock panel && below.getValue(GravityPanelBlock.POWERED)
                ? panel.kind() : null;
        apply(living, gravity, kind);
    }

    /** 今の重力の種類（どちらでもなければ null）。 */
    @Nullable
    public static GravityPanelBlock.Kind current(LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return null;
        }
        return (gravity.getModifier(LOW_ID) != null) ? GravityPanelBlock.Kind.LOW : (gravity.getModifier(HIGH_ID) != null) ? GravityPanelBlock.Kind.HIGH : null;
    }

    private static void apply(LivingEntity living, AttributeInstance gravity, @Nullable GravityPanelBlock.Kind kind) {
        boolean low = kind == GravityPanelBlock.Kind.LOW;
        boolean high = kind == GravityPanelBlock.Kind.HIGH;
        set(gravity, LOW_ID, low, LOW_GRAVITY - 1);
        set(gravity, HIGH_ID, high, HIGH_GRAVITY - 1);
        AttributeInstance fall = living.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get());
        if (fall != null) {
            set(fall, FALL_ID, low, LOW_FALL_DAMAGE - 1);
        }
    }

    private static void set(AttributeInstance attribute, java.util.UUID id, boolean on, double amount) {
        boolean has = (attribute.getModifier(id) != null);
        if (on && !has) {
            attribute.addTransientModifier(new AttributeModifier(id, "singulo", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has) {
            attribute.removeModifier(id);
        }
    }
}
