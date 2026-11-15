package io.github.genichimaruo.singulo.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import java.util.ArrayList;
import java.util.List;

/** Extraction-local deferred model submissions, never carrying live worlds or entities. */
final class ModelSubmissions {
    interface Draw { void submit(PoseStack pose, SubmitNodeCollector collector); }
    private static final ThreadLocal<List<Draw>> ACTIVE=new ThreadLocal<>();
    static List<Draw> capture(Runnable extraction) {
        List<Draw> previous=ACTIVE.get(), draws=new ArrayList<>(); ACTIVE.set(draws);
        try { extraction.run(); return List.copyOf(draws); }
        finally { if(previous==null)ACTIVE.remove();else ACTIVE.set(previous); }
    }
    static void add(Draw draw) {
        var active=ACTIVE.get();
        if(active==null) throw new IllegalStateException("Item model submitted outside Singulo extraction");
        active.add(draw);
    }
    static void submit(List<Draw> draws,PoseStack pose,SubmitNodeCollector collector){for(var draw:draws)draw.submit(pose,collector);}
}