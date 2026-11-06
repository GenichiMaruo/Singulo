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
