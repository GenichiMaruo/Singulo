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
import net.minecraftforge.fluids.FluidStack;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=Singulo.MODID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class CosmicWorldReviewHarness {
    private static volatile boolean ready;
    private static int index=Integer.getInteger("singulo.cosmicStart",0),ticks,wait;
    private static long frame,shown;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static java.util.List<net.minecraft.client.renderer.ShaderInstance> oldShaders;
    private static io.github.genichimaruo.singulo.machine.MachineBlockEntity pipeGiver;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void drive(net.minecraftforge.event.TickEvent.ServerTickEvent event){ if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if((index==10||index==11)&&pipeGiver!=null&&!pipeGiver.isRemoved())pipeGiver.forceOutputTankForTest(new FluidStack(Fluids.WATER,500));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){ if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;frame++;}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event)throws Exception{ if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(reload!=null){
            if(!reload.isDone()||mc.getOverlay()!=null)return;
            reload.join();CosmicGeometryReviewHarness.afterReload(oldShaders);
            Singulo.LOGGER.info("SINGULO_COSMIC_COMPLETE 18");mc.stop();return;
        }
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
        if(index>=13&&index<15&&wait==delay){
            // 核を今できたことにして、開く途中を撮る（13: 種が縮む途中、14: 影が広がる途中）
            mc.getSingleplayerServer().execute(CosmicWorldReviewHarness::formCore);
            openAt=wait+(index==13?16:34);
        }
        if(index>=13&&index<15&&wait<openAt)return;
        if(wait==(index>=13&&index<15?openAt:delay)){shown=frame;return;}
        if(frame-shown<4)return;
        Path output=Path.of(System.getProperty("singulo.cosmicOutput"));Files.createDirectories(output);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(output.resolve("cosmic-"+index+".png"));}
        Singulo.LOGGER.info("SINGULO_COSMIC_CAPTURE {}",index);
        if(++index==18){CosmicGeometryReviewHarness.verify();oldShaders=CosmicGeometryReviewHarness.shaders();reload=mc.reloadResourcePacks();return;}
        wait=0;
    }
    private static CosmicChamberBlockEntity opening;
    private static int openAt;
    private static void formCore(){
        var level=(net.minecraft.server.level.ServerLevel)opening.getLevel();
        var tag=opening.saveWithoutMetadata();tag.putBoolean("core",true);
        opening.load(tag);
        level.sendBlockUpdated(opening.getBlockPos(),opening.getBlockState(),opening.getBlockState(),3);
    }
    private static void prepare(Minecraft mc){
        var server=mc.getSingleplayerServer();var level=server.overworld();
        var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);player.getAbilities().flying=true;player.onUpdateAbilities();
        level.setDayTime(6000);level.setWeatherParameters(0,6000,false,false);
        if(index>=16){
            BlockPos c=new BlockPos(48,100,0);
            player.teleportTo(level,c.getX()+(index==16?-8:8),c.getY()+5,c.getZ()+12.5,index==16?-90:90,4);
            ready=true;return;
        }
        if(index==15){
            // 光の奥の水とガラスが手前に割り込まないこと
            BlockPos c=new BlockPos(48,100,0);
            for(int z=5;z<=11;z++)for(int y=1;y<=7;y++){
                var state=y<=4?net.minecraft.world.level.block.Blocks.WATER.defaultBlockState():net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                level.setBlock(c.offset(-4,y,z),state,2|16);
            }
            player.teleportTo(level,c.getX()+6,c.getY()+5,c.getZ()+8,90,3);
            ready=true;return;
        }
        if(index>=13){
            BlockPos c=new BlockPos(0,130+(index-13)*20,200);
            Blueprints.layout(Blueprints.Kind.TIDAL_SPINDLE,c,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
            opening=(CosmicChamberBlockEntity)level.getBlockEntity(c);opening.ready();
            player.teleportTo(level,c.getX()-6.5,c.getY()+6.5,c.getZ()-3,-40,10);
            ready=true;return;
        }
        if(index>=9){prepareExtra(level,player);ready=true;return;}
        int kind=index/3,state=index%3;BlockPos c=new BlockPos(kind*24,100,0);
        var type=kind==0?Blueprints.Kind.SUSPENSION_RESERVOIR:kind==1?Blueprints.Kind.PASTA_ACCUMULATOR:Blueprints.Kind.TIDAL_SPINDLE;
        Blueprints.layout(type,c,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
        var be=(CosmicChamberBlockEntity)level.getBlockEntity(c);var tag=be.saveWithoutMetadata();
        tag.putLong("stored",kind==1?state*4_000_000_000_000L:10_000_000_000L);tag.putBoolean("core",true);
        if(kind==0)tag.put("tank",new net.minecraftforge.fluids.capability.templates.FluidTank(CosmicChamberBlockEntity.FLUID_CAPACITY){
            {setFluid(new FluidStack(Fluids.WATER,(state+1)*CosmicChamberBlockEntity.FLUID_CAPACITY/3));}
        }.writeToNBT(new net.minecraft.nbt.CompoundTag()));
        if(kind==2){tag.putInt("duration",1200);tag.putInt("progress",state*400);tag.put("reserved",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("tidal_filament")),4).save(new net.minecraft.nbt.CompoundTag()));tag.putInt("cost",0);}
        be.load(tag);be.ready();
        level.sendBlockUpdated(c,be.getBlockState(),be.getBlockState(),3);
        if(kind==2)player.teleportTo(level,c.getX()+15,c.getY()+6,c.getZ()+1,72,8);
        else player.teleportTo(level,c.getX()+7,c.getY()+6,c.getZ()-7.5,38,8);
        ready=true;
    }
    private static void prepareExtra(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        if(index==9){
            for(int x:new int[]{80,88}){
                BlockPos p=new BlockPos(x,100,0);level.setBlockAndUpdate(p,io.github.genichimaruo.singulo.registry.SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING,Direction.SOUTH));
                var be=(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity)level.getBlockEntity(p);be.setData(new io.github.genichimaruo.singulo.wormhole.WormholeData(916000,0));
                var tag=be.saveWithoutMetadata();tag.putBoolean("traversable",true);tag.putInt("size",1);tag.putInt("grace",6000);be.load(tag);
            }
            player.teleportTo(level,91.5,101,4.5,145,-6);return;
        }
        if(index==10||index==11){
            var type=io.github.genichimaruo.singulo.machine.MachineType.ELECTROLYZER;BlockPos a=new BlockPos(110,100,0),b=new BlockPos(116,100,0);
            level.setBlockAndUpdate(a,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(b,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(a,io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES.get(type).get().defaultBlockState());level.setBlockAndUpdate(b,io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES.get(type).get().defaultBlockState());
            pipeGiver=(io.github.genichimaruo.singulo.machine.MachineBlockEntity)level.getBlockEntity(a);
            var taker=(io.github.genichimaruo.singulo.machine.MachineBlockEntity)level.getBlockEntity(b);
            if(index==11)taker.automationFluids().fill(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            for(int x=111;x<116;x++)level.setBlockAndUpdate(new BlockPos(x,100,0),io.github.genichimaruo.singulo.registry.SinguloBlocks.FLUID_PIPE.get().defaultBlockState());
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("survey_glasses"))));
            player.teleportTo(level,113.5,101,4.5,180,28);return;
        }
        BlockPos c=new BlockPos(160,100,0);Direction back=Direction.NORTH;
        Blueprints.layout(Blueprints.Kind.JET_FORGE,c,back,io.github.genichimaruo.singulo.forge.JetForgeLayout.H).forEach((p,s)->level.setBlockAndUpdate(p,s));
        var be=(io.github.genichimaruo.singulo.forge.JetForgeBlockEntity)level.getBlockEntity(c);be.forceBlackHole();
        var tag=be.saveWithoutMetadata();tag.putBoolean("parallel_upgrade",true);tag.putInt("batch_size",4);tag.putInt("phase",io.github.genichimaruo.singulo.forge.JetForgeBlockEntity.IRRADIATE);tag.putInt("phase_ticks",400);tag.putInt("irradiate_ticks",12000);tag.putInt("energy_per_tick",0);
        tag.put("working",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("collapsar_billet"))).save(new net.minecraft.nbt.CompoundTag()));tag.put("result",new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("collapsar_ingot")),4).save(new net.minecraft.nbt.CompoundTag()));be.load(tag);
        var target=io.github.genichimaruo.singulo.forge.JetForgeLayout.collector(c,back);
        for(int x=-40;x<=6;x++)for(int y=-4;y<=14;y++){
            var state=y<=1?net.minecraft.world.level.block.Blocks.WATER.defaultBlockState():net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            BlockPos wall=BlockPos.containing(target.x+x,target.y+y-4,target.z-20);if(level.isEmptyBlock(wall))level.setBlock(wall,state,2|16);
        }
// ジェットの奥のブラックホール（ジェットの下に見えること）
        BlockPos far=BlockPos.containing((2*c.getX()+target.x)/3+6,target.y,target.z-14);
        level.setBlockAndUpdate(far,io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState());
        var rogue=(io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity)level.getBlockEntity(far);rogue.setStable(true);rogue.setCore(5000,0.8);
        player.teleportTo(level,(2*c.getX()+target.x)/3+1,target.y+1,target.z+9,180,4);
    }
}
