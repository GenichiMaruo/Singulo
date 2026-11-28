package io.github.genichimaruo.singulo.android.program;

import javax.annotation.Nullable;

/**
 * アンドロイドのプログラム（Python に似た書き方。0.24.0）。文を訳したもので、{@link #start} で動かし始める。
 * 書き方は {@link Compiler}、関数は {@link Library}、動き方は {@link Vm}。
 */
public final class Program {
    /** 文の長さの上限（文字）。 */
    public static final int MAX_SOURCE = Compiler.MAX_SOURCE;

    private final String source;
    private final Code code;

    private Program(String source, Code code) {
        this.source = source;
        this.code = code;
    }

    /** 訳す。書き方が誤っていれば ProgramError。 */
    public static Program compile(String source) {
        return new Program(source, Compiler.compile(source));
    }

    /** 書き方の誤り（なければ null）。 */
    @Nullable
    public static ProgramError check(String source) {
        try {
            Compiler.compile(source);
            return null;
        } catch (ProgramError e) {
            return e;
        }
    }

    public String source() {
        return source;
    }

    public Vm start(long seed) {
        return new Vm(code, seed);
    }
}
