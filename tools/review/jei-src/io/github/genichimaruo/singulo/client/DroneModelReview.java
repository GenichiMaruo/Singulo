package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.drone.CargoDrone;
import io.github.genichimaruo.singulo.drone.DroneKind;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Exercise all three legacy drone models and emissive layers on a real GPU path. Development only. */
public final class DroneModelReview extends Screen {
    private final CargoDrone[] drones = new CargoDrone[DroneKind.values().length];
    public DroneModelReview() {
        super(Component.literal("Drone model review"));
        for (int i = 0; i < drones.length; i++) {
            var drone = SinguloEntities.CARGO_DRONE.get().create(Minecraft.getInstance().level);
            if (drone == null) throw new IllegalStateException("No drone factory");
            var tag = new CompoundTag(); tag.putInt("kind", i);
            drone.readAdditionalSaveData(tag); drone.tickCount = 100;
            drones[i] = drone;
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF17232D);
        for (int i = 0; i < drones.length; i++) {
            int x = width * (i + 1) / (drones.length + 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, height / 2, 70, 25, -10, drones[i]);
            g.drawCenteredString(font, DroneKind.values()[i].name(), x, height / 2 + 60, 0xFFFFFF);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
