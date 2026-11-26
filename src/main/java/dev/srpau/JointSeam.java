package dev.srpau;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import java.util.function.Function;

/** Connect the opening side of two articulated cuboid cut rings. Stretch only
 * the narrow seam texture strip; the original rigid limb faces stay untouched.
 * Coordinates are upper-limb local model pixels. */
final class JointSeam {
    static void draw(double x,double y,double z,double w,double d,int u,int v,int tw,int th,boolean mirror,float scale,Function<double[],double[]> transform){
        double[][] top={{x,y,z},{x+w,y,z},{x+w,y,z+d},{x,y,z+d}};
        double[][] bottom=new double[4][];
        for(int i=0;i<4;i++)bottom[i]=transform.apply(top[i]);
        int[] faceU={4,8,12,0};
        Tessellator t=Tessellator.getInstance();
        BufferBuilder b=t.getBuffer();
        boolean started=false;
        for(int i=0;i<4;i++){
            int j=(i+1)%4;
            // Ignore the overlapping (compressed) side of the bend.
            if(Math.max(bottom[i][1],bottom[j][1])<=y+1e-4)continue;
            double[] a=top[i],c=top[j],e=bottom[j],f=bottom[i];
            int face=mirror?(i==1?3:i==3?1:i):i;
            double u0=(u+faceU[face])/(double)tw,u1=(u+faceU[face]+4)/(double)tw;
            if(mirror){double q=u0;u0=u1;u1=q;}
            double v0=(v+5.5)/th,v1=(v+5.99)/th;
            if(!started){b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX_NORMAL);started=true;}
            tri(b,a,c,e,u0,v0,u1,v0,u1,v1,scale);
            tri(b,a,e,f,u0,v0,u1,v1,u0,v1,scale);
        }
        if(started)t.draw();
    }
    private static void tri(BufferBuilder b,double[] a,double[] c,double[] e,double u0,double v0,double u1,double v1,double u2,double v2,float s){
        double[] normal=FootPlant.unit(FootPlant.cross(FootPlant.sub(c,a),FootPlant.sub(e,a)));
        if(FootPlant.len(normal)<.5)return;
        // Ring winds clockwise viewed from above; this order faces outward.
        vertex(b,a,u0,v0,normal,s);vertex(b,c,u1,v1,normal,s);vertex(b,e,u2,v2,normal,s);
    }
    private static void vertex(BufferBuilder b,double[] p,double u,double v,double[] n,float s){
        b.pos(p[0]*s,p[1]*s,p[2]*s).tex(u,v).normal((float)n[0],(float)n[1],(float)n[2]).endVertex();
    }
}
