package io.github.genichimaruo.singulo.compat;

/**
 * Explicit constructor parameter names isolate anonymous subclasses from legacy mapped
 * Minecraft bytecode whose parameter metadata may contain duplicate names during annotation processing.
 */
public final class LegacyConstructors {
    private LegacyConstructors() {}

    public static class MenuSlot extends net.minecraft.world.inventory.Slot {
        public MenuSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
    }

    public abstract static class CraftingMenu extends net.minecraft.world.inventory.AbstractContainerMenu {
        public CraftingMenu(net.minecraft.world.inventory.MenuType<?> type, int id) {
            super(type, id);
        }
    }

    public static class GroundNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
        public GroundNavigation(net.minecraft.world.entity.Mob mob, net.minecraft.world.level.Level level) {
            super(mob, level);
        }
    }

    public static class StrollGoal extends net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal {
        public StrollGoal(net.minecraft.world.entity.PathfinderMob mob, double speed) {
            super(mob, speed);
        }
    }

    public static class RetaliationGoal extends net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal {
        public RetaliationGoal(net.minecraft.world.entity.PathfinderMob mob) {
            super(mob);
        }
    }
}
