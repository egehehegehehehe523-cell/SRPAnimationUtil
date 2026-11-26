package dev.srpau;

/** Pure cover geometry and latched reaction delay. */
final class PeekLock {
    static final int REACTION_TICKS=10;
    private int firstSeen=-1;
    void reset(){firstSeen=-1;}
    void detect(int now,boolean seen){if(seen&&firstSeen<0)firstSeen=now;}
    boolean detected(){return firstSeen>=0;}
    boolean retreat(int now){return detected()&&now-firstSeen>=REACTION_TICKS;}
    static double[] anchor(double x,double y,double z,double fx,double fz){
        return new double[]{x-fx*.85+fz*.5,y,z-fz*.85-fx*.5};
    }
    static float yaw(double fx,double fz){return (float)Math.toDegrees(Math.atan2(fz,fx))-90;}
}
