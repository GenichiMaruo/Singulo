package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.nio.file.*;
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
    private static final net.minecraft.client.GraphicsPreset[] MODES = {
        net.minecraft.client.GraphicsPreset.FANCY, net.minecraft.client.GraphicsPreset.FABULOUS,
        net.minecraft.client.GraphicsPreset.FANCY
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
                mc.options.graphicsPreset().set(MODES[stage/3]);
                mc.options.improvedTransparency().set(MODES[stage/3]==net.minecraft.client.GraphicsPreset.FABULOUS);
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
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level), "time set 6000");level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level), "weather clear 6000");
        if(stage%3==0){
            Blueprints.layout(Blueprints.Kind.TIDAL_SPINDLE,C,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
            var be=(CosmicChamberBlockEntity)level.getBlockEntity(C);var tag=be.saveWithoutMetadata(level.registryAccess());
            tag.putBoolean("core",true);tag.putLong("stored",10000000000L);be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),tag));be.ready();
            level.sendBlockUpdated(C,be.getBlockState(),be.getBlockState(),3);
        }
        for(int z=-4;z<=11;z++)for(int y=98;y<=112;y++){
            boolean solid=stage%3==0 || stage%3==1&&z<=3;
            level.setBlockAndUpdate(new BlockPos(4,y,z),solid?((z+y)%2==0?Blocks.GOLD_BLOCK:Blocks.DIAMOND_BLOCK).defaultBlockState():Blocks.AIR.defaultBlockState());
        }
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,10.5,103,3.5,java.util.Set.of(),90,0,false);
        prepared=true;
    }
    @SubscribeEvent(priority=EventPriority.NORMAL) public static void before(RenderLevelStageEvent.AfterOpaqueFeatures e)throws Exception{
        if(!prepared||wait<100||captured)return;
        if(!GravitationalLensing.hasSources())throw new AssertionError("Spindle lens source not registered");
        if (((Minecraft.getInstance().levelRenderer.getTranslucentTarget()!=null) != (MODES[stage/3]==net.minecraft.client.GraphicsPreset.FABULOUS))) {
            throw new AssertionError("Requested graphics pipeline not active");
        }
        before=ReviewScreenshot.take(Minecraft.getInstance().getMainRenderTarget());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(RenderLevelStageEvent.AfterOpaqueFeatures e)throws Exception{
        if(before==null)return;
        var mc=Minecraft.getInstance();var out=Path.of(System.getProperty("singulo.occlusionOutput"));Files.createDirectories(out);
        try(var after=ReviewScreenshot.take(mc.getMainRenderTarget())){
            before.writeToFile(out.resolve("occlusion-"+stage+"-before.png"));after.writeToFile(out.resolve("occlusion-"+stage+"-after.png"));
            int changed=0,max=0,left=0,right=0;
            for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++){
                int a=before.getPixel(x,y),b=after.getPixel(x,y);int d=Math.max(Math.abs((a&255)-(b&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a>>16&255)-(b>>16&255))));
                if(d>2){changed++;if(x<620)left++;if(x>660)right++;}max=Math.max(max,d);
            }
            Singulo.LOGGER.info("SINGULO_OCCLUSION_RESULT mode={} stage={} changed={} left={} right={} max={}",mc.options.graphicsPreset().get(),stage,changed,left,right,max);
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
