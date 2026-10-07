package io.github.genichimaruo.singulo.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 通信の登録。 */
public final class SinguloNetwork {
    private SinguloNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ToggleAreaPayload.TYPE, ToggleAreaPayload.STREAM_CODEC, ToggleAreaPayload::handle);
        r.playToClient(RecordsPayload.TYPE, RecordsPayload.STREAM_CODEC, RecordsPayload::handle);
        r.playToClient(ScanPayload.TYPE, ScanPayload.STREAM_CODEC, ScanPayload::handle);
        r.playToServer(SideConfigPayload.TYPE, SideConfigPayload.STREAM_CODEC, SideConfigPayload::handle);
        r.playToServer(ManipulatorLeftPayload.TYPE, ManipulatorLeftPayload.STREAM_CODEC, ManipulatorLeftPayload::handle);
    }
}
