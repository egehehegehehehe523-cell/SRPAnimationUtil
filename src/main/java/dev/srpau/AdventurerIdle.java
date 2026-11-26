package dev.srpau;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Only the authored additive edits from the supplied TBL, not its sampled
 * native motion. Native SRP continues to evaluate its own procedural idle.
 * The added curves ping-pong because the authored endpoints differ. */
final class AdventurerIdle {
    static final String PATH="/assets/srpanimationutil/animations/adventurer_idle_edits.json";
    String rig;
    int duration;
    Map<String,double[][]> tracks;
    static AdventurerIdle load() throws Exception {
        try(InputStream in=AdventurerIdle.class.getResourceAsStream(PATH)){
            if(in==null)throw new IllegalStateException("Missing "+PATH);
            AdventurerIdle a=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),AdventurerIdle.class);
            if(a==null||a.duration<=0||a.duration>1200||a.tracks==null)throw new IllegalArgumentException("Bad idle resource");
            for(double[][] track:a.tracks.values())for(double[] c:track){
                if(c.length!=11||c[0]<0||c[1]<=0||c[0]+c[1]>a.duration)throw new IllegalArgumentException("Bad idle component");
                for(double v:c)if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite idle component");
            }
            return a;
        }
    }
    static double phase(double time,int duration){
        double t=Math.max(0,time)%(2D*duration);
        return t<=duration?t:2D*duration-t;
    }
    // Output rotation degrees, then positional offsets in model pixels.
    void evaluate(String name,double tick,double[] out){
        java.util.Arrays.fill(out,0);
        double[][] track=tracks.get(name);
        if(track==null)return;
        for(double[] c:track){
            if(tick<c[0])continue;
            double f=Math.min(1,(tick-c[0])/c[1]);
            for(int i=0;i<3;i++){
                out[i]+=c[2+i]+c[5+i]*f;
                out[3+i]+=c[8+i];
            }
        }
    }
    static final class Blend {
        double last=-1,weight,elapsed;
        void update(double now,boolean active,boolean normal){
            double dt=last<0||now<last||now-last>8?0:now-last;
            if(last<0||now<last||now-last>8){weight=0;elapsed=0;}
            last=now;
            if(!normal){weight=0;elapsed=0;return;}
            if(active){if(weight==0)elapsed=0;else elapsed+=dt;weight=Math.min(1,weight+dt/4);}
            else{weight=Math.max(0,weight-dt/4);if(weight==0)elapsed=0;}
        }
        double smooth(){return weight*weight*(3-2*weight);}
    }
}
