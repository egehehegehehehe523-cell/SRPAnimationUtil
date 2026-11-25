package dev.srpau;

import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.AnimationComponent;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import java.awt.image.BufferedImage;
import java.util.*;

/** Native Tabula animation components are cumulative. Encode initial absolute offset
 * against the project's existing bind pose, then incremental pose deltas. */
final class Capture {
    final Rig rig;
    final BufferedImage texture;
    final String name;
    final List<Integer> times=new ArrayList<>();
    final List<double[][]> frames=new ArrayList<>();
    Capture(Rig rig,BufferedImage texture,String name){this.rig=rig;this.texture=texture;this.name=name;}
    void add(int tick,double[][] pose){
        if(!times.isEmpty() && tick<=times.get(times.size()-1))return;
        if(tick>1200 || frames.size()>=1201)throw new IllegalArgumentException("Capture limit is 60 seconds");
        // Bound Tabula's cumulative components and avoid enormous projects.
        if((long)(frames.size()+1)*rig.bones.size()>120000)throw new IllegalArgumentException("Capture joint sample budget reached");
        times.add(tick);frames.add(pose);
    }
    boolean usable(){return frames.size()>=2;}
    boolean matches(ProjectInfo project){
        return project!=null&&project.metadata!=null&&project.metadata.contains("srpau:rig:"+rig.signature);
    }
    void append(ProjectInfo project){
        if(!matches(project)||!usable())throw new IllegalArgumentException("Capture/project mismatch or too few samples");
        Map<String,CubeInfo> names=new HashMap<>();
        for(CubeInfo cube:project.getAllCubes())if(names.put(cube.name,cube)!=null)throw new IllegalArgumentException("Ambiguous cube name "+cube.name);
        String label=name;
        Set<String> existing=new HashSet<>();
        for(Animation a:project.anims)existing.add(a.name);
        for(int suffix=2;existing.contains(label);suffix++)label=name+" "+suffix;
        Animation animation=new Animation(label);
        animation.loops=false;
        int max=times.get(times.size()-1);
        for(int i=0;i<rig.bones.size();i++){
            CubeInfo joint=names.get(rig.bones.get(i).name);
            if(joint==null)throw new IllegalArgumentException("Missing joint "+rig.bones.get(i).name);
            double[] first=frames.get(0)[i];
            double[] base={joint.position[0],joint.position[1],joint.position[2],joint.rotation[0],joint.rotation[1],joint.rotation[2]};
            ArrayList<AnimationComponent> track=new ArrayList<>();
            boolean offset=false;
            for(int k=0;k<6;k++)if(Math.abs(first[k]-base[k])>1e-7)offset=true;
            if(offset){
                AnimationComponent c=new AnimationComponent("Start pose",1,0);
                for(int k=0;k<3;k++){c.posOffset[k]=first[k]-base[k];c.rotOffset[k]=first[k+3]-base[k+3];}
                track.add(c);
            }
            for(int f=1;f<frames.size();f++){
                double[] a=frames.get(f-1)[i],b=frames.get(f)[i];
                boolean changed=false;
                for(int k=0;k<6;k++)if(Math.abs(b[k]-a[k])>1e-7)changed=true;
                if(!changed)continue;
                AnimationComponent c=new AnimationComponent("Pose "+times.get(f),times.get(f)-times.get(f-1),times.get(f-1));
                for(int k=0;k<3;k++){c.posChange[k]=b[k]-a[k];c.rotChange[k]=b[k+3]-a[k+3];}
                track.add(c);
            }
            if(!track.isEmpty())animation.sets.put(joint.identifier,track);
        }
        // Preserve full duration even when all final joints are stationary.
        CubeInfo root=names.get(rig.roots.get(0).name);
        animation.sets.computeIfAbsent(root.identifier,k->new ArrayList<>())
            .add(new AnimationComponent("End",1,Math.max(0,max-1)));
        project.anims.add(animation);
    }
}
