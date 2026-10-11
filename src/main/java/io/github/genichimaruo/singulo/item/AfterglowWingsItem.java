package io.github.genichimaruo.singulo.item;

import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * 残光の翼（最後の記録者が落とす）。胴に着ける、光の結晶の羽根でできた一対の翼。
 * <ul>
 *   <li>エリトラのように滑空できる。壊れない</li>
 *   <li>滑空中にスニークすると、羽ばたいて前へ加速する（{@value #FLAP_COOLDOWN} tick に1回）</li>
 *   <li>着けていると落下ダメージを受けない</li>
 *   <li>防御はダイヤモンドの胸当てと同じ（胸当ての代わりに着けても守りが落ちない）</li>
 * </ul>
 * 翼の見た目は AfterglowWingsLayer が描く（記録者の象牙と金の羽根とはちがい、水色の結晶の羽根で先が菫に光る）。
 * クリエイティブのタブにも JEI にも出さない。
 */
public class AfterglowWingsItem extends ArmorItem {
    public static final int FLAP_COOLDOWN = 30;
    public static final double FLAP_SPEED = 0.85;

    /** 防御はダイヤモンドの胸当てと同じ。鎧の絵は描かない（翼は別に描く）。 */
    public static final Holder<ArmorMaterial> MATERIAL = GravityBootsItem.MATERIALS.register("afterglow", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) {
            defense.put(t, t == ArmorItem.Type.CHESTPLATE ? 8 : 0);
        }
        return new ArmorMaterial(defense, 20, SoundEvents.ARMOR_EQUIP_ELYTRA, () -> Ingredient.EMPTY, List.of(), 2.0F, 0.0F);
    });

    public AfterglowWingsItem(Properties properties) {
        super(MATERIAL, ArmorItem.Type.CHESTPLATE, properties.stacksTo(1).fireResistant());
    }

    /** クラスを読み込んで、鎧の素材を登録に間に合わせる（Singulo の初期化から呼ぶ）。 */
    public static void init() {
    }

    public static boolean wearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof AfterglowWingsItem;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return true;                                    // 壊れない
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /** 滑空中のスニークで羽ばたく。滑空中は光の粒の尾を引く。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof Player player) || player.getItemBySlot(EquipmentSlot.CHEST) != stack || !player.isFallFlying()) {
            return;
        }
        if (level.isClientSide) {
            if (player.tickCount % 2 == 0) {
                Vec3 back = player.getLookAngle().scale(-0.6);
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 wing = player.getLookAngle().cross(new Vec3(0, 1, 0));
                    wing = wing.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : wing.normalize().scale(side * 1.4);
                    level.addParticle(ParticleTypes.END_ROD, player.getX() + back.x + wing.x, player.getY() + 0.9 + back.y,
                            player.getZ() + back.z + wing.z, 0, 0, 0);
                }
            }
            return;
        }
        if (player.isShiftKeyDown() && !player.getCooldowns().isOnCooldown(this)) {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(player.getDeltaMovement().add(look.scale(FLAP_SPEED)).add(0, 0.15, 0));
            player.hurtMarked = true;
            player.getCooldowns().addCooldown(this, FLAP_COOLDOWN);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.6F);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 24, 0.8, 0.3, 0.8, 0.08);
            }
        }
    }

    /** 着けていれば落下ダメージなし。 */
    public static void onFall(LivingFallEvent event) {
        if (wearing(event.getEntity())) {
            event.setDistance(0);
            event.setCanceled(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.singulo.afterglow_wings.tip_1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.singulo.afterglow_wings.tip_2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.singulo.afterglow_wings.tip_3").withStyle(ChatFormatting.GRAY));
    }
}
