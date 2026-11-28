package io.github.genichimaruo.singulo.android.program;

import io.github.genichimaruo.singulo.android.program.Lexer.Token;
import io.github.genichimaruo.singulo.android.program.Lexer.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * プログラムを読みながら、そのまま命令に訳す（再帰下降）。書ける文は Python の一部:
 * 代入（{@code = += -= *= /= //= %= **=}）・if / elif / else・while・for … in・def（いちばん外側だけ）・return・break・continue・pass・global。
 * 式は or / and / not・比べる（== != < <= > >= in not in is is not。続けて比べるのは不可）・+ - * / // % **・
 * 呼び出し・添え字・リスト・場所の .x / .y / .z・メソッド（list.append など）。
 */
final class Compiler {
    /** 長さの上限（文字数・命令の数）。 */
    static final int MAX_SOURCE = 16_000, MAX_CODE = 20_000;

    private static final Set<String> KEYWORDS = Set.of("if", "elif", "else", "while", "for", "in", "def", "return", "break",
            "continue", "pass", "and", "or", "not", "True", "False", "None", "global", "is", "lambda", "class", "import", "from",
            "try", "except", "finally", "with", "as", "yield", "del", "assert", "raise", "nonlocal");

    private record Loop(int continueAt, List<Integer> breaks) {}

    private record Call(String name, int argc, int line) {}

    private final List<Token> t;
    private int p;
    private final List<int[]> ins = new ArrayList<>();
    private final List<Object> consts = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> nameIndex = new HashMap<>();
    private final Map<String, Code.Fn> functions = new LinkedHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final Deque<Loop> loops = new ArrayDeque<>();
    /** 今訳している関数（いちばん外側なら null）と、その中で global にした名前。 */
    private String function;
    private Set<String> globals = new HashSet<>();
    private int depth;
    private int hidden;
    private int line = 1;

    private Compiler(List<Token> tokens) {
        this.t = tokens;
    }

    static Code compile(String source) {
        if (source.length() > MAX_SOURCE) {
            throw new ProgramError(1, "too_long", MAX_SOURCE);
        }
        Compiler c = new Compiler(Lexer.tokenize(source));
        while (c.peek().type() != Type.EOF) {
            c.statement();
        }
        c.emit(Code.HALT, 0, 0);
        c.check();
        return c.build();
    }

    private Code build() {
        int n = ins.size();
        int[] op = new int[n], a = new int[n], b = new int[n], line = new int[n];
        for (int i = 0; i < n; i++) {
            int[] x = ins.get(i);
            op[i] = x[0];
            a[i] = x[1];
            b[i] = x[2];
            line[i] = x[3];
        }
        return new Code(op, a, b, line, consts.toArray(), names.toArray(String[]::new), Map.copyOf(functions));
    }

    /** 呼んだ関数があるか、引数の数が合うかを確かめる。 */
    private void check() {
        for (Call c : calls) {
            Code.Fn fn = functions.get(c.name);
            if (fn != null) {
                if (fn.params().length != c.argc) {
                    throw new ProgramError(c.line, "arg_count", c.name, fn.params().length, c.argc);
                }
                continue;
            }
            Library.Sig sig = Library.FUNCTIONS.get(c.name);
            if (sig == null) {
                throw new ProgramError(c.line, "unknown_function", c.name);
            }
            if (!sig.accepts(c.argc)) {
                String want = sig.max() < 0 ? sig.min() + "+" : sig.min() == sig.max() ? String.valueOf(sig.min()) : sig.min() + "-" + sig.max();
                throw new ProgramError(c.line, "arg_count", c.name, want, c.argc);
            }
        }
    }

    // ------------------------------------------------------------------ 語

    private Token peek() {
        return t.get(p);
    }

    private Token peek(int ahead) {
        return t.get(Math.min(p + ahead, t.size() - 1));
    }

    private Token next() {
        Token k = t.get(p);
        if (k.type() != Type.EOF) {
            p++;
        }
        return k;
    }

    private boolean at(String s) {
        return peek().is(s);
    }

    private boolean accept(String s) {
        if (at(s)) {
            next();
            return true;
        }
        return false;
    }

    private void expect(String s) {
        if (!accept(s)) {
            throw unexpected(s);
        }
    }

    private ProgramError unexpected(String wanted) {
        Token k = peek();
        String got = switch (k.type()) {
            case NEWLINE -> "⏎";
            case INDENT, DEDENT -> "⇥";
            case EOF -> "EOF";
            default -> k.text();
        };
        return new ProgramError(k.line(), "expected", wanted, got);
    }

    private void expectNewline() {
        if (peek().type() != Type.NEWLINE) {
            throw unexpected("⏎");
        }
        next();
    }

    private String expectName() {
        Token k = peek();
        if (k.type() != Type.NAME || KEYWORDS.contains(k.text())) {
            throw unexpected("name");
        }
        next();
        return k.text();
    }

    // ------------------------------------------------------------------ 命令

    private int emit(int op, int a, int b) {
        if (ins.size() >= MAX_CODE) {
            throw new ProgramError(line, "too_long", MAX_SOURCE);
        }
        ins.add(new int[] {op, a, b, line});
        return ins.size() - 1;
    }

    private int here() {
        return ins.size();
    }

    private void patch(int at) {
        ins.get(at)[1] = here();
    }

    private int constant(Object v) {
        for (int i = 0; i < consts.size(); i++) {
            Object c = consts.get(i);
            if (c == null ? v == null : v != null && c.getClass() == v.getClass() && c.equals(v)) {
                return i;
            }
        }
        consts.add(v);
        return consts.size() - 1;
    }

    private int name(String n) {
        return nameIndex.computeIfAbsent(n, k -> {
            names.add(k);
            return names.size() - 1;
        });
    }

    private void load(String n) {
        emit(Code.LOAD, name(n), 0);
    }

    private void store(String n) {
        // 関数の中で global にした名前は、外側の変数へ
        emit(Code.STORE, name(n), function != null && globals.contains(n) ? 1 : 0);
    }

    // ------------------------------------------------------------------ 文

    private void statement() {
        Token k = peek();
        line = k.line();
        if (k.type() == Type.INDENT) {
            throw new ProgramError(k.line(), "unexpected_indent");
        }
        if (k.type() == Type.DEDENT || k.type() == Type.NEWLINE) {
            next();
            return;
        }
        if (k.type() == Type.NAME) {
            switch (k.text()) {
                case "if" -> {
                    ifStatement();
                    return;
                }
                case "while" -> {
                    whileStatement();
                    return;
                }
                case "for" -> {
                    forStatement();
                    return;
                }
                case "def" -> {
                    defStatement();
                    return;
                }
                case "elif", "else" -> throw new ProgramError(k.line(), "stray", k.text());
                default -> {
                }
            }
        }
        simple();
        expectNewline();
    }

    /** ブロック（{@code :} のあと。次の行から字下げした文か、同じ行の1つの文）。 */
    private void block() {
        expect(":");
        depth++;
        if (peek().type() == Type.NEWLINE) {
            next();
            if (peek().type() != Type.INDENT) {
                throw new ProgramError(peek().line(), "expected_indent");
            }
            next();
            while (peek().type() != Type.DEDENT && peek().type() != Type.EOF) {
                statement();
            }
            if (peek().type() == Type.DEDENT) {
                next();
            }
        } else {
            line = peek().line();
            simple();
            expectNewline();
        }
        depth--;
    }

    private void ifStatement() {
        next();
        expr();
        int jf = emit(Code.JF, -1, 0);
        block();
        List<Integer> ends = new ArrayList<>();
        while (at("elif")) {
            line = peek().line();
            ends.add(emit(Code.JUMP, -1, 0));
            patch(jf);
            next();
            expr();
            jf = emit(Code.JF, -1, 0);
            block();
        }
        if (at("else")) {
            line = peek().line();
            ends.add(emit(Code.JUMP, -1, 0));
            patch(jf);
            next();
            block();
        } else {
            patch(jf);
        }
        for (int e : ends) {
            patch(e);
        }
    }

    private void whileStatement() {
        next();
        int start = here();
        expr();
        int jf = emit(Code.JF, -1, 0);
        Loop loop = new Loop(start, new ArrayList<>());
        loops.push(loop);
        block();
        loops.pop();
        emit(Code.JUMP, start, 0);
        patch(jf);
        for (int b : loop.breaks) {
            patch(b);
        }
    }

    /** for x in 式: 式の値と番号を隠れた変数に置き、番号が長さに届くまで回す。 */
    private void forStatement() {
        next();
        String var = expectName();
        expect("in");
        expr();
        int n = hidden++;
        String it = "$it" + n, i = "$i" + n;
        store(it);
        emit(Code.CONST, constant(0L), 0);
        store(i);
        int start = here();
        load(i);
        load(it);
        emit(Code.LEN, 0, 0);
        emit(Code.BIN, Values.LT, 0);
        int jf = emit(Code.JF, -1, 0);
        load(it);
        load(i);
        emit(Code.INDEX, 0, 0);
        store(var);
        load(i);
        emit(Code.CONST, constant(1L), 0);
        emit(Code.BIN, Values.ADD, 0);
        store(i);
        Loop loop = new Loop(start, new ArrayList<>());
        loops.push(loop);
        block();
        loops.pop();
        emit(Code.JUMP, start, 0);
        patch(jf);
        for (int b : loop.breaks) {
            patch(b);
        }
    }

    private void defStatement() {
        int defLine = peek().line();
        next();
        if (depth > 0 || function != null) {
            throw new ProgramError(defLine, "nested_def");
        }
        String fname = expectName();
        if (functions.containsKey(fname)) {
            throw new ProgramError(defLine, "duplicate_function", fname);
        }
        if (Library.FUNCTIONS.containsKey(fname)) {
            throw new ProgramError(defLine, "builtin_name", fname);
        }
        expect("(");
        List<String> params = new ArrayList<>();
        if (!at(")")) {
            do {
                if (at(")")) {
                    break;
                }
                String param = expectName();
                if (params.contains(param)) {
                    throw new ProgramError(defLine, "duplicate_param", param);
                }
                params.add(param);
            } while (accept(","));
        }
        expect(")");
        int skip = emit(Code.JUMP, -1, 0);
        Set<String> declared = new HashSet<>();
        functions.put(fname, new Code.Fn(fname, here(), params.toArray(String[]::new), declared, defLine));
        function = fname;
        globals = declared;
        block();
        emit(Code.CONST, constant(null), 0);
        emit(Code.RET, 0, 0);
        function = null;
        globals = new HashSet<>();
        patch(skip);
    }

    private void simple() {
        Token k = peek();
        if (k.type() == Type.NAME) {
            switch (k.text()) {
                case "pass" -> {
                    next();
                    return;
                }
                case "break" -> {
                    next();
                    if (loops.isEmpty()) {
                        throw new ProgramError(k.line(), "outside_loop", "break");
                    }
                    loops.peek().breaks.add(emit(Code.JUMP, -1, 0));
                    return;
                }
                case "continue" -> {
                    next();
                    if (loops.isEmpty()) {
                        throw new ProgramError(k.line(), "outside_loop", "continue");
                    }
                    emit(Code.JUMP, loops.peek().continueAt, 0);
                    return;
                }
                case "return" -> {
                    next();
                    if (function == null) {
                        throw new ProgramError(k.line(), "outside_function");
                    }
                    if (peek().type() == Type.NEWLINE) {
                        emit(Code.CONST, constant(null), 0);
                    } else {
                        expr();
                    }
                    emit(Code.RET, 0, 0);
                    return;
                }
                case "global" -> {
                    next();
                    do {
                        globals.add(expectName());
                    } while (accept(","));
                    return;
                }
                default -> {
                    if (KEYWORDS.contains(k.text()) && !Set.of("not", "True", "False", "None").contains(k.text())) {
                        throw new ProgramError(k.line(), "unsupported", k.text());
                    }
                }
            }
        }
        if (assignmentAhead()) {
            assignment();
            return;
        }
        expr();
        emit(Code.POP, 0, 0);
    }

    /** この行（かっこの外）に代入の記号があるか。 */
    private boolean assignmentAhead() {
        int d = 0;
        for (int i = p; i < t.size(); i++) {
            Token k = t.get(i);
            if (k.type() == Type.NEWLINE || k.type() == Type.EOF) {
                return false;
            }
            if (k.type() != Type.OP) {
                continue;
            }
            switch (k.text()) {
                case "(", "[" -> d++;
                case ")", "]" -> d--;
                case "=", "+=", "-=", "*=", "/=", "//=", "%=", "**=" -> {
                    if (d == 0) {
                        return true;
                    }
                }
                case ":" -> {
                    if (d == 0) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private void assignment() {
        Token first = peek();
        if (first.type() != Type.NAME || KEYWORDS.contains(first.text())) {
            throw new ProgramError(first.line(), "bad_target");
        }
        String target = expectName();
        boolean indexed = false;
        if (at("[")) {
            load(target);
            while (true) {
                next();
                expr();
                expect("]");
                if (at("[")) {
                    emit(Code.INDEX, 0, 0);
                    continue;
                }
                break;
            }
            indexed = true;
        }
        Token op = peek();
        if (op.type() != Type.OP) {
            throw new ProgramError(op.line(), "bad_target");
        }
        int bin = switch (op.text()) {
            case "=" -> -1;
            case "+=" -> Values.ADD;
            case "-=" -> Values.SUB;
            case "*=" -> Values.MUL;
            case "/=" -> Values.DIV;
            case "//=" -> Values.FLOORDIV;
            case "%=" -> Values.MOD;
            case "**=" -> Values.POW;
            default -> throw new ProgramError(op.line(), "bad_target");
        };
        next();
        if (indexed) {
            if (bin >= 0) {
                emit(Code.DUP2, 0, 0);
                emit(Code.INDEX, 0, 0);
                expr();
                emit(Code.BIN, bin, 0);
            } else {
                expr();
            }
            emit(Code.SET_INDEX, 0, 0);
        } else {
            if (bin >= 0) {
                load(target);
                expr();
                emit(Code.BIN, bin, 0);
            } else {
                expr();
            }
            store(target);
        }
        if (at("=")) {
            throw new ProgramError(peek().line(), "unsupported", "a = b = c");
        }
    }

    // ------------------------------------------------------------------ 式

    private void expr() {
        orExpr();
    }

    private void orExpr() {
        andExpr();
        while (accept("or")) {
            int j = emit(Code.JT_KEEP, -1, 0);
            andExpr();
            patch(j);
        }
    }

    private void andExpr() {
        notExpr();
        while (accept("and")) {
            int j = emit(Code.JF_KEEP, -1, 0);
            notExpr();
            patch(j);
        }
    }

    private void notExpr() {
        if (accept("not")) {
            notExpr();
            emit(Code.NOT, 0, 0);
            return;
        }
        comparison();
    }

    private int comparisonOp() {
        Token k = peek();
        if (k.type() == Type.OP) {
            return switch (k.text()) {
                case "==" -> Values.EQ;
                case "!=" -> Values.NE;
                case "<" -> Values.LT;
                case "<=" -> Values.LE;
                case ">" -> Values.GT;
                case ">=" -> Values.GE;
                default -> -1;
            };
        }
        if (k.is("in")) {
            return Values.IN;
        }
        if (k.is("not") && peek(1).is("in")) {
            return Values.NOT_IN;
        }
        if (k.is("is")) {
            return peek(1).is("not") ? Values.NE : Values.EQ;
        }
        return -1;
    }

    private void comparison() {
        addExpr();
        int op = comparisonOp();
        if (op < 0) {
            return;
        }
        Token k = next();
        if (op == Values.NOT_IN || k.is("is") && op == Values.NE) {
            next();
        }
        addExpr();
        emit(Code.BIN, op, 0);
        if (comparisonOp() >= 0) {
            throw new ProgramError(peek().line(), "unsupported", "a < b < c");
        }
    }

    private void addExpr() {
        mulExpr();
        while (true) {
            if (accept("+")) {
                mulExpr();
                emit(Code.BIN, Values.ADD, 0);
            } else if (accept("-")) {
                mulExpr();
                emit(Code.BIN, Values.SUB, 0);
            } else {
                return;
            }
        }
    }

    private void mulExpr() {
        unary();
        while (true) {
            int op = at("*") ? Values.MUL : at("/") ? Values.DIV : at("//") ? Values.FLOORDIV : at("%") ? Values.MOD : -1;
            if (op < 0) {
                return;
            }
            next();
            unary();
            emit(Code.BIN, op, 0);
        }
    }

    private void unary() {
        if (accept("-")) {
            unary();
            emit(Code.NEG, 0, 0);
        } else if (accept("+")) {
            unary();
            emit(Code.PLUS, 0, 0);
        } else {
            power();
        }
    }

    private void power() {
        postfix();
        if (accept("**")) {
            unary();
            emit(Code.BIN, Values.POW, 0);
        }
    }

    private void postfix() {
        atom();
        while (true) {
            if (accept("[")) {
                expr();
                expect("]");
                emit(Code.INDEX, 0, 0);
            } else if (accept(".")) {
                String member = expectName();
                if (at("(")) {
                    int argc = arguments();
                    emit(Code.METHOD, name(member), argc);
                } else {
                    emit(Code.ATTR, name(member), 0);
                }
            } else if (at("(")) {
                throw new ProgramError(peek().line(), "not_callable");
            } else {
                return;
            }
        }
    }

    /** ( 式, 式, … ) を訳して、引数の数。 */
    private int arguments() {
        expect("(");
        int n = 0;
        while (!at(")")) {
            if (peek().type() == Type.NAME && peek(1).is("=") ) {
                throw new ProgramError(peek().line(), "unsupported", peek().text() + "=");
            }
            expr();
            n++;
            if (!accept(",")) {
                break;
            }
        }
        expect(")");
        return n;
    }

    private void atom() {
        Token k = peek();
        switch (k.type()) {
            case NUMBER -> {
                next();
                emit(Code.CONST, constant(k.value()), 0);
            }
            case STRING -> {
                next();
                StringBuilder s = new StringBuilder((String) k.value());
                while (peek().type() == Type.STRING) {
                    s.append((String) next().value());
                }
                emit(Code.CONST, constant(Values.checkString(s.toString())), 0);
            }
            case NAME -> {
                switch (k.text()) {
                    case "True" -> {
                        next();
                        emit(Code.CONST, constant(Boolean.TRUE), 0);
                        return;
                    }
                    case "False" -> {
                        next();
                        emit(Code.CONST, constant(Boolean.FALSE), 0);
                        return;
                    }
                    case "None" -> {
                        next();
                        emit(Code.CONST, constant(null), 0);
                        return;
                    }
                    default -> {
                    }
                }
                String n = expectName();
                if (at("(")) {
                    int callLine = k.line();
                    int argc = arguments();
                    calls.add(new Call(n, argc, callLine));
                    emit(Code.CALL, name(n), argc);
                } else {
                    load(n);
                }
            }
            case OP -> {
                if (accept("(")) {
                    expr();
                    expect(")");
                } else if (accept("[")) {
                    int n = 0;
                    while (!at("]")) {
                        expr();
                        n++;
                        if (!accept(",")) {
                            break;
                        }
                    }
                    expect("]");
                    emit(Code.LIST, n, 0);
                } else {
                    throw unexpected("value");
                }
            }
            default -> throw unexpected("value");
        }
    }
}
