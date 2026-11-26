package dev.srpau;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import static dev.srpau.FootPlant.*;

/** Optional gameplay renderer. Uses Mo' Bends Alpha.3's own textured split-box API,
 * but leaves SRP in control of every original model transform. Only four leaf
 * nodes are temporarily swapped during the draw; capture and armour see the
 * unmodified original hierarchy. The renderer owns this cache, not the world. */
public final class AdventurerRuntime {
    private final Limb[] limbs=new Limb[4];
    private final Map<EntityLivingBase,State> states=new WeakHashMap<>();
    private final Map<EntityLivingBase,AdventurerIdle.Blend> idleStates=new WeakHashMap<>();
    private AdventurerIdle idle;
    private final double[] edit=new double[6];
    private Rig idleRig;
    private ModelRenderer rootProxy,torsoCore,torsoCarrier;
    private PlayerWalk walk;
    private final Map<EntityLivingBase,TorsoMotion.Balance> balances=new WeakHashMap<>();
    private final Map<EntityLivingBase,AdventurerIdle.Blend> walkStates=new WeakHashMap<>();
    private final List<List<ModelRenderer>> armPaths=new ArrayList<>();
    private float[][] saved;
    private boolean poseApplied;
    private static final String[] UPPER={"0068_arm","0034_arm_1","0078_leg","0046_leg_1"};
    private static final String[] LOWER={"9000_leftElbow","9001_rightElbow","9002_rightKnee","9003_leftKnee"};
    private boolean initialized,failed;
    private Method status,flash,size;
    private static final String[] PARTS={"arm","arm_1","leg","leg_1"};
    private static final String[] PARENTS={"jointLA","jointRA","jointRL","jointLL"};
    private static final class Limb{
        final ModelRenderer original,parent,upper,lower;
        final int slot;
        final double[] knee,foot;
        Limb(ModelRenderer o,ModelRenderer p,ModelRenderer u,ModelRenderer l,int slot,double[] knee,double[] foot){
            original=o;parent=p;upper=u;lower=l;this.slot=slot;this.knee=knee;this.foot=foot;
        }
    }
    public void render(ModelBase model,ModelRenderer root,float scale,Entity entity,float swing,float amount){
        if(failed||!Loader.isModLoaded("mobends")||!(entity instanceof EntityLivingBase)){
            root.render(scale);return;
        }
        EntityLivingBase mob=(EntityLivingBase)entity;
        boolean legs=mob.getItemStackFromSlot(EntityEquipmentSlot.LEGS).isEmpty()&&mob.getItemStackFromSlot(EntityEquipmentSlot.FEET).isEmpty();
        boolean arms=mob.getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty();
        try{
            if(!initialized){initialize(model,mob);initialized=true;}
            boolean normal=normalState(mob);
            boolean stationary=normal&&stationary(mob,amount);
            float pt=MathHelper.clamp(Minecraft.getMinecraft().getRenderPartialTicks(),0F,1F);
            boolean fullIdle=idle!=null&&arms&&legs&&mob.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty();
            if(fullIdle){
                AdventurerIdle.Blend blend=idleStates.computeIfAbsent(mob,e->new AdventurerIdle.Blend());
                blend.update(mob.ticksExisted+pt,stationary,normal);
                if(blend.weight>0)applyFullPose(AdventurerIdle.phase(blend.elapsed,idle.duration),blend.smooth());
            }else idleStates.remove(mob);
            for(Limb l:limbs){
                copy(l.original,l.upper);l.lower.rotateAngleX=l.lower.rotateAngleY=l.lower.rotateAngleZ=0;
                l.lower.setRotationPoint((float)l.knee[0],(float)l.knee[1],(float)l.knee[2]);
            }
            if(fullIdle){
                AdventurerIdle.Blend blend=idleStates.get(mob);
                double t=AdventurerIdle.phase(blend.elapsed,idle.duration),weight=blend.smooth();
                for(int i=0;i<4;i++)applyAbsolute(limbs[i].lower,LOWER[i],t,weight);
            }
            copy(root,rootProxy);
            torsoCore.rotateAngleX=torsoCore.rotateAngleY=torsoCore.rotateAngleZ=0;
            boolean testWalk=walk!=null&&arms&&legs&&mob.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty();
            double now=mob.ticksExisted+pt;
            if(testWalk){
                AdventurerIdle.Blend blend=walkStates.computeIfAbsent(mob,x->new AdventurerIdle.Blend());
                blend.update(now,normal&&!stationary,normal);
                if(blend.weight>0)applyWalk(swing*.6662,amount,blend.smooth());
            }else walkStates.remove(mob);
            if(legs&&stationary&&(!fullIdle||idleStates.get(mob).weight>=1))plant(rootProxy,mob,arms);
            else{states.remove(mob);balances.remove(mob);}
        }catch(Exception|LinkageError e){
            restorePose();failed=true;states.clear();idleStates.clear();balances.clear();walkStates.clear();
            LogManager.getLogger("SRPAnimationUtil").warn("Adventurer Mo' Bends compatibility disabled; using original model",e);
            root.render(scale);return;
        }
        int swapped=0;
        // No changes to cached native display lists. Each substitute compiles its
        // own display list once. Restore even if a shader/render layer throws.
        try{
            for(int i=0;i<4;i++){
                if(i<2?!arms:!legs)continue;
                Limb l=limbs[i];
                if(l.parent.childModels.get(l.slot)!=l.original)continue;
                l.parent.childModels.set(l.slot,l.upper);swapped|=1<<i;
            }
            rootProxy.render(scale);
        }finally{
            for(int i=0;i<4;i++)if((swapped&(1<<i))!=0){
                Limb l=limbs[i];l.parent.childModels.set(l.slot,l.original);
            }
            restorePose();
        }
    }
    private boolean normalState(EntityLivingBase e)throws Exception{
        if(!e.onGround||e.isRiding()||e.isInWater()||e.isInLava()||e.deathTime>0||e.isSwingInProgress)return false;
        if(((Number)status.invoke(e)).intValue()!=0)return false;
        float pt=Minecraft.getMinecraft().getRenderPartialTicks();
        return Math.abs(((Number)size.invoke(e)).floatValue()-1F)<.01F&&Math.abs(((Number)flash.invoke(e,pt)).floatValue())<.01F;
    }
    private boolean stationary(EntityLivingBase e,float amount){
        double dx=e.posX-e.prevPosX,dz=e.posZ-e.prevPosZ;
        return amount<=.09F&&dx*dx+dz*dz<=.0004;
    }
    private static float mix(float a,double b,double w){return (float)(a+(b-a)*w);}
    private static float angle(float a,double degrees,double w){
        return a+(float)(Math.toRadians(MathHelper.wrapDegrees((float)(degrees-Math.toDegrees(a))))*w);
    }
    private void applyAbsolute(ModelRenderer p,String name,double tick,double weight){
        if(weight<=0)return;
        idle.evaluate(name,tick,edit);
        p.rotationPointX=mix(p.rotationPointX,edit[0]-16*p.offsetX,weight);
        p.rotationPointY=mix(p.rotationPointY,edit[1]-16*p.offsetY,weight);
        p.rotationPointZ=mix(p.rotationPointZ,edit[2]-16*p.offsetZ,weight);
        p.rotateAngleX=angle(p.rotateAngleX,edit[3],weight);
        p.rotateAngleY=angle(p.rotateAngleY,edit[4],weight);
        p.rotateAngleZ=angle(p.rotateAngleZ,edit[5],weight);
    }
    private void applyFullPose(double tick,double weight){
        // Snapshot all native fields before any edit; restore before armour/other entities.
        for(int i=0;i<idleRig.bones.size();i++){
            ModelRenderer p=idleRig.bones.get(i).part;float[] s=saved[i];
            s[0]=p.rotationPointX;s[1]=p.rotationPointY;s[2]=p.rotationPointZ;
            s[3]=p.rotateAngleX;s[4]=p.rotateAngleY;s[5]=p.rotateAngleZ;
        }
        poseApplied=true;
        for(Rig.Bone b:idleRig.bones)applyAbsolute(b.part,b.name,tick,weight);
    }
    private void restorePose(){
        if(!poseApplied)return;
        for(int i=0;i<idleRig.bones.size();i++){
            ModelRenderer p=idleRig.bones.get(i).part;float[] s=saved[i];
            p.setRotationPoint(s[0],s[1],s[2]);
            p.rotateAngleX=s[3];p.rotateAngleY=s[4];p.rotateAngleZ=s[5];
        }
        poseApplied=false;
    }
    private void plant(ModelRenderer root,EntityLivingBase mob,boolean moveTorso){
        float pt=MathHelper.clamp(Minecraft.getMinecraft().getRenderPartialTicks(),0F,1F);
        double yaw=Math.toRadians(mob.prevRenderYawOffset+MathHelper.wrapDegrees(mob.renderYawOffset-mob.prevRenderYawOffset)*pt);
        // Vanilla RenderLivingBase's Y(180-yaw) followed by (-1,-1,1).
        // Work in world axes with Y pointing down, and 16 model pixels per block.
        Rot world=new Rot(Math.cos(yaw),0,Math.sin(yaw),0,1,0,Math.sin(yaw),0,-Math.cos(yaw));
        // This world basis is an orthogonal reflection, which is only used for
        // transforming points. IK rotations are solved in the proper hip frame.
        double[] origin=v((mob.prevPosX+(mob.posX-mob.prevPosX)*pt)*16,-(mob.prevPosY+(mob.posY-mob.prevPosY)*pt)*16-24.016,
            (mob.prevPosZ+(mob.posZ-mob.prevPosZ)*pt)*16);
        Pose base=new Pose(world,origin);
        Pose torso=child(base,root);
        double[][] natural=new double[2][];
        Pose[] hip=new Pose[2];
        Rot[] nativeUpper=new Rot[2],authoredLower=new Rot[2];
        for(int i=0;i<2;i++){
            Limb l=limbs[i+2];
            Pose parent=child(torso,l.parent);
            hip[i]=parent.child(Rot.identity(),translation(l.upper));
            nativeUpper[i]=rotation(l.upper);authoredLower[i]=rotation(l.lower);
            natural[i]=hip[i].point(nativeUpper[i].apply(add(l.knee,authoredLower[i].apply(l.foot))));
        }
        State state=states.computeIfAbsent(mob,e->new State());
        double now=mob.ticksExisted+pt;
        double[][] targets=state.update(natural,now);
        if(moveTorso&&mob.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()){
            double[] naturalLine=sub(torso.local(natural[1]),torso.local(natural[0]));
            double[] plantedLine=sub(torso.local(targets[1]),torso.local(targets[0]));
            double lag=clamp(TorsoMotion.yaw(naturalLine,plantedLine),-Math.toRadians(45),Math.toRadians(45));
            double side=0,lift=0;
            if(state.stepping>=0){
                lift=Math.sin(Math.PI*clamp((now-state.began)/5,0,1));side=(state.stepping==0?1:-1)*lift;
            }
            TorsoMotion.Balance balance=balances.computeIfAbsent(mob,x->new TorsoMotion.Balance());
            balance.update(now,lag,side,lift);
            torsoCore.rotateAngleY+=(float)balance.yaw;torsoCore.rotateAngleZ+=(float)balance.roll;
            root.rotationPointX+=(float)balance.shift;root.rotationPointY+=(float)balance.drop;
            torso=child(base,root);
            // Recompute hip frames after pelvis translation. Preserve foot targets.
            for(int i=0;i<2;i++){
                Limb l=limbs[i+2];
                hip[i]=child(torso,l.parent).child(Rot.identity(),translation(l.upper));
                natural[i]=hip[i].point(nativeUpper[i].apply(add(l.knee,authoredLower[i].apply(l.foot))));
            }
        }
        for(int i=0;i<2;i++){
            Limb l=limbs[i+2];
            if(len(sub(natural[i],targets[i]))<.001)continue;
            Solution result=solve(nativeUpper[i],authoredLower[i],l.knee,l.foot,hip[i].local(targets[i]));
            angles(l.upper,result.upper);angles(l.lower,result.lower);
        }
    }
    private void applyWalk(double phase,double amount,double weight){
        blendRotation(torsoCore,walk.rotation("body",phase,amount),weight);
        rootProxy.rotationPointY+=(float)(walk.bob(phase)*weight);
        for(int i=0;i<4;i++){
            String name=i==0?"leftArm":i==1?"rightArm":i==2?"rightLeg":"leftLeg";
            String lower=i==0?"leftForeArm":i==1?"rightForeArm":i==2?"rightForeLeg":"leftForeLeg";
            Rot desired=walk.rotation(name,phase,amount);
            Rot parent=Rot.identity();
            if(i<2)for(ModelRenderer p:armPaths.get(i))parent=parent.mul(rotation(p));
            else parent=rotation(limbs[i].parent);
            blendRotation(limbs[i].upper,parent.inv().mul(desired),weight);
            blendRotation(limbs[i].lower,walk.rotation(lower,phase,amount),weight);
        }
    }
    private static void blendRotation(ModelRenderer p,Rot target,double w){
        double[] a=target.angles();
        p.rotateAngleX=angle(p.rotateAngleX,Math.toDegrees(a[0]),w);
        p.rotateAngleY=angle(p.rotateAngleY,Math.toDegrees(a[1]),w);
        p.rotateAngleZ=angle(p.rotateAngleZ,Math.toDegrees(a[2]),w);
    }
    private static boolean path(ModelRenderer n,ModelRenderer target,List<ModelRenderer> out){
        if(n==target)return true;
        if(n.childModels!=null)for(ModelRenderer child:n.childModels){
            out.add(child);if(path(child,target,out))return true;out.remove(out.size()-1);
        }
        return false;
    }
    private void initializeCore(ModelBase model,ModelRenderer root){
        ModelBase mesh=new ModelBase(){};
        mesh.textureWidth=model.textureWidth;mesh.textureHeight=model.textureHeight;
        rootProxy=new ModelRenderer(mesh);torsoCore=new ModelRenderer(mesh);torsoCarrier=new ModelRenderer(mesh);
        torsoCore.setRotationPoint(0,(float)AdventurerTorso.PIVOT_Y,0);
        torsoCarrier.setRotationPoint(0,(float)-AdventurerTorso.PIVOT_Y,0);
        rootProxy.addChild(torsoCore);torsoCore.addChild(torsoCarrier);
        torsoCarrier.cubeList.addAll(root.cubeList);
        for(ModelRenderer c:root.childModels){
            if(c==limbs[2].parent||c==limbs[3].parent)rootProxy.addChild(c);
            else torsoCarrier.addChild(c);
        }
        for(int i=0;i<2;i++){
            List<ModelRenderer> chain=new ArrayList<>();
            if(!path(root,limbs[i].parent,chain))throw new IllegalArgumentException("Missing shoulder chain");
            armPaths.add(chain);
        }
    }
    private static Pose child(Pose p,ModelRenderer r){return p.child(rotation(r),translation(r));}
    private static double[] translation(ModelRenderer r){return v(r.rotationPointX+16*r.offsetX,r.rotationPointY+16*r.offsetY,r.rotationPointZ+16*r.offsetZ);}
    private static Rot rotation(ModelRenderer r){return Rot.euler(r.rotateAngleX,r.rotateAngleY,r.rotateAngleZ);}
    private static void angles(ModelRenderer r,Rot q){double[] a=q.angles();r.rotateAngleX=(float)a[0];r.rotateAngleY=(float)a[1];r.rotateAngleZ=(float)a[2];}
    private static void copy(ModelRenderer a,ModelRenderer b){
        b.setRotationPoint(a.rotationPointX,a.rotationPointY,a.rotationPointZ);
        b.rotateAngleX=a.rotateAngleX;b.rotateAngleY=a.rotateAngleY;b.rotateAngleZ=a.rotateAngleZ;
        b.offsetX=a.offsetX;b.offsetY=a.offsetY;b.offsetZ=a.offsetZ;b.showModel=a.showModel;b.isHidden=a.isHidden;
    }
    private void initialize(ModelBase model,EntityLivingBase mob)throws Exception{
        status=mob.getClass().getMethod("getParasiteStatus");
        flash=mob.getClass().getMethod("getSelfeFlashIntensity",float.class);
        size=mob.getClass().getMethod("getSelfeFlashIntensity2");
        ModelRenderer root=(ModelRenderer)model.getClass().getField("mainbody").get(model);
        for(int i=0;i<4;i++){
            ModelRenderer o=(ModelRenderer)model.getClass().getField(PARTS[i]).get(model);
            ModelRenderer p=(ModelRenderer)model.getClass().getField(PARENTS[i]).get(model);
            if(i>=2&&!root.childModels.contains(p))throw new IllegalArgumentException("Unexpected hip hierarchy");
            int slot=p.childModels.indexOf(o);
            if(slot<0||o.cubeList.size()!=1||(o.childModels!=null&&!o.childModels.isEmpty()))throw new IllegalArgumentException("Unexpected limb geometry");
            ModelBox box=o.cubeList.get(0);
            if(Math.abs(box.posX2-box.posX1-4)>.01||Math.abs(box.posY2-box.posY1-12)>.01||Math.abs(box.posZ2-box.posZ1-4)>.01)
                throw new IllegalArgumentException("Expected 4x12x4 limb");
            ModelBase mesh=new ModelBase(){};
            mesh.textureWidth=model.textureWidth;mesh.textureHeight=model.textureHeight;
            ModelRenderer u=new ModelRenderer(mesh),l=new ModelRenderer(mesh);
            u.mirror=l.mirror=o.mirror;
            boolean knee=i>=2;
            double[] hinge=v(0,box.posY1+6,knee?box.posZ1:box.posZ2);
            double[] foot=v((box.posX1+box.posX2)*.5,box.posY2-hinge[1],(box.posZ1+box.posZ2)*.5-hinge[2]);
            l.setRotationPoint((float)hinge[0],(float)hinge[1],(float)hinge[2]);
            split(o,box,u,l,knee);
            u.addChild(l);
            if(!knee){
                // Connect the two cut rings in upper-limb coordinates. Geometry
                // follows the actual lower transform, including sideways bends.
                final ModelRenderer lower=l;
                final double[] h=hinge;
                final int[] tex=i==0?new int[]{40,16}:new int[]{0,26};
                u.addChild(new ModelRenderer(mesh){
                    @Override public void render(float unit){
                        Rot rot=rotation(lower);
                        JointSeam.draw(box.posX1,h[1],box.posZ1,4,4,.01,0,0,0,tex[0],tex[1]+4,
                            model.textureWidth,model.textureHeight,o.mirror,unit,
                            point->add(translation(lower),rot.apply(sub(point,h))));
                    }
                });
            }
            limbs[i]=new Limb(o,p,u,l,slot,hinge,foot);
        }
        try{
            AdventurerIdle candidate=AdventurerIdle.load();
            Rig rig=new Rig(model);
            if(!candidate.rig.equals(rig.signature))throw new IllegalArgumentException("Uploaded rig signature differs: "+rig.signature);
            for(int i=0;i<4;i++)if(!UPPER[i].isEmpty()){
                final int index=i;
                if(rig.bones.stream().noneMatch(b->b.name.equals(UPPER[index])&&b.part==limbs[index].original))
                    throw new IllegalArgumentException("Unexpected authored limb "+UPPER[i]);
            }
            for(Rig.Bone b:rig.bones)if(!candidate.has(b.name))throw new IllegalArgumentException("Missing native bone "+b.name);
            for(String name:LOWER)if(!candidate.has(name))throw new IllegalArgumentException("Missing bend "+name);
            idleRig=rig;saved=new float[rig.bones.size()][6];idle=candidate;
            LogManager.getLogger("SRPAnimationUtil").info("Adventurer authored idle loaded from ModelInfPlayer.tbl: full 94-bone absolute idle.");
        }catch(Exception|LinkageError ex){
            LogManager.getLogger("SRPAnimationUtil").warn("Authored Adventurer idle unavailable; keeping native split geometry and foot planting",ex);
        }
        initializeCore(model,root);
        try{walk=PlayerWalk.load();LogManager.getLogger("SRPAnimationUtil").info("Adventurer: installed Mo' Bends player walk clips loaded.");}
        catch(Exception|LinkageError ex){LogManager.getLogger("SRPAnimationUtil").warn("Player walk unavailable; retaining native SRP walk",ex);}
        LogManager.getLogger("SRPAnimationUtil").info("Adventurer: torso core and stationary foot planting enabled.");
    }
    private static void split(ModelRenderer original,ModelBox source,ModelRenderer upper,ModelRenderer lower,boolean knee)throws Exception{
        Class<?> factory=Class.forName("goblinbob.mobends.core.client.model.BoxFactory");
        Class<?> face=Class.forName("goblinbob.mobends.core.client.model.BoxFactory$TextureFace");
        Class<?> splitter=Class.forName("goblinbob.mobends.core.definition.BoxSplitter");
        Object nativeFactory=factory.getConstructor(ModelRenderer.class,ModelBox.class).newInstance(original,source);
        // The native BoxFactory recovery truncates floating UVs on non-power-of-two
        // textures. Recover the actual rectangles by rounding the source vertices.
        // Do not modify the SRP model or its texture.
        net.minecraft.client.model.TexturedQuad[] quads=null;
        for(java.lang.reflect.Field f:ModelBox.class.getDeclaredFields()){
            if(f.getType()==net.minecraft.client.model.TexturedQuad[].class){
                f.setAccessible(true);quads=(net.minecraft.client.model.TexturedQuad[])f.get(source);break;
            }
        }
        Object faces=factory.getField("faces").get(nativeFactory);
        float[][] uv=new float[6][4];
        String[] names={"uPos","vPos","uSize","vSize"};
        for(int i=0;i<6;i++){
            for(int j=0;j<4;j++)uv[i][j]=face.getField(names[j]).getInt(Array.get(faces,i));
            if(quads!=null&&quads.length==6){
                net.minecraft.client.model.PositionTextureVertex[] vs=quads[i].vertexPositions;
                net.minecraft.client.model.PositionTextureVertex start=vs[original.mirror?2:1],end=vs[original.mirror?0:3];
                uv[i][0]=Math.round(start.texturePositionX*original.textureWidth);
                uv[i][1]=Math.round(start.texturePositionY*original.textureHeight);
                uv[i][2]=Math.round((end.texturePositionX-start.texturePositionX)*original.textureWidth);
                uv[i][3]=Math.round((end.texturePositionY-start.texturePositionY)*original.textureHeight);
            }
        }
        Method split=splitter.getMethod("split",float[].class,float[].class,float[][].class,int.class,float[].class,int.class,int.class,boolean.class);
        List<?> segments=(List<?>)split.invoke(null,new float[]{source.posX1,source.posY1,source.posZ1},
            new float[]{source.posX2,source.posY2,source.posZ2},uv,1,new float[]{.5F},2,knee?-1:1,true);
        Constructor<?> fc=face.getConstructor(int.class,int.class,int.class,int.class);
        for(int i=0;i<2;i++){
            Object seg=segments.get(i);Class<?> type=seg.getClass();
            float[] min=(float[])type.getField("min").get(seg),max=(float[])type.getField("max").get(seg);
            float[][] strips=(float[][])type.getField("faces").get(seg);
            Object arr=Array.newInstance(face,6);
            for(int f=0;f<6;f++){
                float[] q=strips[f];
                Array.set(arr,f,fc.newInstance(Math.round(q[0]),Math.round(q[1]),Math.round(q[2]),Math.round(q[3])));
            }
            Object box=factory.getConstructor(float.class,float.class,float.class,float.class,float.class,float.class,arr.getClass())
                .newInstance(min[0],min[1],min[2],max[0],max[1],max[2],arr);
            factory.getField("faceVisibilityFlag").setByte(box,(byte)type.getField("visibility").getInt(seg));
            // Match player.json: tiny X/Z inflation on upper arms and lower legs.
            if(knee?i==1:i==0)factory.getMethod("inflate",float.class,float.class,float.class).invoke(box,.01F,0F,.01F);
            ModelRenderer part=i==0?upper:lower;
            part.cubeList.add((ModelBox)factory.getMethod("create",ModelRenderer.class).invoke(box,part));
        }
    }
}
