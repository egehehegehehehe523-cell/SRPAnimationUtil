package dev.srpau;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import java.util.*;
final class AdventurerTorso {
    static final String MARK="srpau:adventurer-torso:v1";
    static final double PIVOT_Y=12;
    static boolean upgrade(ProjectInfo p){
        if(p==null||p.metadata==null||!p.metadata.contains(AdventurerBends.MARK)||p.metadata.contains(MARK))return false;
        CubeInfo root=null;
        for(CubeInfo c:p.getAllCubes())if(AdventurerBends.field(c).equals("mainbody")){root=c;break;}
        if(root==null)return false;
        List<CubeInfo> children=new ArrayList<>(root.getChildren());
        int hips=0;for(CubeInfo c:children)if(isHip(AdventurerBends.field(c)))hips++;
        if(hips!=2||children.size()<3)return false;
        CubeInfo torso=new CubeInfo("9004_torsoCore");
        torso.dimensions=new int[3];torso.metadata.add("srpau:bone");
        torso.position=new double[]{0,PIVOT_Y,0};
        root.addChild(torso);
        for(CubeInfo c:children)if(!isHip(AdventurerBends.field(c))){
            root.removeChild(c);
            torso.getChildren().add(c);c.parentIdentifier=torso.identifier;
            c.position=c.position.clone();c.position[1]-=PIVOT_Y;
        }
        p.cubeCount++;p.metadata.add(MARK);return true;
    }
    static boolean isHip(String s){return "jointRL".equals(s)||"jointLL".equals(s);}
}
