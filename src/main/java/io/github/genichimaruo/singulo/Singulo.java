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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;

import org.slf4j.Logger;

@Mod(Singulo.MODID)
public final class Singulo {
    public static final String MODID = "singulo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Singulo() {
        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        SinguloFluids.FLUID_TYPES.register(modBus);
        io.github.genichimaruo.singulo.compat.LegacyAttributes.REGISTER.register(modBus);
        modBus.addListener(io.github.genichimaruo.singulo.compat.LegacyAttributes::add);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.compat.LegacyAttributes::fall);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.compat.LegacyAttributes::flight);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.compat.LegacyAttributes::explosion);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.compat.LegacyAttributes::afterExplosions);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> io.github.genichimaruo.singulo.client.SinguloClient::init);
        SinguloFluids.FLUIDS.register(modBus);
        SinguloBlocks.BLOCKS.register(modBus);
        SinguloItems.ITEMS.register(modBus);
        SinguloBlockEntities.REGISTER.register(modBus);
        SinguloEntities.REGISTER.register(modBus);
        io.github.genichimaruo.singulo.registry.SinguloSounds.REGISTER.register(modBus);
        SinguloMenus.REGISTER.register(modBus);
        SinguloRecipes.TYPES.register(modBus);
        SinguloRecipes.SERIALIZERS.register(modBus);
        SinguloCreativeTab.REGISTER.register(modBus);

        modBus.addListener(SinguloEntities::registerAttributes);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            SinguloCapabilities.register(new io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent());
            io.github.genichimaruo.singulo.network.SinguloNetwork.register();
            io.github.genichimaruo.singulo.registry.SinguloTriggers.register();
        }));
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class, (net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> e) -> io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent.attach(e));
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.item.ItemStack.class, (net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.item.ItemStack> e) -> io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent.attach(e));
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.compat.PlayerData::clone);
        net.minecraftforge.common.world.ForgeChunkManager.setForcedChunkLoadingCallback(MODID, (level, helper) ->
                helper.getBlockTickets().keySet().forEach(pos -> {
                    if (!(level.getBlockEntity(pos) instanceof WorldlineAnchorBlockEntity)) helper.removeAllTickets(pos);
                }));
        MinecraftForge.EVENT_BUS.addListener(SinguloReloadListeners::register);
        MinecraftForge.EVENT_BUS.addListener(InertialStabilizerBlockEntity::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onExplosionStart);
        MinecraftForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onExplosionDetonate);
        MinecraftForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onMobGriefing);
        MinecraftForge.EVENT_BUS.addListener(ShieldTowerBlockEntity::onSpawnCheck);
        io.github.genichimaruo.singulo.machine.ShieldPermits.register();
        MinecraftForge.EVENT_BUS.addListener(MetricDriveItem::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.GravityBootsItem::onFall);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.GravitonManipulatorItem::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.multiblock.MultiblockInteraction::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.ruin.SealedContainerBlock::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.gravity.GravityEffects::onEntityTick);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.nature.Fulgurite::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.item.HandbookItem::onLogin);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.ruin.AncientRecords::onLogin);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.command.SinguloCommands::register);
        MinecraftForge.EVENT_BUS.addListener(io.github.genichimaruo.singulo.multiblock.FormationEffect::onServerTick);
        if (RuinSelfCheck.enabled()) {
            MinecraftForge.EVENT_BUS.addListener(RuinSelfCheck::onServerStarted);
        }

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
