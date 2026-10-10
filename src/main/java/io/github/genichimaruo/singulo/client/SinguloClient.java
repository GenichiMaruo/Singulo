package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;


public final class SinguloClient {
    private static final ResourceLocation STILL = new ResourceLocation("block/water_still");
    private static final ResourceLocation FLOWING = new ResourceLocation("block/water_flow");

    public static void init() { new SinguloClient(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()); }

    public SinguloClient(IEventBus modBus) {


        modBus.addListener(SinguloClient::registerRenderers);
        modBus.addListener(SinguloClient::registerLayers);
        modBus.addListener(GravitationalLensing::registerShaders);
        modBus.addListener(StaffTipTracker::wrapModel);
        modBus.addListener(SinguloClient::addPacks);
        modBus.addListener(SinguloClient::clientSetup);
        modBus.addListener(SinguloKeys::register);
        MinecraftForge.EVENT_BUS.addListener(SinguloClient::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(GravitationalLensing::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(GravitationalLensing::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(HologramRenderer::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(NeutrinoOverlay::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(SinguloKeys::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(BlackHolePull::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(BlackHoleAmbience::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(MachineSounds::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(BossSounds::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(GravityBootsClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(SinguloKeys::onInteraction);
        if (Boolean.getBoolean("singulo.clientSmokeTest")) {
            MinecraftForge.EVENT_BUS.addListener(SinguloClient::smokeTest);
        }
    }

    private static void smokeTest(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END
                && minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                && minecraft.getOverlay() == null) {
            org.slf4j.LoggerFactory.getLogger(SinguloClient.class).info("Singulo client smoke test passed: title screen and resources loaded");
            minecraft.stop();
        }
    }

    /** 型として使っている残響の欠片（バニラのアイテム）に使用回数を出す。Singulo のアイテムには説明と作り方・使い道を出す。 */
    private static void onTooltip(ItemTooltipEvent event) {
        describe(event);
        if (Boolean.TRUE.equals(SinguloComponents.get(event.getItemStack(), SinguloComponents.STABILIZED.get()))) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.catalyst_stabilized").withStyle(ChatFormatting.GOLD));
        }
        Integer dark = SinguloComponents.get(event.getItemStack(), SinguloComponents.DARK_MATTER.get());
        if (dark != null) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.dark_matter", dark).withStyle(ChatFormatting.GRAY));
        }
        UsesData data = SinguloComponents.get(event.getItemStack(), SinguloComponents.USES.get());
        if (data != null && event.getItemStack().is(Items.ECHO_SHARD)) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.uses", data.remaining(), data.max())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ACCELERATOR_CONTROLLER.get(),
                AcceleratorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.CABLE.get(),
                CableRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.KERAUNOS_TOWER.get(),
                KeraunosTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEALED_CONTAINER.get(),
                SealedContainerRenderer::new);
        event.registerEntityRenderer(SinguloEntities.SECURITY_DRONE.get(), SecurityDroneRenderer::new);
        event.registerEntityRenderer(SinguloEntities.HORIZON_WARDEN.get(), HorizonWardenRenderer::new);
        event.registerEntityRenderer(SinguloEntities.ECHO_SENTINEL.get(), EchoSentinelRenderer::new);
        event.registerEntityRenderer(SinguloEntities.GRAVITY_REMNANT.get(), GravityRemnantRenderer::new);
        event.registerEntityRenderer(SinguloEntities.GRAVITY_DEBRIS.get(), GravityDebrisRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ECHO_PROJECTOR.get(),
                EchoProjectorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.PENROSE_REACTOR.get(),
                PenroseReactorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEAL_CONSOLE.get(),
                ctx -> new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<>() {
                    // 重力異常点: 封印コンソールの上の空間がゆがんで見える
                    @Override
                    public void render(io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity be, float pt,
                                       com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf,
                                       int light, int overlay) {
                        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).add(0, 3.5, 0);
                        GravitationalLensing.add(c, 1.2F, 0.5F);
                    }

                    @Override
                    public boolean shouldRenderOffScreen(io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity be) {
                        return true;
                    }


                });
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SHIELD_TOWER.get(),
                ShieldTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.WORMHOLE_MOUTH.get(),
                WormholeMouthRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.TIPLER_CYLINDER.get(),
                TiplerCylinderRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.DEGENERATE_FURNACE.get(),
                DegenerateFurnaceRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.DEGENERATE_COMPACTOR_CONTROLLER.get(),
                DegenerateCompactorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.CASIMIR_CAVITY_CONTROLLER.get(),
                CasimirCavityRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.COOLING_TOWER_CONTROLLER.get(),
                CoolingTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.WORMHOLE_GENERATOR.get(),
                WormholeGeneratorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ROGUE_BLACK_HOLE.get(),
                RogueBlackHoleRenderer::new);
        event.registerEntityRenderer(SinguloEntities.HORIZON_BOLT.get(), HorizonBoltRenderer::new);
        event.registerEntityRenderer(SinguloEntities.WARDEN_SINGULARITY.get(), WardenSingularityRenderer::new);
        event.registerEntityRenderer(SinguloEntities.BLACK_HOLE_BOMB.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(SinguloEntities.MICRO_BLACK_HOLE.get(), MicroBlackHoleRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SecurityDroneRenderer.LAYER, SecurityDroneRenderer::createLayer);
        event.registerLayerDefinition(HorizonWardenRenderer.LAYER, HorizonWardenRenderer::createLayer);
    }

    private static void registerScreens() {
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.MACHINE.get(), MachineScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.THERMOELECTRIC_GENERATOR.get(), ThermoelectricGeneratorScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.CATALYST_DEVICE.get(), CatalystDeviceScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.PENROSE_REACTOR.get(), PenroseReactorScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.SMES.get(), SmesScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.WORMHOLE_STABILIZER.get(), WormholeStabilizerScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.DEVICE.get(), DeviceScreen::new);
    }

    /** 組み込みのリソースパック「Singulo HD」（32×32 のテクスチャ）。リソースパックの画面で選ぶと使える。 */
    private static void addPacks(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            var file = net.minecraftforge.fml.ModList.get().getModFileById(Singulo.MODID).getFile();
            var resources = new net.minecraft.server.packs.PathPackResources("singulo_hd", file.findResource("resourcepacks/singulo_hd"), false);
            var pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate("singulo_hd", Component.translatable("pack.singulo.hd"), false,
                    id -> resources, net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    net.minecraft.server.packs.repository.Pack.Position.TOP, net.minecraft.server.packs.repository.PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    /** 探索コンパスの針の向き（バニラのコンパスと同じ「angle」）。 */
    private static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(SinguloClient::registerScreens);
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get(), new ResourceLocation("angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) ->
                        io.github.genichimaruo.singulo.item.ExplorerCompassItem.pointing(stack))));
    }

    /** 説明（折り返し）と、Shift を押している間は作り方・使い道。 */
    private static void describe(ItemTooltipEvent event) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(event.getItemStack().getItem());
        if (!Singulo.MODID.equals(id.getNamespace())) {
            return;
        }
        String key = "desc.singulo." + id.getPath();
        if (!net.minecraft.client.resources.language.I18n.exists(key)) {
            return;
        }
        java.util.List<Component> tip = event.getToolTip();
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (net.minecraft.network.chat.FormattedText line : font.getSplitter().splitLines(Component.translatable(key), 220,
                net.minecraft.network.chat.Style.EMPTY)) {
            tip.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
        }
        boolean hasHowto = net.minecraft.client.resources.language.I18n.exists("howto.singulo." + id.getPath());
        boolean hasUses = net.minecraft.client.resources.language.I18n.exists("uses.singulo." + id.getPath());
        if (!hasHowto && !hasUses) {
            return;
        }
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            for (String k : new String[]{"howto", "uses"}) {
                String full = k + ".singulo." + id.getPath();
                if (net.minecraft.client.resources.language.I18n.exists(full)) {
                    for (net.minecraft.network.chat.FormattedText line : font.getSplitter().splitLines(Component.translatable(full),
                            220, net.minecraft.network.chat.Style.EMPTY)) {
                        tip.add(Component.literal(line.getString()).withStyle(ChatFormatting.DARK_AQUA));
                    }
                }
            }
        } else {
            tip.add(Component.translatable("tooltip.singulo.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static IClientFluidTypeExtensions fluidExtensions(int color) {
        return new IClientFluidTypeExtensions() {
            public ResourceLocation getStillTexture() { return STILL; }
            public ResourceLocation getFlowingTexture() { return FLOWING; }
            public int getTintColor() { return color; }
        };
    }
}
