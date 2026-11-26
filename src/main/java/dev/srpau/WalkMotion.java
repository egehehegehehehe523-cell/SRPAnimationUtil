package dev.srpau;

/** Keep the original player clips, but normalize their gait input for a mob.
 * Vanilla's limbSwingAmount is much smaller on slow hostile mobs than players.
 * The render phase advances from the native limb accumulator, including
 * interpolated renders; duplicate render passes cannot advance the clock. */
final class WalkMotion {
    private WalkMotion(){}
    static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    static boolean moving(double distance,double swingAmount,boolean wasMoving){
        return distance>(wasMoving?.009:.018)||swingAmount>(wasMoving?.035:.065);
    }
    // ModelInfPlayer's native ordinary animation branch accepts 0,1,2.
    static boolean locomotionStatus(int status){return status>=0&&status<=2;}
    // 1 is ordinary melee pursuit, 2 is the accelerated pursuit branch.
    // For this visual test both moving pursuit states use the sprint clips.
    static boolean pursuitRun(int status,boolean sprinting){return status==1||status==2||sprinting;}
    static double amplitude(double distance,double swingAmount){
        return clamp(Math.max(distance/.12,swingAmount/.48),0,1);
    }
    static final class State {
        double last=-1,previousSwing,phase,weight,amount,runWeight;
        boolean moving;
        final double[] knee={0,0},elbow={0,0};
        boolean kneeReady,elbowReady;
        void update(double now,double swing,double distance,double rawAmount,boolean allowed){update(now,swing,distance,rawAmount,allowed,false);}
        void update(double now,double swing,double distance,double rawAmount,boolean allowed,boolean running){
            boolean reset=last<0||now<last||now-last>8;
            double dt=reset?0:now-last;
            if(reset){phase=swing*.6662;weight=0;amount=0;runWeight=0;moving=false;kneeReady=elbowReady=false;}
            double delta=swing-previousSwing;previousSwing=swing;last=now;
            if(!allowed){weight=0;amount=0;runWeight=0;moving=false;kneeReady=elbowReady=false;return;}
            moving=moving(distance,rawAmount,moving);
            // Preserve the original player clip's 0.6662 phase rate, with
            // a shorter stride for the Adventurer's slow movement.
            if(!reset&&dt>0&&moving&&delta>=0&&delta<16)phase+=delta*(.6662+(.53296-.6662)*runWeight)*1.5;
            if(dt>0){
                weight=clamp(weight+(moving?1:-1)*dt/3,0,1);
                runWeight=clamp(runWeight+(running&&moving?1:-1)*dt/3,0,1);
                double target=moving?Math.max(.65+.35*runWeight,amplitude(distance,rawAmount)):0;
                amount+=(target-amount)*(1-Math.exp(-dt/1.5));
            }
            if(weight==0&&!moving)kneeReady=elbowReady=false;
            kneeDt=dt;
        }
        double kneeDt;
        double smooth(){return weight*weight*(3-2*weight);}
        void updateElbows(double left,double right){
            if(!elbowReady){elbow[0]=left;elbow[1]=right;elbowReady=true;return;}
            double f=1-Math.exp(-kneeDt/3.33);
            elbow[0]+=(left-elbow[0])*f;elbow[1]+=(right-elbow[1])*f;
        }
        void updateKnees(double right,double left){
            if(!kneeReady){knee[0]=right;knee[1]=left;kneeReady=true;return;}
            double f=1-Math.exp(-kneeDt/1.0);
            knee[0]+=(right-knee[0])*f;knee[1]+=(left-knee[1])*f;
        }
    }
}
