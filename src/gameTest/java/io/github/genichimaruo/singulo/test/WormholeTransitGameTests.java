package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.github.genichimaruo.singulo.wormhole.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class WormholeTransitGameTests {
    private static final BlockPos A=new BlockPos(5,1,5),B=new BlockPos(20,1,5);
    private static WormholeMouthBlockEntity mouth(GameTestHelper h,BlockPos p,long pair,boolean upgrade){
        h.setBlock(p,SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,Direction.SOUTH));
        WormholeMouthBlockEntity be=h.getBlockEntity(p);be.setData(new WormholeData(pair,0));
        var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());tag.putBoolean("traversable",upgrade);tag.putInt("grace",6000);tag.putInt("size",1);be.loadWithComponents(tag,h.getLevel().registryAccess());return be;
    }
    private static void floor(GameTestHelper h){
        for(int x=17;x<=23;x++)for(int z=6;z<=9;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
    }
    private static void place(ServerPlayer player,Vec3 at){player.setPos(at.x,at.y,at.z);player.setDeltaMovement(Vec3.ZERO);}
    @GameTest(template="forge",timeoutTicks=80)
    public static void wormholeTransitDetectsEntryBetweenLogisticsPulses(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();var a=mouth(h,A,pair,true);var b=mouth(h,B,pair,true);floor(h);
        var player=TestScope.player(h);
        h.succeedWhen(()->{
            h.assertTrue(h.getLevel().getGameTime()%5!=0,"Wait for a tick between logistics pulses");
            WormholeMouthBlockEntity.serverTick(h.getLevel(),b.getBlockPos(),b.getBlockState(),b);
            place(player,WormholeTransit.center(a).add(0,-0.9,0));
            WormholeMouthBlockEntity.serverTick(h.getLevel(),a.getBlockPos(),a.getBlockState(),a);
            h.assertTrue(player.position().distanceToSqr(Vec3.atCenterOf(b.getBlockPos()))<30,"Entry missed between 5-tick logistics pulses");
        });
    }
    @GameTest(template="forge",timeoutTicks=100)
    public static void wormholeTransitCrossesDimensionsToLoadedSafePartner(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();var a=mouth(h,A,pair,true);
        var target=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos pos=h.absolutePos(B).atY(200);
        // Fixture setup explicitly loads a target chunk; the production transit must not do so.
        target.getChunkAt(pos);
        var previous=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var scope=TestScope.of(h);
        var ticket=net.minecraft.server.level.TicketType.create("singulo_transit_fixture",java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
        var chunks=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,0,0),pos.offset(2,0,3)))chunks.add(new net.minecraft.world.level.ChunkPos(p));
        for(var chunk:chunks){
            target.getChunkSource().addRegionTicket(ticket,chunk,0,chunk);target.getChunk(chunk.x,chunk.z);
            scope.defer(()->target.getChunkSource().removeRegionTicket(ticket,chunk,0,chunk));
        }
        scope.defer(()->previous.forEach((p,s)->target.setBlockAndUpdate(p,s)));
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,0,1),pos.offset(2,5,3))){
            previous.put(p.immutable(),target.getBlockState(p));target.setBlockAndUpdate(p,p.getY()==200?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        }
        previous.put(pos,target.getBlockState(pos));target.setBlockAndUpdate(pos,SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,Direction.SOUTH));
        var b=(WormholeMouthBlockEntity)target.getBlockEntity(pos);b.setData(new WormholeData(pair,0));
        var tag=b.saveWithoutMetadata(target.registryAccess());tag.putBoolean("traversable",true);tag.putInt("grace",6000);tag.putInt("size",1);b.loadWithComponents(tag,target.registryAccess());
        ServerPlayer player=TestScope.player(h);
        h.runAtTickTime(10,()->{
            // The explicitly loaded remote fixture is outside player simulation distance.
            // Tick it once to perform the normal registry registration before testing passage.
            WormholeMouthBlockEntity.serverTick(h.getLevel(),a.getBlockPos(),a.getBlockState(),a);
            WormholeMouthBlockEntity.serverTick(target,pos,b.getBlockState(),b);
            h.assertTrue(a.partner()==b&&b.partner()==a,"Cross-dimensional pairing failed");place(player,WormholeTransit.center(a).add(0,-0.9,0));
            WormholeTransit.tick(a,h.getLevel());
            h.assertTrue(player.serverLevel()==target&&player.position().distanceToSqr(Vec3.atCenterOf(pos))<30&&player.isOnPortalCooldown(),"Cross-dimensional transit failed");h.succeed();
        });
    }
    @GameTest(template="forge",timeoutTicks=80)
    public static void wormholeTransitRequiresBothUpgradesAndUsesCooldown(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();var a=mouth(h,A,pair,true);var b=mouth(h,B,pair,false);floor(h);
        ServerPlayer player=TestScope.player(h);
        h.runAtTickTime(5,()->{
            place(player,WormholeTransit.center(a).add(0,-0.9,0));var start=player.position();
            WormholeTransit.tick(a,h.getLevel());h.assertTrue(player.position().equals(start),"Single upgrade permitted travel");
            var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());tag.putBoolean("traversable",true);b.loadWithComponents(tag,h.getLevel().registryAccess());
            h.assertTrue(a.partner()==b&&b.partner()==a,"Pair fixture not linked");
            WormholeTransit.tick(a,h.getLevel());
            h.assertTrue(player.position().distanceToSqr(Vec3.atCenterOf(b.getBlockPos()))<30&&player.isOnPortalCooldown(),"Valid passage failed or omitted cooldown");
            place(player,WormholeTransit.center(a).add(0,-0.9,0));start=player.position();WormholeTransit.tick(a,h.getLevel());
            h.assertTrue(player.position().equals(start),"Cooldown allowed immediate travel");h.succeed();
        });
    }
    /**
     * ネザーポータルと同じく、口の中の位置を保って向こうの口の同じ位置に出る。横のずれ・体の向き・勢いは口の向きの違いだけ回る。
     * 出た口の中にいる間は、もう一度は飛ばない。
     */
    @GameTest(template="forge",timeoutTicks=80)
    public static void wormholeTransitKeepsPositionFacingAndMomentum(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();
        var a=mouth(h,A,pair,true);
        // 向こうの口は東向き（こちらは南向き）: 南の前から北へ入ると、東の前へ東向きに出る
        h.setBlock(B,SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,Direction.EAST));
        WormholeMouthBlockEntity b=h.getBlockEntity(B);b.setData(new WormholeData(pair,0));
        var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());tag.putBoolean("traversable",true);tag.putInt("grace",6000);tag.putInt("size",1);b.loadWithComponents(tag,h.getLevel().registryAccess());
        // 両方の口の開口部を空け、床（口の高さ）を敷く
        for(BlockPos m:new BlockPos[]{A,B})for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            BlockPos p=m.offset(dx,0,dz);
            if(!p.equals(m))h.setBlock(p,Blocks.STONE);
            for(int y=1;y<=4;y++)h.setBlock(p.above(y),Blocks.AIR);
        }
        ServerPlayer player=TestScope.player(h);
        h.runAtTickTime(5,()->{
            h.assertTrue(a.partner()==b&&b.partner()==a,"Pair fixture not linked");
            // こちらの口の中心から東へ1ブロック、床の上に立ち、北（口の中）へ歩いている
            Vec3 start=new Vec3(WormholeTransit.center(a).x+1,h.absolutePos(A).getY()+1,WormholeTransit.center(a).z+0.2);
            place(player,start);player.setYRot(180);player.setDeltaMovement(new Vec3(0,0,-0.2));
            WormholeTransit.tick(a,h.getLevel());
            Vec3 c=WormholeTransit.center(b);
            // 南向き → 東向き（の反対の西へ入る向き）: 時計回りに1回。東へ1のずれは南へ1、北への勢いは東へ
            h.assertTrue(Math.abs(player.getX()-c.x)<1e-6&&Math.abs(player.getZ()-(c.z+1))<1e-6&&Math.abs(player.getY()-start.y)<1e-6,
                    "Arrival not mirrored: "+player.position()+" centre "+c);
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot()-270))<1e-3,"Facing not rotated: "+player.getYRot());
            h.assertTrue(player.getDeltaMovement().distanceTo(new Vec3(0.2,0,0))<1e-6,"Momentum not rotated: "+player.getDeltaMovement());
            // 出た口の中にいる間は戻らない（待ち時間が延び続ける）
            for(int i=0;i<20;i++)WormholeTransit.tick(b,h.getLevel());
            h.assertTrue(player.position().distanceToSqr(Vec3.atCenterOf(h.absolutePos(B)))<9&&player.isOnPortalCooldown(),
                    "Player bounced back while still in the exit mouth");
            h.succeed();
        });
    }
    /** 口へ撃った矢は、人と同じ写し方で向こうの口から出て飛び続ける（向きの違う口では勢いが回る）。 */
    @GameTest(template="forge",timeoutTicks=80)
    public static void wormholeCarriesArrowsToTheOtherMouth(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();
        var a=mouth(h,A,pair,true);
        h.setBlock(B,SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,Direction.EAST));
        WormholeMouthBlockEntity b=h.getBlockEntity(B);b.setData(new WormholeData(pair,0));
        var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());tag.putBoolean("traversable",true);tag.putInt("grace",6000);tag.putInt("size",1);b.loadWithComponents(tag,h.getLevel().registryAccess());
        for(BlockPos m:new BlockPos[]{A,B})for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int y=1;y<=4;y++)h.setBlock(m.offset(dx,y,dz),Blocks.AIR);
        var arrow=new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,h.getLevel());
        h.runAtTickTime(5,()->{
            h.assertTrue(a.partner()==b&&b.partner()==a,"Pair fixture not linked");
            // こちらの口の中心から東へ1、南の前2.5から北（口の中）へ飛ぶ
            Vec3 c=WormholeTransit.center(a);
            arrow.setNoGravity(true);
            arrow.setPos(c.x+1,c.y-arrow.getBbHeight()/2,c.z+2.5);arrow.setDeltaMovement(0,0,-1);
            h.getLevel().addFreshEntity(arrow);
        });
        h.succeedWhen(()->{
            Vec3 c=WormholeTransit.center(b);
            // 東向きの口: 東へ1のずれは南へ1、北への勢いは東へ
            h.assertTrue(arrow.isAlive()&&arrow.level()==h.getLevel(),"Arrow vanished");
            h.assertTrue(arrow.getX()>c.x&&arrow.getX()<c.x+4&&Math.abs(arrow.getZ()-(c.z+1))<0.05,"Arrow not at the far mouth: "+arrow.position()+" centre "+c);
            h.assertTrue(arrow.getDeltaMovement().x>0.9&&Math.abs(arrow.getDeltaMovement().z)<1e-6,"Arrow momentum not rotated: "+arrow.getDeltaMovement());
        });
    }
    /** 向こうの口が読み込まれていないとき、開口部に入った人がいれば、ネザーポータルと同じく向こうを読み込ませる。 */
    @GameTest(template="forge",timeoutTicks=400)
    public static void wormholeTransitLoadsTheUnloadedFarMouth(GameTestHelper h){
        long pair=h.getLevel().random.nextLong();var a=mouth(h,A,pair,true);
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos base=h.absolutePos(BlockPos.ZERO);
        BlockPos far=new BlockPos(Math.floorMod(base.getX(),4096)+260_000,64,Math.floorMod(base.getZ(),4096)+260_000);
        var wormholes=WormholeNetwork.get(h.getLevel().getServer());
        var at=net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.NETHER,far);
        wormholes.register(pair,at);TestScope.of(h).defer(()->wormholes.unregister(pair,at));
        ServerPlayer player=TestScope.player(h);
        h.runAtTickTime(5,()->{
            h.assertFalse(nether.isLoaded(far),"Fixture expects the far mouth to be unloaded");
            place(player,WormholeTransit.center(a).add(0,-0.9,0));
            WormholeTransit.tick(a,h.getLevel());
            h.assertTrue(player.serverLevel()==h.getLevel(),"Travelled to an unloaded mouth");
        });
        h.succeedWhen(()->h.assertTrue(nether.isLoaded(far),"The far mouth was not loaded"));
    }
    @GameTest(template="forge")
    public static void wormholeExitRejectsMissingFloorFluidAndObstruction(GameTestHelper h){
        var b=mouth(h,B,h.getLevel().random.nextLong(),true);ServerPlayer player=TestScope.player(h);place(player,Vec3.atCenterOf(h.absolutePos(A)));
        // Clear every candidate's floor/body; a void must never become an exit.
        for(int x=18;x<=22;x++)for(int z=6;z<=8;z++)for(int y=1;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        h.assertTrue(WormholeTransit.findSafeExit(b,h.getLevel(),player)==null,"Void exit accepted");floor(h);
        h.assertTrue(WormholeTransit.findSafeExit(b,h.getLevel(),player)!=null,"Safe floor rejected");
        for(int x=18;x<=22;x++)for(int z=6;z<=8;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        h.assertTrue(WormholeTransit.findSafeExit(b,h.getLevel(),player)==null,"Fluid exit accepted");
        for(int x=18;x<=22;x++)for(int z=6;z<=8;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        h.assertTrue(WormholeTransit.findSafeExit(b,h.getLevel(),player)==null,"Suffocating exit accepted");h.succeed();
    }
}
