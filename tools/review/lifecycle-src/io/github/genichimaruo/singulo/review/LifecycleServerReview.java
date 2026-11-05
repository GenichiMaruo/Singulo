package io.github.genichimaruo.singulo.review;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeLayout;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

@EventBusSubscriber(modid = Singulo.MODID)
public final class LifecycleServerReview {
    public static final BlockPos C = new BlockPos(4095, 80, 4095);
    private static final Direction BACK = Direction.SOUTH;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("singulo_pc_review", Comparator.comparingLong(ChunkPos::toLong));
    private static final List<ChunkPos> footprint = new ArrayList<>();
    private static final Map<String,Object> evidence = new LinkedHashMap<>();
    private static final Set<Long> unloadEvents = new HashSet<>();
    private static JetForgeBlockEntity be, previous;
    private static ChunkPos tail;
    private static int phase, ticks, at, frozen, savedProgress, aLogins;
    private static long frozenEnergy, savedConsumed;
    private static String savedInventory;
    private static boolean aPresent, bothMenus, playerTravel, travelDone;

    private static Path output() { return Path.of(System.getProperty("singulo.lifecycleOutput")); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static boolean loaded(ServerLevel level, ChunkPos p) { return level.getChunkSource().hasChunk(p.x,p.z); }
    @SubscribeEvent
    public static void unloaded(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if(Boolean.getBoolean("singulo.lifecycleServer") && !event.getLevel().isClientSide()) unloadEvents.add(event.getChunk().getPos().toLong());
    }
    private static void ticket(ServerLevel level, ChunkPos p, boolean add) {
        if (add) level.getChunkSource().addRegionTicket(TICKET,p,0,p);
        else level.getChunkSource().removeRegionTicket(TICKET,p,0,p);
    }
    private static String inventory() {
        StringBuilder s = new StringBuilder();
        for (int i=0;i<be.items().getSlots();i++) s.append(be.items().getStackInSlot(i)).append(';');
        s.append(be.charged()).append('/').append(be.working()).append('/').append(be.result());
        return s.toString();
    }
    private static void drive(ServerLevel level, boolean power) {
        if (power) be.energy().receiveLong(Long.MAX_VALUE,false);
        JetForgeBlockEntity.serverTick(level,C,be.getBlockState(),be);
    }
    private static void build(ServerLevel level) {
        BlockPos a=JetForgeLayout.SPEC.pos(C,BACK,0,0,0), b=JetForgeLayout.SPEC.pos(C,BACK,43,0,16);
        for(int x=Math.min(a.getX(),b.getX())>>4;x<=(Math.max(a.getX(),b.getX())>>4);x++)
            for(int z=Math.min(a.getZ(),b.getZ())>>4;z<=(Math.max(a.getZ(),b.getZ())>>4);z++) {
                ChunkPos p=new ChunkPos(x,z); footprint.add(p); ticket(level,p,true); level.getChunk(p.x,p.z);
            }
        for(var e:Blueprints.layout(Blueprints.Kind.JET_FORGE,C,BACK,JetForgeLayout.H).entrySet()) level.setBlockAndUpdate(e.getKey(),e.getValue());
        be=(JetForgeBlockEntity)level.getBlockEntity(C);
        be.forceBlackHole();
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN,new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("collapsar_billet"))));
        be.items().setStackInSlot(JetForgeBlockEntity.SLOT_IN+1,new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("mass_pellet")),16));
        be.tank().fill(new FluidStack(SinguloFluids.get("liquid_helium"),1000),IFluidHandler.FluidAction.EXECUTE);
        ChunkPos controller=new ChunkPos(C);
        tail=footprint.stream().max(Comparator.comparingInt(p->Math.abs(p.x-controller.x)+Math.abs(p.z-controller.z))).orElseThrow();
        require(!tail.equals(controller),"Fixture must cross a real chunk boundary");
        evidence.put("minecraft",net.minecraft.SharedConstants.getCurrentVersion().getName());
        evidence.put("footprintChunks",footprint.toString());
        evidence.put("controllerChunk",controller.toString());
        evidence.put("unloadedFootprintChunk",tail.toString());
        evidence.put("probeMode","Real FULL-only tickets (level 33), explicit serverTick probe; natural block ticking resumes for multiplayer");
    }
    private static void write(String name,String value) throws Exception { Files.createDirectories(output());Files.writeString(output().resolve(name),value); }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END)return;
        if (!Boolean.getBoolean("singulo.lifecycleServer")) return;
        var server=event.getServer(); var level=server.overworld();
        try {
            if(++ticks>8000) throw new AssertionError("Lifecycle review timeout phase="+phase);
            switch(phase) {
                case 0 -> { build(level);phase=1; }
                case 1 -> {
                    drive(level,true);
                    if(be.phase()!=JetForgeBlockEntity.IDLE && be.phaseTicks()>12) {
                        ticket(level,tail,false);phase=2;at=ticks;
                    }
                }
                case 2 -> {
                    if(!loaded(level,tail)) {
                        drive(level,true);
                        require(be.hold()==JetForgeBlockEntity.UNLOADED,"Actual unloaded chunk did not pause forge");
                        frozen=be.phaseTicks();frozenEnergy=be.consumedTotal();phase=3;at=ticks;
                        evidence.put("actualFootprintInaccessible",true);
                        evidence.put("footprintUnloadEvent",unloadEvents.contains(tail.toLong()));
                        evidence.put("probeSuspendedWhileAwaitingEviction",true);
                        Singulo.LOGGER.info("SINGULO_REAL_CHUNK_UNLOADED tail={} progress={}",tail,frozen);
                    } else {
                        if((ticks-at)%200==0) Singulo.LOGGER.info("SINGULO_CHUNK_WAIT {} {}",tail,level.getChunkSource().getChunkDebugData(tail));
                        require(ticks-at<1000,"Tail chunk never actually unloaded");
                    }
                }
                case 3 -> {
                    drive(level,true);
                    require(!loaded(level,tail),"Forge unexpectedly forced the unloaded footprint chunk");
                    require(be.hold()==JetForgeBlockEntity.UNLOADED && be.phaseTicks()==frozen && be.consumedTotal()==frozenEnergy,"Unloaded forge advanced or consumed energy");
                    if(ticks-at>=100) { ticket(level,tail,true);level.getChunk(tail.x,tail.z);phase=4;at=ticks; }
                }
                case 4 -> {
                    drive(level,true);
                    if(be.hold()==JetForgeBlockEntity.OK && be.phaseTicks()>frozen) {
                        evidence.put("pausedProbeTicks",100);evidence.put("actualFootprintReloadResume",true);
                        savedProgress=be.phaseTicks();savedConsumed=be.consumedTotal();savedInventory=inventory();previous=be;
                        for(ChunkPos p:footprint) ticket(level,p,false);
                        phase=5;at=ticks;
                    } else require(ticks-at<100,"Reload failed to resume");
                }
                case 5 -> {
                    if(!loaded(level,new ChunkPos(C)) && unloadEvents.contains(new ChunkPos(C).toLong())) {
                        evidence.put("actualControllerUnload",true);
                        for(ChunkPos p:footprint) { ticket(level,p,true);level.getChunk(p.x,p.z); }
                        be=(JetForgeBlockEntity)level.getBlockEntity(C);
                        require(be!=null && be!=previous,"Reload did not create a new block entity");
                        require(be.phaseTicks()==savedProgress && be.consumedTotal()==savedConsumed && inventory().equals(savedInventory),"Progress, consumed energy or reserved inventory changed across actual disk unload/reload");
                        require(level.getBlockState(BlockPos.containing(JetForgeLayout.core(C,BACK))).isAir(),"Unload spawned a duplicate rogue black hole");
                        evidence.put("diskReloadPreservesState",true);
                        write("chunk-complete.json",new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
                        write("server-ready.txt","ready for loopback clients");phase=6;at=ticks;
                        Singulo.LOGGER.info("SINGULO_REAL_CHUNK_COMPLETE newBlockEntity=true reservedInventory=true progress={}",savedProgress);
                        for(ChunkPos p:footprint) level.setChunkForced(p.x,p.z,true);
                    } else require(ticks-at<1000,"Controller never actually unloaded");
                }
                case 6,7 -> {
                    var players=server.getPlayerList().getPlayers();
                    boolean nowA=players.stream().anyMatch(p->p.getGameProfile().getName().equals("ReviewA"));
                    if(nowA&&!aPresent) aLogins++;
                    aPresent=nowA;
                    for(var player:players) if(!playerTravel && player.distanceToSqr(C.getX(),C.getY(),C.getZ())>256) {
                        player.setGameMode(GameType.CREATIVE);player.teleportTo(level,C.getX()+0.5,C.getY()+1,C.getZ()-2.5,0,0);
                    }
                    if(phase==7) be.energy().receiveLong(Long.MAX_VALUE,false);
                    if(phase==6 && players.size()==2) { be.forcePhase(JetForgeBlockEntity.IRRADIATE,100);phase=7; }
                    if(phase==7 && !bothMenus && players.size()==2 && players.stream().allMatch(p->p.containerMenu instanceof DeviceMenu)) {
                        if(!travelDone) {
                            playerTravel=true;previous=be;unloadEvents.remove(new ChunkPos(C).toLong());
                            for(ChunkPos p:footprint) {ticket(level,p,false);level.setChunkForced(p.x,p.z,false);}
                            for(var player:players) {player.closeContainer();player.teleportTo(level,8,150,8,0,0);}
                            phase=8;at=ticks;break;
                        }
                        bothMenus=true;write("both-menus.txt","both server-side menus opened through network interaction");
                    }
                    if(Files.exists(output().resolve("client-A-complete.txt"))&&Files.exists(output().resolve("client-B-complete.txt"))) {
                        require(bothMenus&&aLogins>=2,"Two-client menu or reconnect evidence missing");
                        require(be.hasCore()&&be.charged().size()==2&&be.result().getCount()==1,"Reserved batch duplicated or lost during multiplayer/reconnect");
                        evidence.put("twoRealClients",true);evidence.put("bothServerMenus",true);evidence.put("clientAConnections",aLogins);
                        evidence.put("reservedBatchRetained",true);evidence.put("complete",true);
                        write("results.json",new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
                        Singulo.LOGGER.info("SINGULO_MULTIPLAYER_COMPLETE clients=2 reconnects={} inventory=true",aLogins-1);
                        phase=10;server.halt(false);
                    }
                }
                case 8 -> {
                    if(!loaded(level,new ChunkPos(C))&&unloadEvents.contains(new ChunkPos(C).toLong())) {
                        savedProgress=be.phaseTicks();savedConsumed=be.consumedTotal();savedInventory=inventory();
                        evidence.put("playerDrivenUnload",true);
                        for(var player:server.getPlayerList().getPlayers()) player.teleportTo(level,C.getX()+0.5,C.getY()+1,C.getZ()-2.5,0,0);
                        phase=9;at=ticks;
                    } else require(ticks-at<1000,"Players left but controller did not unload");
                }
                case 9 -> {
                    if(loaded(level,new ChunkPos(C))) {
                        var raw=level.getBlockEntity(C);
                        if(raw instanceof JetForgeBlockEntity fresh && fresh!=previous) {
                            be=fresh;
                            require(be.phaseTicks()==savedProgress&&be.consumedTotal()==savedConsumed&&inventory().equals(savedInventory),"Player-driven reload changed reserved process state");
                            evidence.put("playerDrivenReloadPreservesState",true);playerTravel=false;travelDone=true;phase=7;
                            Singulo.LOGGER.info("SINGULO_PLAYER_CHUNK_RELOAD_COMPLETE progress={} consumed={}",savedProgress,savedConsumed);
                        }
                    }
                    require(ticks-at<1000,"Players returned but new forge block entity did not load");
                }
                default -> {}
            }
        } catch(Throwable error) {
            write("failure.txt",error.toString()+" phase="+phase);server.halt(false);throw error;
        }
    }
}
