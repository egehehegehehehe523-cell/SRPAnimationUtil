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
    public void render(ModelBase model,ModelRenderer root,float scale,Entity entity,float amount){
        if(failed||!Loader.isModLoaded("mobends")||!(entity instanceof EntityLivingBase)){
            root.render(scale);return;
        }
        EntityLivingBase mob=(EntityLivingBase)entity;
        boolean legs=mob.getItemStackFromSlot(EntityEquipmentSlot.LEGS).isEmpty()&&mob.getItemStackFromSlot(EntityEquipmentSlot.FEET).isEmpty();
        boolean arms=mob.getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty();
        try{
            if(!initialized){initialize(model,mob);initialized=true;}
            for(Limb l:limbs){
                copy(l.original,l.upper);l.lower.rotateAngleX=l.lower.rotateAngleY=l.lower.rotateAngleZ=0;
            }
            if(legs&&canPlant(mob,amount))plant(root,mob);
            else states.remove(mob);
        }catch(Exception|LinkageError e){
            failed=true;states.clear();
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
            root.render(scale);
        }finally{
            for(int i=0;i<4;i++)if((swapped&(1<<i))!=0){
                Limb l=limbs[i];l.parent.childModels.set(l.slot,l.original);
            }
        }
    }
    private boolean canPlant(EntityLivingBase e,float amount)throws Exception{
        if(!e.onGround||e.isRiding()||e.isInWater()||e.isInLava()||e.deathTime>0||amount>.09F)return false;
        double dx=e.posX-e.prevPosX,dz=e.posZ-e.prevPosZ;
        if(dx*dx+dz*dz>.0004)return false;
        if(((Number)status.invoke(e)).intValue()!=0)return false;
        float pt=Minecraft.getMinecraft().getRenderPartialTicks();
        return Math.abs(((Number)size.invoke(e)).floatValue()-1F)<.01F&&Math.abs(((Number)flash.invoke(e,pt)).floatValue())<.01F;
    }
    private void plant(ModelRenderer root,EntityLivingBase mob){
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
        Rot[] nativeUpper=new Rot[2];
        for(int i=0;i<2;i++){
            Limb l=limbs[i+2];
            Pose parent=child(torso,l.parent);
            hip[i]=parent.child(Rot.identity(),translation(l.original));
            nativeUpper[i]=rotation(l.original);
            natural[i]=hip[i].point(nativeUpper[i].apply(add(l.knee,l.foot)));
        }
        State state=states.computeIfAbsent(mob,e->new State());
        double[][] targets=state.update(natural,mob.ticksExisted+pt);
        for(int i=0;i<2;i++){
            Limb l=limbs[i+2];
            Solution result=solve(nativeUpper[i],l.knee,l.foot,hip[i].local(targets[i]));
            angles(l.upper,result.upper);angles(l.lower,result.lower);
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
            u.addChild(l);limbs[i]=new Limb(o,p,u,l,slot,hinge,foot);
        }
        LogManager.getLogger("SRPAnimationUtil").info("Adventurer: native Mo' Bends split geometry and stationary foot planting enabled.");
    }
    private static void split(ModelRenderer original,ModelBox source,ModelRenderer upper,ModelRenderer lower,boolean knee)throws Exception{
        Class<?> factory=Class.forName("goblinbob.mobends.core.client.model.BoxFactory");
        Class<?> face=Class.forName("goblinbob.mobends.core.client.model.BoxFactory$TextureFace");
        Class<?> splitter=Class.forName("goblinbob.mobends.core.definition.BoxSplitter");
        Object nativeFactory=factory.getConstructor(ModelRenderer.class,ModelBox.class).newInstance(original,source);
        Object faces=factory.getField("faces").get(nativeFactory);
        float[][] uv=new float[6][4];
        String[] names={"uPos","vPos","uSize","vSize"};
        for(int i=0;i<6;i++)for(int j=0;j<4;j++)uv[i][j]=face.getField(names[j]).getInt(Array.get(faces,i));
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
                float[] q=(f==2||f==3)?uv[f]:strips[f]; // same cap pixels as editor atlas
                Array.set(arr,f,fc.newInstance(Math.round(q[0]),Math.round(q[1]),Math.round(q[2]),Math.round(q[3])));
            }
            Object box=factory.getConstructor(float.class,float.class,float.class,float.class,float.class,float.class,arr.getClass())
                .newInstance(min[0],min[1],min[2],max[0],max[1],max[2],arr);
            factory.getField("faceVisibilityFlag").setByte(box,(byte)63);
            ModelRenderer part=i==0?upper:lower;
            part.cubeList.add((ModelBox)factory.getMethod("create",ModelRenderer.class).invoke(box,part));
        }
    }
}
