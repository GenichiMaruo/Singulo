package io.github.genichimaruo.singulo.drone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Short-lived loading ticket: expires after 300 ticks and is never persisted to disk. */
@EventBusSubscriber(modid = "singulo")
public final class DroneTickets {
    private DroneTickets() {}
    public static final TicketType WORMHOLE_DRONE = new TicketType(300L,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TICKET_TYPE, helper -> helper.register(
                Identifier.fromNamespaceAndPath("singulo", "wormhole_drone"), WORMHOLE_DRONE));
    }
}
