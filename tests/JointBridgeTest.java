package dev.srpau;

import static dev.srpau.FootPlant.*;

public final class JointBridgeTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        double[][] a=JointBridge.ring(-2,4,-2,4,4,0);
        check(!JointBridge.bent(a,a),"neutral");
        double[][] b=JointBridge.transform(a,p->add(p,v(0,1,0)));
        double[][] p=new double[8][];
        System.arraycopy(a,0,p,0,4);System.arraycopy(b,0,p,4,4);
        double[][] expected={v(0,0,-1),v(1,0,0),v(0,0,1),v(-1,0,0)};
        for(int i=0;i<4;i++)for(int[] t:JointBridge.triangles(i))
            check(dot(JointBridge.normal(p[t[0]],p[t[1]],p[t[2]]),expected[i])>0,"outward winding "+i);
        double[] hinge=v(0,4,2);
        for(int axis=0;axis<3;axis++)for(int deg=-120;deg<=120;deg+=15){
            if(deg==0)continue;
            double q=Math.toRadians(deg);
            Rot r=Rot.euler(axis==0?q:0,axis==1?q:0,axis==2?q:0);
            b=JointBridge.transform(a,x->add(hinge,r.apply(sub(x,hinge))));
            check(JointBridge.bent(a,b),"bent");
            System.arraycopy(b,0,p,4,4);
            int count=0;
            for(int i=0;i<4;i++)for(int[] t:JointBridge.triangles(i)){
                double[] n=JointBridge.normal(p[t[0]],p[t[1]],p[t[2]]);
                if(len(n)>JointBridge.EPS)count++;
                for(double v:n)check(Double.isFinite(v),"nonfinite");
            }
            check(count>=4,"missing connector faces");
        }
        System.out.println("Joint bridge winding and multi-axis geometry tests passed");
    }
}
