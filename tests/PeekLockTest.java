package dev.srpau;
public final class PeekLockTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        PeekLock p=new PeekLock();
        p.detect(20,false);check(!p.retreat(200),"unseen");
        p.detect(21,true);check(!p.retreat(30),"nine ticks");
        p.detect(29,false);p.detect(30,true);
        check(p.retreat(31),"latched ten ticks");
        p.reset();check(!p.detected(),"reset");
        for(int[] f:new int[][]{{1,0},{0,1},{-1,0},{0,-1}}){
            double[] a=PeekLock.anchor(4.5,64,5.5,f[0],f[1]);
            double dx=a[0]-4.5,dz=a[2]-5.5;
            check(Math.abs(dx*f[0]+dz*f[1]+.85)<1e-8,"behind");
            check(Math.abs(dx*f[1]-dz*f[0]-.5)<1e-8,"left");
            double yaw=Math.toRadians(PeekLock.yaw(f[0],f[1]));
            check(Math.abs(-Math.sin(yaw)-f[0])<1e-6&&Math.abs(Math.cos(yaw)-f[1])<1e-6,"facing");
            double[] h=PeekLock.head(a[0],a[1],a[2],PeekLock.yaw(f[0],f[1]));
            check(Math.abs((h[0]-a[0])*f[1]-(h[2]-a[2])*f[0]-.457)<1e-6,"authored head left offset");
            check(Math.abs((h[0]-a[0])*f[0]+(h[2]-a[2])*f[1]+.127)<1e-6,"authored head depth");
            check(PeekLock.behindFace(4.5,5.5,4.5+5*f[0],5.5+5*f[1],PeekLock.yaw(f[0],f[1])),"observer in front");
            check(!PeekLock.behindFace(4.5,5.5,4.5-5*f[0],5.5-5*f[1],PeekLock.yaw(f[0],f[1])),"observer behind");
        }
        System.out.println("Peek anchor, fixed facing and 10-tick reaction tests passed");
    }
}
