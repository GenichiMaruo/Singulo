package io.github.genichimaruo.singulo;

import com.mojang.logging.LogUtils;
import io.github.genichimaruo.singulo.data.SinguloReloadListeners;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.MetricDriveItem;
import io.github.genichimaruo.singulo.machine.InertialStabilizerBlockEntity;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloCapabilities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloCreativeTab;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import io.github.genichimaruo.singulo.ruin.RuinSelfCheck;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import org.slf4j.Logger;

@Mod(Singulo.MODID)
public final class Singulo {
    public static final String MODID = "singulo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Singulo(IEventBus modBus, ModContainer container) {
        SinguloComponents.REGISTER.register(modBus);
        io.github.genichimaruo.singulo.registry.SinguloTriggers.REGISTER.register(modBus);
        SinguloFluids.FLUID_TYPES.register(modBus);
        SinguloFluids.FLUIDS.register(modBus);
        SinguloBlocks.BLOCKS.register(modBus);
        SinguloItems.ITEMS.register(modBus);
        SinguloBlockEntities.REGISTER.register(modBus);
        SinguloEntities.REGISTER.register(modBus);
        io.github.genichimaruo.singulo.registry.SinguloSounds.REGISTER.register(modBus);
        RuinDiscovery.REGISTER.register(modBus);
        io.github.genichimaruo.singulo.item.DimensionalPocketItem.REGISTER.register(modBus);
        io.github.genichimaruo.singulo.item.GravityBootsItem.MATERIALS.register(modBus);
        SinguloMenus.REGISTER.register(modBus);
        SinguloRecipes.TYPES.register(modBus);
        SinguloRecipes.SERIALIZERS.register(modBus);
        SinguloCreativeTab.REGISTER.register(modBus);

        modBus.addListener(SinguloCapabilities::register);
        modBus.addListener(io.github.genichimaruo.singulo.network.SinguloNetwork::register);
        io.github.genichimaruo.singulo.ruin.AncientRecords.REGISTER.register(modBus);
        modBus.addListener(SinguloEntities::registerAttributes);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(WorldlineAnchorBlockEntity.TICKETS));
        NeoForge.EVENT_BUS.addListener(SinguloReloadListeners::register);
        NeoForge.EVENT_BUS.addListener(InertialStabilizerBlockEntity::onExplosion);
        NeoForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onExplosionStart);
        NeoForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onSpawnCheck);
        io.github.genichimaruo.singulo.machine.ShieldPermits.register();
        NeoForge.EVENT_BUS.addListener(MetricDriveItem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.GravityBootsItem::onFall);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.GravitonManipulatorItem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.multiblock.MultiblockInteraction::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.ruin.SealedContainerBlock::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.gravity.GravityEffects::onEntityTick);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.nature.Fulgurite::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.HandbookItem::onLogin);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.ruin.AncientRecords::onLogin);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.command.SinguloCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.multiblock.FormationEffect::onServerTick);
        if (RuinSelfCheck.enabled()) {
            NeoForge.EVENT_BUS.addListener(RuinSelfCheck::onServerStarted);
        }

        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
