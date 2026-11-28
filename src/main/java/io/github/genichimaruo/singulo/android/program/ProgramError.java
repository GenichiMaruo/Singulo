package io.github.genichimaruo.singulo.android.program;

import net.minecraft.network.chat.Component;

/**
 * プログラムの誤り（書き方の誤りと、動かしている途中の誤り）。行番号（1から。0 はまだ分からない）と、
 * 翻訳キー {@code program.singulo.error.<key>} とその引数を持つ。
 */
public final class ProgramError extends RuntimeException {
    private final int line;
    private final String key;
    private final String[] args;

    public ProgramError(int line, String key, Object... args) {
        super(key, null, false, false);
        this.line = line;
        this.key = key;
        this.args = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            this.args[i] = String.valueOf(args[i]);
        }
    }

    /** 行の分からない誤り（動かしている途中で、あとから行を付ける）。 */
    public ProgramError(String key, Object... args) {
        this(0, key, args);
    }

    public int line() {
        return line;
    }

    public String key() {
        return key;
    }

    public String[] args() {
        return args.clone();
    }

    /** 行がまだなければ、line を付けたもの。 */
    public ProgramError at(int line) {
        return this.line > 0 ? this : new ProgramError(line, key, (Object[]) args);
    }

    /** 誤りの説明（行番号なし）。 */
    public Component message() {
        return Component.translatable("program.singulo.error." + key, (Object[]) args);
    }

    /** 「3行目: …」。 */
    public Component describe() {
        return line > 0 ? Component.translatable("program.singulo.error_at", line, message()) : message();
    }
}
