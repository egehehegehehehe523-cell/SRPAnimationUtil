package dev.srpau;
import static dev.srpau.FootPlant.*;
public final class PursuitMotionTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        check(WalkMotion.locomotionStatus(0)&&WalkMotion.locomotionStatus(1)&&WalkMotion.locomotionStatus(2),"native normal branch");
        check(!WalkMotion.locomotionStatus(3)&&!WalkMotion.locomotionStatus(6)&&!WalkMotion.locomotionStatus(10),"special state");
        check(WalkMotion.pursuitRun(1,false)&&WalkMotion.pursuitRun(2,false)&&!WalkMotion.pursuitRun(0,false),"pursuit mapping");
        WalkMotion.State s=new WalkMotion.State();
        for(int i=0;i<8;i++){s.update(i,i*.3,.08,.3,true,true);s.updateElbows(-.3,-.7);}
        check(s.runWeight==1&&s.weight==1&&s.amount>.95,"run transition");
        s.update(8,2.4,.08,.3,true,false);
        check(s.runWeight>0&&s.runWeight<1,"run to walk transition");
        s.update(9,2.7,.08,.3,false,true);
        check(s.runWeight==0&&s.weight==0,"native special state");
        Rot parent=Rot.euler(.9,.3,-.1).mul(Rot.euler(1.1,0,.3));
        Rot wanted=Rot.euler(0,.6,0).mul(Rot.euler(.2,0,0));
        double[] f=v(0,0,-1);
        check(len(sub(parent.mul(parent.inv().mul(wanted)).apply(f),wanted.apply(f)))<1e-6,"head parent compensation");
        System.out.println("Pursuit and head tracking tests passed");
    }
}
