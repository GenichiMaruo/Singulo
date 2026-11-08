package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-only passage through a loaded, mutually upgraded pair. Never creates tickets. */
public final class WormholeTransit {
    private WormholeTransit() {}
    public static Vec3 center(WormholeMouthBlockEntity mouth){return Vec3.atCenterOf(mouth.getBlockPos()).add(0,2,0);}
    public static void tick(WormholeMouthBlockEntity mouth,ServerLevel source){
        if(!mouth.traversable()||!mouth.open())return;
        WormholeMouthBlockEntity other=mouth.partner();
        if(other==null||!other.traversable()||!other.open()||!(other.getLevel() instanceof ServerLevel target))return;
        // A stale or changed pairing must not be used for one last transfer.
        if(other.partner()!=mouth)return;
        Direction normal=mouth.getBlockState().getValue(AbstractMachineBlock.FACING);
        Vec3 c=center(mouth);
        AABB area=new AABB(c,c).inflate(normal.getAxis()==Direction.Axis.X?0.28:1.35,1.45,normal.getAxis()==Direction.Axis.Z?0.28:1.35);
        for(ServerPlayer player:source.getEntitiesOfClass(ServerPlayer.class,area,Player::isAlive)){
            if(player.isSpectator()||player.isPassenger()||player.isVehicle()||player.isSleeping()||player.isOnPortalCooldown())continue;
            Vec3 exit=findSafeExit(other,target,player);
            if(exit==null)continue;
            Direction front=other.getBlockState().getValue(AbstractMachineBlock.FACING);
            player.closeContainer();
            player.teleportTo(target,exit.x,exit.y,exit.z,front.toYRot(),player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.setPortalCooldown();
        }
    }
    public static Vec3 findSafeExit(WormholeMouthBlockEntity mouth,ServerLevel target,Player player){
        Direction front=mouth.getBlockState().getValue(AbstractMachineBlock.FACING),side=front.getClockWise();
        for(int distance:new int[]{2,3,1})for(int lateral:new int[]{0,-1,1})for(int height=1;height<=3;height++){
            BlockPos feet=mouth.getBlockPos().relative(front,distance).relative(side,lateral).above(height);
            Vec3 at=Vec3.atBottomCenterOf(feet).add(0,0.01,0);
            double half=Math.max(0.3,player.getBbWidth()/2.0),h=Math.max(1.8,player.getBbHeight());
            AABB box=new AABB(at.x-half,at.y,at.z-half,at.x+half,at.y+h,at.z+half);
            if(!safe(target,player,feet,box))continue;
            return at;
        }
        return null;
    }
    private static boolean safe(ServerLevel level,Player player,BlockPos feet,AABB box){
        if(!level.getWorldBorder().isWithinBounds(box)||box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight())return false;
        BlockPos min=BlockPos.containing(box.minX,box.minY-1,box.minZ),max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        for(BlockPos p:BlockPos.betweenClosed(min,max)){
            if(!level.isLoaded(p))return false;
            var state=level.getBlockState(p);
            if(!state.getFluidState().isEmpty()||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CACTUS)
                    ||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)
                    ||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.WITHER_ROSE))return false;
        }
        // Also support players whose dimensions were enlarged by another mod.
        // Every block under the footprint must support them, rather than just its centre.
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,feet.getY()-1,box.minZ),
                BlockPos.containing(Math.nextDown(box.maxX),feet.getY()-1,Math.nextDown(box.maxZ))))
            if(!level.getBlockState(p).isFaceSturdy(level,p,Direction.UP))return false;
        return level.noCollision(player,box)&&level.getEntities(player,box).isEmpty();
    }
}
