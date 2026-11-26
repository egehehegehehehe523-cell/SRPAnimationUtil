package dev.srpau;

import java.util.function.Function;

/** A continuous four-sided connector between the actual two limb cut rings.
 * Ring indices are ordered around X/Z; matching edges use the same skin strip.
 * The connector is independent of the direction or Euler axis of the bend. */
final class JointBridge {
    private JointBridge(){}
    static final double EPS=1e-8;
    static double[][] ring(double x,double y,double z,double w,double d,double inflationXZ){
        return new double[][]{{x-inflationXZ,y,z-inflationXZ},{x+w+inflationXZ,y,z-inflationXZ},
            {x+w+inflationXZ,y,z+d+inflationXZ},{x-inflationXZ,y,z+d+inflationXZ}};
    }
    static double[][] transform(double[][] ring,Function<double[],double[]> transform){
        double[][] out=new double[4][];
        for(int i=0;i<4;i++)out[i]=transform.apply(ring[i]);
        return out;
    }
    static double[] normal(double[] a,double[] b,double[] c){
        double[] ab=FootPlant.sub(b,a),ac=FootPlant.sub(c,a);
        return FootPlant.cross(ab,ac);
    }
    static boolean bent(double[][] a,double[][] b){
        for(int i=0;i<4;i++)if(FootPlant.len(FootPlant.sub(a[i],b[i]))>EPS)return true;
        return false;
    }
    /** Outward winding at a straight joint: front -Z, right +X, back +Z, left -X.
     * Adjacent faces share the very same vertices; no directional rejection or
     * holes when a ring crosses the other ring's plane. */
    static int[][] triangles(int face){
        int j=(face+1)%4;
        return new int[][]{{face,face+4,j+4},{face,j+4,j}};
    }
}
