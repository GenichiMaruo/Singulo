package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.MachineScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerScreen.class)
abstract class LegacyStockCountMixin {
    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void singulo$renderStockCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
                                          String label, GuiGraphics originalGraphics, Slot slot) {
        if ((Object) this instanceof MachineScreen machine) {
            machine.renderAssemblyCount(graphics, font, stack, slot, x, y, label);
        } else {
            graphics.renderItemDecorations(font, stack, x, y, label);
        }
    }
}
