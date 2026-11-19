package io.github.genichimaruo.singulo.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.supporter.SupporterCredits;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Singulo ハンドブックの画面。左に章、右にページ。章とページは assets/singulo/guide/chapters.json と言語ファイルから読む。
 * 「進み具合」は進捗の達成状況から段階ごとに作り、「旧文明の記録」は解読した記録から作る。
 */
public class HandbookScreen extends Screen {
    private int bookWidth, bookHeight, listWidth;
    private static final int ROW = 16;
    /** 章の一覧の上の検索欄の高さ（一覧はその下から）。 */
    private static final int SEARCH_H = 14;

    /** 作者への支援（Ko-fi）。題名の行の右端の小さなボタンから、確認の画面を出してブラウザで開く。 */
    static final String SUPPORT_URL = "https://ko-fi.com/graycat9";

    private record Page(Component title, List<Line> lines, List<SupporterCredits.Entry> supporters,
                        @org.jspecify.annotations.Nullable HandbookVisual visual) {
        Page(Component title, List<Line> lines) {
            this(title, lines, List.of(), null);
        }
    }

    /** A paragraph can link to an authored topic or illustrate a named tool. */
    private record Line(Component text, int color, @org.jspecify.annotations.Nullable String link, ItemStack icon) {
        Line(Component text, int color, @org.jspecify.annotations.Nullable String link) {
            this(text, color, link, ItemStack.EMPTY);
        }
        Line(Component text, int color) {
            this(text, color, null);
        }
    }

    /** ページの行き先（"章のid/ページのid" から引く）。 */
    private record Target(Chapter chapter, int page) {}

    private final Map<String, Target> targets = new HashMap<>();
    private final Map<String, ItemStack> namedItems = new HashMap<>();
    /** 描いた行き先つきの行の場所（x, y, 幅, 高さ）と行き先。描くたびに作り直す。 */
    private record LinkArea(int x, int y, int w, int h, Target target) {}
    private final List<LinkArea> linkAreas = new ArrayList<>();
    private boolean contents;
    private boolean details;
    private boolean hasFullText;
    private int contentHeight;
    @org.jspecify.annotations.Nullable private HandbookBlueprint blueprint;
    private int blueprintY;
    @org.jspecify.annotations.Nullable private Component hoveredNote;
    /** 「進み具合」の章を開いたときに出す段階（次の目標のある、いちばん若い段階）。 */
    private int progressStart;

    private record Chapter(Component title, ItemStack icon, List<Page> pages) {}

    private final List<Chapter> chapters = new ArrayList<>();
    /** 検索欄（文字があれば、左の一覧は章の代わりに、合うページの一覧になる）。 */
    private net.minecraft.client.gui.components.@org.jspecify.annotations.Nullable EditBox searchBox;
    /** 検索で合ったページ（章の番号・ページの番号。よく合う順）。 */
    private final List<int[]> results = new ArrayList<>();
    private static int lastPage;
    private static Component lastChapterTitle, lastPageTitle;
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

    /** 開いたときに探す物の名前（なければ null）。 */
    @org.jspecify.annotations.Nullable
    private String focus;

    /**
     * Singulo の物なら、その物のことが書いてあるページを開く（持ち物などの画面は閉じる）。Singulo の物でなければ何もしない（false）。
     */
    public static boolean lookup(ItemStack stack) {
        if (stack.isEmpty() || !Singulo.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
        HandbookScreen screen = new HandbookScreen();
        screen.focus = stack.getHoverName().getString();
        mc.setScreen(screen);
        return true;
    }

    /**
     * 探す物のことが一番書いてあるページへ移る: 題名に名前があれば大きく、本文に名前が出てくる回数を足して比べる。
     * 見つからなければ、前に開いていたページのまま。
     */
    private void focusPage() {
        if (focus == null || focus.isBlank()) {
            return;
        }
        int best = 0;
        String query = focus.toLowerCase(java.util.Locale.ROOT);
        for (int c = 0; c < chapters.size(); c++) {
            List<Page> pages = chapters.get(c).pages;
            for (int p = 0; p < pages.size(); p++) {
                Page pg = pages.get(p);
                int score = matchScore(pg, query);
                if (score > best) {
                    best = score;
                    select(c, p);
                }
            }
        }
        if (best > 0) {
            Page match = chapters.get(chapter).pages.get(page);
            details = match.visual == null || !match.visual.icon().getHoverName().getString().equalsIgnoreCase(focus);
        }
        focus = null;
    }

    @Override
    protected void init() {
        bookWidth = Math.min(424, width - 8);
        bookHeight = Math.min(260, height - 8);
        resetScroll();
        listWidth = Math.min(120, bookWidth / 3);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2;
        chapters.clear();
        targets.clear();
        namedItems.clear();
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            if (entry.getKey().identifier().getNamespace().equals(Singulo.MODID)) {
                ItemStack item = entry.getValue().getDefaultInstance();
                namedItems.putIfAbsent(item.getHoverName().getString(), item);
            }
        });
        int stage = industrialStage();
        loadChapters(stage);
        chapters.add(1, progressChapter(stage));
        if (!ClientRecords.decoded().isEmpty()) chapters.add(recordsChapter());
        chapters.add(supportersChapter());
        // Discovery can insert or remove chapters between visits, including on a world change.
        chapter = 0;
        page = 0;
        for (int c = 0; c < chapters.size(); c++) {
            Chapter candidate = chapters.get(c);
            if (!candidate.title.equals(lastChapterTitle)) continue;
            chapter = c;
            int saved = Math.min(lastPage, candidate.pages.size() - 1);
            if (candidate.pages.get(saved).title.equals(lastPageTitle)) {
                page = saved;
            } else {
                for (int i = 0; i < candidate.pages.size(); i++) {
                    if (candidate.pages.get(i).title.equals(lastPageTitle)) { page = i; break; }
                }
            }
            break;
        }
        blueprint = null;
        String query = searchBox != null ? searchBox.getValue() : focus != null ? focus : "";
        focusPage();
        searchBox = new net.minecraft.client.gui.components.EditBox(font, left + 9, top + 22, listWidth - 6, SEARCH_H - 4,
                Component.translatable("gui.singulo.handbook.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(48);
        searchBox.setTextColor(0xFFECEEF0);
        searchBox.setHint(Component.translatable("gui.singulo.handbook.search").withStyle(ChatFormatting.GRAY));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            search(text);
            listScroll = 0;
        });
        addRenderableWidget(searchBox);
        search(query);
    }

    /** 検索: 題名に文字があれば大きく、本文に出てくる回数を足して、合うページをよく合う順に並べる（大文字・小文字は区別しない）。 */
    private void search(String text) {
        results.clear();
        String q = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) {
            return;
        }
        List<int[]> scored = new ArrayList<>();
        for (int c = 0; c < chapters.size(); c++) {
            List<Page> pages = chapters.get(c).pages;
            for (int p = 0; p < pages.size(); p++) {
                Page pg = pages.get(p);
                int score = matchScore(pg, q);
                if (score > 0) {
                    scored.add(new int[]{c, p, score});
                }
            }
        }
        scored.sort((a, b) -> Integer.compare(b[2], a[2]));
        results.addAll(scored);
    }

    private int matchScore(Page pg, String query) {
        int score = pg.title.getString().toLowerCase(java.util.Locale.ROOT).contains(query) ? 100 : 0;
        if (pg.visual != null && pg.visual.icon().getHoverName().getString().equalsIgnoreCase(query)) score += 50;
        for (Line line : pg.lines) {
            String body = line.text.getString().toLowerCase(java.util.Locale.ROOT);
            for (int i = body.indexOf(query); i >= 0; i = body.indexOf(query, i + query.length())) score++;
        }
        return score;
    }

    /** 検索しているか（左の一覧が、合うページの一覧になっている）。 */
    private boolean searching() {
        return searchBox != null && !searchBox.getValue().isBlank();
    }

    /** 左の一覧の行の数（章か、検索で合ったページ）。 */
    private int listRows() {
        return searching() ? results.size() : chapters.size();
    }

    // ------------------------------------------------------------------ 中身

    private static int industrialStage() {
        if (done("seed") || done("reactor") || done("ignite") || done("core") || knowsEquipment("reactor_core_controller")) return 5;
        if (done("gravity") || done("crystal") || done("compactor") || knowsEquipment("time_crystal_incubator")) return 4;
        if (done("quantum") || done("bose") || knowsEquipment("laser_cooler")) return 3;
        if (done("tower") || done("coil") || done("assembler") || knowsEquipment("precision_assembler")) return 2;
        return 1;
    }

    private static boolean knowsEquipment(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        var item = BuiltInRegistries.ITEM.getOptional(Singulo.id(id)).orElse(null);
        if (item == null) return false;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) return true;
        }
        return mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).getBlock().asItem() == item;
    }

    private static boolean canRead(JsonObject topic, int stage) {
        if (topic.has("stage") && stage < topic.get("stage").getAsInt()) return false;
        if (!topic.has("discoveries")) return true;
        for (JsonElement condition : topic.getAsJsonArray("discoveries")) {
            String key = condition.getAsString();
            if (key.startsWith("item/") ? knowsEquipment(key.substring(5)) : done(key)) return true;
        }
        return false;
    }

    private void loadChapters(int stage) {
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(Singulo.id("guide/chapters.json"))) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject visuals = root.getAsJsonObject("visuals");
            for (JsonElement e : root.getAsJsonArray("chapters")) {
                JsonObject c = e.getAsJsonObject();
                String id = c.get("id").getAsString();
                List<Page> pages = new ArrayList<>();
                List<String> pageIds = new ArrayList<>();
                for (JsonElement p : c.getAsJsonArray("pages")) {
                    String key = "guide.singulo." + p.getAsString();
                    JsonObject topic = visuals == null ? null : visuals.getAsJsonObject(p.getAsString());
                    if (topic != null && !canRead(topic, stage)) continue;
                    HandbookVisual visual = topic == null ? null : HandbookVisual.read(topic);
                    pages.add(textPage(Component.translatable(key + ".title"), Component.translatable(key + ".body").getString(), visual));
                    pageIds.add(p.getAsString());
                }
                if (pages.isEmpty()) continue;
                Chapter chapter = new Chapter(Component.translatable("guide.singulo." + id), icon(c.get("icon").getAsString()), pages);
                chapters.add(chapter);
                for (int i = 0; i < pageIds.size(); i++) {
                    targets.put(pageIds.get(i), new Target(chapter, i));      // ページのidは "章のid.ページのid"
                }
            }
        } catch (Exception ex) {
            Singulo.LOGGER.error("ハンドブックの章を読めなかった", ex);
            chapters.add(new Chapter(Component.literal("Singulo"), new ItemStack(Items.BOOK),
                    List.of(textPage(Component.literal("Error"), ex.toString()))));
        }
    }

    private static ItemStack icon(String id) {
        Identifier rl = Identifier.tryParse(id);
        return rl == null ? new ItemStack(Items.BOOK) : new ItemStack(BuiltInRegistries.ITEM.getValue(rl));
    }

    private Page textPage(Component title, String body) {
        return textPage(title, body, null);
    }

    private Page textPage(Component title, String body, @org.jspecify.annotations.Nullable HandbookVisual visual) {
        List<Line> lines = new ArrayList<>();
        for (String para : body.split("\n")) {
            ItemStack illustration = ItemStack.EMPTY;
            if (visual != null) {
                String label = para.replaceFirst("^[-・•]\\s*", "");
                int sep = label.replace('：', ':').indexOf(':');
                if (sep > 0) illustration = namedItems.getOrDefault(label.substring(0, sep).strip(), ItemStack.EMPTY);
            }
            lines.add(new Line(Component.literal(para), Panel.TEXT, null, illustration));
        }
        return new Page(title, lines, List.of(), visual);
    }

    /** 進捗から「進み具合」の章を作る。段階ごとに1ページ。 */
    // Only these early technical objectives may be suggested before completion.
    private static final Set<String> STARTER_GOALS = Set.of("steel", "ceramic", "circuit", "thermo", "compressor", "projector", "archive");

    private Chapter progressChapter(int currentStage) {
        List<Page> pages = new ArrayList<>();
        Map<String, Integer> stageOf = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        List<String> order = new ArrayList<>();
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(Singulo.id("guide/advancements.json"))) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("advancements");
            for (JsonElement e : arr) {
                JsonObject a = e.getAsJsonObject();
                String id = a.get("id").getAsString();
                // 進捗画面で隠す実績を、親を達成しただけで本に先出ししない。件数にも含めない。
                if (!done(id) && (!STARTER_GOALS.contains(id) || a.has("hidden") && a.get("hidden").getAsBoolean())) {
                    continue;
                }
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
            if (!id.equals("root") && done(id)) {
                doneAll++;
            }
        }
        progressStart = -1;
        for (int stage = 1; stage <= currentStage; stage++) {
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(Component.translatable("gui.singulo.handbook.done_count", doneAll), Panel.WELL_EDGE));
            // この段階の流れ（やることの順番）のページへ
            lines.add(new Line(Component.translatable("gui.singulo.handbook.read_flow", stage), 0xFF2A8FB8, "stage" + stage + ".flow"));
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
                }
            }
            if (!next.isEmpty()) {
                if (progressStart < 0) {
                    progressStart = stage - 1;
                }
                lines.add(new Line(Component.translatable("gui.singulo.handbook.next"), 0xFF2A8FB8));
                lines.addAll(next);
            }
            lines.addAll(rest);
            pages.add(new Page(Component.translatable("gui.singulo.handbook.stage", stage)
                    .append(" ").append(Component.translatable("stage.singulo." + stage)), lines));
        }
        if (progressStart < 0) progressStart = currentStage - 1;
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
        return new Chapter(Component.translatable("gui.singulo.handbook.records"), icon("singulo:crystal_memory"), pages);
    }

    /** Four individually framed supporters per page; both tiers share the same roster. */
    private Chapter supportersChapter() {
        SupporterCredits.Catalog roster = SupporterRoster.load();
        List<Page> pages = new ArrayList<>();
        List<Line> intro = new ArrayList<>();
        intro.add(new Line(Component.translatable("gui.singulo.supporters.intro"), Panel.TEXT));
        intro.add(new Line(Component.translatable("gui.singulo.supporters.count", roster.entries().size()), Panel.WELL_EDGE));
        if (roster.entries().isEmpty()) {
            intro.add(new Line(Component.translatable("gui.singulo.supporters.empty"), Panel.TEXT));
        }
        pages.add(new Page(Component.translatable("gui.singulo.supporters.title"), intro));
        for (int i = 0; i < roster.pageCount(4); i++) {
            pages.add(new Page(Component.translatable("gui.singulo.handbook.supporters"), List.of(), roster.page(i, 4), null));
        }
        return new Chapter(Component.translatable("gui.singulo.handbook.supporters"), new ItemStack(Items.NAME_TAG), pages);
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Panel.background(g, left, top, bookWidth, bookHeight);
        g.text(font, title, left + 8, top + 7, Panel.TEXT, false);
        drawSupport(g, mouseX, mouseY);
        // 検索欄と、章の一覧（検索しているときは、合うページの一覧）
        Panel.well(g, left + 6, top + 20, listWidth, SEARCH_H - 2);
        if (searchBox != null) {
            searchBox.extractRenderState(g, mouseX, mouseY, partialTick);
        }
        int listTop = top + 20 + SEARCH_H;
        int listH = bookHeight - 28 - SEARCH_H;
        Panel.well(g, left + 6, listTop, listWidth, listH);
        g.enableScissor(left + 6, listTop, left + 6 + listWidth, listTop + listH);
        for (int i = 0; i < listRows(); i++) {
            int y = listTop + i * ROW - listScroll;
            if (y + ROW <= listTop || y >= listTop + listH) continue;
            Chapter c = chapters.get(searching() ? results.get(i)[0] : i);
            boolean on = searching() ? results.get(i)[0] == chapter && results.get(i)[1] == page : i == chapter;
            boolean hover = Panel.inside(mouseX, mouseY, left + 6, y, listWidth, ROW) && mouseY >= listTop && mouseY < listTop + listH;
            if (on) {
                g.fill(left + 6, y, left + 6 + listWidth, y + ROW, 0xFF2A6F8A);
            } else if (hover) {
                g.fill(left + 6, y, left + 6 + listWidth, y + ROW, 0xFF4A535C);
            }
            g.pose().pushMatrix();
            g.pose().translate(left + 8, y + 2);
            g.pose().scale(0.75F, 0.75F);
            Page result = searching() ? c.pages.get(results.get(i)[1]) : null;
            g.item(result != null && result.visual != null ? result.visual.icon() : c.icon, 0, 0);
            g.pose().popMatrix();
            String label = searching() ? c.pages.get(results.get(i)[1]).title().getString() : c.title.getString();
            g.text(font, font.plainSubstrByWidth(label, listWidth - 22), left + 22, y + 4, 0xFFECEEF0, false);
        }
        if (searching() && results.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(Component.translatable("gui.singulo.handbook.search.none").getString(), listWidth - 8),
                    left + 10, listTop + 4, 0xFFBFC8D0, false);
        }
        g.disableScissor();
        if (listRows() * ROW > listH) {
            int thumb = Math.max(8, listH * listH / (listRows() * ROW));
            int offset = listScroll * (listH - thumb) / (listRows() * ROW - listH);
            g.fill(left + listWidth + 4, listTop + offset, left + listWidth + 6, listTop + offset + thumb, Panel.WELL_EDGE);
        }

        // ページ
        Chapter c = chapters.get(chapter);
        Page p = c.pages.get(page);
        int px = left + listWidth + 14;
        int pw = bookWidth - listWidth - 22;
        Component heading = contents ? c.title : p.title;
        g.item(contents || p.visual == null ? c.icon : p.visual.icon(), px, top + 19);
        String headingText = heading.getString();
        boolean hasIndex = p.visual != null && c.pages.size() > 1;
        int titleSpace = pw - 20 - (hasIndex ? 18 : 0);
        hoveredNote = null;
        g.text(font, ellipsize(headingText, titleSpace), px + 20, top + 24, 0xFF1E5A73, false);
        if (font.width(heading) > titleSpace && Panel.inside(mouseX, mouseY, px, top + 18, titleSpace + 20, 18)) {
            hoveredNote = heading;
        }
        if (hasIndex) {
            int bx = px + pw - 16;
            boolean over = Panel.inside(mouseX, mouseY, bx, top + 19, 16, 16);
            g.fill(bx, top + 19, bx + 16, top + 35, over || contents ? 0xFFBEDBE3 : Panel.SHADE);
            for (int j = 0; j < 3; j++) {
                g.fill(bx + 3, top + 23 + j * 3, bx + 5, top + 24 + j * 3, Panel.TEXT);
                g.fill(bx + 7, top + 23 + j * 3, bx + 13, top + 24 + j * 3, Panel.TEXT);
            }
            if (over) hoveredNote = Component.translatable("gui.singulo.handbook.contents");
        }
        g.fill(px, top + 37, px + pw, top + 38, Panel.SEAM);
        int bodyTop = top + 41;
        int bodyH = bookHeight - 63;
        g.enableScissor(px, bodyTop, px + pw, bodyTop + bodyH);
        int y = bodyTop - scroll;
        linkAreas.clear();
        HandbookVisual visual = p.visual;
        boolean route = visual != null && !visual.links().isEmpty();
        hasFullText = visual != null && (details || route || !visual.rows().isEmpty() || visual.blueprint() != null);
        if (contents) {
            for (int i = 0; i < c.pages.size(); i++) {
                y += drawCard(g, new Target(c, i), false, 0, px, y, pw - 4, mouseX, mouseY);
            }
        } else if (route && !details) {
            if (chapter == 0) {
                for (var seq : font.split(Component.translatable("guide.singulo.visual.start"), pw - 5)) {
                    g.text(font, seq, px + 2, y, Panel.TEXT, false);
                    y += 10;
                }
                y += 4;
            }
            int step = 1;
            for (String id : visual.links()) {
                Target t = targets.get(id);
                if (t != null) y += drawCard(g, t, chapter == 0, step++, px, y, pw - 4, mouseX, mouseY);
            }
        } else if (visual != null && !details) {
            if (visual.caption() != null) {
                for (var seq : font.split(visual.caption(), pw - 5)) {
                    g.text(font, seq, px + 2, y, Panel.TEXT, false);
                    y += 10;
                }
                y += 4;
            }
            if (visual.blueprint() != null) {
                if (blueprint == null) blueprint = new HandbookBlueprint(visual.blueprint());
                blueprintY = y;
                int height = blueprint.height(pw - 4);
                if (y + height > bodyTop && y < bodyTop + bodyH) {
                    Component note = blueprint.draw(g, font, px, y, pw - 4, mouseX, mouseY);
                    if (note != null && mouseY >= bodyTop && mouseY < bodyTop + bodyH) hoveredNote = note;
                }
                y += height + 4;
            }
            for (HandbookVisual.Row row : visual.rows()) {
                if (y + HandbookVisual.Row.HEIGHT > bodyTop && y < bodyTop + bodyH) {
                    Component note = row.draw(g, font, px, y, pw - 4, mouseX, mouseY);
                    if (note != null && mouseY >= bodyTop && mouseY < bodyTop + bodyH) hoveredNote = note;
                }
                y += HandbookVisual.Row.HEIGHT;
            }
        }
        if (!contents && (details || visual == null || (!route && visual.caption() == null))) {
            for (Line line : p.lines) {
                y += drawParagraph(g, line, px, y, pw, visual != null && !details);
            }
        }
        float animation = (net.minecraft.util.Util.getMillis() % 800_000L) / 50F;
        for (SupporterCredits.Entry supporter : p.supporters) {
            SupporterPlaque.draw(g, font, supporter, px, y, pw - 2, SupporterPlaque.HEIGHT, animation);
            if (Panel.inside(mouseX, mouseY, px, y, pw - 2, SupporterPlaque.HEIGHT)
                    && mouseY >= bodyTop && mouseY < bodyTop + bodyH) {
                hoveredNote = SupporterPlaque.tooltip(supporter);
            }
            y += SupporterPlaque.HEIGHT + SupporterPlaque.GAP;
        }
        g.disableScissor();
        contentHeight = y + scroll - bodyTop;
        scroll = Math.min(scroll, Math.max(0, contentHeight - bodyH));
        if (contentHeight > bodyH) {
            int thumb = Math.max(8, bodyH * bodyH / contentHeight);
            int offset = scroll * (bodyH - thumb) / (contentHeight - bodyH);
            g.fill(px + pw - 2, bodyTop, px + pw, bodyTop + bodyH, Panel.SHADE);
            g.fill(px + pw - 2, bodyTop + offset, px + pw, bodyTop + offset + thumb, Panel.WELL_EDGE);
        }
        // ページ送り
        int navY = top + bookHeight - 18;
        String count = Component.translatable("gui.singulo.handbook.page", page + 1, c.pages.size()).getString();
        if (!contents) {
            if (c.pages.size() > 1) {
                g.centeredText(font, count, px + pw - 40, navY + 2, Panel.TEXT);
                drawArrow(g, px + pw - 80, navY, "<", page > 0, mouseX, mouseY);
                drawArrow(g, px + pw - 12, navY, ">", page < c.pages.size() - 1, mouseX, mouseY);
            }
            if (hasFullText) {
                String label = Component.translatable(details ? "gui.singulo.handbook.overview" : "gui.singulo.handbook.details").getString();
                int w = Math.min(pw - 84, font.width(label) + 10);
                boolean over = Panel.inside(mouseX, mouseY, px, navY, w, 12);
                g.fill(px, navY, px + w, navY + 12, over ? 0xFFBEDBE3 : Panel.SHADE);
                g.text(font, ellipsize(label, w - 8), px + 4, navY + 2, 0xFF245B70, false);
            }
        }
        if (hoveredNote != null) {
            g.setTooltipForNextFrame(font, font.split(hoveredNote, Math.min(220, width - 20)), mouseX, mouseY);
        }
    }

    private String ellipsize(String text, int w) {
        return font.width(text) <= w ? text : font.plainSubstrByWidth(text, w - font.width("…")) + "…";
    }

    private int drawParagraph(GuiGraphicsExtractor g, Line line, int x, int y, int w, boolean compact) {
        boolean illustrated = compact && !line.icon.isEmpty();
        int inset = illustrated ? 23 : compact ? 7 : 0;
        int textWidth = w - inset - 5;
        var wrapped = font.split(line.text, textWidth);
        int shown = compact ? Math.min(3, wrapped.size()) : wrapped.size();
        if (shown < wrapped.size()) hasFullText = true;
        int height = Math.max(illustrated ? 16 : 0, shown * 10);
        if (y + height > top + 41 && y < top + bookHeight - 22) {
            if (illustrated) {
                g.item(line.icon, x + 2, y);
            } else if (compact) {
                g.fill(x + 1, y + 1, x + 3, y + 8, 0xFF74A8B7);
            }
            for (int i = 0; i < shown; i++) {
                if (i == shown - 1 && shown < wrapped.size()) {
                    StringBuilder plain = new StringBuilder();
                    wrapped.get(i).accept((pos, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                    String tail = font.plainSubstrByWidth(plain.toString(), textWidth - font.width("…")).stripTrailing() + "…";
                    g.text(font, tail, x + inset, y + i * 10, line.color, false);
                } else {
                    g.text(font, wrapped.get(i), x + inset, y + i * 10, line.color, false);
                }
            }
            if (line.link != null && targets.containsKey(line.link)) {
                linkAreas.add(new LinkArea(x, y, w, height, targets.get(line.link)));
            }
        }
        return height + (compact ? 6 : 3);
    }

    private int drawCard(GuiGraphicsExtractor g, Target t, boolean stageTitle, int step, int x, int y, int w, int mx, int my) {
        Page destination = t.chapter.pages.get(t.page);
        Component label = stageTitle ? t.chapter.title : destination.title;
        if (step > 0 && !stageTitle && destination.visual != null && destination.visual.action() != null) {
            label = destination.visual.action();
        }
        int textX = x + (step > 0 && !stageTitle ? 43 : 28);
        List<FormattedCharSequence> lines = font.split(label, x + w - textX - 13);
        int h = Math.max(22, 6 + lines.size() * 10);
        if (y + h > top + 41 && y < top + bookHeight - 22) {
            boolean over = Panel.inside(mx, my, x, y, w, h) && my >= top + 41 && my < top + bookHeight - 22;
            g.fill(x, y, x + w, y + h, over ? 0xFFD2E8EE : 0xFFF5F7F8);
            g.fill(x, y, x + 2, y + h, over ? 0xFF2A6F8A : 0xFF8CB6C1);
            g.item(destination.visual == null ? t.chapter.icon : destination.visual.icon(), x + 6, y + (h - 16) / 2);
            if (step > 0 && !stageTitle) g.text(font, Integer.toString(step), x + 29, y + (h - 8) / 2, 0xFF417282, false);
            int textY = y + (h - lines.size() * 10) / 2 + 1;
            for (var line : lines) {
                g.text(font, line, textX, textY, Panel.TEXT, false);
                textY += 10;
            }
            g.text(font, "›", x + w - 9, y + (h - 8) / 2, 0xFF417282, false);
            linkAreas.add(new LinkArea(x, y, w, h, t));
        }
        return h + 3;
    }

    /** 支援ボタンの位置と大きさ（題名の行の右端）。 */
    private int[] supportPos() {
        int w = font.width(Component.translatable("gui.singulo.handbook.support")) + 16;
        return new int[]{left + bookWidth - 6 - w, top + 4, w, 12};
    }

    private void drawSupport(GuiGraphicsExtractor g, int mx, int my) {
        int[] b = supportPos();
        boolean hover = Panel.inside(mx, my, b[0], b[1], b[2], b[3]);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? 0xFFFF7A86 : 0xFFE8D6D8);
        // 小さなハート
        int hx = b[0] + 4;
        int hy = b[1] + 4;
        int c = hover ? 0xFFFFFFFF : 0xFFE0505E;
        g.fill(hx, hy, hx + 2, hy + 2, c);
        g.fill(hx + 3, hy, hx + 5, hy + 2, c);
        g.fill(hx, hy + 1, hx + 5, hy + 3, c);
        g.fill(hx + 1, hy + 3, hx + 4, hy + 4, c);
        g.fill(hx + 2, hy + 4, hx + 3, hy + 5, c);
        g.text(font, Component.translatable("gui.singulo.handbook.support"), b[0] + 11, b[1] + 2,
                hover ? 0xFFFFFFFF : 0xFF8A3A44, false);
        if (hover) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.singulo.handbook.support_hint"), mx, my);
        }
    }

    private void drawArrow(GuiGraphicsExtractor g, int x, int y, String s, boolean enabled, int mx, int my) {
        int color = !enabled ? Panel.SEAM : Panel.inside(mx, my, x, y, 12, 12) ? Panel.GLOW : Panel.TEXT;
        g.fill(x, y, x + 12, y + 12, enabled ? Panel.SHADE : Panel.BACKGROUND);
        g.centeredText(font, s, x + 6, y + 2, color);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(g);
    }

    // ------------------------------------------------------------------ 操作

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        int[] b = supportPos();
        if (Panel.inside(mx, my, b[0], b[1], b[2], b[3])) {
            net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, SUPPORT_URL);
            return true;
        }
        if (searchBox != null && Panel.inside(mx, my, left + 6, top + 20, listWidth, SEARCH_H - 2)) {
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        int listTop = top + 20 + SEARCH_H;
        int listH = bookHeight - 28 - SEARCH_H;
        if (Panel.inside(mx, my, left + 6, listTop, listWidth, listH)) {
            int i = (int) ((my - listTop + listScroll) / ROW);
            if (searching()) {
                if (i >= 0 && i < results.size()) {
                    select(results.get(i)[0], results.get(i)[1]);
                    details = true;
                }
                return true;
            }
            if (i >= 0 && i < chapters.size()) {
                // 「進み具合」は、次の目標のある段階のページから開く
                select(i, i == 1 ? Math.min(progressStart, chapters.get(i).pages.size() - 1) : 0);
                Page first = chapters.get(i).pages.get(0);
                contents = first.visual != null && first.visual.links().isEmpty() && chapters.get(i).pages.size() > 1;
                return true;
            }
        }
        int px = left + listWidth + 14;
        int pw = bookWidth - listWidth - 22;
        Page current = chapters.get(chapter).pages.get(page);
        if (current.visual != null && chapters.get(chapter).pages.size() > 1
                && Panel.inside(mx, my, px + pw - 16, top + 19, 16, 16)) {
            contents = !contents;
            resetScroll();
            return true;
        }
        int bodyTop = top + 41;
        int bodyH = bookHeight - 63;
        if (my >= bodyTop && my < bodyTop + bodyH) {
            if (!contents && !details && blueprint != null) {
                int end = px + pw - 4;
                if (Panel.inside(mx, my, end - 34, blueprintY + 1, 15, 12)) return blueprint.turn(-1);
                if (Panel.inside(mx, my, end - 17, blueprintY + 1, 15, 12)) return blueprint.turn(1);
            }
            for (LinkArea area : linkAreas) {
                if (Panel.inside(mx, my, area.x, area.y, area.w, area.h)) {
                    Target t = area.target;
                    if (searching()) searchBox.setValue("");
                    select(chapters.indexOf(t.chapter()), t.page());
                    int max = Math.max(0, listRows() * ROW - listH);
                    listScroll = Math.clamp(listScroll, Math.max(0, (chapter + 1) * ROW - listH), Math.min(max, chapter * ROW));
                    return true;
                }
            }
        }
        int navY = top + bookHeight - 18;
        if (contents) return super.mouseClicked(event, doubleClick);
        if (hasFullText) {
            String key = details ? "gui.singulo.handbook.overview" : "gui.singulo.handbook.details";
            int w = Math.min(pw - 84, font.width(Component.translatable(key)) + 10);
            if (Panel.inside(mx, my, px, navY, w, 12)) {
                details = !details;
                resetScroll();
                return true;
            }
        }
        if (Panel.inside(mx, my, px + pw - 80, navY, 12, 12) && page > 0) {
            select(chapter, page - 1);
            return true;
        }
        if (Panel.inside(mx, my, px + pw - 12, navY, 12, 12) && page < chapters.get(chapter).pages.size() - 1) {
            select(chapter, page + 1);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < left + listWidth + 8) {
            int max = Math.max(0, listRows() * ROW - (bookHeight - 28 - SEARCH_H));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (dy * ROW)));
        } else {
            scroll = Math.clamp(scroll - (int) (dy * 12), 0, Math.max(0, contentHeight - (bookHeight - 63)));
            linkAreas.clear();
        }
        return true;
    }

    private void resetScroll() {
        hasFullText = false;
        scroll = 0;
        contentHeight = 0;
        linkAreas.clear();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key(), scan = event.scancode(), mods = event.modifiers();
        if (searchBox != null && searchBox.isFocused() && key != 256) {
            return searchBox.keyPressed(event) || searchBox.canConsumeInput();   // 打っている間は、ページ送りや画面を閉じるキーにしない
        }
        if (key == 262 && page < chapters.get(chapter).pages.size() - 1) {          // →
            select(chapter, page + 1);
            return true;
        }
        if (key == 263 && page > 0) {                                                  // ←
            select(chapter, page - 1);
            return true;
        }
        return super.keyPressed(event);
    }

    private void select(int c, int p) {
        chapter = c;
        page = p;
        resetScroll();
        blueprint = null;
        details = false;
        contents = false;
        lastPage = p;
        lastChapterTitle = chapters.get(c).title;
        lastPageTitle = chapters.get(c).pages.get(p).title;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
