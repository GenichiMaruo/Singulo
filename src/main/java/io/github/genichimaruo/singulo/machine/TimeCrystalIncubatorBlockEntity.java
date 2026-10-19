package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.item.UsesItem;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 時間結晶育成槽。育成は実際に動いた tick で進み（チャンクが読み込まれている間だけ）、触媒の育成は電力が足りなくてもある分で進む。
 * ただし要求の80%を割った tick の割合だけ純度が下がり、できた触媒の寿命もその割合になる（純度50%なら寿命も半分）。
 * 石炭化・苔・錆のような、触媒でない物の時間加工は、ほかの装置と同じく電力が全部そろわないと進まない。
 */
public class TimeCrystalIncubatorBlockEntity extends MachineBlockEntity {
    public TimeCrystalIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.TIME_CRYSTAL_INCUBATOR.get(), pos, state);
    }

    @Override
    protected boolean runsUnderpowered(MachineRecipe recipe) {
        return recipe.result().getItem() instanceof UsesItem uses && uses.isCatalyst();
    }

    @Override
    protected ItemStack adjustResult(MachineRecipe recipe, ItemStack result, double purity) {
        if (purity >= 1.0 || !(result.getItem() instanceof UsesItem uses) || !uses.isCatalyst()) {
            return result;
        }
        int max = Math.max(1, (int) Math.round(UsesHelper.baseMax(result) * purity));
        SinguloComponents.set(result, SinguloComponents.USES.get(), new UsesData(0, max, 0));
        return result;
    }
}
