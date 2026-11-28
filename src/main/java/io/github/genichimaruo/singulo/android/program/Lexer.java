package io.github.genichimaruo.singulo.android.program;

import java.util.ArrayList;
import java.util.List;

/**
 * プログラムの文を語に分ける。字下げはブロックの始まり（INDENT）と終わり（DEDENT）になる（Python と同じ）。
 * タブは4つの空白として数える。かっこの中では改行しても文が続く。行の終わりの {@code \} でも続く。{@code #} から行の終わりまではコメント。
 */
final class Lexer {
    enum Type { NAME, NUMBER, STRING, OP, NEWLINE, INDENT, DEDENT, EOF }

    record Token(Type type, String text, Object value, int line) {
        boolean is(String s) {
            return (type == Type.OP || type == Type.NAME) && text.equals(s);
        }
    }

    /** 長い順に並べた記号（先に長いものを当てる）。 */
    private static final String[] OPS = {"**=", "//=", "**", "//", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=",
            "+", "-", "*", "/", "%", "<", ">", "=", "(", ")", "[", "]", ",", ":", "."};

    private Lexer() {}

    static List<Token> tokenize(String source) {
        List<Token> out = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        indents.add(0);
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int depth = 0;                 // かっこの深さ
        boolean continued = false;     // 前の行が \ で終わった
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            int lineNo = n + 1;
            int i = 0;
            if (depth == 0 && !continued) {
                int width = 0;
                while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                    width = line.charAt(i) == '\t' ? (width / 4 + 1) * 4 : width + 1;
                    i++;
                }
                if (i >= line.length() || line.charAt(i) == '#') {
                    continue;                                  // 空の行・コメントだけの行
                }
                int top = indents.get(indents.size() - 1);
                if (width > top) {
                    indents.add(width);
                    out.add(new Token(Type.INDENT, "", null, lineNo));
                } else if (width < top) {
                    while (width < indents.get(indents.size() - 1)) {
                        indents.remove(indents.size() - 1);
                        out.add(new Token(Type.DEDENT, "", null, lineNo));
                    }
                    if (width != indents.get(indents.size() - 1)) {
                        throw new ProgramError(lineNo, "dedent");
                    }
                }
            }
            continued = false;
            boolean any = false;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == ' ' || c == '\t') {
                    i++;
                    continue;
                }
                if (c == '#') {
                    break;
                }
                if (c == '\\' && i == line.length() - 1) {
                    continued = true;
                    i++;
                    continue;
                }
                any = true;
                if (Character.isLetter(c) || c == '_') {
                    int s = i;
                    while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
                        i++;
                    }
                    String w = line.substring(s, i);
                    out.add(new Token(Type.NAME, w, null, lineNo));
                    continue;
                }
                if (Character.isDigit(c) || c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
                    i = number(line, i, lineNo, out);
                    continue;
                }
                if (c == '"' || c == '\'') {
                    i = string(line, i, lineNo, out);
                    continue;
                }
                String op = null;
                for (String o : OPS) {
                    if (line.startsWith(o, i)) {
                        op = o;
                        break;
                    }
                }
                if (op == null) {
                    throw new ProgramError(lineNo, "bad_char", String.valueOf(c));
                }
                if (op.equals("(") || op.equals("[")) {
                    depth++;
                } else if (op.equals(")") || op.equals("]")) {
                    depth = Math.max(0, depth - 1);
                }
                out.add(new Token(Type.OP, op, null, lineNo));
                i += op.length();
            }
            if (any && depth == 0 && !continued) {
                out.add(new Token(Type.NEWLINE, "", null, lineNo));
            }
        }
        int last = lines.length;
        while (last > 1 && lines[last - 1].isBlank()) {
            last--;
        }
        if (depth > 0) {
            throw new ProgramError(last, "unclosed");
        }
        if (!out.isEmpty() && out.get(out.size() - 1).type() != Type.NEWLINE && out.get(out.size() - 1).type() != Type.DEDENT) {
            out.add(new Token(Type.NEWLINE, "", null, last));
        }
        while (indents.size() > 1) {
            indents.remove(indents.size() - 1);
            out.add(new Token(Type.DEDENT, "", null, last));
        }
        out.add(new Token(Type.EOF, "", null, last));
        return out;
    }

    private static int number(String line, int i, int lineNo, List<Token> out) {
        int s = i;
        if (line.startsWith("0x", i) || line.startsWith("0X", i)) {
            i += 2;
            while (i < line.length() && (Character.digit(line.charAt(i), 16) >= 0 || line.charAt(i) == '_')) {
                i++;
            }
            try {
                out.add(new Token(Type.NUMBER, line.substring(s, i), Long.parseLong(line.substring(s + 2, i).replace("_", ""), 16), lineNo));
            } catch (NumberFormatException e) {
                throw new ProgramError(lineNo, "bad_number", line.substring(s, i));
            }
            return i;
        }
        boolean real = false;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || c == '_') {
                i++;
            } else if (c == '.' && !real) {
                real = true;
                i++;
            } else if ((c == 'e' || c == 'E') && i + 1 < line.length()
                    && (Character.isDigit(line.charAt(i + 1)) || (line.charAt(i + 1) == '-' || line.charAt(i + 1) == '+')
                    && i + 2 < line.length() && Character.isDigit(line.charAt(i + 2)))) {
                real = true;
                i += 2;
            } else {
                break;
            }
        }
        if (i < line.length() && (Character.isLetter(line.charAt(i)) || line.charAt(i) == '_')) {
            throw new ProgramError(lineNo, "bad_number", line.substring(s, i + 1));
        }
        String text = line.substring(s, i);
        String digits = text.replace("_", "");
        try {
            Object v = real ? (Object) Double.parseDouble(digits) : (Object) Long.parseLong(digits);
            out.add(new Token(Type.NUMBER, text, v, lineNo));
        } catch (NumberFormatException e) {
            throw new ProgramError(lineNo, "bad_number", text);
        }
        return i;
    }

    private static int string(String line, int i, int lineNo, List<Token> out) {
        char q = line.charAt(i);
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= line.length()) {
                throw new ProgramError(lineNo, "unterminated_string");
            }
            char c = line.charAt(i);
            if (c == q) {
                i++;
                break;
            }
            if (c == '\\' && i + 1 < line.length()) {
                char e = line.charAt(i + 1);
                b.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    default -> e;
                });
                i += 2;
                continue;
            }
            b.append(c);
            i++;
        }
        out.add(new Token(Type.STRING, b.toString(), b.toString(), lineNo));
        return i;
    }
}
