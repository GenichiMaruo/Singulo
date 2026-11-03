package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.genichimaruo.singulo.client.PreviewLighting;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Optional Ponder compatibility; includes its deferred buffer flush and restores state on failure. */
@Pseudo
@Mixin(targets = "net.createmod.ponder.foundation.ui.PonderUI", remap = false)
public abstract class PonderLightingMixin {
    @WrapMethod(method = "renderScene")
    private void singulo$preview(GuiGraphics graphics, int mouseX, int mouseY, int scene, float partialTick, Operation<Void> original) {
        PreviewLighting.render(() -> original.call(graphics, mouseX, mouseY, scene, partialTick));
    }
}
