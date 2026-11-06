package io.github.genichimaruo.singulo.review;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid=Singulo.MODID,value=Dist.CLIENT)
public final class LifecycleClientReview {
    private static int ticks,phase,wait,before=-1,visits,menuTicks;
    private static boolean sawA,sawALeave;
    private static int cosmicBefore=-1;
    private static boolean portRequested;
    private static long renderedFrames,cosmicShown=-1;
    @SubscribeEvent
    public static void rendered(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){renderedFrames++;}
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        String role=System.getProperty("singulo.lifecycleRole");if(role==null)return;
        var mc=Minecraft.getInstance();var output=Path.of(System.getProperty("singulo.lifecycleOutput"));
        if(Files.exists(output.resolve("results.json"))){mc.stop();return;}
        if(++ticks>8000) throw new AssertionError("Multiplayer client timeout role="+role+" phase="+phase);
        // Observe the connection list before waiting on the menu fixture. Otherwise A
        // can leave while B's first menu is opening and B misses its initial presence.
        if(role.equals("B")&&mc.getConnection()!=null){
            boolean present=mc.getConnection().getOnlinePlayers().stream().anyMatch(p->p.getProfile().getName().equals("ReviewA"));
            if(present)sawA=true;if(sawA&&!present)sawALeave=true;
        }
        if(Files.exists(output.resolve("cosmic-stage.txt"))){
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            var pos=LifecycleServerReview.COSMIC_C;
            if(!(mc.level.getBlockEntity(pos) instanceof io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity))return;
            if(!(mc.player.containerMenu instanceof io.github.genichimaruo.singulo.cosmic.CosmicMenu menu)){
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));return;
            }
            if(cosmicBefore<0){cosmicBefore=menu.value(8);return;}
            if(menu.value(8)<cosmicBefore+8)return;
            if(menu.value(0)!=2||menu.value(10)!=1||menu.slots.size()!=40)throw new AssertionError("Spindle menu layout/data mismatch");
            if(role.equals("A")&&!portRequested){
                portRequested=true;mc.gameMode.handleInventoryButtonClick(menu.containerId,0);
            }
            if(menu.value(12)!=io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity.PORT_OUTPUT
                    ||menu.value(13)!=io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity.PORT_OUTPUT)return;
            if(cosmicShown<0){cosmicShown=renderedFrames;return;}
            if(renderedFrames-cosmicShown<2)return;
            if(!Files.exists(output.resolve("cosmic-"+role+"-complete.txt"))){
                Files.writeString(output.resolve("cosmic-"+role+"-complete.txt"),"real network menu: 40 slots, core, progress and both port modes synchronized after A's menu button packet");
                try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(output.resolve("cosmic-client-"+role+".png"));}
            }
            return;
        }
        if(phase==2 && mc.screen instanceof TitleScreen) {
            if(++wait<40)return;
            String address=System.getProperty("singulo.lifecycleAddress","127.0.0.1:25595");
            ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Review",address,ServerData.Type.OTHER),false,null);
            phase=3;before=-1;menuTicks=0;return;
        }
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        var raw=mc.level.getBlockEntity(LifecycleServerReview.C);if(!(raw instanceof JetForgeBlockEntity be)||!be.formed()||!be.hasCore())return;
        if(be.phase()!=JetForgeBlockEntity.IRRADIATE)return;
        if(before<0){before=be.phaseTicks();visits++;}
        if(be.phaseTicks()<before+8)return;
        if(!(mc.player.containerMenu instanceof DeviceMenu)) {
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(LifecycleServerReview.C),Direction.UP,LifecycleServerReview.C,false));return;
        }
        if(++menuTicks<10)return;
        Files.createDirectories(output);
        if(role.equals("A")) {
            if(phase==0&&Files.exists(output.resolve("both-menus.txt"))) {
                try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(output.resolve("client-A-before-reconnect.png"));}
                mc.level.disconnect();mc.disconnect(new TitleScreen());phase=2;wait=0;
                Singulo.LOGGER.info("SINGULO_MULTIPLAYER_CLIENT_DISCONNECT A");return;
            }
            if(phase==3) {
                try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(output.resolve("client-A-after-reconnect.png"));}
                Files.writeString(output.resolve("client-A-complete.txt"),"real TCP reconnect; synchronized phase advanced; forge menu reopened; visits="+visits);
                Singulo.LOGGER.info("SINGULO_MULTIPLAYER_CLIENT_COMPLETE A visits={}",visits);phase=4;
            }
        } else {
            boolean present=mc.getConnection().getOnlinePlayers().stream().anyMatch(p->p.getProfile().getName().equals("ReviewA"));
            if(present)sawA=true;
            if(sawA&&!present)sawALeave=true;
            if(sawALeave&&present) {
                Files.writeString(output.resolve("client-B-complete.txt"),"second real TCP client remained connected; observed A leave and return; progress and menu synchronized");
                try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(output.resolve("client-B-continuous.png"));}
                Singulo.LOGGER.info("SINGULO_MULTIPLAYER_CLIENT_COMPLETE B leave=true return=true");phase=4;
            }
        }
        if(phase==4&&Files.exists(output.resolve("results.json")))mc.stop();
    }
}
