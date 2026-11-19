package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Top views of a default-size projector blueprint. Positive Z (towards the player) is down. */
final class HandbookBlueprint {
    private record Part(int x, int z, Block block) {}
    private record Layer(List<Part> parts, Map<Block, Integer> counts) {}
    private final List<Layer> layers = new ArrayList<>();
    private final Map<Block, ItemStack> icons = new LinkedHashMap<>();
    private final int columns, depth;
    private int layer;

    HandbookBlueprint(Blueprints.Kind kind) {
        var blocks = Blueprints.layout(kind, BlockPos.ZERO, Direction.NORTH, kind.defaultSize());
        var bounds = net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(blocks.keySet()).orElseThrow();
        columns = bounds.getXSpan();
        depth = bounds.getZSpan();
        layer = -bounds.minY(); // Open on the controller.
        for (int y = 0; y < bounds.getYSpan(); y++) layers.add(new Layer(new ArrayList<>(), new LinkedHashMap<>()));
        blocks.forEach((pos, state) -> {
            if (state.isAir()) return;
            Block block = state.getBlock();
            Layer slice = layers.get(pos.getY() - bounds.minY());
            slice.parts.add(new Part(pos.getX() - bounds.minX(), pos.getZ() - bounds.minZ(), block));
            slice.counts.merge(block, 1, Integer::sum);
            icons.computeIfAbsent(block, b -> b.asItem().getDefaultInstance());
        });
    }

    private int cellSize(int width) {
        return Math.max(4, Math.min(16, Math.min((width / 2 - 6) / columns, 94 / depth)));
    }

    int height(int w) {
        return 22 + Math.max(cellSize(w) * depth, layers.get(layer).counts.size() * 15);
    }

    boolean turn(int direction) {
        int next = io.github.genichimaruo.singulo.compat.Legacy.clamp(layer + direction, 0, layers.size() - 1);
        if (next == layer) return false;
        layer = next;
        return true;
    }

    @Nullable Component draw(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        int h = height(w);
        g.fill(x, y, x + w, y + h, 0xFFF5F7F8);
        g.drawString(font, Component.translatable("guide.singulo.visual.layer", layer + 1, layers.size()),
                x + 5, y + 4, 0xFF245B70, false);
        if (layers.size() > 1) {
            for (int d : new int[]{-1, 1}) {
                int bx = x + w - (d < 0 ? 34 : 17);
                boolean enabled = layer + d >= 0 && layer + d < layers.size();
                boolean over = enabled && Panel.inside(mx, my, bx, y + 1, 15, 12);
                g.fill(bx, y + 1, bx + 15, y + 13, over ? 0xFFBEDBE3 : Panel.SHADE);
                g.drawString(font, d < 0 ? "−" : "+", bx + 5, y + 3, enabled ? Panel.TEXT : Panel.WELL_EDGE, false);
            }
        }
        int cell = cellSize(w);
        int gx = x + (w / 2 - columns * cell) / 2;
        int gy = y + 17;
        g.fill(gx, gy, gx + columns * cell, gy + depth * cell, 0xFFD7E1E5);
        for (int i = 0; i <= columns; i++) g.fill(gx + i * cell, gy, gx + i * cell + 1, gy + depth * cell + 1, Panel.SEAM);
        for (int i = 0; i <= depth; i++) g.fill(gx, gy + i * cell, gx + columns * cell, gy + i * cell + 1, Panel.SEAM);
        Component hovered = null;
        Layer slice = layers.get(layer);
        for (Part part : slice.parts) {
            int bx = gx + part.x * cell, by = gy + part.z * cell;
            drawIcon(g, icons.get(part.block), bx, by, cell);
            if (Panel.inside(mx, my, bx, by, cell, cell)) hovered = part.block.getName();
        }
        int lx = x + w / 2 + 3;
        int ly = gy;
        for (var entry : slice.counts.entrySet()) {
            Block block = entry.getKey();
            drawIcon(g, icons.get(block), lx, ly, 13);
            String count = "×" + entry.getValue();
            int right = x + w - 4 - font.width(count);
            String name = block.getName().getString();
            int space = right - lx - 18;
            if (font.width(name) > space) name = font.plainSubstrByWidth(name, space - font.width("…")) + "…";
            g.drawString(font, name, lx + 15, ly + 3, Panel.TEXT, false);
            g.drawString(font, count, right, ly + 3, 0xFF245B70, false);
            if (Panel.inside(mx, my, lx, ly, x + w - lx, 15)) hovered = block.getName();
            ly += 15;
        }
        return hovered;
    }

    private static void drawIcon(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16f, size / 16f, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
