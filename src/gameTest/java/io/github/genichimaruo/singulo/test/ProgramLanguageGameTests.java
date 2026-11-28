package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.program.Program;
import io.github.genichimaruo.singulo.android.program.ProgramError;
import io.github.genichimaruo.singulo.android.program.Values;
import io.github.genichimaruo.singulo.android.program.Vm;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** アンドロイドのプログラム（0.24.0）の言語: 訳す・動かす・誤りの行・手数の上限・動きを待つ。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ProgramLanguageGameTests {
    private static final String EMPTY = "empty";

    private ProgramLanguageGameTests() {}

    /** say / print の出力を集めるだけの host（wait は待つ）。 */
    private static final class Recorder implements Vm.Host {
        final List<String> out = new ArrayList<>();


        @Override
        public Object call(String name, List<Object> args) {
            switch (name) {
                case "say", "print" -> {
                    out.add(String.join(" ", args.stream().map(Values::str).toList()));
                    return null;
                }
                case "wait" -> {

                    return Vm.PENDING;
                }
                default -> throw new ProgramError("unknown_function", name);
            }
        }
    }

    private static List<String> run(GameTestHelper helper, String source) {
        Recorder host = new Recorder();
        Vm vm = Program.compile(source).start(1);
        for (int i = 0; i < 1000 && vm.state() == Vm.State.RUNNING; i++) {
            vm.run(host, 500);
        }
        if (vm.state() == Vm.State.ERROR) {
            throw new AssertionError("動かしている途中で誤り: " + vm.error().line() + " " + vm.error().key());
        }
        helper.assertTrue(vm.state() == Vm.State.DONE, "終わらない: " + vm.state());
        return host.out;
    }

    private static void expect(GameTestHelper helper, String source, String... lines) {
        List<String> out = run(helper, source);
        helper.assertTrue(out.equals(List.of(lines)), "出力が違う: " + out + " / 期待 " + List.of(lines));
    }

    @GameTest(template = EMPTY)
    public static void programArithmeticAndText(GameTestHelper helper) {
        expect(helper, """
                x = 7
                y = 2
                print(x + y, x - y, x * y, x / y, x // y, x % y, x ** y)
                print(-7 // 2, -7 % 2, 2 ** -1, 1 + 2.5)
                s = "ab" * 3
                print(s, len(s), s.upper(), "b" in s, s[1], s[-1])
                print(str(10) + "!", int("42") + 1, round(2.6), abs(-3))
                print(True + 1, not 0, None is None, 3 != 3.0)
                """,
                "9 5 14 3.5 3 1 49",
                "-4 1 0.5 3.5",
                "ababab 6 ABABAB True b b",
                "10! 43 3 3",
                "2 True True False");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void programControlFlowAndFunctions(GameTestHelper helper) {
        expect(helper, """
                def fib(n):
                    if n < 2:
                        return n
                    return fib(n - 1) + fib(n - 2)

                total = 0
                for i in range(10):
                    if i == 3:
                        continue
                    if i == 8:
                        break
                    total += i
                print(total, fib(10))

                n = 0
                while True:
                    n += 1
                    if n >= 5: break
                print(n)

                items = [3, 1, 2]
                items.append(5)
                items[0] = 9
                items[1] += 10
                print(items, sorted(items), sum(items), max(items), min(4, 2, 8))

                def bump():
                    global counter
                    counter += 1
                counter = 0
                bump()
                bump()
                print(counter)

                p = pos(1, 64, -2)
                print(p, p.y, p.up(2), p.north().x)
                if 1 > 2:
                    print("no")
                elif 2 > 1 and not False:
                    print("elif")
                else:
                    print("else")
                """,
                "25 55",
                "5",
                "[9, 11, 2, 5] [2, 5, 9, 11] 27 11 2",
                "2",
                "pos(1, 64, -2) 64 pos(1, 66, -2) 1",
                "elif");
        helper.succeed();
    }

    private static void compileError(GameTestHelper helper, String source, int line, String key) {
        ProgramError e = Program.check(source);
        helper.assertTrue(e != null, "誤りにならない: " + source);
        helper.assertTrue(e.line() == line && e.key().equals(key), "誤りが違う: " + e.line() + " " + e.key() + " / 期待 " + line + " " + key);
    }

    @GameTest(template = EMPTY)
    public static void programCompileErrorsPointAtTheLine(GameTestHelper helper) {
        compileError(helper, "x = 1\nif x\n    pass\n", 2, "expected");
        compileError(helper, "x = 1\n  y = 2\n", 2, "unexpected_indent");
        compileError(helper, "if True:\npass\n", 2, "expected_indent");
        compileError(helper, "for i in range(3):\n        pass\n    pass\n", 3, "dedent");
        compileError(helper, "say('hi)\n", 1, "unterminated_string");
        compileError(helper, "x = 1\nexplode()\n", 2, "unknown_function");
        compileError(helper, "go()\n", 1, "arg_count");
        compileError(helper, "break\n", 1, "outside_loop");
        compileError(helper, "return 1\n", 1, "outside_function");
        compileError(helper, "x = (1 +\n2\n", 2, "unclosed");
        compileError(helper, "while True:\n    def f():\n        pass\n", 2, "nested_def");
        compileError(helper, "import os\n", 1, "unsupported");
        helper.assertTrue(Program.check("# comment only\n\n") == null, "コメントだけの文が誤りになる");
        helper.succeed();
    }

    private static void runtimeError(GameTestHelper helper, String source, int line, String key) {
        Vm vm = Program.compile(source).start(1);
        vm.run(new Recorder(), 10_000);
        helper.assertTrue(vm.state() == Vm.State.ERROR, "誤りで止まらない: " + source);
        helper.assertTrue(vm.error().line() == line && vm.error().key().equals(key),
                "誤りが違う: " + vm.error().line() + " " + vm.error().key() + " / 期待 " + line + " " + key);
    }

    @GameTest(template = EMPTY)
    public static void programRuntimeErrorsStopAtTheLine(GameTestHelper helper) {
        runtimeError(helper, "x = 1\ny = x / 0\n", 2, "div_zero");
        runtimeError(helper, "print(1)\nprint(nothing)\n", 2, "undefined");
        runtimeError(helper, "a = [1]\nb = a[3]\n", 2, "index_range");
        runtimeError(helper, "x = 'a' - 1\n", 1, "bad_operands");
        runtimeError(helper, "def f(n):\n    return f(n + 1)\nf(0)\n", 2, "too_deep");
        runtimeError(helper, "a = []\nwhile True:\n    a.append(1)\n", 3, "too_big");
        runtimeError(helper, "x = 9223372036854775807\nx += 1\n", 2, "overflow");
        helper.succeed();
    }

    /** 動きを頼むと待ち、結果を渡すと続く。終わらないループも、手数を使い切ればそこで返る。 */
    @GameTest(template = EMPTY)
    public static void programWaitsForActionsAndYieldsEachTick(GameTestHelper helper) {
        Recorder host = new Recorder();
        Vm vm = Program.compile("print('a')\nwait(1)\nprint('b')\n").start(1);
        helper.assertTrue(vm.run(host, 500) == Vm.State.WAITING && vm.line() == 2, "動きを待たない");
        helper.assertTrue(vm.run(host, 500) == Vm.State.WAITING, "待っている間に先へ進む");
        vm.resume(null);
        helper.assertTrue(vm.run(host, 500) == Vm.State.DONE && host.out.equals(List.of("a", "b")), "続きから動かない: " + host.out);

        Vm loop = Program.compile("n = 0\nwhile True:\n    n += 1\n").start(1);
        helper.assertTrue(loop.run(host, 200) == Vm.State.RUNNING && loop.steps() <= 200, "手数を使い切っても返らない");
        helper.succeed();
    }
}
