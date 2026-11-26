package dev.srpau;
import static dev.srpau.FootPlant.*;
final class TorsoMotion {
    static double wrap(double x){return Math.atan2(Math.sin(x),Math.cos(x));}
    static double yaw(double[] a,double[] b){return wrap(Math.atan2(b[0],b[2])-Math.atan2(a[0],a[2]));}
    static Rot quat(double[] q){
        double x=q[0],y=q[1],z=q[2],w=q[3],n=x*x+y*y+z*z+w*w;
        if(n<1e-12)return Rot.identity();
        double s=2/n;
        return new Rot(1-s*(y*y+z*z),s*(x*y-z*w),s*(x*z+y*w),s*(x*y+z*w),1-s*(x*x+z*z),s*(y*z-x*w),s*(x*z-y*w),s*(y*z+x*w),1-s*(x*x+y*y));
    }
    static double[] slerp(double[] a,double[] b,double t){
        double d=0;for(int i=0;i<4;i++)d+=a[i]*b[i];
        double sign=d<0?-1:1;d=clamp(Math.abs(d),0,1);
        double w0=1-t,w1=t;
        if(d<.9995){double angle=Math.acos(d),sin=Math.sin(angle);w0=Math.sin((1-t)*angle)/sin;w1=Math.sin(t*angle)/sin;}
        double[] q=new double[4];double n=0;
        for(int i=0;i<4;i++){q[i]=a[i]*w0+b[i]*w1*sign;n+=q[i]*q[i];}
        n=Math.sqrt(n);for(int i=0;i<4;i++)q[i]/=n;return q;
    }
    static final class Balance {
        double last=-1,yaw,roll,shift,drop;
        void update(double now,double turn,double side,double lift){
            double dt=last<0||now<last||now-last>8?0:now-last;
            if(last<0||now<last||now-last>8){yaw=roll=shift=drop=0;}
            last=now;double f=1-Math.exp(-dt/1.7);
            yaw+=wrap(turn-yaw)*f;roll+=(side*Math.toRadians(1.5)-roll)*f;
            shift+=(side*.6-shift)*f;drop+=(lift*.2-drop)*f;
        }
    }
}
