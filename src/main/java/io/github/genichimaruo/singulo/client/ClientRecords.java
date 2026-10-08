package io.github.genichimaruo.singulo.client;

import java.util.ArrayList;
import java.util.List;

/** クライアントが知っている、解読済みの旧文明の記録（サーバーから送られる）。 */
public final class ClientRecords {
    private static List<String> decoded = new ArrayList<>();

    private ClientRecords() {}

    public static List<String> decoded() {
        return decoded;
    }

    public static void set(List<String> list) {
        decoded = new ArrayList<>(list);
    }
}
