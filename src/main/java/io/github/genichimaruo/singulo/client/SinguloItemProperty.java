package io.github.genichimaruo.singulo.client;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.Nullable;

/** Preserves the former card, wrench, glasses and compass predicates in item definitions. */
public final class SinguloItemProperty implements RangeSelectItemModelProperty {
    public static final MapCodec<SinguloItemProperty> CODEC=Codec.STRING.fieldOf("kind").xmap(SinguloItemProperty::new,p->p.kind);
    private final String kind;
    private final CompassAngle compass=new CompassAngle(true,CompassAngleState.CompassTarget.LODESTONE);
    public SinguloItemProperty(String kind){this.kind=kind;}
    @Override public MapCodec<? extends RangeSelectItemModelProperty> type(){return CODEC;}
    @Override public float get(ItemStack stack,@Nullable ClientLevel level,@Nullable ItemOwner owner,int seed){
        return switch(kind){
            case "area_card" -> io.github.genichimaruo.singulo.android.work.AreaCardItem.box(stack)!=null?1:io.github.genichimaruo.singulo.android.work.AreaCardItem.first(stack)!=null?0.5F:0;
            case "work_location_card" -> io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.pos(stack)!=null?1:0;
            case "survey_glasses" -> io.github.genichimaruo.singulo.android.work.SurveyGlassesItem.heightPredicate(stack);
            case "relocation_wrench" -> io.github.genichimaruo.singulo.item.RelocationWrenchItem.mode(stack);
            case "explorer_compass", "meteor_compass" -> {
                ItemStack copy=stack.copy();
                copy.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(java.util.Optional.ofNullable(io.github.genichimaruo.singulo.item.ExplorerCompassItem.pointing(stack)),false));
                yield compass.get(copy,level,owner,seed);
            }
            default -> throw new IllegalArgumentException("Unknown Singulo numeric item property: "+kind);
        };
    }
}