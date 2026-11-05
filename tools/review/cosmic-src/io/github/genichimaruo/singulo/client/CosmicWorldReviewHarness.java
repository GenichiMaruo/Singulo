package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

@net.neoforged.fml.common.EventBusSubscriber(modid=Singulo.MODID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class CosmicWorldReviewHarness {
    private static volatile boolean ready;
    private static int index=Integer.getInteger("singulo.cosmicStart",0),ticks,wait;
    private static long frame,shown;
    private static io.github.genichimaruo.singulo.machine.MachineBlockEntity pipeGiver;
    @net.neoforged.bus.api.SubscribeEvent
    public static void drive(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if((index==10||index==11)&&pipeGiver!=null&&!pipeGiver.isRemoved())pipeGiver.forceOutputTankForTest(new FluidStack(Fluids.WATER,500));
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post e){frame++;}
    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event)throws Exception{
        Minecraft mc=Minecraft.getInstance();
        if(++ticks>20*240)throw new IllegalStateException("Cosmic review timed out "+index);
        if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                && mc.screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("selectWorld.backupQuestion.experimental")){
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b
                    && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents l
                    && l.getKey().equals("selectWorld.backupJoinSkipButton"))b.onPress();return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        mc.player.setNoGravity(true);mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(wait==0){
            wait=1;ready=false;mc.setScreen(null);mc.options.hideGui=index!=10&&index!=11;
            mc.getSingleplayerServer().execute(()->prepare(mc));return;
        }
        if(!ready)return;
        int delay=index==10?40:80;
        if(++wait<delay)return;
        if(wait==delay){shown=frame;return;}
        if(frame-shown<4)return;
        Path output=Path.of(System.getProperty("singulo.cosmicOutput"));Files.createDirectories(output);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(output.resolve("cosmic-"+index+".png"));}
        Singulo.LOGGER.info("SINGULO_COSMIC_CAPTURE {}",index);
        if(++index==13){Singulo.LOGGER.info("SINGULO_COSMIC_COMPLETE 13");mc.stop();return;}
        wait=0;
    }
    private static void prepare(Minecraft mc){
        var server=mc.getSingleplayerServer();var level=server.overworld();
        var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);player.getAbilities().flying=true;player.onUpdateAbilities();
        level.setDayTime(6000);level.setWeatherParameters(0,6000,false,false);
        if(index>=9){prepareExtra(level,player);ready=true;return;}
        int kind=index/3,state=index%3;BlockPos c=new BlockPos(kind*24,100,0);
        var type=kind==0?Blueprints.Kind.SUSPENSION_RESERVOIR:kind==1?Blueprints.Kind.PASTA_ACCUMULATOR:Blueprints.Kind.TIDAL_SPINDLE;
        Blueprints.layout(type,c,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
        var be=(CosmicChamberBlockEntity)level.getBlockEntity(c);var tag=be.saveWithoutMetadata(level.registryAccess());
        tag.putLong("stored",kind==1?state*4_000_000_000_000L:10_000_000_000L);tag.putBoolean("core",true);
        if(kind==0)tag.put("tank",new net.neoforged.neoforge.fluids.capability.templates.FluidTank(CosmicChamberBlockEntity.FLUID_CAPACITY){
            {setFluid(new FluidStack(Fluids.WATER,(state+1)*CosmicChamberBlockEntity.FLUID_CAPACITY/3));}
        }.writeToNBT(level.registryAccess(),new net.minecraft.nbt.CompoundTag()));
        if(kind==2){tag.putInt("duration",1200);tag.putInt("progress",state*400);tag.put("reserved",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("tidal_filament")),4).save(level.registryAccess()));tag.putInt("cost",0);}
        be.loadWithComponents(tag,level.registryAccess());be.ready();
        level.sendBlockUpdated(c,be.getBlockState(),be.getBlockState(),3);
        if(kind==2)player.teleportTo(level,c.getX()+17,c.getY()+8,c.getZ()-1,68,15);
        else player.teleportTo(level,c.getX()+9,c.getY()+8,c.getZ()-12,32,18);
        ready=true;
    }
    private static void prepareExtra(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        if(index==9){
            for(int x:new int[]{80,88}){
                BlockPos p=new BlockPos(x,100,0);level.setBlockAndUpdate(p,io.github.genichimaruo.singulo.registry.SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING,Direction.SOUTH));
                var be=(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity)level.getBlockEntity(p);be.setData(new io.github.genichimaruo.singulo.wormhole.WormholeData(916000,0));
                var tag=be.saveWithoutMetadata(level.registryAccess());tag.putBoolean("traversable",true);tag.putInt("size",1);tag.putInt("grace",6000);be.loadWithComponents(tag,level.registryAccess());
            }
            player.teleportTo(level,90,106,14,145,12);return;
        }
        if(index==10||index==11){
            var type=io.github.genichimaruo.singulo.machine.MachineType.ELECTROLYZER;BlockPos a=new BlockPos(110,100,0),b=new BlockPos(116,100,0);
            level.setBlockAndUpdate(a,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(b,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(a,io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES.get(type).get().defaultBlockState());level.setBlockAndUpdate(b,io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES.get(type).get().defaultBlockState());
            pipeGiver=(io.github.genichimaruo.singulo.machine.MachineBlockEntity)level.getBlockEntity(a);
            var taker=(io.github.genichimaruo.singulo.machine.MachineBlockEntity)level.getBlockEntity(b);
            if(index==11)taker.automationFluids().fill(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            for(int x=111;x<116;x++)level.setBlockAndUpdate(new BlockPos(x,100,0),io.github.genichimaruo.singulo.registry.SinguloBlocks.FLUID_PIPE.get().defaultBlockState());
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("survey_glasses"))));
            player.teleportTo(level,113.5,101,4.5,180,28);return;
        }
        BlockPos c=new BlockPos(160,100,0);Direction back=Direction.NORTH;
        Blueprints.layout(Blueprints.Kind.JET_FORGE,c,back,io.github.genichimaruo.singulo.forge.JetForgeLayout.H).forEach((p,s)->level.setBlockAndUpdate(p,s));
        var be=(io.github.genichimaruo.singulo.forge.JetForgeBlockEntity)level.getBlockEntity(c);be.forceBlackHole();
        var tag=be.saveWithoutMetadata(level.registryAccess());tag.putBoolean("parallel_upgrade",true);tag.putInt("batch_size",4);tag.putInt("phase",io.github.genichimaruo.singulo.forge.JetForgeBlockEntity.IRRADIATE);tag.putInt("phase_ticks",400);tag.putInt("irradiate_ticks",12000);tag.putInt("energy_per_tick",0);
        tag.put("working",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("collapsar_billet"))).save(level.registryAccess()));tag.put("result",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("collapsar_ingot")),4).save(level.registryAccess()));be.loadWithComponents(tag,level.registryAccess());
        var target=io.github.genichimaruo.singulo.forge.JetForgeLayout.collector(c,back);player.teleportTo(level,target.x+10,target.y+6,target.z+14,145,15);
    }
}
