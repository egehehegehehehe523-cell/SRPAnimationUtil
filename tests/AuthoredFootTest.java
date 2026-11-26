package dev.srpau;
import static dev.srpau.FootPlant.*;
public final class AuthoredFootTest{
    public static void main(String[] args){
        double[] knee=v(0,6,-2),foot=v(0,6,2);
        Rot u=Rot.euler(-.26,.3,0),l=Rot.euler(.23,0,0);
        double[] natural=u.apply(add(knee,l.apply(foot)));
        Solution s=solve(u,l,knee,foot,natural);
        if(len(sub(s.upper.apply(add(knee,s.lower.apply(foot))),natural))>1e-5)throw new AssertionError("authored stance not preserved");
        double[] target=v(1,10,2);
        s=solve(u,l,knee,foot,target);
        if(len(sub(s.upper.apply(add(knee,s.lower.apply(foot))),target))>1e-5)throw new AssertionError("foot target mismatch");
        System.out.println("Authored foot stance tests passed");
    }
}
