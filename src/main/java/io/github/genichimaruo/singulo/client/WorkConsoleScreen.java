package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkConsoleMenu;
import io.github.genichimaruo.singulo.android.work.WorkOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * アンドロイド作業指令台の画面。幅はインベントリに合わせた 176 で、高さは注文の一覧の行数（設定）に合わせて伸び縮みする。
 * <ul>
 * <li>注文: 注文できる物（作れる物には光る印）が並ぶ。選ぶと、個数・必要な素材・割り当てるアンドロイドを決める窓が開く。</li>
 * <li>進行中: 注文ごとの進み具合と状態。取り消せる。行を選ぶと、詳しい様子の窓が開く。</li>
 * <li>つながり: つないだ資材口・納品口・レシピ棚。外せる。</li>
 * <li>レシピ登録（窓）: 3×3 に見本を置いて（物は減らない）、レシピカードに記録する。JEI の「+」でも開く。</li>
 * <li>納品箱: 指令台に届いた完成品（ホッパーなどでも取り出せる）。</li>
 * </ul>
 * 窓（{@link Popup}）は上の帯をつかんで動かせ、下の画面もそのまま触れる。注文の確認では、納品先（指令台か、名前の付いた納品口）を選ぶ。
 * 数は5桁まで小さな字で出し、10000 からは 10K・10M・10G… と縮めて出す。
 */
public class WorkConsoleScreen extends FramedScreen<WorkConsoleMenu> {
    private static final int TAB_RECORD = 3, TAB_OUTBOX = 4, TAB_STANDING = 5, TAB_ROUTE = 6;
    /** タブの並び（左から）: 注文・登録・進行中・つながり・記録・納品箱。 */
    private static final int[] TAB_ORDER = {0, TAB_STANDING, 1, 2, TAB_ROUTE, 3, 4};
    private static final String[] TAB_KEYS = {"order", "running", "links", "record", "outbox", "standing", "routes"};
    /** 注文の確認: きっかけのボタン（-1 は今すぐ注文、0〜 は登録。{@link StandingOrder}）。 */
    private static final int TRIGGER_X = 8, TRIGGER_W = 116;
    /** 注文の確認: 納品先を選ぶボタン。 */
    private static final int DEST_X = 128, DEST_Y = 27, DEST_W = 112, DEST_H = 14;
    private static final int TOP = 24;
    /** 注文タブの物の一覧: インベントリと同じ9列で、列もインベントリにそろえる。 */
    private static final int COLS = 9, CELL = 18, GRID_LEFT = 7;
    /** 注文タブの物の一覧の行数（設定 workOrderRows）。行を増やすと画面ごと縦に伸びる（画面に収まるところまで）。 */
    private static final int MIN_ROWS = 3, MAX_ROWS = 10;
    /** 一覧の行数のほかの、画面の高さ（ヘッダー・下の段・インベントリ）。 */
    private static final int BASE_HEIGHT = 138;
    /** 注文タブの下の段（インベントリの見出しのすぐ上）: 検索欄・並びのボタン・逆順のボタン・JEI 連携のボタン・行の増減。 */
    private static final int SEARCH_H = 12, SEARCH_X = 7, SEARCH_W = 86,
            SORT_X = 95, SORT_W = 14, REV_X = 111, REV_W = 12, JEI_X = 125, JEI_W = 20, ROWS_LESS_X = 147, ROWS_MORE_X = 159, ROWS_BTN_W = 10;
    private static final String[] SORTS = {"default", "name", "count", "mod", "craftable"};
    private static final int ROW_H = 18;
    private static final int LINK_H = 13;
    /** 行の右端の小さなボタン（もう一度注文・取り消し・外す）。 */
    private static final int AGAIN_X = 140, CANCEL_X = 154, ROW_BTN = 12;
    private static final int LIST_Y = 61, LIST_ROWS = 6, LIST_H = 16;
    /** 注文の確認の下の段: きっかけ・始める（やめるのは窓の×）。 */
    private static final int START_X = 128, START_W = 112, BUTTON_Y = 176, BUTTON_H = 16;
    /** 注文の確認: 「新規」（完成品の在庫を使わず、新しく作る）のボタン。 */
    private static final int FRESH_X = 90, FRESH_Y = 27, FRESH_W = 34, FRESH_H = 14;
    /**
     * 窓（注文の確認・注文の詳しい様子・レシピ登録）の幅と高さ。中身は前の画面の座標のまま、窓の中へずらして描く
     * （{@link #pickX()} など。注文の確認は y 27 から、詳しい様子は y 24 から、レシピ登録は y 29 から）。
     */
    private static final int POP_W = 246, PICK_H = Popup.BAR + 1 + BUTTON_Y + BUTTON_H + 4 - 25, RECORD_H = Popup.BAR + 1 + WorkConsoleMenu.RECORD_BODY_H,
            DETAIL_H_POP = Popup.BAR + 1 + 85;
    /** 作っている最中の物の枠の色。 */
    private static final int MAKING = 0xFFF0B040;
    private static final int RED = 0xFFD04848, GREEN = 0xFF3A9A60, MUTED = 0xFF8A949E;

    private int tab;
    private int scroll;
    private int listScroll;
    private int androidScroll;
    /** 注文の確認中の物（なければ null）。 */
    @Nullable
    private ItemStack picking;
    @Nullable
    private EditBox countBox;
    private long count = 1;
    /** 完成品の在庫を使わず、新しく作る（今すぐの注文だけ。在庫を保つ登録は、もともと新しく作る）。 */
    private boolean fresh;
    /** 個数を変えたあと、計算を頼むまでの待ち（打つたびに頼まないように）。 */
    private int planDelay;
    @Nullable
    private CompoundTag plan;
    @Nullable
    private Component error;
    /** 進行中のページで、詳しく見ている注文（なければ -1）。 */
    private int detail = -1;
    private int detailScroll, workerScroll;
    private static final int DETAIL_ROWS = 5, DETAIL_H = 16;
    /** 窓を描く奥行きの始まり（下の画面の物の絵と数より手前）。窓ごとに 300 ずつ手前へ。 */
    private static final int POPUP_Z = 500;
    /** 窓: 注文の確認（picking があるあいだ開く）・詳しい様子（detail があるあいだ）・レシピ登録。stack の後ろほど手前に描く。 */
    private final Popup orderPop = new Popup("console.order");
    private final Popup detailPop = new Popup("console.detail");
    private final Popup recordPop = new Popup("console.record");
    private final List<Popup> stack = new ArrayList<>(List.of(detailPop, orderPop, recordPop));
    /** レシピ登録の窓のスロット（1〜18）の、窓の中の位置（メニューが決めた座標）。 */
    private final int[][] recordSlotBase = new int[WorkConsoleMenu.SLOT_OUTBOX][];
    /** 注文の確認で選んだきっかけ（-1: 今すぐ注文）。 */
    private int trigger = -1;
    /** 登録を送ったところ（返事で登録のページへ移る）。 */
    private boolean registering;
    private final Set<UUID> chosen = new HashSet<>();
    /** 選んだ納品先（様子の deliveries の番号）。 */
    private int destination;
    private int seenVersion = -1;
    /** 注文タブの検索欄（押してから文字を打てる。勝手に打ち込み先にはならない）。 */
    @Nullable
    private EditBox searchBox;
    /** 絞り込み・並べ替えた注文の一覧の覚え（様子・検索の文字・並びが変わったら作り直す）。 */
    private List<CompoundTag> visible = List.of();
    private Object visibleKey;

    public WorkConsoleScreen(WorkConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WorkConsoleMenu.WIDTH, WorkConsoleMenu.HEIGHT);
        for (int i = WorkConsoleMenu.SLOT_GRID; i < WorkConsoleMenu.SLOT_OUTBOX; i++) {
            recordSlotBase[i] = new int[]{menu.getSlot(i).x, menu.getSlot(i).y};
        }
    }

    // ------------------------------------------------------------------ 枠の右にはみ出すタブ（ページと許可証）

    /** タブ i（0〜6 がページ、7 が許可証）の範囲。 */
    private int[] outerTab(int i) {
        return new int[]{frameX() + io.github.genichimaruo.singulo.machine.GuiFrame.railX(frameWidth()),
                topPos + io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_TOP + i * WorkConsoleMenu.TAB_PITCH, 22, 22};
    }

    @Override
    protected List<int[]> outsideAreas() {
        List<int[]> areas = new ArrayList<>();
        for (int i = 0; i <= WorkConsoleMenu.PAGE_TABS; i++) {
            areas.add(outerTab(i));
        }
        return areas;
    }

    /** タブを描く（connected: 開いているページと許可証の段。枠とつながった形）。枠の前後で2回呼ぶ。 */
    private void drawOuterTabs(GuiGraphics g, int mouseX, int mouseY, boolean connected) {
        for (int i = 0; i <= WorkConsoleMenu.PAGE_TABS; i++) {
            boolean popupTab = i < WorkConsoleMenu.PAGE_TABS && TAB_ORDER[i] == TAB_RECORD;
            boolean on = i == WorkConsoleMenu.PAGE_TABS || TAB_ORDER[i] == tab && !popupTab;
            if (on != connected) {
                continue;
            }
            int[] t = outerTab(i);
            boolean hover = Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3]);
            Panel.railTab(g, skin, t[0], t[1], on, hover && !on, topPos, imageHeight);
            int ix = t[0] + (on ? 3 : 2);
            int iy = t[1] + 3;
            if (i == WorkConsoleMenu.PAGE_TABS) {
                var permit = menu.getSlot(WorkConsoleMenu.SLOT_PERMIT);
                if (permit.isActive() && !permit.hasItem()) {
                    area(t[0] - leftPos, t[1] - topPos, t[2], t[3], List.of(Component.translatable("gui.singulo.android_station.permit")));
                }
                if (permit.isActive()) {
                    ghostSlot(g, leftPos + WorkConsoleMenu.PERMIT_X, topPos + WorkConsoleMenu.PERMIT_Y, permit,
                            new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("shield_permit"))));
                }
                continue;
            }
            pageIcon(g, TAB_ORDER[i], ix + 4, iy + 4, on || popupTab && recordPop.open ? skin.accent() : skin.text());
            area(t[0] - leftPos, t[1] - topPos, t[2], t[3], List.of(Component.translatable("gui.singulo.work_console.tab." + TAB_KEYS[TAB_ORDER[i]])));
        }
    }

    /** ページの記号（8×8 ほど）: 注文は箱、登録はくり返し、進行中は再生、つながりは人、経路は照準、記録はカード、納品箱は送り出し。 */
    private void pageIcon(GuiGraphics g, int page, int x, int y, int c) {
        switch (page) {
            case 0 -> Panel.icon(g, x, y, Panel.Icon.BOX, c);
            case TAB_STANDING -> Panel.autoIcon(g, x - 1, y - 1, c);
            case 1 -> Panel.statusGlyph(g, x, y, Panel.Status.RUN, c);
            case 2 -> Panel.icon(g, x, y, Panel.Icon.PERSON, c);
            case TAB_ROUTE -> Panel.icon(g, x, y, Panel.Icon.RANGE, c);
            case TAB_RECORD -> Panel.icon(g, x, y, Panel.Icon.INFO, c);
            default -> Panel.icon(g, x, y, Panel.Icon.OUT, c);
        }
    }

    /** マウスの下のタブの番号（なければ -1）。 */
    private int outerTabAt(double mouseX, double mouseY) {
        for (int i = 0; i <= WorkConsoleMenu.PAGE_TABS; i++) {
            int[] t = outerTab(i);
            if (Panel.inside(mouseX, mouseY, t[0], t[1], t[2], t[3])) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 高さ（一覧の行数）

    /** 行数 rows のときの画面の高さ。 */
    private static int heightFor(int rows) {
        return BASE_HEIGHT + rows * CELL;
    }

    /** 画面の高さを今の行数に合わせ、インベントリのスロットを下端へ動かす（init と行の増減のたび）。 */
    private void applyHeight() {
        imageHeight = heightFor(rows());
        topPos = (height - imageHeight) / 2;
        inventoryLabelY = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryLabelY(imageHeight);
        int iy = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryY(imageHeight);
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.inventory.Slot s = menu.getSlot(WorkConsoleMenu.OWN + i);
            s.y = i < 27 ? iy + (i / 9) * 18 : io.github.genichimaruo.singulo.machine.GuiFrame.hotbarY(imageHeight);
        }
        if (searchBox != null) {
            searchBox.setY(topPos + searchY() + 2);
        }
    }

    // ------------------------------------------------------------------ 窓

    @Override
    protected List<Popup> popups() {
        return stack;
    }

    /** 窓の開け閉めを様子に合わせ、大きさと位置を決め、レシピ登録のスロットを窓へ動かす（毎フレームと、クリックの前）。 */
    private void layoutPopups() {
        orderPop.open = picking != null;
        detailPop.open = detailOrder() != null;
        int dx = (imageWidth - POP_W) / 2;
        int bottom = io.github.genichimaruo.singulo.machine.GuiFrame.contentBottom(imageHeight);
        orderPop.layout(leftPos, topPos, POP_W, PICK_H, dx, (imageHeight - PICK_H) / 2, width, height);
        detailPop.layout(leftPos, topPos, POP_W, DETAIL_H_POP, dx, TOP - 4, width, height);
        recordPop.layout(leftPos, topPos, POP_W, RECORD_H, dx, bottom - RECORD_H, width, height);
        for (int i = WorkConsoleMenu.SLOT_GRID; i < WorkConsoleMenu.SLOT_OUTBOX; i++) {
            net.minecraft.world.inventory.Slot s = menu.getSlot(i);
            boolean moved = i == WorkConsoleMenu.SLOT_RESULT && procedure();      // 手順モードの作る物は、手順のボタンの下へ
            s.x = recordX() - leftPos + (moved ? WorkConsoleMenu.RESULT_PROC_X : recordSlotBase[i][0]);
            s.y = recordY() - topPos + (moved ? WorkConsoleMenu.RESULT_PROC_Y : recordSlotBase[i][1]);
        }
        if (countBox != null) {
            countBox.setX(pickX() + 28);
            countBox.setY(pickY() + 30);
        }
    }

    /** 窓の中身の原点（前の画面の座標をそのまま使えるように、窓の中へずらした位置）。 */
    private int pickX() {
        return orderPop.x - 1;
    }

    private int pickY() {
        return orderPop.bodyY() - 25;
    }

    private int detailX() {
        return detailPop.x - 1;
    }

    private int detailY() {
        return detailPop.bodyY() - 22;
    }

    /** レシピ登録の窓の中身の原点（窓の中身の左上。メニューの座標はここから）。 */
    private int recordX() {
        return recordPop.x;
    }

    private int recordY() {
        return recordPop.bodyY();
    }

    private boolean procedure() {
        return menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE;
    }

    /** マウスの下の、いちばん手前の窓（なければ null）。 */
    @Nullable
    private Popup popupAt(double mouseX, double mouseY) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).contains(mouseX, mouseY)) {
                return stack.get(i);
            }
        }
        return null;
    }

    private void toFront(Popup p) {
        stack.remove(p);
        stack.add(p);
    }

    /** レシピ登録の窓を開く・閉じる。 */
    private void setRecordOpen(boolean open) {
        recordPop.open = open;
        if (open) {
            toFront(recordPop);
        }
    }

    private static boolean isRecordSlot(int index) {
        return index >= WorkConsoleMenu.SLOT_GRID && index < WorkConsoleMenu.SLOT_OUTBOX;
    }

    /** 窓の上では、レシピ登録の窓が手前にあるときだけ、その窓のスロットが反応する。 */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        if (popupAt(mouseX, mouseY) == recordPop) {
            for (int i = WorkConsoleMenu.SLOT_GRID; i < WorkConsoleMenu.SLOT_OUTBOX; i++) {
                net.minecraft.world.inventory.Slot s = menu.getSlot(i);
                if (s.isActive() && s.x == x && s.y == y) {
                    return Panel.inside(mouseX, mouseY, leftPos + x - 1, topPos + y - 1, w + 2, h + 2);
                }
            }
            return false;
        }
        return super.isHovering(x, y, w, h, mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (Popup p : stack) {
            if (p.drag(leftPos, topPos, mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean dragged = false;
        for (Popup p : stack) {
            dragged |= p.release();
        }
        return dragged || super.mouseReleased(mouseX, mouseY, button);
    }

    /** 見た目の確認用: 窓の中や下の段の部品の、画面の座標（x, y, 幅, 高さ）。 */
    int[] freshButton() {
        return new int[]{pickX() + FRESH_X, pickY() + FRESH_Y, FRESH_W, FRESH_H};
    }

    int[] sortButton() {
        return new int[]{leftPos + SORT_X, topPos + searchY(), SORT_W, SEARCH_H};
    }

    /** 詳しい様子の窓の、左の列（工程）と右の列（アンドロイド）の中ほど（x, y, x, y）。 */
    int[] detailColumns() {
        return new int[]{detailX() + 20, detailY() + TOP + 20, detailX() + 200, detailY() + TOP + 20};
    }

    @Override
    protected void init() {
        imageHeight = heightFor(rows());
        super.init();
        String keep = searchBox != null ? searchBox.getValue() : jeiSync() ? java.util.Objects.requireNonNullElse(jeiText(), "") : "";
        searchBox = new EditBox(font, leftPos + SEARCH_X + 3, topPos + searchY() + 2, SEARCH_W - 6, SEARCH_H - 2,
                Component.translatable("gui.singulo.work_console.search.hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(0xFFECEEF0);
        searchBox.setHint(Component.translatable("gui.singulo.work_console.search.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        searchBox.setValue(keep);
        searchBox.setCanLoseFocus(true);
        searchBox.setFocused(false);
        searchBox.setResponder(text -> {
            scroll = 0;
            if (jeiSync()) {
                jeiSet(text);
            }
        });
        addRenderableWidget(searchBox);
        if (countBox != null) {
            addWidget(countBox);                       // 窓の中に描く（下の画面の部品と一緒には描かない）
        }
        applyHeight();
    }

    // ------------------------------------------------------------------ 検索と並べ替え

    /** 検索欄を出しているか（注文タブ）。 */
    private boolean searchShown() {
        return searchBox != null && tab == 0;
    }

    private static boolean jeiLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("jei");
    }

    private static boolean jeiSync() {
        return jeiLoaded() && io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_JEI_SYNC.get();
    }

    @Nullable
    private static String jeiText() {
        return jeiLoaded() ? io.github.genichimaruo.singulo.compat.jei.JeiSearch.get() : null;
    }

    private static void jeiSet(String text) {
        if (jeiLoaded()) {
            io.github.genichimaruo.singulo.compat.jei.JeiSearch.set(text);
        }
    }

    private static String sortMode() {
        return io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_SORT.get();
    }

    private static boolean sortReversed() {
        return io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_SORT_REVERSED.get();
    }

    private static <T> void saveConfig(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T v) {
        value.set(v);
        value.save();
    }

    /**
     * 注文タブに並べる物: 検索欄の文字で絞り込み、選んだ並びに並べ替える。
     * 文字は空白で区切り、すべてに合う物だけ残す。@ で始まる語は mod（ID・名前）、ほかは物の名前か ID で合わせる。
     */
    private List<CompoundTag> visibleEntries() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        Object key = java.util.List.of(menu.viewVersion(), query, sortMode(), sortReversed());
        if (key.equals(visibleKey)) {
            return visible;
        }
        String[] words = query.isEmpty() ? new String[0] : query.split("\\s+");
        List<CompoundTag> out = new ArrayList<>();
        java.util.Map<CompoundTag, ItemStack> stacks = new java.util.IdentityHashMap<>();
        for (CompoundTag e : list("entries")) {
            ItemStack item = stack(e, "item");
            if (matches(item, words)) {
                out.add(e);
                stacks.put(e, item);
            }
        }
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag(
                minecraft.getLanguageManager().getSelected().replace('_', '-')));
        java.util.Comparator<CompoundTag> order = switch (sortMode()) {
            case "name" -> java.util.Comparator.comparing(e -> stacks.get(e).getHoverName().getString(), collator);
            case "count" -> java.util.Comparator.comparingLong((CompoundTag e) -> e.getLong("count")).reversed();
            case "mod" -> java.util.Comparator.comparing(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stacks.get(e).getItem()).getNamespace());
            case "craftable" -> java.util.Comparator.comparing((CompoundTag e) -> !e.getBoolean("craft"));
            default -> null;                            // 指令台の順のまま
        };
        if (order != null) {
            out.sort(order);                            // 同じ順位の物は、指令台の順のまま
        }
        if (sortReversed()) {
            java.util.Collections.reverse(out);
        }
        visible = out;
        visibleKey = key;
        return out;
    }

    private static boolean matches(ItemStack item, String[] words) {
        if (words.length == 0) {
            return true;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
        String name = item.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
        for (String w : words) {
            if (w.startsWith("@")) {
                String mod = w.substring(1);
                String modName = net.neoforged.fml.ModList.get().getModContainerById(id.getNamespace())
                        .map(c -> c.getModInfo().getDisplayName()).orElse(id.getNamespace()).toLowerCase(java.util.Locale.ROOT);
                if (!id.getNamespace().contains(mod) && !modName.contains(mod)) {
                    return false;
                }
            } else if (!name.contains(w) && !id.getPath().contains(w)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 様子

    private HolderLookup.Provider reg() {
        return minecraft.level.registryAccess();
    }

    private List<CompoundTag> list(String key) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag v = menu.view();
        if (v != null) {
            for (Tag t : v.getList(key, Tag.TAG_COMPOUND)) {
                out.add((CompoundTag) t);
            }
        }
        return out;
    }

    private ItemStack stack(CompoundTag t, String key) {
        return ItemStack.parseOptional(reg(), t.getCompound(key));
    }

    private Component text(CompoundTag t, String key) {
        Component c = t.contains(key) ? Component.Serializer.fromJson(t.getString(key), reg()) : null;
        return c == null ? Component.empty() : c;
    }

    private void send(CompoundTag action) {
        PacketDistributor.sendToServer(new WorkConsoleActionPayload(menu.containerId, action));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.viewVersion() != seenVersion) {
            seenVersion = menu.viewVersion();
            CompoundTag v = menu.view();
            if (v != null && v.contains("plan")) {
                CompoundTag p = v.getCompound("plan");
                if (p.contains("started")) {
                    if (p.getBoolean("started")) {
                        closePicking();
                        tab = registering ? TAB_STANDING : 1;
                    } else {
                        error = text(p, "error");
                    }
                } else if (picking != null) {
                    plan = p;
                }
            }
        }
        if (planDelay > 0 && --planDelay == 0 && picking != null) {
            send(WorkConsoleActionPayload.plan(picking, count, freshOrder(), reg()));
        }
        menu.setVisibility(recordPop.open, tab == TAB_OUTBOX, tab == TAB_ROUTE);
        if (searchBox != null) {
            boolean shown = searchShown();
            searchBox.visible = shown;
            searchBox.active = shown;
            if (!shown && searchBox.isFocused()) {
                searchBox.setFocused(false);
                setFocused(null);
            }
            // JEI と連携している間: JEI の検索欄で打った文字も、こちらへ写す（こちらで打っている間はそのまま）
            String jei = jeiSync() && !searchBox.isFocused() ? jeiText() : null;
            if (jei != null && !jei.equals(searchBox.getValue())) {
                searchBox.setValue(jei);
            }
        }
    }

    /** レシピ登録の窓を開く（JEI の「+」で見本を置いたとき）。 */
    public void showRecordTab() {
        setRecordOpen(true);
        menu.setVisibility(true, tab == TAB_OUTBOX, tab == TAB_ROUTE);
    }

    /** 数を縮めて書く: 9999 まではそのまま、10000 からは 10K、10000K からは 10M、そのあと G・T・P・E。 */
    static String compact(long n) {
        String[] units = {"", "K", "M", "G", "T", "P", "E"};
        int u = 0;
        while (n >= 10000 && u < units.length - 1) {
            n /= 1000;
            u++;
        }
        return n + units[u];
    }

    /** スロットの右下に、数を小さな字で書く（5桁まで収まる大きさ）。 */
    private void drawCount(GuiGraphics g, int x, int y, String text) {
        if (text.isEmpty()) {
            return;
        }
        float k = Math.min(0.6F, 17F / font.width(text));
        g.pose().pushPose();
        g.pose().translate(x + 17 - font.width(text) * k, y + 17 - font.lineHeight * k, 300);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ 注文の確認

    /** 「新規」で注文するか（今すぐの注文のときだけ効く）。 */
    private boolean freshOrder() {
        return fresh && trigger < 0;
    }

    private void openPicking(ItemStack item) {
        openPicking(item, 1);
    }

    /** 注文の確認を開く（数は n から）。 */
    private void openPicking(ItemStack item, long n) {
        picking = item.copyWithCount(1);
        count = Math.max(1, Math.min(WorkConsoleBlockEntity.MAX_COUNT, n));
        fresh = false;
        plan = null;
        error = null;
        listScroll = 0;
        androidScroll = 0;
        chosen.clear();
        destination = 0;
        trigger = -1;
        countBox = new EditBox(font, pickX() + 28, pickY() + 30, 44, 10, Component.empty());
        countBox.setBordered(false);                   // 地は凹みの部品で描く（検索欄と同じ）
        countBox.setTextColor(0xFFECEEF0);
        countBox.setMaxLength(4);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(c -> c >= '0' && c <= '9'));
        countBox.setValue(String.valueOf(count));
        countBox.setResponder(s -> {
            count = s.isEmpty() ? 1 : Math.max(1, Math.min(WorkConsoleBlockEntity.MAX_COUNT, Long.parseLong(s)));
            planDelay = 6;
        });
        addWidget(countBox);                           // 窓の中に描く
        orderPop.open = true;
        toFront(orderPop);
        send(WorkConsoleActionPayload.plan(picking, count, freshOrder(), reg()));
    }

    private void closePicking() {
        picking = null;
        plan = null;
        error = null;
        if (countBox != null) {
            removeWidget(countBox);
            countBox = null;
        }
    }

    private void addCount(long d) {
        count = Math.max(1, Math.min(WorkConsoleBlockEntity.MAX_COUNT, count + d));
        if (countBox != null) {
            countBox.setValue(String.valueOf(count));
        }
        planDelay = 4;
    }

    // ------------------------------------------------------------------ 入力

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picking != null && keyCode == 256) {        // Esc: 確認をやめる（画面は閉じない）
            closePicking();
            return true;
        }
        if (searchShown() && searchBox.isFocused()) {
            if (keyCode == 256) {                       // Esc: 打つのをやめる（画面は閉じない）
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;                                // 打っている間は、E などで画面を閉じない
        }
        if (countBox != null && countBox.isFocused() && countBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (countBox != null && countBox.isFocused() && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        layoutPopups();
        Popup top = popupAt(mouseX, mouseY);
        int step = dy > 0 ? -1 : 1;
        if (top == recordPop) {
            if (optionShown() && Panel.inside(mouseX, mouseY, recordX() + WorkConsoleMenu.OPTION_X, recordY() + WorkConsoleMenu.OPTION_Y,
                    WorkConsoleMenu.OPTION_W, WorkConsoleMenu.OPTION_H)) {
                adjustOption(dy > 0, hasShiftDown());
                return true;
            }
            // 加工モードの見本: ホイールで数を変える（シフトで10ずつ）
            adjustHovered(dy > 0, hasShiftDown());
            return true;
        }
        if (top == detailPop) {
            CompoundTag detailed = detailOrder();
            if (detailed != null && mouseX < detailX() + 128) {
                detailScroll = Math.max(0, Math.min(detailScroll + step, Math.max(0, tags(detailed, "steps").size() - (DETAIL_ROWS - 1))));
            } else if (detailed != null) {
                workerScroll = Math.max(0, Math.min(workerScroll + step, Math.max(0, tags(detailed, "worker_list").size() - DETAIL_ROWS)));
            }
            return true;
        }
        if (top == orderPop) {
            if (mouseX < pickX() + 128) {
                listScroll = Math.max(0, listScroll + step);
            } else {
                androidScroll = Math.max(0, androidScroll + step);
            }
            return true;
        }
        scroll = Math.max(0, scroll + step);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layoutPopups();
        Popup top = popupAt(mouseX, mouseY);
        if (top != null) {
            toFront(top);
            if (top.clickChrome(mouseX, mouseY)) {
                if (!orderPop.open && picking != null) {
                    closePicking();                    // ×で閉じた
                }
                if (!detailPop.open) {
                    detail = -1;
                }
                return true;
            }
            if (top == orderPop) {
                return clickPicking(mouseX, mouseY, button);
            }
            if (top == recordPop) {
                return clickRecord(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
            }
            return true;                               // 詳しい様子の窓: 見るだけ
        }
        int x = leftPos, y = topPos;
        int outer = outerTabAt(mouseX, mouseY);
        if (outer >= 0 && outer < WorkConsoleMenu.PAGE_TABS) {
            if (TAB_ORDER[outer] == TAB_RECORD) {
                setRecordOpen(!recordPop.open);        // レシピ登録は窓: 開け閉めする
                menu.setVisibility(recordPop.open, tab == TAB_OUTBOX, tab == TAB_ROUTE);
            } else {
                tab = TAB_ORDER[outer];
                scroll = 0;
            }
            return true;
        }
        if (searchShown()) {
            boolean inSearch = Panel.inside(mouseX, mouseY, x + SEARCH_X, y + searchY(), SEARCH_W, SEARCH_H);
            searchBox.setFocused(inSearch);
            setFocused(inSearch ? searchBox : null);
            if (inSearch) {
                if (button == 1) {
                    searchBox.setValue("");               // 右クリック: 消す
                } else {
                    searchBox.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + SORT_X, y + searchY(), SORT_W, SEARCH_H)) {
                int i = java.util.Arrays.asList(SORTS).indexOf(sortMode());
                int next = Math.floorMod(i + (Screen.hasShiftDown() || button == 1 ? -1 : 1), SORTS.length);
                saveConfig(io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_SORT, SORTS[next]);
                scroll = 0;
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + REV_X, y + searchY(), REV_W, SEARCH_H)) {
                saveConfig(io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_SORT_REVERSED, !sortReversed());
                scroll = 0;
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + ROWS_LESS_X, y + searchY(), ROWS_BTN_W, SEARCH_H)) {
                setRows(rows() - 1);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + ROWS_MORE_X, y + searchY(), ROWS_BTN_W, SEARCH_H)) {
                setRows(rows() + 1);
                return true;
            }
            if (jeiLoaded() && Panel.inside(mouseX, mouseY, x + JEI_X, y + searchY(), JEI_W, SEARCH_H)) {
                boolean on = !jeiSync();
                saveConfig(io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_JEI_SYNC, on);
                if (on) {
                    // 連携を始める: こちらが空なら JEI の文字を写し、文字があれば JEI へ送る
                    String jei = jeiText();
                    if (searchBox.getValue().isEmpty() && jei != null) {
                        searchBox.setValue(jei);
                    } else {
                        jeiSet(searchBox.getValue());
                    }
                }
                return true;
            }
        }
        if (tab == 0) {
            List<CompoundTag> entries = visibleEntries();
            for (int i = 0; i < COLS * rows(); i++) {
                int idx = i + scroll * COLS;
                int cx = x + GRID_LEFT + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
                if (idx < entries.size() && Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                    openPicking(stack(entries.get(idx), "item"));
                    return true;
                }
            }
        } else if (tab == 1) {
            List<CompoundTag> orders = list("orders");
            for (int i = 0; i < orderRows(); i++) {
                int idx = i + scroll;
                int ry = y + TOP + i * ROW_H;
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + CANCEL_X, ry + 3, ROW_BTN, ROW_BTN)) {
                    send(WorkConsoleActionPayload.cancel(orders.get(idx).getInt("id")));
                    return true;
                }
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + AGAIN_X, ry + 3, ROW_BTN, ROW_BTN)) {
                    // 同じ物を、もう一度注文する（数はこの注文と同じから。確認の窓で変えられる）
                    CompoundTag o = orders.get(idx);
                    openPicking(stack(o, "target"), o.getLong("count"));
                    return true;
                }
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 8, ry, AGAIN_X - 10, ROW_H - 1)) {
                    detail = orders.get(idx).getInt("id");     // 行をクリック: 詳しい様子の窓を開く
                    detailScroll = workerScroll = 0;
                    detailPop.open = true;
                    toFront(detailPop);
                    return true;
                }
            }
        } else if (tab == TAB_STANDING) {
            List<CompoundTag> rows = list("standing");
            for (int i = 0; i < orderRows(); i++) {
                int idx = i + scroll;
                if (idx < rows.size() && Panel.inside(mouseX, mouseY, x + CANCEL_X, y + TOP + i * ROW_H + 3, ROW_BTN, ROW_BTN)) {
                    send(WorkConsoleActionPayload.unregister(rows.get(idx).getInt("id")));
                    return true;
                }
            }
        } else if (tab == 2) {
            List<CompoundTag> links = list("links");
            for (int i = 0; i < linkRows(); i++) {
                int idx = i + scroll;
                if (idx < links.size() && Panel.inside(mouseX, mouseY, x + CANCEL_X, y + TOP + i * LINK_H, ROW_BTN, ROW_BTN)) {
                    send(WorkConsoleActionPayload.unlink(BlockPos.of(links.get(idx).getLong("pos"))));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 注文の確認の窓の中のクリック（窓の中は下の画面に届かない）。 */
    private boolean clickPicking(double mouseX, double mouseY, int button) {
        int x = pickX(), y = pickY();
        if (countBox != null && countBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(countBox);
            return true;
        }
        long step = Screen.hasShiftDown() ? 10 : Screen.hasControlDown() ? 64 : 1;
        if (Panel.inside(mouseX, mouseY, x + 8, y + 27, 14, 14)) {
            addCount(-step);
            return true;
        }
        if (Panel.inside(mouseX, mouseY, x + 76, y + 27, 14, 14)) {
            addCount(step);
            return true;
        }
        List<CompoundTag> androids = list("androids");
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + androidScroll;
            if (idx < androids.size() && Panel.inside(mouseX, mouseY, x + 128, y + LIST_Y + i * LIST_H, 112, LIST_H)) {
                CompoundTag a = androids.get(idx);
                UUID id = a.getUUID("id");
                if (selectable(a) && !chosen.remove(id)) {
                    chosen.add(id);
                }
                return true;
            }
        }
        if (trigger < 0 && Panel.inside(mouseX, mouseY, x + FRESH_X, y + FRESH_Y, FRESH_W, FRESH_H)) {
            fresh = !fresh;                        // 新規: 計算し直す
            plan = null;
            error = null;
            planDelay = 1;
            return true;
        }
        List<CompoundTag> deliveries = list("deliveries");
        if (Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H) && !deliveries.isEmpty()) {
            destination = (destination + (button == 1 ? deliveries.size() - 1 : 1)) % deliveries.size();
            return true;
        }
        if (Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H)) {
            // 今すぐ → 在庫を保つ → 信号で1回 → 信号の間だけ在庫を保つ（右クリックで逆順）
            int n = io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGERS + 1;
            boolean wasFresh = freshOrder();
            trigger = Math.floorMod(trigger + 1 + (button == 1 ? -1 : 1), n) - 1;
            error = null;
            if (wasFresh != freshOrder()) {
                plan = null;
                planDelay = 1;                     // 「新規」が効く・効かないが変わった: 計算し直す
            }
            return true;
        }
        if (Panel.inside(mouseX, mouseY, x + START_X, y + BUTTON_Y, START_W, BUTTON_H) && canStart()) {
            error = null;
            BlockPos dest = deliveries.isEmpty() ? menu.pos()
                    : BlockPos.of(deliveries.get(Math.min(destination, deliveries.size() - 1)).getLong("pos"));
            registering = trigger >= 0;
            if (registering) {
                List<UUID> picked = new ArrayList<>(chosen);
                send(WorkConsoleActionPayload.register(picking, count, trigger, picked, dest, reg()));
            } else {
                send(WorkConsoleActionPayload.start(picking, count, startAndroids(), autoAndroids(), dest, freshOrder(), reg()));
            }
            return true;
        }
        return true;
    }

    /** レシピ登録の窓の中のボタン。扱ったら true（スロットのクリックは呼び出し側で）。 */
    private boolean clickRecord(double mouseX, double mouseY, int button) {
        if (minecraft.gameMode == null) {
            return false;
        }
        int x = recordX(), y = recordY();
        if (button == 2 && adjustHovered(!hasShiftDown(), false)) {
            return true;                   // ミドルクリックで1つ増やす（シフトで減らす）
        }
        if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.MODE_X, y + WorkConsoleMenu.MODE_Y, WorkConsoleMenu.BUTTON_W,
                WorkConsoleMenu.BUTTON_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_MODE);
            return true;
        }
        int variantSlot = variantMarkAt(mouseX, mouseY);
        if (variantSlot >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_VARIANT_SLOT + variantSlot);
            return true;
        }
        if (menu.mode() == WorkConsoleBlockEntity.MODE_CRAFTING && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.VARIANTS_X,
                y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_VARIANTS);
            return true;
        }
        if (fuelModeHovered(mouseX, mouseY)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_FUEL_FILL);
            return true;
        }
        int face = hoveredFace(mouseX, mouseY);
        if (face >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    (button == 1 ? WorkConsoleMenu.BUTTON_FACE_BACK : WorkConsoleMenu.BUTTON_FACE) + face);
            return true;
        }
        if (menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE) {
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.KIND_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.KIND_W,
                    WorkConsoleMenu.SMALL_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        button == 1 ? WorkConsoleMenu.BUTTON_KIND_BACK : WorkConsoleMenu.BUTTON_KIND);
                return true;
            }
            if (optionShown() && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.OPTION_X, y + WorkConsoleMenu.OPTION_Y,
                    WorkConsoleMenu.OPTION_W, WorkConsoleMenu.OPTION_H)) {
                adjustOption(button != 1, hasShiftDown());
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ADD_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.ADD_W,
                    WorkConsoleMenu.SMALL_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_ADD);
                return true;
            }
            int step = hoveredStep(mouseX, mouseY);
            if (step >= 0 && mouseX >= x + WorkConsoleMenu.STEPS_X + WorkConsoleMenu.STEPS_W - 9) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_REMOVE_STEP + step);
                return true;
            }
        }
        int[][] buttons = {{WorkConsoleMenu.CLEAR_CARD_X, WorkConsoleMenu.CLEAR_CARD_Y, WorkConsoleMenu.BUTTON_CLEAR_CARD, WorkConsoleMenu.ICON_W},
                {WorkConsoleMenu.ENCODE_X, WorkConsoleMenu.ENCODE_Y, WorkConsoleMenu.BUTTON_ENCODE, WorkConsoleMenu.BUTTON_W},
                {WorkConsoleMenu.CLEAR_X, WorkConsoleMenu.CLEAR_Y, WorkConsoleMenu.BUTTON_CLEAR, WorkConsoleMenu.BUTTON_W}};
        for (int[] b : buttons) {
            if (Panel.inside(mouseX, mouseY, x + b[0], y + b[1], b[3], WorkConsoleMenu.BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b[2]);
                return true;
            }
        }
        return false;
    }

    private boolean canStart() {
        if (trigger >= 0) {
            return picking != null;                // 登録は、今そろっていなくてもできる（そろったら始める）
        }
        return plan != null && plan.getBoolean("ok") && plan.getLong("count") == count
                && picking != null && ItemStack.isSameItemSameComponents(stack(plan, "target"), picking)
                && !startAndroids().isEmpty() && planDelay == 0;
    }

    /** 選べるアンドロイドか（作業中でなく、採掘の工程があれば採掘アップグレードを積んでいる）。 */
    private boolean selectable(CompoundTag a) {
        return !a.getBoolean("busy") && (plan == null || !plan.getBoolean("mining") || a.getBoolean("miner"))
                && (plan == null || !plan.getBoolean("felling") || a.getBoolean("feller"))
                && (plan == null || !plan.getBoolean("fluids") || a.getBoolean("tank"))
                && (plan == null || !plan.getBoolean("chemicals") || a.getBoolean("chem_tank"));
    }

    /** だれも選んでいない（選べる候補をみな送り、指令台が作業量に見合う人数だけを招集する）。 */
    private boolean autoAndroids() {
        return chosen.stream().noneMatch(id -> list("androids").stream().anyMatch(a -> a.getUUID("id").equals(id) && selectable(a)));
    }

    /** 作業を始めるアンドロイド: 選んだうちの選べるもの。だれも選んでいなければ、選べるもの全部。 */
    private List<UUID> startAndroids() {
        List<UUID> picked = new ArrayList<>(), all = new ArrayList<>();
        for (CompoundTag a : list("androids")) {
            if (selectable(a)) {
                all.add(a.getUUID("id"));
                if (chosen.contains(a.getUUID("id"))) {
                    picked.add(a.getUUID("id"));
                }
            }
        }
        return picked.isEmpty() ? all : picked;
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layoutPopups();
        tips.clear();
        super.render(g, mouseX, mouseY, partialTick);
        // 窓は下の画面（スロットの物の数は z 300 あたり）より手前に、後ろに積んだものほど手前に描く
        int z = POPUP_Z;
        for (Popup p : List.copyOf(stack)) {
            if (!p.open) {
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, z);
            p.draw(g, skin, mouseX, mouseY);
            if (p == orderPop && picking != null) {
                renderPickingPopup(g, mouseX, mouseY, partialTick);
            } else if (p == detailPop) {
                renderDetailPopup(g, mouseX, mouseY);
            } else if (p == recordPop) {
                renderRecordPopup(g, mouseX, mouseY);
            }
            g.pose().popPose();
            z += 300;
        }
        Popup top = popupAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && z > POPUP_Z) {
            g.renderItem(carried, mouseX - 8, mouseY - 8);          // 持っている物は、窓の上でも手前に
            g.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }
        if (top == orderPop) {
            if (!showTip(g, orderPop, mouseX, mouseY)) {
                renderPickingTooltips(g, mouseX, mouseY);
            }
        } else if (top == detailPop) {
            showTip(g, detailPop, mouseX, mouseY);
        } else if (top == recordPop) {
            renderTooltip(g, mouseX, mouseY);
            if (hoveredSlot == null || !hoveredSlot.hasItem() || variantMarkAt(mouseX, mouseY) >= 0) {
                renderRecordTooltips(g, mouseX, mouseY);   // アイテムの説明が出ている間は、ほかの説明を重ねない
            }
        } else if (top == null) {
            renderTooltip(g, mouseX, mouseY);
            if ((hoveredSlot == null || !hoveredSlot.hasItem()) && !showTip(g, null, mouseX, mouseY)) {
                renderMainTooltips(g, mouseX, mouseY);
            }
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ 長い文字とアイコンの説明

    /** マウスを重ねたときに出す説明の場所（毎フレーム作り直す）。owner: 窓の中なら窓、下の画面なら null。 */
    private record Tip(@Nullable Popup owner, int x, int y, int w, int h, List<Component> lines) {}

    private final List<Tip> tips = new ArrayList<>();

    /** 幅 w に収まらなければ、字を縮めずに「…」で切って書く。切ったら true。 */
    private boolean clipped(GuiGraphics g, Component text, int x, int y, int w, int color) {
        if (font.width(text) <= w) {
            g.drawString(font, text, x, y, color, false);
            return false;
        }
        net.minecraft.network.chat.FormattedText cut = font.substrByWidth(text, w - font.width("…"));
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cut, Component.literal("…"))), x, y, color, false);
        return true;
    }

    /** clipped と同じで、切ったときは全文をマウスを重ねたときに出す。ox, oy: 今の座標の原点（画面の座標）。 */
    private void clip(GuiGraphics g, @Nullable Popup owner, Component text, int x, int y, int w, int color, int ox, int oy) {
        if (clipped(g, text, x, y, w, color)) {
            tips.add(new Tip(owner, ox + x, oy + y - 1, w, 10, List.of(text)));
        }
    }

    /** 文字の代わりのアイコン。説明はマウスを重ねたときに出す。 */
    private void iconTip(GuiGraphics g, @Nullable Popup owner, Panel.Icon icon, int x, int y, int color, int ox, int oy, Component... lines) {
        Panel.icon(g, x, y, icon, color);
        tips.add(new Tip(owner, ox + x - 1, oy + y - 1, 10, 10, List.of(lines)));
    }

    /** マウスの下の説明を出す（出したら true）。 */
    private boolean showTip(GuiGraphics g, @Nullable Popup owner, int mouseX, int mouseY) {
        for (int i = tips.size() - 1; i >= 0; i--) {
            Tip t = tips.get(i);
            if (t.owner() == owner && Panel.inside(mouseX, mouseY, t.x(), t.y(), t.w(), t.h())) {
                Panel.tooltip(g, font, t.lines(), 200, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    /**
     * 今の並びのアイコン（8×7）: 指令台の順は同じ長さの3本線、名前は「A」、数は高さの違う棒、mod は箱、作れる物は光る印。
     */
    private static void sortIcon(GuiGraphics g, String mode, int x, int y, int c, int accent) {
        switch (mode) {
            case "name" -> {
                g.fill(x + 2, y, x + 5, y + 1, c);                  // A
                g.fill(x + 1, y + 1, x + 2, y + 7, c);
                g.fill(x + 5, y + 1, x + 6, y + 7, c);
                g.fill(x + 2, y + 4, x + 5, y + 5, c);
                g.fill(x + 7, y + 6, x + 8, y + 7, accent);         // 並びの印
            }
            case "count" -> {
                g.fill(x, y + 1, x + 2, y + 7, c);                  // 多い順の棒
                g.fill(x + 3, y + 3, x + 5, y + 7, c);
                g.fill(x + 6, y + 5, x + 8, y + 7, c);
            }
            case "mod" -> Panel.icon(g, x, y, Panel.Icon.BOX, c);
            case "craftable" -> {
                g.fill(x + 1, y + 1, x + 7, y + 7, Panel.SEAM);     // 一覧の作れる印（左上の水色）と同じ形
                g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF78D2F0);
            }
            default -> {
                g.fill(x, y, x + 8, y + 1, c);                      // 指令台の順
                g.fill(x, y + 3, x + 8, y + 4, c);
                g.fill(x, y + 6, x + 8, y + 7, c);
            }
        }
    }

    /** ごみ箱の記号（7×8）。 */
    private static void trashIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y, x + 5, y + 1, c);
        g.fill(x, y + 1, x + 7, y + 2, c);
        g.fill(x + 1, y + 3, x + 6, y + 4, c);
        g.fill(x + 1, y + 3, x + 2, y + 8, c);
        g.fill(x + 5, y + 3, x + 6, y + 8, c);
        g.fill(x + 3, y + 4, x + 4, y + 7, c);
        g.fill(x + 1, y + 7, x + 6, y + 8, c);
    }

    /** 進行中の注文の2行目（様子か、足りない物）。 */
    private Component orderState(CompoundTag o) {
        List<CompoundTag> missing = tags(o, "missing");
        if (missing.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.status." + o.getInt("status"), o.getInt("workers"));
        }
        // 足りない物（1つめと、ほかの種類の数）
        CompoundTag m = missing.get(0);
        return Component.translatable(missing.size() > 1 ? "gui.singulo.work_console.missing.more" : "gui.singulo.work_console.missing.one",
                stack(m, "item").getHoverName(), compact(m.getLong("count")), missing.size() - 1);
    }

    /** 登録の行の1行目と2行目（きっかけはアクセントの色）。 */
    private Component[] standingLines(CompoundTag s) {
        int trig = s.getInt("trigger");
        Component head = stack(s, "item").getHoverName().copy().append(" ×" + compact(s.getLong("count")));
        int state = s.getInt("state");
        Component line = state == io.github.genichimaruo.singulo.android.work.StandingOrder.WAITING && s.contains("reason")
                ? Component.translatable("gui.singulo.work_console.standing.waiting", text(s, "reason"))
                : Component.translatable("gui.singulo.work_console.standing.state." + state);
        if (trig != io.github.genichimaruo.singulo.android.work.StandingOrder.PULSE) {
            line = line.copy().append(Component.translatable("gui.singulo.work_console.standing.stock", compact(s.getLong("stock"))));
        }
        Component trigger = Component.translatable("gui.singulo.work_console.trigger."
                + io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trig]).withColor(skin.accent() & 0xFFFFFF);
        return new Component[]{head, Component.empty().append(trigger).append(" ").append(line)};
    }

    /** つながりの行の文字（種類と座標）。 */
    private static Component linkLine(CompoundTag l) {
        BlockPos p = BlockPos.of(l.getLong("pos"));
        int kind = l.getInt("kind");
        Component name = Component.translatable(kind < 0 ? "gui.singulo.work_console.link.lost" : "gui.singulo.work_console.link." + kind);
        return name.copy().append("  " + p.getX() + ", " + p.getY() + ", " + p.getZ());
    }

    /** 窓の上の帯の左に、名前（と物の小さな絵）を書く（帯の中ほどのつまみの印にはかからない幅で）。 */
    private void popupTitle(GuiGraphics g, Popup p, Component name, @Nullable ItemStack icon) {
        int tx = p.x + 4;
        if (icon != null && !icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(tx, p.y + 3, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            tx += 11;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        AndroidStationScreen.fit(g, font, name, tx, p.y + 3, p.x + p.w / 2 - 12 - tx, skin.text());
        g.pose().popPose();
    }

    /** レシピ登録の窓の中身: スロットの地と物、見本の囲み・ボタン、文字。 */
    private void renderRecordPopup(GuiGraphics g, int mouseX, int mouseY) {
        int x = recordX(), y = recordY();
        popupTitle(g, recordPop, Component.translatable("gui.singulo.work_console.tab.record"), null);
        for (int i = WorkConsoleMenu.SLOT_GRID; i < WorkConsoleMenu.SLOT_OUTBOX; i++) {
            net.minecraft.world.inventory.Slot s = menu.getSlot(i);
            if (!s.isActive()) {
                continue;
            }
            // カードのストック・記録したカード・燃料の欄は、入れる物のグレーの見本（文字の見出しの代わり）
            ItemStack ghost = i == WorkConsoleMenu.SLOT_STOCK || i == WorkConsoleMenu.SLOT_CARD
                    ? new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.RECIPE_CARD.get())
                    : i == WorkConsoleMenu.SLOT_FUEL ? new ItemStack(Items.COAL) : ItemStack.EMPTY;
            ghostSlot(g, leftPos + s.x, topPos + s.y, s, ghost);
        }
        renderRecord(g, x, y, mouseX, mouseY);
        for (int i = WorkConsoleMenu.SLOT_GRID; i < WorkConsoleMenu.SLOT_OUTBOX; i++) {
            net.minecraft.world.inventory.Slot s = menu.getSlot(i);
            if (s.isActive() && s.hasItem()) {
                g.renderItem(s.getItem(), leftPos + s.x, topPos + s.y);
                g.renderItemDecorations(font, s.getItem(), leftPos + s.x, topPos + s.y);
            }
        }
        if (hoveredSlot != null && isRecordSlot(hoveredSlot.index) && popupAt(mouseX, mouseY) == recordPop) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(leftPos + hoveredSlot.x, topPos + hoveredSlot.y, leftPos + hoveredSlot.x + 16, topPos + hoveredSlot.y + 16, 0x80FFFFFF);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        renderRecordLabels(g, mouseX, mouseY);
        g.pose().popPose();
    }

    /** 注文の確認の窓の中身。 */
    private void renderPickingPopup(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = pickX(), y = pickY();
        popupTitle(g, orderPop, picking.getHoverName(), picking);
        renderPicking(g, x, y, mouseX, mouseY);
        if (countBox != null) {
            countBox.render(g, mouseX, mouseY, partialTick);
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        renderPickingLabels(g);
        g.pose().popPose();
    }

    /** 注文の詳しい様子の窓の中身。 */
    private void renderDetailPopup(GuiGraphics g, int mouseX, int mouseY) {
        CompoundTag o = detailOrder();
        if (o == null) {
            return;
        }
        ItemStack target = stack(o, "target");
        popupTitle(g, detailPop, target.getHoverName().copy().append(" ×" + compact(o.getLong("count"))), target);
        renderDetail(g, detailX(), detailY(), mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(detailX(), detailY(), 0);
        renderDetailLabels(g, o);
        g.pose().popPose();
    }

    /** 窓の外（下の画面）の説明。 */
    private void renderMainTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        int x = leftPos, y = topPos;
        if (tab == TAB_ROUTE && hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index >= WorkConsoleMenu.SLOT_ROUTE
                && hoveredSlot.index < WorkConsoleMenu.OWN) {
            int col = (hoveredSlot.index - WorkConsoleMenu.SLOT_ROUTE) % io.github.genichimaruo.singulo.android.work.PathRule.COLS;
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.route." + ROUTE_COL_KEYS[col] + ".hint"), 180), mouseX, mouseY);
        }
        if (searchShown()) {
            Component help = null;
            if (Panel.inside(mouseX, mouseY, x + SEARCH_X, y + searchY(), SEARCH_W, SEARCH_H) && !searchBox.isFocused()) {
                help = Component.translatable("gui.singulo.work_console.search.help");
            } else if (Panel.inside(mouseX, mouseY, x + SORT_X, y + searchY(), SORT_W, SEARCH_H)) {
                help = Component.translatable("gui.singulo.work_console.sort", Component.translatable("gui.singulo.work_console.sort." + sortMode()))
                        .append("\n").append(Component.translatable("gui.singulo.work_console.sort.help"));
            } else if (Panel.inside(mouseX, mouseY, x + REV_X, y + searchY(), REV_W, SEARCH_H)) {
                help = Component.translatable("gui.singulo.work_console.sort.reverse");
            } else if (jeiLoaded() && Panel.inside(mouseX, mouseY, x + JEI_X, y + searchY(), JEI_W, SEARCH_H)) {
                help = Component.translatable(jeiSync() ? "gui.singulo.work_console.jei.on" : "gui.singulo.work_console.jei.off");
            } else if (Panel.inside(mouseX, mouseY, x + ROWS_LESS_X, y + searchY(), ROWS_BTN_W, SEARCH_H)) {
                help = Component.translatable("gui.singulo.select.rows_less");
            } else if (Panel.inside(mouseX, mouseY, x + ROWS_MORE_X, y + searchY(), ROWS_BTN_W, SEARCH_H)) {
                help = Component.translatable("gui.singulo.select.rows_more");
            }
            if (help != null) {
                g.renderTooltip(font, font.split(help, 200), mouseX, mouseY);
            }
        }
        if (tab == 0) {
            List<CompoundTag> entries = visibleEntries();
            for (int i = 0; i < COLS * rows(); i++) {
                int idx = i + scroll * COLS;
                int cx = x + GRID_LEFT + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
                if (idx < entries.size() && Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                    CompoundTag e = entries.get(idx);
                    ItemStack s = stack(e, "item");
                    List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(s));
                    lines.add(Component.translatable("gui.singulo.work_console.stock", e.getLong("count")).withStyle(net.minecraft.ChatFormatting.GRAY));
                    if (e.getBoolean("craft")) {
                        lines.add(Component.translatable("gui.singulo.work_console.craftable").withStyle(net.minecraft.ChatFormatting.AQUA));
                    }
                    if (e.getLong("making") > 0) {
                        lines.add(Component.translatable("gui.singulo.work_console.making", e.getLong("making")).withStyle(net.minecraft.ChatFormatting.GOLD));
                    }
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        } else if (tab == 1) {
            List<CompoundTag> orders = list("orders");
            for (int i = 0; i < orderRows(); i++) {
                int idx = i + scroll;
                int ry = y + TOP + i * ROW_H;
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + CANCEL_X, ry + 3, ROW_BTN, ROW_BTN)) {
                    g.renderTooltip(font, Component.translatable("gui.singulo.work_console.cancel"), mouseX, mouseY);
                } else if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + AGAIN_X, ry + 3, ROW_BTN, ROW_BTN)) {
                    g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.again"), 180), mouseX, mouseY);
                } else if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 8, ry, AGAIN_X - 10, ROW_H - 1)) {
                    // 素材を待っている注文: 足りない物を全部並べる
                    List<Component> lines = new ArrayList<>();
                    CompoundTag o = orders.get(idx);
                    lines.add(stack(o, "target").getHoverName().copy().append(" ×" + compact(o.getLong("count"))));
                    lines.add(orderState(o).copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                    List<CompoundTag> missing = tags(o, "missing");
                    if (!missing.isEmpty()) {
                        lines.add(Component.translatable("gui.singulo.work_console.missing").withStyle(net.minecraft.ChatFormatting.GOLD));
                        for (CompoundTag m : missing) {
                            lines.add(Component.literal(" ").append(stack(m, "item").getHoverName()).append(" ×" + compact(m.getLong("count"))));
                        }
                    }
                    lines.add(Component.translatable("gui.singulo.work_console.detail.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        } else if (tab == TAB_STANDING) {
            List<CompoundTag> rows = list("standing");
            for (int i = 0; i < orderRows() && i + scroll < rows.size(); i++) {
                if (Panel.inside(mouseX, mouseY, x + CANCEL_X, y + TOP + i * ROW_H + 3, ROW_BTN, ROW_BTN)) {
                    g.renderTooltip(font, Component.translatable("gui.singulo.work_console.cancel"), mouseX, mouseY);
                } else if (Panel.inside(mouseX, mouseY, x + 8, y + TOP + i * ROW_H, CANCEL_X - 10, ROW_H - 1)) {
                    Panel.tooltip(g, font, List.of(standingLines(rows.get(i + scroll))), 220, mouseX, mouseY);
                }
            }
        } else if (tab == 2) {
            List<CompoundTag> links = list("links");
            for (int i = 0; i < linkRows() && i + scroll < links.size(); i++) {
                if (Panel.inside(mouseX, mouseY, x + CANCEL_X, y + TOP + i * LINK_H, ROW_BTN, ROW_BTN)) {
                    g.renderTooltip(font, Component.translatable("gui.singulo.work_console.unlink"), mouseX, mouseY);
                } else if (Panel.inside(mouseX, mouseY, x + 8, y + TOP + i * LINK_H, CANCEL_X - 10, LINK_H - 1)) {
                    g.renderTooltip(font, linkLine(links.get(i + scroll)), mouseX, mouseY);
                }
            }
        }
    }

    /** レシピ登録の窓の説明。 */
    private void renderRecordTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = recordX(), y = recordY();
        if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ENCODE_X, y + WorkConsoleMenu.ENCODE_Y, WorkConsoleMenu.BUTTON_W,
                WorkConsoleMenu.BUTTON_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.encode.hint"), 160), mouseX, mouseY);
        } else if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_X, y + WorkConsoleMenu.CLEAR_Y, WorkConsoleMenu.BUTTON_W,
                WorkConsoleMenu.BUTTON_H)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.recipe_encoder.clear"), mouseX, mouseY);
        } else if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_CARD_X, y + WorkConsoleMenu.CLEAR_CARD_Y, WorkConsoleMenu.ICON_W,
                WorkConsoleMenu.BUTTON_H)) {
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.singulo.recipe_encoder.clear_card"),
                    Component.translatable("gui.singulo.recipe_encoder.clear_card.hint").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_CARD && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, Component.translatable("gui.singulo.recipe_encoder.card"), mouseX, mouseY);
        } else if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.MODE_X, y + WorkConsoleMenu.MODE_Y, WorkConsoleMenu.BUTTON_W,
                WorkConsoleMenu.BUTTON_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.mode.hint"), 160), mouseX, mouseY);
        } else if (hoveredStep(mouseX, mouseY) >= 0) {
            io.github.genichimaruo.singulo.android.work.ProcedureStep s = io.github.genichimaruo.singulo.android.work.ProcedureStep.load(list("proc").get(hoveredStep(mouseX, mouseY)),
                    minecraft.level.registryAccess());
            g.renderTooltip(font, java.util.List.of(io.github.genichimaruo.singulo.android.work.ProcedureCardItem.describe(s).getVisualOrderText(),
                    Component.translatable("gui.singulo.recipe_encoder.step.remove").withStyle(net.minecraft.ChatFormatting.GRAY)
                            .getVisualOrderText()), mouseX, mouseY);
        } else if (optionShown() && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.OPTION_X, y + WorkConsoleMenu.OPTION_Y, WorkConsoleMenu.OPTION_W,
                WorkConsoleMenu.OPTION_H)) {
            g.renderTooltip(font, font.split(Component.translatable(menu.stepKind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE
                    ? "gui.singulo.recipe_encoder.use.hint" : "gui.singulo.recipe_encoder.limit.hint"), 180), mouseX, mouseY);
        } else if (menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.KIND_X,
                y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.KIND_W, WorkConsoleMenu.SMALL_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.kind.hint"), 180), mouseX, mouseY);
        } else if (menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ADD_X,
                y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.ADD_W, WorkConsoleMenu.SMALL_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.step.add.hint"), 180), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && (hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_A
                || hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_B || hoveredSlot.index == WorkConsoleMenu.SLOT_PITEM)) {
            String key = hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_A ? "loc_a" : hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_B ? "loc_b" : "item";
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.step." + key + ".hint"), 180), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_RESULT && !hoveredSlot.hasItem()
                && menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.product.hint"), 160), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index >= WorkConsoleMenu.SLOT_EXTRA && hoveredSlot.index < WorkConsoleMenu.SLOT_LOC_A
                && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.extra.hint"), 160), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_RESULT && !hoveredSlot.hasItem() && menu.processing()) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.output.hint"), 160), mouseX, mouseY);
        } else if (variantMarkAt(mouseX, mouseY) >= 0) {
            int i = variantMarkAt(mouseX, mouseY);
            g.renderComponentTooltip(font, List.of(Component.translatable(menu.variant(i) ? "gui.singulo.recipe_encoder.variant_slot.on"
                    : "gui.singulo.recipe_encoder.variant_slot.off"), Component.translatable("gui.singulo.recipe_encoder.variant_slot.hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (menu.mode() == WorkConsoleBlockEntity.MODE_CRAFTING && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.VARIANTS_X,
                y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H)) {
            int mask = menu.variantMask();
            Component state = Component.translatable(mask == WorkConsoleBlockEntity.ALL_VARIANTS
                    ? "gui.singulo.recipe_encoder.variants.on" : mask != 0 ? "gui.singulo.recipe_encoder.variants.some"
                    : "gui.singulo.recipe_encoder.variants.off");
            Panel.tooltip(g, font, List.of(state, Component.translatable("gui.singulo.recipe_encoder.variants.hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)), 180, mouseX, mouseY);
        } else if (fuelModeHovered(mouseX, mouseY)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.fuel_mode.hint"), 180), mouseX, mouseY);
        } else if (hoveredFace(mouseX, mouseY) >= 0) {
            String[] keys = {"in", "out", "fuel"};
            int i = hoveredFace(mouseX, mouseY);
            Panel.tooltip(g, font, List.of(Component.translatable("gui.singulo.recipe_encoder.face_button." + keys[i], faceName(i)),
                    Component.translatable("gui.singulo.recipe_encoder.face_button." + keys[i] + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY)),
                    200, mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_FUEL && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.fuel.hint"), 180), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_STOCK && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.stock.hint"), 160), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index >= WorkConsoleMenu.SLOT_GRID && hoveredSlot.index < WorkConsoleMenu.SLOT_RESULT
                && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.grid.hint"), 160), mouseX, mouseY);
        }
    }

    /** 加工モードの、燃料を持ってくる量のボタンを指しているか。 */
    private boolean fuelModeHovered(double mouseX, double mouseY) {
        return menu.processing() && recordPop.open && Panel.inside(mouseX, mouseY, recordX() + WorkConsoleMenu.FUEL_MODE_X,
                recordY() + WorkConsoleMenu.FUEL_MODE_Y, WorkConsoleMenu.FUEL_MODE_W, WorkConsoleMenu.FUEL_MODE_H);
    }

    /** 加工モードの面のボタンのうち、指しているもの（0 入れる、1 取り出す、2 燃料。なければ -1）。 */
    private int hoveredFace(double mouseX, double mouseY) {
        if (!menu.processing() || !recordPop.open) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            if (Panel.inside(mouseX, mouseY, recordX() + WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP),
                    recordY() + WorkConsoleMenu.FACE_Y, WorkConsoleMenu.FACE_W, WorkConsoleMenu.FACE_H)) {
                return i;
            }
        }
        return -1;
    }

    /** 燃料を、装置に入るだけ持ってくる設定か。 */
    private boolean fuelFill() {
        return (menu.faces() & io.github.genichimaruo.singulo.android.work.RecipeData.FUEL_FILL) != 0;
    }

    /** 役目 role（0 入れる、1 取り出す、2 燃料）の面の名前（自動・上・下・正面・背面・左・右・北・南・西・東）。 */
    private Component faceName(int role) {
        return Component.translatable("gui.singulo.recipe_encoder.face." + io.github.genichimaruo.singulo.android.work.RecipeData.faceChoiceKey(
                io.github.genichimaruo.singulo.android.work.RecipeData.faceChoice(menu.faces(), role)));
    }

    /** 手順モードで、次に足す手順が数の上限・レバーの扱いを持つか（ボタンを出す）。 */
    private boolean optionShown() {
        return menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE && io.github.genichimaruo.singulo.android.work.ProcedureStep.hasOption(menu.stepKind());
    }

    private void adjustOption(boolean up, boolean ten) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_OPTION + (up ? 0 : 1) + (ten ? 2 : 0));
        }
    }

    /** 加工モードで、指している見本の数を変える。変えられる所なら true。 */
    private boolean adjustHovered(boolean up, boolean ten) {
        if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING || hoveredSlot == null
                || !menu.isGhost(hoveredSlot.index) || !hoveredSlot.hasItem()
                || minecraft.gameMode == null) {
            return false;
        }
        int kind = (up ? 0 : 1) + (ten ? 2 : 0);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_ADJUST + hoveredSlot.index * 4 + kind);
        return true;
    }

    private void renderPickingTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = pickX(), y = pickY();
        if (trigger < 0 && Panel.inside(mouseX, mouseY, x + FRESH_X, y + FRESH_Y, FRESH_W, FRESH_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.fresh.hint"), 200), mouseX, mouseY);
            return;
        }
        if (Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H)) {
            String key = trigger < 0 ? "now" : io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trigger];
            Panel.tooltip(g, font, List.of(triggerLabel(), Component.translatable("gui.singulo.work_console.trigger." + key + ".hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)), 180, mouseX, mouseY);
            return;
        }
        List<CompoundTag> androids = list("androids");
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + androidScroll;
            if (idx < androids.size() && Panel.inside(mouseX, mouseY, x + 128, y + LIST_Y + i * LIST_H, 112, LIST_H)) {
                CompoundTag a = androids.get(idx);
                if (!a.getBoolean("busy") && !selectable(a)) {
                    boolean axe = plan != null && plan.getBoolean("felling") && !a.getBoolean("feller");
                    boolean tank = plan != null && plan.getBoolean("fluids") && !a.getBoolean("tank");
                    boolean chem = plan != null && plan.getBoolean("chemicals") && !a.getBoolean("chem_tank");
                    g.renderTooltip(font, font.split(Component.translatable(chem ? "gui.singulo.work_console.needs_chem_tank"
                            : tank ? "gui.singulo.work_console.needs_tank"
                            : axe ? "gui.singulo.work_console.needs_feller" : "gui.singulo.work_console.needs_miner"), 160), mouseX, mouseY);
                    return;
                }
            }
        }
        if (Panel.inside(mouseX, mouseY, x + START_X, y + BUTTON_Y, START_W, BUTTON_H) && canStart() && chosen.stream().noneMatch(
                id -> androids.stream().anyMatch(a -> a.getUUID("id").equals(id) && selectable(a)))) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.auto_android", startAndroids().size()), 160),
                    mouseX, mouseY);
            return;
        }
        if (Panel.inside(mouseX, mouseY, x + 8, y + 27, 14, 14) || Panel.inside(mouseX, mouseY, x + 76, y + 27, 14, 14)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.count.hint"), 160), mouseX, mouseY);
        }
        List<CompoundTag> deliveries = list("deliveries");
        if (Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H) && !deliveries.isEmpty()) {
            CompoundTag d = deliveries.get(Math.min(destination, deliveries.size() - 1));
            Panel.tooltip(g, font, List.of(Component.translatable("gui.singulo.work_console.delivery", text(d, "name")),
                    Component.translatable("gui.singulo.work_console.delivery.hint").withStyle(net.minecraft.ChatFormatting.GRAY)), 180, mouseX, mouseY);
        }
        List<CompoundTag> mats = plan == null ? List.of() : planRows();
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + listScroll;
            if (idx < mats.size() && Panel.inside(mouseX, mouseY, x + 8, y + LIST_Y + i * LIST_H, 116, LIST_H)) {
                CompoundTag m = mats.get(idx);
                List<Component> lines = new ArrayList<>();
                lines.add(stack(m, "item").getHoverName());
                lines.add(Component.translatable(m.contains("surplus") ? "gui.singulo.work_console.crafted.hint"
                        : "gui.singulo.work_console.material.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
        if (Panel.inside(mouseX, mouseY, x + START_X, y + BUTTON_Y, START_W, BUTTON_H) && !canStart()) {
            g.renderTooltip(font, font.split(Component.translatable(startAndroids().isEmpty() ? "gui.singulo.work_console.choose_android"
                    : "gui.singulo.work_console.cannot_start"), 160), mouseX, mouseY);
        }
    }

    /** 注文の確認の、きっかけのボタンの文字。 */
    private Component triggerLabel() {
        return Component.translatable(trigger < 0 ? "gui.singulo.work_console.trigger.now"
                : "gui.singulo.work_console.trigger." + io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trigger]);
    }

    /** 素材の一覧の行: 作る物（中間の素材・完成品）を先に、そのあと素材置き場から使う物。 */
    private List<CompoundTag> planRows() {
        List<CompoundTag> rows = new ArrayList<>(planList("crafted"));
        rows.addAll(planList("materials"));
        return rows;
    }

    private List<CompoundTag> planList(String key) {
        List<CompoundTag> out = new ArrayList<>();
        if (plan != null) {
            for (Tag t : plan.getList(key, Tag.TAG_COMPOUND)) {
                out.add((CompoundTag) t);
            }
        }
        return out;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        clearStats();
        drawOuterTabs(g, mouseX, mouseY, false);
        drawFrame(g, mouseX, mouseY);
        drawOuterTabs(g, mouseX, mouseY, true);
        for (var s : menu.slots) {
            if (s.isActive() && !isRecordSlot(s.index) && s.index != WorkConsoleMenu.SLOT_PERMIT && s.index < WorkConsoleMenu.OWN) {
                Panel.slot(g, skin, x + s.x, y + s.y);      // 持ち物の地は骨格が描く。レシピ登録の欄は窓の中に描く
            }
        }
        if (tab == 0) {
            renderEntries(g, x, y, mouseX, mouseY);
        } else if (tab == 1) {
            renderOrders(g, x, y, mouseX, mouseY);
        } else if (tab == 2) {
            renderLinks(g, x, y, mouseX, mouseY);
        } else if (tab == TAB_STANDING) {
            renderStanding(g, x, y, mouseX, mouseY);
        }
    }

    /** 経路の欄の列の名前の鍵（経由地点・対象範囲・進入禁止ゾーン2つ）。 */
    static final String[] ROUTE_COL_KEYS = {"via", "scope", "zone", "zone"};

    /**
     * 経路の欄の見出し（列の名前・行の番号）と、下の説明。colX は列の左端、y は1行目の欄の上端、row は行の間隔、left は行の番号の左端。
     * 作業指令台とステーションの経路のページで共通。
     */
    static void routeLabels(GuiGraphics g, net.minecraft.client.gui.Font font, int[] colX, int y, int row, int left, int muted) {
        routeLabels(g, font, colX, y, row, left, muted, colX[3] + 58 - left);
    }

    /** hintWidth: 下の説明の幅。 */
    static void routeLabels(GuiGraphics g, net.minecraft.client.gui.Font font, int[] colX, int y, int row, int left, int muted, int hintWidth) {
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.route.via"), colX[0] - 12, y - 11, 40, Panel.TEXT);
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.route.scope"), colX[1] - 12, y - 11, 40, Panel.TEXT);
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.route.zone"), colX[2] - 4, y - 11, colX[3] - colX[2] + 24,
                0xFFB04040);
        for (int r = 0; r < io.github.genichimaruo.singulo.android.work.PathRule.ROWS; r++) {
            g.drawString(font, String.valueOf(r + 1), left, y + r * row + 4, Panel.TEXT, false);
            g.drawString(font, "→", colX[0] + 21, y + r * row + 4, muted, false);
        }
        int bottom = y + io.github.genichimaruo.singulo.android.work.PathRule.ROWS * row;
        for (int i = 0; i < 2 && hintWidth > 0; i++) {
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.route.hint." + i), left, bottom + i * 10, hintWidth, muted);
        }
    }

    /** 記録のページ: 見本の欄を光る線で囲み（物は減らない目印）、矢印と、記録・消去のボタン。 */
    private void renderRecord(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean procedure = menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE;
        if (procedure) {
            renderSteps(g, x, y, mouseX, mouseY);
        } else {
            g.renderOutline(x + WorkConsoleMenu.GRID_X - 3, y + WorkConsoleMenu.GRID_Y - 3, 3 * 18 + 4, 3 * 18 + 4, Panel.GLOW);
            Panel.arrow(g, x + WorkConsoleMenu.GRID_X + 62, y + WorkConsoleMenu.RESULT_Y + 1,
                    menu.getSlot(WorkConsoleMenu.SLOT_RESULT).hasItem() ? 1 : 0);
        }
        if (menu.processing()) {
            // 作る物とほかに出てくる物も、自分で置く欄（光る線で囲む）
            g.renderOutline(x + WorkConsoleMenu.RESULT_X - 3, y + WorkConsoleMenu.RESULT_Y - 3, 22, 22, Panel.GLOW);
            g.renderOutline(x + WorkConsoleMenu.EXTRA_X - 3, y + WorkConsoleMenu.EXTRA_Y - 3,
                    18 * io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.EXTRAS + 4, 22, Panel.GLOW);
            g.renderOutline(x + WorkConsoleMenu.FUEL_X - 3, y + WorkConsoleMenu.FUEL_Y - 3, 22, 22, 0xFFE0A050);   // 燃料（橙）
        }
        button(g, x + WorkConsoleMenu.ENCODE_X, y + WorkConsoleMenu.ENCODE_Y, WorkConsoleMenu.BUTTON_W, WorkConsoleMenu.BUTTON_H, true, mouseX, mouseY);
        button(g, x + WorkConsoleMenu.MODE_X, y + WorkConsoleMenu.MODE_Y, WorkConsoleMenu.BUTTON_W, WorkConsoleMenu.BUTTON_H, false, mouseX, mouseY);
        // ごみ箱のボタン: 見本消去（右の列いっぱい）とレシピ消去（カードの右の小さなボタン）
        int[][] trash = {{WorkConsoleMenu.CLEAR_X, WorkConsoleMenu.CLEAR_Y, WorkConsoleMenu.BUTTON_W},
                {WorkConsoleMenu.CLEAR_CARD_X, WorkConsoleMenu.CLEAR_CARD_Y, WorkConsoleMenu.ICON_W}};
        for (int[] b : trash) {
            boolean hover = Panel.inside(mouseX, mouseY, x + b[0], y + b[1], b[2], WorkConsoleMenu.BUTTON_H);
            int d = Panel.button(g, skin, x + b[0], y + b[1], b[2], WorkConsoleMenu.BUTTON_H, hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            trashIcon(g, x + b[0] + (b[2] - 7) / 2, y + b[1] + 3 + d, hover ? GuiSkin.BAD : skin.text());
        }
        if (optionShown()) {
            button(g, x + WorkConsoleMenu.OPTION_X, y + WorkConsoleMenu.OPTION_Y, WorkConsoleMenu.OPTION_W, WorkConsoleMenu.OPTION_H, false, mouseX,
                    mouseY);
        }
        if (menu.processing()) {
            // 面のボタン: 役目のアイコン（入れる・取り出す・燃料）と面の名前
            Panel.Icon[] icons = {Panel.Icon.IN, Panel.Icon.OUT, Panel.Icon.FLAME};
            for (int i = 0; i < 3; i++) {
                int bx = x + WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP);
                boolean hover = Panel.inside(mouseX, mouseY, bx, y + WorkConsoleMenu.FACE_Y, WorkConsoleMenu.FACE_W, WorkConsoleMenu.FACE_H);
                int d = Panel.button(g, skin, bx, y + WorkConsoleMenu.FACE_Y, WorkConsoleMenu.FACE_W, WorkConsoleMenu.FACE_H,
                        hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
                Panel.icon(g, bx + 3, y + WorkConsoleMenu.FACE_Y + 3 + d, icons[i], i == 2 ? 0xFFE0A050 : skin.accent());
            }
            button(g, x + WorkConsoleMenu.FUEL_MODE_X, y + WorkConsoleMenu.FUEL_MODE_Y, WorkConsoleMenu.FUEL_MODE_W, WorkConsoleMenu.FUEL_MODE_H,
                    fuelFill(), mouseX, mouseY);
        }
        if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING) {
            int mask = menu.variantMask();
            boolean all = mask == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.ALL_VARIANTS;
            boolean over = Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.VARIANTS_X, y + WorkConsoleMenu.VARIANTS_Y,
                    WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H);
            int dv = Panel.button(g, skin, x + WorkConsoleMenu.VARIANTS_X, y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W,
                    WorkConsoleMenu.VARIANTS_H, all ? Panel.Press.ON : over ? Panel.Press.HOVER : Panel.Press.NORMAL);
            wave(g, x + WorkConsoleMenu.VARIANTS_X + 5, y + WorkConsoleMenu.VARIANTS_Y + 4 + dv,
                    all ? skin.onAccent() : mask != 0 ? skin.accent() : skin.dim());
            // 同じ種類も使う欄は、アクセントの縁で囲む
            for (int i = 0; i < io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.GRID; i++) {
                if (menu.variant(i) && menu.getSlot(WorkConsoleMenu.SLOT_GRID + i).hasItem()) {
                    g.renderOutline(x + gridSlotX(i) - 1, y + gridSlotY(i) - 1, 18, 18, skin.accent());   // 欄の縁の内側（隣の欄に重ねない）
                }
            }
        }
        if (procedure) {
            button(g, x + WorkConsoleMenu.KIND_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.KIND_W, WorkConsoleMenu.SMALL_H, false, mouseX, mouseY);
            button(g, x + WorkConsoleMenu.ADD_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.ADD_W, WorkConsoleMenu.SMALL_H, true, mouseX, mouseY);
        }
    }

    private static void button(GuiGraphics g, int bx, int by, int w, int h, boolean primary, int mouseX, int mouseY) {
        boolean hover = Panel.inside(mouseX, mouseY, bx, by, w, h);
        Panel.button(g, GuiSkin.STANDARD, bx, by, w, h, primary ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
    }

    /** 手順モード: 並べた手順の一覧（番号・物・種類と数。右端の × で消す）。物と作る物の欄は光る線で囲む。 */
    private void renderSteps(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int lx = x + WorkConsoleMenu.STEPS_X, ly = y + WorkConsoleMenu.STEPS_Y;
        Panel.well(g, lx, ly, WorkConsoleMenu.STEPS_W, WorkConsoleMenu.STEP_H * io.github.genichimaruo.singulo.android.work.RecipeData.MAX_STEPS);
        List<CompoundTag> steps = list("proc");
        for (int i = 0; i < steps.size() && i < io.github.genichimaruo.singulo.android.work.RecipeData.MAX_STEPS; i++) {
            io.github.genichimaruo.singulo.android.work.ProcedureStep s = io.github.genichimaruo.singulo.android.work.ProcedureStep.load(steps.get(i), minecraft.level.registryAccess());
            int ry = ly + i * WorkConsoleMenu.STEP_H;
            if (Panel.inside(mouseX, mouseY, lx, ry, WorkConsoleMenu.STEPS_W, WorkConsoleMenu.STEP_H)) {
                g.fill(lx, ry, lx + WorkConsoleMenu.STEPS_W, ry + WorkConsoleMenu.STEP_H, 0xFF52606C);
            }
            g.drawString(font, String.valueOf(i + 1), lx + 2, ry + 1, 0xFFE8EEF2, false);
            if (!s.item().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(lx + 9, ry + 1, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                g.renderItem(s.item(), 0, 0);
                g.pose().popPose();
            }
            String count = s.item().isEmpty() ? "" : " ×" + s.item().getCount();
            Component opt = io.github.genichimaruo.singulo.android.work.ProcedureCardItem.option(s.kind(), s.option());
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.recipe_encoder.kind." + io.github.genichimaruo.singulo.android.work.ProcedureStep.KEYS[s.kind()])
                    .append(count).append(opt.getString().isEmpty() ? Component.empty() : Component.literal(" ").append(opt)), lx + 19, ry + 1,
                    WorkConsoleMenu.STEPS_W - 28, 0xFFE8EEF2);
            g.drawString(font, "×", lx + WorkConsoleMenu.STEPS_W - 7, ry + 1, 0xFFE07070, false);
        }
        g.renderOutline(x + WorkConsoleMenu.PITEM_X - 3, y + WorkConsoleMenu.PITEM_Y - 3, 22, 22, Panel.GLOW);
        g.renderOutline(x + WorkConsoleMenu.RESULT_PROC_X - 3, y + WorkConsoleMenu.RESULT_PROC_Y - 3, 22, 22, Panel.GLOW);
    }

    /** 小さめの文字（3/4）を、cx を中心に、幅 maxWidth に収めて描く。 */
    private void small(GuiGraphics g, Component text, int cx, int y, int maxWidth, int color) {
        float k = Math.min(0.75F, (float) maxWidth / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(cx - font.width(text) * k / 2, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** 手順モードで、指している手順の番号（なければ -1）。 */
    private int hoveredStep(double mouseX, double mouseY) {
        if (menu.mode() != io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE || !recordPop.open) {
            return -1;
        }
        int lx = recordX() + WorkConsoleMenu.STEPS_X, ly = recordY() + WorkConsoleMenu.STEPS_Y;
        if (!Panel.inside(mouseX, mouseY, lx, ly, WorkConsoleMenu.STEPS_W, WorkConsoleMenu.STEP_H * io.github.genichimaruo.singulo.android.work.RecipeData.MAX_STEPS)) {
            return -1;
        }
        int i = (int) ((mouseY - ly) / WorkConsoleMenu.STEP_H);
        return i < list("proc").size() ? i : -1;
    }

    private void renderEntries(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        renderSearchRow(g, x, y, mouseX, mouseY);
        List<CompoundTag> entries = visibleEntries();
        int maxScroll = Math.max(0, (entries.size() + COLS - 1) / COLS - rows());
        scroll = Math.min(scroll, maxScroll);
        Panel.well(g, x + GRID_LEFT, y + TOP, COLS * CELL, rows() * CELL);
        for (int i = 0; i < COLS * rows(); i++) {
            int idx = i + scroll * COLS;
            if (idx >= entries.size()) {
                break;
            }
            CompoundTag e = entries.get(idx);
            int cx = x + GRID_LEFT + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
            if (Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF52606C);
            }
            ItemStack s = stack(e, "item");
            g.renderItem(s, cx + 1, cy + 1);
            long n = e.getLong("count");
            g.renderItemDecorations(font, s, cx + 1, cy + 1, "");
            drawCount(g, cx + 1, cy + 1, n <= 0 ? "" : compact(n));
            if (e.getBoolean("craft") || e.getLong("making") > 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                if (e.getBoolean("craft")) {
                    g.fill(cx + 1, cy + 1, cx + 4, cy + 4, 0xFF78D2F0);     // 作れる印
                }
                if (e.getLong("making") > 0) {
                    g.renderOutline(cx, cy, CELL, CELL, MAKING);             // 作っている最中の枠
                }
                g.pose().popPose();
            }
        }
        if (maxScroll > 0) {
            int h = rows() * CELL;
            int bar = Math.max(8, h / (maxScroll + 1));
            int by = y + TOP + (h - bar) * scroll / maxScroll;
            int sx = x + GRID_LEFT + COLS * CELL + 1;
            g.fill(sx, y + TOP, sx + 3, y + TOP + h, Panel.SEAM);
            g.fill(sx, by, sx + 3, by + bar, skin.accent());
        }
    }

    /** 注文タブの下の段: 検索欄の地（打っている間は縁が光る）・並びのボタン・逆順のボタン・JEI 連携のボタン。 */
    private void renderSearchRow(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean typing = searchBox != null && searchBox.isFocused();
        Panel.inset(g, skin, x + SEARCH_X, y + searchY(), SEARCH_W, SEARCH_H, skin.fill());
        if (typing) {
            g.renderOutline(x + SEARCH_X - 1, y + searchY() - 1, SEARCH_W + 2, SEARCH_H + 2, skin.accent());
        }
        // 一覧の行を減らす・増やす
        boolean canLess = rows() > MIN_ROWS;
        boolean canMore = rows() < maxRows();
        int dl = Panel.button(g, skin, x + ROWS_LESS_X, y + searchY(), ROWS_BTN_W, SEARCH_H, !canLess ? Panel.Press.DISABLED
                : Panel.inside(mouseX, mouseY, x + ROWS_LESS_X, y + searchY(), ROWS_BTN_W, SEARCH_H) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.minusIcon(g, x + ROWS_LESS_X + 3, y + searchY() + 4 + dl, canLess ? skin.text() : skin.dim());
        int dr = Panel.button(g, skin, x + ROWS_MORE_X, y + searchY(), ROWS_BTN_W, SEARCH_H, !canMore ? Panel.Press.DISABLED
                : Panel.inside(mouseX, mouseY, x + ROWS_MORE_X, y + searchY(), ROWS_BTN_W, SEARCH_H) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.plusIcon(g, x + ROWS_MORE_X + 3, y + searchY() + 4 + dr, canMore ? skin.text() : skin.dim());
        button(g, x + SORT_X, y + searchY(), SORT_W, mouseX, mouseY, false, Component.empty());
        sortIcon(g, sortMode(), x + SORT_X + (SORT_W - 8) / 2, y + searchY() + 3, skin.text(), skin.accent());   // 並びの名前は、重ねたときの説明に
        button(g, x + REV_X, y + searchY(), REV_W, mouseX, mouseY, sortReversed(), Component.literal(sortReversed() ? "↑" : "↓"));
        if (jeiLoaded()) {
            button(g, x + JEI_X, y + searchY(), JEI_W, mouseX, mouseY, jeiSync(), Component.literal("JEI"));
        }
    }

    // ------------------------------------------------------------------ 注文タブの物の一覧の行数

    /** 物の一覧の行数（設定。3〜10行で、画面に収まるところまで）。 */
    private int rows() {
        return Math.max(MIN_ROWS, Math.min(maxRows(), io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_ROWS.get()));
    }

    /** 画面に収まる行数（10行まで）。 */
    private int maxRows() {
        return height <= 0 ? MAX_ROWS : Math.max(MIN_ROWS, Math.min(MAX_ROWS, (height - BASE_HEIGHT - 8) / CELL));
    }

    /** ほかのページの行の数（高さに合わせて増える。下に1行の説明を残す）。 */
    private int orderRows() {
        return (io.github.genichimaruo.singulo.machine.GuiFrame.grooveY(imageHeight) - TOP - 13) / ROW_H;
    }

    private int linkRows() {
        return (io.github.genichimaruo.singulo.machine.GuiFrame.grooveY(imageHeight) - TOP - 14) / LINK_H;
    }

    /** 一覧の下の段（検索欄・並び・行の増減）の高さ。 */
    private int searchY() {
        return TOP + rows() * CELL + 2;
    }

    private void setRows(int rows) {
        saveConfig(io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_ROWS, Math.max(MIN_ROWS, Math.min(maxRows(), rows)));
        applyHeight();                                 // 画面ごと伸び縮みする
    }

    // ------------------------------------------------------------------ 見本の欄ごとの「同じ種類も使う」

    private static int gridSlotX(int i) {
        return WorkConsoleMenu.GRID_X + (i % 3) * 18;
    }

    private static int gridSlotY(int i) {
        return WorkConsoleMenu.GRID_Y + (i / 3) * 18;
    }

    /** レシピ登録の窓の作業台モードで、見本の欄の右上に印を出すか。 */
    private boolean showsVariantMarks() {
        return recordPop.open && menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING;
    }

    /** マウスの下にある、見本の欄の右上の印の欄の番号（なければ -1）。印は物を置いた欄にだけ出す。 */
    private int variantMarkAt(double mouseX, double mouseY) {
        if (!showsVariantMarks()) {
            return -1;
        }
        for (int i = 0; i < io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.GRID; i++) {
            if (markShown(i, mouseX, mouseY) && Panel.inside(mouseX, mouseY, recordX() + gridSlotX(i) + 10, recordY() + gridSlotY(i) - 1, 7, 7)) {
                return i;
            }
        }
        return -1;
    }

    /** 印を出すか: 同じ種類も使う欄はいつも、そうでない欄はマウスを重ねている間だけ（重ねると切り替えられる）。 */
    private boolean markShown(int i, double mouseX, double mouseY) {
        return menu.getSlot(WorkConsoleMenu.SLOT_GRID + i).hasItem() && (menu.variant(i)
                || Panel.inside(mouseX, mouseY, recordX() + gridSlotX(i) - 1, recordY() + gridSlotY(i) - 2, 19, 19));
    }

    /** 波の記号（「いろいろ」。7×3）。 */
    private static void wave(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y + 1, x + 1, y + 2, c);
        g.fill(x + 1, y, x + 2, y + 1, c);
        g.fill(x + 2, y + 1, x + 3, y + 2, c);
        g.fill(x + 3, y + 2, x + 4, y + 3, c);
        g.fill(x + 4, y + 1, x + 5, y + 2, c);
        g.fill(x + 5, y, x + 6, y + 1, c);
        g.fill(x + 6, y + 1, x + 7, y + 2, c);
    }

    /** 見本の欄の右上の小さな印（オンはアクセントの丸に白い波、オフは薄い丸）。renderLabels の座標で描く。 */
    private void drawVariantMarks(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int i = 0; i < io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.GRID; i++) {
            if (!markShown(i, mouseX, mouseY)) {
                continue;
            }
            int mx = gridSlotX(i) + 10;
            int my = gridSlotY(i) - 1;
            boolean on = menu.variant(i);
            int fill = on ? skin.accent() : skin.button2();
            int edge = on ? skin.accentLo() : skin.lo();
            g.fill(mx + 1, my, mx + 6, my + 7, edge);
            g.fill(mx, my + 1, mx + 7, my + 6, edge);
            g.fill(mx + 1, my + 1, mx + 6, my + 6, fill);
            int c = on ? 0xFFFFFFFF : skin.dim();
            // 波（「いろいろ」の印）
            g.fill(mx + 1, my + 3, mx + 2, my + 4, c);
            g.fill(mx + 2, my + 2, mx + 3, my + 3, c);
            g.fill(mx + 3, my + 3, mx + 4, my + 4, c);
            g.fill(mx + 4, my + 2, mx + 5, my + 3, c);
            g.fill(mx + 5, my + 3, mx + 6, my + 4, c);
        }
        g.pose().popPose();
    }

    /** 下の段の小さなボタン（on: 押されている色）。 */
    private void button(GuiGraphics g, int bx, int by, int bw, int mouseX, int mouseY, boolean on, Component label) {
        boolean hover = Panel.inside(mouseX, mouseY, bx, by, bw, SEARCH_H);
        Panel.button(g, skin, bx, by, bw, SEARCH_H, on ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        AndroidStationScreen.fitCentered(g, font, label, bx + 1, by + 2, bw - 2, on ? skin.onAccent() : skin.text());
    }

    /** 進行中の行: 進み具合は行の地の色で（左から満ちる）、右端に もう一度注文・取り消し のボタン。 */
    private void renderOrders(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CompoundTag> orders = list("orders");
        scroll = Math.min(scroll, Math.max(0, orders.size() - orderRows()));
        for (int i = 0; i < orderRows(); i++) {
            int idx = i + scroll;
            if (idx >= orders.size()) {
                break;
            }
            CompoundTag o = orders.get(idx);
            int ry = y + TOP + i * ROW_H;
            rowBackground(g, x, ry, ROW_H - 1);
            int done = (AGAIN_X - 4 - 8) * Math.max(0, Math.min(1000, o.getInt("progress"))) / 1000;
            g.fill(x + 8, ry + ROW_H - 2, x + 8 + done, ry + ROW_H - 1, GREEN);
            g.renderItem(stack(o, "target"), x + 9, ry + 1);
            rowButton(g, x + AGAIN_X, ry + 3, mouseX, mouseY, "↻", skin.text());
            rowButton(g, x + CANCEL_X, ry + 3, mouseX, mouseY, "×", GuiSkin.BAD);
        }
    }

    /** 一覧の行の地（少し浮いた板）。 */
    private void rowBackground(GuiGraphics g, int x, int ry, int h) {
        g.fill(x + 8, ry, x + 168, ry + h, skin.button());
        g.fill(x + 8, ry, x + 168, ry + 1, skin.hi());
        g.fill(x + 8, ry + h - 1, x + 168, ry + h, skin.lo());
    }

    /** 行の右端の小さなボタン（記号1文字）。 */
    private void rowButton(GuiGraphics g, int bx, int by, int mouseX, int mouseY, String glyph, int color) {
        boolean hover = Panel.inside(mouseX, mouseY, bx, by, ROW_BTN, ROW_BTN);
        int d = Panel.button(g, skin, bx, by, ROW_BTN, ROW_BTN, hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        g.drawString(font, glyph, bx + (ROW_BTN - font.width(glyph) + 1) / 2, by + 2 + d, color, false);
    }

    /** 詳しく見ている注文（終わった・ほかのページへ移ったら null）。 */
    @Nullable
    private CompoundTag detailOrder() {
        if (detail < 0) {
            return null;
        }
        for (CompoundTag o : list("orders")) {
            if (o.getInt("id") == detail) {
                return o;
            }
        }
        detail = -1;
        return null;
    }

    /** 注文の詳しい様子: 左に工程ごとの回数（最後に納品）、右に受け持っているアンドロイドと、今していること。 */
    private void renderDetail(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        CompoundTag o = detailOrder();
        Panel.well(g, x + 8, y + TOP, 116, DETAIL_ROWS * DETAIL_H);
        Panel.well(g, x + 128, y + TOP, 112, DETAIL_ROWS * DETAIL_H);
        List<CompoundTag> steps = tags(o, "steps");
        detailScroll = Math.min(detailScroll, Math.max(0, steps.size() - (DETAIL_ROWS - 1)));
        for (int i = 0; i + detailScroll < steps.size() && i < DETAIL_ROWS - 1; i++) {
            g.renderItem(stack(steps.get(i + detailScroll), "item"), x + 9, y + TOP + i * DETAIL_H);
        }
        int last = Math.min(steps.size(), DETAIL_ROWS - 1);
        g.renderItem(stack(o, "target"), x + 9, y + TOP + last * DETAIL_H);
        List<CompoundTag> workers = tags(o, "worker_list");
        workerScroll = Math.min(workerScroll, Math.max(0, workers.size() - DETAIL_ROWS));
        for (int i = 0; i + workerScroll < workers.size() && i < DETAIL_ROWS; i++) {
            int ry = y + TOP + i * DETAIL_H;
            CompoundTag worker = workers.get(i + workerScroll);
            g.fill(x + 131, ry + 4, x + 139, ry + 12, worker.getInt("activity") < 0 ? 0xFF8A949E
                    : 0xFF000000 | worker.getInt("accent"));
        }
        detailScrollbar(g, x + 122, y + TOP, DETAIL_ROWS - 1, steps.size(), detailScroll);
        detailScrollbar(g, x + 238, y + TOP, DETAIL_ROWS, workers.size(), workerScroll);
    }

    private static void detailScrollbar(GuiGraphics g, int x, int y, int rows, int total, int offset) {
        if (total <= rows) {
            return;
        }
        int height = rows * DETAIL_H;
        int thumb = Math.max(6, height * rows / total);
        int top = y + (height - thumb) * offset / (total - rows);
        g.fill(x, y, x + 1, y + height, 0xFF46515B);
        g.fill(x, top, x + 1, top + thumb, 0xFF78D2F0);
    }

    private static List<CompoundTag> tags(CompoundTag t, String key) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag e : t.getList(key, Tag.TAG_COMPOUND)) {
            out.add((CompoundTag) e);
        }
        return out;
    }

    /** 登録のページ: 登録した注文の行（物・きっかけ・様子、右端の × で消す）。 */
    private void renderStanding(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CompoundTag> rows = list("standing");
        scroll = Math.min(scroll, Math.max(0, rows.size() - orderRows()));
        for (int i = 0; i < orderRows(); i++) {
            int idx = i + scroll;
            if (idx >= rows.size()) {
                break;
            }
            int ry = y + TOP + i * ROW_H;
            rowBackground(g, x, ry, ROW_H - 1);
            g.renderItem(stack(rows.get(idx), "item"), x + 9, ry + 1);
            rowButton(g, x + CANCEL_X, ry + 3, mouseX, mouseY, "×", GuiSkin.BAD);
        }
    }

    private void renderLinks(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CompoundTag> links = list("links");
        scroll = Math.min(scroll, Math.max(0, links.size() - linkRows()));
        for (int i = 0; i < linkRows(); i++) {
            int idx = i + scroll;
            if (idx >= links.size()) {
                break;
            }
            int ry = y + TOP + i * LINK_H;
            rowBackground(g, x, ry, LINK_H - 1);
            rowButton(g, x + CANCEL_X, ry, mouseX, mouseY, "×", GuiSkin.BAD);
        }
    }

    private void renderPicking(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int dm = Panel.button(g, skin, x + 8, y + 27, 14, 14, Panel.inside(mouseX, mouseY, x + 8, y + 27, 14, 14) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.minusIcon(g, x + 13, y + 32 + dm, skin.text());
        Panel.inset(g, skin, x + 25, y + 27, 48, 14, skin.fill());
        if (countBox != null && countBox.isFocused()) {
            g.renderOutline(x + 24, y + 26, 50, 16, skin.accent());
        }
        int dp = Panel.button(g, skin, x + 76, y + 27, 14, 14, Panel.inside(mouseX, mouseY, x + 76, y + 27, 14, 14) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.plusIcon(g, x + 81, y + 32 + dp, skin.text());
        Panel.well(g, x + 8, y + LIST_Y, 116, LIST_ROWS * LIST_H);
        Panel.well(g, x + 128, y + LIST_Y, 112, LIST_ROWS * LIST_H);
        if (trigger < 0) {
            Panel.button(g, skin, x + FRESH_X, y + FRESH_Y, FRESH_W, FRESH_H, fresh ? Panel.Press.ON
                    : Panel.inside(mouseX, mouseY, x + FRESH_X, y + FRESH_Y, FRESH_W, FRESH_H) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        }
        Panel.button(g, skin, x + DEST_X, y + DEST_Y, DEST_W, DEST_H,
                Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.button(g, skin, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H, trigger >= 0 ? Panel.Press.ON
                : Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        List<CompoundTag> mats = planRows();
        listScroll = Math.min(listScroll, Math.max(0, mats.size() - LIST_ROWS));
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + listScroll;
            if (idx >= mats.size()) {
                break;
            }
            g.renderItem(stack(mats.get(idx), "item"), x + 9, y + LIST_Y + i * LIST_H);
        }
        List<CompoundTag> androids = list("androids");
        androidScroll = Math.min(androidScroll, Math.max(0, androids.size() - LIST_ROWS));
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + androidScroll;
            if (idx >= androids.size()) {
                break;
            }
            CompoundTag a = androids.get(idx);
            int ry = y + LIST_Y + i * LIST_H;
            boolean can = selectable(a);
            boolean on = can && chosen.contains(a.getUUID("id"));
            int accent = 0xFF000000 | a.getInt("accent");
            Panel.inset(g, skin, x + 130, ry + 3, 10, 10, on ? accent : can ? 0xFFF4F6F8 : skin.dim());
        }
        boolean start = canStart();
        Panel.button(g, skin, x + START_X, y + BUTTON_Y, START_W, BUTTON_H, start ? Panel.Press.ON : Panel.Press.DISABLED);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
        CompoundTag v = menu.view();
        Component message = v != null && v.contains("message") ? text(v, "message") : null;
        int bottomLine = TOP + orderRows() * ROW_H + 2;
        if (tab == 0) {
            if (list("entries").isEmpty()) {
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.empty"), GRID_LEFT, TOP + rows() * CELL / 2 - 4,
                        COLS * CELL, 0xFFBFC8D0);
            } else if (visibleEntries().isEmpty()) {
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.no_match"), GRID_LEFT, TOP + rows() * CELL / 2 - 4,
                        COLS * CELL, 0xFFBFC8D0);
            }
            if (message != null) {
                // お知らせは、インベントリの見出しと同じ行の右側に（注文の欄はインベントリのすぐ上まで使う）
                int mx = inventoryLabelX + font.width(playerInventoryTitle) + 8;
                clip(g, null, message, mx, inventoryLabelY, imageWidth - 8 - mx, MUTED, leftPos, topPos);
            }
        } else if (tab == 1) {
            List<CompoundTag> orders = list("orders");
            if (orders.isEmpty()) {
                clip(g, null, Component.translatable("gui.singulo.work_console.no_orders"), 10, TOP + 4, 156, MUTED, leftPos, topPos);
            }
            for (int i = 0; i < orderRows(); i++) {
                int idx = i + scroll;
                if (idx >= orders.size()) {
                    break;
                }
                CompoundTag o = orders.get(idx);
                int ry = TOP + i * ROW_H;
                // 収まらない文字は「…」で切る（全文は行に重ねたときの説明に）
                clipped(g, stack(o, "target").getHoverName().copy().append(" ×" + compact(o.getLong("count"))), 28, ry + 1, AGAIN_X - 32, Panel.TEXT);
                clipped(g, orderState(o), 28, ry + 9, AGAIN_X - 32, o.getInt("status") == WorkOrder.RUNNING ? GREEN : 0xFFC08020);
            }
            if (message != null) {
                clip(g, null, message, 8, bottomLine, 160, MUTED, leftPos, topPos);
            }
        } else if (tab == TAB_STANDING) {
            List<CompoundTag> rows = list("standing");
            if (rows.isEmpty()) {
                clip(g, null, Component.translatable("gui.singulo.work_console.standing.none"), 10, TOP + 4, 156, MUTED, leftPos, topPos);
            }
            for (int i = 0; i < orderRows(); i++) {
                int idx = i + scroll;
                if (idx >= rows.size()) {
                    break;
                }
                CompoundTag s = rows.get(idx);
                int ry = TOP + i * ROW_H;
                int state = s.getInt("state");
                Component[] lines = standingLines(s);         // 全文は行に重ねたときの説明に
                clipped(g, lines[0], 28, ry + 1, CANCEL_X - 32, Panel.TEXT);
                clipped(g, lines[1], 28, ry + 9, CANCEL_X - 32,
                        state == io.github.genichimaruo.singulo.android.work.StandingOrder.RUNNING ? GREEN
                        : state == io.github.genichimaruo.singulo.android.work.StandingOrder.WAITING ? 0xFFC08020 : MUTED);
            }
            // レッドストーン信号: 文字の代わりに、色の付いた稲妻（説明は重ねたときに）
            CompoundTag view = menu.view();
            boolean on = view != null && view.getBoolean("powered");
            iconTip(g, null, Panel.Icon.BOLT, 8, bottomLine, on ? 0xFFD04848 : MUTED, leftPos, topPos,
                    Component.translatable(on ? "gui.singulo.work_console.signal.on" : "gui.singulo.work_console.signal.off"));
        } else if (tab == TAB_OUTBOX) {
            iconTip(g, null, Panel.Icon.INFO, 8, TOP + 1, MUTED, leftPos, topPos, Component.translatable("gui.singulo.work_console.outbox.hint"));
        } else if (tab == TAB_ROUTE) {
            routeLabels(g, font, WorkConsoleMenu.ROUTE_COL_X, WorkConsoleMenu.ROUTE_Y, WorkConsoleMenu.ROUTE_ROW, 10, MUTED, 0);
            int bottom = WorkConsoleMenu.ROUTE_Y + io.github.genichimaruo.singulo.android.work.PathRule.ROWS * WorkConsoleMenu.ROUTE_ROW + 1;
            iconTip(g, null, Panel.Icon.INFO, 10, bottom, MUTED, leftPos, topPos, Component.translatable("gui.singulo.route.hint.0"),
                    Component.translatable("gui.singulo.route.hint.1"));
        } else {
            List<CompoundTag> links = list("links");
            if (links.isEmpty()) {
                clip(g, null, Component.translatable("gui.singulo.work_console.no_links"), 10, TOP + 2, 156, MUTED, leftPos, topPos);
            }
            for (int i = 0; i < linkRows(); i++) {
                int idx = i + scroll;
                if (idx >= links.size()) {
                    break;
                }
                CompoundTag l = links.get(idx);
                clipped(g, linkLine(l), 10, TOP + i * LINK_H + 2, CANCEL_X - 14, l.getInt("kind") < 0 ? RED : Panel.TEXT);
            }
            iconTip(g, null, Panel.Icon.INFO, 8, TOP + linkRows() * LINK_H + 3, MUTED, leftPos, topPos,
                    Component.translatable("gui.singulo.work_console.link.hint"));
        }
    }

    /** 詳しい様子の窓の文字（窓の中身の原点からの座標）: 工程ごとの回数（最後に納品）と、アンドロイドごとの今していること。 */
    private void renderDetailLabels(GuiGraphics g, CompoundTag o) {
        List<CompoundTag> steps = tags(o, "steps");
        int light = 0xFFE9EDF0;
        for (int i = 0; i + detailScroll < steps.size() && i < DETAIL_ROWS - 1; i++) {
            CompoundTag st = steps.get(i + detailScroll);
            long done = st.getLong("done"), total = st.getLong("total"), doing = st.getLong("doing");
            Component line = Component.literal(compact(done) + " / " + compact(total));
            if (doing > 0) {
                line = line.copy().append(Component.translatable("gui.singulo.work_console.detail.doing", compact(doing)));
            }
            long parked = st.getLong("parked");
            if (parked > 0) {
                int eta = st.getInt("eta");
                line = line.copy().append(eta >= 0
                        ? Component.translatable("gui.singulo.work_console.detail.machine_eta", compact(parked), (eta + 19) / 20)
                        : Component.translatable("gui.singulo.work_console.detail.machine", compact(parked)));
            }
            clip(g, detailPop, line, 27, TOP + i * DETAIL_H + 4, 95, done >= total ? 0xFF78D2F0 : light, detailX(), detailY());
        }
        int last = Math.min(steps.size(), DETAIL_ROWS - 1);
        Component deliver = o.getBoolean("to_stock")
                ? Component.translatable("gui.singulo.work_console.detail.to_stock", compact(o.getLong("delivered")), compact(o.getLong("count")))
                : Component.translatable("gui.singulo.work_console.detail.delivered", compact(o.getLong("delivered")), compact(o.getLong("count")));
        clip(g, detailPop, deliver, 27, TOP + last * DETAIL_H + 4, 95, light, detailX(), detailY());
        List<CompoundTag> workers = tags(o, "worker_list");
        for (int i = 0; i + workerScroll < workers.size() && i < DETAIL_ROWS; i++) {
            CompoundTag w = workers.get(i + workerScroll);
            int act = w.getInt("activity");
            int stall = w.getInt("stall");
            Component line = text(w, "name").copy();
            if (stall > io.github.genichimaruo.singulo.android.RuinAndroid.STALL_NONE
                    && stall < io.github.genichimaruo.singulo.android.RuinAndroid.STALL_KEYS.length) {
                // 止まっている理由（赤い字）: 作業の中身より先に知らせる
                line = line.copy().append(": ").append(Component.translatable("gui.singulo.android.stall."
                        + io.github.genichimaruo.singulo.android.RuinAndroid.STALL_KEYS[stall]));
                clip(g, detailPop, line, 143, TOP + i * DETAIL_H + 4, 95, 0xFFE06C5A, detailX(), detailY());
                continue;
            }
            if (act == -2) {
                line = line.copy().append(": ").append(Component.translatable("gui.singulo.work_console.worker.elsewhere"));
            } else if (act >= 0) {
                line = line.copy().append(": ").append(Component.translatable("gui.singulo.work_console.activity."
                        + io.github.genichimaruo.singulo.android.RuinAndroid.ACTIVITY_KEYS[Math.min(act, io.github.genichimaruo.singulo.android.RuinAndroid.ACTIVITY_KEYS.length - 1)]));
            }
            clip(g, detailPop, line, 143, TOP + i * DETAIL_H + 4, 95, act == -1 ? MUTED : light, detailX(), detailY());
        }
    }

    /** レシピ登録の窓の文字（窓の中身の原点からの座標）。 */
    private void renderRecordLabels(GuiGraphics g, int mouseX, int mouseY) {
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.encode"), WorkConsoleMenu.ENCODE_X + 2,
                WorkConsoleMenu.ENCODE_Y + 3, WorkConsoleMenu.BUTTON_W - 4, 0xFFFFFFFF);
        String[] modes = {"crafting", "processing", "procedure"};
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.mode." + modes[Math.floorMod(menu.mode(), 3)]),
                WorkConsoleMenu.MODE_X + 2, WorkConsoleMenu.MODE_Y + 3, WorkConsoleMenu.BUTTON_W - 4, Panel.TEXT);
        if (menu.mode() == WorkConsoleBlockEntity.MODE_PROCEDURE) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.kind."
                    + io.github.genichimaruo.singulo.android.work.ProcedureStep.KEYS[Math.floorMod(menu.stepKind(), io.github.genichimaruo.singulo.android.work.ProcedureStep.KINDS)]), WorkConsoleMenu.KIND_X + 1, WorkConsoleMenu.SMALL_Y + 2,
                    WorkConsoleMenu.KIND_W - 2, Panel.TEXT);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.step.add"), WorkConsoleMenu.ADD_X + 1,
                    WorkConsoleMenu.SMALL_Y + 2, WorkConsoleMenu.ADD_W - 2, 0xFFFFFFFF);
            if (optionShown()) {
                AndroidStationScreen.fitCentered(g, font, io.github.genichimaruo.singulo.android.work.ProcedureCardItem.option(menu.stepKind(), menu.stepOption()),
                        WorkConsoleMenu.OPTION_X + 1, WorkConsoleMenu.OPTION_Y + 2, WorkConsoleMenu.OPTION_W - 2, Panel.TEXT);
            }
        } else if (menu.processing()) {
            // 面のボタン: アイコンの右に面の名前（役目は重ねたときの説明に）
            for (int i = 0; i < 3; i++) {
                int bx = WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP);
                AndroidStationScreen.fitCentered(g, font, faceName(i), bx + 12, WorkConsoleMenu.FACE_Y + 3, WorkConsoleMenu.FACE_W - 14, Panel.TEXT);
            }
            // 燃料を持ってくる量: 「N個」（燃料の欄の数ずつ）か「満タン」（装置に入るだけ）
            int fuelCount = Math.max(1, menu.getSlot(WorkConsoleMenu.SLOT_FUEL).getItem().getCount());
            small(g, fuelFill() ? Component.translatable("gui.singulo.recipe_encoder.fuel_mode.fill")
                            : Component.translatable("gui.singulo.recipe_encoder.fuel_mode.count", fuelCount),
                    WorkConsoleMenu.FUEL_MODE_X + WorkConsoleMenu.FUEL_MODE_W / 2, WorkConsoleMenu.FUEL_MODE_Y + 3, WorkConsoleMenu.FUEL_MODE_W - 2,
                    fuelFill() ? 0xFFFFFFFF : Panel.TEXT);
        } else {
            drawVariantMarks(g, mouseX, mouseY);
        }
    }

    private void renderPickingLabels(GuiGraphics g) {
        if (trigger < 0) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.fresh"), FRESH_X + 1, FRESH_Y + 3, FRESH_W - 2,
                    fresh ? 0xFFFFFFFF : Panel.TEXT);
        }
        int ox = pickX(), oy = pickY();
        List<CompoundTag> deliveries = list("deliveries");
        if (!deliveries.isEmpty()) {
            // 納品先: 送り出しのアイコンと名前だけ（「納品先:」の文字は重ねたときの説明に）
            CompoundTag d = deliveries.get(Math.min(destination, deliveries.size() - 1));
            Panel.icon(g, DEST_X + 4, DEST_Y + 3, Panel.Icon.OUT, skin.text());
            clipped(g, text(d, "name"), DEST_X + 15, DEST_Y + 3, DEST_W - 19, Panel.TEXT);
        }
        // 一覧の見出し: アイコンだけ（説明は重ねたときに）
        iconTip(g, orderPop, Panel.Icon.BOX, 9, LIST_Y - 10, Panel.TEXT, ox, oy, Component.translatable("gui.singulo.work_console.materials"));
        iconTip(g, orderPop, Panel.Icon.PERSON, 129, LIST_Y - 10, Panel.TEXT, ox, oy, Component.translatable("gui.singulo.work_console.androids"));
        List<CompoundTag> mats = planRows();
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + listScroll;
            if (idx >= mats.size()) {
                break;
            }
            CompoundTag m = mats.get(idx);
            if (m.contains("surplus")) {
                // 作る物: 作る数と、余り
                long surplus = m.getLong("surplus");
                Component made = surplus > 0
                        ? Component.translatable("gui.singulo.work_console.crafted.surplus", compact(m.getLong("count")), compact(surplus))
                        : Component.translatable("gui.singulo.work_console.crafted", compact(m.getLong("count")));
                clip(g, orderPop, made, 27, LIST_Y + i * LIST_H + 4, 95, 0xFF78D2F0, ox, oy);
                continue;
            }
            boolean short_ = m.getLong("missing") > 0;
            clip(g, orderPop, Component.literal(compact(m.getLong("count")) + " / " + compact(m.getLong("have"))), 27, LIST_Y + i * LIST_H + 4, 95,
                    short_ ? RED : 0xFFE9EDF0, ox, oy);
        }
        List<CompoundTag> androids = list("androids");
        if (androids.isEmpty()) {
            clip(g, orderPop, Component.translatable("gui.singulo.work_console.no_androids"), 130, LIST_Y + 4, 108, 0xFFBFC8D0, ox, oy);
        }
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + androidScroll;
            if (idx >= androids.size()) {
                break;
            }
            CompoundTag a = androids.get(idx);
            Component name = text(a, "name").copy().append(" (" + a.getInt("slots") + ")");
            if (a.getBoolean("busy")) {
                name = Component.translatable("gui.singulo.work_console.busy", name);
            }
            clip(g, orderPop, name, 144, LIST_Y + i * LIST_H + 4, 94, selectable(a) ? 0xFFE9EDF0 : MUTED, ox, oy);
        }
        Component status;
        int color = Panel.TEXT;
        if (error != null) {
            status = error;
            color = RED;
        } else if (plan == null || planDelay > 0) {
            status = Component.translatable("gui.singulo.work_console.planning");
        } else if (!plan.getBoolean("ok")) {
            status = Component.translatable(plan.getBoolean("loop") ? "gui.singulo.work_console.error.loop" : "gui.singulo.work_console.error.missing");
            color = RED;
        } else {
            status = Component.translatable("gui.singulo.work_console.plan", plan.getInt("steps"), plan.getLong("crafts"));
            color = GREEN;
        }
        clip(g, orderPop, status, 8, LIST_Y + LIST_ROWS * LIST_H + 6, 232, color, ox, oy);
        AndroidStationScreen.fitCentered(g, font, Component.translatable(trigger >= 0 ? "gui.singulo.work_console.register"
                : "gui.singulo.work_console.start"), START_X + 2, BUTTON_Y + 4, START_W - 4, canStart() ? 0xFFFFFFFF : 0xFF8A949E);
        Component trig = triggerLabel();
        int tw = Math.min(font.width(trig), TRIGGER_W - 6);
        clipped(g, trig, TRIGGER_X + (TRIGGER_W - tw) / 2, BUTTON_Y + 4, TRIGGER_W - 6, Panel.TEXT);    // 全文は重ねたときの説明に
        if (picking.is(Items.AIR)) {
            closePicking();
        }
    }
}
