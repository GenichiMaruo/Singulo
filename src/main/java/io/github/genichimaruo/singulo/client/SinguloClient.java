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
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    public SinguloClient(IEventBus modBus) {
        modBus.addListener(SinguloClient::registerScreens);
        modBus.addListener(SinguloClient::registerDecorations);
        modBus.addListener(SinguloClient::registerItemColors);
        modBus.addListener(SinguloClient::registerBlockColors);
        modBus.addListener(SinguloClient::registerExtensions);
        modBus.addListener(SinguloClient::registerRenderers);
        modBus.addListener(SinguloClient::registerLayers);
        modBus.addListener(SinguloClient::addWingLayers);
        modBus.addListener(GravitationalLensing::registerShaders);
        modBus.addListener(StaffTipTracker::wrapModel);
        modBus.addListener(SinguloClient::addPacks);
        modBus.addListener(SinguloClient::clientSetup);
        modBus.addListener(SinguloKeys::register);
        NeoForge.EVENT_BUS.addListener(SinguloClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(SinguloClient::sizeTooltip);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::onRenderStage);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::onRenderGui);
        NeoForge.EVENT_BUS.addListener(HologramRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SurveyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SkyGalaxyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientGalaxies.set(java.util.List.of(), false);
            ClientRecords.set(java.util.List.of());
            clearWorldState();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) clearWorldState();
        });
        NeoForge.EVENT_BUS.addListener(NeutrinoOverlay::onRenderStage);
        NeoForge.EVENT_BUS.addListener(RayleighLighting::tick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onScreenKey);
        NeoForge.EVENT_BUS.addListener(BlackHolePull::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlackHoleAmbience::onClientTick);
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
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
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
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.RAYLEIGH_PANEL.get(),
                RayleighPanelRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.KERAUNOS_TOWER.get(),
                KeraunosTowerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SEALED_CONTAINER.get(),
                SealedContainerRenderer::new);
        event.registerBlockEntityRenderer(io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SLIDING_DOOR.get(),
                SlidingDoorRenderer::new);
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

    private static void registerScreens(RegisterMenuScreensEvent event) {
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
    }

    /** 作業位置カードとレシピカードは、シフトを押している間、中身の絵を重ねて見せる。 */
    /** 流体の見本は、流体の色に染める。 */
    private static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            var f = io.github.genichimaruo.singulo.android.work.FluidSamples.fluid(stack);
            if (f.isEmpty()) {
                return 0xFFFFFFFF;
            }
            return 0xFF000000 | net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(f.getFluid()).getTintColor(f);
        }, io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_SAMPLE.get());
        // 液体キャニスター: 窓の絵（layer1）だけを中身の色に。空なら窓を描かない
        event.register((stack, layer) -> {
            var f = io.github.genichimaruo.singulo.item.FluidCanisterItem.fluid(stack);
            if (layer != 1) {
                return 0xFFFFFFFF;
            }
            return f.isEmpty() ? 0 : 0xFF000000 | net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(f.getFluid()).getTintColor(f);
        }, io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_CANISTER.get());
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

    /** 置いたティーカップ: 色付けの番号0（カップと受け皿）を、ブロックエンティティの色に。 */
    private static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (tint == 0 && level != null && pos != null
                    && level.getBlockEntity(pos) instanceof io.github.genichimaruo.singulo.android.TeacupBlockEntity cup) {
                return 0xFF000000 | cup.color();
            }
            return 0xFFFFFFFF;
        }, io.github.genichimaruo.singulo.registry.SinguloBlocks.TEACUP.get());
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
        event.addPackFinders(Singulo.id("resourcepacks/singulo_hd"), net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Component.translatable("pack.singulo.hd"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, false,
                net.minecraft.server.packs.repository.Pack.Position.TOP);
    }

    /** 探索コンパスの針の向き（バニラのコンパスと同じ「angle」）。 */
    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // 電力の数: Shift を押している間は正確な値
        io.github.genichimaruo.singulo.energy.EnergyText.detail = net.minecraft.client.gui.screens.Screen::hasShiftDown;
        // 作業位置カード: 記録済みなら絵を切り替える
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get(), Singulo.id("recorded"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.pos(stack) != null ? 1 : 0));
        // 範囲カード: 1つ目の角だけなら 0.5、範囲ができていれば 1
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.AREA_CARD.get(), Singulo.id("recorded"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.android.work.AreaCardItem.box(stack) != null ? 1
                        : io.github.genichimaruo.singulo.android.work.AreaCardItem.first(stack) != null ? 0.5F : 0));
        // 測量メガネ: 掛ける高さごとに、頭の模型を切り替える
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.SURVEY_GLASSES.get(), Singulo.id("height"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.android.work.SurveyGlassesItem.heightPredicate(stack)));
        // 移設レンチ: ケーブル撤去モードは表示灯が橙の絵
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.RELOCATION_WRENCH.get(), Singulo.id("mode"),
                (stack, level, entity, seed) -> io.github.genichimaruo.singulo.item.RelocationWrenchItem.mode(stack)));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) ->
                        io.github.genichimaruo.singulo.item.ExplorerCompassItem.pointing(stack))));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.METEOR_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
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
        // 傘を手に持つ間は、腕を上げて柄を握る構えにする（ほかの人から見て、手で持って見えるように）
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                                                                              net.minecraft.world.InteractionHand hand,
                                                                              net.minecraft.world.item.ItemStack stack) {
                return UmbrellaArmPose.UMBRELLA.getValue();
            }
        }, io.github.genichimaruo.singulo.registry.SinguloItems.ANDROID_UMBRELLA.get());
        for (SinguloFluids.Entry entry : SinguloFluids.ALL.values()) {
            int color = entry.def().color();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOWING;
                }

                @Override
                public int getTintColor() {
                    return color;
                }
            }, entry.type().get());
        }
    }
}
