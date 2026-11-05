package io.github.genichimaruo.singulo.forge;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * クライアントで読み込まれている鍛造炉（画面の閃光・揺れ・音が、近くの炉を探すのに使う）。
 * ブロックエンティティの読み込みと撤去で出し入れするので、チャンクを離れれば消える。
 */
public final class JetForgeClientIndex {
    private static final Set<JetForgeBlockEntity> LOADED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private JetForgeClientIndex() {}

    static void add(JetForgeBlockEntity be) {
        LOADED.add(be);
    }

    static void remove(JetForgeBlockEntity be) {
        LOADED.remove(be);
    }

    /** 読み込まれている炉（撤去済みのものは除く）。 */
    public static List<JetForgeBlockEntity> loaded() {
        LOADED.removeIf(be -> be.isRemoved() || be.getLevel() == null);
        return List.copyOf(LOADED);
    }

    public static void clear() {
        LOADED.clear();
    }
}
