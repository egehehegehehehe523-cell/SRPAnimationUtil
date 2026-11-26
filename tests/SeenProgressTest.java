package dev.srpau;
public final class SeenProgressTest {
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args){
        int a=0,b=0;for(int i=0;i<500;i++)a=SeenProgress.advance(a);
        for(int i=0;i<30;i++)b=SeenProgress.advance(b);
        for(int i=0;i<499;i++)a=SeenProgress.advance(a);
        check(a==999&&b==30,"per-target progress");
        check(SeenProgress.advance(a)==1000&&SeenProgress.advance(1000)==1000,"50-second threshold");
        int safe=0;for(int i=0;i<9;i++)safe=SeenProgress.safe(safe,true);
        check(safe==9&&SeenProgress.safe(safe,false)==0&&SeenProgress.safe(safe,true)==10,"safe window");
        SeenProgress.PeekBlend p=new SeenProgress.PeekBlend();
        p.update(0,false);p.update(1,true);p.update(5,true);
        check(p.weight==1,"enter");p.update(100,true);check(p.weight==1,"held after render gap");
        p.update(104,false);check(p.weight==0,"exit");
        System.out.println("Seen counter, hidden opportunity and persistent peek blend passed");
    }
}
