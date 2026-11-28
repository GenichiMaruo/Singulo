package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.AndroidStationMenu;
import io.github.genichimaruo.singulo.android.program.Library;
import io.github.genichimaruo.singulo.android.program.Program;
import io.github.genichimaruo.singulo.android.program.ProgramCardItem;
import io.github.genichimaruo.singulo.android.program.ProgramError;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import io.github.genichimaruo.singulo.network.SaveProgramPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * プログラムカードを書く画面（0.24.0）。上に道具の段（候補・ヒントの切り替え、もう一度、完了、閉じる）、
 * 真ん中に文の欄、右に「関数」「場所」「例」「出力」のタブ、下に誤り・最後の言葉・カーソルの位置の段。
 * 打っている語の候補（関数・変数・場所の名前・物の ID）と、呼んでいる関数の引数のヒントを出す（どちらも切り替えられる）。
 * <p>
 * 手に持ったカードを使って開くか、組立ステーションの画面の「編集」から開く。ステーションから開くと、場所の名前と、
 * 動いている行・say の言葉が分かり、閉じるとステーションの画面へ戻る（保存はステーションの欄のカードへ）。
 */
public final class ProgramEditorScreen extends Screen {
    private static final int HEADER = 20, STATUS = 14, ROW = 10, TAB_H = 13, POPUP_ROW = 11, POPUP_ROWS = 8;
    private static final int C_PANEL = 0xF0141820, C_EDGE = 0xFF333A44, C_TEXT = 0xFFE6E8EE, C_DIM = 0xFF8A94A0,
            C_ACTION = 0xFFF0B070, C_QUERY = 0xFF82C8FF, C_VALUE = 0xFFB8C0CC, C_OK = 0xFF80D080, C_BAD = 0xFFFF7070,
            C_WARN = 0xFFE0C050, C_ACCENT = 0xFF5A8AD0;
    private static final String[] TABS = {"functions", "places", "examples", "output"};

    /** 一覧の1行（関数・見出し・場所・ひな形）。 */
    private record Row(@Nullable Component header, @Nullable String insert, Component text, int color, List<Component> tip,
                       @Nullable ItemStack icon) {}

    @Nullable
    private final InteractionHand hand;
    @Nullable
    private final Screen parent;
    @Nullable
    private final AndroidStationMenu station;
    /** 最後に保存した文（変えたかどうかを見る）。 */
    private String saved;
    private CodeEditor editor;
    @Nullable
    private ProgramError error;
    private List<Object[]> warnings = List.of();
    private int tab;
    private int listTop;
    private EditBox search;
    private Button completionButton, hintButton, discardButton;
    private long discardArmed;
    /** 候補の一覧（出していなければ空）と、選んでいる番号、上に見えている番号、打っている途中の語。 */
    private List<ProgramAssist.Candidate> candidates = List.of();
    private int pick, pickTop;
    private ProgramAssist.Context context = new ProgramAssist.Context("", false, false, false, null);
    private boolean forced;

    private ProgramEditorScreen(@Nullable InteractionHand hand, @Nullable Screen parent, @Nullable AndroidStationMenu station, String source) {
        super(Component.translatable("gui.singulo.program.title"));
        this.hand = hand;
        this.parent = parent;
        this.station = station;
        this.saved = source;
    }

    /** 手に持ったカードを書く。 */
    public static void open(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new ProgramEditorScreen(hand, null, null, ProgramCardItem.source(mc.player.getItemInHand(hand))));
        }
    }

    /** ステーションのプログラムの欄のカードを書く（閉じると parent へ戻る）。 */
    static void openForStation(Screen parent, AndroidStationMenu menu) {
        ItemStack card = menu.getSlot(AndroidStationBlockEntity.SLOT_PROGRAM).getItem();
        if (ProgramCardItem.isCard(card)) {
            Minecraft.getInstance().setScreen(new ProgramEditorScreen(null, parent, menu, ProgramCardItem.source(card)));
        }
    }

    // ------------------------------------------------------------------ 配置

    private int side() {
        return width < 330 ? 0 : Math.max(112, Math.min(170, width / 4));
    }

    private int sideX() {
        return width - 6 - side();
    }

    private int listY() {
        return HEADER + 2 + TAB_H + (tab == 0 ? 16 : 3);
    }

    private int bodyBottom() {
        return height - STATUS - 2;
    }

    @Override
    protected void init() {
        if (editor == null) {
            editor = new CodeEditor(font, saved, Program.MAX_SOURCE, this::changed);
            recheck();
        }
        editor.setBounds(6, HEADER + 2, width - 12 - (side() > 0 ? side() + 4 : 0), bodyBottom() - HEADER - 2);
        // 道具の段（右から）
        int x = width - 6;
        discardButton = addRenderableWidget(Button.builder(Component.literal("×"), b -> discard()).bounds(x -= 16, 3, 16, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.singulo.program.discard"))).build());
        x -= 2;
        int doneW = font.width(Component.translatable("gui.done")) + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> close(true)).bounds(x -= doneW, 3, doneW, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.singulo.program.done"))).build());
        if (station != null) {
            Component restart = Component.translatable("gui.singulo.android_station.program.restart");
            int w = font.width(restart) + 12;
            x -= 6;
            addRenderableWidget(Button.builder(restart, b -> restart()).bounds(x -= w, 3, w, 14)
                    .tooltip(Tooltip.create(Component.translatable("gui.singulo.program.restart.hint"))).build());
        }
        x -= 6;
        hintButton = addRenderableWidget(Button.builder(toggleLabel("hints", hints()), b -> {
            saveConfig(ClientConfig.PROGRAM_HINTS, !hints());
            b.setMessage(toggleLabel("hints", hints()));
        }).bounds(x -= toggleWidth("hints"), 3, toggleWidth("hints"), 14).tooltip(Tooltip.create(Component.translatable("gui.singulo.program.hints.hint"))).build());
        x -= 2;
        completionButton = addRenderableWidget(Button.builder(toggleLabel("completion", completion()), b -> {
            saveConfig(ClientConfig.PROGRAM_COMPLETION, !completion());
            b.setMessage(toggleLabel("completion", completion()));
            closePopup();
        }).bounds(x -= toggleWidth("completion"), 3, toggleWidth("completion"), 14)
                .tooltip(Tooltip.create(Component.translatable("gui.singulo.program.completion.hint"))).build());
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, sideX() + 3, HEADER + 2 + TAB_H + 2, Math.max(10, side() - 6), 12,
                Component.translatable("gui.singulo.program.search"));
        search.setHint(Component.translatable("gui.singulo.program.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(s -> listTop = 0);
        search.visible = side() > 0 && tab == 0;
        addWidget(search);
    }

    private static boolean completion() {
        return ClientConfig.PROGRAM_COMPLETION.get();
    }

    private static boolean hints() {
        return ClientConfig.PROGRAM_HINTS.get();
    }

    private static <T> void saveConfig(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T v) {
        value.set(v);
        value.save();
    }

    private Component toggleLabel(String key, boolean on) {
        return Component.literal(on ? "✔ " : "✖ ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.translatable("gui.singulo.program." + key).withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY));
    }

    private int toggleWidth(String key) {
        return font.width(toggleLabel(key, true)) + 10;
    }

    // ------------------------------------------------------------------ 文・保存

    private void changed() {
        recheck();
        discardArmed = 0;
    }

    private void recheck() {
        String text = editor.text();
        error = Program.check(text);
        editor.setErrorLine(error == null ? 0 : error.line());
        warnings = station != null ? ProgramAssist.unknownPlaces(text, placeNames()) : List.of();
        java.util.Set<Integer> lines = new java.util.HashSet<>();
        for (Object[] w : warnings) {
            lines.add((Integer) w[0]);
        }
        editor.setWarningLines(lines);
    }

    private boolean modified() {
        return !editor.text().equals(saved);
    }

    private void save() {
        String source = editor.text();
        if (minecraft == null) {
            return;
        }
        if (station != null) {
            PacketDistributor.sendToServer(new SaveProgramPayload(station.containerId, false, source));
        } else if (hand != null) {
            if (minecraft.player != null && ProgramCardItem.isCard(minecraft.player.getItemInHand(hand))) {
                ProgramCardItem.setSource(minecraft.player.getItemInHand(hand), source);   // 手元もすぐ変える
            }
            PacketDistributor.sendToServer(new SaveProgramPayload(-1, hand == InteractionHand.OFF_HAND, source));
        }
        saved = source;
    }

    private void close(boolean keep) {
        if (keep) {
            save();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** 変えていれば、2回押して捨てる。 */
    private void discard() {
        long now = net.minecraft.Util.getMillis();
        if (modified() && now - discardArmed > 3000) {
            discardArmed = now;
            discardButton.setMessage(Component.literal("×").withStyle(ChatFormatting.RED));
            discardButton.setTooltip(Tooltip.create(Component.translatable("gui.singulo.program.discard.confirm")));
            return;
        }
        close(false);
    }

    /** ステーションのアンドロイドのプログラムを最初から（変えていれば、保存してから。保存でも最初から動く）。 */
    private void restart() {
        if (station == null || minecraft == null || minecraft.gameMode == null) {
            return;
        }
        if (modified()) {
            save();
        }
        minecraft.gameMode.handleInventoryButtonClick(station.containerId, AndroidStationBlockEntity.BUTTON_PROGRAM_RESTART);
    }

    @Override
    public void onClose() {
        close(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // ステーションの画面が閉じられた（ステーションが壊れたなど）: 保存先がないので、手元の文はそのままにして知らせる
        if (station != null && minecraft != null && minecraft.player != null && minecraft.player.containerMenu != station) {
            minecraft.setScreen(null);
            return;
        }
        if (station != null) {
            int status = station.value(AndroidStationBlockEntity.D_PROGRAM);
            boolean running = status == 1 || status == 2 || status == 5;
            editor.setRunningLine(running && !modified() ? station.value(AndroidStationBlockEntity.D_PROGRAM_LINE) : 0);
        }
    }

    // ------------------------------------------------------------------ 場所

    /** ステーションの場所（巡回路の欄）。{名前, 場所, カード} の並び。手に持って開いたときは空。 */
    private List<Object[]> places() {
        List<Object[]> out = new ArrayList<>();
        if (station == null) {
            return out;
        }
        for (int i = 0; i < AndroidStationBlockEntity.ROUTE_SLOTS; i++) {
            ItemStack card = station.getSlot(AndroidStationBlockEntity.SLOT_ROUTE + i).getItem();
            BlockPos p = io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.pos(card);
            Component custom = card.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            String name = p == null ? null : custom != null ? custom.getString().strip() : "p" + (i + 1);
            out.add(new Object[] {name, p, card, i});
        }
        return out;
    }

    /** 呼べる場所の名前（名前と、カードのある欄の p 番号）。手に持って開いたときは p1〜p8。 */
    private List<String> placeNames() {
        List<String> out = new ArrayList<>();
        if (station == null) {
            for (int i = 1; i <= AndroidStationBlockEntity.ROUTE_SLOTS; i++) {
                out.add("p" + i);
            }
            return out;
        }
        for (Object[] p : places()) {
            if (p[0] != null) {
                out.add((String) p[0]);
                String alias = "p" + ((int) p[3] + 1);
                if (!out.contains(alias)) {
                    out.add(alias);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 候補とヒント

    private void refreshAssist() {
        context = ProgramAssist.context(editor.currentLine(), editor.column());
        if (!completion() || editor.selecting()) {
            closePopup();
            return;
        }
        List<ProgramAssist.Candidate> next = ProgramAssist.candidates(context, editor.text(), placeNames(), forced);
        if (next.isEmpty()) {
            closePopup();
            return;
        }
        String keep = candidates.isEmpty() || pick >= candidates.size() ? null : candidates.get(pick).label();
        candidates = next;
        pick = 0;
        for (int i = 0; keep != null && i < next.size(); i++) {
            if (next.get(i).label().equals(keep)) {
                pick = i;
            }
        }
        revealPick();
    }

    private void closePopup() {
        candidates = List.of();
        forced = false;
        pick = 0;
        pickTop = 0;
    }

    private void revealPick() {
        if (pick < pickTop) {
            pickTop = pick;
        } else if (pick >= pickTop + POPUP_ROWS) {
            pickTop = pick - POPUP_ROWS + 1;
        }
    }

    private void accept() {
        if (candidates.isEmpty()) {
            return;
        }
        ProgramAssist.Candidate c = candidates.get(pick);
        String text = c.insert();
        if (c.kind() == ProgramAssist.Kind.FUNCTION || c.kind() == ProgramAssist.Kind.METHOD) {
            text += "(" + (takesNothing(c) ? ")" : "");
        }
        editor.replaceBefore(context.prefix().length(), text);
        closePopup();
        context = ProgramAssist.context(editor.currentLine(), editor.column());
    }

    private boolean takesNothing(ProgramAssist.Candidate c) {
        if (c.kind() == ProgramAssist.Kind.METHOD) {
            ProgramHelp.Fn m = ProgramHelp.METHODS.get(c.insert());
            return m != null && m.params().isEmpty();
        }
        Library.Sig sig = Library.FUNCTIONS.get(c.insert());
        if (sig != null) {
            return sig.max() == 0;
        }
        List<String> params = userParams(c.insert());
        return params != null && params.isEmpty();
    }

    /** 自分で定めた関数の引数（なければ null）。 */
    @Nullable
    private List<String> userParams(String name) {
        for (String line : editor.text().split("\n", -1)) {
            var m = java.util.regex.Pattern.compile("^\\s*def\\s+" + java.util.regex.Pattern.quote(name) + "\\s*\\(([^)]*)\\)").matcher(line);
            if (m.find()) {
                List<String> out = new ArrayList<>();
                for (String p : m.group(1).split(",")) {
                    if (!p.isBlank()) {
                        out.add(p.strip());
                    }
                }
                return out;
            }
        }
        return null;
    }

    /** 呼んでいる関数のヒント（引数を並べ、今の引数を目立たせる）と、その説明。なければ null。 */
    @Nullable
    private List<Component> hint() {
        ProgramAssist.Call call = context.call();
        if (!hints() || call == null || context.inComment()) {
            return null;
        }
        List<String> params;
        Component description = null;
        if (call.method()) {
            ProgramHelp.Fn m = ProgramHelp.METHODS.get(call.name());
            if (m == null) {
                return null;
            }
            params = m.params();
        } else if (ProgramHelp.FUNCTIONS.containsKey(call.name()) || call.name().equals("print")) {
            ProgramHelp.Fn f = ProgramHelp.FUNCTIONS.get(call.name().equals("print") ? "say" : call.name());
            params = f.params();
            description = Component.translatable("program.singulo.fn." + f.name());
        } else {
            params = userParams(call.name());
            if (params == null) {
                return null;
            }
        }
        MutableComponent line = Component.literal(call.name() + "(").withColor(C_QUERY);
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                line.append(Component.literal(", ").withColor(C_DIM));
            }
            boolean current = i == call.argument() || params.get(i).equals("...") && call.argument() >= i;
            line.append(Component.literal(params.get(i)).withColor(current ? 0xFFFFE070 : C_DIM)
                    .withStyle(current ? ChatFormatting.UNDERLINE : ChatFormatting.RESET));
        }
        line.append(Component.literal(")").withColor(C_QUERY));
        List<Component> out = new ArrayList<>();
        out.add(line);
        if (description != null) {
            out.add(description.copy().withColor(C_TEXT));
        }
        return out;
    }

    // ------------------------------------------------------------------ 入力

    private boolean editorFocused() {
        return getFocused() == null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!candidates.isEmpty() && editorFocused()) {
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    pick = (pick - 1 + candidates.size()) % candidates.size();
                    revealPick();
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    pick = (pick + 1) % candidates.size();
                    revealPick();
                    return true;
                }
                case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    accept();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    closePopup();
                    return true;
                }
                default -> {
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!editorFocused()) {
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        if (editorFocused()) {
            if (key == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
                forced = true;
                refreshAssist();
                if (candidates.isEmpty()) {
                    forced = false;
                }
                return true;
            }
            if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_S) {
                save();
                return true;
            }
            if (editor.keyPressed(key, modifiers)) {
                boolean moved = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_HOME
                        || key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
                if (moved && candidates.isEmpty()) {
                    context = ProgramAssist.context(editor.currentLine(), editor.column());
                } else {
                    refreshAssist();
                }
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (editorFocused() && editor.charTyped(c)) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '"' && c != '\'' && c != '#' && c != ':' && c != '.') {
                forced = false;
            }
            refreshAssist();
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int popup = popupAt(mx, my);
        if (popup >= 0) {
            pick = popup;
            accept();
            return true;
        }
        if (editor.mouseClicked(mx, my, button)) {
            setFocused(null);
            closePopup();
            context = ProgramAssist.context(editor.currentLine(), editor.column());
            return true;
        }
        if (my >= height - STATUS && my < height && mx < width / 2.0) {
            int line = error != null ? error.line() : warnings.isEmpty() ? 0 : (Integer) warnings.get(0)[0];
            if (line > 0) {
                editor.jumpTo(line);
                setFocused(null);
                return true;
            }
        }
        if (side() > 0) {
            int sx = sideX();
            int w = side() / TABS.length;
            if (my >= HEADER + 2 && my < HEADER + 2 + TAB_H && mx >= sx && mx < sx + side()) {
                tab = Math.min(TABS.length - 1, (int) ((mx - sx) / w));
                listTop = 0;
                search.visible = tab == 0;
                setFocused(null);
                return true;
            }
            if (tab == 0 && search.mouseClicked(mx, my, button)) {
                setFocused(search);
                search.setFocused(true);
                return true;
            }
            Row row = rowAt(mx, my);
            if (row != null && row.insert() != null) {
                insertFromList(row.insert());
                return true;
            }
        }
        boolean r = super.mouseClicked(mx, my, button);
        search.setFocused(false);
        setFocused(null);                       // ボタンを押したあとも、打った字は欄へ
        return r;
    }

    private void insertFromList(String text) {
        if (tab == 2) {
            // ひな形: 欄が空なら置き換え、そうでなければ次の行へ
            if (editor.text().isBlank()) {
                editor.replaceBefore(editor.column(), "");
                editor.insertText(text);
            } else {
                editor.insertText((editor.currentLine().isBlank() ? "" : "\n") + text);
            }
        } else {
            editor.insertText(text);
        }
        setFocused(null);
        search.setFocused(false);
        refreshAssist();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return editor.mouseDragged(mx, my) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        editor.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!candidates.isEmpty() && popupBox() != null && inside(popupBox(), mx, my)) {
            pick = Math.max(0, Math.min(candidates.size() - 1, pick - (int) Math.signum(sy)));
            revealPick();
            return true;
        }
        if (side() > 0 && mx >= sideX()) {
            listTop = Math.max(0, Math.min(Math.max(0, rows().size() - listRows()), listTop - (int) Math.signum(sy) * 3));
            return true;
        }
        if (editor.mouseScrolled(mx, my, sy)) {
            closePopup();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private static boolean inside(int[] box, double mx, double my) {
        return mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
    }

    // ------------------------------------------------------------------ 右の一覧

    private int listRows() {
        return Math.max(1, (bodyBottom() - listY()) / ROW);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
                for (ProgramHelp.Category cat : ProgramHelp.Category.values()) {
                    List<Row> group = new ArrayList<>();
                    int color = cat == ProgramHelp.Category.ACTION ? C_ACTION : cat == ProgramHelp.Category.QUERY ? C_QUERY : C_VALUE;
                    for (ProgramHelp.Fn f : ProgramHelp.of(cat)) {
                        Component desc = Component.translatable("program.singulo.fn." + f.name());
                        if (!q.isEmpty() && !f.name().contains(q) && !desc.getString().toLowerCase(Locale.ROOT).contains(q)) {
                            continue;
                        }
                        group.add(new Row(null, f.name() + "(", Component.literal(f.signature()), color,
                                List.of(Component.literal(f.signature()).withColor(color), desc), null));
                    }
                    if (!group.isEmpty()) {
                        out.add(new Row(Component.translatable("gui.singulo.program.category." + cat.name().toLowerCase(Locale.ROOT)), null,
                                Component.empty(), C_DIM, List.of(), null));
                        out.addAll(group);
                    }
                }
            }
            case 1 -> {
                if (station == null) {
                    return out;
                }
                for (Object[] p : places()) {
                    int slot = (int) p[3];
                    if (p[0] == null) {
                        out.add(new Row(null, null, Component.translatable("gui.singulo.program.places.empty", slot + 1), C_DIM, List.of(), null));
                        continue;
                    }
                    String name = (String) p[0];
                    BlockPos pos = (BlockPos) p[1];
                    var block = io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.block((ItemStack) p[2]);
                    ItemStack icon = block == null ? ItemStack.EMPTY : new ItemStack(block);
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.literal("\"" + name + "\"").withColor(C_ACTION));
                    tip.add(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withColor(C_DIM));
                    if (!icon.isEmpty()) {
                        tip.add(icon.getHoverName());
                    }
                    tip.add(Component.translatable("gui.singulo.program.places.click").withColor(C_DIM));
                    out.add(new Row(null, "\"" + name + "\"", Component.literal(name), C_TEXT, tip, icon));
                }
            }
            case 2 -> {
                for (ProgramHelp.Snippet s : ProgramHelp.SNIPPETS) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("gui.singulo.program.snippet." + s.id() + ".desc"));
                    for (String l : s.code().split("\n")) {
                        tip.add(Component.literal(l).withColor(C_DIM));
                    }
                    out.add(new Row(null, s.code(), Component.translatable("gui.singulo.program.snippet." + s.id() + ".title"), C_TEXT, tip, null));
                }
            }
            default -> {
                if (station == null) {
                    return out;
                }
                Minecraft mc = Minecraft.getInstance();
                int unit = station.value(AndroidStationBlockEntity.D_SELECTED);
                List<String> log = mc.level != null && mc.level.getBlockEntity(station.pos()) instanceof AndroidStationBlockEntity be
                        ? be.programLog(unit) : List.of();
                for (String l : log) {
                    out.add(new Row(null, null, Component.literal("▸ " + l), C_TEXT, List.of(Component.literal(l)), null));
                }
            }
        }
        return out;
    }

    @Nullable
    private Row rowAt(double mx, double my) {
        if (side() == 0 || mx < sideX() || mx >= sideX() + side() || my < listY() || my >= bodyBottom()) {
            return null;
        }
        int i = listTop + (int) ((my - listY()) / ROW);
        List<Row> rows = rows();
        return i >= 0 && i < rows.size() && (i - listTop) < listRows() ? rows.get(i) : null;
    }

    // ------------------------------------------------------------------ 描く

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tip = new ArrayList<>();
        // 見出し（カードの最初の行のコメントがあれば、それも）
        String title = ProgramCardItem.title(editor.text());
        Component head = title.isEmpty() ? this.title : this.title.copy().append(Component.literal(" — " + title).withColor(C_DIM));
        fit(g, head, 8, 7, Math.max(20, completionButton.getX() - 14), 0xFFFFFFFF);
        if (modified()) {
            g.drawString(font, "●", Math.min(8 + font.width(head) + 4, completionButton.getX() - 10), 7, C_WARN, false);
        }
        editor.render(g, editorFocused());
        if (side() > 0) {
            renderSide(g, mouseX, mouseY, tip);
        }
        renderStatus(g, mouseX, mouseY, tip);
        renderAssist(g, mouseX, mouseY);
        if (!tip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tip) {
                lines.addAll(font.split(c, 220));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void renderSide(GuiGraphics g, int mouseX, int mouseY, List<Component> tip) {
        int sx = sideX();
        int w = side();
        int top = HEADER + 2;
        g.fill(sx, top, sx + w, bodyBottom(), C_PANEL);
        g.renderOutline(sx, top, w, bodyBottom() - top, C_EDGE);
        int tw = w / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = sx + i * tw;
            int tW = i == TABS.length - 1 ? w - i * tw : tw;
            boolean on = i == tab;
            boolean hover = mouseX >= tx && mouseX < tx + tW && mouseY >= top && mouseY < top + TAB_H;
            g.fill(tx + 1, top + 1, tx + tW - 1, top + TAB_H, on ? 0xFF26303E : hover ? 0xFF1E2530 : 0xFF181D25);
            if (on) {
                g.fill(tx + 1, top + TAB_H - 1, tx + tW - 1, top + TAB_H, C_ACCENT);
            }
            Component label = Component.translatable("gui.singulo.program.tab." + TABS[i]);
            int lw = Math.min(font.width(label), tW - 4);
            fit(g, label, tx + (tW - lw) / 2, top + 3, tW - 4, on ? 0xFFFFFFFF : C_DIM);
        }
        if (tab == 0) {
            search.render(g, mouseX, mouseY, 0);
        }
        List<Row> rows = rows();
        int y = listY();
        if (rows.isEmpty()) {
            String key = tab == 1 && station == null ? "gui.singulo.program.places.hand" : tab == 3 && station == null
                    ? "gui.singulo.program.output.hand" : tab == 3 ? "gui.singulo.program.output.empty" : "gui.singulo.program.none";
            int ty = y + 2;
            for (FormattedCharSequence l : font.split(Component.translatable(key), w - 8)) {
                g.drawString(font, l, sx + 4, ty, C_DIM, false);
                ty += ROW;
            }
        }
        if (tab == 3 && station != null) {
            // 出力のタブの頭: 様子（ステーションの画面と同じ文）と、誤りの説明
            Component status = stationStatus();
            fit(g, status, sx + 4, y - 1, w - 8, station.value(AndroidStationBlockEntity.D_PROGRAM) == 4 ? C_BAD : C_OK);
        }
        int shown = listRows();
        for (int i = listTop; i < rows.size() && i - listTop < shown; i++) {
            Row r = rows.get(i);
            int ry = y + (i - listTop) * ROW + (tab == 3 ? ROW : 0);
            if (ry + ROW > bodyBottom()) {
                break;
            }
            if (r.header() != null) {
                fit(g, r.header(), sx + 3, ry + 1, w - 6, C_DIM);
                g.fill(sx + 3 + Math.min(font.width(r.header()), w - 8) + 3, ry + 5, sx + w - 4, ry + 6, 0xFF2A323C);
                continue;
            }
            boolean hover = mouseX >= sx && mouseX < sx + w && mouseY >= ry && mouseY < ry + ROW;
            if (hover && r.insert() != null) {
                g.fill(sx + 1, ry - 1, sx + w - 1, ry + ROW - 1, 0x30FFFFFF);
            }
            int tx = sx + 5;
            if (r.icon() != null && !r.icon().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(tx, ry - 1, 0);
                g.pose().scale(0.625F, 0.625F, 1);
                g.renderItem(r.icon(), 0, 0);
                g.pose().popPose();
                tx += 12;
            }
            fit(g, r.text(), tx, ry, sx + w - 4 - tx, r.color());
            if (hover && !r.tip().isEmpty()) {
                tip.addAll(r.tip());
            }
        }
        if (rows.size() > shown) {
            int barH = Math.max(8, (bodyBottom() - y) * shown / rows.size());
            int barY = y + (bodyBottom() - y - barH) * listTop / Math.max(1, rows.size() - shown);
            g.fill(sx + w - 3, barY, sx + w - 1, barY + barH, 0x80A0A8B4);
        }
    }

    private Component stationStatus() {
        if (station == null) {
            return Component.empty();
        }
        int status = Math.max(0, Math.min(5, station.value(AndroidStationBlockEntity.D_PROGRAM)));
        Component text = Component.translatable("gui.singulo.android_station.program." + status, station.value(AndroidStationBlockEntity.D_PROGRAM_LINE));
        if (status == 4 && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(station.pos()) instanceof AndroidStationBlockEntity be) {
            Component why = AndroidStationBlockEntity.describeProgramMessage(be.programMessage(station.value(AndroidStationBlockEntity.D_SELECTED)));
            return text.copy().append(Component.literal(": ")).append(why);
        }
        return text;
    }

    private void renderStatus(GuiGraphics g, int mouseX, int mouseY, List<Component> tip) {
        int y = height - STATUS;
        g.fill(0, y - 1, width, height, 0xE00E1116);
        Component left;
        int color;
        if (error != null) {
            left = Component.literal("✖ ").append(error.describe());
            color = C_BAD;
        } else if (!warnings.isEmpty()) {
            Object[] w = warnings.get(0);
            left = Component.literal("⚠ ").append(Component.translatable("gui.singulo.program.warn.no_point", w[0], w[1]));
            color = C_WARN;
        } else {
            left = Component.literal("✔ ").append(Component.translatable("gui.singulo.program.ok"));
            color = C_OK;
        }
        String where = Component.translatable("gui.singulo.program.cursor", editor.cursorLine(), editor.cursorColumn()).getString()
                + "  " + editor.text().length() + "/" + Program.MAX_SOURCE;
        int whereX = width - 6 - font.width(where);
        g.drawString(font, where, whereX, y + 3, C_DIM, false);
        int leftMax = width / 2 - 10;
        fit(g, left, 6, y + 3, leftMax, color);
        if (mouseY >= y && mouseX < 6 + leftMax && font.width(left) > leftMax) {
            tip.add(left);
        }
        if (station != null && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(station.pos()) instanceof AndroidStationBlockEntity be) {
            List<String> log = be.programLog(station.value(AndroidStationBlockEntity.D_SELECTED));
            if (!log.isEmpty()) {
                Component said = Component.literal("▸ " + log.get(log.size() - 1));
                fit(g, said, width / 2, y + 3, whereX - width / 2 - 8, C_TEXT);
            }
        }
    }

    /** 候補の一覧の枠 {x, y, 幅, 高さ}（出していなければ null）。 */
    @Nullable
    private int[] popupBox() {
        if (candidates.isEmpty() || !editor.cursorVisible()) {
            return null;
        }
        int w = 90;
        for (ProgramAssist.Candidate c : candidates) {
            w = Math.max(w, font.width(c.label()) + 24);
        }
        w = Math.min(w, 200);
        int rows = Math.min(POPUP_ROWS, candidates.size());
        int h = rows * POPUP_ROW + 4;
        int x = Math.max(2, Math.min(width - w - 2, editor.cursorX() - font.width(context.prefix()) - 3));
        int y = editor.cursorY() + CodeEditor.LINE_H + 1;
        if (y + h > bodyBottom() && editor.cursorY() - h - 1 > HEADER) {
            y = editor.cursorY() - h - 1;
        }
        return new int[] {x, y, w, h};
    }

    private int popupAt(double mx, double my) {
        int[] box = popupBox();
        if (box == null || !inside(box, mx, my)) {
            return -1;
        }
        int i = pickTop + (int) ((my - box[1] - 2) / POPUP_ROW);
        return i >= 0 && i < candidates.size() ? i : -1;
    }

    private void renderAssist(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int[] box = popupBox();
        List<Component> hint = hint();
        if (hint != null && editorFocused() && editor.cursorVisible()) {
            int w = 0;
            for (Component c : hint) {
                w = Math.max(w, font.width(c));
            }
            w = Math.min(w + 8, 260);
            int h = hint.size() * ROW + 4;
            int x = Math.max(2, Math.min(width - w - 2, editor.cursorX() - 4));
            boolean below = box != null && box[1] < editor.cursorY();     // 候補が上に出ているときは、ヒントを下へ
            int y = below ? editor.cursorY() + CodeEditor.LINE_H + 1 : editor.cursorY() - h - 1;
            if (!below && y < HEADER) {
                y = editor.cursorY() + CodeEditor.LINE_H + 1 + (box != null ? box[3] + 1 : 0);
            }
            g.fill(x, y, x + w, y + h, 0xF0202836);
            g.renderOutline(x, y, w, h, 0xFF4A5A70);
            int ty = y + 3;
            for (Component c : hint) {
                fit(g, c, x + 4, ty, w - 8, C_TEXT);
                ty += ROW;
            }
        }
        if (box != null) {
            g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], 0xF8181E28);
            g.renderOutline(box[0], box[1], box[2], box[3], 0xFF4A5A70);
            for (int i = pickTop; i < candidates.size() && i - pickTop < POPUP_ROWS; i++) {
                ProgramAssist.Candidate c = candidates.get(i);
                int ry = box[1] + 2 + (i - pickTop) * POPUP_ROW;
                if (i == pick) {
                    g.fill(box[0] + 1, ry, box[0] + box[2] - 1, ry + POPUP_ROW, 0xFF2E4A70);
                } else if (popupAt(mouseX, mouseY) == i) {
                    g.fill(box[0] + 1, ry, box[0] + box[2] - 1, ry + POPUP_ROW, 0x40FFFFFF);
                }
                int tx = box[0] + 4;
                if (c.item() != null) {
                    g.pose().pushPose();
                    g.pose().translate(tx, ry + 0.5F, 0);
                    g.pose().scale(0.625F, 0.625F, 1);
                    g.renderItem(new ItemStack(c.item()), 0, 0);
                    g.pose().popPose();
                } else {
                    g.drawString(font, kindMark(c.kind()), tx + 1, ry + 2, kindColor(c.kind()), false);
                }
                tx += 13;
                fit(g, Component.literal(c.label()), tx, ry + 2, box[0] + box[2] - 4 - tx, i == pick ? 0xFFFFFFFF : C_TEXT);
            }
            if (candidates.size() > POPUP_ROWS) {
                int barH = Math.max(6, (box[3] - 4) * POPUP_ROWS / candidates.size());
                int barY = box[1] + 2 + (box[3] - 4 - barH) * pickTop / Math.max(1, candidates.size() - POPUP_ROWS);
                g.fill(box[0] + box[2] - 3, barY, box[0] + box[2] - 1, barY + barH, 0x80A0A8B4);
            }
        }
        g.pose().popPose();
    }

    private static String kindMark(ProgramAssist.Kind k) {
        return switch (k) {
            case KEYWORD -> "k";
            case FUNCTION -> "ƒ";
            case VARIABLE -> "v";
            case METHOD -> "m";
            case ATTRIBUTE -> ".";
            case PLACE -> "◆";
            case TAG -> "#";
            case ITEM -> "▪";
        };
    }

    private static int kindColor(ProgramAssist.Kind k) {
        return switch (k) {
            case KEYWORD -> 0xFFC792EA;
            case FUNCTION -> C_QUERY;
            case VARIABLE -> C_TEXT;
            case METHOD, ATTRIBUTE -> 0xFFA6E08C;
            case PLACE -> C_ACTION;
            case TAG, ITEM -> C_DIM;
        };
    }

    /** 幅に収まらなければ「…」で切って描く。 */
    private void fit(GuiGraphics g, Component text, int x, int y, int max, int color) {
        if (max <= 0) {
            return;
        }
        if (font.width(text) <= max) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        int dots = font.width("…");
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, Math.max(0, max - dots))),
                x, y, color, false);
        g.drawString(font, "…", x + max - dots, y, color, false);
    }
}
