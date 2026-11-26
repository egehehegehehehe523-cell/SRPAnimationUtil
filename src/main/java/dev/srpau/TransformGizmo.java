package dev.srpau;

import java.util.ArrayList;
import java.util.List;

/** Screen-space handles derived from the exact evaluated model transform.
 * Pure geometry: no OpenGL, entity, editor or Minecraft dependencies. */
final class TransformGizmo {
    static final int MOVE=0, ROTATE=1, SCALE=2;
    static final double RADIUS=32.5;
    static final int SEGMENTS=64;
    final int mode;
    final double[] origin;
    final List<Handle> handles=new ArrayList<>();
    TransformGizmo(int mode,double[] origin){this.mode=mode;this.origin=origin;}
    static double norm3(double[] v){return Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);}
    static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static double[] mul(double[] a,double s){return new double[]{a[0]*s,a[1]*s,a[2]*s};}
    static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    void axis(int axis,double[] unit){
        double n=norm3(unit);
        if(n<1e-8)return;
        double[] span=mul(unit,RADIUS/n);
        if(Math.hypot(span[0],span[1])<6)return; // end-on axes are not ambiguous click targets
        handles.add(new Handle(axis,new double[][]{origin,add(origin,span)},unit,null,null));
    }
    void ring(int axis,double[] u,double[] v){
        double n=Math.max(norm3(u),norm3(v));
        if(n<1e-8)return;
        u=mul(u,RADIUS/n);v=mul(v,RADIUS/n);
        double[][] points=new double[SEGMENTS+1][];
        for(int i=0;i<=SEGMENTS;i++){
            double a=i*2*Math.PI/SEGMENTS;
            points[i]=add(origin,add(mul(u,Math.cos(a)),mul(v,Math.sin(a))));
        }
        handles.add(new Handle(axis,points,null,u,v));
    }
    static final class Handle {
        final int axis;
        final double[][] points;
        final double[] unit,u,v;
        Handle(int axis,double[][] points,double[] unit,double[] u,double[] v){
            this.axis=axis;this.points=points;this.unit=unit;this.u=u;this.v=v;
        }
    }
    static final class Pick {
        final Handle handle;
        final double distance,depth,angle;
        Pick(Handle h,double d,double z,double a){handle=h;distance=d;depth=z;angle=a;}
    }
    Pick pick(double x,double y){
        Pick best=null;
        if(mode!=ROTATE&&Math.hypot(x-origin[0],y-origin[1])<6)return null;
        for(Handle h:handles)for(int i=0;i<h.points.length-1;i++){
            double[] a=h.points[i],b=h.points[i+1];
            double dx=b[0]-a[0],dy=b[1]-a[1],len=dx*dx+dy*dy;
            if(len<1e-9)continue;
            double t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/len));
            double d=Math.hypot(x-a[0]-t*dx,y-a[1]-t*dy);
            double z=a[2]+t*(b[2]-a[2]);
            if(d<=5&&(best==null||d<best.distance-.7||(Math.abs(d-best.distance)<=.7&&z>best.depth)))
                best=new Pick(h,d,z,(i+t)*2*Math.PI/SEGMENTS);
        }
        return best;
    }
    /** Freeze the starting projection so changes to the pose cannot move the
     * drag reference. An ellipse inverse gives rotation in radians even under
     * foreshortening; edge-on rings use their local screen tangent instead. */
    static final class Drag {
        final int mode,axis;
        final double[] origin,unit,u,v;
        final double tx,ty;
        final boolean invertible;
        double lastX,lastY;
        Drag(TransformGizmo g,Pick p,double x,double y){
            mode=g.mode;axis=p.handle.axis;origin=g.origin.clone();
            unit=p.handle.unit;u=p.handle.u;v=p.handle.v;
            lastX=x;lastY=y;
            if(mode==ROTATE){
                double a=p.angle;
                tx=-u[0]*Math.sin(a)+v[0]*Math.cos(a);
                ty=-u[1]*Math.sin(a)+v[1]*Math.cos(a);
                double det=u[0]*v[1]-u[1]*v[0];
                invertible=Math.abs(det)>RADIUS*RADIUS*.12;
            }else{tx=0;ty=0;invertible=false;}
        }
        private double angle(double x,double y){
            double dx=x-origin[0],dy=y-origin[1];
            double det=u[0]*v[1]-u[1]*v[0];
            return Math.atan2((u[0]*dy-u[1]*dx)/det,(dx*v[1]-dy*v[0])/det);
        }
        double delta(double x,double y){
            double dx=x-lastX,dy=y-lastY,result=0;
            if(mode==ROTATE){
                if(invertible&&Math.hypot(x-origin[0],y-origin[1])>4&&Math.hypot(lastX-origin[0],lastY-origin[1])>4){
                    double a=angle(x,y)-angle(lastX,lastY);
                    result=Math.toDegrees(Math.atan2(Math.sin(a),Math.cos(a)));
                }else{
                    double len=tx*tx+ty*ty;
                    if(len>4)result=Math.toDegrees((dx*tx+dy*ty)/len);
                }
            }else{
                double len=unit[0]*unit[0]+unit[1]*unit[1];
                if(len>1e-10){
                    if(mode==MOVE)result=(dx*unit[0]+dy*unit[1])/len;
                    else result=(dx*unit[0]+dy*unit[1])/Math.sqrt(len)/RADIUS;
                }
            }
            lastX=x;lastY=y;
            return Double.isFinite(result)?result:0;
        }
    }
}
