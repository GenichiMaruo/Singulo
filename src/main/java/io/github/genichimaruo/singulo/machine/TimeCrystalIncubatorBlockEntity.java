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
 * 時間結晶育成槽。育成は実際に動いた tick で進み（チャンクが読み込まれている間だけ）、電力が足りなくてもある分で進む。
 * ただし要求の80%を割った tick の割合だけ純度が下がり、できた触媒の寿命もその割合になる（純度50%なら寿命も半分）。
 */
public class TimeCrystalIncubatorBlockEntity extends MachineBlockEntity {
    public TimeCrystalIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.TIME_CRYSTAL_INCUBATOR.get(), pos, state);
    }

    @Override
    protected boolean runsUnderpowered() {
        return true;
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
