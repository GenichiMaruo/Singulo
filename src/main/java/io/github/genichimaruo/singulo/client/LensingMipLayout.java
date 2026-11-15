package io.github.genichimaruo.singulo.client;

/** Mip levels supported by backends that allocate dimensions with an unclamped shift. */
final class LensingMipLayout {
    private LensingMipLayout() {}
    static int levels(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Positive texture dimensions required");
        return 32 - Integer.numberOfLeadingZeros(Math.min(width, height));
    }
}
