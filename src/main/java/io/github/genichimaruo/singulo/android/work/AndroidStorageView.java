package io.github.genichimaruo.singulo.android.work;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.items.IItemHandler;

/**
 * アンドロイドの素材置き場として見せる中身を、ふつうの搬入出とは別に持つ入れ物（ドローン発着ポートなど。梱包済みの箱や
 * 発送待ちの荷物を素材に数えない）。資材口や作業指令台が隣を調べるときは、ふつうの窓口の代わりにこれを使う。
 */
public interface AndroidStorageView {
    IItemHandler androidItems(@Nullable Direction side);
}
