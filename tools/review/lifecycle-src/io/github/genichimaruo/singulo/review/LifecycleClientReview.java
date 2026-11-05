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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

@EventBusSubscriber(modid=Singulo.MODID,value=Dist.CLIENT)
public final class LifecycleClientReview {
    private static int ticks,phase,wait,before=-1,visits,menuTicks;
    private static boolean sawA,sawALeave;
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END)return;
        String role=System.getProperty("singulo.lifecycleRole");if(role==null)return;
        var mc=Minecraft.getInstance();var output=Path.of(System.getProperty("singulo.lifecycleOutput"));
        if(phase==4&&Files.exists(output.resolve("results.json"))){mc.stop();return;}
        if(++ticks>4000) throw new AssertionError("Multiplayer client timeout role="+role+" phase="+phase);
        if(phase==2 && mc.screen instanceof TitleScreen) {
            if(++wait<40)return;
            String address=System.getProperty("singulo.lifecycleAddress","127.0.0.1:25595");
            ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Review",address,false),false);
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
                mc.level.disconnect();mc.clearLevel(new TitleScreen());phase=2;wait=0;
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
