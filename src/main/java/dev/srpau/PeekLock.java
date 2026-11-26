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
    // Head-box centre measured from authored tick zero (model pixels / 16).
    // World vertical origin is the vanilla living renderer's 1.501 offset.
    static double[] head(double x,double y,double z,float yaw){
        double a=Math.toRadians(yaw),c=Math.cos(a),s=Math.sin(a);
        return new double[]{x+c*.457+s*.127,y+1.88,z+s*.457-c*.127};
    }
    static boolean behindFace(double cx,double cz,double tx,double tz,float yaw){
        double a=Math.toRadians(yaw);
        double forward=-(tx-cx)*Math.sin(a)+(tz-cz)*Math.cos(a);
        return forward>.5;
    }
    static float yaw(double fx,double fz){return (float)Math.toDegrees(Math.atan2(fz,fx))-90;}
}
