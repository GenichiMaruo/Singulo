package io.github.genichimaruo.singulo.client;

/** Extraction scope for virtual previews. New vanilla GUI submissions use UiLightmap,
 * while synchronous third-party lightmap lookups receive the unmodified natural texture. */
public final class PreviewLighting {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private PreviewLighting() {}
    public static boolean active() { return DEPTH.get() != 0; }
    public static void render(Runnable draw) {
        DEPTH.set(DEPTH.get() + 1);
        try { draw.run(); } finally { int depth = DEPTH.get() - 1; if (depth == 0) DEPTH.remove(); else DEPTH.set(depth); }
    }
}
