package dev.srpau;

import java.util.NavigableSet;

/** Keep neighbouring keys in order. A key may meet its immediate neighbour:
 * a ramp of zero duration becomes an instantaneous native offset. */
final class KeyframeTiming {
    private KeyframeTiming(){}
    static int destination(NavigableSet<Integer> keys,int source,int requested,int duration){
        if(!keys.contains(source))return source;
        Integer prev=keys.lower(source),next=keys.higher(source);
        return Math.max(prev==null?0:prev,Math.min(next==null?duration:next,requested));
    }
    static int endpoint(int value,int source,int destination){
        return value==source?destination:value;
    }
    static int duration(int start,int end){return Math.max(1,end-start);}
}
