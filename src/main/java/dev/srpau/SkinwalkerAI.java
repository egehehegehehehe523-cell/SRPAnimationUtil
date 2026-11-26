package dev.srpau;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityArmorStand;
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
    static final String TYPE="com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfPlayer";
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
        SkinwalkerState.data(e).setBoolean("Peek",false);
        e.tasks.addTask(1,new Goal(e));
    }
    static final class Goal extends EntityAIBase {
        final EntityCreature mob;
        EntityLivingBase target;
        List<EntityArmorStand> testObservers=Collections.emptyList();
        int nextObserverScan;
        Vec3d destination;
        BlockPos cover;
        boolean peeking,fleeing;
        int nextPlan,nextTarget,avoidUntil,fleeUntil,safeTicks,nextPassiveWalk,nextPassiveLook,nextSlot,stopAt;
        Vec3d interest;
        int passiveMode;
        float coverYaw;
        final PeekLock lock=new PeekLock();
        BlockPos lastExposedCover;
        double speed=1.8;
        Goal(EntityCreature mob){this.mob=mob;setMutexBits(3);}
        @Override public boolean shouldExecute(){return mob.isEntityAlive()&&!mob.isInWater()&&!mob.isInLava();}
        @Override public boolean shouldContinueExecuting(){return shouldExecute();}
        @Override public void resetTask(){clear();}
        private void clear(){mob.getNavigator().clearPath();mob.setSneaking(false);mob.setSprinting(false);destination=null;cover=null;peeking=false;fleeing=false;lock.reset();SkinwalkerState.peek(mob,false);}
        private Vec3d feet(){return new Vec3d(mob.posX,mob.posY,mob.posZ);}
        private boolean valid(EntityLivingBase p){
            if(p==null||!p.isEntityAlive()||p.world!=mob.world)return false;
            if(p instanceof EntityPlayer){
                EntityPlayer player=(EntityPlayer)p;
                return !player.isSpectator()&&!player.capabilities.isCreativeMode;
            }
            return p instanceof EntityArmorStand;
        }
        private void refreshObservers(int tick){
            if(tick<nextObserverScan)return;
            nextObserverScan=tick+20;
            // One bounded entity query per second, never per cover candidate.
            testObservers=mob.world.getEntitiesWithinAABB(EntityArmorStand.class,
                mob.getEntityBoundingBox().grow(48));
        }
        private EntityLivingBase nearest(){
            EntityLivingBase best=null;double dist=48*48;
            for(EntityPlayer p:mob.world.playerEntities)if(valid(p)){
                double d=mob.getDistanceSq(p);if(d<dist){dist=d;best=p;}
            }
            for(EntityArmorStand p:testObservers)if(valid(p)){
                double d=mob.getDistanceSq(p);if(d<dist){dist=d;best=p;}
            }
            return best;
        }
        private Vec3d gaze(EntityLivingBase p){
            if(!(p instanceof EntityArmorStand))return p.getLook(1);
            // Stand poses rotate the skull relative to its base, not entity look.
            Rotations head=((EntityArmorStand)p).getHeadRotation();
            double yaw=Math.toRadians(p.rotationYaw+head.getY());
            double pitch=Math.toRadians(head.getX());
            double cp=Math.cos(pitch);
            return new Vec3d(-Math.sin(yaw)*cp,-Math.sin(pitch),Math.cos(yaw)*cp);
        }
        private boolean clearRay(Vec3d from,Vec3d to){
            return mob.world.rayTraceBlocks(from,to,false,true,false)==null;
        }
        private boolean visible(EntityLivingBase p,Vec3d point){
            Vec3d eye=p.getPositionEyes(1),delta=point.subtract(eye);
            double len=Math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z);if(len>48||len<1e-6)return len<1e-6;
            // Generous 120-degree total view cone to account for camera FOV.
            return gaze(p).dotProduct(delta.scale(1/len))>.5&&clearRay(eye,point);
        }
        private boolean seen(Vec3d pos){
            // Check players and cached armour-stand test observers only.
            for(EntityPlayer p:mob.world.playerEntities)if(valid(p)&&p.getDistanceSq(pos.x,pos.y,pos.z)<48*48&&
                (visible(p,pos.add(0,1.55,0))||visible(p,pos.add(0,.7,0))))return true;
            for(EntityArmorStand p:testObservers)if(valid(p)&&p.getDistanceSq(pos.x,pos.y,pos.z)<48*48&&
                (visible(p,pos.add(0,1.55,0))||visible(p,pos.add(0,.7,0))))return true;
            return false;
        }
        private Vec3d peekHead(Vec3d p,float yaw){
            double[] h=PeekLock.head(p.x,p.y,p.z,yaw);
            return new Vec3d(h[0],h[1],h[2]);
        }
        private boolean headSeen(Vec3d p,float yaw){
            Vec3d head=peekHead(p,yaw);
            for(EntityPlayer observer:mob.world.playerEntities)if(valid(observer)&&visible(observer,head))return true;
            for(EntityArmorStand observer:testObservers)if(valid(observer)&&visible(observer,head))return true;
            return false;
        }
        private boolean anchoredCover(Vec3d observer,Vec3d p,BlockPos b,float yaw){
            if(b==null||!mob.world.isBlockLoaded(b)||!mob.world.isBlockLoaded(b.up())||
                !mob.world.getBlockState(b).isFullCube()||!mob.world.getBlockState(b.up()).isFullCube())return false;
            if(!PeekLock.behindFace(b.getX()+.5,b.getZ()+.5,observer.x,observer.z,yaw))return false;
            // A different obstruction must not validate an unrelated pillar.
            RayTraceResult hit=mob.world.rayTraceBlocks(observer,p.add(0,.85,0),false,true,false);
            if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK)return false;
            BlockPos h=hit.getBlockPos();
            return b.equals(h)||b.up().equals(h);
        }
        private boolean fits(Vec3d p){
            BlockPos at=new BlockPos(p);
            if(!mob.world.isBlockLoaded(at)||!mob.world.isBlockLoaded(at.up()))return false;
            AxisAlignedBB bb=mob.getEntityBoundingBox().offset(p.x-mob.posX,p.y-mob.posY,p.z-mob.posZ);
            return mob.world.getCollisionBoxes(mob,bb).isEmpty()&&
                !mob.world.getCollisionBoxes(mob,bb.offset(0,-.18,0)).isEmpty()&&!mob.world.containsAnyLiquid(bb);
        }
        private static final class Candidate{
            final Vec3d p;final BlockPos block;final double score;final float yaw;
            Candidate(Vec3d p,BlockPos b,double s,float yaw){this.p=p;block=b;score=s;this.yaw=yaw;}
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
                    double[] anchor=PeekLock.anchor(cx,b.getY(),cz,fx,fz);
                    Vec3d p=new Vec3d(anchor[0],anchor[1],anchor[2]);
                    float yaw=PeekLock.yaw(fx,fz);
                    if(!fits(p))continue;
                    // Feet/body must be concealed; the authored upper-body
                    // lean should have a sight line around the left edge.
                    if(!anchoredCover(observer,p,b,yaw))continue;
                    boolean headClear=clearRay(observer,peekHead(p,yaw));
                    if(!escaping&&!headClear)continue;
                    if(seen(p))continue;
                    double dd=p.squareDistanceTo(here),td=p.squareDistanceTo(new Vec3d(target.posX,target.posY,target.posZ));
                    if(td<9||dd<.05)continue;
                    choices.add(new Candidate(p,b,dd+(headClear?0:8)+(escaping?Math.max(0,64-td)*.35:Math.abs(Math.sqrt(td)-9))+(headSeen(p,yaw)?3:0),yaw));
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
                    destination=c.p;cover=c.block;coverYaw=c.yaw;return true;
                }
            }
            return false;
        }
        private void retreat(){
            Vec3d away=feet().subtract(new Vec3d(target.posX,mob.posY,target.posZ)).normalize();
            if(away.x*away.x+away.z*away.z<.01)away=new Vec3d(1,0,0);
            for(int i=0;i<3;i++){
                double angle=(i==0?0:i==1?.65:-.65);
                Vec3d v=new Vec3d(away.x*Math.cos(angle)-away.z*Math.sin(angle),0,away.x*Math.sin(angle)+away.z*Math.cos(angle));
                Vec3d p=feet().add(v.scale(6));
                if(mob.getNavigator().tryMoveToXYZ(p.x,p.y,p.z,speed)){destination=p;cover=null;return;}
            }
        }
        private void holdPeek(){
            // Lock to the chosen world-space anchor and block face, not the
            // changing player angle. Only correct small residual drift.
            mob.getNavigator().clearPath();
            mob.getMoveHelper().setMoveTo(destination.x,destination.y,destination.z,0);
            mob.setAIMoveSpeed(0);
            mob.moveForward=0;mob.moveStrafing=0;
            mob.motionX=0;mob.motionZ=0;
            mob.setPosition(destination.x,destination.y,destination.z);
            mob.rotationYaw=coverYaw;mob.renderYawOffset=coverYaw;
            mob.getLookHelper().setLookPositionWithEntity(target,30,30);
            mob.setSprinting(false);mob.setSneaking(true);SkinwalkerState.peek(mob,true,coverYaw);
        }
        // Short, purposeful action bouts rather than a continuous mob wander.
        // All item changes are visual only; no interaction or inventory on mob.
        private void passive(int tick){
            if(mob.getAttackTarget()!=null)mob.setAttackTarget(null);
            java.util.Random rand=mob.getRNG();
            if(nextSlot==0)nextSlot=tick+40+rand.nextInt(140);
            if(tick>=nextSlot){
                nextSlot=tick+40+rand.nextInt(140);
                net.minecraft.nbt.NBTTagList bar=SkinwalkerState.data(mob).getCompoundTag("Identity").getTagList("Hotbar",10);
                // Prefer a nonempty slot, occasionally choose an empty hand.
                int slot=rand.nextInt(9);
                if(bar.tagCount()>0&&rand.nextInt(5)!=0)slot=bar.getCompoundTagAt(rand.nextInt(bar.tagCount())).getInteger("Slot");
                SkinwalkerState.select(mob,slot);
            }
            if(tick>=stopAt&&passiveMode!=0){
                mob.getNavigator().clearPath();passiveMode=0;mob.setSprinting(false);mob.setSneaking(false);
                nextPassiveWalk=tick+20+rand.nextInt(60);
            }
            if(tick>=nextPassiveWalk&&passiveMode==0){
                nextPassiveWalk=tick+20+rand.nextInt(65);
                int choice=rand.nextInt(10);
                if(choice<7){
                    Vec3d point=RandomPositionGenerator.findRandomTarget(mob,choice==0?12:8,2);
                    if(point!=null&&mob.getNavigator().tryMoveToXYZ(point.x,point.y,point.z,speed)){
                        passiveMode=choice==0?2:1;stopAt=tick+50+rand.nextInt(70);
                        interest=point.add(0,1.6,0);
                    }
                }else if(choice==7){
                    passiveMode=3;stopAt=tick+15+rand.nextInt(25); // brief crouch
                    interest=feet().add(rand.nextDouble()*4-2,0,rand.nextDouble()*4-2);
                }else{
                    // Stop to inspect terrain or a nearby entity, without attacking.
                    EntityLivingBase observer=nearest();
                    interest=observer!=null&&mob.getDistanceSq(observer)<100&&rand.nextBoolean()
                        ?observer.getPositionEyes(1):feet().add(rand.nextDouble()*12-6,rand.nextDouble()*3,rand.nextDouble()*12-6);
                }
            }
            if((passiveMode==1||passiveMode==2)&&mob.getNavigator().noPath())stopAt=tick;
            mob.setSprinting(passiveMode==2);mob.setSneaking(passiveMode==3);
            if(passiveMode==1||passiveMode==2){
                double moved=Math.hypot(mob.posX-mob.prevPosX,mob.posZ-mob.prevPosZ);
                if(mob.onGround&&moved>.035&&!mob.collidedHorizontally){
                    double expected=passiveMode==2?.2806:.21585;
                    speed=MathHelper.clamp(speed*(1+.04*(expected/moved-1)),.65,2.8);
                    mob.getNavigator().setSpeed(speed);
                }
            }
            if(tick>=nextPassiveLook){
                nextPassiveLook=tick+20+rand.nextInt(45);
                if(passiveMode==0&&rand.nextBoolean())
                    interest=feet().add(rand.nextDouble()*12-6,rand.nextDouble()*3,rand.nextDouble()*12-6);
            }
            if(interest!=null)mob.getLookHelper().setLookPosition(interest.x,interest.y,interest.z,12,10);
        }
        @Override public void updateTask(){
            int tick=mob.ticksExisted;
            refreshObservers(tick);
            if(SkinwalkerState.disguised(mob)){passive(tick);return;}
            if(SkinwalkerState.data(mob).getBoolean("JustRevealed")){
                SkinwalkerState.data(mob).setBoolean("JustRevealed",false);
                clear();passiveMode=0;interest=null;safeTicks=0;nextPlan=tick;fleeUntil=tick+40;
            }
            // A learned identity is permanent. Wait for a genuinely unseen,
            // grounded opportunity after ending the peek, even if target left.
            if(SkinwalkerState.ready(mob)&&!peeking){
                safeTicks=SeenProgress.safe(safeTicks,mob.onGround&&!seen(feet()));
                if(safeTicks>=SeenProgress.SAFE_TICKS){
                    SkinwalkerState.refreshIdentity(mob,target);
                    clear();SkinwalkerState.disguise(mob);passive(tick);return;
                }
            }else safeTicks=0;
            if((!peeking&&tick>=nextTarget)||!valid(target)){
                nextTarget=tick+20;EntityLivingBase n=nearest();
                if(n!=target){clear();target=n;nextPlan=tick;}
            }
            if(!valid(target)){clear();return;}
            boolean exposed=seen(feet());
            if(peeking){
                boolean caught=exposed||headSeen(destination,coverYaw);
                lock.detect(tick,caught);
                boolean invalid=!anchoredCover(target.getPositionEyes(1),destination,cover,coverYaw)||
                    feet().squareDistanceTo(destination)>.36||!fits(destination);
                boolean detected=lock.retreat(tick);
                boolean observing=!invalid&&clearRay(peekHead(destination,coverYaw),target.getPositionEyes(1));
                if(observing&&!detected)SkinwalkerState.observe(mob,target);
                boolean learned=SkinwalkerState.ready(mob);
                if(invalid||detected||(!lock.detected()&&(!observing||learned))){
                    if(lock.detected()||invalid){lastExposedCover=cover;avoidUntil=tick+100;fleeUntil=tick+30;}
                    clear();nextPlan=tick;
                }else{holdPeek();return;}
            }
            boolean escaping=exposed||tick<fleeUntil||mob.getDistanceSq(target)<9;
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
                // Visible head is allowed; detection starts the reaction
                // timer, not an immediate cancellation of the authored pose.
                if(!SkinwalkerState.ready(mob)&&!escaping&&!exposed&&anchoredCover(target.getPositionEyes(1),destination,cover,coverYaw)&&clearRay(target.getPositionEyes(1),peekHead(destination,coverYaw))){
                    peeking=true;lock.reset();
                    lock.detect(tick,headSeen(destination,coverYaw));holdPeek();return;
                }
                if(!escaping&&!exposed){destination=null;cover=null;nextPlan=tick+2;return;}
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
