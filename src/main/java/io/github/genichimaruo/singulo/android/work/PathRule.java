package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 経路の決まり（作業指令台・組立ステーションの「経路」の1行）: 必須経由地点と、その対象範囲、進入禁止ゾーン。
 * <ul>
 * <li>対象範囲の中と外を行き来する移動（作業・巡回）では、経由地点（作業位置カードの場所の上）を通る（拠点の扉のように）。</li>
 * <li>その移動のあいだは、進入禁止ゾーン（範囲カード）に入らない。ただし、出発した所・行き先がゾーンの中なら、そのゾーンは避けない（出入りできなくなるので）。</li>
 * </ul>
 * 範囲は、記録したブロックの高さから、その上2マスまでを含む（床のブロックを記録しても、その上を歩くアンドロイドに効くように）。
 */
public record PathRule(BlockPos via, BoundingBox scope, List<BoundingBox> zones) {
    /** 行の数と、1行の欄（経由地点・対象範囲・進入禁止ゾーン2つ）。 */
    public static final int ROWS = 4, COLS = 4, SLOTS = ROWS * COLS;

    /** 欄に置けるカードか（列 col: 0 は作業位置カード、ほかは範囲カード）。 */
    public static boolean fits(int col, ItemStack stack) {
        return col == 0 ? WorkLocationCardItem.isCard(stack) : AreaCardItem.isCard(stack);
    }

    /** 欄（start から {@link #SLOTS} 個）のカードから決まりを読む（経由地点と対象範囲がそろい、この世界のものだけ）。 */
    public static List<PathRule> read(IItemHandler items, int start, Level level) {
        List<PathRule> out = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            ItemStack via = items.getStackInSlot(start + row * COLS);
            ItemStack scope = items.getStackInSlot(start + row * COLS + 1);
            BlockPos p = WorkLocationCardItem.pos(via);
            BoundingBox box = AreaCardItem.box(scope);
            if (p == null || box == null || !WorkLocationCardItem.sameDimension(via, level) || !AreaCardItem.sameDimension(scope, level)) {
                continue;
            }
            List<BoundingBox> zones = new ArrayList<>();
            for (int z = 2; z < COLS; z++) {
                ItemStack zone = items.getStackInSlot(start + row * COLS + z);
                BoundingBox zb = AreaCardItem.box(zone);
                if (zb != null && AreaCardItem.sameDimension(zone, level)) {
                    zones.add(zb);
                }
            }
            out.add(new PathRule(p.immutable(), box, List.copyOf(zones)));
        }
        return out;
    }

    /** 範囲に入っているか（記録したブロックの高さの1マス下から、上2マスまで）。 */
    public static boolean inside(BoundingBox box, double x, double y, double z) {
        return x >= box.minX() && x < box.maxX() + 1 && z >= box.minZ() && z < box.maxZ() + 1 && y >= box.minY() - 1 && y < box.maxY() + 3;
    }

    /** from から to への移動が、対象範囲の中と外を行き来するか。 */
    public boolean crosses(Vec3 from, Vec3 to) {
        return inside(scope, from.x, from.y, from.z) != inside(scope, to.x, to.y, to.z);
    }

    /** 経由地点で立つ所（作業位置カードの場所の上）。 */
    public Vec3 stand() {
        return Vec3.atBottomCenterOf(via.above());
    }
}
