package dev.srpau;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Server-authoritative experimental Adventurer replacement. Vanilla navigation
 * handles obstacles; only this entity type has its AI changed. Sneaking is the
 * synchronized visual peek flag, sprinting is the synchronized run flag. */
public final class SkinwalkerAI {
    private static final String TYPE="com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfPlayer";
    @SubscribeEvent
    public void join(EntityJoinWorldEvent event){
        if(event.getWorld().isRemote||!TYPE.equals(event.getEntity().getClass().getName())||!(event.getEntity() instanceof EntityCreature))return;
        EntityCreature e=(EntityCreature)event.getEntity();
        for(EntityAITasks.EntityAITaskEntry entry:e.tasks.taskEntries)if(entry.action instanceof Goal)return;
        // Preserve swimming only; all old pursuit, wandering and targeting
        // tasks are replaced. No attacks during this first stealth test.
        for(EntityAITasks.EntityAITaskEntry entry:new ArrayList<>(e.tasks.taskEntries))
            if(!entry.action.getClass().getSimpleName().equals("EntityAISwimmingDiving"))e.tasks.removeTask(entry.action);
        for(EntityAITasks.EntityAITaskEntry entry:new ArrayList<>(e.targetTasks.taskEntries))e.targetTasks.removeTask(entry.action);
        e.setAttackTarget(null);e.setSneaking(false);e.setSprinting(false);
        e.tasks.addTask(1,new Goal(e));
    }
    static final class Goal extends EntityAIBase {
        final EntityCreature mob;
        EntityPlayer target;
        Vec3d destination;
        BlockPos cover;
        boolean peeking,fleeing;
        int nextPlan,nextTarget,peekUntil,avoidUntil;
        BlockPos lastExposedCover;
        double speed=1.8;
        Goal(EntityCreature mob){this.mob=mob;setMutexBits(3);}
        @Override public boolean shouldExecute(){return mob.isEntityAlive()&&!mob.isInWater()&&!mob.isInLava();}
        @Override public boolean shouldContinueExecuting(){return shouldExecute();}
        @Override public void resetTask(){clear();}
        private void clear(){mob.getNavigator().clearPath();mob.setSneaking(false);mob.setSprinting(false);destination=null;cover=null;peeking=false;fleeing=false;}
        private Vec3d feet(){return new Vec3d(mob.posX,mob.posY,mob.posZ);}
        private static boolean valid(EntityPlayer p){return p!=null&&p.isEntityAlive()&&!p.isSpectator()&&!p.capabilities.isCreativeMode;}
        private EntityPlayer nearest(){
            EntityPlayer best=null;double dist=48*48;
            for(EntityPlayer p:mob.world.playerEntities)if(valid(p)){
                double d=mob.getDistanceSq(p);if(d<dist){dist=d;best=p;}
            }
            return best;
        }
        private boolean clearRay(Vec3d from,Vec3d to){
            return mob.world.rayTraceBlocks(from,to,false,true,false)==null;
        }
        private boolean visible(EntityPlayer p,Vec3d point){
            Vec3d eye=p.getPositionEyes(1),delta=point.subtract(eye);
            double len=delta.lengthVector();if(len>48||len<1e-6)return len<1e-6;
            // Generous 120-degree total view cone to account for camera FOV.
            return p.getLook(1).dotProduct(delta.scale(1/len))>.5&&clearRay(eye,point);
        }
        private boolean seen(Vec3d pos){
            // No scan of unrelated mobs. Check player observers only.
            for(EntityPlayer p:mob.world.playerEntities)if(valid(p)&&p.getDistanceSq(pos.x,pos.y,pos.z)<48*48&&
                (visible(p,pos.addVector(0,1.55,0))||visible(p,pos.addVector(0,.7,0))))return true;
            return false;
        }
        private Vec3d peekHead(Vec3d p,BlockPos b){
            double x=target.posX-(b.getX()+.5),z=target.posZ-(b.getZ()+.5);
            double fx=Math.abs(x)>=Math.abs(z)?Math.signum(x):0,fz=fx==0?Math.signum(z):0;
            return p.addVector(fz*.8,1.75,-fx*.8);
        }
        private boolean headSeen(Vec3d p,BlockPos b){
            Vec3d head=peekHead(p,b);
            for(EntityPlayer observer:mob.world.playerEntities)if(valid(observer)&&visible(observer,head))return true;
            return false;
        }
        private boolean fits(Vec3d p){
            BlockPos at=new BlockPos(p);
            if(!mob.world.isBlockLoaded(at)||!mob.world.isBlockLoaded(at.up()))return false;
            AxisAlignedBB bb=mob.getEntityBoundingBox().offset(p.x-mob.posX,p.y-mob.posY,p.z-mob.posZ);
            return mob.world.getCollisionBoxes(mob,bb).isEmpty()&&
                !mob.world.getCollisionBoxes(mob,bb.offset(0,-.18,0)).isEmpty()&&!mob.world.containsAnyLiquid(bb);
        }
        private static final class Candidate{
            final Vec3d p;final BlockPos block;final double score;
            Candidate(Vec3d p,BlockPos b,double s){this.p=p;block=b;score=s;}
        }
        private boolean chooseCover(boolean escaping){
            World w=mob.world;BlockPos origin=new BlockPos(mob);
            Vec3d observer=target.getPositionEyes(1),here=feet();
            List<Candidate> choices=new ArrayList<>();
            // Bounded local search; only two-block-high full cover is used.
            for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++){
                if(dx*dx+dz*dz>36)continue;
                for(int dy=-1;dy<=1;dy++){
                    BlockPos b=origin.add(dx,dy,dz);
                    if(b.equals(lastExposedCover)&&mob.ticksExisted<avoidUntil)continue;
                    if(!w.isBlockLoaded(b)||!w.isBlockLoaded(b.up())||
                        !w.getBlockState(b).isFullCube()||!w.getBlockState(b.up()).isFullCube())continue;
                    double cx=b.getX()+.5,cz=b.getZ()+.5;
                    double x=observer.x-cx,z=observer.z-cz;
                    // Face the observer along the dominant block face; left
                    // is relative to the mob looking toward that observer.
                    double fx=Math.abs(x)>=Math.abs(z)?Math.signum(x):0;
                    double fz=fx==0?Math.signum(z):0;
                    if(fx==0&&fz==0)continue;
                    Vec3d p=new Vec3d(cx-fx*.85+fz*.5,b.getY(),cz-fz*.85-fx*.5);
                    if(!fits(p))continue;
                    // Feet/body must be concealed; the authored upper-body
                    // lean should have a sight line around the left edge.
                    if(clearRay(observer,p.addVector(0,.8,0)))continue;
                    boolean headClear=clearRay(observer,p.addVector(fz*.8,1.75,-fx*.8));
                    if(!escaping&&(!headClear||headSeen(p,b)))continue;
                    if(seen(p))continue;
                    double dd=p.squareDistanceTo(here),td=p.squareDistanceTo(new Vec3d(target.posX,target.posY,target.posZ));
                    if(td<9||dd<.05)continue;
                    choices.add(new Candidate(p,b,dd+(headClear?0:8)+(escaping?Math.max(0,64-td)*.35:Math.abs(Math.sqrt(td)-9))));
                }
            }
            choices.sort(Comparator.comparingDouble(c->c.score));
            int n=0;
            for(Candidate c:choices){
                if(n++>=3)break;
                Path path=mob.getNavigator().getPathToXYZ(c.p.x,c.p.y,c.p.z);
                if(path==null||path.getFinalPathPoint()==null)continue;
                net.minecraft.pathfinding.PathPoint end=path.getFinalPathPoint();
                if(c.p.squareDistanceTo(new Vec3d(end.x+.5,end.y,end.z+.5))>2.5)continue;
                if(mob.getNavigator().setPath(path,speed)){
                    destination=c.p;cover=c.block;return true;
                }
            }
            return false;
        }
        private void retreat(){
            Vec3d away=feet().subtract(new Vec3d(target.posX,mob.posY,target.posZ)).normalize();
            if(away.lengthVector()<.1)away=new Vec3d(1,0,0);
            for(int i=0;i<3;i++){
                double angle=(i==0?0:i==1?.65:-.65);
                Vec3d v=new Vec3d(away.x*Math.cos(angle)-away.z*Math.sin(angle),0,away.x*Math.sin(angle)+away.z*Math.cos(angle));
                Vec3d p=feet().add(v.scale(6));
                if(mob.getNavigator().tryMoveToXYZ(p.x,p.y,p.z,speed)){destination=p;cover=null;return;}
            }
        }
        private void face(){
            double dx=target.posX-mob.posX,dz=target.posZ-mob.posZ;
            float yaw=(float)(Math.atan2(dz,dx)*180/Math.PI)-90;
            mob.rotationYaw=yaw;mob.rotationYawHead=yaw;mob.renderYawOffset=yaw;
            mob.getLookHelper().setLookPositionWithEntity(target,30,30);
        }
        @Override public void updateTask(){
            int tick=mob.ticksExisted;
            if(tick>=nextTarget||!valid(target)){
                nextTarget=tick+20;EntityPlayer n=nearest();
                if(n!=target){clear();target=n;nextPlan=tick;}
            }
            if(!valid(target)){clear();return;}
            boolean exposed=seen(feet());
            if(peeking){
                boolean caught=exposed||headSeen(feet(),cover);
                if(caught||tick>=peekUntil||!mob.world.getBlockState(cover).isFullCube()){
                    if(caught){lastExposedCover=cover;avoidUntil=tick+100;}
                    clear();nextPlan=tick;fleeing=caught;exposed|=caught;
                }else{face();mob.setSneaking(true);return;}
            }
            boolean escaping=exposed||mob.getDistanceSq(target)<9;
            if(escaping&&!fleeing){mob.getNavigator().clearPath();destination=null;cover=null;nextPlan=tick;}
            fleeing=escaping;
            mob.setSprinting(escaping);
            // Player flat-ground reference speeds: ~4.317 and 5.612 blocks/s.
            // The AI move helper differs from player input, so calibrate the
            // path speed gradually from actual horizontal displacement.
            double moved=Math.hypot(mob.posX-mob.prevPosX,mob.posZ-mob.prevPosZ);
            if(!mob.getNavigator().noPath()&&mob.onGround&&moved>.035&&!mob.collidedHorizontally){
                double expected=escaping?.2806:.21585;
                speed=MathHelper.clamp(speed*(1+.04*(expected/moved-1)),.65,2.8);
                mob.getNavigator().setSpeed(speed);
            }
            if(destination!=null&&cover!=null&&(feet().squareDistanceTo(destination)<.20||
                (mob.getNavigator().noPath()&&feet().squareDistanceTo(destination)<1.44))&&fits(destination)){
                // Navigator rounds to block centres. Finish the fractional
                // half-block offset with the vanilla move helper, no teleport.
                if(feet().squareDistanceTo(destination)>.025){
                    mob.getNavigator().clearPath();
                    mob.getMoveHelper().setMoveTo(destination.x,destination.y,destination.z,.65);
                    return;
                }
                mob.getNavigator().clearPath();mob.setSprinting(false);
                if(!exposed&&!headSeen(feet(),cover)){peeking=true;peekUntil=tick+80;mob.setSneaking(true);face();return;}
                lastExposedCover=cover;avoidUntil=tick+60;
                destination=null;cover=null;
            }
            if(tick>=nextPlan&&(destination==null||mob.getNavigator().noPath()||escaping)){
                nextPlan=tick+12;
                if(!chooseCover(escaping)){
                    if(escaping)retreat();
                    else{mob.getNavigator().clearPath();destination=null;cover=null;}
                }
            }
        }
    }
}
