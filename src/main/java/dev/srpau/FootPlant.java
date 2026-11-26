package dev.srpau;

/** A two-segment hinge solver and stationary foot anchors in model-pixel units.
 * Independent of the Minecraft renderer, so geometry and stepping can be tested. */
final class FootPlant {
    private FootPlant(){}
    static double[] v(double x,double y,double z){return new double[]{x,y,z};}
    static double[] add(double[] a,double[] b){return v(a[0]+b[0],a[1]+b[1],a[2]+b[2]);}
    static double[] sub(double[] a,double[] b){return v(a[0]-b[0],a[1]-b[1],a[2]-b[2]);}
    static double[] mul(double[] a,double s){return v(a[0]*s,a[1]*s,a[2]*s);}
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b){return v(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]);}
    static double len(double[] a){return Math.sqrt(dot(a,a));}
    static double[] unit(double[] a){double n=len(a);return n>1e-9?mul(a,1/n):v(0,0,0);}
    static double clamp(double a,double lo,double hi){return Math.max(lo,Math.min(hi,a));}
    static double[] lerp(double[] a,double[] b,double t){return add(a,mul(sub(b,a),t));}
    static final class Rot {
        final double[] m;
        Rot(double... m){this.m=m;}
        static Rot identity(){return new Rot(1,0,0,0,1,0,0,0,1);}
        static Rot euler(double x,double y,double z){
            double a=Math.cos(x),b=Math.sin(x),c=Math.cos(y),d=Math.sin(y),e=Math.cos(z),f=Math.sin(z);
            return new Rot(e*c,e*d*b-f*a,e*d*a+f*b,f*c,f*d*b+e*a,f*d*a-e*b,-d,c*b,c*a);
        }
        double[] apply(double[] p){return v(m[0]*p[0]+m[1]*p[1]+m[2]*p[2],m[3]*p[0]+m[4]*p[1]+m[5]*p[2],m[6]*p[0]+m[7]*p[1]+m[8]*p[2]);}
        Rot inv(){return new Rot(m[0],m[3],m[6],m[1],m[4],m[7],m[2],m[5],m[8]);}
        Rot mul(Rot b){double[] n=new double[9];for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++)n[i*3+j]+=m[i*3+k]*b.m[k*3+j];return new Rot(n);}
        double[] angles(){
            double y=Math.asin(clamp(-m[6],-1,1));
            if(Math.abs(Math.cos(y))<1e-7)return v(0,y,Math.atan2(-m[1],m[4]));
            return v(Math.atan2(m[7],m[8]),y,Math.atan2(m[3],m[0]));
        }
        static Rot align(double[] from,double[] to){
            double[] a=unit(from),b=unit(to);
            if(len(a)<.5||len(b)<.5)return identity();
            double c=clamp(dot(a,b),-1,1),s=len(cross(a,b));
            if(c>1-1e-10)return identity();
            double[] axis;
            if(s<1e-8){axis=unit(cross(a,Math.abs(a[0])<.8?v(1,0,0):v(0,1,0)));s=0;c=-1;}
            else axis=unit(cross(a,b));
            double x=axis[0],y=axis[1],z=axis[2],t=1-c;
            return new Rot(t*x*x+c,t*x*y-s*z,t*x*z+s*y,t*x*y+s*z,t*y*y+c,t*y*z-s*x,t*x*z-s*y,t*y*z+s*x,t*z*z+c);
        }
    }
    static final class Pose {
        final Rot r;final double[] p;
        Pose(Rot r,double[] p){this.r=r;this.p=p;}
        Pose child(Rot q,double[] t){return new Pose(r.mul(q),point(t));}
        double[] point(double[] t){return add(p,r.apply(t));}
        double[] local(double[] t){return r.inv().apply(sub(t,p));}
    }
    static final class Solution {
        final Rot upper,lower;
        Solution(Rot a,Rot b){upper=a;lower=b;}
    }
    // Knee and foot vectors are the actual front-edge hinge geometry, not assumed
    // six-unit centre pivots. Preserve native joint twist by minimal corrections.
    static Solution solve(Rot nativeUpper,double[] knee,double[] foot,double[] target){
        return solve(nativeUpper,Rot.identity(),knee,foot,target);
    }
    static Solution solve(Rot nativeUpper,Rot authoredLower,double[] knee,double[] foot,double[] target){
        double a=len(knee),b=len(foot),d=len(target);
        if(d<1e-7||a<1e-7||b<1e-7)return new Solution(nativeUpper,authoredLower);
        double[] dir=unit(target);
        d=clamp(d,Math.abs(a-b)+.001,a+b-.001);
        double along=(a*a+d*d-b*b)/(2*d);
        double height=Math.sqrt(Math.max(0,a*a-along*along));
        double[] nk=nativeUpper.apply(knee);
        double[] pole=sub(nk,mul(dir,dot(nk,dir)));
        if(len(pole)<1e-6){double[] forward=v(0,0,-1);pole=sub(forward,mul(dir,dot(forward,dir)));}
        if(len(pole)<1e-6){double[] side=v(1,0,0);pole=sub(side,mul(dir,dot(side,dir)));}
        double[] k=add(mul(dir,along),mul(unit(pole),height));
        Rot upper=Rot.align(nk,k).mul(nativeUpper);
        Rot lower=Rot.align(authoredLower.apply(foot),upper.inv().apply(sub(mul(dir,d),k))).mul(authoredLower);
        return new Solution(upper,lower);
    }
    /** One foot steps at a time; the other remains locked in world space.
     * No collision/terrain queries. Targets are the native feet on entry and
     * the current native stance when the body turns sufficiently far. */
    static final class State{
        final double[][] anchors=new double[2][];
        double[] from,to;
        int stepping=-1;
        double began,last=-1;
        double[][] update(double[][] natural,double now){
            if(last<0||now<last||now-last>8||anchors[0]==null||
                len(sub(anchors[0],natural[0]))>20||len(sub(anchors[1],natural[1]))>20){
                for(int i=0;i<2;i++)anchors[i]=natural[i].clone();
                stepping=-1;
            }
            last=now;
            if(stepping>=0&&now-began>=5){
                anchors[stepping]=to;stepping=-1;
            }
            if(stepping<0){
                double a=horizontal(anchors[0],natural[0]),b=horizontal(anchors[1],natural[1]);
                int i=a>=b?0:1;
                if(Math.max(a,b)>2.1){
                    stepping=i;began=now;from=anchors[i].clone();
                    to=natural[i].clone();to[1]=from[1];
                }
            }
            double[][] out={anchors[0].clone(),anchors[1].clone()};
            if(stepping>=0){
                double t=clamp((now-began)/5,0,1),ease=t*t*(3-2*t);
                out[stepping]=lerp(from,to,ease);
                out[stepping][1]-=Math.sin(Math.PI*t)*2.2;
            }
            return out;
        }
        static double horizontal(double[] a,double[] b){return Math.hypot(a[0]-b[0],a[2]-b[2]);}
    }
}
