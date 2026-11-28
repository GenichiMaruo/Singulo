package io.github.genichimaruo.singulo.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 通信の登録。 */
public final class SinguloNetwork {
    private SinguloNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        // The assembler menu indices and synchronized machine recipe format changed with the manual board.
        r.versioned("2").playToServer(MachineRecipeTransferPayload.TYPE, MachineRecipeTransferPayload.STREAM_CODEC, MachineRecipeTransferPayload::handle);
        r.playToServer(ToggleAreaPayload.TYPE, ToggleAreaPayload.STREAM_CODEC, ToggleAreaPayload::handle);
        r.playToServer(GlassesHeightPayload.TYPE, GlassesHeightPayload.STREAM_CODEC, GlassesHeightPayload::handle);
        r.playToClient(RecordsPayload.TYPE, RecordsPayload.STREAM_CODEC, RecordsPayload::handle);
        r.playToClient(GalaxyPayload.TYPE, GalaxyPayload.STREAM_CODEC, GalaxyPayload::handle);
        r.versioned("2").playToClient(MonitorPayload.TYPE, MonitorPayload.STREAM_CODEC, MonitorPayload::handle);
        r.playToServer(MonitorRequestPayload.TYPE, MonitorRequestPayload.STREAM_CODEC, MonitorRequestPayload::handle);
        r.playToClient(EndingPayload.TYPE, EndingPayload.STREAM_CODEC, EndingPayload::handle);
        r.playToClient(ScanPayload.TYPE, ScanPayload.STREAM_CODEC, ScanPayload::handle);
        r.playToServer(SideConfigPayload.TYPE, SideConfigPayload.STREAM_CODEC, SideConfigPayload::handle);
        r.playToServer(ManipulatorInputPayload.TYPE, ManipulatorInputPayload.STREAM_CODEC, ManipulatorInputPayload::handle);
        r.playToServer(RenameAndroidPayload.TYPE, RenameAndroidPayload.STREAM_CODEC, RenameAndroidPayload::handle);
        r.playToServer(io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload.TYPE, io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload.STREAM_CODEC,
                io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload::handle);
        r.playToClient(io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload.TYPE, io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload.STREAM_CODEC,
                io.github.genichimaruo.singulo.android.work.WorkConsoleViewPayload::handle);
        r.playToServer(SaveProgramPayload.TYPE, SaveProgramPayload.STREAM_CODEC, SaveProgramPayload::handle);
    }
}
