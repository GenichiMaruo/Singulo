package io.github.genichimaruo.singulo.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.genichimaruo.singulo.Singulo;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Singulo ハンドブックの画面。左に章、右にページ。章とページは assets/singulo/guide/chapters.json と言語ファイルから読む。
 * 「進み具合」は進捗の達成状況から段階ごとに作り、「旧文明の記録」は解読した記録から作る。
 */
public class HandbookScreen extends Screen {
    private static final int W = 340;
    private static final int H = 232;
    private static final int LIST_W = 112;
    private static final int ROW = 16;

    /** 1ページぶんの中身。title と本文の行。 */
    private record Page(Component title, List<Line> lines) {}

    /** 本文の1行（色つき）。 */
    private record Line(Component text, int color) {}

    private record Chapter(Component title, ItemStack icon, List<Page> pages) {}

    private final List<Chapter> chapters = new ArrayList<>();
    private static int lastChapter;
    private static int lastPage;
    private int chapter;
    private int page;
    private int scroll;
    private int listScroll;
    private int left;
    private int top;

    public HandbookScreen() {
        super(Component.translatable("gui.singulo.handbook.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HandbookScreen());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        chapters.clear();
        loadChapters();
        chapters.add(1, progressChapter());
        chapters.add(recordsChapter());
        chapter = Math.min(lastChapter, chapters.size() - 1);
        page = Math.min(lastPage, chapters.get(chapter).pages.size() - 1);
    }

    // ------------------------------------------------------------------ 中身

    private void loadChapters() {
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(Singulo.id("guide/chapters.json"))) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("chapters")) {
                JsonObject c = e.getAsJsonObject();
                String id = c.get("id").getAsString();
                List<Page> pages = new ArrayList<>();
                for (JsonElement p : c.getAsJsonArray("pages")) {
                    String key = "guide.singulo." + p.getAsString();
                    pages.add(textPage(Component.translatable(key + ".title"), Component.translatable(key + ".body").getString()));
                }
                chapters.add(new Chapter(Component.translatable("guide.singulo." + id), icon(c.get("icon").getAsString()), pages));
            }
        } catch (Exception ex) {
            Singulo.LOGGER.error("ハンドブックの章を読めなかった", ex);
            chapters.add(new Chapter(Component.literal("Singulo"), new ItemStack(Items.BOOK),
                    List.of(textPage(Component.literal("Error"), ex.toString()))));
        }
    }

    private static ItemStack icon(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? new ItemStack(Items.BOOK) : new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    private Page textPage(Component title, String body) {
        List<Line> lines = new ArrayList<>();
        for (String para : body.split("\n")) {
            lines.add(new Line(Component.literal(para), Panel.TEXT));
        }
        return new Page(title, lines);
    }

    /** 進捗から「進み具合」の章を作る。段階ごとに1ページ。 */
    private Chapter progressChapter() {
        List<Page> pages = new ArrayList<>();
        Map<String, Integer> stageOf = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        List<String> order = new ArrayList<>();
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(Singulo.id("guide/advancements.json"))) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("advancements");
            for (JsonElement e : arr) {
                JsonObject a = e.getAsJsonObject();
                String id = a.get("id").getAsString();
                order.add(id);
                stageOf.put(id, a.get("stage").getAsInt());
                if (a.has("parent") && !a.get("parent").isJsonNull()) {
                    parentOf.put(id, a.get("parent").getAsString());
                }
            }
        } catch (Exception ex) {
            Singulo.LOGGER.error("進捗の一覧を読めなかった", ex);
        }
        int doneAll = 0;
        for (String id : order) {
            if (done(id)) {
                doneAll++;
            }
        }
        for (int stage = 1; stage <= 5; stage++) {
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(Component.translatable("gui.singulo.handbook.done_count", doneAll, order.size()), Panel.WELL_EDGE));
            List<Line> next = new ArrayList<>();
            List<Line> rest = new ArrayList<>();
            for (String id : order) {
                if (stageOf.getOrDefault(id, 1) != stage || id.equals("root")) {
                    continue;
                }
                String key = "advancement.singulo." + id;
                String parent = parentOf.get(id);
                boolean available = parent == null || done(parent);
                if (done(id)) {
                    rest.add(new Line(Component.literal("✔ ").append(Component.translatable(key + ".title")), 0xFF3C9A5A));
                } else if (available) {
                    next.add(new Line(Component.literal("▶ ").append(Component.translatable(key + ".title"))
                            .withStyle(ChatFormatting.BOLD), Panel.TEXT));
                    next.add(new Line(Component.literal("   ").append(Component.translatable(key + ".description")), 0xFF5A6670));
                } else {
                    rest.add(new Line(Component.literal("・").append(Component.translatable("gui.singulo.handbook.locked")),
                            Panel.WELL_EDGE));
                }
            }
            if (!next.isEmpty()) {
                lines.add(new Line(Component.translatable("gui.singulo.handbook.next"), 0xFF2A8FB8));
                lines.addAll(next);
            }
            lines.addAll(rest);
            pages.add(new Page(Component.translatable("gui.singulo.handbook.stage", stage)
                    .append(" ").append(Component.translatable("stage.singulo." + stage)), lines));
        }
        return new Chapter(Component.translatable("gui.singulo.handbook.progress"), new ItemStack(Items.WRITABLE_BOOK), pages);
    }

    private static boolean done(String id) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            return false;
        }
        ClientAdvancements adv = conn.getAdvancements();
        AdvancementHolder holder = adv.get(Singulo.id(id));
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = adv.progress.get(holder);
        return progress != null && progress.isDone();
    }

    /** 解読した旧文明の記録。 */
    private Chapter recordsChapter() {
        List<Page> pages = new ArrayList<>();
        for (String id : ClientRecords.decoded()) {
            String key = "record.singulo." + id;
            pages.add(textPage(Component.translatable(key + ".title"), Component.translatable(key + ".body").getString()));
        }
        if (pages.isEmpty()) {
            pages.add(textPage(Component.translatable("gui.singulo.handbook.records"),
                    Component.translatable("gui.singulo.handbook.no_records").getString()));
        }
        return new Chapter(Component.translatable("gui.singulo.handbook.records"), icon("singulo:record_fragment"), pages);
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Panel.background(g, left, top, W, H);
        g.drawString(font, title, left + 8, top + 7, Panel.TEXT, false);
        // 章の一覧
        int listTop = top + 20;
        int listH = H - 28;
        Panel.well(g, left + 6, listTop, LIST_W, listH);
        g.enableScissor(left + 6, listTop, left + 6 + LIST_W, listTop + listH);
        for (int i = 0; i < chapters.size(); i++) {
            int y = listTop + i * ROW - listScroll;
            Chapter c = chapters.get(i);
            boolean hover = Panel.inside(mouseX, mouseY, left + 6, y, LIST_W, ROW) && mouseY >= listTop && mouseY < listTop + listH;
            if (i == chapter) {
                g.fill(left + 6, y, left + 6 + LIST_W, y + ROW, 0xFF2A6F8A);
            } else if (hover) {
                g.fill(left + 6, y, left + 6 + LIST_W, y + ROW, 0xFF4A535C);
            }
            g.pose().pushPose();
            g.pose().translate(left + 8, y + 2, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.renderItem(c.icon, 0, 0);
            g.pose().popPose();
            g.drawString(font, font.plainSubstrByWidth(c.title.getString(), LIST_W - 22), left + 22, y + 4, 0xFFECEEF0, false);
        }
        g.disableScissor();

        // ページ
        Chapter c = chapters.get(chapter);
        Page p = c.pages.get(page);
        int px = left + LIST_W + 14;
        int pw = W - LIST_W - 22;
        g.drawString(font, p.title, px, top + 22, 0xFF1E5A73, false);
        g.fill(px, top + 33, px + pw, top + 34, Panel.SEAM);
        int bodyTop = top + 38;
        int bodyH = H - 38 - 22;
        g.enableScissor(px, bodyTop, px + pw, bodyTop + bodyH);
        int y = bodyTop - scroll;
        for (Line line : p.lines) {
            for (FormattedCharSequence seq : font.split(line.text, pw)) {
                g.drawString(font, seq, px, y, line.color, false);
                y += 10;
            }
            y += 3;
        }
        g.disableScissor();
        // ページ送り
        int navY = top + H - 18;
        String count = Component.translatable("gui.singulo.handbook.page", page + 1, c.pages.size()).getString();
        g.drawCenteredString(font, count, px + pw / 2, navY + 2, Panel.TEXT);
        drawArrow(g, px + pw / 2 - 40, navY, "<", page > 0, mouseX, mouseY);
        drawArrow(g, px + pw / 2 + 28, navY, ">", page < c.pages.size() - 1, mouseX, mouseY);
    }

    private void drawArrow(GuiGraphics g, int x, int y, String s, boolean enabled, int mx, int my) {
        int color = !enabled ? Panel.SEAM : Panel.inside(mx, my, x, y, 12, 12) ? Panel.GLOW : Panel.TEXT;
        g.fill(x, y, x + 12, y + 12, enabled ? Panel.SHADE : Panel.BACKGROUND);
        g.drawCenteredString(font, s, x + 6, y + 2, color);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    // ------------------------------------------------------------------ 操作

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int listTop = top + 20;
        int listH = H - 28;
        if (Panel.inside(mx, my, left + 6, listTop, LIST_W, listH)) {
            int i = (int) ((my - listTop + listScroll) / ROW);
            if (i >= 0 && i < chapters.size()) {
                select(i, 0);
                return true;
            }
        }
        int px = left + LIST_W + 14;
        int pw = W - LIST_W - 22;
        int navY = top + H - 18;
        if (Panel.inside(mx, my, px + pw / 2 - 40, navY, 12, 12) && page > 0) {
            select(chapter, page - 1);
            return true;
        }
        if (Panel.inside(mx, my, px + pw / 2 + 28, navY, 12, 12) && page < chapters.get(chapter).pages.size() - 1) {
            select(chapter, page + 1);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < left + LIST_W + 8) {
            int max = Math.max(0, chapters.size() * ROW - (H - 28));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (dy * ROW)));
        } else {
            scroll = Math.max(0, scroll - (int) (dy * 12));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 262 && page < chapters.get(chapter).pages.size() - 1) {          // →
            select(chapter, page + 1);
            return true;
        }
        if (key == 263 && page > 0) {                                                  // ←
            select(chapter, page - 1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void select(int c, int p) {
        chapter = c;
        page = p;
        scroll = 0;
        lastChapter = c;
        lastPage = p;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
