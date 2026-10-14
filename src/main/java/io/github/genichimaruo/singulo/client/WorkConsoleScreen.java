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
 * アンドロイド作業指令台の画面。
 * <ul>
 * <li>注文: 注文できる物（作れる物には光る印）が並ぶ。選ぶと、個数・必要な素材・割り当てるアンドロイドを決める画面になる。</li>
 * <li>進行中: 注文ごとの進み具合と状態。取り消せる。</li>
 * <li>つながり: つないだ資材口・納品口・レシピ棚。外せる。許可証のスロットもここ。</li>
 * <li>記録: 3×3 に見本を置いて（物は減らない）、レシピカードに記録する。JEI の「+」でも置ける。</li>
 * <li>納品箱: 指令台に届いた完成品（ホッパーなどでも取り出せる）。</li>
 * </ul>
 * 注文の確認では、納品先（指令台か、名前の付いた納品口）を選ぶ。
 * 数は5桁まで小さな字で出し、10000 からは 10K・10M・10G… と縮めて出す。
 */
public class WorkConsoleScreen extends AbstractContainerScreen<WorkConsoleMenu> {
    private static final int TAB_Y = 16, TAB_W = 38, TAB_H = 13, TABS = 6, TAB_RECORD = 3, TAB_OUTBOX = 4, TAB_STANDING = 5;
    /** タブの並び（左から）: 注文・登録・進行中・つながり・記録・納品箱。 */
    private static final int[] TAB_ORDER = {0, TAB_STANDING, 1, 2, 3, 4};
    private static final String[] TAB_KEYS = {"order", "running", "links", "record", "outbox", "standing"};
    /** 注文の確認: きっかけのボタン（-1 は今すぐ注文、0〜 は登録。{@link StandingOrder}）。 */
    private static final int TRIGGER_X = 8, TRIGGER_W = 116;
    /** 注文の確認: 納品先を選ぶボタン。 */
    private static final int DEST_X = 132, DEST_Y = 36, DEST_W = 116, DEST_H = 14;
    private static final int TOP = 33;
    private static final int COLS = 13, ROWS = 5, CELL = 18;
    private static final int ROW_H = 18, ORDER_ROWS = 5;
    private static final int LINK_ROWS = 6, LINK_H = 13;
    private static final int LIST_Y = 70, LIST_ROWS = 7, LIST_H = 16;
    private static final int START_X = 132, START_W = 70, BACK_X = 208, BACK_W = 40, BUTTON_Y = 206, BUTTON_H = 16;
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
    /** 個数を変えたあと、計算を頼むまでの待ち（打つたびに頼まないように）。 */
    private int planDelay;
    @Nullable
    private CompoundTag plan;
    @Nullable
    private Component error;
    /** 進行中のページで、詳しく見ている注文（なければ -1）。 */
    private int detail = -1;
    private static final int DETAIL_ROWS = 5, DETAIL_H = 16, DETAIL_BACK_Y = TOP + DETAIL_ROWS * DETAIL_H + 4;
    /** 注文の確認で選んだきっかけ（-1: 今すぐ注文）。 */
    private int trigger = -1;
    /** 登録を送ったところ（返事で登録のページへ移る）。 */
    private boolean registering;
    private final Set<UUID> chosen = new HashSet<>();
    /** 選んだ納品先（様子の deliveries の番号）。 */
    private int destination;
    private int seenVersion = -1;

    public WorkConsoleScreen(WorkConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WorkConsoleMenu.WIDTH;
        imageHeight = WorkConsoleMenu.HEIGHT;
        inventoryLabelX = WorkConsoleMenu.INV_X;
        inventoryLabelY = WorkConsoleMenu.INV_Y - 11;
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
            send(WorkConsoleActionPayload.plan(picking, count, reg()));
        }
        menu.setVisibility(picking == null, tab == 2, tab == TAB_RECORD, tab == TAB_OUTBOX);
    }

    /** 記録のページを見せる（JEI の「+」で見本を置いたとき）。 */
    public void showRecordTab() {
        closePicking();
        tab = TAB_RECORD;
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

    private void openPicking(ItemStack item) {
        picking = item.copyWithCount(1);
        count = 1;
        plan = null;
        error = null;
        listScroll = 0;
        androidScroll = 0;
        chosen.clear();
        destination = 0;
        trigger = -1;
        countBox = new EditBox(font, leftPos + 26, topPos + 36, 46, 14, Component.empty());
        countBox.setMaxLength(4);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setValue("1");
        countBox.setResponder(s -> {
            count = s.isEmpty() ? 1 : Math.max(1, Math.min(WorkConsoleBlockEntity.MAX_COUNT, Long.parseLong(s)));
            planDelay = 6;
        });
        addRenderableWidget(countBox);
        send(WorkConsoleActionPayload.plan(picking, count, reg()));
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
        if (tab == TAB_RECORD && picking == null) {
            if (optionShown() && Panel.inside(mouseX, mouseY, leftPos + WorkConsoleMenu.OPTION_X, topPos + WorkConsoleMenu.OPTION_Y,
                    WorkConsoleMenu.OPTION_W, WorkConsoleMenu.OPTION_H)) {
                adjustOption(dy > 0, hasShiftDown());
                return true;
            }
            // 加工モードの見本: ホイールで数を変える（シフトで10ずつ）
            adjustHovered(dy > 0, hasShiftDown());
            return true;
        }
        int step = dy > 0 ? -1 : 1;
        if (picking != null) {
            if (mouseX < leftPos + 128) {
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
        int x = leftPos, y = topPos;
        if (picking != null) {
            if (countBox != null && countBox.mouseClicked(mouseX, mouseY, button)) {
                setFocused(countBox);
                return true;
            }
            long step = Screen.hasShiftDown() ? 10 : Screen.hasControlDown() ? 64 : 1;
            if (Panel.inside(mouseX, mouseY, x + 8, y + 36, 14, 14)) {
                addCount(-step);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + 76, y + 36, 14, 14)) {
                addCount(step);
                return true;
            }
            List<CompoundTag> androids = list("androids");
            for (int i = 0; i < LIST_ROWS; i++) {
                int idx = i + androidScroll;
                if (idx < androids.size() && Panel.inside(mouseX, mouseY, x + 132, y + LIST_Y + i * LIST_H, 116, LIST_H)) {
                    CompoundTag a = androids.get(idx);
                    UUID id = a.getUUID("id");
                    if (selectable(a) && !chosen.remove(id)) {
                        chosen.add(id);
                    }
                    return true;
                }
            }
            List<CompoundTag> deliveries = list("deliveries");
            if (Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H) && !deliveries.isEmpty()) {
                destination = (destination + (button == 1 ? deliveries.size() - 1 : 1)) % deliveries.size();
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H)) {
                // 今すぐ → 在庫を保つ → 信号で1回 → 信号の間だけ在庫を保つ（右クリックで逆順）
                int n = io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGERS + 1;
                trigger = Math.floorMod(trigger + 1 + (button == 1 ? -1 : 1), n) - 1;
                error = null;
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
                    send(WorkConsoleActionPayload.start(picking, count, startAndroids(), dest, reg()));
                }
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + BACK_X, y + BUTTON_Y, BACK_W, BUTTON_H)) {
                closePicking();
                return true;
            }
            return true;
        }
        for (int i = 0; i < TABS; i++) {
            if (Panel.inside(mouseX, mouseY, x + 8 + i * (TAB_W + 2), y + TAB_Y, TAB_W, TAB_H)) {
                tab = TAB_ORDER[i];
                scroll = 0;
                detail = -1;
                return true;
            }
        }
        if (tab == TAB_RECORD && minecraft.gameMode != null) {
            if (button == 2 && adjustHovered(!hasShiftDown(), false)) {
                return true;                   // ミドルクリックで1つ増やす（シフトで減らす）
            }
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.MODE_X, y + WorkConsoleMenu.MODE_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_MODE);
                return true;
            }
            if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.VARIANTS_X,
                    y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_VARIANTS);
                return true;
            }
            int face = hoveredFace(mouseX, mouseY);
            if (face >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        (button == 1 ? WorkConsoleMenu.BUTTON_FACE_BACK : WorkConsoleMenu.BUTTON_FACE) + face);
                return true;
            }
            if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE) {
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
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_CARD_X, y + WorkConsoleMenu.CLEAR_CARD_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_CLEAR_CARD);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ENCODE_X, y + WorkConsoleMenu.ENCODE_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_ENCODE);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_X, y + WorkConsoleMenu.CLEAR_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkConsoleMenu.BUTTON_CLEAR);
                return true;
            }
        }
        if (tab == 0) {
            List<CompoundTag> entries = list("entries");
            for (int i = 0; i < COLS * ROWS; i++) {
                int idx = i + scroll * COLS;
                int cx = x + 8 + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
                if (idx < entries.size() && Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                    openPicking(stack(entries.get(idx), "item"));
                    return true;
                }
            }
        } else if (tab == 1) {
            if (detailOrder() != null) {
                if (Panel.inside(mouseX, mouseY, x + 8, y + DETAIL_BACK_Y, 50, 12)) {
                    detail = -1;
                }
                return true;
            }
            List<CompoundTag> orders = list("orders");
            for (int i = 0; i < ORDER_ROWS; i++) {
                int idx = i + scroll;
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 232, y + TOP + i * ROW_H + 3, 12, 12)) {
                    send(WorkConsoleActionPayload.cancel(orders.get(idx).getInt("id")));
                    return true;
                }
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 8, y + TOP + i * ROW_H, 222, ROW_H - 1)) {
                    detail = orders.get(idx).getInt("id");     // 行をクリック: 詳しい様子を見る
                    return true;
                }
            }
        } else if (tab == TAB_STANDING) {
            List<CompoundTag> rows = list("standing");
            for (int i = 0; i < ORDER_ROWS; i++) {
                int idx = i + scroll;
                if (idx < rows.size() && Panel.inside(mouseX, mouseY, x + 232, y + TOP + i * ROW_H + 3, 12, 12)) {
                    send(WorkConsoleActionPayload.unregister(rows.get(idx).getInt("id")));
                    return true;
                }
            }
        } else if (tab == 2) {
            List<CompoundTag> links = list("links");
            for (int i = 0; i < LINK_ROWS; i++) {
                int idx = i + scroll;
                if (idx < links.size() && Panel.inside(mouseX, mouseY, x + 176, y + TOP + i * LINK_H, 40, LINK_H - 1)) {
                    send(WorkConsoleActionPayload.unlink(BlockPos.of(links.get(idx).getLong("pos"))));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean canStart() {
        if (trigger >= 0) {
            return picking != null;                // 登録は、今そろっていなくてもできる（そろったら始める）
        }
        return plan != null && plan.getBoolean("ok") && !startAndroids().isEmpty() && planDelay == 0;
    }

    /** 選べるアンドロイドか（作業中でなく、採掘の工程があれば採掘アップグレードを積んでいる）。 */
    private boolean selectable(CompoundTag a) {
        return !a.getBoolean("busy") && (plan == null || !plan.getBoolean("mining") || a.getBoolean("miner"))
                && (plan == null || !plan.getBoolean("felling") || a.getBoolean("feller"))
                && (plan == null || !plan.getBoolean("fluids") || a.getBoolean("tank"));
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
        super.render(g, mouseX, mouseY, partialTick);
        if (picking != null) {
            renderPickingTooltips(g, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        int x = leftPos, y = topPos;
        if (tab == 0) {
            List<CompoundTag> entries = list("entries");
            for (int i = 0; i < COLS * ROWS; i++) {
                int idx = i + scroll * COLS;
                int cx = x + 8 + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
                if (idx < entries.size() && Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                    CompoundTag e = entries.get(idx);
                    ItemStack s = stack(e, "item");
                    List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(s));
                    lines.add(Component.translatable("gui.singulo.work_console.stock", e.getLong("count")).withStyle(net.minecraft.ChatFormatting.GRAY));
                    if (e.getBoolean("craft")) {
                        lines.add(Component.translatable("gui.singulo.work_console.craftable").withStyle(net.minecraft.ChatFormatting.AQUA));
                    }
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        } else if (tab == 1 && detailOrder() == null) {
            List<CompoundTag> orders = list("orders");
            for (int i = 0; i < ORDER_ROWS; i++) {
                int idx = i + scroll;
                if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 232, y + TOP + i * ROW_H + 3, 12, 12)) {
                    g.renderTooltip(font, Component.translatable("gui.singulo.work_console.cancel"), mouseX, mouseY);
                } else if (idx < orders.size() && Panel.inside(mouseX, mouseY, x + 8, y + TOP + i * ROW_H, 222, ROW_H - 1)) {
                    g.renderTooltip(font, Component.translatable("gui.singulo.work_console.detail.hint"), mouseX, mouseY);
                }
            }
        } else if (tab == TAB_RECORD) {
            if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ENCODE_X, y + WorkConsoleMenu.ENCODE_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.encode.hint"), 160), mouseX, mouseY);
            } else if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_X, y + WorkConsoleMenu.CLEAR_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                g.renderTooltip(font, Component.translatable("gui.singulo.recipe_encoder.clear"), mouseX, mouseY);
            } else if (Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.CLEAR_CARD_X, y + WorkConsoleMenu.CLEAR_CARD_Y, WorkConsoleMenu.BUTTON_W,
                    WorkConsoleMenu.BUTTON_H)) {
                g.renderTooltip(font, Component.translatable("gui.singulo.recipe_encoder.clear_card.hint"), mouseX, mouseY);
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
            } else if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.KIND_X,
                    y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.KIND_W, WorkConsoleMenu.SMALL_H)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.kind.hint"), 180), mouseX, mouseY);
            } else if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.ADD_X,
                    y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.ADD_W, WorkConsoleMenu.SMALL_H)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.step.add.hint"), 180), mouseX, mouseY);
            } else if (hoveredSlot != null && !hoveredSlot.hasItem() && (hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_A
                    || hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_B || hoveredSlot.index == WorkConsoleMenu.SLOT_PITEM)) {
                String key = hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_A ? "loc_a" : hoveredSlot.index == WorkConsoleMenu.SLOT_LOC_B ? "loc_b" : "item";
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.step." + key + ".hint"), 180), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_RESULT && !hoveredSlot.hasItem()
                    && menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.product.hint"), 160), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index >= WorkConsoleMenu.SLOT_EXTRA && hoveredSlot.index < WorkConsoleMenu.SLOT_LOC_A
                    && !hoveredSlot.hasItem()) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.extra.hint"), 160), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_RESULT && !hoveredSlot.hasItem() && menu.processing()) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.output.hint"), 160), mouseX, mouseY);
            } else if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING && Panel.inside(mouseX, mouseY, x + WorkConsoleMenu.VARIANTS_X,
                    y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.variants.hint"), 180), mouseX, mouseY);
            } else if (hoveredFace(mouseX, mouseY) >= 0) {
                String[] keys = {"in", "out", "fuel"};
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.face_button." + keys[hoveredFace(mouseX, mouseY)]
                        + ".hint"), 180), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_FUEL && !hoveredSlot.hasItem()) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.fuel.hint"), 180), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index == WorkConsoleMenu.SLOT_STOCK && !hoveredSlot.hasItem()) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.stock.hint"), 160), mouseX, mouseY);
            } else if (hoveredSlot != null && hoveredSlot.index >= WorkConsoleMenu.SLOT_GRID && hoveredSlot.index < WorkConsoleMenu.SLOT_RESULT
                    && !hoveredSlot.hasItem()) {
                g.renderTooltip(font, font.split(Component.translatable("gui.singulo.recipe_encoder.grid.hint"), 160), mouseX, mouseY);
            }
        }
    }

    /** 加工モードの面のボタンのうち、指しているもの（0 入れる、1 取り出す、2 燃料。なければ -1）。 */
    private int hoveredFace(double mouseX, double mouseY) {
        if (!menu.processing() || tab != TAB_RECORD) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            if (Panel.inside(mouseX, mouseY, leftPos + WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP),
                    topPos + WorkConsoleMenu.FACE_Y, WorkConsoleMenu.FACE_W, WorkConsoleMenu.FACE_H)) {
                return i;
            }
        }
        return -1;
    }

    /** 面の名前（上・下・北・南・西・東、燃料は自動も）。 */
    private static Component faceName(int value) {
        return Component.translatable("gui.singulo.recipe_encoder.face." + (value == io.github.genichimaruo.singulo.android.work.RecipeData.FACE_AUTO
                ? "auto" : net.minecraft.core.Direction.from3DDataValue(value).getName()));
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
        int x = leftPos, y = topPos;
        if (Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H)) {
            String key = trigger < 0 ? "now" : io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trigger];
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.trigger." + key + ".hint"), 180), mouseX, mouseY);
            return;
        }
        List<CompoundTag> androids = list("androids");
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + androidScroll;
            if (idx < androids.size() && Panel.inside(mouseX, mouseY, x + 132, y + LIST_Y + i * LIST_H, 116, LIST_H)) {
                CompoundTag a = androids.get(idx);
                if (!a.getBoolean("busy") && !selectable(a)) {
                    boolean axe = plan != null && plan.getBoolean("felling") && !a.getBoolean("feller");
                    boolean tank = plan != null && plan.getBoolean("fluids") && !a.getBoolean("tank");
                    g.renderTooltip(font, font.split(Component.translatable(tank ? "gui.singulo.work_console.needs_tank"
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
        if (Panel.inside(mouseX, mouseY, x + 8, y + 36, 14, 14) || Panel.inside(mouseX, mouseY, x + 76, y + 36, 14, 14)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.count.hint"), 160), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H)) {
            g.renderTooltip(font, font.split(Component.translatable("gui.singulo.work_console.delivery.hint"), 160), mouseX, mouseY);
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
        Panel.background(g, x, y, imageWidth, imageHeight);
        if (picking != null) {
            renderPicking(g, x, y, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < TABS; i++) {
            int tx = x + 8 + i * (TAB_W + 2);
            boolean on = TAB_ORDER[i] == tab;
            g.fill(tx - 1, y + TAB_Y - 1, tx + TAB_W + 1, y + TAB_Y + TAB_H + 1, Panel.WELL_EDGE);
            g.fill(tx, y + TAB_Y, tx + TAB_W, y + TAB_Y + TAB_H,
                    on ? 0xFF3A8FB0 : Panel.inside(mouseX, mouseY, tx, y + TAB_Y, TAB_W, TAB_H) ? 0xFFD6E6EE : 0xFFF4F6F8);
        }
        for (var s : menu.slots) {
            if (s.isActive()) {
                Panel.slot(g, x + s.x, y + s.y);
            }
        }
        if (tab == 0) {
            renderEntries(g, x, y, mouseX, mouseY);
        } else if (tab == 1) {
            if (detailOrder() != null) {
                renderDetail(g, x, y, mouseX, mouseY);
            } else {
                renderOrders(g, x, y);
            }
        } else if (tab == 2) {
            renderLinks(g, x, y, mouseX, mouseY);
        } else if (tab == TAB_RECORD) {
            renderRecord(g, x, y, mouseX, mouseY);
        } else if (tab == TAB_STANDING) {
            renderStanding(g, x, y);
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
        int[][] buttons = {{WorkConsoleMenu.ENCODE_X, WorkConsoleMenu.ENCODE_Y, 1}, {WorkConsoleMenu.CLEAR_X, WorkConsoleMenu.CLEAR_Y, 0},
                {WorkConsoleMenu.CLEAR_CARD_X, WorkConsoleMenu.CLEAR_CARD_Y, 0}, {WorkConsoleMenu.MODE_X, WorkConsoleMenu.MODE_Y, 0}};
        for (int[] b : buttons) {
            button(g, x + b[0], y + b[1], WorkConsoleMenu.BUTTON_W, WorkConsoleMenu.BUTTON_H, b[2] == 1, mouseX, mouseY);
        }
        if (optionShown()) {
            button(g, x + WorkConsoleMenu.OPTION_X, y + WorkConsoleMenu.OPTION_Y, WorkConsoleMenu.OPTION_W, WorkConsoleMenu.OPTION_H, false, mouseX,
                    mouseY);
        }
        if (menu.processing()) {
            for (int i = 0; i < 3; i++) {
                button(g, x + WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP), y + WorkConsoleMenu.FACE_Y,
                        WorkConsoleMenu.FACE_W, WorkConsoleMenu.FACE_H, false, mouseX, mouseY);
            }
        }
        if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_CRAFTING) {
            button(g, x + WorkConsoleMenu.VARIANTS_X, y + WorkConsoleMenu.VARIANTS_Y, WorkConsoleMenu.VARIANTS_W, WorkConsoleMenu.VARIANTS_H,
                    menu.variants(), mouseX, mouseY);
        }
        if (procedure) {
            button(g, x + WorkConsoleMenu.KIND_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.KIND_W, WorkConsoleMenu.SMALL_H, false, mouseX, mouseY);
            button(g, x + WorkConsoleMenu.ADD_X, y + WorkConsoleMenu.SMALL_Y, WorkConsoleMenu.ADD_W, WorkConsoleMenu.SMALL_H, true, mouseX, mouseY);
        }
    }

    private static void button(GuiGraphics g, int bx, int by, int w, int h, boolean primary, int mouseX, int mouseY) {
        boolean hover = Panel.inside(mouseX, mouseY, bx, by, w, h);
        g.fill(bx - 1, by - 1, bx + w + 1, by + h + 1, Panel.WELL_EDGE);
        g.fill(bx, by, bx + w, by + h, primary ? (hover ? 0xFF4AA3C4 : 0xFF3A8FB0) : (hover ? 0xFFD6E6EE : 0xFFF4F6F8));
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
        g.renderOutline(x + WorkConsoleMenu.RESULT_X - 3, y + WorkConsoleMenu.RESULT_Y - 3, 22, 22, Panel.GLOW);
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
        if (menu.mode() != io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE) {
            return -1;
        }
        int lx = leftPos + WorkConsoleMenu.STEPS_X, ly = topPos + WorkConsoleMenu.STEPS_Y;
        if (!Panel.inside(mouseX, mouseY, lx, ly, WorkConsoleMenu.STEPS_W, WorkConsoleMenu.STEP_H * io.github.genichimaruo.singulo.android.work.RecipeData.MAX_STEPS)) {
            return -1;
        }
        int i = (int) ((mouseY - ly) / WorkConsoleMenu.STEP_H);
        return i < list("proc").size() ? i : -1;
    }

    private void renderEntries(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CompoundTag> entries = list("entries");
        int maxScroll = Math.max(0, (entries.size() + COLS - 1) / COLS - ROWS);
        scroll = Math.min(scroll, maxScroll);
        Panel.well(g, x + 8, y + TOP, COLS * CELL, ROWS * CELL);
        for (int i = 0; i < COLS * ROWS; i++) {
            int idx = i + scroll * COLS;
            if (idx >= entries.size()) {
                break;
            }
            CompoundTag e = entries.get(idx);
            int cx = x + 8 + (i % COLS) * CELL, cy = y + TOP + (i / COLS) * CELL;
            if (Panel.inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF52606C);
            }
            ItemStack s = stack(e, "item");
            g.renderItem(s, cx + 1, cy + 1);
            long n = e.getLong("count");
            g.renderItemDecorations(font, s, cx + 1, cy + 1, "");
            drawCount(g, cx + 1, cy + 1, n <= 0 ? "" : compact(n));
            if (e.getBoolean("craft")) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.fill(cx + 1, cy + 1, cx + 4, cy + 4, 0xFF78D2F0);     // 作れる印
                g.pose().popPose();
            }
        }
        if (maxScroll > 0) {
            int h = ROWS * CELL;
            int bar = Math.max(8, h / (maxScroll + 1));
            int by = y + TOP + (h - bar) * scroll / maxScroll;
            g.fill(x + 8 + COLS * CELL + 2, y + TOP, x + 8 + COLS * CELL + 5, y + TOP + h, Panel.SEAM);
            g.fill(x + 8 + COLS * CELL + 2, by, x + 8 + COLS * CELL + 5, by + bar, 0xFF3A8FB0);
        }
    }

    private void renderOrders(GuiGraphics g, int x, int y) {
        List<CompoundTag> orders = list("orders");
        scroll = Math.min(scroll, Math.max(0, orders.size() - ORDER_ROWS));
        for (int i = 0; i < ORDER_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= orders.size()) {
                break;
            }
            CompoundTag o = orders.get(idx);
            int ry = y + TOP + i * ROW_H;
            g.fill(x + 8, ry, x + 248, ry + ROW_H - 1, 0xFFF4F6F8);
            g.renderItem(stack(o, "target"), x + 9, ry + 1);
            Panel.well(g, x + 160, ry + 6, 66, 5);
            g.fill(x + 160, ry + 6, x + 160 + 66 * o.getInt("progress") / 1000, ry + 11, GREEN);
            g.fill(x + 232, ry + 3, x + 244, ry + 15, 0xFFE8B0B0);
        }
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
        Panel.well(g, x + 132, y + TOP, 116, DETAIL_ROWS * DETAIL_H);
        List<CompoundTag> steps = tags(o, "steps");
        for (int i = 0; i < steps.size() && i < DETAIL_ROWS - 1; i++) {
            g.renderItem(stack(steps.get(i), "item"), x + 9, y + TOP + i * DETAIL_H);
        }
        int last = Math.min(steps.size(), DETAIL_ROWS - 1);
        g.renderItem(stack(o, "target"), x + 9, y + TOP + last * DETAIL_H);
        List<CompoundTag> workers = tags(o, "worker_list");
        for (int i = 0; i < workers.size() && i < DETAIL_ROWS; i++) {
            int ry = y + TOP + i * DETAIL_H;
            g.fill(x + 135, ry + 4, x + 143, ry + 12, workers.get(i).getInt("activity") < 0 ? 0xFF8A949E
                    : 0xFF000000 | workers.get(i).getInt("accent"));
        }
        boolean hover = Panel.inside(mouseX, mouseY, x + 8, y + DETAIL_BACK_Y, 50, 12);
        g.fill(x + 7, y + DETAIL_BACK_Y - 1, x + 59, y + DETAIL_BACK_Y + 13, Panel.WELL_EDGE);
        g.fill(x + 8, y + DETAIL_BACK_Y, x + 58, y + DETAIL_BACK_Y + 12, hover ? 0xFFD6E6EE : 0xFFF4F6F8);
    }

    private static List<CompoundTag> tags(CompoundTag t, String key) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag e : t.getList(key, Tag.TAG_COMPOUND)) {
            out.add((CompoundTag) e);
        }
        return out;
    }

    /** 登録のページ: 登録した注文の行（物・きっかけ・様子、右端の × で消す）。 */
    private void renderStanding(GuiGraphics g, int x, int y) {
        List<CompoundTag> rows = list("standing");
        scroll = Math.min(scroll, Math.max(0, rows.size() - ORDER_ROWS));
        for (int i = 0; i < ORDER_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= rows.size()) {
                break;
            }
            int ry = y + TOP + i * ROW_H;
            g.fill(x + 8, ry, x + 248, ry + ROW_H - 1, 0xFFF4F6F8);
            g.renderItem(stack(rows.get(idx), "item"), x + 9, ry + 1);
            g.fill(x + 232, ry + 3, x + 244, ry + 15, 0xFFE8B0B0);
        }
    }

    private void renderLinks(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CompoundTag> links = list("links");
        scroll = Math.min(scroll, Math.max(0, links.size() - LINK_ROWS));
        for (int i = 0; i < LINK_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= links.size()) {
                break;
            }
            int ry = y + TOP + i * LINK_H;
            g.fill(x + 8, ry, x + 218, ry + LINK_H - 1, 0xFFF4F6F8);
            boolean hover = Panel.inside(mouseX, mouseY, x + 176, ry, 40, LINK_H - 1);
            g.fill(x + 176, ry, x + 216, ry + LINK_H - 1, hover ? 0xFFE8B0B0 : 0xFFE6D6D6);
        }
    }

    private void renderPicking(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        g.renderItem(picking, x + 8, y + 15);
        g.fill(x + 7, y + 35, x + 23, y + 51, Panel.WELL_EDGE);
        g.fill(x + 8, y + 36, x + 22, y + 50, Panel.inside(mouseX, mouseY, x + 8, y + 36, 14, 14) ? 0xFFD6E6EE : 0xFFF4F6F8);
        g.fill(x + 75, y + 35, x + 91, y + 51, Panel.WELL_EDGE);
        g.fill(x + 76, y + 36, x + 90, y + 50, Panel.inside(mouseX, mouseY, x + 76, y + 36, 14, 14) ? 0xFFD6E6EE : 0xFFF4F6F8);
        Panel.well(g, x + 8, y + LIST_Y, 116, LIST_ROWS * LIST_H);
        Panel.well(g, x + 132, y + LIST_Y, 116, LIST_ROWS * LIST_H);
        g.fill(x + DEST_X - 1, y + DEST_Y - 1, x + DEST_X + DEST_W + 1, y + DEST_Y + DEST_H + 1, Panel.WELL_EDGE);
        g.fill(x + DEST_X, y + DEST_Y, x + DEST_X + DEST_W, y + DEST_Y + DEST_H,
                Panel.inside(mouseX, mouseY, x + DEST_X, y + DEST_Y, DEST_W, DEST_H) ? 0xFFD6E6EE : 0xFFF4F6F8);
        g.fill(x + TRIGGER_X - 1, y + BUTTON_Y - 1, x + TRIGGER_X + TRIGGER_W + 1, y + BUTTON_Y + BUTTON_H + 1, Panel.WELL_EDGE);
        g.fill(x + TRIGGER_X, y + BUTTON_Y, x + TRIGGER_X + TRIGGER_W, y + BUTTON_Y + BUTTON_H,
                Panel.inside(mouseX, mouseY, x + TRIGGER_X, y + BUTTON_Y, TRIGGER_W, BUTTON_H) ? 0xFFD6E6EE
                        : trigger >= 0 ? 0xFFDCEAF2 : 0xFFF4F6F8);
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
            g.fill(x + 134, ry + 3, x + 144, ry + 13, Panel.WELL_EDGE);
            g.fill(x + 135, ry + 4, x + 143, ry + 12, on ? accent : can ? 0xFFF4F6F8 : 0xFF8A949E);
        }
        boolean start = canStart();
        g.fill(x + START_X - 1, y + BUTTON_Y - 1, x + START_X + START_W + 1, y + BUTTON_Y + BUTTON_H + 1, Panel.WELL_EDGE);
        g.fill(x + START_X, y + BUTTON_Y, x + START_X + START_W, y + BUTTON_Y + BUTTON_H, start ? 0xFF3A8FB0 : 0xFFC4CBD2);
        g.fill(x + BACK_X - 1, y + BUTTON_Y - 1, x + BACK_X + BACK_W + 1, y + BUTTON_Y + BUTTON_H + 1, Panel.WELL_EDGE);
        g.fill(x + BACK_X, y + BUTTON_Y, x + BACK_X + BACK_W, y + BUTTON_Y + BUTTON_H,
                Panel.inside(mouseX, mouseY, x + BACK_X, y + BUTTON_Y, BACK_W, BUTTON_H) ? 0xFFD6E6EE : 0xFFF4F6F8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (picking != null) {
            renderPickingLabels(g);
            return;
        }
        AndroidStationScreen.fit(g, font, title, 8, 5, imageWidth - 16, Panel.TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        for (int i = 0; i < TABS; i++) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.tab." + TAB_KEYS[TAB_ORDER[i]]),
                    8 + i * (TAB_W + 2) + 2, TAB_Y + 3, TAB_W - 4, TAB_ORDER[i] == tab ? 0xFFFFFFFF : Panel.TEXT);
        }
        CompoundTag v = menu.view();
        Component message = v != null && v.contains("message") ? text(v, "message") : null;
        if (tab == 0) {
            if (list("entries").isEmpty()) {
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.empty"), 8, TOP + 38, COLS * CELL,
                        0xFFBFC8D0);
            }
            if (message != null) {
                AndroidStationScreen.fit(g, font, message, 8, TOP + ROWS * CELL + 4, 240, MUTED);
            }
        } else if (tab == 1 && detailOrder() != null) {
            CompoundTag o = detailOrder();
            List<CompoundTag> steps = tags(o, "steps");
            int light = 0xFFE9EDF0;
            for (int i = 0; i < steps.size() && i < DETAIL_ROWS - 1; i++) {
                CompoundTag st = steps.get(i);
                long done = st.getLong("done"), total = st.getLong("total"), doing = st.getLong("doing");
                Component line = Component.literal(compact(done) + " / " + compact(total));
                if (doing > 0) {
                    line = line.copy().append(Component.translatable("gui.singulo.work_console.detail.doing", compact(doing)));
                }
                AndroidStationScreen.fit(g, font, line, 27, TOP + i * DETAIL_H + 4, 95, done >= total ? 0xFF78D2F0 : light);
            }
            int last = Math.min(steps.size(), DETAIL_ROWS - 1);
            Component deliver = o.getBoolean("to_stock")
                    ? Component.translatable("gui.singulo.work_console.detail.to_stock", compact(o.getLong("delivered")), compact(o.getLong("count")))
                    : Component.translatable("gui.singulo.work_console.detail.delivered", compact(o.getLong("delivered")), compact(o.getLong("count")));
            AndroidStationScreen.fit(g, font, deliver, 27, TOP + last * DETAIL_H + 4, 95, light);
            List<CompoundTag> workers = tags(o, "worker_list");
            for (int i = 0; i < workers.size() && i < DETAIL_ROWS; i++) {
                CompoundTag w = workers.get(i);
                int act = w.getInt("activity");
                Component line = text(w, "name").copy();
                if (act >= 0) {
                    line = line.copy().append(": ").append(Component.translatable("gui.singulo.work_console.activity."
                            + io.github.genichimaruo.singulo.android.RuinAndroid.ACTIVITY_KEYS[Math.min(act, io.github.genichimaruo.singulo.android.RuinAndroid.ACTIVITY_KEYS.length - 1)]));
                }
                AndroidStationScreen.fit(g, font, line, 147, TOP + i * DETAIL_H + 4, 99, act < 0 ? MUTED : light);
            }
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.back"), 10, DETAIL_BACK_Y + 2, 46, Panel.TEXT);
            AndroidStationScreen.fit(g, font, stack(o, "target").getHoverName().copy().append(" ×" + compact(o.getLong("count"))), 64,
                    DETAIL_BACK_Y + 2, 184, Panel.TEXT);
        } else if (tab == 1) {
            List<CompoundTag> orders = list("orders");
            if (orders.isEmpty()) {
                AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.no_orders"), 10, TOP + 4, 236, MUTED);
            }
            for (int i = 0; i < ORDER_ROWS; i++) {
                int idx = i + scroll;
                if (idx >= orders.size()) {
                    break;
                }
                CompoundTag o = orders.get(idx);
                int ry = TOP + i * ROW_H;
                AndroidStationScreen.fit(g, font, stack(o, "target").getHoverName().copy().append(" ×" + compact(o.getLong("count"))), 28, ry + 1, 128,
                        Panel.TEXT);
                int status = o.getInt("status");
                AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.status." + status, o.getInt("workers")), 28,
                        ry + 10, 128, status == WorkOrder.RUNNING ? GREEN : 0xFFC08020);
                g.drawString(font, "×", 235, ry + 5, 0xFF803030, false);
            }
            if (message != null) {
                AndroidStationScreen.fit(g, font, message, 8, TOP + ORDER_ROWS * ROW_H + 2, 240, MUTED);
            }
        } else if (tab == TAB_STANDING) {
            List<CompoundTag> rows = list("standing");
            if (rows.isEmpty()) {
                AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.standing.none"), 10, TOP + 4, 236, MUTED);
            }
            for (int i = 0; i < ORDER_ROWS; i++) {
                int idx = i + scroll;
                if (idx >= rows.size()) {
                    break;
                }
                CompoundTag s = rows.get(idx);
                int ry = TOP + i * ROW_H;
                int trig = s.getInt("trigger");
                Component head = stack(s, "item").getHoverName().copy().append(" ×" + compact(s.getLong("count")));
                AndroidStationScreen.fit(g, font, head, 28, ry + 1, 120, Panel.TEXT);
                AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.trigger." + io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trig]), 150,
                        ry + 1, 78, 0xFF3A8FB0);
                int state = s.getInt("state");
                Component line = state == io.github.genichimaruo.singulo.android.work.StandingOrder.WAITING && s.contains("reason")
                        ? Component.translatable("gui.singulo.work_console.standing.waiting", text(s, "reason"))
                        : Component.translatable("gui.singulo.work_console.standing.state." + state);
                if (trig != io.github.genichimaruo.singulo.android.work.StandingOrder.PULSE) {
                    line = line.copy().append(Component.translatable("gui.singulo.work_console.standing.stock", compact(s.getLong("stock"))));
                }
                AndroidStationScreen.fit(g, font, line, 28, ry + 10, 200, state == io.github.genichimaruo.singulo.android.work.StandingOrder.RUNNING ? GREEN
                        : state == io.github.genichimaruo.singulo.android.work.StandingOrder.WAITING ? 0xFFC08020 : MUTED);
                g.drawString(font, "×", 235, ry + 5, 0xFF803030, false);
            }
            CompoundTag view = menu.view();
            boolean on = view != null && view.getBoolean("powered");
            AndroidStationScreen.fit(g, font, Component.translatable(on ? "gui.singulo.work_console.signal.on" : "gui.singulo.work_console.signal.off"),
                    8, TOP + ORDER_ROWS * ROW_H + 2, 240, on ? 0xFFD04848 : MUTED);
        } else if (tab == TAB_OUTBOX) {
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.outbox.hint"), 8, TOP + 1, 240, MUTED);
        } else if (tab == TAB_RECORD) {
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.encode"), WorkConsoleMenu.ENCODE_X + 2,
                    WorkConsoleMenu.ENCODE_Y + 3, WorkConsoleMenu.BUTTON_W - 4, 0xFFFFFFFF);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.clear.short"), WorkConsoleMenu.CLEAR_X + 2,
                    WorkConsoleMenu.CLEAR_Y + 3, WorkConsoleMenu.BUTTON_W - 4, Panel.TEXT);
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.clear_card"),
                    WorkConsoleMenu.CLEAR_CARD_X + 2, WorkConsoleMenu.CLEAR_CARD_Y + 3, WorkConsoleMenu.BUTTON_W - 4, Panel.TEXT);
            String[] modes = {"crafting", "processing", "procedure"};
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.mode." + modes[Math.floorMod(menu.mode(), 3)]),
                    WorkConsoleMenu.MODE_X + 2, WorkConsoleMenu.MODE_Y + 3, WorkConsoleMenu.BUTTON_W - 4, Panel.TEXT);
            small(g, Component.translatable("gui.singulo.recipe_encoder.card"), WorkConsoleMenu.CARD_X + 8, WorkConsoleMenu.CARD_Y - 8, 26, MUTED);
            if (menu.processing()) {
                small(g, Component.translatable("gui.singulo.recipe_encoder.fuel"), WorkConsoleMenu.FUEL_X + 8, WorkConsoleMenu.FUEL_Y - 9, 22, MUTED);
            }
            small(g, Component.translatable("gui.singulo.recipe_encoder.stock"), WorkConsoleMenu.STOCK_X + 8, WorkConsoleMenu.STOCK_Y - 8, 26, MUTED);
            if (menu.mode() == io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity.MODE_PROCEDURE) {
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.kind."
                        + io.github.genichimaruo.singulo.android.work.ProcedureStep.KEYS[Math.floorMod(menu.stepKind(), io.github.genichimaruo.singulo.android.work.ProcedureStep.KINDS)]), WorkConsoleMenu.KIND_X + 1, WorkConsoleMenu.SMALL_Y + 2,
                        WorkConsoleMenu.KIND_W - 2, Panel.TEXT);
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.recipe_encoder.step.add"), WorkConsoleMenu.ADD_X + 1,
                        WorkConsoleMenu.SMALL_Y + 2, WorkConsoleMenu.ADD_W - 2, 0xFFFFFFFF);
                if (optionShown()) {
                    AndroidStationScreen.fitCentered(g, font, io.github.genichimaruo.singulo.android.work.ProcedureCardItem.option(menu.stepKind(), menu.stepOption()),
                            WorkConsoleMenu.OPTION_X + 1, WorkConsoleMenu.OPTION_Y + 2, WorkConsoleMenu.OPTION_W - 2, Panel.TEXT);
                }
            } else {
                if (menu.processing()) {
                    // 面のボタン: 「入 上」「出 下」「燃 自動」
                    String[] keys = {"in", "out", "fuel"};
                    for (int i = 0; i < 3; i++) {
                        int fv = menu.faces() >> (i * 3) & 7;
                        small(g, Component.translatable("gui.singulo.recipe_encoder.face_button." + keys[i], faceName(fv)),
                                WorkConsoleMenu.FACE_X + i * (WorkConsoleMenu.FACE_W + WorkConsoleMenu.FACE_GAP) + WorkConsoleMenu.FACE_W / 2,
                                WorkConsoleMenu.FACE_Y + 3, WorkConsoleMenu.FACE_W - 2, Panel.TEXT);
                    }
                } else {
                    small(g, Component.translatable(menu.variants() ? "gui.singulo.recipe_encoder.variants.on" : "gui.singulo.recipe_encoder.variants.off"),
                            WorkConsoleMenu.VARIANTS_X + WorkConsoleMenu.VARIANTS_W / 2, WorkConsoleMenu.VARIANTS_Y + 3, WorkConsoleMenu.VARIANTS_W - 4,
                            menu.variants() ? 0xFFFFFFFF : Panel.TEXT);
                }
            }
        } else {
            List<CompoundTag> links = list("links");
            if (links.isEmpty()) {
                AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.no_links"), 10, TOP + 2, 206, MUTED);
            }
            for (int i = 0; i < LINK_ROWS; i++) {
                int idx = i + scroll;
                if (idx >= links.size()) {
                    break;
                }
                CompoundTag l = links.get(idx);
                BlockPos p = BlockPos.of(l.getLong("pos"));
                int kind = l.getInt("kind");
                Component name = Component.translatable(kind < 0 ? "gui.singulo.work_console.link.lost" : "gui.singulo.work_console.link." + kind);
                AndroidStationScreen.fit(g, font, name.copy().append("  " + p.getX() + ", " + p.getY() + ", " + p.getZ()), 10, TOP + i * LINK_H + 2,
                        164, kind < 0 ? RED : Panel.TEXT);
                AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.unlink"), 177, TOP + i * LINK_H + 2, 38,
                        0xFF803030);
            }
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.android_station.permit"), WorkConsoleMenu.PERMIT_X - 12,
                    WorkConsoleMenu.PERMIT_Y - 10, 40, MUTED);
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.link.hint"), 8, TOP + LINK_ROWS * LINK_H + 4, 210,
                    MUTED);
        }
    }

    private void renderPickingLabels(GuiGraphics g) {
        AndroidStationScreen.fit(g, font, picking.getHoverName(), 28, 19, 220, Panel.TEXT);
        g.drawString(font, "−", 12, 39, Panel.TEXT, false);
        g.drawString(font, "+", 80, 39, Panel.TEXT, false);
        List<CompoundTag> deliveries = list("deliveries");
        if (!deliveries.isEmpty()) {
            CompoundTag d = deliveries.get(Math.min(destination, deliveries.size() - 1));
            AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.delivery", text(d, "name")), DEST_X + 2,
                    DEST_Y + 3, DEST_W - 4, Panel.TEXT);
        }
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.materials"), 8, LIST_Y - 11, 116, Panel.TEXT);
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.androids"), 132, LIST_Y - 11, 116, Panel.TEXT);
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
                AndroidStationScreen.fit(g, font, made, 27, LIST_Y + i * LIST_H + 4, 95, 0xFF78D2F0);
                continue;
            }
            boolean short_ = m.getLong("missing") > 0;
            AndroidStationScreen.fit(g, font, Component.literal(compact(m.getLong("count")) + " / " + compact(m.getLong("have"))), 27,
                    LIST_Y + i * LIST_H + 4, 95,
                    short_ ? RED : 0xFFE9EDF0);
        }
        List<CompoundTag> androids = list("androids");
        if (androids.isEmpty()) {
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.work_console.no_androids"), 134, LIST_Y + 4, 112, 0xFFBFC8D0);
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
            AndroidStationScreen.fit(g, font, name, 148, LIST_Y + i * LIST_H + 4, 98, selectable(a) ? 0xFFE9EDF0 : MUTED);
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
        AndroidStationScreen.fit(g, font, status, 8, LIST_Y + LIST_ROWS * LIST_H + 6, 240, color);
        AndroidStationScreen.fitCentered(g, font, Component.translatable(trigger >= 0 ? "gui.singulo.work_console.register"
                : "gui.singulo.work_console.start"), START_X + 2, BUTTON_Y + 4, START_W - 4, canStart() ? 0xFFFFFFFF : 0xFF8A949E);
        AndroidStationScreen.fitCentered(g, font, Component.translatable(trigger < 0 ? "gui.singulo.work_console.trigger.now"
                : "gui.singulo.work_console.trigger." + io.github.genichimaruo.singulo.android.work.StandingOrder.TRIGGER_KEYS[trigger]), TRIGGER_X + 2, BUTTON_Y + 4, TRIGGER_W - 4, Panel.TEXT);
        AndroidStationScreen.fitCentered(g, font, Component.translatable("gui.singulo.work_console.back"), BACK_X + 2, BUTTON_Y + 4, BACK_W - 4,
                Panel.TEXT);
        if (picking.is(Items.AIR)) {
            closePicking();
        }
    }
}
