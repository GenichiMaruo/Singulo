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
        modBus.addListener(SinguloClient::registerExtensions);
        modBus.addListener(SinguloClient::registerRenderers);
        modBus.addListener(SinguloClient::registerLayers);
        modBus.addListener(GravitationalLensing::registerShaders);
        modBus.addListener(StaffTipTracker::wrapModel);
        modBus.addListener(SinguloClient::addPacks);
        modBus.addListener(SinguloClient::clientSetup);
        modBus.addListener(SinguloKeys::register);
        NeoForge.EVENT_BUS.addListener(SinguloClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(GravitationalLensing::onRenderStage);
        NeoForge.EVENT_BUS.addListener(HologramRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(NeutrinoOverlay::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlackHolePull::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlackHoleAmbience::onClientTick);
        NeoForge.EVENT_BUS.addListener(MachineSounds::onClientTick);
        NeoForge.EVENT_BUS.addListener(SinguloKeys::onInteraction);
    }

    /** 型として使っている残響の欠片（バニラのアイテム）に使用回数を出す。Singulo のアイテムには説明と作り方・使い道を出す。 */
    private static void onTooltip(ItemTooltipEvent event) {
        describe(event);
        Integer dark = event.getItemStack().get(SinguloComponents.DARK_MATTER.get());
        if (dark != null) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.dark_matter", dark).withStyle(ChatFormatting.GRAY));
        }
        UsesData data = event.getItemStack().get(SinguloComponents.USES.get());
        if (data != null && event.getItemStack().is(Items.ECHO_SHARD)) {
            event.getToolTip().add(Component.translatable("tooltip.singulo.uses", data.remaining(), data.max())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SinguloEntities.SECURITY_DRONE.get(), SecurityDroneRenderer::new);
        event.registerEntityRenderer(SinguloEntities.HORIZON_WARDEN.get(), HorizonWardenRenderer::new);
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
        event.registerEntityRenderer(SinguloEntities.HORIZON_BOLT.get(), HorizonBoltRenderer::new);
        event.registerEntityRenderer(SinguloEntities.WARDEN_SINGULARITY.get(), WardenSingularityRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SecurityDroneRenderer.LAYER, SecurityDroneRenderer::createLayer);
        event.registerLayerDefinition(HorizonWardenRenderer.LAYER, HorizonWardenRenderer::createLayer);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SinguloMenus.MACHINE.get(), MachineScreen::new);
        event.register(SinguloMenus.THERMOELECTRIC_GENERATOR.get(), ThermoelectricGeneratorScreen::new);
        event.register(SinguloMenus.CATALYST_DEVICE.get(), CatalystDeviceScreen::new);
        event.register(SinguloMenus.PENROSE_REACTOR.get(), PenroseReactorScreen::new);
        event.register(SinguloMenus.SMES.get(), SmesScreen::new);
        event.register(SinguloMenus.WORMHOLE_STABILIZER.get(), WormholeStabilizerScreen::new);
        event.register(SinguloMenus.DEVICE.get(), DeviceScreen::new);
    }

    /** 組み込みのリソースパック「Singulo HD」（32×32 のテクスチャ）。リソースパックの画面で選ぶと使える。 */
    private static void addPacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        event.addPackFinders(Singulo.id("resourcepacks/singulo_hd"), net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Component.translatable("pack.singulo.hd"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, false,
                net.minecraft.server.packs.repository.Pack.Position.TOP);
    }

    /** 探索コンパスの針の向き（バニラのコンパスと同じ「angle」）。 */
    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
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

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
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
