package dev.srpau;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static dev.srpau.FootPlant.*;
/** Read the installed Mo' Bends Alpha.3's actual biped walk clips. */
final class PlayerWalk {
    private static final String PATH="/assets/mobends/bends/animations/biped/";
    private static final double[] ID={0,0,0,1};
    final Clip base,knees,swing;
    private PlayerWalk(Clip base,Clip knees,Clip swing){this.base=base;this.knees=knees;this.swing=swing;}
    static PlayerWalk load()throws IOException{return new PlayerWalk(read("walk_base"),read("walk_forelegs"),read("walk_swing"));}
    private static Clip read(String name)throws IOException{
        try(InputStream in=PlayerWalk.class.getResourceAsStream(PATH+name+".json")){
            if(in==null)throw new IOException("Missing Mo' Bends clip "+name);
            Clip c=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Clip.class);
            if(c==null||c.formatVersion!=2||c.duration<=0||c.bones==null)throw new IOException("Invalid clip "+name);
            for(Track t:c.bones.values()){
                if(t.keyframes==null||t.keyframes.length==0||(c.times!=null&&c.times.length!=t.keyframes.length))throw new IOException("Invalid track "+name);
                for(Frame f:t.keyframes)if(f.position==null||f.position.length!=3||f.rotation==null||f.rotation.length!=4)throw new IOException("Invalid frame "+name);
            }
            return c;
        }
    }
    static final class Frame {double[] position,rotation;}
    static final class Track {Frame[] keyframes;}
    static final class Clip {
        int formatVersion;double duration;double[] times;String interpolation;
        Map<String,Track> bones;
        Frame sample(String bone,double phase){
            Track t=bones.get(bone);
            if(t==null){Frame f=new Frame();f.position=v(0,0,0);f.rotation=ID;return f;}
            double x=((phase%duration)+duration)%duration;
            int lo,hi;double frac;
            if(times!=null){
                lo=0;while(lo+1<times.length&&times[lo+1]<=x)lo++;
                hi=Math.min(lo+1,t.keyframes.length-1);frac=hi==lo?0:(x-times[lo])/(times[hi]-times[lo]);
            }else{
                double i=x/duration*(t.keyframes.length-1);
                lo=(int)i;hi=Math.min(lo+1,t.keyframes.length-1);frac=i-lo;
            }
            if("step".equals(interpolation))frac=0;
            Frame a=t.keyframes[lo],b=t.keyframes[hi],out=new Frame();
            out.position=lerp(a.position,b.position,frac);
            out.rotation=TorsoMotion.slerp(a.rotation,b.rotation,frac);return out;
        }
    }
    Rot rotation(String bone,double phase,double amount){
        Clip c=bone.endsWith("ForeLeg")?knees:base;
        Rot q=TorsoMotion.quat(c.sample(bone,phase).rotation);
        if(swing.bones.containsKey(bone))q=q.mul(TorsoMotion.quat(TorsoMotion.slerp(ID,swing.sample(bone,phase).rotation,clamp(amount,0,1))));
        return q;
    }
    double bob(double phase){return base.sample("root",phase).position[1];}
}
