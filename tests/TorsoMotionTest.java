package dev.srpau;
import static dev.srpau.FootPlant.*;
public final class TorsoMotionTest {
    static void near(double a,double b){if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        double[] p=v(2,11,-3),pivot=v(0,12,0);
        near(len(sub(add(pivot,sub(p,pivot)),p)),0);
        Rot q=TorsoMotion.quat(new double[]{0,Math.sin(Math.PI/4),0,Math.cos(Math.PI/4)});
        near(len(sub(q.apply(v(0,0,1)),v(1,0,0))),0);
        near(TorsoMotion.yaw(v(0,0,1),v(1,0,0)),Math.PI/2);
        TorsoMotion.Balance b=new TorsoMotion.Balance();b.update(0,0,0,0);b.update(2,.5,1,1);
        if(b.yaw<=0||b.yaw>=.5||b.shift<=0)throw new AssertionError("balance response");
        b.update(4,0,0,0);if(b.yaw<=0||b.yaw>=.5)throw new AssertionError("balance settle");
        double[] s=TorsoMotion.slerp(new double[]{0,0,0,1},new double[]{0,1,0,0},.5);
        near(len(sub(TorsoMotion.quat(s).apply(v(0,0,1)),v(1,0,0))),0);
        System.out.println("Torso pivot, quaternion and balance tests passed");
    }
}
