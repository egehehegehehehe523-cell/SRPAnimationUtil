package dev.srpau;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;

/** Exact tick-zero rotations extracted from ModelInfPlayer(1).tbl.
 * Upper-body values are authored deltas; legs are absolute bind + delta.
 * No playback of unintended Return 0 ramps. Native fields restored per draw. */
final class PeekPose {
    private final ModelRenderer[] parts;
    private final float[][] saved;
    private boolean applied;
    private static final double[][] DEGREES={
        {0,0,12.485330505588804}, // left elbow
        {36.62653502055821,0,0}, // left knee (absolute)
        {0,16.475407340069143,0}, // hair_jointM1
        {-9.616288964319459,0,13.507574916214853}, // arm
        {-6.686672934072007,-28.591590098083998,18.654052857030074}, // head
        {-35.60073952238477,0,3.7090905801305323}, // leg absolute
        {25.50474752259804,0,0}, // right knee
        {-29.62804554206051,-31.176585600025685,-4.481563244937147}, // leg_1 absolute
        {1.506260367842297,-11.473356121207797,21.033261564676177}, // torsoCore
        {34.413660114655116,0,0}, // right elbow
        {74.2052840744216,165.69096243566236,80.84863362533716} // arm_1
    };
    PeekPose(ModelBase model,ModelRenderer core,ModelRenderer la,ModelRenderer ra,ModelRenderer rl,ModelRenderer ll,
             ModelRenderer le,ModelRenderer re,ModelRenderer rk,ModelRenderer lk)throws Exception{
        parts=new ModelRenderer[]{le,lk,field(model,"hair_jointM1"),la,field(model,"head"),rl,rk,ll,core,re,ra};
        saved=new float[parts.length][3];
    }
    private static ModelRenderer field(ModelBase model,String name)throws Exception{return (ModelRenderer)model.getClass().getField(name).get(model);}
    private static boolean replace(int i){return i==1||i==5||i==6||i==7;}
    private static float value(float old,double deg,double weight,boolean absolute){
        if(!absolute)return old+(float)Math.toRadians(deg*weight);
        return old+(float)(Math.toRadians(MathHelper.wrapDegrees((float)(deg-Math.toDegrees(old))))*weight);
    }
    void apply(double weight){
        for(int i=0;i<parts.length;i++){
            ModelRenderer p=parts[i];saved[i][0]=p.rotateAngleX;saved[i][1]=p.rotateAngleY;saved[i][2]=p.rotateAngleZ;
        }
        applied=true;
        for(int i=0;i<parts.length;i++){
            ModelRenderer p=parts[i];double[] v=DEGREES[i];
            p.rotateAngleX=value(p.rotateAngleX,v[0],weight,replace(i));
            p.rotateAngleY=value(p.rotateAngleY,v[1],weight,replace(i));
            p.rotateAngleZ=value(p.rotateAngleZ,v[2],weight,replace(i));
        }
    }
    void restore(){
        if(!applied)return;
        for(int i=0;i<parts.length;i++){
            parts[i].rotateAngleX=saved[i][0];parts[i].rotateAngleY=saved[i][1];parts[i].rotateAngleZ=saved[i][2];
        }
        applied=false;
    }
}
