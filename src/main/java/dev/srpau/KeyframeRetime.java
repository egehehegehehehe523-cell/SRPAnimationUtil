package dev.srpau;

import com.google.gson.Gson;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.AnimationComponent;
import java.util.*;

/** Native Tabula curves are additive ramps and instantaneous offsets.
 * Move only the requested channel's endpoint, splitting mixed components
 * when necessary. Other channels and hidden components stay unchanged.
 * Do not resample or rewrite the original imported animation. */
final class KeyframeRetime {
    private static final Gson GSON=new Gson();
    private KeyframeRetime(){}
    static double[] values(AnimationComponent c,int ch,boolean offset){
        if(ch==0)return offset?c.posOffset:c.posChange;
        if(ch==1)return offset?c.rotOffset:c.rotChange;
        return offset?c.scaleOffset:c.scaleChange;
    }
    static boolean nonzero(double[] a){for(double v:a)if(Math.abs(v)>1e-7)return true;return false;}
    static NavigableSet<Integer> keys(List<AnimationComponent> track,int ch){
        NavigableSet<Integer> keys=new TreeSet<>();
        if(track==null)return keys;
        for(AnimationComponent c:track){
            if(c.hidden||c.length<=0)continue;
            boolean ramp=nonzero(values(c,ch,false)),offset=nonzero(values(c,ch,true));
            if(ramp||offset)keys.add(c.startKey);
            if(ramp)keys.add(c.startKey+c.length);
        }
        return keys;
    }
    private static boolean other(AnimationComponent c,int ch){
        if(c.opacityChange!=0||c.opacityOffset!=0)return true;
        for(int i=0;i<3;i++)if(i!=ch&&(nonzero(values(c,i,false))||nonzero(values(c,i,true))))return true;
        return false;
    }
    static ArrayList<AnimationComponent> move(List<AnimationComponent> track,int ch,int source,int dest){
        ArrayList<AnimationComponent> result=new ArrayList<>();
        for(AnimationComponent c:track){
            boolean ramp=nonzero(values(c,ch,false)),offset=nonzero(values(c,ch,true));
            int start=c.startKey,end=start+c.length;
            if(c.hidden||c.length<=0||(!ramp&&!offset)||(start!=source&&(!ramp||end!=source))){
                result.add(c);continue;
            }
            // Offset-only components have no visible end key. Move the offset
            // without manufacturing a second boundary one tick later.
            int a=KeyframeTiming.endpoint(start,source,dest);
            int b=ramp?KeyframeTiming.endpoint(end,source,dest):a+1;
            if(b<a)throw new IllegalArgumentException("Key crosses neighbour");
            if(other(c,ch)){
                AnimationComponent keep=GSON.fromJson(GSON.toJson(c),AnimationComponent.class);
                Arrays.fill(values(keep,ch,false),0);Arrays.fill(values(keep,ch,true),0);
                result.add(keep);
            }
            AnimationComponent moved=new AnimationComponent(c.name,KeyframeTiming.duration(a,b),a);
            double[] change=values(c,ch,false),off=values(c,ch,true);
            for(int i=0;i<3;i++){
                values(moved,ch,true)[i]=off[i]+(b==a?change[i]:0);
                values(moved,ch,false)[i]=b==a?0:change[i];
            }
            if(b>a&&ramp)moved.progressionCoords=c.progressionCoords; // never mutated by this editor
            if(nonzero(values(moved,ch,false))||nonzero(values(moved,ch,true)))result.add(moved);
        }
        Collections.sort(result);
        return result;
    }
}
