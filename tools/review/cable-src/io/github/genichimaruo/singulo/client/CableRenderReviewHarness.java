package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

@EventBusSubscriber(modid=Singulo.MODID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public class CableRenderReviewHarness {
    static final BlockPos A=new BlockPos(1,80,0), B=new BlockPos(5,80,0), CUT=new BlockPos(3,80,0);
    static int phase,ticks,wait;
    static volatile boolean prepared,serverCut;
    @SubscribeEvent public static void tick(ClientTickEvent e) throws Exception {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(++ticks>2400) throw new IllegalStateException("Cable render timeout phase="+phase);
        if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                && mc.screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title
                && title.getKey().equals("selectWorld.backupQuestion.experimental")) {
            for(var child:mc.screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                    && label.getKey().equals("selectWorld.backupJoinSkipButton") && button.active) {button.onPress();return;}
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null) return;
        if(phase==0) {
            phase=1;
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                level.setDayTime(6000);
                for(int x=-2;x<=8;x++) for(int z=-6;z<=2;z++) level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.WHITE_CONCRETE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0,80,0),SinguloBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState());
                for(int x=1;x<=5;x++) level.setBlockAndUpdate(new BlockPos(x,80,0),SinguloBlocks.SUPERCONDUCTING_CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(6,80,0),SinguloBlocks.SMES_MODULE.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,Direction.SOUTH));
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getInventory().clearContent();
                player.teleportTo(level,3.3,80,-4.0,0,14);
                prepared=true;
            });
            mc.setScreen(null); mc.options.hideGui=true;
        } else if(phase==1 && prepared && ++wait>100 && mc.level.getBlockEntity(A) instanceof CableBlockEntity a
                && mc.level.getBlockEntity(B) instanceof CableBlockEntity b && a.flow()>0 && b.flow()>0) {
            Singulo.LOGGER.info("SINGULO_CABLE_CAMERA player={} camera={} yaw={} pitch={} screen={}",mc.player.position(),mc.gameRenderer.getMainCamera().getPosition(),mc.player.getYRot(),mc.player.getXRot(),mc.screen);
            capture(mc,"cable-powered");
            var original=mc.level.getBlockState(CUT);
            var oldSample=a.getUpdateTag(mc.level.registryAccess());
            mc.level.setBlock(CUT,Blocks.AIR.defaultBlockState(),3);
            a.handleUpdateTag(oldSample,mc.level.registryAccess());
            if(a.flow()!=0||b.flow()!=0) throw new IllegalStateException("Predicted cut did not hide both sides");
            mc.level.setBlock(CUT,original,3);
            if(a.flow()<=0||b.flow()<=0) throw new IllegalStateException("Rejected cut lost authoritative glow");
            Singulo.LOGGER.info("SINGULO_CABLE_ROLLBACK_RESTORES_GLOW");
            mc.gameMode.startDestroyBlock(CUT,Direction.UP);
            if(a.flow()!=0||b.flow()!=0) throw new IllegalStateException("Client retained glow after local break");
            Singulo.LOGGER.info("SINGULO_CABLE_LOCAL_CLEAR_IMMEDIATE");
            phase=2;wait=0;
        } else if(phase==2 && ++wait>5) {
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                serverCut=level.getBlockState(CUT).isAir()
                    && ((CableBlockEntity)level.getBlockEntity(A)).flow()==0
                    && ((CableBlockEntity)level.getBlockEntity(B)).flow()==0;
            });
            if(serverCut) {
                if(((CableBlockEntity)mc.level.getBlockEntity(A)).flow()!=0||((CableBlockEntity)mc.level.getBlockEntity(B)).flow()!=0)
                    throw new IllegalStateException("Client relit disconnected network");
                capture(mc,"cable-cut");
                Path output=Path.of(System.getProperty("singulo.guiReviewOutput"));
                Files.writeString(output.resolve("cable-render-result.txt"),"local client clear: immediate\npredicted break rollback: restored\nserver cut: true\nclient after packet: both sides unlit\n");
                Singulo.LOGGER.info("SINGULO_CABLE_RENDER_COMPLETE");
                phase=3;mc.stop();
            }
        }
    }
    static void capture(Minecraft mc,String name) throws Exception {
        Path output=Path.of(System.getProperty("singulo.guiReviewOutput"));Files.createDirectories(output);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {pixels.writeToFile(output.resolve(name+".png"));}
    }
}
