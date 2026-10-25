package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import io.github.genichimaruo.singulo.genesis.GenesisCoreMenu;
import io.github.genichimaruo.singulo.genesis.MassSynthesis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 創世炉の画面（宇宙のスキン）。左に「生まれかけの宇宙」をのぞく窓、右に今の段階と進み（アイコン・バー・%）、
 * 作る物の枠と出来上がりのスロット。投入のスイッチはヘッダー右上（ほかの装置の電源と同じ場所）。
 * <ul>
 *   <li>窓: 揺籃は種を待つ小さな光、種火は膨らむ火の玉、安定化は渦を巻く原始の宇宙（暴走の兆しで赤く震える）、
 *       誕生は閃光、完成は自分の銀河がゆっくり回る</li>
 *   <li>暴走の兆しが来ると、窓に赤い帯で「投入を止めろ」と出る（止めていれば水色の「抑えている」）</li>
 *   <li>次にすることや電力の量は、段階の名前やバーに重ねると出る</li>
 * </ul>
 * 様子は核のブロックエンティティ（クライアントに同期されている写し）から読む。
 */
public class GenesisCoreScreen extends FramedScreen<GenesisCoreMenu> {
    private static final int GOLD = 0xFFF2CC6A;
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int EMBER = 0xFFFF9A4A;
    private static final int RED = 0xFFFF5A5A;
    private static final int OX = GenesisCoreMenu.CONTENT_X;
    /** 窓（宇宙をのぞく）の位置と大きさ。 */
    private static final int VIEW_X = 8, VIEW_Y = 22, VIEW = 84;
    /** 右の欄の左端と、バーの幅。 */
    private static final int PX = 100, BAR_W = 84;

    public GenesisCoreScreen(GenesisCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GenesisCoreMenu.WIDTH, GenesisCoreMenu.HEIGHT);
        skin = GuiSkin.COSMIC;
    }

    private GenesisCoreBlockEntity core() {
        return minecraft == null || minecraft.player == null ? null : menu.core(minecraft.player);
    }

    private int stage() {
        GenesisCoreBlockEntity core = core();
        return core == null ? GenesisCoreBlockEntity.UNFORMED : core.stage();
    }

    private float time(float partialTick) {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + partialTick : 0;
    }

    // ------------------------------------------------------------------ ヘッダー（段階・投入）

    @Override
    protected Panel.Status status() {
        GenesisCoreBlockEntity core = core();
        return switch (stage()) {
            case GenesisCoreBlockEntity.UNFORMED -> Panel.Status.BROKEN;
            case GenesisCoreBlockEntity.CRADLE -> Panel.Status.IDLE;
            case GenesisCoreBlockEntity.STABILIZING -> core != null && core.surgeTicks() > 0 ? Panel.Status.WARN : Panel.Status.RUN;
            default -> Panel.Status.RUN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case BROKEN -> GuiSkin.BAD;
            case WARN -> GuiSkin.WARN;
            case IDLE -> GuiSkin.IDLE;
            default -> GuiSkin.OK;
        };
    }

    @Override
    protected Component statusText() {
        return Component.translatable("gui.singulo.genesis.phase." + stage());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.singulo.genesis.hint." + stage()).withStyle(st -> st.withColor(0xFF8A949E)));
    }

    @Override
    protected Boolean powered() {
        GenesisCoreBlockEntity core = core();
        return core != null && core.canToggleFeed() ? !core.manualStop() : null;
    }

    @Override
    protected void togglePower() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GenesisCoreMenu.BUTTON_FEED);
        }
    }

    @Override
    protected List<Component> powerTooltip() {
        GenesisCoreBlockEntity core = core();
        List<Component> lines = new ArrayList<>();
        if (core == null) {
            return lines;
        }
        String key = core.manualStop() ? "gui.singulo.genesis.screen.feed_off"
                : core.feeding() ? "gui.singulo.genesis.screen.feed_on" : "gui.singulo.genesis.screen.feed_redstone";
        lines.add(Component.translatable(key).withStyle(st -> st.withColor(core.feeding() ? 0xFF7CE0A0 : 0xFFFF9090)));
        lines.add(Component.translatable("gui.singulo.genesis.screen.feed_tip"));
        if (core.stage() == GenesisCoreBlockEntity.STABILIZING) {
            // 止めても危険が全部止まるわけではないことを、押す前に知らせる
            lines.add(Component.translatable("gui.singulo.genesis.screen.feed_tip_stabilizing").withStyle(st -> st.withColor(0xFF8A949E)));
        }
        return lines;
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        Slot out = menu.getSlot(0);
        if (out.isActive() && !out.hasItem() && Panel.inside(mouseX, mouseY, leftPos + out.x, topPos + out.y, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.genesis.screen.output"), mouseX, mouseY);
            return;
        }
        Slot pick = menu.getSlot(GenesisCoreMenu.TEMPLATE_SLOT);
        if (pick.isActive() && Panel.inside(mouseX, mouseY, leftPos + pick.x, topPos + pick.y, 16, 16)) {
            List<Component> lines = new ArrayList<>();
            if (!pick.hasItem()) {
                lines.add(Component.translatable("gui.singulo.genesis.screen.no_template"));
            }
            lines.add(Component.translatable("gui.singulo.genesis.screen.template_tip_1"));
            lines.add(Component.translatable("gui.singulo.genesis.screen.template_tip_2").withStyle(st -> st.withColor(0xFF8A949E)));
            Panel.tooltip(g, font, lines, 200, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos + OX;
        int y = topPos;
        float t = time(partialTick);
        GenesisCoreBlockEntity core = core();
        // 窓
        int vx = x + VIEW_X;
        int vy = y + VIEW_Y;
        Panel.inset(g, skin, vx - 1, vy - 1, VIEW + 2, VIEW + 2, 0xFF03040A);
        g.enableScissor(vx, vy, vx + VIEW, vy + VIEW);
        if (core != null) {
            drawCosmos(g, core, vx + VIEW / 2, vy + VIEW / 2, t);
        }
        g.disableScissor();
        // スロット
        for (Slot s : menu.slots) {
            if (!s.isActive() || s.index > GenesisCoreMenu.TEMPLATE_SLOT) {
                continue;
            }
            Panel.slot(g, skin, leftPos + s.x, topPos + s.y);
            g.renderOutline(leftPos + s.x - 2, topPos + s.y - 2, 20, 20, s.index == 0 ? GOLD : MAGENTA);
        }
        if (core != null) {
            drawPanel(g, core);
        }
    }

    /** 右の欄: 段階の名前と、段階ごとの進み（アイコン・バー・%）。説明は重ねると出る。 */
    private void drawPanel(GuiGraphics g, GenesisCoreBlockEntity core) {
        int stage = core.stage();
        Component phase = Component.translatable("gui.singulo.genesis.phase." + stage);
        stat(g, OX + PX, 24, Panel.Icon.INFO, phaseColor(stage), phase.getString(), phaseColor(stage),
                List.of(phase, Component.translatable("gui.singulo.genesis.hint." + stage).withStyle(st -> st.withColor(0xFF8A949E))));
        String intake = GenesisCoreBlockEntity.formatFe(core.intake());
        Component intakeLine = Component.translatable("gui.singulo.genesis.screen.intake", intake);
        switch (stage) {
            case GenesisCoreBlockEntity.KINDLING -> {
                barRow(g, 40, Panel.Icon.FLAME, EMBER, core.chargeFraction(), List.of(
                        Component.translatable("gui.singulo.genesis.screen.charge"),
                        Component.literal(GenesisCoreBlockEntity.formatFe(core.chargeFraction() * GenesisCoreBlockEntity.seedEnergy())
                                + " / " + GenesisCoreBlockEntity.formatFe(GenesisCoreBlockEntity.seedEnergy()))));
                stat(g, OX + PX, 54, Panel.Icon.BOLT, Panel.GLOW, intake + "/t", skin.text(), List.of(intakeLine,
                        Component.translatable("gui.singulo.genesis.screen.intake_cap",
                                GenesisCoreBlockEntity.formatFe(GenesisCoreBlockEntity.maxInput())).withStyle(st -> st.withColor(0xFF8A949E))));
            }
            case GenesisCoreBlockEntity.STABILIZING -> {
                barRow(g, 40, Panel.Icon.STAR, CYAN, core.progressFraction(),
                        List.of(Component.translatable("gui.singulo.genesis.screen.progress")));
                float k = core.instability() / 100F;
                barRow(g, 52, Panel.Icon.FLAME, core.instability() > 60 ? RED : skin.dim(), k,
                        Mth.hsvToRgb(0.33F * (1 - k), 0.8F, 1) | 0xFF000000, List.of(Component.translatable("gui.singulo.genesis.screen.instability")));
                boolean enough = core.intake() + 1 >= GenesisCoreBlockEntity.upkeep();
                stat(g, OX + PX, 66, Panel.Icon.BOLT, enough ? Panel.GLOW : RED, intake + "/t", enough ? skin.text() : RED, List.of(intakeLine));
            }
            case GenesisCoreBlockEntity.BIRTH -> barRow(g, 40, Panel.Icon.STAR, GOLD,
                    Math.min(1, core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_TICKS),
                    List.of(Component.translatable("gui.singulo.genesis.screen.birth")));
            case GenesisCoreBlockEntity.COMPLETE -> {
                ItemStack template = core.template();
                MassSynthesis.Entry e = MassSynthesis.INSTANCE.entry(template);
                if (!template.isEmpty() && e != null) {
                    String cost = GenesisCoreBlockEntity.formatFe(MassSynthesis.cost(e));
                    stat(g, GenesisCoreMenu.TEMPLATE_X + 22, GenesisCoreMenu.TEMPLATE_Y + 4, Panel.Icon.BOLT, Panel.GLOW, cost, skin.text(),
                            List.of(template.getHoverName(), Component.translatable("gui.singulo.genesis.screen.cost", cost)));
                    barRow(g, 74, Panel.Icon.STAR, MAGENTA, core.synthFraction(),
                            List.of(Component.translatable("gui.singulo.genesis.screen.synth", percent(core.synthFraction()))));
                }
                stat(g, OX + PX, 86, Panel.Icon.BOLT, Panel.GLOW, intake + "/t", skin.text(), List.of(intakeLine));
            }
            default -> { }
        }
    }

    /** アイコン・横長のバー・% の1行（右の欄）。 */
    private void barRow(GuiGraphics g, int y, Panel.Icon icon, int color, float fraction, List<Component> tooltip) {
        barRow(g, y, icon, color, fraction, color, tooltip);
    }

    private void barRow(GuiGraphics g, int y, Panel.Icon icon, int iconColor, float fraction, int barColor, List<Component> tooltip) {
        statBar(g, OX + PX, y, icon, iconColor, BAR_W, fraction, barColor, tooltip);
        String pct = percent(fraction);
        g.drawString(font, pct, leftPos + OX + PX + 11 + BAR_W + 4, topPos + y, skin.text(), false);
    }

    // ------------------------------------------------------------------ 窓の中の宇宙

    private static void drawCosmos(GuiGraphics g, GenesisCoreBlockEntity core, int cx, int cy, float t) {
        switch (core.stage()) {
            case GenesisCoreBlockEntity.UNFORMED -> dust(g, cx, cy, t, 0x40, 30);
            case GenesisCoreBlockEntity.CRADLE -> {
                dust(g, cx, cy, t, 0x60, 30);
                float pulse = 0.5F + 0.5F * Mth.sin(t * 0.1F);
                glow(g, cx, cy, 2 + 2 * pulse, 0xFFF4D890, 0.6F + 0.4F * pulse);
            }
            case GenesisCoreBlockEntity.KINDLING -> {
                float f = core.chargeFraction();
                dust(g, cx, cy, t, 0x70, 30);
                // 吸い込まれていく光の粒
                for (int i = 0; i < 24; i++) {
                    float a = i * 2.4F + t * 0.02F;
                    float r = 38 - ((t * 0.6F + i * 13) % 34);
                    plot(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, 0xFFFFC080, 0.3F + 0.7F * (1 - r / 38));
                }
                float flicker = 0.9F + 0.1F * Mth.sin(t * 0.7F);
                glow(g, cx, cy, (3 + 13 * f) * flicker, EMBER, 1);
                glow(g, cx, cy, (1.5F + 6 * f) * flicker, 0xFFFFF0C0, 1);
            }
            case GenesisCoreBlockEntity.STABILIZING -> {
                boolean surge = core.surgeTicks() > 0 && core.feeding();
                float jitter = surge ? 1.5F : 0;
                float shake = jitter * Mth.sin(t * 2.3F);
                swirl(g, cx + shake, cy - shake, 12 + 26 * core.progressFraction(), t, core.galaxySeed(),
                        surge ? RED : CYAN, surge ? 0xFFFFA060 : MAGENTA, 3, 0.04F + 0.03F * core.instability() / 100F);
                glow(g, cx + shake, cy - shake, 4, surge ? 0xFFFFD0C0 : 0xFFE8F8FF, 1);
            }
            case GenesisCoreBlockEntity.BIRTH -> {
                float k = core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_TICKS;
                if (core.birthTicks() < GenesisCoreBlockEntity.BIRTH_COLLAPSE) {
                    glow(g, cx, cy, 14 * (1 - core.birthTicks() / (float) GenesisCoreBlockEntity.BIRTH_COLLAPSE) + 1, 0xFFFFFFFF, 1);
                } else if (core.birthTicks() < GenesisCoreBlockEntity.BIRTH_BANG) {
                    plot(g, cx, cy, 0xFFFFFFFF, 1);
                } else {
                    float flash = Math.max(0, 1 - (core.birthTicks() - GenesisCoreBlockEntity.BIRTH_BANG) / 40F);
                    if (flash > 0) {
                        g.fill(cx - VIEW / 2, cy - VIEW / 2, cx + VIEW / 2, cy + VIEW / 2, ((int) (flash * 230) << 24) | 0xFFFFFF);
                    }
                    galaxy(g, cx, cy, 6 + 30 * Math.min(1, k * 1.4F), t, core.galaxySeed());
                }
            }
            case GenesisCoreBlockEntity.COMPLETE -> {
                dust(g, cx, cy, t, 0x50, 30);
                galaxy(g, cx, cy, 36, t, core.galaxySeed());
            }
            default -> { }
        }
    }

    /** うっすら瞬く塵（窓の背景）。 */
    private static void dust(GuiGraphics g, int cx, int cy, float t, int alpha, int n) {
        long seed = 0xD057L;
        for (int i = 0; i < n; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int) Math.floorMod(seed >>> 20, (long) VIEW) - VIEW / 2;
            int sy = (int) Math.floorMod(seed >>> 40, (long) VIEW) - VIEW / 2;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.09F + i);
            g.fill(cx + sx, cy + sy, cx + sx + 1, cy + sy + 1, ((int) (alpha * tw) << 24) | 0xB8C8FF);
        }
    }

    /** 渦を巻く原始の宇宙（腕 arms 本、内側 inner 色から外側 outer 色へ）。 */
    private static void swirl(GuiGraphics g, float cx, float cy, float radius, float t, long seed, int inner, int outer, int arms,
                              float speed) {
        java.util.Random rng = new java.util.Random(seed ^ 0x51A7L);
        for (int i = 0; i < 260; i++) {
            int arm = i % arms;
            float u = rng.nextFloat();
            float r = radius * (0.12F + 0.88F * u);
            float a = arm * Mth.TWO_PI / arms + u * 4.2F + t * speed * (1.6F - u) + (rng.nextFloat() - 0.5F) * 0.7F;
            float px = cx + Mth.cos(a) * r;
            float py = cy + Mth.sin(a) * r * 0.78F;
            plot(g, px, py, lerp(inner, outer, u), 0.35F + 0.65F * (1 - u));
        }
    }

    /** 生まれた銀河（種で形と色が決まり、ゆっくり回る）。 */
    private static void galaxy(GuiGraphics g, int cx, int cy, float radius, float t, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        float hue = rng.nextFloat();
        int arms = 2 + rng.nextInt(3);
        float twist = 3 + rng.nextFloat() * 3;
        int core = Mth.hsvToRgb(hue, 0.25F, 1) | 0xFF000000;
        int edge = Mth.hsvToRgb((hue + 0.55F) % 1, 0.7F, 1) | 0xFF000000;
        for (int i = 0; i < 360; i++) {
            int arm = i % arms;
            float u = (float) Math.sqrt(rng.nextFloat());
            float r = radius * u;
            float a = arm * Mth.TWO_PI / arms + u * twist + t * 0.008F + (rng.nextFloat() - 0.5F) * (0.9F - 0.5F * u);
            plot(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r * 0.62F, lerp(core, edge, u), 0.3F + 0.7F * (1 - u * 0.8F));
        }
        glow(g, cx, cy, 3.5F, core, 1);
    }

    /** 丸い光（中心ほど明るい）。 */
    private static void glow(GuiGraphics g, float cx, float cy, float radius, int rgb, float bright) {
        int r = (int) Math.ceil(radius);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                float d = Mth.sqrt(dx * dx + dy * dy) / Math.max(0.5F, radius);
                if (d <= 1) {
                    plot(g, cx + dx, cy + dy, rgb, bright * (1 - d * d));
                }
            }
        }
    }

    private static void plot(GuiGraphics g, float x, float y, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 0) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, (a << 24) | (rgb & 0xFFFFFF));
    }

    private static int lerp(int a, int b, float k) {
        return GuiSkin.mix(a, b, Mth.clamp(k, 0, 1));
    }

    // ------------------------------------------------------------------ 暴走の兆し（窓の上の帯）

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
        GenesisCoreBlockEntity core = core();
        if (core == null || core.stage() != GenesisCoreBlockEntity.STABILIZING || core.surgeTicks() <= 0) {
            return;
        }
        boolean held = !core.feeding();
        if (held || ((int) (time(0) / 6)) % 2 == 0) {
            Component c = Component.translatable(held ? "gui.singulo.genesis.screen.surge_held" : "gui.singulo.genesis.screen.surge");
            var lines = font.split(c, VIEW - 6);
            int left = OX + VIEW_X;
            int top = VIEW_Y + VIEW - 3 - lines.size() * 10;
            g.fill(left, top - 2, left + VIEW, VIEW_Y + VIEW - 1, held ? 0xC0103848 : 0xC0601010);
            for (var seq : lines) {
                g.drawString(font, seq, left + VIEW / 2 - font.width(seq) / 2, top, held ? CYAN : 0xFFFFE0E0, false);
                top += 10;
            }
        }
    }

    private static int phaseColor(int stage) {
        return switch (stage) {
            case GenesisCoreBlockEntity.UNFORMED -> 0xFF9AA0B8;
            case GenesisCoreBlockEntity.CRADLE -> 0xFFF4D890;
            case GenesisCoreBlockEntity.KINDLING -> EMBER;
            case GenesisCoreBlockEntity.STABILIZING -> CYAN;
            case GenesisCoreBlockEntity.BIRTH -> 0xFFFFFFFF;
            default -> MAGENTA;
        };
    }

    private static String percent(float f) {
        return String.format("%.0f%%", f * 100);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
