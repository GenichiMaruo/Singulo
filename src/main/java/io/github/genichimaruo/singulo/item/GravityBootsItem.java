package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
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
public class GravityBootsItem extends ArmorItem {
    public static final int WALL_JUMPS = 3;
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Singulo.MODID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register("gravity", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) {
            defense.put(t, t == ArmorItem.Type.BOOTS ? 3 : 0);
        }
        return new ArmorMaterial(defense, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(Singulo.id("gravity"))), 2.0F, 0.0F);
    });

    private final int stage;

    public GravityBootsItem(Properties properties, int stage) {
        super(MATERIAL, ArmorItem.Type.BOOTS, properties.durability(ArmorItem.Type.BOOTS.getDurability(40)));
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
