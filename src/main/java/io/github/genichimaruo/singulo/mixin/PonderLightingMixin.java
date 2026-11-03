package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.PreviewLighting;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional legacy Ponder support without requiring a newer MixinExtras runtime. */
@Pseudo
@Mixin(targets = "net.createmod.ponder.foundation.ui.PonderUI", remap = false)
public abstract class PonderLightingMixin {
    @Shadow protected abstract void renderScene(GuiGraphics graphics, int mouseX, int mouseY, int scene, float partialTick);

    @Redirect(method = {"renderWindow", "renderVisibleScenes"}, at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/ui/PonderUI;renderScene(Lnet/minecraft/client/gui/GuiGraphics;IIIF)V"))
    private void singulo$preview(@Coerce Object screen, GuiGraphics graphics, int mouseX, int mouseY, int scene, float partialTick) {
        // Wrap both calls made while sliding between scenes, including their deferred buffer flush.
        PreviewLighting.render(() -> renderScene(graphics, mouseX, mouseY, scene, partialTick));
    }
}
