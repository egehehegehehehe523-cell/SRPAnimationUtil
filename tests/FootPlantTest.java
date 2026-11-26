package dev.srpau;
import static dev.srpau.FootPlant.*;
public final class FootPlantTest {
    static void near(double[] a,double[] b){if(len(sub(a,b))>1e-5)throw new AssertionError(java.util.Arrays.toString(a)+" != "+java.util.Arrays.toString(b));}
    public static void main(String[] args){
        double[] knee=v(0,6,-2),foot=v(0,6,2);
        Rot r=Rot.euler(-.26,.13,.04);
        double[] original=r.apply(add(knee,foot));
        Solution nativePose=solve(r,knee,foot,original);
        near(nativePose.upper.apply(knee),r.apply(knee));
        near(nativePose.upper.apply(nativePose.lower.apply(foot)),r.apply(foot));
        for(double[] target:new double[][]{v(1,11,2),v(-2,10,4),v(0,10,-3),v(2,9,-1)}){
            Solution p=solve(r,knee,foot,target);
            near(p.upper.apply(add(knee,p.lower.apply(foot))),target);
            near(Rot.euler(p.upper.angles()[0],p.upper.angles()[1],p.upper.angles()[2]).apply(knee),p.upper.apply(knee));
        }
        State s=new State();
        double[][] stand={v(-2,0,0),v(2,0,0)};
        near(s.update(stand,0)[0],stand[0]);
        double[][] turn={v(-1,0,1),v(1,0,-1)};
        near(s.update(turn,1)[0],stand[0]);
        double[][] big={v(0,0,3),v(0,0,-3)};
        s.update(big,2);
        double[][] stepping=s.update(big,4.5);
        if(stepping[0][1]>=-2)throw new AssertionError("No step lift");
        near(stepping[1],stand[1]);
        near(s.update(big,7)[0],big[0]);
        System.out.println("Foot planting and two-bone IK tests passed");
    }
}
