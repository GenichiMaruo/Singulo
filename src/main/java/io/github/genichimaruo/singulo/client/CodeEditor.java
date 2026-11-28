package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.program.Library;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * プログラムを書く欄（0.24.0）。左に行番号（誤りの行は赤）、文は色分けする（キーワード・関数・文字列・数・コメント）。
 * Tab で4つ字下げ（選んだ行はまとめて。Shift+Tab で戻す）、Enter で前の行と同じだけ字下げ（{@code :} で終わる行なら1段深く）、
 * Backspace は字下げの中なら1段ずつ消す。Ctrl+A / C / X / V / Z（元に戻す）・Ctrl+Y（やり直す）、マウスで選ぶ・スクロール。
 */
final class CodeEditor {
    static final int LINE_H = 10, GUTTER = 26, PAD = 3;
    private static final Set<String> KEYWORDS = Set.of("if", "elif", "else", "while", "for", "in", "def", "return", "break",
            "continue", "pass", "and", "or", "not", "True", "False", "None", "global", "is");
    private static final int C_TEXT = 0xFFE6E8EE, C_KEYWORD = 0xFFC792EA, C_FUNCTION = 0xFF82C8FF, C_STRING = 0xFFF0B070,
            C_NUMBER = 0xFFA6E08C, C_COMMENT = 0xFF7F8C98, C_GUTTER = 0xFF6C7480, C_ERROR = 0xFFFF6060, C_WARNING = 0xFFE0C050;

    private record Snapshot(String text, int line, int col) {}

    private final Font font;
    private final List<StringBuilder> lines = new ArrayList<>();
    private final int maxLength;
    private final Runnable onChange;
    private int x, y, width, height;
    /** カーソル（行・列）と、選び始めた所（line が -1 なら選んでいない）。 */
    private int line, col, anchorLine = -1, anchorCol;
    /** いちばん上に見えている行と、横のずらし（ピクセル）。 */
    private int top, shift;
    private boolean dragging;
    /** 誤りの行（1から。0 はなし）、実行中の行（0 はなし）、気をつける行（場所の名前がないなど）。 */
    private int errorLine, runningLine;
    private java.util.Set<Integer> warningLines = java.util.Set.of();
    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean typing;
    private long blinkStart = net.minecraft.Util.getMillis();

    CodeEditor(Font font, String text, int maxLength, Runnable onChange) {
        this.font = font;
        this.maxLength = maxLength;
        this.onChange = onChange;
        setText(text);
    }

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    String text() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(lines.get(i));
        }
        return b.toString();
    }

    private int length() {
        int n = lines.size() - 1;
        for (StringBuilder l : lines) {
            n += l.length();
        }
        return n;
    }

    private void setText(String text) {
        lines.clear();
        for (String l : text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split("\n", -1)) {
            lines.add(new StringBuilder(l));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        line = Math.min(line, lines.size() - 1);
        col = Math.min(col, lines.get(line).length());
    }

    void setErrorLine(int errorLine) {
        this.errorLine = errorLine;
    }

    void setRunningLine(int runningLine) {
        this.runningLine = runningLine;
    }

    void setWarningLines(java.util.Set<Integer> lines) {
        this.warningLines = lines;
    }

    /** カーソルの行の文と、行の中の位置（0から）。 */
    String currentLine() {
        return lines.get(line).toString();
    }

    int column() {
        return col;
    }

    boolean selecting() {
        return hasSelection();
    }

    /** カーソルの所に入れる（選んでいれば置き換える）。 */
    void insertText(String text) {
        remember(false);
        insert(text);
        changed();
    }

    /** カーソルの前の n 文字を text に置き換える（候補を選んだとき）。 */
    void replaceBefore(int n, String text) {
        remember(false);
        anchorLine = -1;
        StringBuilder cur = lines.get(line);
        int from = Math.max(0, col - n);
        cur.delete(from, col);
        col = from;
        insert(text);
        changed();
    }

    /** カーソルの画面の位置（文字の左上）。 */
    int cursorX() {
        return x + GUTTER + PAD - shift + font.width(lines.get(line).substring(0, col));
    }

    int cursorY() {
        return y + PAD + (line - top) * LINE_H;
    }

    /** カーソルの行が欄の中に見えているか。 */
    boolean cursorVisible() {
        return line >= top && line < top + visibleLines();
    }

    int top() {
        return y;
    }

    int bottom() {
        return y + height;
    }

    int cursorLine() {
        return line + 1;
    }

    int cursorColumn() {
        return col + 1;
    }

    int lineCount() {
        return lines.size();
    }

    /** その行へカーソルを動かし、見えるようにする。 */
    void jumpTo(int oneBasedLine) {
        line = Math.max(0, Math.min(lines.size() - 1, oneBasedLine - 1));
        col = 0;
        anchorLine = -1;
        reveal();
    }

    // ------------------------------------------------------------------ 編集

    private void remember(boolean typed) {
        if (typed && typing) {
            return;                                     // 続けて打った字はまとめて戻す
        }
        typing = typed;
        undo.push(new Snapshot(text(), line, col));
        while (undo.size() > 200) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void restore(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty()) {
            return;
        }
        to.push(new Snapshot(text(), line, col));
        Snapshot s = from.pop();
        setText(s.text);
        line = Math.min(s.line, lines.size() - 1);
        col = Math.min(s.col, lines.get(line).length());
        anchorLine = -1;
        typing = false;
        changed();
    }

    private void changed() {
        blinkStart = net.minecraft.Util.getMillis();
        reveal();
        onChange.run();
    }

    private boolean hasSelection() {
        return anchorLine >= 0 && (anchorLine != line || anchorCol != col);
    }

    /** 選んだ範囲の始まりと終わり（{行, 列, 行, 列}）。 */
    private int[] selection() {
        boolean forward = anchorLine < line || anchorLine == line && anchorCol <= col;
        return forward ? new int[] {anchorLine, anchorCol, line, col} : new int[] {line, col, anchorLine, anchorCol};
    }

    private String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        int[] s = selection();
        if (s[0] == s[2]) {
            return lines.get(s[0]).substring(s[1], s[3]);
        }
        StringBuilder b = new StringBuilder(lines.get(s[0]).substring(s[1]));
        for (int i = s[0] + 1; i < s[2]; i++) {
            b.append('\n').append(lines.get(i));
        }
        return b.append('\n').append(lines.get(s[2]), 0, s[3]).toString();
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            anchorLine = -1;
            return;
        }
        int[] s = selection();
        String tail = lines.get(s[2]).substring(s[3]);
        StringBuilder first = lines.get(s[0]);
        first.setLength(s[1]);
        first.append(tail);
        for (int i = s[2]; i > s[0]; i--) {
            lines.remove(i);
        }
        line = s[0];
        col = s[1];
        anchorLine = -1;
    }

    private void insert(String text) {
        deleteSelection();
        text = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        int room = maxLength - length();
        if (room <= 0) {
            return;
        }
        if (text.length() > room) {
            text = text.substring(0, room);
        }
        String[] parts = text.split("\n", -1);
        StringBuilder cur = lines.get(line);
        String tail = cur.substring(col);
        cur.setLength(col);
        cur.append(parts[0]);
        int at = line;
        for (int i = 1; i < parts.length; i++) {
            lines.add(++at, new StringBuilder(parts[i]));
        }
        line = at;
        col = lines.get(line).length();
        lines.get(line).append(tail);
    }

    private static int indentOf(CharSequence s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private void newline() {
        remember(false);
        StringBuilder cur = lines.get(line);
        int indent = indentOf(cur);
        String before = cur.substring(0, Math.min(col, cur.length())).strip();
        if (before.endsWith(":")) {
            indent += 4;
        }
        insert("\n" + " ".repeat(Math.min(indent, 64)));
        changed();
    }

    private void tab(boolean back) {
        remember(false);
        if (hasSelection() || back) {
            int[] s = hasSelection() ? selection() : new int[] {line, col, line, col};
            for (int i = s[0]; i <= s[2]; i++) {
                StringBuilder l = lines.get(i);
                if (back) {
                    int n = Math.min(4, indentOf(l));
                    l.delete(0, n);
                    if (i == line) {
                        col = Math.max(0, col - n);
                    }
                    if (i == anchorLine) {
                        anchorCol = Math.max(0, anchorCol - n);
                    }
                } else if (length() + 4 <= maxLength) {
                    l.insert(0, "    ");
                    if (i == line) {
                        col += 4;
                    }
                    if (i == anchorLine) {
                        anchorCol += 4;
                    }
                }
            }
        } else {
            insert(" ".repeat(4 - col % 4));
        }
        changed();
    }

    private void backspace(boolean word) {
        remember(false);
        if (hasSelection()) {
            deleteSelection();
        } else if (col > 0) {
            StringBuilder l = lines.get(line);
            int from = col - 1;
            if (indentOf(l) >= col) {
                from = Math.max(0, (col - 1) / 4 * 4);          // 字下げの中: 1段ずつ
            } else if (word) {
                from = wordStart(l, col);
            }
            l.delete(from, col);
            col = from;
        } else if (line > 0) {
            StringBuilder prev = lines.get(line - 1);
            col = prev.length();
            prev.append(lines.remove(line));
            line--;
        }
        changed();
    }

    private void delete() {
        remember(false);
        if (hasSelection()) {
            deleteSelection();
        } else if (col < lines.get(line).length()) {
            lines.get(line).deleteCharAt(col);
        } else if (line + 1 < lines.size()) {
            lines.get(line).append(lines.remove(line + 1));
        }
        changed();
    }

    private static int wordStart(CharSequence s, int from) {
        int i = from;
        while (i > 0 && s.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && (Character.isLetterOrDigit(s.charAt(i - 1)) || s.charAt(i - 1) == '_')) {
            i--;
        }
        return i == from ? from - 1 : i;
    }

    private static int wordEnd(CharSequence s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
            i++;
        }
        return i == from ? from + 1 : i;
    }

    private void move(int newLine, int newCol, boolean select) {
        if (select) {
            if (anchorLine < 0) {
                anchorLine = line;
                anchorCol = col;
            }
        } else {
            anchorLine = -1;
        }
        line = Math.max(0, Math.min(lines.size() - 1, newLine));
        col = Math.max(0, Math.min(lines.get(line).length(), newCol));
        typing = false;
        blinkStart = net.minecraft.Util.getMillis();
        reveal();
    }

    boolean charTyped(char c) {
        if (c < ' ' || c == 127) {
            return false;
        }
        remember(true);
        insert(String.valueOf(c));
        changed();
        return true;
    }

    boolean keyPressed(int key, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = Screen.hasControlDown();
        StringBuilder cur = lines.get(line);
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> newline();
            case GLFW.GLFW_KEY_TAB -> tab(shift);
            case GLFW.GLFW_KEY_BACKSPACE -> backspace(ctrl);
            case GLFW.GLFW_KEY_DELETE -> delete();
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && hasSelection()) {
                    int[] s = selection();
                    move(s[0], s[1], false);
                } else if (col == 0 && line > 0) {
                    move(line - 1, lines.get(line - 1).length(), shift);
                } else {
                    move(line, ctrl ? wordStart(cur, col) : col - 1, shift);
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && hasSelection()) {
                    int[] s = selection();
                    move(s[2], s[3], false);
                } else if (col == cur.length() && line + 1 < lines.size()) {
                    move(line + 1, 0, shift);
                } else {
                    move(line, ctrl ? wordEnd(cur, col) : col + 1, shift);
                }
            }
            case GLFW.GLFW_KEY_UP -> move(line - 1, col, shift);
            case GLFW.GLFW_KEY_DOWN -> move(line + 1, col, shift);
            case GLFW.GLFW_KEY_PAGE_UP -> move(line - visibleLines(), col, shift);
            case GLFW.GLFW_KEY_PAGE_DOWN -> move(line + visibleLines(), col, shift);
            case GLFW.GLFW_KEY_HOME -> {
                int indent = indentOf(cur);
                move(ctrl ? 0 : line, ctrl ? 0 : col == indent ? 0 : indent, shift);
            }
            case GLFW.GLFW_KEY_END -> move(ctrl ? lines.size() - 1 : line, ctrl ? Integer.MAX_VALUE : cur.length(), shift);
            default -> {
                if (!ctrl) {
                    return false;
                }
                switch (key) {
                    case GLFW.GLFW_KEY_A -> {
                        anchorLine = 0;
                        anchorCol = 0;
                        line = lines.size() - 1;
                        col = lines.get(line).length();
                    }
                    case GLFW.GLFW_KEY_C -> Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    case GLFW.GLFW_KEY_X -> {
                        if (hasSelection()) {
                            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                            remember(false);
                            deleteSelection();
                            changed();
                        }
                    }
                    case GLFW.GLFW_KEY_V -> {
                        remember(false);
                        insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                        changed();
                    }
                    case GLFW.GLFW_KEY_Z -> restore(shift ? redo : undo, shift ? undo : redo);
                    case GLFW.GLFW_KEY_Y -> restore(redo, undo);
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ マウス・スクロール

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private int visibleLines() {
        return Math.max(1, (height - 2 * PAD) / LINE_H);
    }

    private void clampScroll() {
        top = Math.max(0, Math.min(top, Math.max(0, lines.size() - visibleLines())));
    }

    private void reveal() {
        if (line < top) {
            top = line;
        } else if (line >= top + visibleLines()) {
            top = line - visibleLines() + 1;
        }
        clampScroll();
        int textW = width - GUTTER - 2 * PAD;
        int cx = font.width(lines.get(line).substring(0, col));
        if (cx - shift > textW - 8) {
            shift = cx - textW + 24;
        } else if (cx - shift < 0) {
            shift = Math.max(0, cx - 24);
        }
    }

    private int[] at(double mx, double my) {
        int l = Math.max(0, Math.min(lines.size() - 1, top + (int) Math.floor((my - y - PAD) / LINE_H)));
        String s = lines.get(l).toString();
        double px = mx - (x + GUTTER + PAD) + shift;
        int c = 0;
        while (c < s.length()) {
            int w = font.width(s.substring(0, c + 1));
            int prev = font.width(s.substring(0, c));
            if (px < (prev + w) / 2.0) {
                break;
            }
            c++;
        }
        return new int[] {l, c};
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my) || button != 0) {
            return false;
        }
        int[] p = at(mx, my);
        move(p[0], p[1], Screen.hasShiftDown());
        dragging = true;
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (!dragging) {
            return false;
        }
        int[] p = at(mx, my);
        move(p[0], p[1], true);
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (!inside(mx, my)) {
            return false;
        }
        top -= (int) Math.signum(amount) * 3;
        clampScroll();
        return true;
    }

    // ------------------------------------------------------------------ 描く

    void render(GuiGraphics g, boolean focused) {
        g.fill(x, y, x + width, y + height, 0xF0141820);
        g.fill(x, y, x + GUTTER, y + height, 0xF01C212A);
        g.renderOutline(x, y, width, height, focused ? 0xFF5A6A80 : 0xFF333A44);
        int textX = x + GUTTER + PAD;
        int textW = width - GUTTER - 2 * PAD;
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int[] sel = hasSelection() ? selection() : null;
        for (int i = 0; i < visibleLines() && top + i < lines.size(); i++) {
            int l = top + i;
            int ly = y + PAD + i * LINE_H;
            String s = lines.get(l).toString();
            if (l == line) {
                g.fill(x + GUTTER, ly - 1, x + width - 1, ly + LINE_H - 1, 0x30FFFFFF);
            }
            if (l + 1 == runningLine) {
                g.fill(x + GUTTER, ly - 1, x + width - 1, ly + LINE_H - 1, 0x3040E080);
                g.fill(x + 1, ly - 1, x + 3, ly + LINE_H - 1, 0xFF50E090);
            }
            if (l + 1 == errorLine) {
                g.fill(x + GUTTER, ly - 1, x + width - 1, ly + LINE_H - 1, 0x40FF3030);
            }
            boolean warn = warningLines.contains(l + 1);
            if (warn && l + 1 != errorLine) {
                g.fill(x + 1, ly - 1, x + 3, ly + LINE_H - 1, C_WARNING);
            }
            String number = String.valueOf(l + 1);
            int numberColor = l + 1 == errorLine ? C_ERROR : warn ? C_WARNING : l + 1 == runningLine ? 0xFF50E090 : C_GUTTER;
            g.drawString(font, number, x + GUTTER - 4 - font.width(number), ly, numberColor, false);
            g.enableScissor(textX - 1, y + 1, textX + textW + PAD, y + height - 1);
            if (sel != null && l >= sel[0] && l <= sel[2]) {
                int from = l == sel[0] ? sel[1] : 0;
                int to = l == sel[2] ? sel[3] : s.length();
                int sx = textX - shift + font.width(s.substring(0, from));
                int ex = textX - shift + font.width(s.substring(0, to)) + (l < sel[2] ? 4 : 0);
                g.fill(sx, ly - 1, ex, ly + LINE_H - 1, 0x805080C0);
            }
            drawColored(g, s, textX - shift, ly);
            if (l == line && focused && (net.minecraft.Util.getMillis() - blinkStart) / 500 % 2 == 0) {
                int cx = textX - shift + font.width(s.substring(0, col));
                g.fill(cx, ly - 1, cx + 1, ly + LINE_H - 1, 0xFFFFFFFF);
            }
            g.disableScissor();
        }
        g.disableScissor();
        // スクロールの位置
        int visible = visibleLines();
        if (lines.size() > visible) {
            int barH = Math.max(8, height * visible / lines.size());
            int barY = y + (height - barH) * top / Math.max(1, lines.size() - visible);
            g.fill(x + width - 3, barY, x + width - 1, barY + barH, 0x80A0A8B4);
        }
    }

    /** 1行を色分けして描く。 */
    private void drawColored(GuiGraphics g, String s, int px, int py) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int start = i;
            int color;
            if (c == '#') {
                i = s.length();
                color = C_COMMENT;
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < s.length() && s.charAt(i) != c) {
                    i += s.charAt(i) == '\\' ? 2 : 1;
                }
                i = Math.min(s.length(), i + 1);
                color = C_STRING;
            } else if (Character.isDigit(c)) {
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == '_')) {
                    i++;
                }
                color = C_NUMBER;
            } else if (Character.isLetter(c) || c == '_') {
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                    i++;
                }
                String word = s.substring(start, i);
                color = KEYWORDS.contains(word) ? C_KEYWORD : Library.FUNCTIONS.containsKey(word) ? C_FUNCTION : C_TEXT;
            } else {
                i++;
                while (i < s.length() && !Character.isLetterOrDigit(s.charAt(i)) && "#\"'_".indexOf(s.charAt(i)) < 0) {
                    i++;
                }
                color = C_TEXT;
            }
            String part = s.substring(start, i);
            g.drawString(font, part, px, py, color, false);
            px += font.width(part);
        }
    }
}
