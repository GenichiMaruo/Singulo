package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineLayout;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Capture the normal container and selector, observing where Minecraft actually paints the tooltip. */
public final class AssemblySelectorReview {
    private static Identifier expected;
    private static int expectedSets;
    private static Popup selector;
    private static int previewFrames, tooltipFrames, hoverTicks, diagramX, diagramY;
    private static boolean awaitingSpent;
    private static int spentFrames;

    public static void expect(Identifier recipe, int sets) {
        expected = recipe;
        expectedSets = sets;
        selector = null;
        previewFrames = tooltipFrames = 0;
    }

    public static void rendered() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof MachineScreen screen) || mc.level == null) return;
        MachineMenu menu = screen.getMenu();
        if (awaitingSpent) {
            ItemStack product = menu.getSlot(menu.type().outputSlot()).getItem();
            ItemStack spent = menu.getSlot(menu.type().spentCatalystSlot()).getItem();
            boolean finished = product.is(BuiltInRegistries.ITEM.get(Singulo.id("casimir_cavity_frame"))) && product.getCount() == 4
                    && spent.is(BuiltInRegistries.ITEM.get(Singulo.id("spent_bose_condensate_catalyst"))) && spent.getCount() == 1
                    && menu.loadedCatalyst().isEmpty() && !menu.getSlot(menu.type().catalystSlot()).hasItem()
                    && menu.value(MachineBlockEntity.assemblyPreviewIndex(menu.type())) == 0;
            spentFrames = finished ? spentFrames + 1 : 0;
        }
        if (expected == null) return;
        int preview = menu.value(MachineBlockEntity.assemblyPreviewIndex(menu.type())) - 1;
        var recipe = RecipeSelection.byIndex(mc.level, menu.type(), preview);
        if (recipe == null || !recipe.id().equals(expected)) { previewFrames = 0; return; }
        if (menu.value(MachineBlockEntity.D_ENERGY) != 0 || menu.value(MachineBlockEntity.D_PROGRESS) != 0
                || menu.getSlot(menu.type().outputSlot()).hasItem())
            throw new IllegalStateException("An unpowered board produced a real output or advanced: " + expected);
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++) {
            var input = recipe.value().assemblyInput(i);
            var slot = menu.getSlot(menu.assemblySlot(i));
            var stack = slot.getItem();
            if (stack.getCount() > stack.getMaxStackSize()) throw new IllegalStateException("Oversized network stack at cell " + i);
            if (input == null ? !stack.isEmpty() : stack.getCount() + menu.assemblyExcess(slot) != expectedSets || !input.ingredient().test(stack)) {
                previewFrames = 0;
                return;
            }
        }
        previewFrames++;
    }

    public static boolean previewReady() { return previewFrames >= 3; }

    public static void openAndHover(MachineScreen screen) throws Exception {
        var layout = new MachineLayout(screen.getMenu().type());
        int x = screen.getGuiLeft() + layout.selectX + 8, y = screen.getGuiTop() + layout.selectY + 8;
        click(screen, x, y);
        var field = MachineScreen.class.getDeclaredField("selector");
        field.setAccessible(true);
        selector = (Popup) field.get(screen);
        if (!selector.open) throw new IllegalStateException("Standard recipe selector did not open");
        tooltipFrames = hoverTicks = 0;
        hoverExpected(screen);
    }

    // A slot update or a window mouse event can move the target after the popup opens.
    @SuppressWarnings("unchecked")
    private static void hoverExpected(MachineScreen screen) throws Exception {
        var entries = (List<RecipeHolder<MachineRecipe>>) read(screen, "selectorEntries");
        int index = 0;
        while (index < entries.size() && (entries.get(index) == null || !entries.get(index).id().equals(expected))) index++;
        if (index == entries.size()) throw new IllegalStateException("Recipe absent from standard selector: " + expected);
        int columns = (int) read(screen, "selectColumns"), visibleRows = (int) read(screen, "selectRows");
        int firstRow = Math.min(index / columns, Math.max(0, (entries.size() + columns - 1) / columns - visibleRows));
        var scroll = MachineScreen.class.getDeclaredField("selectorScroll");
        scroll.setAccessible(true);
        int delta = firstRow - scroll.getInt(screen);
        for (int row = 0; row < Math.abs(delta); row++)
            screen.mouseScrolled(selector.x + 4, selector.bodyY() + 4, 0, -Integer.signum(delta));
        var cellPos = MachineScreen.class.getDeclaredMethod("cellPos", int.class);
        cellPos.setAccessible(true);
        int[] pos = (int[]) cellPos.invoke(screen, index - firstRow * columns);
        moveMouse(screen, pos[0] + 9.5, pos[1] + 9.5);
    }

    private static void moveMouse(MachineScreen screen, double x, double y) throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        // Deliver the window-coordinate input to Minecraft's normal mouse handler, also for a background test window.
        var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(mc.mouseHandler, window.getWindow(), x * window.getScreenWidth() / screen.width, y * window.getScreenHeight() / screen.height);
    }

    private static Object read(MachineScreen screen, String name) throws ReflectiveOperationException {
        var method = MachineScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(screen);
    }

    private static void click(MachineScreen screen, int x, int y) {
        if (!screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Selector ignored click");
        screen.mouseReleased(x, y, 0);
    }

    public static void tooltip(RenderTooltipEvent.Color event) {
        var mc = Minecraft.getInstance();
        if (selector == null || !selector.open || !(mc.screen instanceof MachineScreen screen)) return;
        int y = event.getY();
        for (int i = 0; i < event.getComponents().size(); i++) {
            var component = event.getComponents().get(i);
            if (component instanceof MachineScreen.AssemblyPreview diagram) {
                var menu = screen.getMenu();
                var holder = RecipeSelection.byIndex(mc.level, menu.type(), menu.value(MachineBlockEntity.assemblyPreviewIndex(menu.type())) - 1);
                if (holder != null && holder.id().equals(expected) && ItemStack.matches(holder.value().result(), diagram.recipe().result())
                        && holder.value().shape().equals(diagram.recipe().shape())) {
                    diagramX = event.getX();
                    diagramY = y;
                    tooltipFrames++;
                }
                return;
            }
            y += component.getHeight() + (i == 0 ? 2 : 0);
        }
    }

    public static boolean captureSelector(Minecraft mc, String name, MachineRecipe recipe, Path output) throws Exception {
        if (tooltipFrames < 3) {
            hoverExpected((MachineScreen) mc.screen);
            if (++hoverTicks >= 200) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("selector-hover-failed.png"));
                }
                throw new IllegalStateException("Selector tooltip did not display the recipe " + expected);
            }
            return false;
        }
        var screen = (MachineScreen) mc.screen;
        if (!mc.getLanguageManager().getSelected().equals("ja_jp")) throw new IllegalStateException("Expected Japanese selector");
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(output.resolve("assembly-selector-ja_jp-" + name + ".png"));
            for (int slot = 0; slot < MachineRecipe.ASSEMBLY_CELLS; slot++) {
                int square = MachineRecipe.assemblySquare(slot);
                double x = diagramX + 1 + square % 5 * 18 * .75, y = diagramY + 1 + square / 5 * 18 * .75;
                boolean painted = textured(image, screen, x, y, .75);
                if (painted != (recipe.assemblyInput(slot) != null))
                    throw new IllegalStateException("Rendered icon mask differs from " + expected + " at cell " + slot);
            }
            if (recipe.ingredients().stream().anyMatch(recipe::assemblyCatalyst)
                    && !textured(image, screen, diagramX + 1 + 96 * .75, diagramY + 1 + 36 * .75, .75))
                throw new IllegalStateException("The diagram omitted its separate catalyst: " + expected);
        }
        click(screen, selector.x + selector.w - 7, selector.y + 7);
        if (selector.open) throw new IllegalStateException("Standard selector stayed open");
        Singulo.LOGGER.info("SINGULO_ASSEMBLY_SELECTOR_COMPLETE {} ja_jp", expected);
        expected = null;
        selector = null;
        return true;
    }

    public static void verifyGhost(NativeImage image, MachineScreen screen) {
        var layout = new MachineLayout(screen.getMenu().type());
        if (!textured(image, screen, screen.getGuiLeft() + layout.outputX, screen.getGuiTop() + layout.outputY[0], 1))
            throw new IllegalStateException("Unpowered board did not render its output preview: " + expected);
        Singulo.LOGGER.info("SINGULO_ASSEMBLY_PREVIEW_COMPLETE {} 0 FE", expected);
    }

    public static void awaitSpent(MachineScreen screen) throws ReflectiveOperationException {
        moveMouse(screen, 1, 1);
        spentFrames = 0;
        awaitingSpent = true;
    }

    public static boolean captureSpent(Minecraft mc, MachineRecipe recipe, Path output) throws Exception {
        if (spentFrames < 3) return false;
        var screen = (MachineScreen) mc.screen;
        var menu = screen.getMenu();
        var product = menu.getSlot(menu.type().outputSlot());
        var spent = menu.getSlot(menu.type().spentCatalystSlot());
        if (!mc.getLanguageManager().getSelected().equals("ja_jp") || product == spent || !ItemStack.matches(product.getItem(), recipe.result()))
            throw new IllegalStateException("Unexpected spent catalyst review screen or product");
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++) {
            if (menu.getSlot(menu.assemblySlot(i)).hasItem()) throw new IllegalStateException("Craft left an ingredient in board cell " + i);
        }
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (!textured(image, screen, screen.getGuiLeft() + product.x, screen.getGuiTop() + product.y, 1)
                    || !textured(image, screen, screen.getGuiLeft() + spent.x, screen.getGuiTop() + spent.y, 1))
                throw new IllegalStateException("The result or spent catalyst was absent from the rendered machine screen");
            image.writeToFile(output.resolve("assembly-spent-catalyst.png"));
        }
        awaitingSpent = false;
        Singulo.LOGGER.info("SINGULO_ASSEMBLY_SPENT_COMPLETE 4 casimir_cavity_frame, 1 spent_bose_condensate_catalyst ja_jp");
        return true;
    }

    /** An empty slot has a flat interior. A rendered item changes pixels inside that area. */
    private static boolean textured(NativeImage image, MachineScreen screen, double x, double y, double scale) {
        int first = 0;
        for (int dy = 2; dy < 15; dy++) for (int dx = 2; dx < 15; dx++) {
            int px = (int) ((x + (dx + .5) * scale) * image.getWidth() / screen.width);
            int py = (int) ((y + (dy + .5) * scale) * image.getHeight() / screen.height);
            if (px < 0 || px >= image.getWidth() || py < 0 || py >= image.getHeight())
                throw new IllegalStateException("Assembly image outside screen bounds");
            int color = image.getPixelRGBA(px, py) & 0xFFFFFF;
            if (dy == 2 && dx == 2) first = color;
            else if (color != first) return true;
        }
        return false;
    }
}
