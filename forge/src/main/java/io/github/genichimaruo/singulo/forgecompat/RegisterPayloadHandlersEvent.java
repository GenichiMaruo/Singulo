package io.github.genichimaruo.singulo.forgecompat;

/** Forge 版: NeoForge の RegisterPayloadHandlersEvent と同じ書き方で、通信を登録する（中身は {@link Net}）。 */
public final class RegisterPayloadHandlersEvent {
    private final PayloadRegistrar registrar = new PayloadRegistrar();

    public PayloadRegistrar registrar(String version) {
        return registrar;
    }

    PayloadRegistrar registrar() {
        return registrar;
    }
}
