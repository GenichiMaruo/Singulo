package io.github.genichimaruo.singulo.compat;
import java.util.List;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/** Same grid ordering as 1.21 crafting input, backed by the 1.20.1 recipe API. */
public final class CraftingInput extends TransientCraftingContainer {
    private CraftingInput(int width, int height) {
        super(new LegacyConstructors.CraftingMenu(null, -1) {
            public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
            public boolean stillValid(Player p) { return true; }
        }, width, height);
    }
    /** Legacy shaped matching checks the full input and returns remainders in the same full grid. */
    public record Positioned(CraftingInput input, int left, int top) {}
    public static Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        return new Positioned(of(width, height, items), 0, 0);
    }
    public int width() { return getWidth(); }
    public int height() { return getHeight(); }
    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        var input = new CraftingInput(width, height);
        for (int i = 0; i < items.size(); i++) input.setItem(i, items.get(i));
        return input;
    }
}
