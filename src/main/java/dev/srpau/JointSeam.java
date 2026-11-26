package dev.srpau;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import java.util.function.Function;

/** Shared editor/game seam. Use actual cut-ring bounds, not the bone's
 * anatomical label or a guessed forward axis. All four connecting faces
 * remain present; the closed rigid cuboids occlude compressed portions. */
final class JointSeam {
    static void draw(double x,double y,double z,double w,double d,
                     double upperXZ,double upperY,double lowerXZ,double lowerY,
                     int u,int v,int tw,int th,boolean mirror,float scale,
                     Function<double[],double[]> transform){
        double[][] a=JointBridge.ring(x,y+upperY,z,w,d,upperXZ);
        double[][] source=JointBridge.ring(x,y-lowerY,z,w,d,lowerXZ);
        double[][] c=JointBridge.transform(source,transform);
        if(!JointBridge.bent(a,c))return;
        double[][] p=new double[8][];
        System.arraycopy(a,0,p,0,4);System.arraycopy(c,0,p,4,4);
        // Vanilla box layout: front, +X, back, -X. U travels backwards
        // on front and +X for the ring order; mirror swaps the side strips
        // and reverses the traversal of each face.
        int[] faceU={4,8,12,0};
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        try{
            b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX_NORMAL);
            for(int i=0;i<4;i++){
                int face=mirror?(i==1?3:i==3?1:i):i;
                double u0=(u+faceU[face]+.001)/tw,u1=(u+faceU[face]+4-.001)/tw;
                if(mirror){double q=u0;u0=u1;u1=q;}
                // Repeat the actual edge texel on both rings. No sampling into
                // transparent atlas padding at the cut or into a different face.
                double vv=(v+5.5)/th;
                int j=(i+1)%4;
                for(int[] tri:JointBridge.triangles(i)){
                    double[] n=JointBridge.normal(p[tri[0]],p[tri[1]],p[tri[2]]);
                    double len=FootPlant.len(n);
                    if(len<JointBridge.EPS)continue;
                    n=FootPlant.mul(n,1/len);
                    for(int k:tri)vertex(b,p[k],k%4==j?u1:u0,vv,n,scale);
                }
            }
            t.draw();
        }finally{if(cull)GlStateManager.enableCull();}
    }
    private static void vertex(BufferBuilder b,double[] p,double u,double v,double[] n,float s){
        b.pos(p[0]*s,p[1]*s,p[2]*s).tex(u,v).normal((float)n[0],(float)n[1],(float)n[2]).endVertex();
    }
}
