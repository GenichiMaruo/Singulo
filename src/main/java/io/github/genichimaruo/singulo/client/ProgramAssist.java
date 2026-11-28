package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.program.Library;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 書く画面の手助け（0.24.0）: カーソルの所が何か（語・文字列・コメント、どの関数の何番目の引数か）を読み、
 * 候補（キーワード・関数・自分で定めた関数と変数・メソッド・場所の名前・物の ID）を集める。
 */
final class ProgramAssist {
    enum Kind { KEYWORD, FUNCTION, VARIABLE, METHOD, ATTRIBUTE, PLACE, ITEM, TAG }

    /** 候補。insert は選んだときに入れる文字（打っている途中の語を置き換える）。 */
    record Candidate(String label, String insert, Kind kind, @Nullable Item item) {}

    /** 呼んでいる関数と、カーソルが何番目の引数にあるか。method は {@code .} のあとの呼び出し。 */
    record Call(String name, int argument, boolean method) {}

    /** カーソルの所の様子。prefix は打っている途中の語（文字列の中なら引用符のあと）。 */
    record Context(String prefix, boolean inString, boolean inComment, boolean afterDot, @Nullable Call call) {}

    private static final Pattern ASSIGN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:[-+*/%]|//|\\*\\*)?=(?!=)");
    private static final Pattern FOR = Pattern.compile("\\bfor\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b");
    private static final Pattern DEF = Pattern.compile("^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)");
    static final int MAX_CANDIDATES = 40;

    private ProgramAssist() {}

    /** カーソルの前の文字（その行の頭から）を読んで、様子を返す。 */
    static Context context(String line, int col) {
        String before = line.substring(0, Math.min(col, line.length()));
        Deque<Object[]> calls = new ArrayDeque<>();      // {名前, 引数の番号, メソッドか}
        int i = 0;
        int stringStart = -1;
        char quote = 0;
        while (i < before.length()) {
            char c = before.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                    stringStart = -1;
                }
                i++;
                continue;
            }
            if (c == '#') {
                return new Context("", false, true, false, null);
            }
            if (c == '"' || c == '\'') {
                quote = c;
                stringStart = i;
            } else if (c == '(' || c == '[') {
                String name = c == '(' ? identifierBefore(before, i) : "";
                boolean method = c == '(' && !name.isEmpty() && i - name.length() - 1 >= 0 && before.charAt(i - name.length() - 1) == '.';
                calls.push(new Object[] {name, 0, method});
            } else if (c == ')' || c == ']') {
                if (!calls.isEmpty()) {
                    calls.pop();
                }
            } else if (c == ',' && !calls.isEmpty()) {
                calls.peek()[1] = (int) calls.peek()[1] + 1;
            }
            i++;
        }
        // リスト・かっこの中（名前のない枠）なら、その外側の呼び出しの、その引数とみなす（mobs(p, 5, ["hostile", "!zo… の候補）
        Call call = null;
        for (Object[] frame : calls) {
            if (!((String) frame[0]).isEmpty()) {
                call = new Call((String) frame[0], (int) frame[1], (boolean) frame[2]);
                break;
            }
        }
        if (quote != 0) {
            return new Context(before.substring(stringStart + 1), true, false, false, call);
        }
        int s = before.length();
        while (s > 0 && (Character.isLetterOrDigit(before.charAt(s - 1)) || before.charAt(s - 1) == '_')) {
            s--;
        }
        String prefix = before.substring(s);
        boolean dot = s > 0 && before.charAt(s - 1) == '.';
        if (!prefix.isEmpty() && Character.isDigit(prefix.charAt(0))) {
            prefix = "";                                   // 数を打っている
        }
        return new Context(prefix, false, false, dot, call);
    }

    private static String identifierBefore(String s, int at) {
        int e = at;
        while (e > 0 && s.charAt(e - 1) == ' ') {
            e--;
        }
        int b = e;
        while (b > 0 && (Character.isLetterOrDigit(s.charAt(b - 1)) || s.charAt(b - 1) == '_')) {
            b--;
        }
        return s.substring(b, e);
    }

    /** 候補を集める。forced なら打った語がなくても出す（Ctrl+Space）。 */
    static List<Candidate> candidates(Context ctx, String source, List<String> places, boolean forced) {
        List<Candidate> out = new ArrayList<>();
        if (ctx.inComment()) {
            return out;
        }
        String p = ctx.prefix();
        if (ctx.inString()) {
            Call call = ctx.call();
            if (call == null || call.method()) {
                return out;
            }
            if (ProgramHelp.placeArgument(call.name(), call.argument())) {
                for (String place : places) {
                    if (matches(place, p)) {
                        out.add(new Candidate(place, place, Kind.PLACE, null));
                    }
                }
            } else if (ProgramHelp.filterArgument(call.name(), call.argument())) {
                mobFilters(p, out);
            } else if (ProgramHelp.itemArgument(call.name(), call.argument())) {
                items(p, out);
            }
            return trim(out, p);
        }
        if (p.isEmpty() && !forced) {
            return out;
        }
        if (ctx.afterDot()) {
            for (String m : ProgramHelp.METHODS.keySet()) {
                if (matches(m, p)) {
                    out.add(new Candidate(m, m, Kind.METHOD, null));
                }
            }
            for (String a : ProgramHelp.MOB_ATTRIBUTES) {
                if (matches(a, p)) {
                    out.add(new Candidate(a, a, Kind.ATTRIBUTE, null));
                }
            }
            for (String a : ProgramHelp.MESSAGE_ATTRIBUTES) {
                if (matches(a, p)) {
                    out.add(new Candidate(a, a, Kind.ATTRIBUTE, null));
                }
            }
            return trim(out, p);
        }
        Set<String> seen = new LinkedHashSet<>();
        Set<String> defs = new LinkedHashSet<>();
        Set<String> vars = new LinkedHashSet<>();
        for (String l : source.split("\n", -1)) {
            Matcher d = DEF.matcher(l);
            if (d.find()) {
                defs.add(d.group(1));
                for (String param : d.group(2).split(",")) {
                    if (!param.isBlank()) {
                        vars.add(param.strip());
                    }
                }
            }
            Matcher a = ASSIGN.matcher(l);
            if (a.find()) {
                vars.add(a.group(1));
            }
            Matcher f = FOR.matcher(l);
            while (f.find()) {
                vars.add(f.group(1));
            }
        }
        for (String v : vars) {
            if (!v.equals(p) && matches(v, p) && seen.add(v)) {
                out.add(new Candidate(v, v, Kind.VARIABLE, null));
            }
        }
        for (String fn : defs) {
            if (matches(fn, p) && seen.add(fn)) {
                out.add(new Candidate(fn, fn, Kind.FUNCTION, null));
            }
        }
        for (String fn : Library.FUNCTIONS.keySet()) {
            if (matches(fn, p) && seen.add(fn)) {
                out.add(new Candidate(fn, fn, Kind.FUNCTION, null));
            }
        }
        for (String k : ProgramHelp.KEYWORDS) {
            if (matches(k, p) && seen.add(k)) {
                out.add(new Candidate(k, k, Kind.KEYWORD, null));
            }
        }
        // 打ち終えた語そのものしか合わなければ、出さない
        if (out.size() == 1 && out.get(0).insert().equals(p)) {
            out.clear();
        }
        return trim(out, p);
    }

    private static boolean matches(String candidate, String prefix) {
        return prefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(prefix.toLowerCase(Locale.ROOT));
    }

    /** 頭が合うものを先に、短いものを先に並べ、上限まで。 */
    private static List<Candidate> trim(List<Candidate> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        list.sort(java.util.Comparator.comparingInt((Candidate c) -> c.label().toLowerCase(Locale.ROOT).startsWith(p) ? 0 : 1)
                .thenComparingInt(c -> c.kind() == Kind.ITEM ? c.label().length() : 0));
        return list.size() > MAX_CANDIDATES ? new ArrayList<>(list.subList(0, MAX_CANDIDATES)) : list;
    }

    /** モブの絞り込みの候補: 言葉・モブの ID（ID か表示名の一部で合わせる。スポーンエッグがあれば、その絵）・タグ。! のあとも同じ。 */
    private static void mobFilters(String prefix, List<Candidate> out) {
        String not = prefix.startsWith("!") ? "!" : "";
        String p = prefix.substring(not.length()).toLowerCase(Locale.ROOT);
        for (String k : ProgramHelp.MOB_KINDS) {
            if (matches(k, p)) {
                out.add(new Candidate(not + k, not + k, Kind.KEYWORD, null));
            }
        }
        if (p.startsWith("#")) {
            String t = p.substring(1);
            BuiltInRegistries.ENTITY_TYPE.getTagNames().map(k -> k.location().toString()).filter(id -> id.contains(t)).sorted()
                    .limit(MAX_CANDIDATES).forEach(id -> out.add(new Candidate(not + "#" + id, not + "#" + id, Kind.TAG, null)));
            return;
        }
        if (p.isEmpty()) {
            return;
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) {
                continue;                                // 矢・額縁など、モブでないもの
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String text = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
            String name = type.getDescription().getString().toLowerCase(Locale.ROOT);
            if (!(text.contains(p) || name.contains(p))) {
                continue;
            }
            Item egg = net.minecraft.world.item.SpawnEggItem.byId(type);
            out.add(new Candidate(not + text, not + text, Kind.ITEM, egg));
            if (out.size() >= MAX_CANDIDATES * 2) {
                break;
            }
        }
    }

    /** 物の ID の候補（ID の一部か、表示名の一部で合わせる。minecraft: は省く）。 */
    private static void items(String prefix, List<Candidate> out) {
        String p = prefix.toLowerCase(Locale.ROOT);
        if (p.startsWith("#")) {
            String t = p.substring(1);
            BuiltInRegistries.ITEM.getTagNames().map(k -> k.location().toString()).filter(id -> id.contains(t)).sorted()
                    .limit(MAX_CANDIDATES).forEach(id -> out.add(new Candidate("#" + id, "#" + id, Kind.TAG, null)));
            return;
        }
        if (p.length() < 2) {
            return;                                     // 1文字では多すぎる
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String text = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
            String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            if (item == net.minecraft.world.item.Items.AIR || !(text.contains(p) || name.contains(p))) {
                continue;
            }
            out.add(new Candidate(text, text, Kind.ITEM, item));
            if (out.size() >= MAX_CANDIDATES * 4) {
                break;
            }
        }
    }

    /** 書いた文のうち、場所の名前にない名前を引数にしている行（ステーションから開いたとき）。{行, 名前} の並び。 */
    static List<Object[]> unknownPlaces(String source, List<String> places) {
        List<Object[]> out = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String l = lines[n];
            int i = 0;
            while (i < l.length()) {
                char c = l.charAt(i);
                if (c == '#') {
                    break;
                }
                if (c == '"' || c == '\'') {
                    i = stringEnd(l, i);                    // 文字列の中の go("…") は呼び出しではない
                    continue;
                }
                if (!Character.isLetter(c) && c != '_') {
                    i++;
                    continue;
                }
                int b = i;
                while (i < l.length() && (Character.isLetterOrDigit(l.charAt(i)) || l.charAt(i) == '_')) {
                    i++;
                }
                String word = l.substring(b, i);
                boolean member = b > 0 && l.charAt(b - 1) == '.';
                if (member || !ProgramHelp.PLACE_FIRST.contains(word)) {
                    continue;
                }
                int j = i;
                while (j < l.length() && l.charAt(j) == ' ') {
                    j++;
                }
                if (j >= l.length() || l.charAt(j) != '(') {
                    continue;
                }
                j++;
                while (j < l.length() && l.charAt(j) == ' ') {
                    j++;
                }
                if (j < l.length() && (l.charAt(j) == '"' || l.charAt(j) == '\'')) {
                    int e = stringEnd(l, j);
                    if (e <= l.length() && e - 1 > j && l.charAt(e - 1) == l.charAt(j)) {
                        String name = l.substring(j + 1, e - 1);
                        if (!places.contains(name)) {
                            out.add(new Object[] {n + 1, name});
                        }
                    }
                    i = e;
                }
            }
        }
        return out;
    }

    /** 文字列（at の引用符から）の終わりの次の位置。閉じていなければ行の長さ。 */
    private static int stringEnd(String l, int at) {
        char q = l.charAt(at);
        int i = at + 1;
        while (i < l.length()) {
            char c = l.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == q) {
                return i + 1;
            }
            i++;
        }
        return l.length();
    }
}
