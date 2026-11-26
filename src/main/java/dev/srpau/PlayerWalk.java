package dev.srpau;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static dev.srpau.FootPlant.*;
/** Read the installed Mo' Bends Alpha.3's actual biped walk clips. */
final class PlayerWalk {
    private static final String PATH="/assets/mobends/bends/animations/";
    private static final double[] ID={0,0,0,1};
    final Clip base,knees,swing,runBase,runArms,runSwing;
    private PlayerWalk(Clip base,Clip knees,Clip swing,Clip runBase,Clip runArms,Clip runSwing){
        this.base=base;this.knees=knees;this.swing=swing;this.runBase=runBase;this.runArms=runArms;this.runSwing=runSwing;
    }
    static PlayerWalk load()throws IOException{
        Clip base=read("biped/walk_base"),knees=read("biped/walk_forelegs"),swing=read("biped/walk_swing");
        Clip runBase=null,runArms=null,runSwing=null;
        try{
            runBase=read("player/sprint_base");runArms=read("player/sprint_forearms");runSwing=read("player/sprint_swing");
        }catch(IOException|RuntimeException ex){
            org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").warn("Sprint clips unavailable; using walk fallback",ex);
            runBase=runArms=runSwing=null;
        }
        return new PlayerWalk(base,knees,swing,runBase,runArms,runSwing);
    }
    boolean hasRun(){return runBase!=null;}
    private static InputStream open(String name)throws IOException{
        String path=PATH+name+".json";
        // Forge's active resource manager also supports enabled resource packs.
        try{
            return net.minecraft.client.Minecraft.getMinecraft().getResourceManager()
                .getResource(new net.minecraft.util.ResourceLocation("mobends","bends/animations/"+name+".json")).getInputStream();
        }catch(IOException ignored){}
        InputStream in=PlayerWalk.class.getResourceAsStream(path);
        if(in!=null)return in;
        net.minecraftforge.fml.common.ModContainer mod=net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("mobends");
        if(mod!=null){
            File src=mod.getSource();
            if(src.isDirectory()){
                File f=new File(src,path.substring(1));
                if(f.isFile())return new FileInputStream(f);
            }else if(src.isFile()){
                try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(src)){
                    java.util.zip.ZipEntry entry=zip.getEntry(path.substring(1));
                    if(entry!=null)try(InputStream stream=zip.getInputStream(entry)){
                        ByteArrayOutputStream out=new ByteArrayOutputStream();
                        byte[] bytes=new byte[8192];int n;
                        while((n=stream.read(bytes))!=-1)out.write(bytes,0,n);
                        return new ByteArrayInputStream(out.toByteArray());
                    }
                }
            }
        }
        throw new IOException("Missing Mo' Bends clip "+name+" in resources or installed jar");
    }
    private static Clip read(String name)throws IOException{
        try(InputStream in=open(name)){
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
    private double[] rotationQuat(String bone,double phase,double amount,boolean running){
        Clip main=running?(bone.endsWith("ForeArm")?runArms:runBase):(bone.endsWith("ForeLeg")?knees:base);
        Clip add=running?runSwing:swing;
        double[] q=main.sample(bone,phase).rotation;
        if(add.bones.containsKey(bone))q= multiply(q,TorsoMotion.slerp(ID,add.sample(bone,phase).rotation,clamp(amount,0,1)));
        return q;
    }
    private static double[] multiply(double[] a,double[] b){
        return new double[]{a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
            a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
            a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
            a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]};
    }
    Rot rotation(String bone,double phase,double amount,double runWeight){
        double[] a=rotationQuat(bone,phase,amount,false);
        if(!hasRun()||runWeight<=0)return TorsoMotion.quat(a);
        double[] b=rotationQuat(bone,phase,amount,true);
        return TorsoMotion.quat(TorsoMotion.slerp(a,b,clamp(runWeight,0,1)));
    }
    double bob(double phase,double runWeight){
        double a=base.sample("root",phase).position[1];
        return hasRun()?a+(runBase.sample("root",phase).position[1]-a)*runWeight:a;
    }
}
