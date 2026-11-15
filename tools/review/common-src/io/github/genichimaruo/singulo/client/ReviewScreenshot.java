package io.github.genichimaruo.singulo.client;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Screenshot;
/** Development-only readback. Keep fixtures unchanged until the queued capture finishes. */
public final class ReviewScreenshot {
    private ReviewScreenshot() {}
    public static NativeImage take(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        var captured = new java.util.concurrent.atomic.AtomicReference<NativeImage>();
        Screenshot.takeScreenshot(target, captured::set);
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (captured.get() == null) {
            RenderSystem.executePendingTasks();
            if (System.nanoTime() > deadline) throw new IllegalStateException("Review screenshot readback timed out");
            if (captured.get() == null) java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        }
        return captured.get();
    }
}
