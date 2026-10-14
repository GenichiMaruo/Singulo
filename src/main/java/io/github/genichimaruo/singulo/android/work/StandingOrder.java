package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 作業指令台に登録した注文。きっかけ（在庫を保つ・信号で1回・信号の間だけ在庫を保つ）が来たら、指令台が自分で注文する。
 * <ul>
 * <li>在庫を保つ: 素材置き場の在庫が count を下回ったら、count までの不足分を注文する（できた物は素材置き場へ）。</li>
 * <li>信号で1回: 指令台にレッドストーン信号が入る（立ち上がり）たびに、count 個を注文する（納品先へ）。
 * 素材が足りなければ、足りるまで待つ。進行中に来た信号は数えない。</li>
 * <li>信号の間だけ在庫を保つ: 信号が入っている間だけ「在庫を保つ」。</li>
 * </ul>
 * 同じ登録から、注文を重ねて出さない。アンドロイドを選んでいなければ、選べる全員で作る。
 */
public final class StandingOrder {
    public static final int KEEP = 0, PULSE = 1, KEEP_POWERED = 2, TRIGGERS = 3;
    public static final String[] TRIGGER_KEYS = {"keep", "pulse", "keep_powered"};
    /** 様子（画面の表示）。 */
    public static final int IDLE = 0, RUNNING = 1, WAITING = 2, NO_SIGNAL = 3;

    public final int id;
    public final ItemStack target;
    public final long count;
    public final int trigger;
    @Nullable
    public final BlockPos destination;
    public final List<UUID> androids;
    @Nullable
    public final UUID registrant;
    /** 今進めている注文（なければ -1）。 */
    public int activeOrder = -1;
    /** 信号が来て、まだ注文できていない（信号で1回）。 */
    public boolean pending;
    public int state = IDLE;
    /** 注文できなかった理由（待っているとき。保存しない）。 */
    @Nullable
    public Component reason;

    public StandingOrder(int id, ItemStack target, long count, int trigger, @Nullable BlockPos destination, List<UUID> androids,
                         @Nullable UUID registrant) {
        this.id = id;
        this.target = target.copyWithCount(1);
        this.count = count;
        this.trigger = Math.floorMod(trigger, TRIGGERS);
        this.destination = destination;
        this.androids = List.copyOf(androids);
        this.registrant = registrant;
    }

    /** できた物を素材置き場へ入れる（在庫を保つ）か。 */
    public boolean toStock() {
        return trigger != PULSE;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.put("target", target.save(registries));
        t.putLong("count", count);
        t.putInt("trigger", trigger);
        if (destination != null) {
            t.putLong("destination", destination.asLong());
        }
        ListTag as = new ListTag();
        for (UUID a : androids) {
            as.add(NbtUtils.createUUID(a));
        }
        t.put("androids", as);
        if (registrant != null) {
            t.putUUID("registrant", registrant);
        }
        t.putInt("active", activeOrder);
        t.putBoolean("pending", pending);
        return t;
    }

    @Nullable
    public static StandingOrder load(CompoundTag t, HolderLookup.Provider registries) {
        ItemStack target = ItemStack.parseOptional(registries, t.getCompound("target"));
        if (target.isEmpty()) {
            return null;
        }
        List<UUID> androids = new ArrayList<>();
        for (Tag a : t.getList("androids", Tag.TAG_INT_ARRAY)) {
            androids.add(NbtUtils.loadUUID(a));
        }
        StandingOrder s = new StandingOrder(t.getInt("id"), target, Math.max(1, t.getLong("count")), t.getInt("trigger"),
                t.contains("destination") ? BlockPos.of(t.getLong("destination")) : null, androids,
                t.hasUUID("registrant") ? t.getUUID("registrant") : null);
        s.activeOrder = t.getInt("active");
        s.pending = t.getBoolean("pending");
        return s;
    }
}
