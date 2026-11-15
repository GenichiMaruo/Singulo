package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 重力ブーツ（封印コンテナの量子錠から）。履いていると落下ダメージを受けない。
 * 空中で壁に触れてジャンプすると、壁を蹴って跳べる（着地までに {@link #WALL_JUMPS} 回。操作はクライアントの GravityBootsClient）。
 */
public class GravityBootsItem extends io.github.genichimaruo.singulo.compat.LegacyItem {
    public static final int WALL_JUMPS = 3;
    public static final ArmorMaterial MATERIAL = new ArmorMaterial(40,
            java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.BOOTS, 3), 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.0F,
            net.minecraft.tags.TagKey.create(Registries.ITEM, Singulo.id("non_repairable")),
            net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Singulo.id("gravity")));

    private final int stage;

    public GravityBootsItem(Properties properties, int stage) {
        super(properties.humanoidArmor(MATERIAL, net.minecraft.world.item.equipment.ArmorType.BOOTS));
        this.stage = stage;
    }

    public int stage() {
        return stage;
    }

    public static boolean wearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof GravityBootsItem;
    }

    /** 履いていれば落下ダメージなし。 */
    public static void onFall(LivingFallEvent event) {
        if (wearing(event.getEntity())) {
            event.setDistance(0);
            event.setCanceled(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SinguloItem.addStageLine(tooltip, stage);
    }
}
