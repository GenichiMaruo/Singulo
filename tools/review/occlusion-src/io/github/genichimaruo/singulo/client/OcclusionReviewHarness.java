package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.nio.file.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Compare the same rendered frame before/after lensing with a real spindle and opaque occluders. */
@EventBusSubscriber(modid=Singulo.MODID,value=Dist.CLIENT)
public final class OcclusionReviewHarness {
    private static final BlockPos C=new BlockPos(0,100,0);
    private static final net.minecraft.client.GraphicsStatus[] MODES = {
        net.minecraft.client.GraphicsStatus.FANCY, net.minecraft.client.GraphicsStatus.FABULOUS,
        net.minecraft.client.GraphicsStatus.FANCY
    };
    private static int ticks,stage,wait;
    private static volatile boolean prepared;
    private static boolean requested,captured;
    private static NativeImage before;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance();
        if (++ticks>4000) throw new AssertionError("Occlusion timed out "+stage);
        if (mc.level==null||mc.player==null||mc.getOverlay()!=null) return;
        if (!requested) {
            requested=true;prepared=false;
            if (stage%3==0) {
                mc.options.graphicsMode().set(MODES[stage/3]);
                mc.levelRenderer.allChanged();
            }
            mc.getSingleplayerServer().execute(()->prepare(mc));return;
        }
        if (!prepared)return;
        mc.setScreen(null);mc.options.hideGui=true;
        mc.player.setYRot(90);mc.player.setXRot(0);
        mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        wait++;
    }
    private static void prepare(Minecraft mc) {
        var level=mc.getSingleplayerServer().overworld();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        if(stage%3==0){
            Blueprints.layout(Blueprints.Kind.TIDAL_SPINDLE,C,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
            var be=(CosmicChamberBlockEntity)level.getBlockEntity(C);var tag=be.saveWithoutMetadata(level.registryAccess());
            tag.putBoolean("core",true);tag.putLong("stored",10000000000L);be.loadWithComponents(tag,level.registryAccess());be.ready();
            level.sendBlockUpdated(C,be.getBlockState(),be.getBlockState(),3);
        }
        for(int z=-4;z<=11;z++)for(int y=98;y<=112;y++){
            boolean solid=stage%3==0 || stage%3==1&&z<=3;
            level.setBlockAndUpdate(new BlockPos(4,y,z),solid?((z+y)%2==0?Blocks.GOLD_BLOCK:Blocks.DIAMOND_BLOCK).defaultBlockState():Blocks.AIR.defaultBlockState());
        }
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,10.5,103,3.5,90,0);
        prepared=true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(RenderLevelStageEvent e)throws Exception{
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||!prepared||wait<100||captured)return;
        var f=GravitationalLensing.class.getDeclaredField("SOURCES");f.setAccessible(true);int n=((List<?>)f.get(null)).size();
        if(n<1)throw new AssertionError("Spindle lens source not registered");
        if ((Minecraft.getInstance().levelRenderer.getTranslucentTarget()!=null) != (MODES[stage/3]==net.minecraft.client.GraphicsStatus.FABULOUS)) {
            throw new AssertionError("Requested graphics pipeline not active");
        }
        before=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(RenderLevelStageEvent e)throws Exception{
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||before==null)return;
        var mc=Minecraft.getInstance();var out=Path.of(System.getProperty("singulo.occlusionOutput"));Files.createDirectories(out);
        try(var after=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            before.writeToFile(out.resolve("occlusion-"+stage+"-before.png"));after.writeToFile(out.resolve("occlusion-"+stage+"-after.png"));
            int changed=0,max=0,left=0,right=0;
            for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++){
                int a=before.getPixelRGBA(x,y),b=after.getPixelRGBA(x,y);int d=Math.max(Math.abs((a&255)-(b&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a>>16&255)-(b>>16&255))));
                if(d>2){changed++;if(x<620)left++;if(x>660)right++;}max=Math.max(max,d);
            }
            Singulo.LOGGER.info("SINGULO_OCCLUSION_RESULT mode={} stage={} changed={} left={} right={} max={}",mc.options.graphicsMode().get(),stage,changed,left,right,max);
            if(stage%3==0&&changed>0)throw new AssertionError("Opaque wall distorted: "+changed);
            if(stage%3==1&&right>0)throw new AssertionError("Partial foreground wall distorted: "+right);
            if(stage%3!=0&&changed<100)throw new AssertionError("Visible positive control not lensed");
        }finally{before.close();before=null;captured=true;}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!captured)return;
        if(++stage==MODES.length*3){Singulo.LOGGER.info("SINGULO_OCCLUSION_COMPLETE 9");Minecraft.getInstance().stop();return;}
        wait=0;requested=false;captured=false;prepared=false;
    }
}
