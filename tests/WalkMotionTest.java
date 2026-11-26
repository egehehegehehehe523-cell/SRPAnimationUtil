package dev.srpau;
public final class WalkMotionTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        WalkMotion.State s=new WalkMotion.State();
        s.update(0,0,0,0,true);
        for(int i=1;i<=6;i++){s.update(i,i*.16,.04,.16,true);s.updateKnees(.785,0);}
        check(s.moving&&s.weight==1&&s.amount>.6,"slow walk must have visible gait");
        double phase=s.phase;
        s.update(6,.96,.04,.16,true);check(s.phase==phase,"duplicate render advances phase");
        s.update(7,1.12,.04,.16,true);s.updateKnees(0,.785);
        check(s.knee[0]>0&&s.knee[0]<.785,"knee smoothing");
        for(int i=8;i<=12;i++)s.update(i,1.12,0,0,true);
        check(!s.moving&&s.weight==0,"stop");
        s.update(13,1.2,.04,.16,false);check(s.weight==0,"native attack priority");
        System.out.println("Adventurer walk activation, amplitude, phase and knee tests passed");
    }
}
