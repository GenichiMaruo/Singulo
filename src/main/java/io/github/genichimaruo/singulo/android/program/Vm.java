package io.github.genichimaruo.singulo.android.program;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * プログラムを動かす。{@link #run} は、決まった手数（budget）を使い切るか、アンドロイドの動き（{@link Library.Sig#action()}）を
 * 頼んで待つか、終わるか、誤りで止まるまで進む。動きを頼んだら、終わったときに {@link #resume} で結果を渡すと続きから動く。
 * 変数・リストはこの中だけに持つ（保存しない。ワールドを開き直すと、プログラムは最初から動く）。
 */
public final class Vm {
    /** host が「時間がかかるので、あとで結果を渡す」と返す印。 */
    public static final Object PENDING = new Object();
    /** 呼び出しの深さ・式の途中の値の数・変数の数の上限。 */
    public static final int MAX_FRAMES = 32, MAX_STACK = 256, MAX_VARIABLES = 256;
    /** アンドロイドに頼む関数1回の手数。 */
    static final int HOST_COST = 10;

    public enum State { RUNNING, WAITING, DONE, ERROR }

    /** アンドロイドの側（Library で host のもの）。 */
    public interface Host {
        /** 結果か {@link #PENDING}。誤りは ProgramError を投げる。 */
        Object call(String name, List<Object> args);

        /** 世界の物（モブ）の属性。 */
        default Object attribute(Object target, String name) {
            throw new ProgramError("no_attribute", Values.typeName(target), name);
        }
    }

    private record Frame(Code.Fn fn, int returnPc, Map<String, Object> locals, int stackBase) {}

    private final Code code;
    private final ArrayList<Object> stack = new ArrayList<>();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Map<String, Object> globals = new HashMap<>();
    private final java.util.Random random;
    private int pc;
    private State state = State.RUNNING;
    private ProgramError error;
    private long steps;

    Vm(Code code, long seed) {
        this.code = code;
        this.random = new java.util.Random(seed);
    }

    public State state() {
        return state;
    }

    /** 誤りで止まったときの誤り。 */
    public ProgramError error() {
        return error;
    }

    /** 今の（次に動かす）文の行。 */
    public int line() {
        int at = Math.max(0, Math.min(code.size() - 1, state == State.WAITING ? pc - 1 : pc));
        return code.line[at];
    }

    /** これまでに動かした手数。 */
    public long steps() {
        return steps;
    }

    /** 頼んだ動きの結果を渡して、続きから動けるようにする。 */
    public void resume(Object result) {
        if (state != State.WAITING) {
            return;
        }
        stack.add(result);
        state = State.RUNNING;
    }

    /** 外から止める（誤りとして）。 */
    public void fail(ProgramError e) {
        error = e.at(line());
        state = State.ERROR;
    }

    public State run(Host host, int budget) {
        if (state != State.RUNNING) {
            return state;
        }
        try {
            while (budget > 0) {
                if (step(host)) {
                    budget -= HOST_COST;
                } else {
                    budget--;
                }
                steps++;
                if (state != State.RUNNING) {
                    break;
                }
            }
        } catch (ProgramError e) {
            error = e.at(code.line[Math.max(0, Math.min(code.size() - 1, pc - 1))]);
            state = State.ERROR;
        } catch (StackOverflowError | ClassCastException | IndexOutOfBoundsException e) {
            error = new ProgramError(code.line[Math.max(0, Math.min(code.size() - 1, pc - 1))], "internal");
            state = State.ERROR;
        }
        return state;
    }

    private Object pop() {
        return stack.remove(stack.size() - 1);
    }

    private void push(Object v) {
        if (stack.size() >= MAX_STACK) {
            throw new ProgramError("too_deep");
        }
        stack.add(v);
    }

    private List<Object> popArgs(int n) {
        List<Object> args = new ArrayList<>(stack.subList(stack.size() - n, stack.size()));
        for (int i = 0; i < n; i++) {
            stack.remove(stack.size() - 1);
        }
        return args;
    }

    /** 1つの命令を動かす。アンドロイドの関数を呼んだら true。 */
    private boolean step(Host host) {
        int at = pc++;
        int a = code.a[at], b = code.b[at];
        switch (code.op[at]) {
            case Code.CONST -> push(code.consts[a]);
            case Code.LOAD -> push(load(code.names[a]));
            case Code.STORE -> store(code.names[a], pop(), b == 1);
            case Code.POP -> pop();
            case Code.BIN -> {
                Object r = pop(), l = pop();
                push(Values.binary(a, l, r));
            }
            case Code.NEG -> push(Values.negate(pop()));
            case Code.PLUS -> {
                Object v = pop();
                if (!Values.isNumber(v)) {
                    throw new ProgramError("bad_operand", "+", Values.typeName(v));
                }
                push(v instanceof Boolean ? Values.asLong(v) : v);
            }
            case Code.NOT -> push(!Values.truthy(pop()));
            case Code.JUMP -> pc = a;
            case Code.JF -> {
                if (!Values.truthy(pop())) {
                    pc = a;
                }
            }
            case Code.JF_KEEP -> {
                if (!Values.truthy(stack.get(stack.size() - 1))) {
                    pc = a;
                } else {
                    pop();
                }
            }
            case Code.JT_KEEP -> {
                if (Values.truthy(stack.get(stack.size() - 1))) {
                    pc = a;
                } else {
                    pop();
                }
            }
            case Code.CALL -> {
                return call(host, code.names[a], b);
            }
            case Code.METHOD -> {
                List<Object> args = popArgs(b);
                Object self = pop();
                push(Builtins.method(self, code.names[a], args));
            }
            case Code.ATTR -> {
                Object v = pop();
                if (v instanceof Values.Mob) {
                    push(host.attribute(v, code.names[a]));
                    return true;
                }
                push(Builtins.attribute(v, code.names[a]));
            }
            case Code.INDEX -> {
                Object i = pop(), v = pop();
                push(Values.index(v, i));
            }
            case Code.SET_INDEX -> {
                Object v = pop(), i = pop(), target = pop();
                Values.setIndex(target, i, v);
            }
            case Code.DUP2 -> {
                Object y = stack.get(stack.size() - 1), x = stack.get(stack.size() - 2);
                push(x);
                push(y);
            }
            case Code.LIST -> push(new Values.PList(popArgs(a)));
            case Code.LEN -> push(Values.length(pop()));
            case Code.RET -> {
                Object v = pop();
                Frame f = frames.pop();
                while (stack.size() > f.stackBase) {
                    stack.remove(stack.size() - 1);
                }
                pc = f.returnPc;
                push(v);
            }
            case Code.HALT -> {
                pc = at;
                state = State.DONE;
            }
            default -> throw new ProgramError("internal");
        }
        return false;
    }

    private boolean call(Host host, String name, int argc) {
        List<Object> args = popArgs(argc);
        Code.Fn fn = code.functions.get(name);
        if (fn != null) {
            if (frames.size() >= MAX_FRAMES) {
                throw new ProgramError("too_deep");
            }
            Map<String, Object> locals = new HashMap<>();
            for (int i = 0; i < fn.params().length; i++) {
                locals.put(fn.params()[i], args.get(i));
            }
            frames.push(new Frame(fn, pc, locals, stack.size()));
            pc = fn.entry();
            return false;
        }
        Library.Sig sig = Library.FUNCTIONS.get(name);
        if (sig == null) {
            throw new ProgramError("unknown_function", name);
        }
        if (!sig.host()) {
            push(Builtins.call(name, args, random));
            return false;
        }
        Object r = host.call(name, args);
        if (r == PENDING) {
            state = State.WAITING;
        } else {
            push(r);
        }
        return true;
    }

    private Object load(String name) {
        Frame f = frames.peek();
        if (f != null && f.locals.containsKey(name)) {
            return f.locals.get(name);
        }
        if (globals.containsKey(name)) {
            return globals.get(name);
        }
        throw new ProgramError("undefined", name);
    }

    private void store(String name, Object v, boolean global) {
        Frame f = frames.peek();
        Map<String, Object> scope = f == null || global ? globals : f.locals;
        if (!scope.containsKey(name) && scope.size() >= MAX_VARIABLES) {
            throw new ProgramError("too_big");
        }
        scope.put(name, v);
    }

    /** 変数を読む（試験用）。 */
    Object global(String name) {
        return globals.get(name);
    }
}
