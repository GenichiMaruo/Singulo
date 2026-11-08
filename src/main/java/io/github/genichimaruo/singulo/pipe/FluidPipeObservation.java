package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

/** Bounded, observer-only diagnostics. Transfer correctness never depends on this cache. */
final class FluidPipeObservation {
    private static int packetTick=Integer.MIN_VALUE,packets;
    private final Level level;
    private final Map<BlockPos,FluidPipeBlockEntity> pipes=new HashMap<>();
    private final Map<Long,List<FluidPipeBlockEntity>> chunks=new HashMap<>();
    private final AABB bounds;
    private final Map<Route,List<BlockPos>> routes=new LinkedHashMap<>();
    private final Map<FluidPipeBlockEntity,Sample> samples=new HashMap<>();
    private Set<FluidPipeBlockEntity> selected=Set.of();
    private List<FluidPipeBlockEntity> nearby=List.of();
    private long refreshed=Long.MIN_VALUE;
    private int cursor;
    private record Route(BlockPos from,BlockPos start,BlockPos end,BlockPos to){}
    private record Near(FluidPipeBlockEntity pipe,double distance){}
    private static final class Sample {int in,out,amount;FluidStack fluid=FluidStack.EMPTY;boolean mixed;}
    FluidPipeObservation(Level level,Set<FluidPipeBlockEntity> all){
        this.level=level;AABB b=null;
        for(var pipe:all){
            pipes.put(pipe.getBlockPos(),pipe);
            long key=net.minecraft.world.level.ChunkPos.asLong(pipe.getBlockPos().getX()>>4,pipe.getBlockPos().getZ()>>4);
            chunks.computeIfAbsent(key,k->new ArrayList<>()).add(pipe);
            b=b==null?new AABB(pipe.getBlockPos()):b.minmax(new AABB(pipe.getBlockPos()));
        }
        bounds=b==null?new AABB(BlockPos.ZERO):b;
    }
    void begin(){
        samples.clear();long now=level.getGameTime();
        if(refreshed==Long.MIN_VALUE||now-refreshed>=20){
            refreshed=now;
            var viewers=level.players().stream().filter(SurveyGlassesItem::wearing)
                    .filter(p->bounds.inflate(24).contains(p.position())).map(p->p.position()).toList();
            if(viewers.isEmpty()){nearby=List.of();selected=Set.of();routes.clear();return;}
            // Query only nearby buckets and retain 256 candidates, avoiding a full-network
            // scan/sort every second when a player observes one end of a long network.
            Set<FluidPipeBlockEntity> candidates=new HashSet<>();
            for(var v:viewers)for(int x=((int)Math.floor(v.x-24))>>4;x<=((int)Math.floor(v.x+24))>>4;x++)
                for(int z=((int)Math.floor(v.z-24))>>4;z<=((int)Math.floor(v.z+24))>>4;z++)
                    candidates.addAll(chunks.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),List.of()));
            var nearest=new java.util.PriorityQueue<Near>(Comparator.comparingDouble(Near::distance).reversed());
            for(var pipe:candidates){
                double distance=viewers.stream().mapToDouble(v->Vec3.atCenterOf(pipe.getBlockPos()).distanceToSqr(v)).min().orElse(Double.MAX_VALUE);
                if(distance<=24*24&&(nearest.size()<256||distance<nearest.peek().distance())){
                    nearest.add(new Near(pipe,distance));if(nearest.size()>256)nearest.remove();
                }
            }
            nearby=nearest.stream().sorted(Comparator.comparingDouble(Near::distance)).map(Near::pipe).toList();
            selected=new HashSet<>(nearby);
        }
    }
    void moved(BlockPos from,BlockPos to,FluidStack fluid,int amount){
        moved(from,anchor(from),anchor(to),to,fluid,amount);
    }
    void moved(BlockPos from,BlockPos start,BlockPos end,BlockPos to,FluidStack fluid,int amount){
        if(selected.isEmpty()||amount<=0)return;
        if(start==null||end==null)return;
        List<BlockPos> route=route(from,start,end,to);
        for(int i=0;i<route.size();i++){
            BlockPos p=route.get(i);var pipe=pipes.get(p);if(!selected.contains(pipe))continue;
            Sample s=samples.computeIfAbsent(pipe,k->new Sample());
            if(!s.fluid.isEmpty()&&!io.github.genichimaruo.singulo.compat.Legacy.sameFluid(s.fluid,fluid))s.mixed=true;
            s.fluid=io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,1);s.amount=(int)Math.min(Integer.MAX_VALUE,(long)s.amount+amount);
            if(i>0)s.in|=mask(p,route.get(i-1));
            if(i+1<route.size())s.out|=mask(p,route.get(i+1));
        }
    }
    private static int mask(BlockPos from,BlockPos to){
        for(Direction d:Direction.values())if(from.relative(d).equals(to))return 1<<d.get3DDataValue();return 0;
    }
    private List<BlockPos> route(BlockPos from,BlockPos start,BlockPos end,BlockPos to){
        Route key=new Route(from,start,end,to);List<BlockPos> cached=routes.get(key);if(cached!=null)return cached;
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Map<BlockPos,BlockPos> previous=new HashMap<>();
        queue.add(start);previous.put(start,start);
        while(!queue.isEmpty()&&previous.size()<=4096){
            BlockPos p=queue.remove();if(p.equals(end))break;
            for(Direction d:Direction.values()){BlockPos next=p.relative(d);if(pipes.containsKey(next)&&!previous.containsKey(next)){previous.put(next,p);queue.add(next);}}
        }
        List<BlockPos> result=new ArrayList<>();
        if(previous.containsKey(end)){
            result.add(to);BlockPos p=end;
            for(int n=0;n<4096;n++){result.add(p);if(p.equals(start))break;p=previous.get(p);}
            if(!p.equals(start))return List.of();
            result.add(from);java.util.Collections.reverse(result);
        }
        if(routes.size()>=128)routes.remove(routes.keySet().iterator().next());
        List<BlockPos> immutable=List.copyOf(result);routes.put(key,immutable);return immutable;
    }
    private BlockPos anchor(BlockPos endpoint){
        for(Direction d:Direction.values()){BlockPos p=endpoint.relative(d);if(pipes.containsKey(p))return p;}return null;
    }
    void end(boolean offered,boolean moved){
        if(nearby.isEmpty())return;
        int now=level.getServer().getTickCount();if(now!=packetTick){packetTick=now;packets=64;}
        for(int n=0;n<nearby.size()&&packets>0;n++){
            var pipe=nearby.get(Math.floorMod(cursor++,nearby.size()));Sample s=samples.get(pipe);
            int state=s!=null?1:offered&&!moved?2:0;
            if(pipe.publish(state,s==null?0:s.in,s==null?0:s.out,s==null?0:s.amount,s==null?FluidStack.EMPTY:s.fluid,s!=null&&s.mixed))packets--;
        }
    }
}
