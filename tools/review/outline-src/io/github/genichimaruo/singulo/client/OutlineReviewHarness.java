package io.github.genichimaruo.singulo.client;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;
/** Dedicated real-GPU regression for the procedural entity outline pass. Never distributed. */
@EventBusSubscriber(modid=Singulo.MODID,value=Dist.CLIENT)
public final class OutlineReviewHarness {
 private static final List<Mob> mobs=new ArrayList<>();
 private static boolean requested,done;
 private static volatile int phase=-1;
 private static int ticks,settle;
 private static void phase(int p){
  var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
  server.execute(()->{
   var world=server.overworld();
   for(int x=-3;x<=16;x++)for(int y=240;y<=246;y++)world.setBlockAndUpdate(new BlockPos(x,y,2),p==0?Blocks.AIR.defaultBlockState():Blocks.BLACK_CONCRETE.defaultBlockState());
   for(var mob:mobs){if(mob.isRemoved())throw new AssertionError("Fixture entity unloaded: "+mob.getType());mob.setGlowingTag(p==2||p==3);mob.setInvisible(p>=3);}
   phase=p;settle=0;
  });
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){
  var mc=Minecraft.getInstance();if(++ticks>2400)throw new AssertionError("Outline review timeout");
  if(mc.level==null||mc.player==null||mc.getOverlay()!=null||done)return;
  if(!requested){requested=true;mc.getSingleplayerServer().execute(()->{
   var server=mc.getSingleplayerServer();var world=server.overworld();
   server.clockManager().setTotalTicks(world.dimensionType().defaultClock().orElseThrow(),6000);
   server.setWeatherParameters(1000000,0,false,false);
   server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(world),"forceload add -16 -16 32 32");
   for(int x=-4;x<=17;x++)for(int z=-2;z<=3;z++){world.getChunkAt(new BlockPos(x,240,z));world.setBlockAndUpdate(new BlockPos(x,240,z),Blocks.WHITE_CONCRETE.defaultBlockState());}
   var team=server.getScoreboard().addPlayerTeam("outline_review");team.setColor(ChatFormatting.RED);
   var types=List.of(SinguloEntities.BLADE_ANDROID.get(),SinguloEntities.CARGO_DRONE.get(),SinguloEntities.HORIZON_WARDEN.get(),SinguloEntities.SECURITY_DRONE.get());
   for(int i=0;i<types.size();i++){
    var mob=i==1 ? new io.github.genichimaruo.singulo.drone.CargoDrone(SinguloEntities.CARGO_DRONE.get(),world){@Override public void tick(){baseTick();}} : (Mob)types.get(i).create(world,EntitySpawnReason.COMMAND);mob.setPos(i*4,241,0);mob.setYRot(0);mob.setNoAi(true);mob.setNoGravity(true);mob.setInvulnerable(true);mob.setPersistenceRequired();world.addFreshEntity(mob);server.getScoreboard().addPlayerToTeam(mob.getScoreboardName(),team);mobs.add(mob);
   }
   var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setNoGravity(true);player.teleportTo(world,6,242,20,Set.of(),180,0,false);phase(0);
  });}
  if(phase>=0){mc.setScreen(null);mc.options.hideGui=true;mc.player.setYRot(180);mc.player.setXRot(0);settle++;}
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
  if(phase<0||done||settle<180)return;
  int p=phase;phase=-1;
  if(Minecraft.getInstance().player.position().distanceTo(new net.minecraft.world.phys.Vec3(6,242,20))>0.5)throw new AssertionError("Camera displaced: "+Minecraft.getInstance().player.position());
  var mc=Minecraft.getInstance();Path dir=Path.of(System.getProperty("singulo.outlineOutput"));Files.createDirectories(dir);
  for(var mob:mobs){
   var client=mc.level.getEntity(mob.getId());
   if(client==null || client.isInvisible()!=(p>=3) || client.isCurrentlyGlowing()!=(p==2||p==3))
    throw new AssertionError("Missing/stale client fixture "+mob.getType()+" phase="+p);
  }
  try(var image=ReviewScreenshot.take(mc.getMainRenderTarget())){
   if(image.getWidth()!=1280||image.getHeight()!=800)throw new AssertionError("Unexpected outline viewport");
   for(int i=0;i<4;i++){
    int colored=0,dark=0;
    for(int y=350;y<475;y++)for(int x=440+100*i;x<540+100*i;x++){
     int c=image.getPixel(x,y),r=c>>>16&255,g=c>>>8&255,b=c&255;
     if(r>140&&r>g*1.5&&r>b*1.5)colored++;
     if(r<130&&g<130&&b<130)dark++;
    }
    if(p==0&&dark<20)throw new AssertionError("Normal entity missing: "+i);
    if((p==2||p==3)&&colored<20)throw new AssertionError("Outline missing: entity="+i+" phase="+p+" pixels="+colored);
    if((p==1||p==4)&&colored>10)throw new AssertionError("Unexpected outline: "+i);
    Singulo.LOGGER.info("SINGULO_OUTLINE_ENTITY phase={} index={} redPixels={} darkPixels={}",p,i,colored,dark);
   }
   image.writeToFile(dir.resolve("outline-"+p+".png"));
   int red=0;for(int y=120;y<680;y++)for(int x=60;x<1220;x++){int c=image.getPixel(x,y),r=c>>>16&255,g=c>>>8&255,b=c&255;if(r>140&&r>g*1.5&&r>b*1.5)red++;}
   Singulo.LOGGER.info("SINGULO_OUTLINE_PHASE {} redPixels={}",p,red);
   if((p==2||p==3)&&red<100)throw new AssertionError("Missing wall outline in phase "+p+": "+red);
   if((p==1||p==4)&&red>10)throw new AssertionError("Unexpected non-glowing outline "+p+": "+red);
  }
  if(p==4){done=true;Singulo.LOGGER.info("SINGULO_OUTLINE_COMPLETE 4 5");mc.stop();}else phase(p+1);
 }
}
