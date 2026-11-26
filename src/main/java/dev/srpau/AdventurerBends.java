package dev.srpau;

import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import java.awt.image.BufferedImage;
import java.util.*;

/** Add the four humanoid hinges without changing any original joint IDs or
 * animation components. Repack the original face strips into conventional
 * Tabula box UVs, so even the stock editor and saved TBL can draw the split. */
final class AdventurerBends {
    static final String MARK="srpau:adventurer-bends:v1";
    private static final String[] PARTS={"arm","arm_1","leg","leg_1"};
    private static final String[] NAMES={"leftElbow","rightElbow","rightKnee","leftKnee"};
    private AdventurerBends(){}
    static String field(CubeInfo c){return c.name.replaceFirst("^[0-9]{4,}_","");}
    private static boolean skipped(String reason){
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").warn("Adventurer editor bends skipped: "+reason);
        return false;
    }
    static boolean upgrade(ProjectInfo p){
        if(p==null||p.metadata==null||p.metadata.contains(MARK))return false;
        if(!"ModelInfPlayer".equals(p.modelName)&&!p.metadata.contains("srpau:entity:srparasites:sim_adventurer"))return false;
        if(p.metadata.stream().noneMatch(v->v.startsWith("srpau:rig:")))return skipped("not an imported SRP rig");
        BufferedImage src=p.bufferedTexture;
        if(src==null||src.getWidth()!=p.textureWidth||src.getHeight()!=p.textureHeight||p.textureWidth<16)return skipped("texture dimensions: project "+p.textureWidth+"x"+p.textureHeight+", image "+(src==null?"missing":src.getWidth()+"x"+src.getHeight()));
        CubeInfo[] joints=new CubeInfo[4],boxes=new CubeInfo[4];
        int[][] uv=new int[4][];
        for(CubeInfo c:p.getAllCubes())for(int i=0;i<4;i++)if(field(c).equals(PARTS[i]))joints[i]=c;
        for(int i=0;i<4;i++){
            if(joints[i]==null)return skipped("missing limb "+PARTS[i]);
            for(CubeInfo c:joints[i].getChildren())if(Arrays.equals(c.dimensions,new int[]{4,12,4})&&c.name.contains("#box")){
                if(boxes[i]!=null)return skipped("multiple boxes on "+PARTS[i]);boxes[i]=c;
            }
            CubeInfo c=boxes[i];
            if(c==null||!c.getChildren().isEmpty())return skipped("unexpected limb geometry on "+PARTS[i]);
            uv[i]=AdventurerUV.correct(i,c.txOffset,src.getWidth(),src.getHeight());
            if(uv[i]==null)return skipped("invalid UV on "+PARTS[i]+": "+Arrays.toString(c.txOffset));
            for(Animation a:p.anims)if(a.sets.containsKey(c.identifier))return skipped("geometry has authored animation on "+PARTS[i]);
        }
        int width=p.textureWidth,oldHeight=p.textureHeight,perRow=width/16;
        int needed=oldHeight+((8+perRow-1)/perRow)*10;
        int height=1;while(height<needed)height*=2;
        BufferedImage atlas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        copy(src,atlas,0,0,width,oldHeight,0,0);
        for(int i=0;i<4;i++){
            CubeInfo upper=joints[i],box=boxes[i];boolean knee=i>=2;
            int u=uv[i][0],v=uv[i][1];
            double y=box.offset[1]+6,z=box.offset[2]+(knee?0:4);
            CubeInfo lower=new CubeInfo(String.format(Locale.ROOT,"%04d_%s",9000+i,NAMES[i]));
            lower.dimensions=new int[3]; // CubeInfo defaults to a visible 1x1x1 box
            lower.metadata.add("srpau:bone");
            if(knee)lower.metadata.add("srpau:knee");
            lower.position=new double[]{0,y,z};
            CubeInfo distal=new CubeInfo(lower.name+"#box0");
            distal.dimensions=new int[]{4,6,4};
            distal.offset=new double[]{box.offset[0],box.offset[1]+6-y,box.offset[2]-z};
            distal.txMirror=box.txMirror;distal.opacity=box.opacity;distal.hidden=box.hidden;
            // Original end caps are preserved; newly exposed cut faces use the
            // nearest end cap. All four side strips retain their original pixels.
            for(int k=0;k<2;k++){
                int n=i*2+k,du=(n%perRow)*16,dv=oldHeight+(n/perRow)*10;
                copy(src,atlas,u+4,v,8,4,du+4,dv);
                copy(src,atlas,u,v+4+k*6,16,6,du,dv+4);
                (k==0?box:distal).txOffset=new int[]{du,dv};
            }
            box.dimensions[1]=6;
            lower.addChild(distal);distal.mcScale=box.mcScale;distal.opacity=box.opacity;distal.hidden=box.hidden;
            upper.addChild(lower);p.cubeCount+=2;
        }
        p.textureHeight=height;p.bufferedTexture=atlas;p.metadata.add(MARK);
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").info("Adventurer editor bends added: leftElbow, rightElbow, rightKnee, leftKnee; texture atlas "+width+"x"+height);
        return true;
    }
    private static void copy(BufferedImage src,BufferedImage dest,int x,int y,int w,int h,int dx,int dy){
        dest.setRGB(dx,dy,w,h,src.getRGB(x,y,w,h,null,0,w),0,w);
    }
}
