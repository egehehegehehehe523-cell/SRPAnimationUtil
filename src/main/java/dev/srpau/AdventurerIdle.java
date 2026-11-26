package dev.srpau;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Full absolute pose samples from the edited Tabula idle. */
final class AdventurerIdle {
    static final String PATH="/assets/srpanimationutil/animations/adventurer_idle_full.json";
    String rig;
    int duration;
    Map<String,com.google.gson.JsonArray> poses;
    double[][] curves;
    private final transient java.util.Map<String,double[][]> compiled=new java.util.HashMap<>();
    static AdventurerIdle load() throws Exception {
        try(InputStream in=AdventurerIdle.class.getResourceAsStream(PATH)){
            if(in==null)throw new IllegalStateException("Missing "+PATH);
            AdventurerIdle a=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),AdventurerIdle.class);
            if(a==null||a.duration!=40||a.poses==null||a.poses.size()!=94||a.curves==null)throw new IllegalArgumentException("Bad idle resource");
            for(Map.Entry<String,com.google.gson.JsonArray> e:a.poses.entrySet()){
                if(e.getValue().size()!=6)throw new IllegalArgumentException("Bad pose "+e.getKey());
                double[][] p=new double[6][];
                for(int i=0;i<6;i++){
                    com.google.gson.JsonPrimitive v=e.getValue().get(i).getAsJsonPrimitive();
                    p[i]=v.isString()?a.curves[Integer.parseInt(v.getAsString().substring(1))]:new double[]{v.getAsDouble()};
                    if(p[i].length!=1&&p[i].length!=a.duration+1)throw new IllegalArgumentException("Bad curve length");
                    for(double d:p[i])if(!Double.isFinite(d))throw new IllegalArgumentException("Nonfinite pose");
                }
                a.compiled.put(e.getKey(),p);
            }
            return a;
        }
    }
    static double phase(double time,int duration){
        double t=Math.max(0,time)%(2D*duration);
        return t<=duration?t:2D*duration-t;
    }
    boolean has(String name){return compiled.containsKey(name);}
    // Absolute position (pixels), then Euler rotation (degrees).
    void evaluate(String name,double tick,double[] out){
        double[][] p=compiled.get(name);
        if(p==null)throw new IllegalArgumentException("Missing pose "+name);
        tick=Math.max(0,Math.min(duration,tick));
        int lo=(int)tick,hi=Math.min(duration,lo+1);double f=tick-lo;
        for(int i=0;i<6;i++)out[i]=p[i].length==1?p[i][0]:p[i][lo]+(p[i][hi]-p[i][lo])*f;
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
