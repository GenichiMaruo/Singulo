package io.github.genichimaruo.singulo.client;

import java.util.List;
import net.minecraft.util.Util;

/**
 * 夜空の銀河（サーバーの GenesisGalaxies から受け取る）。種と同じ並びで性質（GalaxyTraits）を持つ。
 * 新しく生まれた銀河は、しばらくかけて現れる。
 */
public final class ClientGalaxies {
    private static List<Long> seeds = List.of();
    private static List<Integer> traits = List.of();
    private static long freshSeed;
    private static long freshSince = Long.MIN_VALUE;

    private ClientGalaxies() {}

    public static void set(List<Long> newSeeds, List<Integer> newTraits, boolean fresh) {
        seeds = List.copyOf(newSeeds);
        traits = List.copyOf(newTraits);
        if (fresh && !seeds.isEmpty()) {
            freshSeed = seeds.get(seeds.size() - 1);
            freshSince = Util.getMillis();
        }
    }

    static List<Long> seeds() {
        return seeds;
    }

    /** i 番目の銀河の性質（届いていなければ 0）。 */
    static int traits(int i) {
        return i < traits.size() ? traits.get(i) : 0;
    }

    /** 現れ具合（0〜1）。新しい銀河は約10秒かけて現れる。 */
    static float appear(long seed) {
        if (seed != freshSeed || freshSince == Long.MIN_VALUE) {
            return 1;
        }
        return Math.min(1, (Util.getMillis() - freshSince) / 10_000F);
    }

    /** いちばん新しい銀河（なければ 0）。 */
    static long latest() {
        return seeds.isEmpty() ? 0 : seeds.get(seeds.size() - 1);
    }

    /** いちばん新しい銀河の性質（なければ 0）。 */
    static int latestTraits() {
        return seeds.isEmpty() ? 0 : traits(seeds.size() - 1);
    }
}
