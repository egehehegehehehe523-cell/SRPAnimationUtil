package dev.srpau;

public final class TransformGizmoTest {
    static void near(double a,double b){if(Math.abs(a-b)>1e-5)throw new AssertionError(a+" != "+b);}
    static TransformGizmo.Pick hit(TransformGizmo g,double x,double y){TransformGizmo.Pick p=g.pick(x,y);if(p==null)throw new AssertionError("no handle");return p;}
    public static void main(String[] args){
        TransformGizmo move=new TransformGizmo(0,new double[]{0,0,0});
        move.axis(0,new double[]{2,0,0});move.axis(1,new double[]{0,-4,0});
        if(move.pick(0,0)!=null)throw new AssertionError("pivot picked");
        TransformGizmo.Drag m=new TransformGizmo.Drag(move,hit(move,20,0),20,0);
        near(m.delta(30,4),5);near(m.delta(30,14),0);
        TransformGizmo rot=new TransformGizmo(1,new double[]{10,20,0});
        rot.ring(2,new double[]{2,0,0},new double[]{0,1,0});
        TransformGizmo.Drag r=new TransformGizmo.Drag(rot,hit(rot,49,20),49,20);
        near(r.delta(10,39.5),90);near(r.delta(-29,20),90);near(r.delta(10,.5),90);
        TransformGizmo edge=new TransformGizmo(1,new double[]{0,0,0});
        edge.ring(0,new double[]{1,0,0},new double[]{0,0,1});
        TransformGizmo.Drag e=new TransformGizmo.Drag(edge,hit(edge,15,0),15,0);
        if(!Double.isFinite(e.delta(20,0)))throw new AssertionError("edge-on rotation");
        TransformGizmo scale=new TransformGizmo(2,new double[]{0,0,0});
        scale.axis(1,new double[]{0,2,0});
        TransformGizmo.Drag q=new TransformGizmo.Drag(scale,hit(scale,0,39),0,39);
        near(q.delta(0,78),1);
        System.out.println("Transform gizmo geometry tests passed");
    }
}
