package io.github.genichimaruo.singulo.review;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

@EventBusSubscriber(modid=Singulo.MODID)
public final class CosmicRestartReviewHarness {
    private static int ticks;
    private static boolean restart;
    private static Path output(){return Path.of(System.getProperty("singulo.cosmicRestartOutput"));}
    private static BlockPos pos(int kind){return new BlockPos(kind*20,80,0);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event)throws Exception{
        if(System.getProperty("singulo.cosmicRestartOutput")==null)return;
        var server=event.getServer();var level=server.overworld();
        if(++ticks==1){
            Files.createDirectories(output());restart=Files.exists(output().resolve("saved.txt"));
            for(int kind=0;kind<3;kind++){
                var c=pos(kind);level.getChunkAt(c);
                var shape=io.github.genichimaruo.singulo.cosmic.CosmicLayouts.of(kind);
                for(int x=0;x<shape.width();x++)for(int z=0;z<shape.depth();z++)level.getChunkAt(shape.pos(c,Direction.SOUTH,x,0,z));
                if(!restart){
                    var type=kind==0?Blueprints.Kind.SUSPENSION_RESERVOIR:kind==1?Blueprints.Kind.PASTA_ACCUMULATOR:Blueprints.Kind.TIDAL_SPINDLE;
                    Blueprints.layout(type,c,Direction.SOUTH,7).forEach((p,s)->level.setBlockAndUpdate(p,s));
                }
                check(level.getBlockEntity(c) instanceof CosmicChamberBlockEntity,"Missing persisted controller "+kind);
                var be=(CosmicChamberBlockEntity)level.getBlockEntity(c);
                if(!restart){
                    var tag=be.saveWithoutMetadata(level.registryAccess());tag.putLong("stored",kind==1?7_123_456_789_012L:0);
                    if(kind==2){tag.putBoolean("core",true);tag.putInt("progress",123);tag.putInt("duration",1200);tag.putInt("cost",20_000_000);tag.put("reserved",item("tidal_filament",4).save(level.registryAccess()));}
                    be.loadWithComponents(tag,level.registryAccess());
                    if(kind==0)be.tank().setFluid(new FluidStack(Fluids.WATER,7_654_321));
                    if(kind==2){be.tank().setFluid(new FluidStack(SinguloFluids.get("liquid_helium"),1234));be.items().setStackInSlot(0,item("collapsar_billet",7));}be.setChanged();
                    be.cyclePort(0);be.cyclePort(1);
                }else{
                    check(be.portMode(0)==CosmicChamberBlockEntity.PORT_OUTPUT&&be.portMode(1)==CosmicChamberBlockEntity.PORT_BOTH,"Port modes lost across full server restart "+kind);
                    if(kind==0)check(be.tank().getFluidAmount()==7_654_321,"Fluid lost across full server restart");
                    if(kind==1)check(be.stored()==7_123_456_789_012L,"Long energy lost across full server restart");
                    if(kind==2)check(be.core()&&be.progress()==123&&be.reserved().getCount()==4&&be.items().getStackInSlot(0).getCount()==7&&be.tank().getFluidAmount()==1234,"Reserved work lost across full server restart");
                }
            }
        }
        if(ticks==30){
            for(int kind=0;kind<3;kind++){
                var be=(CosmicChamberBlockEntity)level.getBlockEntity(pos(kind));
                check(be.ready(),"Persisted structure did not form "+kind);
                for(int index=0;index<2;index++){
                    var port=level.getBlockEntity(be.portPos(index));
                    check(port instanceof io.github.genichimaruo.singulo.multiblock.PortBlockEntity p&&be.getBlockPos().equals(p.controllerPos()),"Port did not relink after restart "+kind+":"+index);
                }
            }
            server.saveEverything(true,true,true);
            Files.writeString(output().resolve(restart?"results.json":"saved.txt"),restart?"{\"complete\":true,\"ordinaryServerRestart\":true,\"fluid\":7654321,\"energy\":7123456789012,\"reserved\":4,\"progress\":123,\"portModesPersisted\":true,\"portsRelinkedAfterRestart\":true}":"saved through ordinary server shutdown");
            Singulo.LOGGER.info("SINGULO_COSMIC_RESTART_{}",restart?"COMPLETE":"SAVED");server.halt(false);
        }
    }
    private static ItemStack item(String id,int count){return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)),count);}
}
