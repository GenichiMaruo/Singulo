package io.github.genichimaruo.singulo.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

record HandbookVisual(ItemStack icon, List<String> links, List<Row> rows, Blueprints.@Nullable Kind blueprint,
                      @Nullable Component action, @Nullable Component caption) {
    static HandbookVisual read(JsonObject json) {
        List<String> links = new ArrayList<>();
        if (json.has("links")) {
            json.getAsJsonArray("links").forEach(link -> links.add(link.getAsString()));
        }
        List<Row> rows = new ArrayList<>();
        if (json.has("rows")) {
            for (JsonElement element : json.getAsJsonArray("rows")) {
                JsonObject row = element.getAsJsonObject();
                rows.add(new Row(Component.translatable(row.get("title").getAsString()), tokens(row, "inputs"),
                        tokens(row, "outputs"), row.has("reading") ? row.get("reading").getAsString() : ""));
            }
        }
        return new HandbookVisual(item(json.get("icon").getAsString()), links, rows,
                json.has("blueprint") ? Blueprints.Kind.byId(json.get("blueprint").getAsString()) : null,
                json.has("action") ? Component.translatable(json.get("action").getAsString()) : null,
                json.has("caption") ? Component.translatable(json.get("caption").getAsString()) : null);
    }

    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static List<Token> tokens(JsonObject row, String key) {
        List<Token> result = new ArrayList<>();
        if (row.has(key)) {
            for (JsonElement element : row.getAsJsonArray(key)) {
                JsonObject obj = element.getAsJsonObject();
                String amount = obj.get("amount").getAsString();
                if (obj.has("fluid")) {
                    Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(obj.get("fluid").getAsString()));
                    result.add(new Token(ItemStack.EMPTY, fluid, amount));
                } else {
                    result.add(new Token(item(obj.get("item").getAsString()), null, amount));
                }
            }
        }
        return result;
    }

    private record Token(ItemStack stack, @Nullable Fluid fluid, String amount) {
        Component name() {
            return fluid == null ? stack.getHoverName() : fluid.getFluidType().getDescription();
        }

        void draw(GuiGraphicsExtractor g, Font font, int cx, int y) {
            if (fluid == null) {
                g.item(stack, cx - 8, y);
            } else {
                Panel.well(g, cx - 8, y, 16, 16);
                FluidDraw.fill(g, fluid, cx - 7, y + 1, 14, 14);
                // Formulas distinguish gases even without colour or a tooltip.
                String id = BuiltInRegistries.FLUID.getKey(fluid).getPath();
                if (id.equals("oxygen") || id.equals("hydrogen")) {
                    g.centeredText(font, id.equals("oxygen") ? "O₂" : "H₂", cx, y + 4, 0xFFFFFFFF);
                }
            }
            if (!amount.isEmpty()) {
                g.text(font, amount, cx - font.width(amount) / 2, y + 17, Panel.TEXT, false);
            }
        }
    }

    record Row(Component title, List<Token> inputs, List<Token> outputs, String reading) {
        static final int HEIGHT = 45;

        @Nullable Component draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my) {
            int count = inputs.size() + Math.max(1, outputs.size());
            int step = (w - 8) / count;
            g.fill(x, y, x + w, y + HEIGHT - 3, 0xFFF5F7F8);
            g.fill(x, y, x + 2, y + HEIGHT - 3, 0xFF5391A4);
            g.text(font, title, x + 6, y + 3, 0xFF245B70, false);
            Component hovered = null;
            for (int i = 0; i < count; i++) {
                int edge = x + 4 + i * step;
                int cx = edge + step / 2;
                if (i > 0) {
                    String op = i == inputs.size() ? "→" : "+";
                    g.text(font, op, edge - font.width(op) / 2, y + 18, 0xFF51717C, false);
                }
                if (i == inputs.size() && outputs.isEmpty()) {
                    g.text(font, reading, cx - font.width(reading) / 2, y + 18, 0xFF245B70, false);
                } else {
                    Token token = i < inputs.size() ? inputs.get(i) : outputs.get(i - inputs.size());
                    token.draw(g, font, cx, y + 13);
                    if (Panel.inside(mx, my, cx - step / 2 + 4, y + 12, step - 8, 28)) {
                        hovered = token.name();
                    }
                }
            }
            return hovered;
        }
    }
}
