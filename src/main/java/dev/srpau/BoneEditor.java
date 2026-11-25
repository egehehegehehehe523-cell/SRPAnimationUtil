package dev.srpau;

import com.google.gson.Gson;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.AnimationComponent;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeGroup;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.common.Tabula;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** A bone-only authoring view over an ordinary Tabula ProjectInfo. No alternate format. */
final class BoneEditor {
    final GuiWorkspace workspace;
    private final ProjectInfo project;
    private final String projectId;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final FontRenderer font=mc.fontRenderer;
    private final List<Node> roots=new ArrayList<>(),bones=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final List<Control> controls=new ArrayList<>();
    private final ModelBase mesh=new ModelBase(){};
    private final GuiTextField name;
    private final GuiTextField[] values=new GuiTextField[3];
    private Animation clip;
    private Node selected;
    private int frame,mode=1,axis=0,clipScroll,boneScroll;
    private boolean playing,sticks=true,leftDown,rightDown,dragBone,dragTimeline,dirty,preview;
    private int lastX,lastY;
    private float pitch=15,yaw=-35,zoom=1;
    private double cx,cy,cz,extent=32;
    private double[] proposed;
    private String status="Select a bone; G/R/S = move/rotate/scale; Set key commits.";
    private int w,h,left,right,top=43,bottom;
    private static final int BG=0xff20242b,PANEL=0xff2b3039,EDGE=0xff414955,FG=0xffe4e9f0,MUTED=0xffa2acbc,ACCENT=0xff72b5ff;
    private static final class Node {
        final CubeInfo base,pose;
        final List<Node> children=new ArrayList<>();
        final ModelRenderer geometry;
        final boolean bone;
        final int depth;
        Node(CubeInfo base,ModelRenderer geometry,boolean bone,int depth){
            this.base=base;this.geometry=geometry;this.bone=bone;this.depth=depth;
            pose=new CubeInfo(base.name);
        }
    }
    private static final class Hit {
        final Node node;final double x,y,z;
        Hit(Node node,double[] p){this.node=node;x=p[0];y=p[1];z=p[2];}
    }
    private static final class Control{
        final int x,y,w,h;final Runnable action;
        Control(int x,int y,int w,int h,Runnable a){this.x=x;this.y=y;this.w=w;this.h=h;action=a;}
        boolean contains(int a,int b){return a>=x&&a<x+w&&b>=y&&b<y+h;}
    }
    BoneEditor(GuiWorkspace workspace,ProjectInfo original){
        this.workspace=workspace;projectId=original.identifier;
        project=new Gson().fromJson(original.getAsJson(),ProjectInfo.class);
        project.identifier=projectId;project.bufferedTexture=original.bufferedTexture;
        mesh.textureWidth=project.textureWidth;mesh.textureHeight=project.textureHeight;
        for(CubeInfo cube:project.cubes)roots.add(node(cube,0));
        for(CubeGroup group:project.cubeGroups)addGroup(group);
        if(bones.isEmpty())throw new IllegalArgumentException("No imported joints found");
        name=new GuiTextField(0,font,0,0,120,16);name.setMaxStringLength(80);
        for(int i=0;i<3;i++){values[i]=new GuiTextField(i+1,font,0,0,47,16);values[i].setMaxStringLength(18);}
        selected=bones.get(0);
        if(!project.anims.isEmpty())choose(project.anims.get(0));else refreshFields();
        fit();
        leftDown=Mouse.isButtonDown(0);rightDown=Mouse.isButtonDown(1);
    }
    private void addGroup(CubeGroup group){
        for(CubeInfo c:group.cubes)roots.add(node(c,0));
        for(CubeGroup g:group.cubeGroups)addGroup(g);
    }
    private Node node(CubeInfo c,int depth){
        boolean joint=(c.metadata!=null&&c.metadata.contains("srpau:bone"))||c.name.matches("^[0-9]{4,}_.*")&&!c.name.contains("#box");
        ModelRenderer geometry=null;
        if(c.dimensions[0]!=0||c.dimensions[1]!=0||c.dimensions[2]!=0){
            geometry=new ModelRenderer(mesh,c.txOffset[0],c.txOffset[1]);geometry.mirror=c.txMirror;
            geometry.addBox((float)c.offset[0],(float)c.offset[1],(float)c.offset[2],c.dimensions[0],c.dimensions[1],c.dimensions[2],(float)c.mcScale);
        }
        Node n=new Node(c,geometry,joint,depth);
        if(joint)bones.add(n);
        for(CubeInfo child:c.getChildren())n.children.add(node(child,depth+(joint?1:0)));
        return n;
    }
    private void choose(Animation a){
        clip=a;frame=0;playing=false;preview=false;proposed=null;
        name.setText(a==null?"":a.name);refreshFields();
    }
    private void select(Node n){selected=n;preview=false;proposed=null;refreshFields();}
    private int length(){return clip==null?40:Math.max(1,clip.getLength());}
    private void seek(int t){frame=Math.max(0,Math.min(length(),t));playing=false;preview=false;proposed=null;refreshFields();}
    void tick(){
        if(!playing||clip==null)return;
        if(frame>=length()){
            if(clip.loops)frame=0;else playing=false;
        }else frame++;
    }
    private void eval(Node n,float time){
        CubeInfo b=n.base,p=n.pose;
        System.arraycopy(b.position,0,p.position,0,3);System.arraycopy(b.rotation,0,p.rotation,0,3);
        System.arraycopy(b.scale,0,p.scale,0,3);p.opacity=b.opacity;p.hidden=b.hidden;
        if(clip!=null){
            List<AnimationComponent> track=clip.sets.get(b.identifier);
            if(track!=null)for(AnimationComponent c:track)if(!c.hidden&&c.length>0)c.animate(p,time);
        }
    }
    private double[] channel(Node n){return mode==0?n.pose.position:mode==1?n.pose.rotation:n.pose.scale;}
    private void refreshFields(){
        if(selected==null)return;
        eval(selected,frame);
        double[] v=channel(selected);
        for(int i=0;i<3;i++)values[i].setText(String.format(Locale.ROOT,"%.4f",v[i]));
    }
    private void setMode(int m){mode=m;preview=false;proposed=null;refreshFields();}
    private double[] readFields(){
        double[] v=new double[3];
        for(int i=0;i<3;i++){
            v[i]=Double.parseDouble(values[i].getText());
            if(!Double.isFinite(v[i])||Math.abs(v[i])>100000||(mode==2&&v[i]<=0))throw new IllegalArgumentException("Invalid transform");
        }
        return v;
    }
    private void adjust(int i,double delta){
        if(selected==null||clip==null)return;
        playing=false;
        try{proposed=readFields();}catch(RuntimeException ex){eval(selected,frame);proposed=channel(selected).clone();}
        proposed[i]+=delta;if(mode==2)proposed[i]=Math.max(.01,proposed[i]);
        preview=true;
        for(int k=0;k<3;k++)values[k].setText(String.format(Locale.ROOT,"%.4f",proposed[k]));
    }
    private void keyframe(){
        if(selected==null||clip==null){status="Create or select a clip first.";return;}
        try{
            double[] desired=readFields();
            eval(selected,frame);double[] current=channel(selected),delta=new double[3];
            boolean changed=false;
            for(int i=0;i<3;i++){delta[i]=desired[i]-current[i];changed|=Math.abs(delta[i])>1e-7;}
            if(!changed){status="No transform change to key.";return;}
            ArrayList<AnimationComponent> track=clip.sets.computeIfAbsent(selected.base.identifier,k->new ArrayList<>());
            int prev=0,next=Integer.MAX_VALUE;
            for(AnimationComponent c:track){
                if(c.hidden||c.name.equals("Duration")||c.name.equals("End"))continue;
                for(int t:new int[]{c.startKey,c.startKey+c.length}){
                    if(t<frame)prev=Math.max(prev,t);if(t>frame)next=Math.min(next,t);
                }
            }
            // Tabula components accumulate their changes. Add a local correction ramp in/out,
            // leaving the preexisting native curve and all unaffected components intact.
            AnimationComponent in=new AnimationComponent("Key "+frame+" "+modeName(),Math.max(1,frame-prev),frame==0?0:prev);
            double[] dest=component(in,frame==0);
            for(int i=0;i<3;i++)dest[i]=delta[i];
            track.add(in);
            if(next!=Integer.MAX_VALUE){
                AnimationComponent out=new AnimationComponent("Return "+frame+" "+modeName(),next-frame,frame);
                double[] reverse=component(out,false);
                for(int i=0;i<3;i++)reverse[i]=-delta[i];track.add(out);
            }
            Collections.sort(track);dirty=true;preview=false;proposed=null;refreshFields();
            status="Keyed "+selected.base.name+" at tick "+frame+".";
        }catch(RuntimeException ex){status="Enter finite transform values (scale > 0).";}
    }
    private double[] component(AnimationComponent c,boolean offset){
        if(mode==0)return offset?c.posOffset:c.posChange;
        if(mode==1)return offset?c.rotOffset:c.rotChange;
        return offset?c.scaleOffset:c.scaleChange;
    }
    private String modeName(){return mode==0?"Position":mode==1?"Rotation":"Scale";}
    private void duration(Animation a,int t){
        a.sets.computeIfAbsent(bones.get(0).base.identifier,k->new ArrayList<>()).add(new AnimationComponent("Duration",1,Math.max(0,t-1)));
    }
    private void newClip(){
        String label=name.getText().trim();
        if(label.isEmpty()||clip!=null&&label.equals(clip.name))label="New animation";
        String base=label;int i=2;
        while(hasName(label))label=base+" "+i++;
        Animation a=new Animation(label);duration(a,40);project.anims.add(a);dirty=true;choose(a);
        status="New native Tabula clip. Select a bone and set keys.";
    }
    private boolean hasName(String name){for(Animation a:project.anims)if(a.name.equals(name))return true;return false;}
    private void rename(){if(clip!=null&&!name.getText().trim().isEmpty()){clip.name=name.getText().trim();dirty=true;}}
    private void apply(){
        if(!dirty){BoneEditorEvents.close();return;}
        ProjectInfo current=workspace.getOpenProject();
        if(current==null||!projectId.equals(current.identifier)){status="Original project changed; cancel instead.";return;}
        Tabula.proxy.tickHandlerClient.mainframe.overrideProject(projectId,project.getAsJson(),project.bufferedTexture);
        BoneEditorEvents.close();
    }
    private void button(int x,int y,int width,int height,String text,boolean on,Runnable action){
        Gui.drawRect(x,y,x+width,y+height,on?0xff365879:EDGE);
        Gui.drawRect(x+1,y+1,x+width-1,y+height-1,on?0xff34577a:0xff353c47);
        font.drawString(font.trimStringToWidth(text,width-4),x+3,y+(height-8)/2,FG);
        controls.add(new Control(x,y,width,height,action));
    }
    private void text(String s,int x,int y,int color,int max){font.drawString(font.trimStringToWidth(s,Math.max(0,max)),x,y,color);}
    void draw(int mx,int my,float partial){
        w=workspace.width;h=workspace.height;left=Math.min(142,Math.max(100,w/5));right=w-left;bottom=h-88;
        controls.clear();hits.clear();lines.clear();
        Gui.drawRect(0,0,w,h,BG);
        Gui.drawRect(0,0,w,22,PANEL);Gui.drawRect(0,23,left,bottom,PANEL);Gui.drawRect(right,23,w,bottom,PANEL);
        text("SRP | ANIMATE",6,7,ACCENT,105);
        button(Math.max(112,w-144),3,72,16,"Apply & back",false,this::apply);
        button(w-68,3,64,16,"Cancel",false,()->{if(!dirty)BoneEditorEvents.close();else if(status.equals("Cancel again to discard all changes."))BoneEditorEvents.close();else status="Cancel again to discard all changes.";});
        text(dirty?"Unsaved edits":"Native Tabula project",115,7,MUTED,Math.max(0,w-265));
        text("ANIMATIONS",5,28,FG,left-10);text("BONES",right+5,28,FG,left-10);
        int rows=Math.max(1,(bottom-47)/14);
        clipScroll=Math.max(0,Math.min(clipScroll,Math.max(0,project.anims.size()-rows)));
        for(int i=clipScroll;i<project.anims.size()&&i<clipScroll+rows;i++){
            Animation a=project.anims.get(i);int y=44+(i-clipScroll)*14;
            if(a==clip)Gui.drawRect(3,y,left-3,y+13,0xff375573);
            text(a.name,6,y+2,FG,left-12);
            controls.add(new Control(3,y,left-6,13,()->choose(a)));
        }
        boneScroll=Math.max(0,Math.min(boneScroll,Math.max(0,bones.size()-rows)));
        for(int i=boneScroll;i<bones.size()&&i<boneScroll+rows;i++){
            Node n=bones.get(i);int y=44+(i-boneScroll)*14,x=right+5+Math.min(4,n.depth)*5;
            if(n==selected)Gui.drawRect(right+3,y,w-3,y+13,0xff375573);
            text(n.base.name,x,y+2,n==selected?ACCENT:FG,w-x-4);
            controls.add(new Control(right+3,y,left-6,13,()->select(n)));
        }
        // Same source geometry and native Tabula keyframe evaluator, orthographic camera.
        renderModel(partial);
        button(left+4,25,44,15,"Sticks",sticks,()->sticks=!sticks);
        button(left+51,25,29,15,"Fit",false,this::fit);
        text("RMB orbit | wheel zoom",left+83,28,MUTED,right-left-86);
        Gui.drawRect(0,bottom,w,h,PANEL);
        int y=bottom+3;
        button(4,y,38,16,"New",false,this::newClip);
        name.x=45;name.y=y;name.drawTextBox();
        int x=168;
        button(x,y,44,16,"Rename",false,this::rename);x+=47;
        button(x,y,37,16,playing?"Pause":"Play",playing,()->{if(clip!=null){if(!playing&&frame>=length())frame=0;playing=!playing;preview=false;proposed=null;}});x+=40;
        button(x,y,34,16,"Loop",clip!=null&&clip.loops,()->{if(clip!=null){clip.loops=!clip.loops;dirty=true;}});x+=37;
        button(x,y,37,16,"+20t",false,()->{if(clip!=null&&length()<1200){duration(clip,length()+20);dirty=true;}});x+=40;
        text("Tick "+frame+" / "+length(),x,y+4,FG,w-x-4);
        int ty=bottom+25,tx=10,tw=Math.max(1,w-20);
        Gui.drawRect(tx,ty,tx+tw,ty+17,0xff181c23);
        for(int t=0;t<=length();t+=Math.max(1,(int)Math.ceil(length()/12D))){
            int px=tx+(int)(tw*t/(double)length());Gui.drawRect(px,ty,px+1,ty+17,EDGE);text(""+t,px+2,ty+2,MUTED,30);
        }
        if(clip!=null&&selected!=null){
            List<AnimationComponent> track=clip.sets.get(selected.base.identifier);
            if(track!=null)for(AnimationComponent c:track){
                if(c.hidden)continue;int a=tx+(int)(tw*c.startKey/(double)length()),b=tx+(int)(tw*(c.startKey+c.length)/(double)length());
                Gui.drawRect(a,ty+9,Math.max(a+2,b),ty+13,0xff6499c7);
                Gui.drawRect(a,ty+7,a+2,ty+16,0xffb9d5ed);
            }
        }
        int pos=tx+(int)(tw*frame/(double)length());Gui.drawRect(pos,ty-2,pos+1,ty+19,0xffffbd69);
        int yy=bottom+47;
        button(4,yy,27,16,"G",mode==0,()->setMode(0));button(33,yy,27,16,"R",mode==1,()->setMode(1));button(62,yy,27,16,"S",mode==2,()->setMode(2));
        int bx=92;
        for(int i=0;i<3;i++){
            final int k=i;
            button(bx,yy,14,16,"XYZ".substring(i,i+1),axis==i,()->axis=k);bx+=16;
            values[i].x=bx;values[i].y=yy;values[i].drawTextBox();bx+=49;
            button(bx,yy,13,16,"-",false,()->adjust(k,-step()));bx+=15;
            button(bx,yy,13,16,"+",false,()->adjust(k,step()));bx+=16;
        }
        button(bx,yy,54,16,"Set key",preview,this::keyframe);
        text(modeName()+" | "+status,5,bottom+70,MUTED,w-10);
        input(mx,my,ty,tx,tw);
    }
    private double step(){return mode==0?.5:mode==1?5:.05;}
    private boolean viewport(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
    private void input(int mx,int my,int ty,int tx,int tw){
        boolean l=Mouse.isButtonDown(0),r=Mouse.isButtonDown(1);
        int wheel=Mouse.getDWheel();
        if(wheel!=0){
            if(viewport(mx,my))zoom=(float)Math.max(.15,Math.min(8,zoom*Math.pow(1.12,wheel/120D)));
            else if(mx<left)clipScroll-=Integer.signum(wheel)*3;
            else if(mx>=right)boneScroll-=Integer.signum(wheel)*3;
        }
        if(r&&rightDown&&viewport(mx,my)){yaw+=(mx-lastX)*.6F;pitch=Math.max(-89,Math.min(89,pitch+(my-lastY)*.6F));}
        if(l&&!leftDown){
            boolean field=name.mouseClicked(mx,my,0);for(GuiTextField v:values)field|=v.mouseClicked(mx,my,0);
            if(!field){
                boolean clicked=false;
                for(Control c:controls)if(c.contains(mx,my)){c.action.run();clicked=true;break;}
                if(!clicked&&my>=ty-3&&my<ty+20){dragTimeline=true;seek((int)Math.round((mx-tx)*length()/(double)tw));}
                else if(!clicked&&viewport(mx,my)){
                    Hit best=null;double dist=81;
                    for(Hit hit:hits){double d=(hit.x-mx)*(hit.x-mx)+(hit.y-my)*(hit.y-my);if(d<dist){dist=d;best=hit;}}
                    if(best!=null){if(best.node!=selected)select(best.node);else dragBone=true;}
                }
            }
        }else if(l&&leftDown){
            if(dragTimeline)seek((int)Math.round((mx-tx)*length()/(double)tw));
            if(dragBone&&mx!=lastX)adjust(axis,(mx-lastX)*step()*.15);
        }
        if(!l){dragTimeline=false;dragBone=false;}
        leftDown=l;rightDown=r;lastX=mx;lastY=my;
    }
    void key(char c,int k){
        if(k==Keyboard.KEY_ESCAPE){
            if(dirty){status="Use Apply & back, or Cancel twice to discard.";}else BoneEditorEvents.close();
            return;
        }
        if(name.isFocused()){if(k==Keyboard.KEY_RETURN){rename();name.setFocused(false);}else name.textboxKeyTyped(c,k);return;}
        for(GuiTextField v:values)if(v.isFocused()){
            if(k==Keyboard.KEY_RETURN){keyframe();v.setFocused(false);}
            else if(v.textboxKeyTyped(c,k))try{proposed=readFields();preview=true;playing=false;}catch(RuntimeException ignored){}
            return;
        }
        if(k==Keyboard.KEY_G)setMode(0);else if(k==Keyboard.KEY_R)setMode(1);else if(k==Keyboard.KEY_S)setMode(2);
        else if(k==Keyboard.KEY_X)axis=0;else if(k==Keyboard.KEY_Y)axis=1;else if(k==Keyboard.KEY_Z)axis=2;
        else if(k==Keyboard.KEY_LEFT)seek(frame-1);else if(k==Keyboard.KEY_RIGHT)seek(frame+1);
        else if(k==Keyboard.KEY_SPACE&&clip!=null){if(!playing&&frame>=length())frame=0;playing=!playing;preview=false;proposed=null;}
        else if(k==Keyboard.KEY_RETURN)keyframe();
    }
    private void fit(){
        double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Node n:roots)bounds(n,Mat.identity(),bounds);
        cx=(bounds[0]+bounds[3])/2;cy=(bounds[1]+bounds[4])/2;cz=(bounds[2]+bounds[5])/2;
        extent=Math.max(16,Math.max(bounds[3]-bounds[0],Math.max(bounds[4]-bounds[1],bounds[5]-bounds[2])));
        zoom=1;
    }
    private void bounds(Node n,Mat parent,double[] b){
        Mat m=parent.mul(Mat.local(n.base));
        for(int mask=0;mask<8;mask++){
            double[] p=m.point(n.base.offset[0]+((mask&1)!=0?n.base.dimensions[0]:0),
                n.base.offset[1]+((mask&2)!=0?n.base.dimensions[1]:0),n.base.offset[2]+((mask&4)!=0?n.base.dimensions[2]:0));
            for(int i=0;i<3;i++){b[i]=Math.min(b[i],p[i]);b[i+3]=Math.max(b[i+3],p[i]);}
        }
        for(Node c:n.children)bounds(c,m,b);
    }
    private void renderModel(float partial){
        if(right<=left||bottom<=top)return;
        float time=frame+(playing&&frame<length()?Math.max(0,Math.min(1,partial)):0);
        double size=Math.min(right-left,bottom-top)*.72/extent*zoom;
        double vx=(left+right)/2D,vy=(top+bottom)/2D;
        Mat view=Mat.translate(vx,vy,0).mul(Mat.scale(size,size,size)).mul(Mat.rx(pitch)).mul(Mat.ry(yaw)).mul(Mat.translate(-cx,-cy,-cz));
        int texture=workspace.windowTexture.imageId;
        boolean textured=texture!=-1;
        int sx=(int)(left*mc.displayWidth/(double)w),sy=(int)((h-bottom)*mc.displayHeight/(double)h);
        int sw=(int)((right-left)*mc.displayWidth/(double)w),sh=(int)((bottom-top)*mc.displayHeight/(double)h);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(sx,sy,sw,sh);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.depthMask(true);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
            if(textured){GlStateManager.enableTexture2D();GlStateManager.bindTexture(texture);}else GlStateManager.disableTexture2D();
            GlStateManager.translate(vx,vy,200);GlStateManager.scale(size,size,size);
            GlStateManager.rotate(pitch,1,0,0);GlStateManager.rotate(yaw,0,1,0);GlStateManager.translate(-cx,-cy,-cz);
            for(Node n:roots)renderNode(n,view,null,time,textured);
        }finally{
            GlStateManager.popMatrix();GlStateManager.disableDepth();GlStateManager.enableTexture2D();
            GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
        }
        try{
            if(sticks){
                GlStateManager.disableTexture2D();GlStateManager.color(.45F,.71F,1F,.9F);GL11.glLineWidth(1.5F);
                GL11.glBegin(GL11.GL_LINES);
                for(double[] line:lines){GL11.glVertex2d(line[0],line[1]);GL11.glVertex2d(line[2],line[3]);}
                GL11.glEnd();GL11.glLineWidth(1F);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
                for(Hit hit:hits){
                    int x=(int)hit.x,y=(int)hit.y;
                    int c=hit.node==selected?0xffffc66e:ACCENT;
                    Gui.drawRect(x-2,y-2,x+3,y+3,c);
                    if(hit.node==selected)text(hit.node.base.name,x+5,y-11,c,120);
                }
            }
        }finally{GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    }
    private final List<double[]> lines=new ArrayList<>();
    private void renderNode(Node n,Mat parent,Hit parentJoint,float time,boolean textured){
        eval(n,time);
        if(n==selected&&preview&&proposed!=null)System.arraycopy(proposed,0,channel(n),0,3);
        CubeInfo p=n.pose;
        Mat local=parent.mul(Mat.local(p));
        Hit hit=parentJoint;
        if(n.bone){
            hit=new Hit(n,local.point(0,0,0));hits.add(hit);
            if(sticks&&parentJoint!=null)lines.add(new double[]{parentJoint.x,parentJoint.y,hit.x,hit.y});
        }
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(p.position[0],p.position[1],p.position[2]);
            GlStateManager.rotate((float)p.rotation[2],0,0,1);GlStateManager.rotate((float)p.rotation[1],0,1,0);GlStateManager.rotate((float)p.rotation[0],1,0,0);
            GlStateManager.scale(p.scale[0],p.scale[1],p.scale[2]);
            if(!p.hidden&&n.geometry!=null){
                GlStateManager.color(textured?1F:.75F,textured?1F:.80F,textured?1F:.86F,(float)Math.max(0,Math.min(1,p.opacity/100)));
                for(ModelBox b:n.geometry.cubeList)b.render(Tessellator.getInstance().getBuffer(),1F);
            }
            if(!p.hidden)for(Node c:n.children)renderNode(c,local,hit,time,textured);
        }finally{GlStateManager.popMatrix();}
    }
    /** Same T * Rz * Ry * Rx * S local transform as viewport; only for picking and bounds. */
    private static final class Mat{
        final double[] m;
        Mat(double... a){m=a;}
        static Mat identity(){return scale(1,1,1);}
        static Mat translate(double x,double y,double z){return new Mat(1,0,0,x,0,1,0,y,0,0,1,z,0,0,0,1);}
        static Mat scale(double x,double y,double z){return new Mat(x,0,0,0,0,y,0,0,0,0,z,0,0,0,0,1);}
        static Mat rx(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(1,0,0,0,0,c,-s,0,0,s,c,0,0,0,0,1);}
        static Mat ry(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(c,0,s,0,0,1,0,0,-s,0,c,0,0,0,0,1);}
        static Mat rz(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(c,-s,0,0,s,c,0,0,0,0,1,0,0,0,0,1);}
        static Mat local(CubeInfo p){return translate(p.position[0],p.position[1],p.position[2]).mul(rz(p.rotation[2])).mul(ry(p.rotation[1])).mul(rx(p.rotation[0])).mul(scale(p.scale[0],p.scale[1],p.scale[2]));}
        Mat mul(Mat b){double[] a=new double[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)for(int k=0;k<4;k++)a[r*4+c]+=m[r*4+k]*b.m[k*4+c];return new Mat(a);}
        double[] point(double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z+m[3],m[4]*x+m[5]*y+m[6]*z+m[7],m[8]*x+m[9]*y+m[10]*z+m[11]};}
    }
}
