package dev.srpau;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Editor-only reference player. No spawned entities or changes to the parasite rig.
 * Uses the vanilla player mesh in model pixels with the torso centre as its origin.
 * Rendering raw boxes avoids persistent display lists and entity animation state. */
final class PlayerAttachment {
    private static final ResourceLocation STEVE=new ResourceLocation("textures/entity/steve.png");
    private final ModelPlayer model=new ModelPlayer(0F,false);
    final double[] position=new double[3],rotation=new double[3];
    double size=1;
    void reset(){
        java.util.Arrays.fill(position,0);java.util.Arrays.fill(rotation,0);size=1;
    }
    void render(boolean parasiteTextured){
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(position[0],position[1],position[2]);
            GlStateManager.rotate((float)rotation[2],0,0,1);
            GlStateManager.rotate((float)rotation[1],0,1,0);
            GlStateManager.rotate((float)rotation[0],1,0,0);
            GlStateManager.scale(size,size,size);
            // Vanilla player model is 32 model pixels tall. Anchor at its chest.
            GlStateManager.translate(0,-6,0);
            GlStateManager.enableTexture2D();
            Minecraft.getMinecraft().getTextureManager().bindTexture(STEVE);
            GlStateManager.color(1,1,1,1);
            part(model.bipedHead);part(model.bipedBody);
            part(model.bipedRightArm);part(model.bipedLeftArm);
            part(model.bipedRightLeg);part(model.bipedLeftLeg);
            part(model.bipedHeadwear);
        }finally{
            GlStateManager.popMatrix();
            GlStateManager.bindTexture(texture);
            if(!parasiteTextured)GlStateManager.disableTexture2D();
            GlStateManager.color(1,1,1,1);
        }
    }
    private static void part(ModelRenderer p){
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(p.rotationPointX,p.rotationPointY,p.rotationPointZ);
            for(ModelBox b:p.cubeList)b.render(Tessellator.getInstance().getBuffer(),1F);
        }finally{GlStateManager.popMatrix();}
    }
}
