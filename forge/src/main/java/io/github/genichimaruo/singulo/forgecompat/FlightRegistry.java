package io.github.genichimaruo.singulo.forgecompat;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 版: NeoForge の「飛べる」能力値（NeoForgeMod.CREATIVE_FLIGHT）の代わり。値が 0 より大きい間はプレイヤーが飛べる。
 * 能力値そのものは {@link Flight#CREATIVE_FLIGHT}（登録が済んだあとに読む）。
 */
public final class FlightRegistry {
    private FlightRegistry() {}

    private static final net.minecraftforge.registries.DeferredRegister<Attribute> REGISTER =
            net.minecraftforge.registries.DeferredRegister.create(Registries.ATTRIBUTE, Singulo.MODID);
    static final RegistryObject<Attribute> CREATIVE_FLIGHT = REGISTER.register("creative_flight",
            () -> new RangedAttribute("attribute.name.singulo.creative_flight", 0, 0, 1).setSyncable(true));
    private static final String GRANTED = "singulo_granted_flight";

    /** Mod の読み込み中に一度だけ呼ぶ。 */
    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent e) -> e.add(EntityType.PLAYER, CREATIVE_FLIGHT.getHolder().orElseThrow()));
        MinecraftForge.EVENT_BUS.addListener(FlightRegistry::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        boolean wanted = player.getAttributeValue(Flight.CREATIVE_FLIGHT) > 0;
        boolean granted = player.getPersistentData().getBoolean(GRANTED);
        Abilities abilities = player.getAbilities();
        if (wanted && !abilities.mayfly) {
            abilities.mayfly = true;
            player.getPersistentData().putBoolean(GRANTED, true);
            player.onUpdateAbilities();
        } else if (!wanted && granted) {
            player.getPersistentData().remove(GRANTED);
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
        }
    }
}
