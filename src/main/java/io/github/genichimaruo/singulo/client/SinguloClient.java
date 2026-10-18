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
        modBus.addListener(SinguloClient::registerDecorations);
        modBus.addListener(SinguloClient::registerItemColors);
        modBus.addListener(SinguloClient::registerRenderers);
        modBus.addListener(SinguloClient::registerLayers);
        modBus.addListener(SinguloClient::addWingLayers);
        modBus.addListener(GravitationalLensing::registerShaders);
        modBus.addListener(StaffTipTracker::wrapModel);
        modBus.addListener(SinguloClient::addPacks);
        modBus.addListener(SinguloClient::clientSetup);
        modBus.addListener(SinguloKeys::register);
        MinecraftForge.EVENT_BUS.addListener(SinguloClient::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(GravitationalLensing::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(GravitationalLensing::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(HologramRenderer::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(SurveyRenderer::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(SkyGalaxyRenderer::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                ClientGalaxies.set(java.util.List.of(), false));
        MinecraftForge.EVENT_BUS.addListener(NeutrinoOverlay::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(SinguloKeys::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(SinguloKeys::onScreenKey);
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
        // Mekanism の化学物質のためのアップグレードは、Mekanism がなければ使えないことを知らせる
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (Singulo.MODID.equals(id.getNamespace()) && (id.getPath().equals("android_gas_tank_upgrade") || id.getPath().equals("android_dual_tank_upgrade"))
                && !io.github.genichimaruo.singulo.android.work.Chemicals.present()) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.needs_mekanism").withStyle(ChatFormatting.RED));
        }
        UsesData data = SinguloComponents.get(event.getItemStack(), SinguloComponents.USES.get());
        if (data != null && event.getItemStack().is(Items.ECHO_SHARD)) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.uses", data.remaining(), data.max())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 残光の翼を、プレイヤー（両方の体型）と防具立てに描く。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addWingLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r) {
                r.addLayer(new AfterglowWingsLayer<>(r));
            }
        }
        if (event.getRenderer(net.minecraft.world.entity.EntityType.ARMOR_STAND)
                instanceof net.minecraft.client.renderer.entity.ArmorStandRenderer stand) {
            stand.addLayer(new AfterglowWingsLayer(stand));
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ANDROID_WORKBENCH.get(),
                WorkbenchRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ACCELERATOR_CONTROLLER.get(),
                AcceleratorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.CABLE.get(),
                CableRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.KERAUNOS_TOWER.get(),
                KeraunosTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEALED_CONTAINER.get(),
                SealedContainerRenderer::new);
        event.registerEntityRenderer(SinguloEntities.SECURITY_DRONE.get(), SecurityDroneRenderer::new);
        event.registerEntityRenderer(SinguloEntities.BLADE_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.BLADE));
        event.registerEntityRenderer(SinguloEntities.WHIP_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.WHIP));
        event.registerEntityRenderer(SinguloEntities.SABER_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.SABER));
        event.registerEntityRenderer(SinguloEntities.AXE_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.AXE));
        event.registerEntityRenderer(SinguloEntities.BIT_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.BIT));
        event.registerEntityRenderer(SinguloEntities.GUNNER_ANDROID.get(), ctx -> new AndroidRenderer(ctx, AndroidRenderer.Variant.GUNNER));
        event.registerEntityRenderer(SinguloEntities.ANDROID_CRESCENT.get(), AndroidCrescentRenderer::new);
        event.registerEntityRenderer(SinguloEntities.ANDROID_LASER.get(), AndroidLaserRenderer::new);
        event.registerEntityRenderer(SinguloEntities.HORIZON_WARDEN.get(), HorizonWardenRenderer::new);
        event.registerEntityRenderer(SinguloEntities.ECHO_SENTINEL.get(), EchoSentinelRenderer::new);
        event.registerEntityRenderer(SinguloEntities.GRAVITY_REMNANT.get(), GravityRemnantRenderer::new);
        event.registerEntityRenderer(SinguloEntities.GRAVITY_DEBRIS.get(), GravityDebrisRenderer::new);
        event.registerEntityRenderer(SinguloEntities.ERGOSPHERE_LEVIATHAN.get(), ErgosphereLeviathanRenderer::new);
        event.registerEntityRenderer(SinguloEntities.LAST_ARCHIVIST.get(), LastArchivistRenderer::new);
        event.registerEntityRenderer(SinguloEntities.FOUNDRY_OVERSEER.get(), FoundryOverseerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.FACTORY_LINE.get(),
                FactoryLineRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.ECHO_PROJECTOR.get(),
                EchoProjectorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.PENROSE_REACTOR.get(),
                PenroseReactorRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEAL_CONSOLE.get(),
                ctx -> new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<>() {
                    // 重力異常点: 封印コンソールの上の空間がゆがんで見える。ボスが現れると縮んで消え、倒されると戻る
                    @Override
                    public void render(io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity be, float pt,
                                       com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf,
                                       int light, int overlay) {
                        if (be.getLevel() == null) {
                            return;
                        }
                        float scale = be.anomalyScale(be.getLevel().getGameTime() + pt);
                        if (scale > 0.01F) {
                            net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).add(0, 3.5, 0);
                            GravitationalLensing.add(c, 1.2F * scale, 0.5F * Math.min(1, scale));
                        }
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
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.GENESIS_CORE.get(),
                GenesisCoreRenderer::new);
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
        event.registerLayerDefinition(AndroidModel.BLADE_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.BLADE));
        event.registerLayerDefinition(AndroidModel.WHIP_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.WHIP));
        event.registerLayerDefinition(AndroidModel.SABER_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.SABER));
        event.registerLayerDefinition(AndroidModel.AXE_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.AXE));
        event.registerLayerDefinition(AndroidModel.BIT_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.BIT));
        event.registerLayerDefinition(AndroidModel.GUNNER_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.GUNNER));
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
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.GENESIS_CORE.get(), GenesisCoreScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.ANDROID_STATION.get(), AndroidStationScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.ANDROID.get(), AndroidScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.ANDROID_WORK_CONSOLE.get(), WorkConsoleScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.ANDROID_RECIPE_RACK.get(), RecipeRackScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(SinguloMenus.ANDROID_DELIVERY_PORT.get(), DeliveryPortScreen::new);
    }

    /** 作業位置カードとレシピカードは、シフトを押している間、中身の絵を重ねて見せる。 */
    /** 流体の見本は、流体の色に染める。 */
    private static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            var f = io.github.genichimaruo.singulo.android.work.FluidSamples.fluid(stack);
            if (f.isEmpty()) {
                return 0xFFFFFFFF;
            }
            return 0xFF000000 | net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(f.getFluid()).getTintColor(f);
        }, io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_SAMPLE.get());
        // 化学物質の見本は、化学物質の色に
        event.register((stack, layer) -> {
            String id = io.github.genichimaruo.singulo.android.work.ChemicalSamples.id(stack);
            return id.isEmpty() ? 0xFFFFFFFF : 0xFF000000 | io.github.genichimaruo.singulo.android.work.Chemicals.tint(id);
        }, io.github.genichimaruo.singulo.registry.SinguloItems.CHEMICAL_SAMPLE.get());
        // アンドロイドの装飾: 染める絵（layer0）だけを、染めた色に
        for (var acc : io.github.genichimaruo.singulo.registry.SinguloItems.ACCESSORIES) {
            event.register((stack, layer) -> layer == 0 && stack.getItem() instanceof io.github.genichimaruo.singulo.android.AndroidAccessoryItem a
                    ? 0xFF000000 | a.color(stack) : 0xFFFFFFFF, acc.get());
        }
    }

    private static void registerDecorations(net.minecraftforge.client.event.RegisterItemDecorationsEvent event) {
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get(), WorkCardDecorator.LOCATION);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.RECIPE_CARD.get(), WorkCardDecorator.RECIPE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.PROCEDURE_CARD.get(), WorkCardDecorator.RECIPE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_SAMPLE.get(), FluidSampleDecorator.INSTANCE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.CHEMICAL_SAMPLE.get(), FluidSampleDecorator.INSTANCE);
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
        // 作業位置カード: 記録済みなら絵を切り替える
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get(), Singulo.id("recorded"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.pos(stack) != null ? 1 : 0));
        // 範囲カード: 1つ目の角だけなら 0.5、範囲ができていれば 1
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.AREA_CARD.get(), Singulo.id("recorded"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.android.work.AreaCardItem.box(stack) != null ? 1
                        : io.github.genichimaruo.singulo.android.work.AreaCardItem.first(stack) != null ? 0.5F : 0));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get(), new ResourceLocation("angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) ->
                        io.github.genichimaruo.singulo.item.ExplorerCompassItem.pointing(stack))));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.METEOR_COMPASS.get(), new ResourceLocation("minecraft", "angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) ->
                        io.github.genichimaruo.singulo.item.ExplorerCompassItem.pointing(stack))));
    }

    /**
     * 説明（折り返し）。長い説明には短い要約があり、ふだんは要約だけを出す。
     * Shift を押している間は、説明の全文と作り方・使い道を出す（詳しく見られるものには「Shift: 詳しく見る」を添える）。
     */
    private static void describe(ItemTooltipEvent event) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(event.getItemStack().getItem());
        if (!Singulo.MODID.equals(id.getNamespace())) {
            return;
        }
        if (io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity.remembersUniverse(event.getItemStack())) {
            event.getToolTip().add(1, Component.translatable("tooltip.singulo.genesis_core.memory")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        String key = "desc.singulo." + id.getPath();
        if (!net.minecraft.client.resources.language.I18n.exists(key)) {
            return;
        }
        String summary = "summary.singulo." + id.getPath();
        boolean hasSummary = net.minecraft.client.resources.language.I18n.exists(summary);
        boolean hasHowto = net.minecraft.client.resources.language.I18n.exists("howto.singulo." + id.getPath());
        boolean hasUses = net.minecraft.client.resources.language.I18n.exists("uses.singulo." + id.getPath());
        boolean detailed = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        java.util.List<Component> tip = event.getToolTip();
        addWrapped(tip, hasSummary && !detailed ? summary : key, ChatFormatting.GRAY);
        if (detailed) {
            for (String k : new String[]{"howto", "uses"}) {
                String full = k + ".singulo." + id.getPath();
                if (net.minecraft.client.resources.language.I18n.exists(full)) {
                    addWrapped(tip, full, ChatFormatting.DARK_AQUA);
                }
            }
        } else if (hasSummary || hasHowto || hasUses) {
            tip.add(Component.translatable("tooltip.singulo.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 翻訳キー key の文を、説明欄の幅で折り返して足す。 */
    private static void addWrapped(java.util.List<Component> tip, String key, ChatFormatting color) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (net.minecraft.network.chat.FormattedText line : font.getSplitter().splitLines(Component.translatable(key), 220,
                net.minecraft.network.chat.Style.EMPTY)) {
            tip.add(Component.literal(line.getString()).withStyle(color));
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
