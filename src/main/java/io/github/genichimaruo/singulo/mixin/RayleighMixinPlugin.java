package io.github.genichimaruo.singulo.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Let AC keep normal biome lighting while the synthetic noon pass runs vanilla's color calculation. */
public final class RayleighMixinPlugin implements IMixinConfigPlugin {
    private static final String ACCESS = "io/github/genichimaruo/singulo/client/RayleighLightmapAccess";
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".RayleighLightTextureMixin")) return;
        for (MethodNode method : node.methods) {
            if (!method.name.endsWith("ac_updateLightTexture")
                    || !method.desc.equals("(FLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
                    || !fromAlexsCaves(method.visibleAnnotations)) continue;
            // This is AC's merged callback, not vanilla updateLightTexture. Returning from the
            // callback leaves its original CallbackInfo uncancelled so vanilla can produce noon.
            LabelNode ordinary = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, ACCESS, "singulo$isNoonPass", "()Z", true));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, ordinary));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(ordinary);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            method.maxStack = Math.max(method.maxStack, 1);
        }
    }
    private static boolean fromAlexsCaves(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.endsWith("/MixinMerged;") || annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).toString().replace('/', '.')
                        .equals("com.github.alexmodguy.alexscaves.mixin.client.LightTextureMixin")) return true;
            }
        }
        return false;
    }
}
