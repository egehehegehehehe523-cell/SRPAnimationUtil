package dev.srpau;
/** Observation uses loaded server ticks, never elapsed wall-clock time. */
final class SeenProgress {
    static final int REQUIRED=1000, SAFE_TICKS=10;
    static int advance(int seen){return Math.min(REQUIRED,Math.max(0,seen)+1);}
    static int safe(int ticks,boolean hidden){return hidden?Math.min(SAFE_TICKS,ticks+1):0;}
    static final class PeekBlend {
        double last=-1,weight;
        void update(double now,boolean active){
            if(last<0||now<last||now-last>8){weight=active?1:0;last=now;return;}
            double dt=now-last;last=now;
            weight=Math.max(0,Math.min(1,weight+(active?1:-1)*dt/4));
        }
        double smooth(){return weight*weight*(3-2*weight);}
    }
}
