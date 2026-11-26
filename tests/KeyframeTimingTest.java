package dev.srpau;
import java.util.*;
public final class KeyframeTimingTest{
    static void eq(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        NavigableSet<Integer> keys=new TreeSet<>(Arrays.asList(0,1,10,40));
        eq(KeyframeTiming.destination(keys,1,0,40),0);
        eq(KeyframeTiming.endpoint(1,1,0),0);
        eq(KeyframeTiming.duration(0,0),1);
        eq(KeyframeTiming.duration(0,10),10);
        eq(KeyframeTiming.destination(keys,1,20,40),10);
        eq(KeyframeTiming.destination(keys,10,-5,40),1);
        eq(KeyframeTiming.destination(keys,2,0,40),2);
        EditHistory h=new EditHistory();int[] x={0};
        x[0]=1;h.record(()->x[0]=0,()->x[0]=1);
        x[0]=2;h.record(()->x[0]=1,()->x[0]=2);
        if(!h.undo())throw new AssertionError();eq(x[0],1);
        h.undo();eq(x[0],0);h.redo();eq(x[0],1);
        x[0]=3;h.record(()->x[0]=1,()->x[0]=3);
        if(h.canRedo())throw new AssertionError("stale redo");
        for(int i=0;i<70;i++)h.record(()->{},()->{});
        int n=0;while(h.undo())n++;eq(n,64);
        System.out.println("Keyframe timing and edit history tests passed");
    }
}
