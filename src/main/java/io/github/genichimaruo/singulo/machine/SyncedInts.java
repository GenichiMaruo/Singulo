package io.github.genichimaruo.singulo.machine;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.inventory.ContainerData;

/**
 * メニューで int を同期する。バニラの同期パケットは16bitで送るので、1つの値を上下2つの枠に分ける。
 * サーバー側は getter から読み、クライアント側は受け取った値を組み立て直す。
 */
public final class SyncedInts implements ContainerData {
    private final int count;
    private final IntUnaryOperator getter;
    private final int[] client;

    private SyncedInts(int count, IntUnaryOperator getter) {
        this.count = count;
        this.getter = getter;
        this.client = getter == null ? new int[count] : null;
    }

    public static SyncedInts server(int count, IntUnaryOperator getter) {
        return new SyncedInts(count, getter);
    }

    public static SyncedInts client(int count) {
        return new SyncedInts(count, null);
    }

    /** 組み立て済みの値。 */
    public int getInt(int index) {
        return getter != null ? getter.applyAsInt(index) : client[index];
    }

    @Override
    public int get(int slot) {
        int v = getInt(slot / 2);
        return slot % 2 == 0 ? v & 0xFFFF : (v >>> 16) & 0xFFFF;
    }

    @Override
    public void set(int slot, int value) {
        if (client == null) {
            return;
        }
        int i = slot / 2;
        int part = value & 0xFFFF;
        client[i] = slot % 2 == 0 ? (client[i] & 0xFFFF0000) | part : (client[i] & 0x0000FFFF) | (part << 16);
    }

    @Override
    public int getCount() {
        return count * 2;
    }
}
