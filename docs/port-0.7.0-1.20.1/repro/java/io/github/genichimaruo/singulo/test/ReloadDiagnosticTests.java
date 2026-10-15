package io.github.genichimaruo.singulo.test;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("singulo_reload_check") @PrefixGameTestTemplate(false)
public final class ReloadDiagnosticTests {
    @GameTest(template="empty", timeoutTicks=2400)
    public static void reload(GameTestHelper h) {
        AndroidGameTests.androidResumesCraftAfterReload(h);
        Port070DiagnosticTests.observe(h);
    }
    @GameTest(template="empty", batch="mine", timeoutTicks=2400)
    public static void mine(GameTestHelper h) {
        AndroidGameTests.androidMinesWithProcedure(h);
        Port070DiagnosticTests.observe(h);
    }
}
