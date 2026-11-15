package io.github.genichimaruo.singulo.client;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Data-driven tint sources shared by all item display contexts. */
public record SinguloItemTint(String kind) implements ItemTintSource {
    public static final MapCodec<SinguloItemTint> CODEC=Codec.STRING.fieldOf("kind").xmap(SinguloItemTint::new,SinguloItemTint::kind);
    @Override public MapCodec<? extends ItemTintSource> type(){return CODEC;}
    @Override public int calculate(ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity owner){
        return switch(kind){
            case "fluid_sample" -> {
                var fluid=io.github.genichimaruo.singulo.android.work.FluidSamples.fluid(stack);
                yield fluid.isEmpty()?-1:0xFF000000|FluidVisuals.tint(fluid);
            }
            case "fluid_canister" -> {
                var fluid=io.github.genichimaruo.singulo.item.FluidCanisterItem.fluid(stack);
                yield fluid.isEmpty()?0:0xFF000000|FluidVisuals.tint(fluid);
            }
            case "food_cartridge" -> {
                int food=io.github.genichimaruo.singulo.food.FoodPaste.kind(stack);
                yield food<0?0:0xFF000000|io.github.genichimaruo.singulo.food.FoodPaste.COLORS[food];
            }
            case "chemical_sample" -> {
                String id=io.github.genichimaruo.singulo.android.work.ChemicalSamples.id(stack);
                yield id.isEmpty()?-1:0xFF000000|io.github.genichimaruo.singulo.android.work.Chemicals.tint(id);
            }
            case "accessory" -> stack.getItem() instanceof io.github.genichimaruo.singulo.android.AndroidAccessoryItem accessory ? 0xFF000000|accessory.color(stack):-1;
            default -> throw new IllegalArgumentException("Unknown Singulo item tint: "+kind);
        };
    }
}