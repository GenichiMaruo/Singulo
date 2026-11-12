package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 縫い開いた口の通行（サーバーだけ。チャンクを読み込ませない）。ネザーポータルと同じく、入った口の中の位置を保ったまま向こうの口の
 * 同じ位置に出る。待ち時間はない。
 * <ul>
 *   <li>出る位置: 開口部の面からの横と高さのずれを、向こうの口の向きに回して写す（面に垂直なずれは捨てて、開口部の面の上に出す）。</li>
 *   <li>向きと勢い: 入った口から出る口への回転で、体の向きと動きの勢いを回す。前から入れば向こうの前へ、後ろから入れば向こうの後ろへ出る。</li>
 *   <li>出た口の中にいる間は、もう一度は飛ばない（待ち時間を口の中にいる間ずっと延ばす）。いったん出てから入り直すと通る。</li>
 *   <li>写した位置がふさがっている・危ない・足の下に床がない（4ブロック以内）ときは、向こうの口の前の安全な足場に出す。それもなければ通さない。</li>
 * </ul>
 */
public final class WormholeTransit {
    private WormholeTransit() {}
    /** 足の下に床を探す深さ（これより深く落ちる出口には出さない）。 */
    static final int FLOOR_DEPTH = 4;

    public static Vec3 center(WormholeMouthBlockEntity mouth){return Vec3.atCenterOf(mouth.getBlockPos()).add(0,2,0);}

    /** 口の開口部（通行の判定に使う箱）。 */
    public static AABB opening(WormholeMouthBlockEntity mouth){
        Direction normal=facing(mouth);
        Vec3 c=center(mouth);
        return new AABB(c,c).inflate(normal.getAxis()==Direction.Axis.X?0.28:1.35,1.45,normal.getAxis()==Direction.Axis.Z?0.28:1.35);
    }

    /** 出る場所: 足の位置・体の向き（度）・動きの勢い。 */
    public record Arrival(Vec3 pos,float yRot,Vec3 motion){}

    public static void tick(WormholeMouthBlockEntity mouth,ServerLevel source){
        if(!mouth.traversable()||mouth.size()<=0)return;
        WormholeMouthBlockEntity other=mouth.partner();
        if(other==null){
            // 向こうの口が読み込まれていない: 開口部に人がいれば、ネザーポータルと同じく向こうを読み込ませる（読み込めたら通す）
            var far=mouth.partnerPos();
            ServerLevel farLevel=far==null?null:source.getServer().getLevel(far.dimension());
            if(farLevel!=null&&!source.getEntitiesOfClass(ServerPlayer.class,opening(mouth),p->p.isAlive()&&!p.isSpectator()).isEmpty())
                io.github.genichimaruo.singulo.drone.DroneWormhole.wake(farLevel,far.pos());
            return;
        }
        if(other==null||!other.traversable()||!other.open()||!(other.getLevel() instanceof ServerLevel target))return;
        // A stale or changed pairing must not be used for one last transfer.
        if(other.partner()!=mouth)return;
        for(ServerPlayer player:source.getEntitiesOfClass(ServerPlayer.class,opening(mouth),Player::isAlive)){
            if(player.isSpectator()||player.isPassenger()||player.isVehicle()||player.isSleeping())continue;
            if(player.isOnPortalCooldown()){
                // 出てきたばかりの口の中: 出るまで待ち時間を延ばし続ける（ネザーポータルと同じ）
                player.setPortalCooldown();
                continue;
            }
            Arrival a=arrival(mouth,other,target,player);
            if(a==null)continue;
            player.closeContainer();
            player.teleportTo(target,a.pos().x,a.pos().y,a.pos().z,a.yRot(),player.getXRot());
            player.setDeltaMovement(a.motion());
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.fallDistance=0;player.setPortalCooldown();
        }
    }

    /** from の口から入った player が、to の口のどこへ出るか（出せなければ null）。 */
    @Nullable
    public static Arrival arrival(WormholeMouthBlockEntity from,WormholeMouthBlockEntity to,ServerLevel target,Player player){
        Direction a=facing(from),b=facing(to);
        int turns=quarterTurns(a,b.getOpposite());
        Vec3 rel=player.position().subtract(center(from));
        double across=rel.x*a.getStepX()+rel.z*a.getStepZ();
        rel=rel.subtract(a.getStepX()*across,0,a.getStepZ()*across);
        Vec3 at=center(to).add(rotate(rel,turns));
        if(clear(target,player,at,true))
            return new Arrival(at,player.getYRot()+90*turns,rotate(player.getDeltaMovement(),turns));
        Vec3 exit=findSafeExit(to,target,player);
        return exit==null?null:new Arrival(exit,b.toYRot(),Vec3.ZERO);
    }

    /**
     * 口をくぐる飛び道具（矢・トライデント・投げた物・火の玉、ほかの MOD の弾も {@link Projectile} なら同じ）。動く前に、この tick の
     * 動きが縫い開いた口の開口部の面を横切るなら、向こうの口の同じ位置へ、向きと勢いを回して移す（人と同じ写し方。待ち時間はない）。
     * 開口部まで何かに当たるときは移さない。向こうの口が読み込まれていなければ、ネザーポータルと同じくその場で読み込ませる。
     */
    public static boolean onProjectileTick(net.minecraft.world.entity.Entity entity){
        if(!(entity instanceof Projectile shot)||!(shot.level() instanceof ServerLevel level)||shot.isRemoved()||shot.isPassenger())return false;
        Vec3 v=shot.getDeltaMovement();
        if(v.lengthSqr()<1.0E-6)return false;
        var mouths=WormholeMouthBlockEntity.loadedView(level);
        if(mouths.isEmpty())return false;
        Vec3 from=shot.position().add(0,shot.getBbHeight()/2,0),to=from.add(v);
        double reach=v.length()+3;
        WormholeMouthBlockEntity hit=null;double best=2;
        for(WormholeMouthBlockEntity m:mouths){
            if(m.isRemoved()||!m.traversable()||m.size()<=0||center(m).distanceToSqr(from)>reach*reach)continue;
            double t=crossing(m,from,to);
            if(t>=0&&t<best){best=t;hit=m;}
        }
        if(hit==null)return false;
        Vec3 at=from.add(v.scale(best));
        if(level.clip(new ClipContext(from,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shot)).getType()!=HitResult.Type.MISS)return false;
        Vec3 beforePortal=v.scale(best);
        if(net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,shot,shot.position(),shot.position().add(beforePortal),
                shot.getBoundingBox().expandTowards(beforePortal).inflate(1),
                ((io.github.genichimaruo.singulo.mixin.ProjectileAccessor)shot)::singulo$canHitEntity)!=null)return false;
        WormholeMouthBlockEntity other=hit.partner();
        if(other==null){
            var far=hit.partnerPos();
            ServerLevel farLevel=far==null?null:level.getServer().getLevel(far.dimension());
            if(farLevel==null)return false;
            io.github.genichimaruo.singulo.drone.DroneWormhole.wake(farLevel,far.pos());
            farLevel.getChunk(far.pos());
            other=hit.partner();
        }
        if(other==null||!other.traversable()||!other.open()||other.partner()!=hit||!(other.getLevel() instanceof ServerLevel target))return false;
        Direction a=facing(hit);
        int turns=quarterTurns(a,facing(other).getOpposite());
        Vec3 rel=at.subtract(center(hit));
        double across=rel.x*a.getStepX()+rel.z*a.getStepZ();
        rel=rel.subtract(a.getStepX()*across,0,a.getStepZ()*across);
        Vec3 motion=rotate(v,turns);
        // 向こうの開口部の面の少し先（出ていく向き）に、体の中ほどを合わせて置く
        Vec3 out=center(other).add(rotate(rel,turns)).add(motion.normalize().scale(0.05)).subtract(0,shot.getBbHeight()/2,0);
        float yRot=(float)(Mth.atan2(motion.x,motion.z)*(180F/Math.PI));
        if (target == level) {
            shot.moveTo(out.x, out.y, out.z, yRot, shot.getXRot());
            shot.setDeltaMovement(motion);
        } else {
            var moved = shot.changeDimension(target, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public net.minecraft.world.level.portal.PortalInfo getPortalInfo(net.minecraft.world.entity.Entity entity,
                        ServerLevel destination, java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> fallback) {
                    return new net.minecraft.world.level.portal.PortalInfo(out, motion, yRot, shot.getXRot());
                }
                @Override
                public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity entity, ServerLevel current,
                        ServerLevel destination, float yaw, java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> reposition) {
                    return reposition.apply(false);
                }
            });
            if (moved == null) return false;
        }
        io.github.genichimaruo.singulo.drone.DroneWormhole.wake(target, BlockPos.containing(out));
        return true;
    }

    /** from から to への動きが口の開口部の面を横切るなら、横切る割合（0〜1）。横切らなければ -1。 */
    static double crossing(WormholeMouthBlockEntity mouth,Vec3 from,Vec3 to){
        Direction n=facing(mouth);
        Vec3 c=center(mouth);
        double s0=(from.x-c.x)*n.getStepX()+(from.z-c.z)*n.getStepZ();
        double s1=(to.x-c.x)*n.getStepX()+(to.z-c.z)*n.getStepZ();
        if(s0==0||(s0>0)==(s1>0)&&s1!=0)return -1;
        double t=s0/(s0-s1);
        Vec3 q=from.add(to.subtract(from).scale(t)).subtract(c);
        Direction side=n.getClockWise();
        double lateral=q.x*side.getStepX()+q.z*side.getStepZ();
        return Math.abs(lateral)<=1.35&&Math.abs(q.y)<=1.45?t:-1;
    }

    private static Direction facing(WormholeMouthBlockEntity mouth){
        return mouth.getBlockState().getValue(AbstractMachineBlock.FACING);
    }

    /** a を時計回り（上から見て）に何回 90° 回すと b になるか。 */
    static int quarterTurns(Direction a,Direction b){
        int n=0;
        for(Direction d=a;d!=b&&n<4;d=d.getClockWise())n++;
        return n%4;
    }

    /** 上から見て時計回りに turns × 90° 回す（北 → 東）。 */
    static Vec3 rotate(Vec3 v,int turns){
        for(int i=0;i<turns;i++)v=new Vec3(-v.z,v.y,v.x);
        return v;
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

    /** 写した位置に出られるか: 世界の中で読み込まれていて、ふさがっておらず、危ないものがなく、足の下 FLOOR_DEPTH 以内に床がある。 */
    private static boolean clear(ServerLevel level,Player player,Vec3 at,boolean needFloor){
        double half=player.getBbWidth()/2.0;
        AABB box=new AABB(at.x-half,at.y,at.z-half,at.x+half,at.y+player.getBbHeight(),at.z+half);
        if(!level.getWorldBorder().isWithinBounds(box)||box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight())return false;
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            if(!level.isLoaded(p)||hazard(level.getBlockState(p))||level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA))return false;
        }
        if(!level.noCollision(player,box))return false;
        if(!needFloor)return true;
        BlockPos feet=BlockPos.containing(at.x,at.y,at.z);
        for(int d=0;d<=FLOOR_DEPTH;d++){
            BlockPos p=feet.below(d);
            if(!level.isLoaded(p))return false;
            var state=level.getBlockState(p);
            if(hazard(state)||level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA))return false;
            if(!state.getCollisionShape(level,p).isEmpty()||!level.getFluidState(p).isEmpty())return true;
        }
        return false;
    }

    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state){
        return state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CACTUS)||state.is(Blocks.MAGMA_BLOCK)
                ||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.SWEET_BERRY_BUSH)
                ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.WITHER_ROSE)||state.is(Blocks.LAVA);
    }

    private static boolean safe(ServerLevel level,Player player,BlockPos feet,AABB box){
        if(!level.getWorldBorder().isWithinBounds(box)||box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight())return false;
        BlockPos min=BlockPos.containing(box.minX,box.minY-1,box.minZ),max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        for(BlockPos p:BlockPos.betweenClosed(min,max)){
            if(!level.isLoaded(p))return false;
            var state=level.getBlockState(p);
            if(!state.getFluidState().isEmpty()||hazard(state))return false;
        }
        // Also support players whose dimensions were enlarged by another mod.
        // Every block under the footprint must support them, rather than just its centre.
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,feet.getY()-1,box.minZ),
                BlockPos.containing(Math.nextDown(box.maxX),feet.getY()-1,Math.nextDown(box.maxZ))))
            if(!level.getBlockState(p).isFaceSturdy(level,p,Direction.UP))return false;
        return level.noCollision(player,box)&&level.getEntities(player,box).isEmpty();
    }
}
