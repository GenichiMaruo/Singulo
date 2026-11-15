package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = Singulo.MODID, dist = Dist.CLIENT)
public final class SinguloClient {
    private static final Identifier STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier FLOWING = Identifier.withDefaultNamespace("block/water_flow");

    public SinguloClient(IEventBus modBus) {
        modBus.addListener(SinguloClient::registerScreens);
        modBus.addListener(SinguloClient::registerDecorations);
        modBus.addListener(SinguloClient::registerItemColors);
        modBus.addListener(SinguloClient::registerBlockColors);
        modBus.addListener(SinguloClient::registerExtensions);
        modBus.addListener(SinguloClient::registerFluidModels);
        modBus.addListener(SinguloClient::registerRenderers);
        modBus.addListener(SinguloClient::registerLayers);
        modBus.addListener(SinguloClient::addWingLayers);
        modBus.addListener((net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) -> AfterglowWingsLayer.registerModifiers(event));
        modBus.addListener(RayleighLightmapBanks::registerShaders);
        modBus.addListener(GravitationalLensing::registerShaders);
        modBus.addListener(JetForgeRenderTypes::registerPipelines);
        modBus.addListener(EffectRenderTypes::register);
        modBus.addListener(GlowDepth::registerShaders);
        modBus.addListener(JetForgeRenderer::registerModels);
        modBus.addListener(FluidCapsuleRenderer::registerModels);
        modBus.addListener(CoverModel::wrap);
        modBus.addListener(CoverModel::registerColors);
        modBus.addListener(SinguloClient::addPacks);
        modBus.addListener(SinguloClient::clientSetup);
        modBus.addListener(SinguloClient::registerNumericProperties);
        modBus.addListener(SinguloKeys::register);
        NeoForge.EVENT_BUS.addListener(SinguloClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(ClientRecipes::receive);
        NeoForge.EVENT_BUS.addListener(SinguloClient::sizeTooltip);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::onRenderStage);
        NeoForge.EVENT_BUS.addListener(GlowDepth::onRenderStage);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::onRenderGui);
        NeoForge.EVENT_BUS.addListener(WorldGeometry::extract);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::extract);
        NeoForge.EVENT_BUS.addListener(WorldGeometry::submit);
        NeoForge.EVENT_BUS.addListener(PipeSurveyRenderer::hud);
        NeoForge.EVENT_BUS.addListener(PipeSurveyRenderer::clear);
        NeoForge.EVENT_BUS.addListener(SkyGalaxyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SkyGalaxyRenderer::extract);
        NeoForge.EVENT_BUS.addListener(ArsenalNotice::onLogin);
        NeoForge.EVENT_BUS.addListener(ArsenalNotice::onLogout);
        NeoForge.EVENT_BUS.addListener(ArsenalNotice::onClientTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientRecipes.clear();
            ClientGalaxies.set(java.util.List.of(), false);
            ClientRecords.set(java.util.List.of());
            clearWorldState();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) clearWorldState();
        });
        NeoForge.EVENT_BUS.addListener(RayleighLighting::tick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onScreenKey);
        NeoForge.EVENT_BUS.addListener(UiClick::onRender);
        NeoForge.EVENT_BUS.addListener(UiClick::onMouse);
        NeoForge.EVENT_BUS.addListener(BlackHolePull::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlackHoleAmbience::onClientTick);
        NeoForge.EVENT_BUS.addListener(JetForgeEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(JetForgeEffects::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(JetForgeEffects::onRenderGui);
        NeoForge.EVENT_BUS.addListener(MachineSounds::onClientTick);
        NeoForge.EVENT_BUS.addListener(BossSounds::onClientTick);
        NeoForge.EVENT_BUS.addListener(GravityBootsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onInteraction);
    }

    static void clearWorldState() {
        ShapeCache.clear();
        HologramRenderer.clear();
        NeutrinoOverlay.clear();
        BlackHoleAmbience.reset();
        MachineSounds.reset();
        BossSounds.reset();
    }

    /** 型として使っている残響の欠片（バニラのアイテム）に使用回数を出す。Singulo のアイテムには説明と作り方・使い道を出す。 */
    private static void onTooltip(ItemTooltipEvent event) {
        describe(event);
        if (Boolean.TRUE.equals(event.getItemStack().get(SinguloComponents.STABILIZED.get()))) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.catalyst_stabilized").withStyle(ChatFormatting.GOLD));
        }
        Integer dark = event.getItemStack().get(SinguloComponents.DARK_MATTER.get());
        if (dark != null) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.dark_matter", dark).withStyle(ChatFormatting.GRAY));
        }
        // Mekanism の化学物質のためのアップグレードは、Mekanism がなければ使えないことを知らせる
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (Singulo.MODID.equals(id.getNamespace()) && (id.getPath().equals("android_gas_tank_upgrade") || id.getPath().equals("android_dual_tank_upgrade"))
                && !io.github.genichimaruo.singulo.android.work.Chemicals.present()) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.needs_mekanism").withStyle(ChatFormatting.RED));
        }
        UsesData data = event.getItemStack().get(SinguloComponents.USES.get());
        if (data != null && event.getItemStack().is(Items.ECHO_SHARD)) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.uses", data.remaining(), data.max())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 残光の翼を、プレイヤー（両方の体型）と防具立てに描く。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addWingLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getPlayerRenderer(skin) instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> r) {
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
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.FLUID_PIPE.get(),
                FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.RAYLEIGH_PANEL.get(),
                RayleighPanelRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.KERAUNOS_TOWER.get(),
                KeraunosTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEALED_CONTAINER.get(),
                SealedContainerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SLIDING_DOOR.get(),
                SlidingDoorRenderer::new);
        event.registerEntityRenderer(SinguloEntities.SECURITY_DRONE.get(), SecurityDroneRenderer::new);
        event.registerEntityRenderer(SinguloEntities.CARGO_DRONE.get(), CargoDroneRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.DRONE_PORT.get(),
                DronePortRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.FLUID_DRONE_PORT.get(),
                DronePortRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.FOOD_PRINTER.get(),
                FoodPrinterRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.HORIZON_VAULT.get(),
                HorizonVaultRenderer::new);
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
        event.registerEntityRenderer(SinguloEntities.INFALLING_BLOCK.get(), InfallingBlockRenderer::new);
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
                ctx -> new SnapshotBlockRenderer<io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity>() {
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
                            GravitationalLensing.add(c, 0.7F * scale, 0.5F * Math.min(1, scale));
                        }
                    }

                    @Override
                    public boolean shouldRenderOffScreen() {
                        return true;
                    }

                    @Override
                    public net.minecraft.world.phys.AABB getRenderBoundingBox(io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity be) {
                        // 異常点（コンソールの3.5ブロック上）の歪みが画面にかかる間は描く
                        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 4, 0).inflate(6);
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
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.JET_FORGE.get(), JetForgeRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.COSMIC_CHAMBER.get(), CosmicChamberRenderer::new);
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
        event.registerLayerDefinition(CargoDroneRenderer.LAYER, CargoDroneRenderer::createLayer);
        event.registerLayerDefinition(CargoDroneRenderer.JET_LAYER, CargoDroneRenderer::createJetLayer);
        event.registerLayerDefinition(CargoDroneRenderer.GRAVITY_LAYER, CargoDroneRenderer::createGravityLayer);
        event.registerLayerDefinition(AndroidModel.BLADE_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.BLADE));
        event.registerLayerDefinition(AndroidModel.WHIP_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.WHIP));
        event.registerLayerDefinition(AndroidModel.SABER_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.SABER));
        event.registerLayerDefinition(AndroidModel.AXE_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.AXE));
        event.registerLayerDefinition(AndroidModel.BIT_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.BIT));
        event.registerLayerDefinition(AndroidModel.GUNNER_LAYER, () -> AndroidModel.createLayer(AndroidRenderer.Variant.GUNNER));
        event.registerLayerDefinition(HorizonWardenRenderer.LAYER, HorizonWardenRenderer::createLayer);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SinguloMenus.COSMIC.get(), CosmicScreen::new);
        event.register(SinguloMenus.MACHINE.get(), MachineScreen::new);
        event.register(SinguloMenus.THERMOELECTRIC_GENERATOR.get(), ThermoelectricGeneratorScreen::new);
        event.register(SinguloMenus.GENERATOR_SLOTS.get(), GeneratorSlotsScreen::new);
        event.register(SinguloMenus.CATALYST_DEVICE.get(), CatalystDeviceScreen::new);
        event.register(SinguloMenus.PENROSE_REACTOR.get(), PenroseReactorScreen::new);
        event.register(SinguloMenus.SMES.get(), SmesScreen::new);
        event.register(SinguloMenus.WORMHOLE_STABILIZER.get(), WormholeStabilizerScreen::new);
        event.register(SinguloMenus.DEVICE.get(), DeviceScreen::new);
        event.register(SinguloMenus.GENESIS_CORE.get(), GenesisCoreScreen::new);
        event.register(SinguloMenus.ANDROID_STATION.get(), AndroidStationScreen::new);
        event.register(SinguloMenus.ANDROID.get(), AndroidScreen::new);
        event.register(SinguloMenus.ANDROID_WORK_CONSOLE.get(), WorkConsoleScreen::new);
        event.register(SinguloMenus.ANDROID_RECIPE_RACK.get(), RecipeRackScreen::new);
        event.register(SinguloMenus.ANDROID_DELIVERY_PORT.get(), DeliveryPortScreen::new);
        event.register(SinguloMenus.RGB_DYE_STATION.get(), RgbDyeStationScreen::new);
        event.register(SinguloMenus.FOOD_PRINTER.get(), FoodPrinterScreen::new);
        event.register(SinguloMenus.ANDROID_SUPPLY_PORT.get(), SupplyPortScreen::new);
        event.register(SinguloMenus.DRONE_STATION.get(), DroneStationScreen::new);
        event.register(SinguloMenus.DRONE_PORT.get(), DronePortScreen::new);
    }

    /** 作業位置カードとレシピカードは、シフトを押している間、中身の絵を重ねて見せる。 */
    /** 流体の見本は、流体の色に染める。 */
    private static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Singulo.id("dynamic"), SinguloItemTint.CODEC);
    }
    private static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(java.util.List.of(new net.minecraft.client.color.block.BlockTintSource() {
            @Override public int color(net.minecraft.world.level.block.state.BlockState state) { return -1; }
            @Override public int colorInWorld(net.minecraft.world.level.block.state.BlockState state,
                    net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                Integer color=level.getModelData(pos).get(io.github.genichimaruo.singulo.android.TeacupBlockEntity.MODEL_COLOR);
                return color==null?-1:0xFF000000|color;
            }
        }), io.github.genichimaruo.singulo.registry.SinguloBlocks.TEACUP.get());
    }
    private static void registerDecorations(net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent event) {
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get(), WorkCardDecorator.LOCATION);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.RECIPE_CARD.get(), WorkCardDecorator.RECIPE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.PROCEDURE_CARD.get(), WorkCardDecorator.RECIPE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_SAMPLE.get(), FluidSampleDecorator.INSTANCE);
        event.register(io.github.genichimaruo.singulo.registry.SinguloItems.CHEMICAL_SAMPLE.get(), FluidSampleDecorator.INSTANCE);
    }

    /** 組み込みのリソースパック「Singulo HD」（32×32 のテクスチャ）。リソースパックの画面で選ぶと使える。 */
    private static void addPacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        var modFile = net.neoforged.fml.ModList.get().getModFileById(Singulo.MODID).getFile();
        if (!modFile.getContents().containsFile("resourcepacks/singulo_hd/pack.mcmeta")) {
            return;
        }
        event.addPackFinders(Singulo.id("resourcepacks/singulo_hd"), net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Component.translatable("pack.singulo.hd"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, false,
                net.minecraft.server.packs.repository.Pack.Position.TOP);
    }

    /** 探索コンパスの針の向き（バニラのコンパスと同じ「angle」）。 */
    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        io.github.genichimaruo.singulo.energy.EnergyText.detail = () -> net.minecraft.client.Minecraft.getInstance().hasShiftDown();
    }
    private static void registerNumericProperties(net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Singulo.id("dynamic"), SinguloItemProperty.CODEC);
    }
    /**
     * 説明（折り返し）。長い説明には短い要約があり、ふだんは要約だけを出す。
     * Shift を押している間は、説明の全文と作り方・使い道を出す（詳しく見られるものには「Shift: 詳しく見る」を添える）。
     */
    private static void describe(ItemTooltipEvent event) {
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM
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
        boolean detailed = net.minecraft.client.Minecraft.getInstance().hasShiftDown();
        java.util.List<Component> tip = event.getToolTip();
        tip.add(Component.translatable(hasSummary && !detailed ? summary : key).withStyle(ChatFormatting.GRAY));
        if (detailed) {
            for (String k : new String[]{"howto", "uses"}) {
                String full = k + ".singulo." + id.getPath();
                if (net.minecraft.client.resources.language.I18n.exists(full)) {
                    tip.add(Component.translatable(full).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
        } else if (hasSummary || hasHowto || hasUses) {
            tip.add(Component.translatable("tooltip.singulo.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 画面端で幅が狭くなる場合も、全文から一度だけ折り返す。 */
    private static void sizeTooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (Singulo.MODID.equals(id.getNamespace())) {
            int width = event.getMaxWidth();
            event.setMaxWidth(width > 0 ? Math.min(width, 220) : 220);
        }
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        // 液体カプセル: 立体の殻の中に、詰めた液体を描く
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                                                                              net.minecraft.world.InteractionHand hand,
                                                                              net.minecraft.world.item.ItemStack stack) {
                return UmbrellaArmPose.UMBRELLA.getValue();
            }
        }, io.github.genichimaruo.singulo.registry.SinguloItems.ANDROID_UMBRELLA.get());
    }
    private static void registerFluidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        for(SinguloFluids.Entry entry:SinguloFluids.ALL.values()) {
            net.neoforged.neoforge.client.fluid.FluidTintSource tint=state->entry.def().color();
            event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                    new net.minecraft.client.resources.model.sprite.Material(STILL),
                    new net.minecraft.client.resources.model.sprite.Material(FLOWING),null,tint),entry.source(),entry.flowing());
        }
    }
}